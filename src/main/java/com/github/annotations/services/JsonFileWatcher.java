package com.github.annotations.services;

import com.github.annotations.utils.MappingsPaths;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Слушатель JSON-файлов
 * Следит за правками рабочих JSON-файлов плагина (MappingsPaths) внутри IDE и сразу обновляет заметки
 */
public class JsonFileWatcher implements BulkFileListener {
    
    
    private final Project project;
    private final AnnotationService annotationService;
    private final ProjectViewRefreshService refreshService;
    private final MessageBusConnection messageBusConnection;
    
    public JsonFileWatcher(Project project, AnnotationService annotationService) {
        this.project = project;
        this.annotationService = annotationService; // берём переданную ссылку, чтобы не было циклической зависимости
        this.refreshService = new ProjectViewRefreshService(project);
        
        // Подключаемся к шине сообщений
        this.messageBusConnection = project.getMessageBus().connect();
        this.messageBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, this);
        

        
        // Записываем успешную инициализацию
        com.intellij.openapi.diagnostic.Logger.getInstance(JsonFileWatcher.class)
            .info("JsonFileWatcher инициализирован и подключён к VFS");
    }
    
    /**
     * Освобождает ресурсы
     */
    public void dispose() {
        if (messageBusConnection != null) {
            messageBusConnection.disconnect();
        }
        

    }
    
    /**
     * JSON-файл ли это в папке рабочих JSON плагина (MappingsPaths)
     */
    private boolean isOurJsonFile(@NotNull VirtualFile file) {
        return file.exists() && MappingsPaths.isMappingsJson(project, file);
    }
    
    /**
     * Обрабатывает изменения файлов: создание, правку, удаление
     */
    @Override
    public void after(@NotNull List<? extends VFileEvent> events) {
        for (VFileEvent event : events) {
            VirtualFile file = event.getFile();
            if (isOurJsonFile(file)) {
                // Записываем тип изменения
                com.intellij.openapi.diagnostic.Logger.getInstance(JsonFileWatcher.class)
                    .info("BulkFileListener: файл изменён: " + event.getClass().getSimpleName() + " - " + file.getPath());
                
                // Обработка с задержкой, чтобы не обновлять слишком часто
                com.intellij.openapi.application.ApplicationManager.getApplication()
                    .invokeLater(() -> handleJsonFileChanged(file));
            }
        }
    }
    
    /**
     * Обрабатывает изменение JSON-файла
     */
    private void handleJsonFileChanged(@NotNull VirtualFile file) {
        try {
            // Перечитываем заметки
            annotationService.reloadAnnotations();
            
            // Обновляем дерево проекта
            refreshService.refreshProjectView();
        } catch (Exception e) {
            com.intellij.openapi.diagnostic.Logger.getInstance(JsonFileWatcher.class)
                .warn("Не удалось обработать изменение JSON-файла: " + e.getMessage());
        }
    }
    

}
