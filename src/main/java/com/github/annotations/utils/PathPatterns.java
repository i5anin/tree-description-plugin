package com.github.annotations.utils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

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

    /**
     * Сколько обращений к символам допускается на одну проверку регулярным выражением.
     * Обычному выражению на имени файла хватает тысяч; выражению с экспоненциальным
     * перебором (например "^(.*a){12}$" из чужой библиотеки сопоставлений) — нет.
     * Проверка идёт при отрисовке каждого узла дерева, зависание = зависание IDE.
     */
    private static final int REGEX_CHAR_ACCESS_BUDGET = 1_000_000;

    /** Скомпилированные выражения: шаблонов немного, а вызовов — на каждый узел дерева. */
    private static final Map<String, Pattern> COMPILED = new ConcurrentHashMap<>();

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
                return regexMatches(fileName, pattern);
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
                return regexMatches(dottedPath, pattern);
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

    /**
     * Проверяет текст регулярным выражением с ограничением работы.
     *
     * Некорректное выражение бросает PatternSyntaxException — его обрабатывает
     * вызывающий метод (запасная проверка вхождения). Превышение бюджета
     * означает «не подходит»: лучше не показать заметку, чем повесить IDE.
     */
    private static boolean regexMatches(@NotNull String text, @NotNull String regex) {
        Pattern compiled = COMPILED.computeIfAbsent(regex, Pattern::compile);
        try {
            return compiled.matcher(new BudgetedText(text, new int[]{REGEX_CHAR_ACCESS_BUDGET})).matches();
        } catch (BudgetExceeded e) {
            return false;
        }
    }

    /** Текст для Matcher, который считает обращения к символам и останавливает перебор по бюджету. */
    private static final class BudgetedText implements CharSequence {
        private final String text;
        private final int[] remaining; // общий счётчик для text и всех subSequence

        BudgetedText(String text, int[] remaining) {
            this.text = text;
            this.remaining = remaining;
        }

        @Override
        public char charAt(int index) {
            if (--remaining[0] < 0) {
                throw BudgetExceeded.INSTANCE;
            }
            return text.charAt(index);
        }

        @Override
        public int length() {
            return text.length();
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return new BudgetedText(text.substring(start, end), remaining);
        }

        @Override
        public String toString() {
            return text;
        }
    }

    /** Сигнал «бюджет исчерпан»; без стека — он не нужен и дорог. */
    private static final class BudgetExceeded extends RuntimeException {
        static final BudgetExceeded INSTANCE = new BudgetExceeded();

        private BudgetExceeded() {
            super(null, null, false, false);
        }
    }
}
