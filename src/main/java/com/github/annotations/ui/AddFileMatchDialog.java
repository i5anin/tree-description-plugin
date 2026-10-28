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
 * Диалог добавления шаблона файла
 * Пользователь задаёт правило сопоставления по имени файла
 */
public class AddFileMatchDialog extends DialogWrapper {
    
    private final Project project;
    private final AnnotationService annotationService;
    private final String defaultFileName;
    
    private JBTextField patternField;
    private JBTextField descriptionField;
    
    public AddFileMatchDialog(@NotNull Project project) {
        this(project, null);
    }
    
    public AddFileMatchDialog(@NotNull Project project, @Nullable String defaultFileName) {
        super(project);
        this.project = project;
        this.annotationService = AnnotationService.getInstance(project);
        this.defaultFileName = defaultFileName;
        
        setTitle(I18nUtils.getText(project, "Добавить сопоставление по файлу", "Add File Match Mapping"));
        setSize(540, 200);
        init();
    }
    
    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(JBUI.Borders.empty(10));
        
        // Форма
        JPanel formPanel = FormBuilder.createFormBuilder()
                .addLabeledComponent(I18nUtils.getText(project, "Шаблон файла:", "Match File:"), createPatternField())
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
        
        // Если передано имя файла по умолчанию — подставляем в поле
        if (defaultFileName != null && !defaultFileName.trim().isEmpty()) {
            patternField.setText(defaultFileName);
        }
        
        patternField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) { validateInput(); }
            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) { validateInput(); }
            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) { validateInput(); }
        });
        
        // Значение по умолчанию задано — запускаем проверку
        if (defaultFileName != null && !defaultFileName.trim().isEmpty()) {
            SwingUtilities.invokeLater(this::validateInput);
        }
        
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
            "• заметка ставится на файлы, подходящие под шаблон\n" +
            "• сопоставления хранятся в: " + MappingsPaths.MAPPINGS_DIR + "/" + MappingsPaths.LOCAL_DESCRIPTION_FILE + "\n" +
            "• pom.xml — все файлы с именем pom.xml\n" +
            "• com/common/pom.xml — файл pom.xml в пакете com.common",
            "• Will match the corresponding files\n" +
            "• Mapping save location: " + MappingsPaths.MAPPINGS_DIR + "/" + MappingsPaths.LOCAL_DESCRIPTION_FILE + "\n" +
            "• pom.xml will match all files named pom.xml\n" +
            "• com/common/pom.xml will match pom.xml files in com.common package"));
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
        
        // Разбираем префикс с подстановочными знаками, поддерживаем несколько форматов
        String savePattern = pattern;
        if (pattern.startsWith("*/")) {
            savePattern = pattern.substring(2);
        } else if (pattern.startsWith("*.")) {
            savePattern = pattern.substring(2);
        } else if (pattern.startsWith("*") && !pattern.startsWith("*.") && !pattern.startsWith("*/")) {
            savePattern = pattern.substring(1);
        }
        
        // Существующий шаблон перезаписываем без подтверждения
        
        // Сохраняем в AnnotationService (шаблон без префикса *.)
        annotationService.setFileMatchAnnotation(savePattern, description);
        
        // Обновляем, чтобы JSON-файл и интерфейс совпадали
        annotationService.refreshAfterSave();
        
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
        
        // Нет ли в шаблоне запрещённых символов (разделитель пути теперь разрешён)
        if (pattern.contains("\\") || pattern.contains(":")) {
            return new ValidationInfo(I18nUtils.getText(project, "Шаблон не может содержать обратную косую черту или двоеточие", "Match pattern cannot contain backslashes or colons"), patternField);
        }
        
        return null;
    }
}
