package com.github.annotations.utils;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * Единственное место, где задано, где лежат рабочие JSON-файлы плагина.
 *
 * Файлы заметок (local-description.json и скачанные библиотеки сопоставлений)
 * хранятся в .idea/tree-description/, а не в корне проекта: это настройки IDE,
 * им место рядом с остальными файлами .idea.
 *
 * Используется сервисами загрузки и сохранения (AnnotationService), слушателями
 * изменений JSON (JsonEditorListener, JsonFileWatcher, FileEditorListener,
 * RealTimeAnnotationService), обновлением VFS (VFSRefreshService) и диалогами,
 * которые показывают путь пользователю.
 */
public final class MappingsPaths {

    private static final Logger LOG = Logger.getInstance(MappingsPaths.class);

    /** Папка рабочих JSON относительно корня проекта. */
    public static final String MAPPINGS_DIR = ".idea/tree-description";

    /** Старое место папки (до переноса в .idea) — переносится автоматически. */
    private static final String LEGACY_MAPPINGS_DIR = ".td-maps";

    /** Основной файл с заметками пользователя. */
    public static final String LOCAL_DESCRIPTION_FILE = "local-description.json";

    private MappingsPaths() {
    }

    /**
     * Абсолютный путь к папке рабочих JSON.
     *
     * @return null, если у проекта нет корня (например, проект по умолчанию)
     */
    @Nullable
    public static Path getMappingsDir(@NotNull Project project) {
        String basePath = project.getBasePath();
        return basePath == null ? null : Paths.get(basePath, MAPPINGS_DIR);
    }

    /**
     * Абсолютный путь к local-description.json в системном формате VFS (разделитель /).
     *
     * @return null, если у проекта нет корня
     */
    @Nullable
    public static String getLocalDescriptionPath(@NotNull Project project) {
        String mappingsDirPath = getMappingsDirVfsPath(project);
        return mappingsDirPath == null ? null : mappingsDirPath + "/" + LOCAL_DESCRIPTION_FILE;
    }

    /**
     * Путь к папке рабочих JSON в формате VFS (разделитель /, без / в конце).
     * VirtualFile.getPath() всегда использует /, поэтому сравнивать надо с этим видом.
     */
    @Nullable
    public static String getMappingsDirVfsPath(@NotNull Project project) {
        String basePath = project.getBasePath();
        return basePath == null ? null : basePath + "/" + MAPPINGS_DIR;
    }

    /**
     * Лежит ли файл прямо в папке рабочих JSON и является ли он JSON.
     * Общая проверка для всех слушателей изменений файлов.
     *
     * Сравнение идёт с «папка + /», чтобы соседняя папка с похожим именем
     * (например, tree-description-old) не считалась нашей.
     */
    public static boolean isMappingsJson(@NotNull Project project, @Nullable VirtualFile file) {
        if (file == null || !file.getName().toLowerCase().endsWith(".json")) {
            return false;
        }
        String mappingsDirPath = getMappingsDirVfsPath(project);
        return mappingsDirPath != null && file.getPath().startsWith(mappingsDirPath + "/");
    }

    /**
     * Переносит файлы из старой папки .td-maps в корне проекта в .idea/tree-description.
     *
     * Вызывается один раз при загрузке заметок (AnnotationService), до чтения файлов.
     * Правила:
     * - старой папки нет — ничего не делаем;
     * - файл с таким именем уже есть на новом месте — старый не трогаем
     *   и пишем предупреждение в журнал, чтобы не потерять ни одну версию;
     * - если после переноса старая папка пуста — удаляем её.
     *
     * Ошибки ввода-вывода не прерывают загрузку: пишем в журнал и продолжаем.
     */
    public static void migrateLegacyDir(@NotNull Project project) {
        String basePath = project.getBasePath();
        Path newDir = getMappingsDir(project);
        if (basePath == null || newDir == null) {
            return;
        }
        Path legacyDir = Paths.get(basePath, LEGACY_MAPPINGS_DIR);
        if (!Files.isDirectory(legacyDir)) {
            return;
        }

        try {
            Files.createDirectories(newDir);
            try (Stream<Path> files = Files.list(legacyDir)) {
                files.filter(Files::isRegularFile).forEach(file -> moveIfFree(file, newDir));
            }
            try (Stream<Path> rest = Files.list(legacyDir)) {
                if (rest.findAny().isEmpty()) {
                    Files.delete(legacyDir);
                    LOG.info("Старая папка " + legacyDir + " перенесена в " + newDir + " и удалена");
                }
            }
        } catch (IOException e) {
            LOG.warn("Не удалось перенести " + legacyDir + " в " + newDir + ": " + e.getMessage());
        }
    }

    /** Переносит один файл, если на новом месте нет файла с тем же именем. */
    private static void moveIfFree(@NotNull Path file, @NotNull Path targetDir) {
        Path target = targetDir.resolve(file.getFileName());
        if (Files.exists(target)) {
            LOG.warn("Файл " + target + " уже существует, старый " + file + " оставлен на месте");
            return;
        }
        try {
            Files.move(file, target);
        } catch (IOException e) {
            LOG.warn("Не удалось перенести " + file + ": " + e.getMessage());
        }
    }
}
