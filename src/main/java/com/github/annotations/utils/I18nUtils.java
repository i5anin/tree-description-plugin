package com.github.annotations.utils;

import com.github.annotations.services.AnnotationService;
import com.intellij.openapi.project.Project;

/**
 * Выбор текста интерфейса по языку
 * Возвращает русский или английский вариант строки
 */
public class I18nUtils {
    
    /**
     * Возвращает текст на языке, выбранном в настройках проекта
     * @param project проект
     * @param russian русский текст
     * @param english английский текст
     * @return текст на текущем языке
     */
    public static String getText(Project project, String russian, String english) {
        if (project == null) {
            return russian; // по умолчанию русский
        }
        
        AnnotationService service = AnnotationService.getInstance(project);
        if (service == null) {
            return russian; // по умолчанию русский
        }
        
        String currentLanguage = service.getLanguage();
        return "en".equals(currentLanguage) ? english : russian;
    }
    
    /**
     * Возвращает текст на указанном языке (без проекта)
     * @param language код языка
     * @param russian русский текст
     * @param english английский текст
     * @return текст на указанном языке
     */
    public static String getText(String language, String russian, String english) {
        return "en".equals(language) ? english : russian;
    }
}
