package com.github.annotations.actions;

import com.github.annotations.ui.AddFileMatchDialog;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class AddFileMatchAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        // Имя выбранного файла с префиксом *. для показа в интерфейсе
        String currentFileName = null;
        VirtualFile selectedFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
        if (selectedFile != null && !selectedFile.isDirectory()) {
            currentFileName = "*." + selectedFile.getName();
        }

        AddFileMatchDialog dialog = new AddFileMatchDialog(project, currentFileName);
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
            com.github.annotations.services.AnnotationService service = com.github.annotations.services.AnnotationService
                    .getInstance(project);
            if (service != null) {
                String currentLanguage = service.getLanguage();
                if ("en".equals(currentLanguage)) {
                    e.getPresentation().setText("Add File Match");
                    e.getPresentation().setDescription("Add filename matching pattern for automatic annotations");
                } else {
                    e.getPresentation().setText("Добавить сопоставление по файлу");
                    e.getPresentation().setDescription("Добавить шаблон имени файла: подходящие файлы получат заметку автоматически");
                }
            } else {
                e.getPresentation().setText("Добавить сопоставление по файлу");
                e.getPresentation().setDescription("Добавить шаблон имени файла: подходящие файлы получат заметку автоматически");
            }
        } catch (Exception ex) {
            e.getPresentation().setText("Добавить сопоставление по файлу");
            e.getPresentation().setDescription("Добавить шаблон имени файла: подходящие файлы получат заметку автоматически");
        }
    }
}
