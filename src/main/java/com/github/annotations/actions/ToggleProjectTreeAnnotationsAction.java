package com.github.annotations.actions;

import com.github.annotations.services.AnnotationService;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;


public class ToggleProjectTreeAnnotationsAction extends AnAction {
    
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }
        
        AnnotationService annotationService = AnnotationService.getInstance(project);
        boolean isEnabled = annotationService.isProjectTreeAnnotationsEnabled();
        
        if (isEnabled) {
            int result = Messages.showYesNoDialog(
                project,
                "Отключить показ заметок в дереве проекта?\n\nПосле отключения заметки в дереве проекта не отображаются.",
                "Отключить заметки в дереве",
                "Отключить",
                "Отмена",
                Messages.getWarningIcon()
            );
            
            if (result == Messages.YES) {
                annotationService.setProjectTreeAnnotationsEnabled(false);
            }
        } else {
            int result = Messages.showYesNoDialog(
                project,
                "Включить показ заметок в дереве проекта?\n\nПосле включения заметки отображаются в дереве проекта.",
                "Включить заметки в дереве",
                "Включить",
                "Отмена",
                Messages.getInformationIcon()
            );
            
            if (result == Messages.YES) {
                annotationService.setProjectTreeAnnotationsEnabled(true);
            }
        }
    }
    
    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            e.getPresentation().setEnabledAndVisible(false);
            return;
        }
        
        AnnotationService annotationService = AnnotationService.getInstance(project);
        boolean isEnabled = annotationService.isProjectTreeAnnotationsEnabled();
        
        // Текст пункта меню зависит от текущего языка
        String currentLanguage = annotationService.getLanguage();
        if ("en".equals(currentLanguage)) {
            if (isEnabled) {
                e.getPresentation().setText("Disable Project Tree Annotations");
                e.getPresentation().setDescription("Disable annotation display in project tree");
            } else {
                e.getPresentation().setText("Enable Project Tree Annotations");
                e.getPresentation().setDescription("Enable annotation display in project tree");
            }
        } else {
            if (isEnabled) {
                e.getPresentation().setText("Отключить заметки в дереве");
                e.getPresentation().setDescription("Не показывать заметки в дереве проекта");
            } else {
                e.getPresentation().setText("Включить заметки в дереве");
                e.getPresentation().setDescription("Показывать заметки в дереве проекта");
            }
        }
        
        e.getPresentation().setVisible(true);
        e.getPresentation().setEnabled(true);
    }
}
