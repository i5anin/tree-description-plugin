package com.github.annotations.services;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Сервис обновления дерева проекта
 * Принудительно обновляет дерево проекта и оформление узлов
 */
public class ProjectViewRefreshService {
    
    private final Project project;
    
    public ProjectViewRefreshService(Project project) {
        this.project = project;
    }
    
    /**
     * Принудительно обновляет дерево проекта
     */
    public void refreshProjectView() {
        try {
            ProjectView projectView = ProjectView.getInstance(project);
            if (projectView != null) {
                // Принудительно обновляем всё дерево проекта штатным API
                ApplicationManager.getApplication().invokeLater(() -> {
                    try {
                        projectView.refresh();
                        // getCurrentProjectViewPane() не используем: в некоторых версиях IDE его нет

                        
                        com.intellij.openapi.diagnostic.Logger.getInstance(ProjectViewRefreshService.class)
                            .info("Дерево проекта принудительно обновлено");
                    } catch (Exception e) {
                        com.intellij.openapi.diagnostic.Logger.getInstance(ProjectViewRefreshService.class)
                            .warn("Не удалось принудительно обновить дерево проекта: " + e.getMessage());
                    }
                }, project.getDisposed());
            }
        } catch (Exception e) {
            com.intellij.openapi.diagnostic.Logger.getInstance(ProjectViewRefreshService.class)
                .warn("Не удалось обновить дерево проекта: " + e.getMessage());
        }
    }
    
    /**
     * Принудительно перерисовывает оформление всех узлов
     */
    public void forceNodeRedecoration() {
        try {
            ApplicationManager.getApplication().invokeLater(() -> {
                try {
                    ProjectView projectView = ProjectView.getInstance(project);
                    if (projectView != null) {
                        // Обновляем дерево проекта, чтобы узлы оформились заново
                        projectView.refresh();
                    }
                } catch (Exception e) {
                    com.intellij.openapi.diagnostic.Logger.getInstance(ProjectViewRefreshService.class)
                        .warn("Не удалось перерисовать оформление узлов: " + e.getMessage());
                }
            }, project.getDisposed());
        } catch (Exception e) {
            com.intellij.openapi.diagnostic.Logger.getInstance(ProjectViewRefreshService.class)
                .warn("Не удалось перерисовать оформление узлов: " + e.getMessage());
        }
    }
}
