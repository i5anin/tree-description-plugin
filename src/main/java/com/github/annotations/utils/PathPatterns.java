package com.github.annotations.utils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Единые правила сопоставления шаблонов заметок с файлами и пакетами.
 *
 * Используется пользовательскими шаблонами (AnnotationService) и шаблонами
 * библиотек сопоставлений (MappingLibraryService) — раньше это были две копии
 * с расхождениями. Чистые функции, без IDE.
 *
 * Шаблон считается регулярным выражением, если содержит ".*", "\", "$" или "^".
 */
public final class PathPatterns {

    private PathPatterns() {
    }

    /**
     * Подходит ли файл под шаблон.
     *
     * Порядок:
     * 1. регулярное выражение — по имени файла целиком;
     * 2. шаблон с "/" (например "com/common/pom.xml") — имя файла совпадает без учёта
     *    регистра, а папка файла оканчивается на часть шаблона до последнего "/";
     *    без relativePath сверяется только имя;
     * 3. иначе — полное совпадение имени без учёта регистра.
     * Некорректное регулярное выражение — проверка «имя содержит шаблон».
     *
     * @param fileName     имя файла
     * @param pattern      шаблон
     * @param relativePath путь файла от корня проекта через "/", может быть null
     */
    public static boolean matchesFile(@NotNull String fileName, @NotNull String pattern, @Nullable String relativePath) {
        try {
            if (isRegex(pattern)) {
                return fileName.matches(pattern);
            }
            if (pattern.contains("/")) {
                return matchesFileInPackage(fileName, pattern, relativePath);
            }
            return fileName.equalsIgnoreCase(pattern);
        } catch (Exception e) {
            return fileName.toLowerCase().contains(pattern.toLowerCase());
        }
    }

    /**
     * Подходит ли путь пакета под шаблон.
     *
     * Путь приводится к виду через точки ("com/a/b" -> "com.a.b").
     * 1. регулярное выражение — по всему пути;
     * 2. шаблон из одного сегмента — равен последнему сегменту пути (имени папки);
     * 3. шаблон из нескольких сегментов — равен окончанию пути целыми сегментами.
     * Сравнение 2–3 без учёта регистра. Некорректное регулярное выражение —
     * проверка «путь содержит шаблон».
     */
    public static boolean matchesPackage(@NotNull String packagePath, @NotNull String pattern) {
        String dottedPath = packagePath.replace("/", ".");
        try {
            if (isRegex(pattern)) {
                return dottedPath.matches(pattern);
            }
            String[] pathSegments = dottedPath.toLowerCase().split("\\.");
            String[] patternSegments = pattern.toLowerCase().split("\\.");
            if (patternSegments.length > pathSegments.length) {
                return false;
            }
            int pathStart = pathSegments.length - patternSegments.length;
            for (int i = 0; i < patternSegments.length; i++) {
                if (!pathSegments[pathStart + i].equals(patternSegments[i])) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return dottedPath.toLowerCase().contains(pattern.toLowerCase());
        }
    }

    private static boolean isRegex(@NotNull String pattern) {
        return pattern.contains(".*") || pattern.contains("\\") || pattern.contains("$") || pattern.contains("^");
    }

    private static boolean matchesFileInPackage(@NotNull String fileName, @NotNull String pattern, @Nullable String relativePath) {
        int lastSlash = pattern.lastIndexOf('/');
        String expectedFileName = pattern.substring(lastSlash + 1);
        String expectedPackagePath = pattern.substring(0, lastSlash);
        if (!fileName.equalsIgnoreCase(expectedFileName)) {
            return false;
        }
        if (relativePath == null || relativePath.isEmpty()) {
            return true;
        }
        int fileLastSlash = relativePath.lastIndexOf('/');
        String fileDir = fileLastSlash == -1 ? "" : relativePath.substring(0, fileLastSlash);
        return fileDir.endsWith(expectedPackagePath);
    }
}
