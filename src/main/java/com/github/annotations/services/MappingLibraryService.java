package com.github.annotations.services;

import com.github.annotations.utils.PathPatterns;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Сервис библиотек сопоставлений уровня приложения
 * Управляет встроенными библиотеками сопоставлений для фреймворков
 */
@Service
public final class MappingLibraryService {
    
    private static final Logger LOG = Logger.getInstance(MappingLibraryService.class);
    private static final Gson gson = new Gson();
    
    private final Map<String, MappingLibrary> libraries = new HashMap<>();
    private boolean initialized = false;
    
    /**
     * Возвращает экземпляр сервиса уровня приложения
     */
    public static MappingLibraryService getInstance() {
        MappingLibraryService service = ApplicationManager.getApplication().getService(MappingLibraryService.class);
        // Инициализируем при первом получении сервиса
        if (!service.initialized) {
            service.initializeDefaultLibraries();
        }
        return service;
    }
    
    /**
     * Включена ли встроенная библиотека сопоставлений
     * Состояние берётся из AnnotationService проекта
     */
    public boolean isBuiltinMappingsEnabled() {
        // Берём состояние из текущего проекта; если проекта нет — по умолчанию true
        try {
            com.intellij.openapi.project.Project[] openProjects = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects();
            if (openProjects.length > 0) {
                com.github.annotations.services.AnnotationService annotationService =
                    com.github.annotations.services.AnnotationService.getInstance(openProjects[0]);
                return annotationService.isBuiltinMappingsEnabled();
            }
        } catch (Exception e) {
            // Не удалось получить — значение по умолчанию
        }
        return true; // по умолчанию включена
    }
    
    /**
     * Включает или выключает встроенную библиотеку сопоставлений
     * Состояние задаётся через AnnotationService проекта
     */
    public void setBuiltinMappingsEnabled(boolean enabled) {
        try {
            com.intellij.openapi.project.Project[] openProjects = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects();
            if (openProjects.length > 0) {
                com.github.annotations.services.AnnotationService annotationService =
                    com.github.annotations.services.AnnotationService.getInstance(openProjects[0]);
                annotationService.setBuiltinMappingsEnabled(enabled);
                
                // Обновляем встроенную библиотеку под новое состояние
                if (!enabled) {
                    clearBuiltinLibraries();
                } else if (initialized) {
                    reloadBuiltinLibraries();
                }
            }
        } catch (Exception e) {
            // Не удалось задать — записываем ошибку
            LOG.error("Не удалось переключить встроенную библиотеку сопоставлений", e);
        }
    }
    
    /**
     * Гарантирует, что сервис инициализирован (проверка дешёвая)
     */
    private void ensureInitialized() {
        if (!initialized) {
            initializeDefaultLibraries();
        }
    }
    
    /**
     * Инициализирует встроенные библиотеки сопоставлений
     */
    public void initializeDefaultLibraries() {
        if (initialized) {
            return;
        }
        
        if (!isBuiltinMappingsEnabled()) {
            initialized = true;
            return;
        }
        
        try {
            loadBuiltinLibrary("spring-boot", "/.td-maps/spring-boot-common.json");
            loadBuiltinLibrary("apache", "/.td-maps/apache-commons.json");
            loadBuiltinLibrary("mybatis", "/.td-maps/mybatis-common.json");
            initialized = true;
        } catch (Exception e) {
            LOG.error("Не удалось инициализировать встроенные библиотеки сопоставлений", e);
        }
    }
    
    /**
     * Очищает встроенную библиотеку сопоставлений
     */
    private void clearBuiltinLibraries() {
        libraries.remove("spring-boot");
        libraries.remove("apache");
        libraries.remove("mybatis");
    }
    
    /**
     * Перезагружает встроенную библиотеку сопоставлений
     */
    private void reloadBuiltinLibraries() {
        if (!isBuiltinMappingsEnabled()) {
            return;
        }
        
        try {
            loadBuiltinLibrary("spring-boot", "/.td-maps/spring-boot-common.json");
            loadBuiltinLibrary("apache", "/.td-maps/apache-commons.json");
            loadBuiltinLibrary("mybatis", "/.td-maps/mybatis-common.json");
        } catch (Exception e) {
            LOG.error("Не удалось перезагрузить встроенную библиотеку сопоставлений", e);
        }
    }
    
