package com.github.annotations.actions;

import com.github.annotations.ui.DonationDialog;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class AboutPluginAction extends AnAction {
    
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }
        
        DonationDialog dialog = new DonationDialog(project);
        dialog.show();
    }
    
    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setVisible(true);
        e.getPresentation().setEnabled(true);
        
        // Текст пункта меню зависит от текущего языка
        Project project = e.getProject();
        if (project != null) {
            try {
                com.github.annotations.services.AnnotationService service = com.github.annotations.services.AnnotationService.getInstance(project);
                if (service != null) {
                    String currentLanguage = service.getLanguage();
                    if ("en".equals(currentLanguage)) {
                        e.getPresentation().setText("About");
                        e.getPresentation().setDescription("Support developer, About plugin");
                    } else {
                        e.getPresentation().setText("О плагине");
                        e.getPresentation().setDescription("Поддержать разработчика, о плагине");
                    }
                } else {
                    e.getPresentation().setText("About");
                    e.getPresentation().setDescription("Support developer, About plugin");
                }
            } catch (Exception ex) {
                e.getPresentation().setText("About");
                e.getPresentation().setDescription("Support developer, About plugin");
            }
        } else {
            e.getPresentation().setText("About");
            e.getPresentation().setDescription("Support developer, About plugin");
        }
    }
}

