package com.github.annotations.actions;

import com.github.annotations.services.AnnotationService;
import com.github.annotations.services.MappingLibraryService;
import com.github.annotations.ui.AddAnnotationDialog;
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
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class AddAnnotationAction extends AnAction {
    
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
        
        int fileCount = 0;
        int directoryCount = 0;
        for (VirtualFile file : files) {
            if (file.isDirectory()) {
                directoryCount++;
            } else {
                fileCount++;
            }
        }
        
        StringBuilder message = new StringBuilder();
        message.append("Для выбранных ").append(files.length).append(" ");
        
        if (fileCount > 0 && directoryCount > 0) {
            message.append("файлов/папок");
        } else if (fileCount > 0) {
            message.append("файлов");
        } else {
            message.append("папок");
        }
        
        message.append("добавить или обновить заметку:");
        
        AnnotationService annotationService = AnnotationService.getInstance(project);
        
        String existingAnnotation = null;
        VirtualFile firstFile = files[0];
        if (firstFile.isDirectory()) {
            existingAnnotation = annotationService.getPackageAnnotation(firstFile);
        } else {
            existingAnnotation = annotationService.getAnnotation(firstFile);
        }
        
        String suggestedAnnotation = getSuggestedAnnotation(firstFile, existingAnnotation);
        String initialValue = existingAnnotation != null ? existingAnnotation : 
                             (suggestedAnnotation != null ? suggestedAnnotation : "");
        
        // Цвет берём у первого файла
        String existingColor = null;
        String firstRelativePath = getRelativePath(project, firstFile);
        if (firstRelativePath != null) {
            if (firstFile.isDirectory()) {
                existingColor = annotationService.getPackageTextColor(firstRelativePath);
            } else {
                existingColor = annotationService.getFileTextColor(firstRelativePath);
            }
        }
        
        AddAnnotationDialog dialog = new AddAnnotationDialog(project, I18nUtils.getText(project, "Несколько файлов", "Multiple Files"), initialValue, existingColor);
        
        if (dialog.showAndGet()) {
            String annotation = dialog.getAnnotation();
            String selectedColor = dialog.getSelectedColor();
            
            if (annotation.isEmpty()) {
                return;
            }
            

            
            for (VirtualFile file : files) {
                String relativePath = getRelativePath(project, file);
                if (relativePath != null) {
                    if (file.isDirectory()) {
                        annotationService.setPackageAnnotation(relativePath, annotation, selectedColor);
                    } else {
                        annotationService.setAnnotation(relativePath, annotation, selectedColor);
                    }
                }
            }
            
            // Сразу после сохранения обновляем VFS и дерево проекта (иначе при выборе нескольких файлов дерево не обновлялось)
            annotationService.refreshAfterSave();
        }
    }
    
    private void handleSingleFile(@NotNull AnActionEvent e, @NotNull Project project) {
        VirtualFile file = getSelectedFile(e);
        if (file == null) {
            Messages.showWarningDialog(project, 
                I18nUtils.getText(project, "Выберите файл или папку", "Please select a file or directory"),
                I18nUtils.getText(project, "Добавить заметку", "Add Annotation"));
            return;
        }
        
        AnnotationService annotationService = AnnotationService.getInstance(project);
        
        String existingAnnotation = null;
        if (file.isDirectory()) {
            existingAnnotation = annotationService.getPackageAnnotation(file);
        } else {
            existingAnnotation = annotationService.getAnnotation(file);
        }
        
        String suggestedAnnotation = getSuggestedAnnotation(file, existingAnnotation);
        String initialValue = existingAnnotation != null ? existingAnnotation : 
                             (suggestedAnnotation != null ? suggestedAnnotation : "");
        
        // Текущий цвет
        String existingColor = null;
        String relativePath = getRelativePath(project, file);
        if (relativePath != null) {
            if (file.isDirectory()) {
                existingColor = annotationService.getPackageTextColor(relativePath);
            } else {
                existingColor = annotationService.getFileTextColor(relativePath);
            }
        }
        
        AddAnnotationDialog dialog = new AddAnnotationDialog(project, file.getName(), initialValue, existingColor);
        
        if (dialog.showAndGet()) {
            String annotation = dialog.getAnnotation();
            String selectedColor = dialog.getSelectedColor();
            
            if (annotation.isEmpty()) {
                return;
            }
            
            if (relativePath != null) {
                if (file.isDirectory()) {
                    annotationService.setPackageAnnotationAndRefresh(relativePath, annotation, selectedColor);
                } else {
                    annotationService.setAnnotationAndRefresh(relativePath, annotation, selectedColor);
                }
            } else {
                if (file.isDirectory()) {
                    annotationService.setPackageAnnotationAndRefresh(file.getPath(), annotation, selectedColor);
                } else {
                    annotationService.setAnnotationAndRefresh(file.getPath(), annotation, selectedColor);
                }
            }
            
            // Ручное обновление не нужно: setAnnotationAndRefresh уже всё сделал
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
        AnnotationService service = AnnotationService.getInstance(project);
        if (service != null) {
            String currentLanguage = service.getLanguage();
            if ("en".equals(currentLanguage)) {
                e.getPresentation().setText("Add Annotation");
                e.getPresentation().setDescription("Add user annotation to selected files");
            } else {
                e.getPresentation().setText("Добавить заметку");
                e.getPresentation().setDescription("Добавить свою заметку к выбранному файлу");
            }
        } else {
            e.getPresentation().setText("Добавить заметку");
            e.getPresentation().setDescription("Добавить свою заметку к выбранному файлу");
        }
    }
    
 
    private boolean isMultipleSelection(@NotNull AnActionEvent e) {
        VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        return files != null && files.length > 1;
    }
    

    @Nullable
    private VirtualFile getSelectedFile(@NotNull AnActionEvent e) {
        // 1. Сначала — из массива выбранных файлов
        VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        if (files != null && files.length > 0) {
            return files[0];
        }
        
        // 2. Из PSI-элемента
        PsiElement psiElement = e.getData(CommonDataKeys.PSI_ELEMENT);
        if (psiElement instanceof PsiFile) {
            return ((PsiFile) psiElement).getVirtualFile();
        } else if (psiElement instanceof PsiDirectory) {
            return ((PsiDirectory) psiElement).getVirtualFile();
        }
        
        // 3. Из одиночного виртуального файла
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        if (file != null) {
            return file;
        }
        
        // 4. Из текущего редактора (курсор стоит внутри файла)
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (editor != null) {
            VirtualFile editorFile = getFileFromEditor(editor);
            if (editorFile != null) {
                return editorFile;
            }
        }
        
        // 5. Из активного редактора
        Project project = e.getProject();
        if (project != null) {
            VirtualFile activeFile = getActiveFileFromProject(project);
            if (activeFile != null) {
                return activeFile;
            }
        }
        
        return null;
    }

    @Nullable
    private String getSuggestedAnnotation(@NotNull VirtualFile file, @Nullable String existingAnnotation) {
        if (existingAnnotation != null) {
            return null; // если заметка уже есть, подсказку не предлагаем
        }
        
        MappingLibraryService mappingService = MappingLibraryService.getInstance();
        return mappingService.smartSearch(file.getName());
    }
    
    /**
     * Получает файл из редактора
     */
    @Nullable
    private VirtualFile getFileFromEditor(@NotNull Editor editor) {
        try {
            // Файл через Document
            com.intellij.openapi.fileEditor.FileDocumentManager fileDocumentManager = 
                com.intellij.openapi.fileEditor.FileDocumentManager.getInstance();
            return fileDocumentManager.getFile(editor.getDocument());
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Получает активный файл проекта
     */
    @Nullable
    private VirtualFile getActiveFileFromProject(@NotNull Project project) {
        try {
            FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
            FileEditor[] editors = fileEditorManager.getSelectedEditors();
            
            for (FileEditor editor : editors) {
                if (editor instanceof TextEditor) {
                    TextEditor textEditor = (TextEditor) editor;
                    VirtualFile file = textEditor.getFile();
                    if (file != null) {
                        return file;
                    }
                }
            }
            
            // Если выбранного редактора нет — берём открытый файл
            VirtualFile[] openFiles = fileEditorManager.getOpenFiles();
            if (openFiles.length > 0) {
                return openFiles[0];
            }
        } catch (Exception e) {
            // Ошибку игнорируем, возвращаем null
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
