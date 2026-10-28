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
     * @param chinese русский текст
     * @param english английский текст
     * @return текст на текущем языке
     */
    public static String getText(Project project, String chinese, String english) {
        if (project == null) {
            return chinese; // по умолчанию русский
        }
        
        AnnotationService service = AnnotationService.getInstance(project);
        if (service == null) {
            return chinese; // по умолчанию русский
        }
        
        String currentLanguage = service.getLanguage();
        return "en".equals(currentLanguage) ? english : chinese;
    }
    
    /**
     * Возвращает текст на указанном языке (без проекта)
     * @param language код языка
     * @param chinese русский текст
     * @param english английский текст
     * @return текст на указанном языке
     */
    public static String getText(String language, String chinese, String english) {
        return "en".equals(language) ? english : chinese;
    }
}
