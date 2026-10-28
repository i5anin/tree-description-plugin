package com.github.annotations.actions;

import com.github.annotations.services.AnnotationService;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Группа контекстного меню «Заметки проекта»
 * Текст меняется вместе с языком
 */
public class ContextMenuGroupAction extends AnAction {
    
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        // Это группа, своего действия у неё нет
    }
    
    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project != null) {
            AnnotationService service = AnnotationService.getInstance(project);
            if (service != null) {
                String currentLanguage = service.getLanguage();
                if ("en".equals(currentLanguage)) {
                    e.getPresentation().setText("Project Annotation Operations");
                } else      e.getPresentation().setText("Project Annotation Operations"); {
                    e.getPresentation().setText("Заметки проекта");
                }
            }
        }
        e.getPresentation().setIcon(com.intellij.openapi.util.IconLoader.getIcon("icons/remark.svg", ContextMenuGroupAction.class));
    }
}
