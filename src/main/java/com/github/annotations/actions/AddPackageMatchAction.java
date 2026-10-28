package com.github.annotations.actions;

import com.github.annotations.ui.AddPackageMatchDialog;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;


public class AddPackageMatchAction extends AnAction {
    
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }
        
        // Имя выбранной папки с префиксом *. для показа в интерфейсе
        String currentFolderName = null;
        VirtualFile selectedFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
        if (selectedFile != null && selectedFile.isDirectory()) {
            currentFolderName = "*." + selectedFile.getName();
        }
        
        AddPackageMatchDialog dialog = new AddPackageMatchDialog(project, currentFolderName);
        if (dialog.showAndGet()) {
        }
    }
    
    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            e.getPresentation().setEnabledAndVisible(false);
            return;
        }
        
        e.getPresentation().setEnabledAndVisible(true);
        
        // Текст пункта меню зависит от текущего языка
        try {
            com.github.annotations.services.AnnotationService service = com.github.annotations.services.AnnotationService.getInstance(project);
            if (service != null) {
                String currentLanguage = service.getLanguage();
                if ("en".equals(currentLanguage)) {
                    e.getPresentation().setText("Add Package Match");
                    e.getPresentation().setDescription("Add package path matching pattern for automatic annotations");
                } else {
                    e.getPresentation().setText("Добавить сопоставление по пути");
                    e.getPresentation().setDescription("Добавить шаблон пути: подходящие пути получат заметку автоматически");
                }
            } else {
                e.getPresentation().setText("Добавить сопоставление по пути");
                e.getPresentation().setDescription("Добавить шаблон пути: подходящие пути получат заметку автоматически");
            }
        } catch (Exception ex) {
            e.getPresentation().setText("Добавить сопоставление по пути");
            e.getPresentation().setDescription("Добавить шаблон пути: подходящие пути получат заметку автоматически");
        }
    }
}
