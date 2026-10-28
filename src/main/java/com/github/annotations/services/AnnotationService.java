package com.github.annotations.services;

import com.github.annotations.utils.PathPatterns;
import com.github.annotations.model.LocalMappingFile;
import com.github.annotations.model.MappingFileParser;
import com.github.annotations.utils.MappingsPaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.JsonSyntaxException;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.diagnostic.Logger;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Сервис заметок уровня проекта
 * Хранит все заметки текущего проекта и управляет ими
 * Данные лежат в JSON-файлах папки .idea/tree-description (см. MappingsPaths)
 * Поддерживает несколько файлов сопоставлений, основной — local-description.json
 */
public class AnnotationService implements Disposable {
    
    private static final Logger LOG = Logger.getInstance(AnnotationService.class);
    private static final Gson gson = new GsonBuilder()
        .setPrettyPrinting()
        .setFieldNamingPolicy(FieldNamingPolicy.IDENTITY)
        .create();
    
    private final Project project;
    private Map<String, String> annotations = new LinkedHashMap<>(); // заметки файлов
    private Map<String, String> packageAnnotations = new LinkedHashMap<>(); // заметки пакетов
    private Map<String, String> fileMatchAnnotations = new LinkedHashMap<>(); // заметки по шаблонам файлов
    private Map<String, String> packageMatchAnnotations = new LinkedHashMap<>(); // заметки по шаблонам пакетов
    
    // Цвета текста заметок
    private Map<String, String> packagesTextColor = new LinkedHashMap<>(); // цвета заметок пакетов
    private Map<String, String> filesTextColor = new LinkedHashMap<>(); // цвета заметок файлов
    
    private boolean projectTreeAnnotationsEnabled = true; // показывать ли заметки в дереве проекта
    private String language = "ru"; // язык интерфейса, по умолчанию русский
    private MessageBusConnection messageBusConnection;
    
    // Содержимое всех файлов сопоставлений
    private Map<String, LocalMappingFile> allMappingFiles = new HashMap<>();
    
    // Слушатель JSON-файлов
    private JsonFileWatcher jsonFileWatcher;
    
    // Сервис живого обновления заметок (на основе DocumentListener)
    private MappingsAutoSaver mappingsAutoSaver;
    
    // Слушатель редактора файлов
    private FileEditorListener fileEditorListener;
    
    public AnnotationService(Project project) {
        this.project = project;
        setupFileWatcher();
        // При запуске загружаем все файлы сопоставлений из папки рабочих JSON
        loadFromMappingsDirectory();
        // Слушатель JSON-файлов создаётся позже, чтобы не было циклической зависимости
        // setupJsonFileWatcher(); // сразу не инициализируем
        
        // Сервис живого обновления: следит за изменениями в обе стороны
        setupMappingsAutoSaver();
        
        // Слушатель запуска проекта (новый API)
        registerProjectListener();
    }
    
    /**
     * Освобождает ресурсы; платформа вызывает при закрытии проекта (Disposable)
     */
    @Override
    public void dispose() {
        if (jsonFileWatcher != null) {
            jsonFileWatcher.dispose();
        }
        if (mappingsAutoSaver != null) {
            mappingsAutoSaver.dispose();
        }
        if (fileEditorListener != null) {
            fileEditorListener.dispose();
        }
    }
    
    /**
     * Создаёт слушатель JSON-файлов (отложенная инициализация)
     */
    private void setupJsonFileWatcher() {
        try {
            if (jsonFileWatcher == null) {
                jsonFileWatcher = new JsonFileWatcher(project, this); // передаём ссылку на себя
                LOG.info("Слушатель JSON-файлов инициализирован");
            }
        } catch (Exception e) {
            LOG.warn("Не удалось инициализировать слушатель JSON-файлов: " + e.getMessage());
        }
    }
    
    /**
     * Создаёт автосохранение открытых рабочих JSON (см. MappingsAutoSaver)
     */
    private void setupMappingsAutoSaver() {
        try {
            if (mappingsAutoSaver == null) {
                mappingsAutoSaver = new MappingsAutoSaver(project);
                
                // Слушатель редактора файлов
                setupFileEditorListener();
            }
        } catch (Exception e) {
            LOG.warn("Не удалось включить автосохранение рабочих JSON: " + e.getMessage());
        }
    }
    
    /**
     * Создаёт слушатель редактора файлов
     */
    private void setupFileEditorListener() {
        try {
            if (fileEditorListener == null) {
                fileEditorListener = new FileEditorListener(project, mappingsAutoSaver);
                LOG.info("Слушатель редактора файлов инициализирован");
            }
        } catch (Exception e) {
            LOG.warn("Не удалось инициализировать слушатель редактора файлов: " + e.getMessage());
        }
    }
    
    /**
     * Гарантирует, что слушатель JSON-файлов создан
     */
    private void ensureJsonFileWatcher() {
        if (jsonFileWatcher == null) {
            setupJsonFileWatcher();
        }
    }
    
    /**
     * Гарантирует, что автосохранение рабочих JSON включено
     */
    private void ensureMappingsAutoSaver() {
        if (mappingsAutoSaver == null) {
            setupMappingsAutoSaver();
        }
    }
    
    /**
     * Возвращает AnnotationService проекта
     */
    public static AnnotationService getInstance(Project project) {
        // Сервис уровня проекта
        return project.getService(AnnotationService.class);
    }
    
