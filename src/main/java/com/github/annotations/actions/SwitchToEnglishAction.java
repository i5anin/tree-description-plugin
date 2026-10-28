package com.github.annotations.actions;

import com.github.annotations.services.AnnotationService;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

/**
 * Переключение на английский язык
 */
public class SwitchToEnglishAction extends AnAction {
    
    public SwitchToEnglishAction() {
        super("English");
        getTemplatePresentation().setDescription("Переключить на английский");
    }
    
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            Messages.showErrorDialog("Не удалось получить проект", "Ошибка");
            return;
        }
        
        AnnotationService service = AnnotationService.getInstance(project);
        if (service == null) {
            Messages.showErrorDialog("Не удалось получить сервис заметок", "Ошибка");
            return;
        }
        
        try {
            // Включаем английский язык
            service.setLanguage("en");
            
            // Сообщение об успехе
            Messages.showInfoMessage(project, "Language switched to English", "Language Switch");
            
        } catch (Exception ex) {
            Messages.showErrorDialog(project, "Language switch failed: " + ex.getMessage(), "Error");
        }
    }
    
    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            e.getPresentation().setEnabled(false);
            return;
        }
        
        AnnotationService service = AnnotationService.getInstance(project);
        if (service == null) {
            e.getPresentation().setEnabled(false);
            return;
        }
        
        // Если английский уже включён — пункт неактивен
        String currentLanguage = service.getLanguage();
        boolean isCurrentLanguage = "en".equals(currentLanguage);
        e.getPresentation().setEnabled(!isCurrentLanguage);
        
        // Текст зависит от текущего языка
        if (isCurrentLanguage) {
            // Сейчас английский — показываем отметку
            e.getPresentation().setText("✓ English");
        } else {
            // Сейчас русский — показываем пункт английского
            e.getPresentation().setText("Английский");
        }
    }
}