    /**
     * Загружает встроенную библиотеку сопоставлений
     */
    private void loadBuiltinLibrary(@NotNull String name, @NotNull String resourcePath) {
        try (InputStream inputStream = getClass().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                return;
            }
            
            InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
            MappingLibrary library = gson.fromJson(reader, MappingLibrary.class);
            
            if (library != null) {
                libraries.put(name, library);
            }
            
        } catch (IOException | JsonSyntaxException e) {
            LOG.error("Не удалось загрузить библиотеку сопоставлений: " + resourcePath, e);
        }
    }
    
    /**
     * Возвращает все доступные библиотеки сопоставлений
     */
    @NotNull
    public Collection<MappingLibrary> getAllLibraries() {
        if (!initialized) {
            initializeDefaultLibraries();
        }
        return new ArrayList<>(libraries.values());
    }
    
    /**
     * Возвращает библиотеку сопоставлений по имени
     */
    @Nullable
    public MappingLibrary getLibrary(@NotNull String name) {
        if (!initialized) {
            initializeDefaultLibraries();
        }
        return libraries.get(name);
    }
    
    /**
     * Ищет сопоставление по имени пакета
     * Производительность: гарантированная инициализация и ранний выход
     */
    @Nullable
    public String searchPackageMapping(@NotNull String packageName) {
        ensureInitialized();
        
        for (MappingLibrary library : libraries.values()) {
            if (library.mappings != null && library.mappings.packages != null) {
                String mapping = library.mappings.packages.get(packageName);
                if (mapping != null) {
                    return mapping;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Ищет сопоставление по имени файла
     * Производительность: гарантированная инициализация и ранний выход
     */
    @Nullable
    public String searchFileMapping(@NotNull String fileName) {
        ensureInitialized();
        
        for (MappingLibrary library : libraries.values()) {
            if (library.mappings != null && library.mappings.files != null) {
                String mapping = library.mappings.files.get(fileName);
                if (mapping != null) {
                    return mapping;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Ищет сопоставление по общему шаблону
     * Производительность: меньше операций со строками и ранний выход
     */
    @Nullable
    public String searchPatternMapping(@NotNull String pattern) {
        ensureInitialized();
        
        for (MappingLibrary library : libraries.values()) {
            if (library.mappings != null && library.mappings.packageMatch != null) {
                for (Map.Entry<String, String> entry : library.mappings.packageMatch.entrySet()) {
                    // Точное сопоставление, как у пользовательских сопоставлений
                    if (PathPatterns.matchesPackage(pattern, entry.getKey())) {
                        return entry.getValue();
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * Ищет сопоставление по шаблону файла
     * Производительность: меньше операций со строками и ранний выход
     */
    @Nullable
    public String searchFileMatchMapping(@NotNull String fileName) {
        ensureInitialized();
        
        for (MappingLibrary library : libraries.values()) {
            if (library.mappings != null && library.mappings.fileMatch != null) {
                for (Map.Entry<String, String> entry : library.mappings.fileMatch.entrySet()) {
                    // Точное сопоставление, как у пользовательских сопоставлений
                    if (PathPatterns.matchesFile(fileName, entry.getKey(), null)) {
                        return entry.getValue();
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * Умный поиск сопоставления
     * Порядок: точное имя пакета -> точное имя файла -> шаблон
     */
    @Nullable
    public String smartSearch(@NotNull String name) {
        // 1. Точное имя пакета
        String packageMapping = searchPackageMapping(name);
        if (packageMapping != null) {
            return packageMapping;
        }
        
        // 2. Точное имя файла
        String fileMapping = searchFileMapping(name);
        if (fileMapping != null) {
            return fileMapping;
        }
        
        // 3. Шаблон
        return searchPatternMapping(name);
    }
    
    /**
     * Все сопоставления одним словарём — для поиска
     * Возвращает словарь «путь файла -> описание»
     * packageMatch не входит: это шаблоны имён пакетов, а не реальные файлы
     */
    @NotNull
    public Map<String, String> getAllMappings() {
        if (!initialized) {
            initializeDefaultLibraries();
        }
        
        Map<String, String> allMappings = new HashMap<>();
        
        for (MappingLibrary library : libraries.values()) {
            if (library.mappings != null) {
                // Сопоставления пакетов
                if (library.mappings.packages != null) {
                    allMappings.putAll(library.mappings.packages);
                }
                
                // Сопоставления файлов
                if (library.mappings.files != null) {
                    allMappings.putAll(library.mappings.files);
                }
                
                // packageMatch не добавляем: это лишь шаблоны имён пакетов
                // Например, controller -> "Слой контроллеров" нужен только для показа у пакета, не для поиска
            }
        }
        
        return allMappings;
    }
    
    /**
     * Все шаблоны пакетов — только для показа у имён пакетов
     * Возвращает словарь «шаблон -> описание»
     */
    @NotNull
    public Map<String, String> getAllPackageMatchPatterns() {
        if (!initialized) {
            initializeDefaultLibraries();
        }
        
        Map<String, String> allPatterns = new HashMap<>();
        
        for (MappingLibrary library : libraries.values()) {
            if (library.mappings != null && library.mappings.packageMatch != null) {
                allPatterns.putAll(library.mappings.packageMatch);
            }
        }
        
        return allPatterns;
    }
    
    /**
     * Все шаблоны файлов — только для показа у имён файлов
     * Возвращает словарь «шаблон -> описание»
     */
    @NotNull
    public Map<String, String> getAllFileMatchPatterns() {
        if (!initialized) {
            initializeDefaultLibraries();
        }
        
        Map<String, String> allPatterns = new HashMap<>();
        
        for (MappingLibrary library : libraries.values()) {
            if (library.mappings != null && library.mappings.fileMatch != null) {
                allPatterns.putAll(library.mappings.fileMatch);
            }
        }
        
        return allPatterns;
    }
    
    /**
     * Загружает пользовательскую библиотеку сопоставлений из JSON-строки
     */
    public boolean loadCustomLibrary(@NotNull String name, @NotNull String jsonContent) {
        try {
            // Пробуем разобрать как библиотеку сопоставлений
            MappingLibrary library = gson.fromJson(jsonContent, MappingLibrary.class);
            if (library != null) {
                // Есть ли в библиотеке полезное содержимое
                boolean hasContent = false;
                
                if (library.mappings != null) {
                    int packageCount = (library.mappings.packages != null) ? library.mappings.packages.size() : 0;
                    int fileCount = (library.mappings.files != null) ? library.mappings.files.size() : 0;
                    int patternCount = (library.mappings.packageMatch != null) ? library.mappings.packageMatch.size() : 0;
                    
                    hasContent = (packageCount + fileCount + patternCount) > 0;
                    
                    LOG.info("Импорт библиотеки сопоставлений: " + (library.name != null ? library.name : name));
                    LOG.info("- сопоставлений пакетов: " + packageCount);
                    LOG.info("- сопоставлений файлов: " + fileCount);
                    LOG.info("- общих шаблонов: " + patternCount);
                }
                
                if (!hasContent) {
                    LOG.warn("Внимание: в импортированной библиотеке сопоставлений нет полезного содержимого");
                    return false;
                }
                
                // Если имя не задано — берём переданное
                if (library.name == null || library.name.isEmpty()) {
                    library.name = name;
                }
                
                // Сохраняем в набор библиотек
                libraries.put(name, library);
                LOG.info("Пользовательская библиотека сопоставлений загружена: " + library.name);
                return true;
            } else {
                LOG.warn("Ошибка: после разбора библиотека сопоставлений равна null");
            }
        } catch (JsonSyntaxException e) {
            LOG.error("Не удалось разобрать пользовательскую библиотеку сопоставлений", e);
        }
        return false;
    }
    
    /**
     * Структура библиотеки сопоставлений
     */
    public static class MappingLibrary {
        public String name;
        public String version;
        public String description;
        public String author;
        public Mappings mappings;
        public List<String> tags;
        public String lastUpdated;
        
        public static class Mappings {
            public Map<String, String> packages;      // сопоставления пакетов: org.springframework.boot -> "Фреймворк Spring Boot"
            public Map<String, String> files;         // сопоставления файлов: pom.xml -> "Файл настроек проекта Maven"
            public Map<String, String> packageMatch; // шаблоны пакетов: controller -> "Слой контроллеров" (только для показа у пакетов, не для поиска)
            public Map<String, String> fileMatch;    // шаблоны файлов: Service -> "Слой бизнес-логики" (только для показа у файлов, не для поиска)
        }
    }
    
    

}

