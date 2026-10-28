package com.github.annotations.utils;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Управление языком плагина
 * Хранит текущий язык интерфейса
 */
public class LanguageManager {
    
    private static final Logger LOG = Logger.getInstance(LanguageManager.class);
    
    // Поддерживаемые языки
    public enum Language {
        ENGLISH("en", "English", Locale.ENGLISH),
        CHINESE("ru", "Русский", Locale.forLanguageTag("ru"));
        
        private final String code;
        private final String displayName;
        private final Locale locale;
        
        Language(String code, String displayName, Locale locale) {
            this.code = code;
            this.displayName = displayName;
            this.locale = locale;
        }
        
        public String getCode() {
            return code;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public Locale getLocale() {
            return locale;
        }
        
        public static Language fromCode(String code) {
            for (Language lang : values()) {
                if (lang.code.equals(code)) {
                    return lang;
                }
            }
            return ENGLISH; // по умолчанию английский
        }
    }
    
    private static Language currentLanguage = Language.ENGLISH; // по умолчанию английский
    
    /**
     * Возвращает текущий язык
     */
    @NotNull
    public static Language getCurrentLanguage() {
        return currentLanguage;
    }
    
    /**
     * Задаёт текущий язык
     */
    public static void setCurrentLanguage(@NotNull Language language) {
        currentLanguage = language;
        LOG.info("Language changed to: " + language.getDisplayName());
    }
    
    /**
     * Возвращает код текущего языка
     */
    @NotNull
    public static String getCurrentLanguageCode() {
        return currentLanguage.getCode();
    }
    
    /**
     * Включён ли русский
     */
    public static boolean isChinese() {
        return currentLanguage == Language.CHINESE;
    }
    
    /**
     * Включён ли английский
     */
    public static boolean isEnglish() {
        return currentLanguage == Language.ENGLISH;
    }
    
    /**
     * Возвращает текст на текущем языке
     * Простая реализация; при необходимости заменяется полноценной локализацией
     */
    @NotNull
    public static String getText(String chineseText, String englishText) {
        return isChinese() ? chineseText : englishText;
    }
    
    /**
     * Возвращает текст на текущем языке (перегрузка: передаётся только русский текст, английский не генерируется)
     */
    @NotNull
    public static String getText(String chineseText) {
        // Простое сопоставление русского и английского, можно доработать
        if (isChinese()) {
            return chineseText;
        } else {
            // Здесь можно добавить перевод с русского на английский
    
            return chineseText;
        }
    }
}
