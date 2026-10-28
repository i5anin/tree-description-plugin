package com.github.annotations.services;

import com.github.annotations.utils.MappingsPaths;
import com.intellij.openapi.application.ApplicationManager;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Автосохранение открытых в редакторе рабочих JSON плагина.
 *
 * Зачем: дерево проекта показывает заметки из AnnotationService, а тот
 * перечитывает файлы только после записи на диск (JsonFileWatcher ловит событие VFS).
 * Чтобы заметки в дереве менялись прямо во время правки JSON, документ
 * сохраняется через AUTO_SAVE_DELAY_MS после последнего нажатия клавиши.
 *
 * Цепочка: правка документа -> MappingsAutoSaver сохраняет -> событие VFS ->
 * JsonFileWatcher -> AnnotationService.reloadAnnotations -> дерево обновлено.
 * Промежуточный невалидный JSON безопасен: MappingFileParser оставляет
 * последнюю удачную версию файла.
 *
 * Документы подключает FileEditorListener (открытие/закрытие файла),
 * уже открытые — конструктор. Создаётся и освобождается в AnnotationService.
 */
public class MappingsAutoSaver {
    private static final Logger LOG = Logger.getInstance(MappingsAutoSaver.class);

    /** Пауза после последней правки перед сохранением. */
    private static final long AUTO_SAVE_DELAY_MS = 50;

    private final Project project;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Map<VirtualFile, ListenerOnDocument> listeners = new HashMap<>();
    private ScheduledFuture<?> pendingSave;

    /** Слушатель вместе с документом, на который он повешен, — чтобы снять его без поиска редактора. */
    private record ListenerOnDocument(@NotNull Document document, @NotNull DocumentListener listener) {
    }

    public MappingsAutoSaver(@NotNull Project project) {
        this.project = project;
        for (FileEditor editor : FileEditorManager.getInstance(project).getAllEditors()) {
            VirtualFile file = editor.getFile();
            if (editor instanceof TextEditor && MappingsPaths.isMappingsJson(project, file)) {
                registerDocumentListener(file);
            }
        }
    }

    /** Начинает следить за документом открытого файла; повторный вызов ничего не делает. */
    public void registerDocumentListener(@NotNull VirtualFile file) {
        if (listeners.containsKey(file)) {
            return;
        }
        Document document = findDocument(file);
        if (document == null) {
            return;
        }
        DocumentListener listener = new DocumentListener() {
            @Override
            public void documentChanged(@NotNull DocumentEvent event) {
                scheduleSave(event.getDocument());
            }
        };
        document.addDocumentListener(listener);
        listeners.put(file, new ListenerOnDocument(document, listener));
    }

    /** Перестаёт следить за документом файла (файл закрыт). */
    public void unregisterDocumentListener(@NotNull VirtualFile file) {
        ListenerOnDocument registered = listeners.remove(file);
        if (registered != null) {
            registered.document().removeDocumentListener(registered.listener());
        }
    }

    @Nullable
    private Document findDocument(@NotNull VirtualFile file) {
        for (FileEditor editor : FileEditorManager.getInstance(project).getEditors(file)) {
            if (editor instanceof TextEditor textEditor) {
                return textEditor.getEditor().getDocument();
            }
        }
        return null;
    }

    /** Откладывает сохранение; новая правка до срабатывания переносит его (защита от дребезга). */
    private synchronized void scheduleSave(@NotNull Document document) {
        if (pendingSave != null) {
            pendingSave.cancel(false);
        }
        pendingSave = scheduler.schedule(
            () -> ApplicationManager.getApplication().invokeLater(() -> saveDocument(document)),
            AUTO_SAVE_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    /** Сохраняет документ на диск; вызывается в потоке интерфейса. */
    private void saveDocument(@NotNull Document document) {
        if (project.isDisposed()) {
            return;
        }
        try {
            FileDocumentManager.getInstance().saveDocument(document);
        } catch (RuntimeException e) {
            LOG.warn("Автосохранение рабочего JSON не удалось: " + e.getMessage());
        }
    }

    /** Снимает все слушатели и останавливает планировщик. */
    public void dispose() {
        for (VirtualFile file : new ArrayList<>(listeners.keySet())) {
            unregisterDocumentListener(file);
        }
        scheduler.shutdownNow();
    }
}
