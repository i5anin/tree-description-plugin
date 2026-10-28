package com.github.annotations.utils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Языки интерфейса плагина.
 *
 * Текущий язык хранится не здесь, а в AnnotationService каждого проекта
 * (поле language, сохраняется в local-description.json); тексты выбирает I18nUtils.
 * Перечисление нужно странице настроек (AnnotationConfigurable) для списка языков.
 */
public final class LanguageManager {

    private LanguageManager() {
    }

    /** Поддерживаемые языки: код хранится в файле, название показывается в настройках. */
    public enum Language {
        ENGLISH("en", "English"),
        RUSSIAN("ru", "Русский");

        private final String code;
        private final String displayName;

        Language(String code, String displayName) {
            this.code = code;
            this.displayName = displayName;
        }

        public String getCode() {
            return code;
        }

        public String getDisplayName() {
            return displayName;
        }

        /**
         * Приводит сохранённый код языка к поддерживаемому.
         *
         * Плагин понимает два языка: "en" и второй язык интерфейса — русский.
         * До перевода вторым языком был китайский с кодом "zh", и он остался
         * в local-description.json у существующих проектов. Любой код,
         * кроме "en", считается русским, иначе пункт меню «Русский» не был бы отмечен.
         *
         * Вызывается из AnnotationService при чтении файлов сопоставлений.
         */
        @NotNull
        public static String normalizeCode(@Nullable String code) {
            return ENGLISH.code.equals(code) ? ENGLISH.code : RUSSIAN.code;
        }
    }
}
