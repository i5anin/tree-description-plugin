package com.github.annotations.actions;

import com.github.annotations.services.AnnotationService;
import com.github.annotations.utils.I18nUtils;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

import java.util.Map;


public class ClearAllAnnotationsAction extends AnAction {
    
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }
        
        AnnotationService annotationService = AnnotationService.getInstance(project);
        
        Map<String, String> fileAnnotations = annotationService.getAllAnnotations();
        Map<String, String> packageAnnotations = annotationService.getAllPackageAnnotations();
        Map<String, String> fileMatchAnnotations = annotationService.getAllFileMatchAnnotations();
        Map<String, String> packageMatchAnnotations = annotationService.getAllPackageMatchAnnotations();
        
        int totalCount = fileAnnotations.size() + packageAnnotations.size() + fileMatchAnnotations.size() + packageMatchAnnotations.size();
        
        if (totalCount == 0) {
            Messages.showInfoMessage(
                project,
                I18nUtils.getText(project, "В проекте нет пользовательских заметок.", "Current project has no user annotations."),
                I18nUtils.getText(project, "Очистить заметки", "Clear User Annotations")
            );
            return;
        }
        
        StringBuilder details = new StringBuilder();
        details.append(I18nUtils.getText(project, "Всего ", "Current total: ")).append(totalCount).append(I18nUtils.getText(project, " пользовательских заметок:\n", " user annotations:\n"));
        if (!fileAnnotations.isEmpty()) {
            details.append("• ").append(I18nUtils.getText(project, "Заметки файлов: ", "File annotations: ")).append(fileAnnotations.size()).append(I18nUtils.getText(project, " шт.\n", " items\n"));
        }
        if (!packageAnnotations.isEmpty()) {
            details.append("• ").append(I18nUtils.getText(project, "Заметки пакетов: ", "Package annotations: ")).append(packageAnnotations.size()).append(I18nUtils.getText(project, " шт.\n", " items\n"));
        }
        if (!fileMatchAnnotations.isEmpty()) {
            details.append("• ").append(I18nUtils.getText(project, "Заметки по шаблонам файлов: ", "File match pattern annotations: ")).append(fileMatchAnnotations.size()).append(I18nUtils.getText(project, " шт.\n", " items\n"));
        }
        if (!packageMatchAnnotations.isEmpty()) {
            details.append("• ").append(I18nUtils.getText(project, "Заметки по шаблонам пакетов: ", "Package match pattern annotations: ")).append(packageMatchAnnotations.size()).append(I18nUtils.getText(project, " шт.\n", " items\n"));
        }
        details.append("\n").append(I18nUtils.getText(project, "Действие нельзя отменить!", "This operation cannot be undone!"));
        
        int result = Messages.showYesNoDialog(
            project,
            I18nUtils.getText(project, 
                "Удалить все заметки текущего проекта?\n\n" + details.toString(),
                "Are you sure you want to clear all annotations in the current project?\n\n" + details.toString()),
            I18nUtils.getText(project, "Подтверждение очистки заметок", "Confirm Clear All Annotations"),
            I18nUtils.getText(project, "Очистить", "Clear"),
            I18nUtils.getText(project, "Отмена", "Cancel"),
            Messages.getWarningIcon()
        );
        
        if (result == Messages.YES) {
            annotationService.clearAllAnnotations();
            // clearAllAnnotations сам обновляет интерфейс и редакторы
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
                // Есть ли заметки хоть какого-то типа
                Map<String, String> fileAnnotations = annotationService.getAllAnnotations();
                Map<String, String> packageAnnotations = annotationService.getAllPackageAnnotations();
                Map<String, String> fileMatchAnnotations = annotationService.getAllFileMatchAnnotations();
                Map<String, String> packageMatchAnnotations = annotationService.getAllPackageMatchAnnotations();
                
                boolean hasAnnotations = !fileAnnotations.isEmpty() || 
                                       !packageAnnotations.isEmpty() || 
                                       !fileMatchAnnotations.isEmpty() || 
                                       !packageMatchAnnotations.isEmpty();
                
                e.getPresentation().setVisible(true);
                e.getPresentation().setEnabled(hasAnnotations);
                
                // Текст пункта меню зависит от текущего языка
                String currentLanguage = annotationService.getLanguage();
                if ("en".equals(currentLanguage)) {
                    e.getPresentation().setText("Clear All Annotations");
                    if (!hasAnnotations) {
                        e.getPresentation().setDescription("No annotations to clear in current project");
                    } else {
                        e.getPresentation().setDescription("Clear all annotations in current project");
                    }
                } else {
                    e.getPresentation().setText("Очистить все заметки");
                    if (!hasAnnotations) {
                        e.getPresentation().setDescription("В проекте нет заметок для очистки");
                    } else {
                        e.getPresentation().setDescription("Удалить все заметки текущего проекта");
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

