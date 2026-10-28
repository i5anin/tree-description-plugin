package com.github.annotations.settings;

import com.github.annotations.services.AnnotationService;
import com.github.annotations.utils.LanguageManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * Страница настроек плагина
 */
public class AnnotationConfigurable implements Configurable {
    
    private final Project project;
    private JComboBox<LanguageManager.Language> languageComboBox;
    private JCheckBox projectTreeAnnotationsCheckBox;
    private JCheckBox builtinMappingsCheckBox;
    
    public AnnotationConfigurable(Project project) {
        this.project = project;
    }
    
    @Nls
    @Override
    public String getDisplayName() {
        return "Заметки путей";
    }
    
    @Nullable
    @Override
    public JComponent createComponent() {
        // Выпадающий список языка
        languageComboBox = new JComboBox<>(LanguageManager.Language.values());
        languageComboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof LanguageManager.Language) {
                    setText(((LanguageManager.Language) value).getDisplayName());
                }
                return this;
            }
        });
        
        // Переключатель заметок в дереве проекта
        projectTreeAnnotationsCheckBox = new JCheckBox("Показывать заметки в дереве проекта");
        
        // Переключатель встроенной библиотеки сопоставлений
        builtinMappingsCheckBox = new JCheckBox("Включить встроенную библиотеку сопоставлений");
        
        // Разметка через FormBuilder
        JPanel panel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Язык:", languageComboBox)
            .addComponent(projectTreeAnnotationsCheckBox)
            .addComponent(builtinMappingsCheckBox)
            .addComponentFillVertically(new JPanel(), 0)
            .getPanel();
        
        panel.setPreferredSize(new Dimension(500, 200));
        
        // Текущие значения
        reset();
        
        return panel;
    }
    
    @Override
    public void reset() {
        AnnotationService service = AnnotationService.getInstance(project);
        if (service == null) return;
        
        // Выбор языка
        String currentLanguage = service.getLanguage();
        for (LanguageManager.Language lang : LanguageManager.Language.values()) {
            if (lang.getCode().equals(currentLanguage)) {
                languageComboBox.setSelectedItem(lang);
                break;
            }
        }
        
        // Переключатель заметок в дереве проекта
        projectTreeAnnotationsCheckBox.setSelected(service.isProjectTreeAnnotationsEnabled());
        
        // Переключатель встроенной библиотеки сопоставлений
        builtinMappingsCheckBox.setSelected(service.isBuiltinMappingsEnabled());
    }
    
    @Override
    public boolean isModified() {
        AnnotationService service = AnnotationService.getInstance(project);
        if (service == null) return false;
        
        // Изменился ли язык
        LanguageManager.Language selectedLanguage = (LanguageManager.Language) languageComboBox.getSelectedItem();
        if (selectedLanguage != null && !selectedLanguage.getCode().equals(service.getLanguage())) {
            return true;
        }
        
        // Изменился ли переключатель заметок в дереве
        if (projectTreeAnnotationsCheckBox.isSelected() != service.isProjectTreeAnnotationsEnabled()) {
            return true;
        }
        
        // Изменился ли переключатель встроенной библиотеки
        if (builtinMappingsCheckBox.isSelected() != service.isBuiltinMappingsEnabled()) {
            return true;
        }
        
        return false;
    }
    
    @Override
    public void apply() {
        AnnotationService service = AnnotationService.getInstance(project);
        if (service == null) return;
        
        // Применяем язык
        LanguageManager.Language selectedLanguage = (LanguageManager.Language) languageComboBox.getSelectedItem();
        if (selectedLanguage != null) {
            service.setLanguage(selectedLanguage.getCode());
        }
        
        // Применяем переключатель заметок в дереве
        service.setProjectTreeAnnotationsEnabled(projectTreeAnnotationsCheckBox.isSelected());
        
        // Применяем переключатель встроенной библиотеки
        service.setBuiltinMappingsEnabled(builtinMappingsCheckBox.isSelected());
    }
}

