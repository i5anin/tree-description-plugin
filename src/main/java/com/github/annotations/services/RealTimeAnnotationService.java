package com.github.annotations.services;

import com.github.annotations.utils.MappingsPaths;
import com.github.annotations.model.LocalMappingFile;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import java.io.IOException;
import java.util.List;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Сервис живого обновления заметок на основе DocumentListener
 * Заметки обновляются прямо во время правки, без сохранения файла
 */
public class RealTimeAnnotationService {
    private static final Logger LOG = Logger.getInstance(RealTimeAnnotationService.class);
    
    private final Project project;
    private final ProjectViewRefreshService treeRefreshService;
    
    // Кэш в памяти для живого обновления — на нём всё держится
    private final Map<String, String> liveFileAnnotations = new HashMap<>();
    private final Map<String, String> livePackageAnnotations = new HashMap<>();
    private final Map<String, String> liveFileTextColors = new HashMap<>();
    private final Map<String, String> livePackageTextColors = new HashMap<>();
    
    // Фоновое сохранение
    private ScheduledFuture<?> saveTask;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean isSavingInProgress = false;
    
    // Слушатели Document по файлам
    private final Map<VirtualFile, DocumentListener> documentListeners = new HashMap<>();
    
    // Слушатель VFS
    private MessageBusConnection messageBusConnection;
    
    public RealTimeAnnotationService(Project project) {
        this.project = project;
        this.treeRefreshService = new ProjectViewRefreshService(project);
        
        // Проверяем уже открытые JSON-файлы
        checkAndRegisterOpenFiles();
        
        // Слушатель VFS для изменений файлов извне
        setupVfsFileWatcher();
        
        LOG.info("RealTimeAnnotationService инициализирован");
    }
    
