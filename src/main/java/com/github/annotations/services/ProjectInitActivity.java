package com.github.annotations.services;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

/**
 * Инициализация после открытия проекта
 * Заменяет внутренний API StartupManager.runAfterOpened
 */
public class ProjectInitActivity implements StartupActivity {
    
    private static final Logger LOG = Logger.getInstance(ProjectInitActivity.class);
    
    @Override
    public void runActivity(@NotNull Project project) {
        // Логика после открытия проекта
        // Здесь можно вызвать инициализацию языка из AnnotationService
        try {
            // Получаем AnnotationService и запускаем инициализацию
            AnnotationService annotationService = project.getService(AnnotationService.class);
            if (annotationService != null) {
                annotationService.initializeLanguageSettings();
            }
        } catch (Exception e) {
            // Записываем ошибку в журнал
            LOG.warn("Failed to initialize project settings", e);
        }
    }
}