    /**
     * Инициализирует сервис при запуске проекта
     */
    public static AnnotationService initializeService(Project project) {
        // Гарантируем, что сервис создан и инициализирован
        return project.getService(AnnotationService.class);
    }
    
    /**
     * Добавляет или обновляет заметку файла
     */
    public void setAnnotation(@NotNull String filePath, @NotNull String annotation) {
        annotations.put(filePath, annotation);
        saveToFile(); // сразу сохраняем в файл
    }
    
    /**
     * Добавляет или обновляет заметку файла с цветом
     */
    public void setAnnotation(@NotNull String filePath, @NotNull String annotation, @Nullable String textColor) {
        annotations.put(filePath, annotation);
        
        // Цвет сохраняем, только если он не по умолчанию
        if (textColor != null && !textColor.equals("#BBBBBB") && !textColor.trim().isEmpty()) {
            filesTextColor.put(filePath, textColor);
        } else {
            // Цвет по умолчанию или пустой — удаляем настройку цвета
            filesTextColor.remove(filePath);
        }
        
        saveToFile(); // сразу сохраняем в файл
    }
    
    /**
     * Добавляет или обновляет заметку файла с цветом и сразу обновляет дерево
     */
    public void setAnnotationAndRefresh(@NotNull String filePath, @NotNull String annotation, @Nullable String textColor) {
        setAnnotation(filePath, annotation, textColor);
        
        // Сразу обновляем VFS и дерево проекта
        refreshAfterSave();
    }
    
    /**
     * Добавляет или обновляет заметку пакета или папки
     */
    public void setPackageAnnotation(@NotNull String packagePath, @NotNull String annotation) {
        packageAnnotations.put(packagePath, annotation);
        saveToFile(); // сразу сохраняем в файл
    }
    
    /**
     * Добавляет или обновляет заметку пакета или папки с цветом
     */
    public void setPackageAnnotation(@NotNull String packagePath, @NotNull String annotation, @Nullable String textColor) {
        packageAnnotations.put(packagePath, annotation);
        
        // Цвет сохраняем, только если он не по умолчанию
        if (textColor != null && !textColor.equals("#BBBBBB") && !textColor.trim().isEmpty()) {
            packagesTextColor.put(packagePath, textColor);
        } else {
            // Цвет по умолчанию или пустой — удаляем настройку цвета
            packagesTextColor.remove(packagePath);
        }
        
        saveToFile(); // сразу сохраняем в файл
    }
    
    /**
     * Добавляет или обновляет заметку пакета или папки с цветом и сразу обновляет дерево
     */
    public void setPackageAnnotationAndRefresh(@NotNull String packagePath, @NotNull String annotation, @Nullable String textColor) {
        setPackageAnnotation(packagePath, annotation, textColor);
        
        // Сразу обновляем VFS и дерево проекта
        refreshAfterSave();
    }
    
    /**
     * Сохраняет заметки в .idea/tree-description/local-description.json.
     *
     * Файл пишет только IDE: если он открыт в редакторе, меняется Document
     * и сразу сохраняется; иначе запись идёт через VFS. Раньше запись шла
     * мимо IDE (Files.writeString), и открытый файл расходился с диском:
     * диалог «файл изменён на диске» или затирание новой заметки
     * автосохранением устаревшего текста (MappingsAutoSaver).
     *
     * Запись возможна только в потоке интерфейса; вызов из фона
     * (например, при переносе старых данных во время загрузки) откладывается туда.
     */
    private void saveToFile() {
        String json = gson.toJson(createLocalMappingFile());
        Application application = ApplicationManager.getApplication();
        if (application.isDispatchThread()) {
            writeLocalDescription(json);
        } else {
            application.invokeLater(() -> writeLocalDescription(json), project.getDisposed());
        }
    }

    /** Записывает текст в local-description.json через Document или VFS; только поток интерфейса. */
    private void writeLocalDescription(@NotNull String json) {
        Path mappingsDir = MappingsPaths.getMappingsDir(project);
        if (mappingsDir == null || project.isDisposed()) {
            return;
        }
        try {
            Files.createDirectories(mappingsDir);
            VirtualFile dir = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(mappingsDir);
            if (dir == null) {
                throw new IOException("папка не найдена в VFS: " + mappingsDir);
            }
            WriteCommandAction.writeCommandAction(project).run(() -> {
                VirtualFile file = dir.findChild(MappingsPaths.LOCAL_DESCRIPTION_FILE);
                if (file == null) {
                    file = dir.createChildData(this, MappingsPaths.LOCAL_DESCRIPTION_FILE);
                }
                Document document = FileDocumentManager.getInstance().getCachedDocument(file);
                if (document != null) {
                    document.setText(json);
                    FileDocumentManager.getInstance().saveDocument(document);
                } else {
                    VfsUtil.saveText(file, json);
                }
            });
        } catch (IOException e) {
            LOG.warn("Не удалось сохранить файл заметок: " + e.getMessage());
        }
    }
    
    /**
     * Возвращает заметки в виде JSON (для экспорта)
     */
    public String getJsonContentForExport() {
        LocalMappingFile localMapping = createLocalMappingFile();
        return gson.toJson(localMapping);
    }
    
