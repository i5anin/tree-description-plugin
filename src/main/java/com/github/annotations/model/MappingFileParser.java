package com.github.annotations.model;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Разбирает все файлы сопоставлений папки рабочих JSON за один проход.
 *
 * Зачем отдельный класс: пока пользователь правит local-description.json,
 * на диск попадают промежуточные невалидные версии. Раньше такой файл просто
 * выпадал из набора, и все его заметки пропадали из дерева, а следующее
 * сохранение записывало пустой набор поверх файла. Здесь правило одно:
 * файл, который не разобрался, остаётся в последней удачной версии.
 *
 * Чистая функция без IDE: вызывается из AnnotationService.scanAndLoadMappingFiles,
 * который читает тексты файлов с диска и пишет в журнал имена неразобранных.
 */
public final class MappingFileParser {

    private static final Gson GSON = new Gson();

    /**
     * Итог разбора.
     *
     * @param files           разобранные файлы по имени (для неразобранных — прошлая версия, если была)
     * @param failedFileNames имена файлов, которые не удалось разобрать, по алфавиту
     */
    public record Result(@NotNull Map<String, LocalMappingFile> files, @NotNull List<String> failedFileNames) {
    }

    private MappingFileParser() {
    }

    /**
     * Разбирает тексты файлов, подставляя прошлую версию вместо неразобранной.
     *
     * Правила:
     * - текст разобрался — берём новую версию;
     * - не разобрался (синтаксическая ошибка, пустой файл) — берём версию из previous,
     *   а имя файла попадает в failedFileNames;
     * - файла нет в contentsByFileName (удалён с диска) — его нет и в результате.
     *
     * @param contentsByFileName текущие тексты файлов: имя файла -> содержимое
     * @param previous           результат прошлого разбора (files), пустой при первой загрузке
     */
    @NotNull
    public static Result parseAll(@NotNull Map<String, String> contentsByFileName,
                                  @NotNull Map<String, LocalMappingFile> previous) {
        Map<String, LocalMappingFile> files = new LinkedHashMap<>();
        List<String> failed = new ArrayList<>();

        for (Map.Entry<String, String> entry : new TreeMap<>(contentsByFileName).entrySet()) {
            String fileName = entry.getKey();
            LocalMappingFile parsed = parseOrNull(entry.getValue());
            if (parsed != null) {
                files.put(fileName, parsed);
                continue;
            }
            failed.add(fileName);
            LocalMappingFile lastGood = previous.get(fileName);
            if (lastGood != null) {
                files.put(fileName, lastGood);
            }
        }
        return new Result(Collections.unmodifiableMap(files), Collections.unmodifiableList(failed));
    }

    /** Разбирает один текст; null — если это не JSON файла сопоставлений. */
    @Nullable
    public static LocalMappingFile parseOrNull(@NotNull String content) {
        try {
            JsonElement root = JsonParser.parseString(content);
            if (!root.isJsonObject()) return null;
            JsonElement mappings = root.getAsJsonObject().get("mappings");
            if (mappings == null || !mappings.isJsonObject()) return null;
            for (String section : List.of("files", "packages", "fileMatch", "packageMatch",
                    "filesTextColor", "packagesTextColor")) {
                JsonElement values = mappings.getAsJsonObject().get(section);
                if (values == null) continue;
                if (!values.isJsonObject()) return null;
                for (Map.Entry<String, JsonElement> entry : values.getAsJsonObject().entrySet()) {
                    if (entry.getKey().isBlank() || !entry.getValue().isJsonPrimitive()
                            || !entry.getValue().getAsJsonPrimitive().isString()) return null;
                }
            }
            return GSON.fromJson(root, LocalMappingFile.class);
        } catch (JsonParseException | IllegalStateException e) {
            return null;
        }
    }
}
