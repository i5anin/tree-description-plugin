package com.github.annotations.ui;

import com.github.annotations.utils.MappingsPaths;
import com.github.annotations.services.AnnotationService;
import com.github.annotations.utils.I18nUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Map;
import java.awt.Font;

/**
 * Диалог добавления шаблона пакета
 * Пользователь задаёт правило сопоставления по пакету
 */
public class AddPackageMatchDialog extends DialogWrapper {
    
    private final Project project;
    private final AnnotationService annotationService;
    private final String defaultFolderName;
    
    private JBTextField patternField;
    private JBTextField descriptionField;
    
    public AddPackageMatchDialog(@NotNull Project project) {
        this(project, null);
    }
    
    public AddPackageMatchDialog(@NotNull Project project, @Nullable String defaultFolderName) {
        super(project);
        this.project = project;
        this.annotationService = AnnotationService.getInstance(project);
        this.defaultFolderName = defaultFolderName;
        
        setTitle(I18nUtils.getText(project, "Добавить сопоставление по пути", "Add Package Match Mapping"));
        setSize(540, 200);
        init();
    }
    
    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(JBUI.Borders.empty(10));
        
        // Форма
        JPanel formPanel = FormBuilder.createFormBuilder()
                .addLabeledComponent(I18nUtils.getText(project, "Шаблон пути:", "Match Package:"), createPatternField())
                .addLabeledComponent(I18nUtils.getText(project, "Текст заметки:", "Annotation Description:"), createDescriptionField())
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
        
        // Справка по использованию
        JPanel helpPanel = createHelpPanel();
        mainPanel.add(helpPanel, BorderLayout.SOUTH);
        
        mainPanel.add(formPanel, BorderLayout.CENTER);
        return mainPanel;
    }
    
    private JComponent createPatternField() {
        patternField = new JBTextField();
        
        // Если передано имя папки по умолчанию — подставляем в поле
        if (defaultFolderName != null && !defaultFolderName.isEmpty()) {
            patternField.setText(defaultFolderName);
        }
        
        patternField.setToolTipText(I18nUtils.getText(project, "Шаблон имени пакета, например: controller, service, dao", "Enter package match pattern, e.g.: controller, service, dao"));
        patternField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) { validateInput(); }
            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) { validateInput(); }
            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) { validateInput(); }
        });
        return patternField;
    }
    
    private JComponent createDescriptionField() {
        descriptionField = new JBTextField();
        descriptionField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) { validateInput(); }
            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) { validateInput(); }
            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) { validateInput(); }
        });
        return descriptionField;
    }
    

    
    private JPanel createHelpPanel() {
        JPanel helpPanel = new JPanel(new BorderLayout());
        helpPanel.setBorder(BorderFactory.createTitledBorder(I18nUtils.getText(project, "Как пользоваться", "Usage Instructions")));
        
        JTextArea helpText = new JTextArea();
        helpText.setText(I18nUtils.getText(project,
            "• заметка ставится на папки, подходящие под шаблон\n" +
            "• сопоставления хранятся в: " + MappingsPaths.MAPPINGS_DIR + "/" + MappingsPaths.LOCAL_DESCRIPTION_FILE + "\n" +
            "• например, springframework.core — все пакеты, оканчивающиеся на *.springframework.core",
            "• Will match the corresponding directories\n" +
            "• Mapping save location: " + MappingsPaths.MAPPINGS_DIR + "/" + MappingsPaths.LOCAL_DESCRIPTION_FILE + "\n" +
            "• Example: springframework.core will match all packages ending with *.springframework.core"));
        helpText.setEditable(false);
        helpText.setLineWrap(true);
        helpText.setWrapStyleWord(true);
        helpText.setBackground(UIManager.getColor("Panel.background"));
        helpText.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        helpText.setFont(helpText.getFont().deriveFont(Font.PLAIN, 11));
        
        helpPanel.add(helpText, BorderLayout.CENTER);
        return helpPanel;
    }
    
    private void validateInput() {
        setOKActionEnabled(!patternField.getText().trim().isEmpty() && 
                          !descriptionField.getText().trim().isEmpty());
    }
    
    @Override
    protected void doOKAction() {
        String pattern = patternField.getText().trim();
        String description = descriptionField.getText().trim();
        
        if (pattern.isEmpty() || description.isEmpty()) {
            return;
        }
        
        // Если шаблон начинается с *., сохраняем его без этого префикса
        String savePattern = pattern;
        if (pattern.startsWith("*.")) {
            savePattern = pattern.substring(2);
        }
        
        // Существующий шаблон перезаписываем без подтверждения
        
        // Сохраняем в AnnotationService (шаблон без префикса *.)
        annotationService.setPackageMatchAnnotation(savePattern, description);
        
        // Обновляем, чтобы JSON-файл и интерфейс совпадали
        try {
            java.lang.reflect.Method refreshMethod = annotationService.getClass().getDeclaredMethod("refreshAfterSave");
            refreshMethod.setAccessible(true);
            refreshMethod.invoke(annotationService);
        } catch (Exception ex) {
            // Если вызов через рефлексию не удался — простое обновление дерева проекта
            com.intellij.ide.projectView.ProjectView.getInstance(project).refresh();
        }
        
        super.doOKAction();
    }
    
    @Override
    protected ValidationInfo doValidate() {
        String pattern = patternField.getText().trim();
        String description = descriptionField.getText().trim();
        
        if (pattern.isEmpty()) {
            return new ValidationInfo(I18nUtils.getText(project, "Введите шаблон", "Please enter match pattern"), patternField);
        }
        
        if (description.isEmpty()) {
            return new ValidationInfo(I18nUtils.getText(project, "Введите текст заметки", "Please enter annotation description"), descriptionField);
        }
        
        // Нет ли в шаблоне служебных символов
        if (pattern.contains("/") || pattern.contains("\\") || pattern.contains(":")) {
            return new ValidationInfo(I18nUtils.getText(project, "Шаблон не может содержать разделители пути и служебные символы", "Match pattern cannot contain path separators or special characters"), patternField);
        }
        
        return null;
    }
}
