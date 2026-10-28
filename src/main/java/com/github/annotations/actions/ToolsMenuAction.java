package com.github.annotations.actions;

import com.github.annotations.services.AnnotationService;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Группа меню «Инструменты заметок»
 * Текст меняется вместе с языком
 */
public class ToolsMenuAction extends ActionGroup {
    
    public ToolsMenuAction() {
        super("Инструменты заметок", true);
        // Иконка задаётся в plugin.xml
    }
    
    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project != null) {
            AnnotationService service = AnnotationService.getInstance(project);
            if (service != null) {
                String currentLanguage = service.getLanguage();
                if ("en".equals(currentLanguage)) {
                    e.getPresentation().setText("Annotation Tools");
                } else {
                    e.getPresentation().setText("Инструменты заметок");
                }
            }
        }
    }
    
    @NotNull
    @Override
    public AnAction[] getChildren(@Nullable AnActionEvent e) {
        // Действия берём из ActionManager, чтобы показывались сочетания клавиш
        ActionManager actionManager = ActionManager.getInstance();
        
        return new AnAction[]{
            new LanguageSubMenuAction(),
            Separator.getInstance(),  // разделитель
            new ToggleProjectTreeAnnotationsAction(),
            actionManager.getAction("ChineseAnnotations.SearchAnnotations"),
            Separator.getInstance(),  // разделитель
            new ViewMappingsAction(),
            Separator.getInstance(),  // разделитель
            new AboutPluginAction()
        };
    }
}
