package com.github.annotations.actions;

import com.github.annotations.services.AnnotationService;
import com.github.annotations.utils.I18nUtils;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RemoveAnnotationAction extends AnAction {
    
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }
        
        if (isMultipleSelection(e)) {
            handleMultipleFiles(e, project);
        } else {
            handleSingleFile(e, project);
        }
    }

    private void handleMultipleFiles(@NotNull AnActionEvent e, @NotNull Project project) {
        VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        if (files == null || files.length == 0) {
            return;
        }
        
        AnnotationService annotationService = AnnotationService.getInstance(project);
        int annotatedCount = 0;
        int fileCount = 0;
        int directoryCount = 0;
        
        for (VirtualFile file : files) {
            if (file.isDirectory()) {
                if (annotationService.hasPackageAnnotation(file)) {
                    annotatedCount++;
                    directoryCount++;
                }
            } else {
                if (annotationService.hasAnnotation(file)) {
                    annotatedCount++;
                    fileCount++;
                }
            }
        }
        
        if (annotatedCount == 0) {
            Messages.showInfoMessage(project, 
                I18nUtils.getText(project, "У выбранных файлов и папок нет заметок", "Selected files/directories have no annotations"),
                I18nUtils.getText(project, "Удалить заметку", "Remove Annotation"));
            return;
        }
        
        StringBuilder message = new StringBuilder();
        message.append(I18nUtils.getText(project, "Удалить заметки у выбранных ", "Are you sure you want to delete the selected ")).append(annotatedCount).append(I18nUtils.getText(project, " ", " "));
        
        if (fileCount > 0 && directoryCount > 0) {
            message.append(I18nUtils.getText(project, "файлов/папок", "files/directories"));
        } else if (fileCount > 0) {
            message.append(I18nUtils.getText(project, "файлов", "files"));
        } else {
            message.append(I18nUtils.getText(project, "папок", "directories"));
        }
        
        message.append(I18nUtils.getText(project, "?", " annotations?"));
        
        int result = Messages.showYesNoDialog(
            project,
            message.toString(),
            I18nUtils.getText(project, "Подтверждение удаления", "Confirm Batch Delete"),
            Messages.getQuestionIcon()
        );
        
        if (result == Messages.YES) {
            int deletedCount = 0;
            
            for (VirtualFile file : files) {
                String relativePath = getRelativePath(project, file);
                if (relativePath != null) {
                    if (file.isDirectory()) {
                        if (annotationService.hasPackageAnnotation(relativePath)) {
                            annotationService.removePackageAnnotation(relativePath);
                            deletedCount++;
                        }
                    } else {
                        if (annotationService.hasAnnotation(relativePath)) {
                            annotationService.removeAnnotation(relativePath);
                            deletedCount++;
                        }
                    }
                } else {
                    if (file.isDirectory()) {
                        if (annotationService.hasPackageAnnotation(file.getPath())) {
                            annotationService.removePackageAnnotation(file.getPath());
                            deletedCount++;
                        }
                    } else {
                        if (annotationService.hasAnnotation(file.getPath())) {
                            annotationService.removeAnnotation(file.getPath());
                            deletedCount++;
                        }
                    }
                }
            }
            
            // Обновляем через AnnotationService, чтобы JSON-файл и интерфейс совпадали
            annotationService.refreshAfterSave();
        }
    }

    private void handleSingleFile(@NotNull AnActionEvent e, @NotNull Project project) {
        VirtualFile file = getSelectedFile(e);
        if (file == null) {
            return;
        }
        
        AnnotationService annotationService = AnnotationService.getInstance(project);
        String existingAnnotation;
        
        if (file.isDirectory()) {
            existingAnnotation = annotationService.getPackageAnnotation(file);
        } else {
            existingAnnotation = annotationService.getAnnotation(file);
        }
        
        if (existingAnnotation == null) {
            String message = file.isDirectory() ? 
                I18nUtils.getText(project, "У папки нет заметки", "This directory has no annotation") :
                I18nUtils.getText(project, "У файла нет заметки", "This file has no annotation");
            Messages.showInfoMessage(project, message, I18nUtils.getText(project, "Удалить заметку", "Remove Annotation"));
            return;
        }
        
        int result = Messages.showYesNoDialog(
            project,
            I18nUtils.getText(project, 
                "Удалить заметку у \"" + file.getName() + "\"?\n\nТекущая заметка: " + existingAnnotation,
                "Are you sure you want to delete the annotation for \"" + file.getName() + "\"?\n\nCurrent annotation: " + existingAnnotation),
            I18nUtils.getText(project, "Подтверждение удаления заметки", "Confirm Delete Annotation"),
            Messages.getQuestionIcon()
        );
        
        if (result == Messages.YES) {
            String relativePath = getRelativePath(project, file);
            if (relativePath != null) {
                if (file.isDirectory()) {
                    annotationService.removePackageAnnotation(relativePath);
                } else {
                    annotationService.removeAnnotation(relativePath);
                }
            } else {
                if (file.isDirectory()) {
                    annotationService.removePackageAnnotation(file.getPath());
                } else {
                    annotationService.removeAnnotation(file.getPath());
                }
            }
            
            // Обновляем через AnnotationService, чтобы JSON-файл и интерфейс совпадали
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
        
        // Пункт меню показывается всегда
        e.getPresentation().setVisible(true);
        e.getPresentation().setEnabled(true);
        
        // Текст пункта меню зависит от текущего языка
        AnnotationService annotationService = AnnotationService.getInstance(project);
        String currentLanguage = annotationService.getLanguage();
        if ("en".equals(currentLanguage)) {
            e.getPresentation().setText("Remove Annotation");
            e.getPresentation().setDescription("Remove user annotation from selected files");
        } else {
            e.getPresentation().setText("Удалить заметку");
            e.getPresentation().setDescription("Удалить свою заметку у выбранного файла");
        }
    }
    
    private boolean isMultipleSelection(@NotNull AnActionEvent e) {
        VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        return files != null && files.length > 1;
    }
    
    @Nullable
    private VirtualFile getSelectedFile(@NotNull AnActionEvent e) {
        VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        if (files != null && files.length > 0) {
            return files[0];
        }
        
        PsiElement psiElement = e.getData(CommonDataKeys.PSI_ELEMENT);
        if (psiElement instanceof PsiFile) {
            return ((PsiFile) psiElement).getVirtualFile();
        } else if (psiElement instanceof PsiDirectory) {
            return ((PsiDirectory) psiElement).getVirtualFile();
        }
        
        // Способ 3: из цели навигации
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        if (file != null) {
            return file;
        }
        
        return null;
    }
    
    @Nullable
    private String getRelativePath(@NotNull Project project, @NotNull VirtualFile file) {
        String projectPath = project.getBasePath();
        if (projectPath == null) {
            return null;
        }
        
        String filePath = file.getPath();
        
        if (filePath.startsWith(projectPath)) {
            if (filePath.length() > projectPath.length()) {
                String relativePath = filePath.substring(projectPath.length());
                return relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;
            } else if (filePath.equals(projectPath)) {
                return "";
            }
        }
        
        return null;
    }
}
