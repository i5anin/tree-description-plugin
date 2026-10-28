package com.github.annotations.services;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

/**
 * Слушатель редактора файлов
 * Следит за открытием и закрытием файлов, подключает и снимает слушатель Document
 */
public class FileEditorListener {
    
    private static final String MAPPINGS_DIR_NAME = ".td-maps";
    
    private final Project project;
    private final RealTimeAnnotationService realTimeAnnotationService;
    private final MessageBusConnection messageBusConnection;
    
    public FileEditorListener(Project project, RealTimeAnnotationService realTimeAnnotationService) {
        this.project = project;
        this.realTimeAnnotationService = realTimeAnnotationService;
        this.messageBusConnection = project.getMessageBus().connect();
        
        // Регистрируем слушатель редактора файлов
        registerFileEditorListener();
    }
    
    /**
     * Регистрирует слушатель редактора файлов
     */
    private void registerFileEditorListener() {
        messageBusConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
            @Override
            public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
                // При открытии файла проверяем, наш ли это JSON
                if (isOurJsonFile(file)) {
                    realTimeAnnotationService.registerDocumentListener(file);
                }
            }
            
            @Override
            public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
                // При закрытии файла снимаем слушатель
                if (isOurJsonFile(file)) {
                    realTimeAnnotationService.unregisterDocumentListener(file);
                }
            }
        });
    }
    
    /**
     * JSON-файл ли это в папке .td-maps
     */
    private boolean isOurJsonFile(VirtualFile file) {
        if (file == null || !file.exists() || !file.getName().endsWith(".json")) {
            return false;
        }
        
        String projectBasePath = project.getBasePath();
        if (projectBasePath == null) {
            return false;
        }
        
        String mappingsPath = projectBasePath + "/" + MAPPINGS_DIR_NAME;
        return file.getPath().startsWith(mappingsPath);
    }
    
    /**
     * Освобождает ресурсы
     */
    public void dispose() {
        if (messageBusConnection != null) {
            messageBusConnection.disconnect();
        }
    }
}
