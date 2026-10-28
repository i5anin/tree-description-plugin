package com.github.annotations.actions;

import com.github.annotations.services.AnnotationService;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

/**
 * Переключение на русский язык
 */
public class SwitchToRussianAction extends AnAction {
    
    public SwitchToRussianAction() {
        super("Русский");
        getTemplatePresentation().setDescription("Переключить на русский");
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
            // Включаем русский язык
            service.setLanguage("ru");
            
            // Сообщение об успехе
            Messages.showInfoMessage(project, "Язык переключён на русский", "Смена языка");
            
        } catch (Exception ex) {
            Messages.showErrorDialog(project, "Не удалось сменить язык: " + ex.getMessage(), "Ошибка");
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
        
        // Если русский уже включён — пункт неактивен
        String currentLanguage = service.getLanguage();
        boolean isCurrentLanguage = "ru".equals(currentLanguage);
        e.getPresentation().setEnabled(!isCurrentLanguage);
        
        // Текст зависит от текущего языка
        if (isCurrentLanguage) {
            // Сейчас русский — показываем отметку
            e.getPresentation().setText("✓ Русский");
        } else {
            // Сейчас английский — показываем пункт русского
            e.getPresentation().setText("Russian");
        }
    }
}
