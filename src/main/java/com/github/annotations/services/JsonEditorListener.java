package com.github.annotations.services;

import com.github.annotations.utils.MappingsPaths;
import com.github.annotations.model.LocalMappingFile;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Следит за изменениями в редакторе JSON и сразу обновляет заметки
 */
public class JsonEditorListener {
    private static final Logger LOG = Logger.getInstance(JsonEditorListener.class);
    
    private final Project project;
    private final AnnotationService annotationService;
    private final ProjectViewRefreshService refreshService;
    private final MessageBusConnection messageBusConnection;
    private final Map<VirtualFile, DocumentListener> documentListeners;
    private final Timer debounceTimer;
    private final Gson gson;
    
    public JsonEditorListener(Project project, AnnotationService annotationService) {
        this.project = project;
        this.annotationService = annotationService;
        this.refreshService = new ProjectViewRefreshService(project);
        this.messageBusConnection = project.getMessageBus().connect();
        this.documentListeners = new HashMap<>();
        this.gson = new Gson();
        
        // Таймер против дребезга: обновление через 50 мс (было 200 мс, уменьшено ради отзывчивости)
        this.debounceTimer = new Timer(50, e -> {
            performUpdate();
        });
        this.debounceTimer.setRepeats(false);
        
        // Проверяем уже открытые JSON-файлы
        checkAndRegisterOpenFiles();
        
        LOG.info("JsonEditorListener инициализирован");
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
     * Регистрирует новый открытый файл (вызывается извне)
     */
    public void registerFileIfNeeded(VirtualFile file) {
        if (isOurJsonFile(file) && !documentListeners.containsKey(file)) {
            registerDocumentListener(file);
        }
    }
    
    /**
     * Снимает закрытый файл (вызывается извне)
     */
    public void unregisterFileIfNeeded(VirtualFile file) {
        if (isOurJsonFile(file) && documentListeners.containsKey(file)) {
            removeDocumentListener(file);
        }
    }
    
    /**
     * Регистрирует слушатель документа для JSON-файла
     */
    private void registerDocumentListener(VirtualFile file) {
        if (documentListeners.containsKey(file)) {
            return; // уже зарегистрирован
        }
        
        FileEditor[] editors = FileEditorManager.getInstance(project).getEditors(file);
        for (FileEditor editor : editors) {
            if (editor instanceof TextEditor) {
                TextEditor textEditor = (TextEditor) editor;
                Editor editorComponent = textEditor.getEditor();
                Document document = editorComponent.getDocument();
                
                DocumentListener listener = new DocumentListener() {
                    @Override
                    public void documentChanged(@NotNull DocumentEvent event) {
                        scheduleUpdate();
                    }
                };
                
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
    private void removeDocumentListener(VirtualFile file) {
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
     * Планирует обновление (с защитой от дребезга)
     */
    private void scheduleUpdate() {
        debounceTimer.restart();
    }
    
    /**
     * Выполняет само обновление
     */
    private void performUpdate() {
        try {
            // Текущий открытый JSON-файл
            VirtualFile jsonFile = findOpenJsonFile();
            if (jsonFile == null) {
                return;
            }
            
            // Содержимое файла
            String jsonContent = getFileContent(jsonFile);
            if (jsonContent == null || jsonContent.trim().isEmpty()) {
                return;
            }
            
            // Разбираем JSON
            LocalMappingFile mapping = parseJsonContent(jsonContent);
            if (mapping != null) {
                // Обновляем заметки в памяти
                updateAnnotationsFromMapping(mapping);
                
                // Обновляем дерево проекта
                refreshService.refreshProjectView();
                
                LOG.info("Заметки обновлены на лету");
            }
        } catch (Exception e) {
            LOG.warn("Ошибка при обновлении заметок на лету: " + e.getMessage());
        }
    }
    
    /**
     * Ищет открытый JSON-файл
     */
    private VirtualFile findOpenJsonFile() {
        FileEditor[] editors = FileEditorManager.getInstance(project).getAllEditors();
        for (FileEditor editor : editors) {
            if (editor instanceof TextEditor) {
                VirtualFile file = editor.getFile();
                if (isOurJsonFile(file)) {
                    return file;
                }
            }
        }
        return null;
    }
    
    /**
     * Читает содержимое файла
     */
    private String getFileContent(VirtualFile file) {
        try {
            Document document = FileDocumentManager.getInstance().getDocument(file);
            if (document != null) {
                return document.getText();
            }
        } catch (Exception e) {
            LOG.warn("Не удалось прочитать файл: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Разбирает JSON
     */
    private LocalMappingFile parseJsonContent(String jsonContent) {
        try {
            return gson.fromJson(jsonContent, LocalMappingFile.class);
        } catch (JsonSyntaxException e) {
            // При ошибке формата JSON ничего не обновляем, старые заметки остаются
            // Ошибка формата JSON — обновление на лету пропускаем
            return null;
        }
    }
    
    /**
     * Обновляет заметки из файла сопоставлений
     */
    private void updateAnnotationsFromMapping(LocalMappingFile mapping) {
        if (mapping == null || mapping.getMappings() == null) {
            return;
        }
        
        LocalMappingFile.Mappings mappings = mapping.getMappings();
        
        // Заметки файлов
        if (mappings.getFiles() != null) {
            annotationService.setAnnotations(mappings.getFiles());
        }
        
        // Заметки пакетов
        if (mappings.getPackages() != null) {
            annotationService.setPackageAnnotations(mappings.getPackages());
        }
        
        // Цвета текста файлов и пакетов задаются по одному
        if (mappings.getFilesTextColor() != null) {
            for (Map.Entry<String, String> entry : mappings.getFilesTextColor().entrySet()) {
                annotationService.setFileTextColor(entry.getKey(), entry.getValue());
            }
        }
        
        if (mappings.getPackagesTextColor() != null) {
            for (Map.Entry<String, String> entry : mappings.getPackagesTextColor().entrySet()) {
                annotationService.setPackageTextColor(entry.getKey(), entry.getValue());
            }
        }
    }
    
    /**
     * Освобождает ресурсы
     */
    public void dispose() {
        // Снимаем все слушатели документов
        for (Map.Entry<VirtualFile, DocumentListener> entry : documentListeners.entrySet()) {
            VirtualFile file = entry.getKey();
            DocumentListener listener = entry.getValue();
            removeDocumentListener(file);
        }
        documentListeners.clear();
        
        // Останавливаем таймер
        if (debounceTimer != null) {
            debounceTimer.stop();
        }
        
        // Отключаемся от шины сообщений
        if (messageBusConnection != null) {
            messageBusConnection.disconnect();
        }
        
        LOG.info("JsonEditorListener освободил ресурсы");
    }
}
