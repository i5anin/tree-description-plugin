package com.github.annotations.actions;

import com.github.annotations.services.AnnotationService;
import com.github.annotations.utils.I18nUtils;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class ClearAllMappingsAction extends AnAction {
    
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }
        
        AnnotationService annotationService = AnnotationService.getInstance(project);
        
        Map<String, String> fileMatchAnnotations = annotationService.getAllFileMatchAnnotations();
        Map<String, String> packageMatchAnnotations = annotationService.getAllPackageMatchAnnotations();
        
        int totalCount = fileMatchAnnotations.size() + packageMatchAnnotations.size();
        
        if (totalCount == 0) {
            Messages.showInfoMessage(
                project,
                I18nUtils.getText(project, "В проекте нет сопоставлений по пути.", "Current project has no path matching mappings."),
                I18nUtils.getText(project, "Очистить все сопоставления", "Clear All Mappings")
            );
            return;
        }
        
        StringBuilder details = new StringBuilder();
        details.append(I18nUtils.getText(project, "Всего ", "Current total: ")).append(totalCount).append(I18nUtils.getText(project, " сопоставлений по пути:\n", " path matching mappings:\n"));
        if (!fileMatchAnnotations.isEmpty()) {
            details.append("• ").append(I18nUtils.getText(project, "Шаблоны файлов: ", "File match patterns: ")).append(fileMatchAnnotations.size()).append(I18nUtils.getText(project, " шт.\n", " items\n"));
        }
        if (!packageMatchAnnotations.isEmpty()) {
            details.append("• ").append(I18nUtils.getText(project, "Шаблоны пакетов: ", "Package match patterns: ")).append(packageMatchAnnotations.size()).append(I18nUtils.getText(project, " шт.\n", " items\n"));
        }
        details.append("\n").append(I18nUtils.getText(project, "Действие нельзя отменить!", "This operation cannot be undone!"));
        
        int result = Messages.showYesNoDialog(
            project,
            I18nUtils.getText(project, 
                "Удалить все сопоставления по пути в текущем проекте?\n\n" + details.toString(),
                "Are you sure you want to clear all path matching mappings in the current project?\n\n" + details.toString()),
            I18nUtils.getText(project, "Подтверждение очистки сопоставлений", "Confirm Clear All Mappings"),
            I18nUtils.getText(project, "Очистить", "Clear"),
            I18nUtils.getText(project, "Отмена", "Cancel"),
            Messages.getWarningIcon()
        );
        
        if (result == Messages.YES) {
            // Очищаем сопоставления по файлам
            for (String pattern : fileMatchAnnotations.keySet()) {
                annotationService.setFileMatchAnnotation(pattern, "");
            }
            
            // Очищаем сопоставления по пакетам
            for (String pattern : packageMatchAnnotations.keySet()) {
                annotationService.setPackageMatchAnnotation(pattern, "");
            }
            
            // Обновляем, чтобы JSON-файл и интерфейс совпадали
            annotationService.refreshAfterSave();
        }
    }
    
    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            e.getPresentation().setEnabledAndVisible(false);
            return;
        }
        
        try {
            AnnotationService annotationService = AnnotationService.getInstance(project);
            if (annotationService != null) {
                Map<String, String> fileMatchAnnotations = annotationService.getAllFileMatchAnnotations();
                Map<String, String> packageMatchAnnotations = annotationService.getAllPackageMatchAnnotations();
                boolean hasMappings = !fileMatchAnnotations.isEmpty() || !packageMatchAnnotations.isEmpty();
                
                e.getPresentation().setEnabledAndVisible(true);
                
                // Текст пункта меню зависит от текущего языка
                String currentLanguage = annotationService.getLanguage();
                if ("en".equals(currentLanguage)) {
                    e.getPresentation().setText("Clear All Mappings");
                    if (!hasMappings) {
                        e.getPresentation().setDescription("No path matching mappings to clear in current project");
                    } else {
                        e.getPresentation().setDescription("Clear all path matching mappings in current project");
                    }
                } else {
                    e.getPresentation().setText("Очистить все сопоставления");
                    if (!hasMappings) {
                        e.getPresentation().setDescription("В проекте нет сопоставлений по пути для очистки");
                    } else {
                        e.getPresentation().setDescription("Удалить все сопоставления по пути в текущем проекте");
                    }
                }
            } else {
                e.getPresentation().setEnabledAndVisible(false);
            }
        } catch (Exception ex) {
            e.getPresentation().setEnabledAndVisible(false);
        }
    }
}