package com.github.annotations.services;

import com.github.annotations.utils.MappingEvents;
import com.github.annotations.utils.MappingsPaths;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Одно перечитывание на пакет событий, включая удаление и перемещение. */
public class JsonFileWatcher implements BulkFileListener {
    private static final Logger LOG = Logger.getInstance(JsonFileWatcher.class);
    private final Project project;
    private final AnnotationService annotationService;
    private final MessageBusConnection connection;
    private final AtomicBoolean queued = new AtomicBoolean();
    private volatile boolean disposed;
    private boolean affectedBefore;

    public JsonFileWatcher(Project project, AnnotationService annotationService) {
        this.project = project;
        this.annotationService = annotationService;
        connection = project.getMessageBus().connect();
        connection.subscribe(VirtualFileManager.VFS_CHANGES, this);
    }

    private boolean affects(List<? extends VFileEvent> events) {
        String directory = MappingsPaths.getMappingsDirVfsPath(project);
        return events.stream().anyMatch(event -> MappingEvents.affects(directory, event.getPath())
                || (event.getFile() != null && MappingEvents.affects(directory, event.getFile().getPath())));
    }

    @Override
    public void before(@NotNull List<? extends VFileEvent> events) {
        affectedBefore = affects(events);
    }

    @Override
    public void after(@NotNull List<? extends VFileEvent> events) {
        boolean changed = affectedBefore || affects(events);
        affectedBefore = false;
        if (!changed || disposed || !queued.compareAndSet(false, true)) return;
        ApplicationManager.getApplication().invokeLater(() -> {
            queued.set(false);
            if (disposed || project.isDisposed()) return;
            try {
                annotationService.reloadAnnotations();
            } catch (RuntimeException e) {
                LOG.warn("Не удалось перечитать заметки после изменения файлов", e);
            }
        }, project.getDisposed());
    }

    public void dispose() {
        disposed = true;
        connection.disconnect();
    }
}
