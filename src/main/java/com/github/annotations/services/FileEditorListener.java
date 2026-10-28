package com.github.annotations.services;

import com.github.annotations.utils.MappingsPaths;
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
    
    
    private final Project project;
    private final MappingsAutoSaver mappingsAutoSaver;
    private final MessageBusConnection messageBusConnection;
    
    public FileEditorListener(Project project, MappingsAutoSaver mappingsAutoSaver) {
        this.project = project;
        this.mappingsAutoSaver = mappingsAutoSaver;
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
                    mappingsAutoSaver.registerDocumentListener(file);
                }
            }
            
            @Override
            public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
                // При закрытии файла снимаем слушатель
                if (isOurJsonFile(file)) {
                    mappingsAutoSaver.unregisterDocumentListener(file);
                }
            }
        });
    }
    
    /**
     * JSON-файл ли это в папке рабочих JSON плагина (MappingsPaths)
     */
    private boolean isOurJsonFile(VirtualFile file) {
        return file != null && file.exists() && MappingsPaths.isMappingsJson(project, file);
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