    /**
     * Подключает слушатель VFS для внешних изменений рабочих JSON-файлов (MappingsPaths)
     */
    private void setupVfsFileWatcher() {
        messageBusConnection = project.getMessageBus().connect();
        messageBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
            @Override
            public void after(@NotNull List<? extends VFileEvent> events) {
                for (VFileEvent event : events) {
                    VirtualFile eventFile = event.getFile();
                    String fileName = eventFile != null ? eventFile.getName() : "null";
                    LOG.info("Событие VFS: " + event.getClass().getSimpleName() + " - " + fileName);
                    VirtualFile file = event.getFile();
                    // Интересуют только правки содержимого рабочих JSON плагина
                    if (!(event instanceof VFileContentChangeEvent) || !MappingsPaths.isMappingsJson(project, file)) {
                        continue;
                    }
                    LOG.info("Изменён рабочий JSON-файл: " + file.getName());
                    // Обработка с задержкой, чтобы не обновлять слишком часто
                    ApplicationManager.getApplication().invokeLater(() -> {
                        try {
                            // Перечитываем файл в кэш памяти
                            reloadFromFile(file);
                            // Обновляем дерево проекта
                            treeRefreshService.refreshProjectView();
                            LOG.info("Файл изменён извне, заметки обновлены: " + file.getName());
                        } catch (Exception e) {
                            LOG.error("Не удалось обновить заметки: " + e.getMessage(), e);
                        }
                    });
                }
            }
        });
    }
    
    /**
     * Перечитывает файл в кэш памяти
     */
    private void reloadFromFile(VirtualFile file) {
        try {
            String content = new String(file.contentsToByteArray());
            updateLiveCacheImmediately(content);
        } catch (IOException e) {
            LOG.error("Не удалось перечитать файл: " + e.getMessage(), e);
        }
    }
    
    /**
     * Находит и регистрирует уже открытые JSON-файлы
     */
    private void checkAndRegisterOpenFiles() {
        FileEditor[] editors = FileEditorManager.getInstance(project).getAllEditors();
        for (FileEditor editor : editors) {
            if (editor instanceof TextEditor) {
                VirtualFile file = editor.getFile();
                if (isOurJsonFile(file)) {
                    registerDocumentListener(file);
                }
            }
        }
    }
    
    /**
     * Регистрирует слушатель документа для JSON-файла
     */
    public void registerDocumentListener(VirtualFile file) {
        if (documentListeners.containsKey(file)) {
            return; // уже зарегистрирован
        }
        
        FileEditor[] editors = FileEditorManager.getInstance(project).getEditors(file);
        for (FileEditor editor : editors) {
            if (editor instanceof TextEditor) {
                TextEditor textEditor = (TextEditor) editor;
                Document document = textEditor.getEditor().getDocument();
                
                DocumentListener listener = new JsonDocumentListener();
                document.addDocumentListener(listener);
                documentListeners.put(file, listener);
                
                LOG.info("Слушатель документа зарегистрирован для файла " + file.getName());
                break;
            }
        }
    }
    
    /**
     * Снимает слушатель документа с файла
     */
    public void unregisterDocumentListener(VirtualFile file) {
        DocumentListener listener = documentListeners.remove(file);
        if (listener != null) {
            FileEditor[] editors = FileEditorManager.getInstance(project).getEditors(file);
            for (FileEditor editor : editors) {
                if (editor instanceof TextEditor) {
                    TextEditor textEditor = (TextEditor) editor;
                    Document document = textEditor.getEditor().getDocument();
                    document.removeDocumentListener(listener);
                    break;
                }
            }
            LOG.info("Слушатель документа снят с файла " + file.getName());
        }
    }
    
    /**
     * JSON-файл ли это в папке рабочих JSON плагина (MappingsPaths)
     */
    private boolean isOurJsonFile(VirtualFile file) {
        return MappingsPaths.isMappingsJson(project, file);
    }
    
    /**
     * Слушатель Document — срабатывает на каждый введённый символ
     */
    public class JsonDocumentListener implements DocumentListener {
        @Override
        public void documentChanged(@NotNull DocumentEvent event) {
            // Срабатывает сразу во время правки, сохранять файл не нужно
            Document document = event.getDocument();
            
            if (isOurJsonFile(document)) {
                // Сразу берём свежее содержимое из редактора
                String currentContent = document.getText();
                
                // Сразу обновляем кэш в памяти
                updateLiveCacheImmediately(currentContent);
                
                // Сразу обновляем дерево проекта
                refreshProjectTreeImmediately();
                
                // Фоновое асинхронное сохранение (пользователь его не замечает)
                scheduleBackgroundSave(document);
            }
        }
    }
    
    /**
     * Сразу обновляет кэш в памяти — основной метод
     */
    private void updateLiveCacheImmediately(String jsonContent) {
        try {
            // Разбираем JSON
            LocalMappingFile mapping = parseJsonContent(jsonContent);
            
            if (mapping != null && mapping.getMappings() != null) {
                LocalMappingFile.Mappings mappings = mapping.getMappings();
                
                // Сразу обновляем кэш в памяти
                liveFileAnnotations.clear();
                livePackageAnnotations.clear();
                liveFileTextColors.clear();
                livePackageTextColors.clear();
                
                if (mappings.getFiles() != null) {
                    liveFileAnnotations.putAll(mappings.getFiles());
                }
                if (mappings.getPackages() != null) {
                    livePackageAnnotations.putAll(mappings.getPackages());
                }
                if (mappings.getFilesTextColor() != null) {
                    liveFileTextColors.putAll(mappings.getFilesTextColor());
                }
                if (mappings.getPackagesTextColor() != null) {
                    livePackageTextColors.putAll(mappings.getPackagesTextColor());
                }
                
                LOG.info("Кэш в памяти обновлён: заметок файлов " + liveFileAnnotations.size() + ", "
                        + "заметок пакетов " + livePackageAnnotations.size());
            }
        } catch (Exception e) {
            // При ошибке формата JSON старый кэш остаётся, показ не меняется
            LOG.warn("Ошибка формата JSON, показ не изменён: " + e.getMessage());
        }
    }
    
    /**
     * Сразу обновляет дерево проекта
     */
    private void refreshProjectTreeImmediately() {
        // Обновление интерфейса — сразу в потоке EDT
        ApplicationManager.getApplication().invokeLater(() -> {
            treeRefreshService.refreshProjectView();
            LOG.info("Дерево проекта обновлено");
        });
    }
    
    /**
     * Возвращает заметку файла — вызывается деревом проекта для показа
     */
    public String getFileAnnotation(String filePath) {
        // Отдаём прямо из кэша в памяти, это быстро
        String annotation = liveFileAnnotations.get(normalizeFilePath(filePath));
        
        if (annotation != null) {

        }
        
        return annotation;
    }
    
    /**
     * Возвращает заметку пакета — вызывается деревом проекта для показа
     */
    public String getPackageAnnotation(String packagePath) {
        // Отдаём прямо из кэша в памяти, это быстро
        String annotation = livePackageAnnotations.get(normalizeFilePath(packagePath));
        
        if (annotation != null) {

        }
        
        return annotation;
    }
    
    /**
     * Возвращает цвет текста файла
     */
    public String getFileTextColor(String filePath) {
        return liveFileTextColors.get(normalizeFilePath(filePath));
    }
    
    /**
     * Возвращает цвет текста пакета
     */
    public String getPackageTextColor(String packagePath) {
        return livePackageTextColors.get(normalizeFilePath(packagePath));
    }
    
    /**
     * Фоновое асинхронное сохранение — пользователь его не замечает
     */
    private void scheduleBackgroundSave(Document document) {
        // Отменяем предыдущее сохранение (защита от дребезга)
        if (saveTask != null) {
            saveTask.cancel(false);
        }
        
        // Пишем на диск с задержкой, чтобы не было частых операций ввода-вывода и конфликтов
        saveTask = scheduler.schedule(() -> {
            saveToFileInBackground(document);
        }, 50, TimeUnit.MILLISECONDS); // было 200 мс, уменьшено до 50 мс ради отзывчивости
    }
    
    /**
     * Фоновое сохранение в файл через безопасный API
     */
    private void saveToFileInBackground(Document document) {
        // Если сохранение уже идёт, второе не запускаем
        if (isSavingInProgress) {

            return;
        }
        
        ApplicationManager.getApplication().invokeLater(() -> {
            WriteCommandAction.runWriteCommandAction(project, () -> {
                try {
                    isSavingInProgress = true;
                    
                    // Сохраняем через Document API, чтобы не было конфликтов с VFS
                    FileDocumentManager.getInstance().saveDocument(document);
                    LOG.info("Файл сохранён в фоне");
                } catch (Exception e) {
                    LOG.warn("Фоновое сохранение не удалось, на показ это не влияет: " + e.getMessage());
                } finally {
                    isSavingInProgress = false;
                }
            });
        });
    }
    
    /**
     * Разбирает JSON
     */
    private LocalMappingFile parseJsonContent(String jsonContent) {
        try {
            Gson gson = new Gson();
            return gson.fromJson(jsonContent, LocalMappingFile.class);
        } catch (JsonSyntaxException e) {
            LOG.warn("Не удалось разобрать JSON: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Наш ли это JSON-файл
     */
    private boolean isOurJsonFile(Document document) {
        VirtualFile file = FileDocumentManager.getInstance().getFile(document);
        return file != null && isOurJsonFile(file);
    }
    
    /**
     * Приводит путь файла к единому виду
     */
    private String normalizeFilePath(String path) {
        return path.replace('\\', '/');
    }
    
    /**
     * Освобождает ресурсы
     */
    public void dispose() {
        // Снимаем слушатель VFS
        if (messageBusConnection != null) {
            messageBusConnection.disconnect();
        }
        
        // Снимаем все слушатели документов
        for (Map.Entry<VirtualFile, DocumentListener> entry : documentListeners.entrySet()) {
            VirtualFile file = entry.getKey();
            DocumentListener listener = entry.getValue();
            unregisterDocumentListener(file);
        }
        documentListeners.clear();
        
        // Отменяем задачу сохранения
        if (saveTask != null) {
            saveTask.cancel(false);
        }
        
        // Останавливаем планировщик
        if (scheduler != null) {
            scheduler.shutdown();
        }
        
        LOG.info("RealTimeAnnotationService освободил ресурсы");
    }
}
