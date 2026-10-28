package com.github.annotations.actions;

import com.github.annotations.ui.SearchAnnotationsDialog;
import com.github.annotations.utils.I18nUtils;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;


public class SearchAnnotationsAction extends AnAction {
    
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        
        if (project == null) {
            Project[] openProjects = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects();
                    if (openProjects.length > 0) {
            project = openProjects[0];
        } else {
            com.intellij.openapi.ui.Messages.showInfoMessage(
                I18nUtils.getText(project, "Для поиска сначала откройте проект", "Please open a project first to use the search function"),
                I18nUtils.getText(project, "Поиск по заметкам дерева проекта", "Search Project Tree Annotations")
            );
            return;
        }
        }
        
        SearchAnnotationsDialog dialog = new SearchAnnotationsDialog(project);
        dialog.show();
    }
    
    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(true);
        e.getPresentation().setVisible(true);
        
        // Текст пункта меню зависит от текущего языка
        Project project = e.getProject();
        if (project != null) {
            try {
                com.github.annotations.services.AnnotationService service = com.github.annotations.services.AnnotationService.getInstance(project);
                if (service != null) {
                    String currentLanguage = service.getLanguage();
                    if ("en".equals(currentLanguage)) {
                        e.getPresentation().setText("Search Project Tree Annotations");
                        e.getPresentation().setDescription("Search all annotations in project");
                    } else {
                        e.getPresentation().setText("Поиск по заметкам");
                        e.getPresentation().setDescription("Искать по всем заметкам проекта");
                    }
                } else {
                    e.getPresentation().setText("Поиск по заметкам");
                    e.getPresentation().setDescription("Искать по всем заметкам проекта");
                }
            } catch (Exception ex) {
                e.getPresentation().setText("Поиск по заметкам");
                e.getPresentation().setDescription("Искать по всем заметкам проекта");
            }
        } else {
            e.getPresentation().setText("Поиск по заметкам");
            e.getPresentation().setDescription("Искать по всем заметкам проекта");
        }
    }
    
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