    /**
     * Собирает объект локального файла сопоставлений
     */
    private LocalMappingFile createLocalMappingFile() {
        LocalMappingFile localMapping = new LocalMappingFile();
        
        // Метаданные
        localMapping.setName("project local mappings");
        localMapping.setVersion("1.0.0");
        localMapping.setDescription("Current project path and file mapping configuration");
        localMapping.setAuthor("Local");
        localMapping.setLanguage(language);
        
        // Содержимое сопоставлений — в нужном порядке
        LocalMappingFile.Mappings mappings = localMapping.getMappings();
        mappings.setFiles(new LinkedHashMap<>(annotations));
        mappings.setPackages(new LinkedHashMap<>(packageAnnotations));
        mappings.setFileMatch(new LinkedHashMap<>(fileMatchAnnotations));
        mappings.setPackageMatch(new LinkedHashMap<>(packageMatchAnnotations));
        mappings.setFilesTextColor(new LinkedHashMap<>(filesTextColor));
        mappings.setPackagesTextColor(new LinkedHashMap<>(packagesTextColor));
        
        return localMapping;
    }
    

    
    /**
     * Возвращает заметку
     */
    @Nullable
    public String getAnnotation(@NotNull String filePath) {
        return annotations.get(filePath);
    }
    
    /**
     * Возвращает заметку файла (по VirtualFile)
     */
    @Nullable
    public String getAnnotation(@NotNull VirtualFile file) {
        String relativePath = getRelativePath(file);
        return relativePath != null ? getAnnotation(relativePath) : null;
    }
    
    /**
     * Возвращает заметку пакета (по VirtualFile)
     */
    @Nullable
    public String getPackageAnnotation(@NotNull VirtualFile file) {
        String relativePath = getRelativePath(file);
        if (relativePath != null) {
            // В packages путь хранится как в файловой системе (с /), не нормализуем
            return getPackageAnnotation(relativePath);
        }
        return null;
    }
    
    /**
     * Возвращает заметку пакета (по строковому пути)
     */
    @Nullable
    public String getPackageAnnotation(@NotNull String packagePath) {
        // Ищем как есть, без нормализации пути
        return packageAnnotations.get(packagePath);
    }
    
    /**
     * Ищет заметку по шаблону файла для имени файла
     * Порядок проверки:
     * 1. Регулярное выражение (например ".*Controller\\.java$")
     * 2. Целое слово (например "service" совпадает только с отдельным словом, не с "consumer.service")
     * 3. Полное совпадение имени файла
     */
    @Nullable
    public String getFileMatchAnnotation(@NotNull String fileName) {
        return getFileMatchAnnotation(fileName, null);
    }
    
