package com.github.annotations.model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Установка библиотеки: проверка формата до записи, без перезаписи заметок. */
public final class MappingLibraryStore {
    private MappingLibraryStore() {}

    public static void saveNew(Path directory, String name, String content) throws IOException {
        if (name == null || name.isBlank() || name.contains("/") || name.contains("\\")
                || name.contains(":") || !name.endsWith(".json")
                || name.equalsIgnoreCase("local-description.json")) {
            throw new IOException("Недопустимое имя файла библиотеки");
        }
        if (MappingFileParser.parseOrNull(content) == null) {
            throw new IOException("Библиотека не содержит корректных сопоставлений");
        }
        Files.createDirectories(directory);
        // CREATE_NEW также запрещает запись через уже существующую символьную ссылку.
        Files.writeString(directory.resolve(name), content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }
}