    /**
     * Ищет заметку по шаблону файла для имени и пути (поддерживает смешанные шаблоны)
     * @param fileName имя файла
     * @param relativePath относительный путь файла (может быть null)
     * @return найденная заметка
     */
    @Nullable
    public String getFileMatchAnnotation(@NotNull String fileName, @Nullable String relativePath) {
        if (fileMatchAnnotations.isEmpty()) {
            return null;
        }
        
        // Сначала полное совпадение
        String exactMatch = fileMatchAnnotations.get(fileName);
        if (exactMatch != null) {
            return exactMatch;
        }
        
        // Затем шаблоны по очереди
        return fileMatchAnnotations.entrySet().stream()
            .filter(entry -> {
                String pattern = entry.getKey();
                String value = entry.getValue();
                
                // Пустые значения пропускаем
                if (value == null || value.trim().isEmpty()) {
                    return false;
                }
                
                // Точное сопоставление
                return PathPatterns.matchesFile(fileName, pattern, relativePath);
            })
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Ищет заметку по шаблону пакета для имени пакета
     * Порядок проверки:
     * 1. Регулярное выражение (например ".*\\.service\\..*")
     * 2. Целые сегменты пути (например "service" совпадает только с целым сегментом, не с "consumer.service")
     * 3. Полное совпадение пути пакета
     */
    @Nullable
    public String getPackageMatchAnnotation(@NotNull String packageName) {
        if (packageMatchAnnotations.isEmpty()) {
            return null;
        }
        
        // Сначала полное совпадение
        String exactMatch = packageMatchAnnotations.get(packageName);
        if (exactMatch != null) {
            return exactMatch;
        }
        
        // Затем шаблоны по очереди
        return packageMatchAnnotations.entrySet().stream()
            .filter(entry -> {
                String pattern = entry.getKey();
                String value = entry.getValue();
                
                // Пустые значения пропускаем
                if (value == null || value.trim().isEmpty()) {
                    return false;
                }
                
                // Точное сопоставление
                return PathPatterns.matchesPackage(packageName, pattern);
            })
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Удаляет заметку файла
     */
    public void removeAnnotation(@NotNull String filePath) {
        annotations.remove(filePath);
        // Заодно удаляем настройку цвета
        filesTextColor.remove(filePath);
        saveToFile(); // сразу сохраняем в файл
    }
    
    /**
     * Удаляет заметку пакета или папки
     */
    public void removePackageAnnotation(@NotNull String packagePath) {
        packageAnnotations.remove(packagePath);
        // Заодно удаляем настройку цвета
        packagesTextColor.remove(packagePath);
        saveToFile(); // сразу сохраняем в файл
    }
    
    /**
     * Удаляет заметку (по VirtualFile)
     */
    public void removeAnnotation(@NotNull VirtualFile file) {
        String relativePath = getRelativePath(file);
        if (relativePath != null) {
            removeAnnotation(relativePath);
        }
    }
    
    /**
     * Есть ли заметка у файла
     */
    public boolean hasAnnotation(@NotNull String filePath) {
        return annotations.containsKey(filePath);
    }
    
    /**
     * Есть ли заметка у пакета или папки
     */
    public boolean hasPackageAnnotation(@NotNull String packagePath) {
        return packageAnnotations.containsKey(packagePath);
    }
    
    /**
     * Есть ли заметка у файла (по VirtualFile)
     */
    public boolean hasAnnotation(@NotNull VirtualFile file) {
        String relativePath = getRelativePath(file);
        return relativePath != null && hasAnnotation(relativePath);
    }
    
    /**
     * Есть ли заметка у пакета или папки (по VirtualFile)
     */
    public boolean hasPackageAnnotation(@NotNull VirtualFile file) {
        String relativePath = getRelativePath(file);
        return relativePath != null && hasPackageAnnotation(relativePath);
    }
    
    /**
     * Все заметки файлов
     */
    @NotNull
    public Map<String, String> getAllAnnotations() {
        return new HashMap<>(annotations);
    }
    
    /**
     * Все заметки пакетов
     */
    @NotNull
    public Map<String, String> getAllPackageAnnotations() {
        return new HashMap<>(packageAnnotations);
    }
    
    /**
     * Все заметки по шаблонам файлов
     */
    @NotNull
    public Map<String, String> getAllFileMatchAnnotations() {
        return new HashMap<>(fileMatchAnnotations);
    }
    
    /**
     * Все заметки по шаблонам пакетов
     */
    @NotNull
    public Map<String, String> getAllPackageMatchAnnotations() {
        return new HashMap<>(packageMatchAnnotations);
    }
    
    /**
     * Цвет заметки пакета
     */
    public String getPackageTextColor(@NotNull String packagePath) {
        return packagesTextColor.get(packagePath);
    }
    
    /**
     * Цвет заметки файла
     */
    public String getFileTextColor(@NotNull String filePath) {
        return filesTextColor.get(filePath);
    }
    
    /**
     * Задаёт цвет заметки пакета
     */
    public void setPackageTextColor(@NotNull String packagePath, @NotNull String color) {
        packagesTextColor.put(packagePath, color);
        saveToFile();
    }
    
    /**
     * Задаёт цвет заметки файла
     */
    public void setFileTextColor(@NotNull String filePath, @NotNull String color) {
        filesTextColor.put(filePath, color);
        saveToFile();
    }
    
    /**
     * Все цвета заметок пакетов
     */
    public Map<String, String> getAllPackageTextColors() {
        return new HashMap<>(packagesTextColor);
    }
    
    /**
     * Все цвета заметок файлов
     */
    public Map<String, String> getAllFileTextColors() {
        return new HashMap<>(filesTextColor);
    }
    
    
    
    /**
     * Задаёт заметки файлов целиком
     */
    public void setAnnotations(@NotNull Map<String, String> newAnnotations) {
        annotations.putAll(newAnnotations);
        saveToFile(); // сразу сохраняем в файл
    }
    
    /**
     * Задаёт заметки пакетов целиком
     */
    public void setPackageAnnotations(@NotNull Map<String, String> newAnnotations) {
        packageAnnotations.putAll(newAnnotations);
        saveToFile(); // сразу сохраняем в файл
    }
    
    /**
     * Добавляет или обновляет заметку по шаблону файла
     */
    public void setFileMatchAnnotation(@NotNull String pattern, @NotNull String annotation) {
        fileMatchAnnotations.put(pattern, annotation);
        saveToFile(); // сразу сохраняем в файл
    }
    
    /**
     * Добавляет или обновляет заметку по шаблону пакета
     */
    public void setPackageMatchAnnotation(@NotNull String pattern, @NotNull String annotation) {
        packageMatchAnnotations.put(pattern, annotation);
        saveToFile(); // сразу сохраняем в файл
    }
    
    /**
     * Задаёт заметки по шаблонам файлов целиком
     */
    public void setFileMatchAnnotations(@NotNull Map<String, String> newAnnotations) {
        fileMatchAnnotations.putAll(newAnnotations);
        saveToFile(); // сразу сохраняем в файл
    }
    
    /**
     * Задаёт заметки по шаблонам пакетов целиком
     */
    public void setPackageMatchAnnotations(@NotNull Map<String, String> newAnnotations) {
        packageMatchAnnotations.putAll(newAnnotations);
        saveToFile(); // сразу сохраняем в файл
    }
    
    /**
     * Удаляет все заметки
     */
    public void clearAllAnnotations() {
        annotations.clear();
        packageAnnotations.clear();
        fileMatchAnnotations.clear();
        packageMatchAnnotations.clear();
        packagesTextColor.clear();
        filesTextColor.clear();
        
        // Удаляем из папки рабочих JSON всё, кроме local-description.json
        clearMappingsDirectory();
        
        saveToFile(); // сразу сохраняем в файл
        
        // Принудительно обновляем интерфейс и редакторы
        forceRefreshUIAndEditors();
    }
    
    /**
     * Путь файла относительно корня проекта
     */
    @Nullable
    private String getRelativePath(@NotNull VirtualFile file) {
        String projectPath = project.getBasePath();
        if (projectPath == null) {
            return null;
        }
        
        String filePath = file.getPath();
        
        if (filePath.startsWith(projectPath)) {
            // Обрезаем, только если есть вложенный путь, иначе StringIndexOutOfBoundsException
            if (filePath.length() > projectPath.length()) {
                String relativePath = filePath.substring(projectPath.length());

                return relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;
            } else if (filePath.equals(projectPath)) {
                // Это сам корень проекта
                return "";
            }
        }
        
        return null;
    }
    

    
    /**
     * Экспортирует заметки в формате, совместимом с библиотекой сопоставлений
     */
    public String exportToMappingFormat() {
        Map<String, Object> exportData = new HashMap<>();
        exportData.put("name", "Заметки пользователя");
        exportData.put("version", "1.0.0");
        exportData.put("description", "Пользовательские заметки");
        
        Map<String, Object> mappings = new HashMap<>();
        mappings.put("files", annotations);
        if (!packageAnnotations.isEmpty()) {
            mappings.put("packages", packageAnnotations);
        }
        if (!fileMatchAnnotations.isEmpty()) {
            mappings.put("fileMatch", fileMatchAnnotations);
        }
        if (!packageMatchAnnotations.isEmpty()) {
            mappings.put("packageMatch", packageMatchAnnotations);
        }
        exportData.put("mappings", mappings);
        
        return gson.toJson(exportData);
    }
    
    /**
     * Импортирует заметки из формата библиотеки сопоставлений
     */
    @SuppressWarnings("unchecked")
    public boolean importFromMappingFormat(String jsonContent) {
        try {
            LocalMappingFile mappingFile = gson.fromJson(jsonContent, LocalMappingFile.class);
            if (mappingFile != null && mappingFile.getMappings() != null) {
                LocalMappingFile.Mappings mappings = mappingFile.getMappings();
                boolean hasImported = false;
                
                // Заметки файлов
                Map<String, String> files = mappings.getFiles();
                if (files != null && !files.isEmpty()) {
                    annotations.putAll(files);
                    hasImported = true;
                }
                
                // Заметки пакетов
                Map<String, String> packages = mappings.getPackages();
                if (packages != null && !packages.isEmpty()) {
                    packageAnnotations.putAll(packages);
                    hasImported = true;
                }
                
                // Заметки по шаблонам файлов
                Map<String, String> fileMatchPatterns = mappings.getFileMatch();
                if (fileMatchPatterns != null && !fileMatchPatterns.isEmpty()) {
                    fileMatchAnnotations.putAll(fileMatchPatterns);
                    hasImported = true;
                }
                
                // Заметки по шаблонам пакетов
                Map<String, String> packageMatchPatterns = mappings.getPackageMatch();
                if (packageMatchPatterns != null && !packageMatchPatterns.isEmpty()) {
                    packageMatchAnnotations.putAll(packageMatchPatterns);
                    hasImported = true;
                }
                
                
                if (hasImported) {
                    saveToFile(); // сразу сохраняем в файл
                    return true;
                }
            }
        } catch (JsonSyntaxException e) {
            LOG.warn("Не удалось импортировать заметки: " + e.getMessage());
        }
        return false;
    }
    
    /**
     * Загружает все файлы сопоставлений из папки рабочих JSON.
     * Перед чтением переносит старую папку .td-maps из корня проекта (MappingsPaths.migrateLegacyDir).
     */
    private void loadFromMappingsDirectory() {
        try {
            Path mappingsDir = MappingsPaths.getMappingsDir(project);
            if (mappingsDir != null) {
                // Старая папка .td-maps в корне проекта переезжает в .idea
                MappingsPaths.migrateLegacyDir(project);
                
                // Папки нет — создаём её
                if (!Files.exists(mappingsDir)) {
                    Files.createDirectories(mappingsDir);
                    LOG.info("Создана папка рабочих JSON: " + mappingsDir);
                    return;
                }
                
                // Читаем все JSON-файлы папки
                scanAndLoadMappingFiles(mappingsDir);
                
                // Объединяем все данные
                mergeMappingData();
                
                LOG.info("Сопоставления загружены из папки рабочих JSON");
                
                // После загрузки создаём слушатель JSON-файлов
                ensureJsonFileWatcher();
                
                // После загрузки создаём сервис живого обновления
                ensureMappingsAutoSaver();
            }
        } catch (IOException e) {
            LOG.error("Не удалось загрузить папку сопоставлений: " + e.getMessage(), e);
            
            // Пробуем перенести данные из старого annotations.xml
            tryMigrateFromXml();
        }
    }
    
    /**
     * Читает все JSON-файлы папки рабочих JSON и обновляет allMappingFiles.
     *
     * Разбор — в MappingFileParser: файл, который сейчас не разбирается
     * (пользователь на середине правки), остаётся в последней удачной версии,
     * поэтому заметки не пропадают и не затираются следующим сохранением.
     * Нечитаемый файл обрабатывается так же, как неразобранный.
     *
     * Невалидный JSON — ввод пользователя, а не сбой плагина, поэтому warn:
     * LOG.error в IntelliJ показывает окно внутренней ошибки IDE.
     */
    private void scanAndLoadMappingFiles(Path mappingsDir) throws IOException {
        Map<String, String> contentsByFileName = new LinkedHashMap<>();
        try (Stream<Path> files = Files.walk(mappingsDir, 1)) {
            files.filter(Files::isRegularFile)
                 .filter(path -> path.toString().toLowerCase().endsWith(".json"))
                 .forEach(path -> contentsByFileName.put(path.getFileName().toString(), readOrEmpty(path)));
        }

        MappingFileParser.Result result = MappingFileParser.parseAll(contentsByFileName, allMappingFiles);
        allMappingFiles = new HashMap<>(result.files());
        for (String fileName : result.failedFileNames()) {
            LOG.warn("Файл сопоставлений не разобран, оставлена прошлая версия: " + fileName);
        }
    }

    /** Текст файла; пустая строка, если файл не читается (тогда остаётся прошлая версия). */
    private String readOrEmpty(Path filePath) {
        try {
            return Files.readString(filePath);
        } catch (IOException e) {
            LOG.warn("Не удалось прочитать файл сопоставлений: " + filePath + ", ошибка: " + e.getMessage());
            return "";
        }
    }
    
    /**
     * Сводит данные всех файлов сопоставлений в основные структуры
     */
    private void mergeMappingData() {
        // Очищаем текущие данные
        annotations.clear();
        packageAnnotations.clear();
        fileMatchAnnotations.clear();
        packageMatchAnnotations.clear();
        packagesTextColor.clear();
        filesTextColor.clear();
        
        // Сначала основной файл
        LocalMappingFile localDescription = allMappingFiles.get(MappingsPaths.LOCAL_DESCRIPTION_FILE);
        if (localDescription != null) {
            mergeSingleMappingFile(localDescription);
            
            // Общие настройки берём из основного файла
        }
        
        // Затем остальные файлы
        for (Map.Entry<String, LocalMappingFile> entry : allMappingFiles.entrySet()) {
            if (!MappingsPaths.LOCAL_DESCRIPTION_FILE.equals(entry.getKey())) {
                mergeSingleMappingFile(entry.getValue());
            }
        }
    }
    
    /**
     * Добавляет данные одного файла сопоставлений
     */
    private void mergeSingleMappingFile(LocalMappingFile mappingFile) {
        if (mappingFile != null && mappingFile.getMappings() != null) {
            LocalMappingFile.Mappings mappings = mappingFile.getMappings();
            
            // Сопоставления файлов
            if (mappings.getFiles() != null) {
                annotations.putAll(mappings.getFiles());
            }
            
            // Сопоставления пакетов
            if (mappings.getPackages() != null) {
                packageAnnotations.putAll(mappings.getPackages());
            }
            
            // Шаблоны файлов
            if (mappings.getFileMatch() != null) {
                fileMatchAnnotations.putAll(mappings.getFileMatch());
            }
            
            // Шаблоны пакетов
            if (mappings.getPackageMatch() != null) {
                packageMatchAnnotations.putAll(mappings.getPackageMatch());
            }
            
            // Цвета заметок пакетов
            if (mappings.getPackagesTextColor() != null) {
                packagesTextColor.putAll(mappings.getPackagesTextColor());
            }
            
            // Цвета заметок файлов
            if (mappings.getFilesTextColor() != null) {
                filesTextColor.putAll(mappings.getFilesTextColor());
            }
            
            // Язык; старый код "zh" превращается в "ru"
            if (mappingFile.getLanguage() != null) {
                language = com.github.annotations.utils.LanguageManager.Language.normalizeCode(mappingFile.getLanguage());
            }
        }
    }
    
    /**
     * Пробует перенести данные из старого annotations.xml
     */
    private void tryMigrateFromXml() {
        try {
            String basePath = project.getBasePath();
            if (basePath != null) {
                Path oldXmlPath = Paths.get(basePath, "annotations.xml");
                if (Files.exists(oldXmlPath)) {
                    LOG.info("Найден старый annotations.xml, переносим данные...");
                    
                    String xmlContent = Files.readString(oldXmlPath);
                    
                    // Разбираем XML и переносим данные
                    migrateFromXmlContent(xmlContent);
                    
                    // Сохраняем в новом формате
                    saveToFile();
                    
                    LOG.info("Перенос данных завершён, старый annotations.xml можно удалить");
                }
            }
        } catch (IOException e) {
            LOG.error("Не удалось перенести старые данные: " + e.getMessage(), e);
        }
    }
    
    /**
     * Переносит данные из содержимого XML
     */
    private void migrateFromXmlContent(String xmlContent) {
        try {
            // Очищаем текущие данные
            annotations.clear();
            packageAnnotations.clear();
            fileMatchAnnotations.clear();
            packageMatchAnnotations.clear();
            
            // Разбираем разделы
            parseXmlSectionForMigration(xmlContent, "files", annotations);
            parseXmlSectionForMigration(xmlContent, "packages", packageAnnotations);
            parseXmlSectionForMigration(xmlContent, "fileMatch", fileMatchAnnotations);
            parseXmlSectionForMigration(xmlContent, "packageMatch", packageMatchAnnotations);
            
            
        } catch (Exception e) {
            LOG.error("Не удалось перенести содержимое XML: " + e.getMessage(), e);
        }
    }
    
    /**
     * Разбирает раздел XML (для переноса)
     */
    private void parseXmlSectionForMigration(String xmlContent, String sectionName, Map<String, String> targetMap) {
        try {
            String startTag = "<entry key=\"" + sectionName + "\">";
            String endTag = "</entry>";
            
            int startIndex = xmlContent.indexOf(startTag);
            if (startIndex != -1) {
                int endIndex = xmlContent.indexOf(endTag, startIndex);
                if (endIndex != -1) {
                    String sectionContent = xmlContent.substring(startIndex, endIndex + endTag.length());
                    
                    // Разбираем теги entry
                    String[] lines = sectionContent.split("\n");
                    for (String line : lines) {
                        if (line.contains("<entry key=") && line.contains("value=")) {
                            // Извлекаем key и value
                            int keyStart = line.indexOf("key=\"") + 5;
                            int keyEnd = line.indexOf("\"", keyStart);
                            int valueStart = line.indexOf("value=\"") + 7;
                            int valueEnd = line.indexOf("\"", valueStart);
                            
                            if (keyStart > 4 && keyEnd > keyStart && valueStart > 6 && valueEnd > valueStart) {
                                String key = line.substring(keyStart, keyEnd);
                                String value = line.substring(valueStart, valueEnd);
                                
                                // Раскрываем экранирование XML
                                key = unescapeXml(key);
                                value = unescapeXml(value);
                                
                                targetMap.put(key, value);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("Не удалось разобрать раздел XML " + sectionName + ": " + e.getMessage(), e);
        }
    }
    
    
    
    
    
    

    
    /**
     * Раскрывает экранирование XML (только для переноса)
     */
    private String unescapeXml(String text) {
        if (text == null) return "";
        return text.replace("&amp;", "&")
                  .replace("&lt;", "<")
                  .replace("&gt;", ">")
                  .replace("&quot;", "\"")
                  .replace("&apos;", "'");
    }
    
    /**
     * Слушатель изменений JSON-файлов в папке рабочих JSON
     * Двусторонняя связь: после правки JSON-файла дерево проекта обновляется само
     */
    private void setupFileWatcher() {
        // Отдельный слушатель VFS не нужен: изменения файлов ловит JsonFileWatcher
        // Тело оставлено пустым, чтобы события не обрабатывались дважды
        LOG.info("Слушатель VFS здесь отключён, работает JsonFileWatcher");
    }
    
    /**
     * Перечитывает все файлы сопоставлений из папки рабочих JSON
     */
    private void reloadFromMappingsDirectory() {
        try {
            Path mappingsDir = MappingsPaths.getMappingsDir(project);
            if (mappingsDir != null) {
                if (Files.exists(mappingsDir)) {
                    // Читаем все JSON-файлы
                    scanAndLoadMappingFiles(mappingsDir);
                    
                    // Объединяем данные
                    mergeMappingData();
                    
                    LOG.info("Сопоставления перечитаны из папки рабочих JSON");
                }
            }
        } catch (IOException e) {
            LOG.error("Не удалось перечитать папку сопоставлений: " + e.getMessage(), e);
        }
    }
    
    /**
     * Перечитывает заметки (публичный метод)
     */
    public void reloadAnnotations() {
        reloadFromMappingsDirectory();
        
        // Принудительно обновляем интерфейс, чтобы заметки отобразились
        forceRefreshUI();
    }
    
    /**
     * Удаляет из папки рабочих JSON всё, кроме local-description.json
     */
    private void clearMappingsDirectory() {
        try {
            Path mappingsDir = MappingsPaths.getMappingsDir(project);
            if (mappingsDir != null) {
                if (Files.exists(mappingsDir) && Files.isDirectory(mappingsDir)) {
                    try (Stream<Path> files = Files.list(mappingsDir)) {
                        files.filter(path -> !path.getFileName().toString().equals(MappingsPaths.LOCAL_DESCRIPTION_FILE))
                             .forEach(path -> {
                                 try {
                                     Files.deleteIfExists(path);
                                     LOG.info("Удалён файл: " + path.toString());
                                 } catch (IOException e) {
                                     LOG.warn("Не удалось удалить файл: " + path.toString() + ", ошибка: " + e.getMessage());
                                 }
                             });
                    }
                }
            }
        } catch (IOException e) {
            LOG.error("Не удалось очистить папку рабочих JSON: " + e.getMessage(), e);
        }
    }
    
    /**
     * Принудительно обновляет интерфейс и редакторы
     */
    private void forceRefreshUIAndEditors() {
        try {
            // Обновляем асинхронно, чтобы не блокировать интерфейс
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> {
                try {
                    // Обновляем VFS
                    VFSRefreshService vfsService = new VFSRefreshService(project);
                    String jsonPath = MappingsPaths.getLocalDescriptionPath(project);
                    if (jsonPath != null) {
                        vfsService.refreshJsonFile(jsonPath);
                        vfsService.refreshMappingsDirectory();
                    }
                    
                    // Принудительно обновляем дерево проекта
                    ProjectViewRefreshService refreshService = new ProjectViewRefreshService(project);
                    refreshService.refreshProjectView();
                    refreshService.forceNodeRedecoration();
                    
                    // Принудительно перерисовываем оформление всех узлов
                    com.intellij.ide.projectView.ProjectView projectView = com.intellij.ide.projectView.ProjectView.getInstance(project);
                    if (projectView != null) {
                        projectView.refresh();
                    }
                    
                    // Принудительно обновляем открытые редакторы
                    refreshOpenEditors();
                    
                    LOG.info("Интерфейс и редакторы принудительно обновлены");
                } catch (Exception e) {
                    LOG.warn("Не удалось принудительно обновить интерфейс и редакторы: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            LOG.warn("Не удалось запланировать обновление интерфейса и редакторов: " + e.getMessage());
        }
    }
    
    /**
     * Обновляет открытые редакторы
     */
    private void refreshOpenEditors() {
        try {
            com.intellij.openapi.fileEditor.FileEditorManager editorManager = 
                com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project);
            
            // Все открытые файлы
            VirtualFile[] openFiles = editorManager.getOpenFiles();
            
            for (VirtualFile file : openFiles) {
                // JSON ли это
                if (file.getName().endsWith(".json")) {
                    // Принудительно перечитываем содержимое файла
                    file.refresh(false, false);
                    
                    // Для local-description.json заставляем редактор перезагрузить файл
                    if (file.getName().equals(MappingsPaths.LOCAL_DESCRIPTION_FILE)) {
                        com.intellij.openapi.fileEditor.FileEditor[] editors = editorManager.getEditors(file);
                        for (com.intellij.openapi.fileEditor.FileEditor editor : editors) {
                            if (editor instanceof com.intellij.openapi.fileEditor.TextEditor) {
                                com.intellij.openapi.fileEditor.TextEditor textEditor = 
                                    (com.intellij.openapi.fileEditor.TextEditor) editor;
                                com.intellij.openapi.editor.Document document = textEditor.getEditor().getDocument();
                                
                                // Перезагружаем документ
                                com.intellij.openapi.application.ApplicationManager.getApplication().runWriteAction(() -> {
                                    try {
                                        String content = new String(file.contentsToByteArray(), file.getCharset());
                                        document.setText(content);
                                    } catch (IOException e) {
                                        LOG.warn("Не удалось обновить содержимое редактора: " + e.getMessage());
                                    }
                                });
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("Не удалось обновить открытые редакторы: " + e.getMessage());
        }
    }
    
    /**
     * Принудительно обновляет интерфейс, чтобы заметки отобразились
     */
    private void forceRefreshUI() {
        try {
            // Обновляем асинхронно, чтобы не блокировать интерфейс
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> {
                try {
                    // Обновляем VFS
                    VFSRefreshService vfsService = new VFSRefreshService(project);
                    String jsonPath = MappingsPaths.getLocalDescriptionPath(project);
                    if (jsonPath != null) {
                        vfsService.refreshJsonFile(jsonPath);
                        vfsService.refreshMappingsDirectory();
                    }
                    
                    // Принудительно обновляем дерево проекта
                    ProjectViewRefreshService refreshService = new ProjectViewRefreshService(project);
                    refreshService.refreshProjectView();
                    refreshService.forceNodeRedecoration();
                    
                    // Принудительно перерисовываем оформление всех узлов
                    com.intellij.ide.projectView.ProjectView projectView = com.intellij.ide.projectView.ProjectView.getInstance(project);
                    if (projectView != null) {
                        projectView.refresh();
                    }
                } catch (Exception e) {
                    LOG.warn("Не удалось принудительно обновить интерфейс: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            LOG.warn("Не удалось запланировать обновление интерфейса: " + e.getMessage());
        }
    }
    
    /**
     * Сразу после сохранения обновляет VFS и дерево проекта
     */
    public void refreshAfterSave() {
        try {
            // Обновляем асинхронно, чтобы не блокировать интерфейс
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> {
                try {
                    // Обновляем VFS
                    VFSRefreshService vfsService = new VFSRefreshService(project);
                    String jsonPath = MappingsPaths.getLocalDescriptionPath(project);
                    if (jsonPath != null) {
                        vfsService.refreshJsonFile(jsonPath);
                        vfsService.refreshMappingsDirectory();
                    }
                    
                    // Обновляем дерево проекта
                    ProjectViewRefreshService refreshService = new ProjectViewRefreshService(project);
                    refreshService.refreshProjectView();
                    refreshService.forceNodeRedecoration();
                    
                    LOG.info("Обновление после сохранения выполнено");
                } catch (Exception e) {
                    LOG.warn("Не удалось обновить после сохранения: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            LOG.warn("Не удалось запланировать обновление после сохранения: " + e.getMessage());
        }
    }
    
    /**
     * Показываются ли заметки в дереве проекта
     */
    public boolean isProjectTreeAnnotationsEnabled() {
        return projectTreeAnnotationsEnabled;
    }
    
    /**
     * Включает или выключает показ заметок в дереве проекта
     */
    public void setProjectTreeAnnotationsEnabled(boolean enabled) {
        this.projectTreeAnnotationsEnabled = enabled;
        // Обновляем дерево проекта, чтобы применить изменение
        ProjectView.getInstance(project).refresh();
    }
    
    /**
     * Возвращает язык интерфейса
     */
    public String getLanguage() {
        return language;
    }
    
    /**
     * Задаёт язык интерфейса
     */
    public void setLanguage(String language) {
        this.language = language;
        saveToFile(); // сохраняем в файл
    }
    
    /**
     * Регистрирует слушатель запуска проекта (StartupActivity вместо устаревшего ProjectManagerListener)
     */
    private void registerProjectListener() {
        // Метод заменён PostStartupActivity, внутренний API больше не нужен
        // Язык теперь инициализирует ProjectInitActivity
    }


}

