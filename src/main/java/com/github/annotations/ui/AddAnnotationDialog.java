package com.github.annotations.ui;

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

/**
 * Диалог добавления заметки с выбором цвета
 * Цвета меток как в Finder на macOS
 */
public class AddAnnotationDialog extends DialogWrapper {
    
    private final Project project;
    private final String fileName;
    private final String initialAnnotation;
    private final String initialColor;
    
    private JBTextField annotationField;
    private ButtonGroup colorButtonGroup;
    private String selectedColor = "#BBBBBB"; // по умолчанию серый
    
    // Цвета в стиле Finder на macOS
    private static final ColorOption[] COLOR_OPTIONS = {
        new ColorOption("Серый", "#BBBBBB", true),   // по умолчанию
        new ColorOption("Синий", "#265DE8", false),
        new ColorOption("Зелёный", "#51B433", false),
        new ColorOption("Красный", "#E93225", false),
        new ColorOption("Оранжевый", "#DE6F36", false),
        new ColorOption("Жёлтый", "#E6B33C", false),
        new ColorOption("Фиолетовый", "#AD8EF8", false),
        new ColorOption("Розовый", "#E95573", false)
    };
    
    public AddAnnotationDialog(@NotNull Project project, @NotNull String fileName, @Nullable String initialAnnotation, @Nullable String initialColor) {
        super(project);
        this.project = project;
        this.fileName = fileName;
        this.initialAnnotation = initialAnnotation != null ? initialAnnotation : "";
        this.initialColor = initialColor != null && isValidColor(initialColor) ? initialColor : "#BBBBBB";
        
        setTitle(I18nUtils.getText(project, "Добавить заметку", "Add Annotation"));
        setSize(480, 180);
        init();
    }
    
    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(JBUI.Borders.empty(15));
        
        // Форма
        JPanel formPanel = FormBuilder.createFormBuilder()
                .addLabeledComponent(I18nUtils.getText(project, "Заметка для \"" + fileName + "\":", "Add annotation for \"" + fileName + "\":"), createAnnotationField())
                .addVerticalGap(15)
                .addLabeledComponent(I18nUtils.getText(project, "Цвет заметки:", "Annotation Color:"), createColorPanel())
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
        
        mainPanel.add(formPanel, BorderLayout.CENTER);
        return mainPanel;
    }
    
    private JComponent createAnnotationField() {
        annotationField = new JBTextField(initialAnnotation);
        annotationField.setPreferredSize(new Dimension(350, 30));
        annotationField.setToolTipText(I18nUtils.getText(project, "Введите текст заметки", "Enter annotation content"));
        
        // Слушатель документа для проверки ввода
        annotationField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) { validateInput(); }
            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) { validateInput(); }
            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) { validateInput(); }
        });
        
        return annotationField;
    }
    
    private JComponent createColorPanel() {
        JPanel colorPanel = new JPanel();
        
        // GridLayout для точных отступов: 1 строка, 8 столбцов
        colorPanel.setLayout(new GridLayout(1, 8, 6, 0));
        colorPanel.setBorder(JBUI.Borders.empty(5));
        
        colorButtonGroup = new ButtonGroup();
        
        for (ColorOption colorOption : COLOR_OPTIONS) {
            JRadioButton colorButton = createColorButton(colorOption);
            colorButtonGroup.add(colorButton);
            colorPanel.add(colorButton);
            
            // Отмечаем начальный цвет или цвет по умолчанию
            if ((initialColor != null && colorOption.colorCode.equals(initialColor)) || 
                (initialColor == null && colorOption.isDefault)) {
                colorButton.setSelected(true);
                selectedColor = colorOption.colorCode;
            }
        }
        
        return colorPanel;
    }
    
    private JRadioButton createColorButton(ColorOption colorOption) {
        JRadioButton button = new JRadioButton();
        button.setPreferredSize(new Dimension(40, 28));
        button.setToolTipText(getColorName(colorOption.name) + " " + colorOption.colorCode);
        
        // Сами рисуем цветной кружок
        button.setUI(new javax.swing.plaf.basic.BasicRadioButtonUI() {
            @Override
            public void paint(Graphics g, JComponent c) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // Положение кружка
                int centerX = c.getWidth() / 2;
                int centerY = c.getHeight() / 2;
                int radius = 8;
                
                // Внешнее кольцо (выбранное состояние)
                if (button.isSelected()) {
                    g2.setColor(new Color(37, 93, 232)); // синее внешнее кольцо
                    g2.setStroke(new BasicStroke(2));
                    g2.drawOval(centerX - radius - 2, centerY - radius - 2, (radius + 2) * 2, (radius + 2) * 2);
                }
                
                // Цветной кружок
                Color color = Color.decode(colorOption.colorCode);
                g2.setColor(color);
                g2.fillOval(centerX - radius, centerY - radius, radius * 2, radius * 2);
                
                // Обводка
                g2.setColor(new Color(200, 200, 200));
                g2.setStroke(new BasicStroke(1));
                g2.drawOval(centerX - radius, centerY - radius, radius * 2, radius * 2);
                
                g2.dispose();
            }
        });
        
        // Слушатель выбора
        button.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                selectedColor = colorOption.colorCode;
            }
        });
        
        return button;
    }
    
    private void validateInput() {
        setOKActionEnabled(!annotationField.getText().trim().isEmpty());
    }
    
    @Override
    protected void doOKAction() {
        super.doOKAction();
    }
    
    @Override
    protected ValidationInfo doValidate() {
        String annotation = annotationField.getText().trim();
        
        if (annotation.isEmpty()) {
            return new ValidationInfo(I18nUtils.getText(project, "Введите текст заметки", "Please enter annotation content"), annotationField);
        }
        
        return null;
    }
    
    /**
     * Возвращает введённую заметку
     */
    public String getAnnotation() {
        return annotationField.getText().trim();
    }
    
    /**
     * Возвращает выбранный цвет
     */
    public String getSelectedColor() {
        return selectedColor;
    }
    
    /**
     * Выбран ли цвет по умолчанию (серый)
     */
    public boolean isDefaultColor() {
        return "#BBBBBB".equals(selectedColor);
    }
    
    /**
     * Есть ли цвет в списке предопределённых
     */
    private boolean isValidColor(String colorCode) {
        if (colorCode == null) return false;
        for (ColorOption option : COLOR_OPTIONS) {
            if (option.colorCode.equals(colorCode)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Название цвета на текущем языке
     */
    private String getColorName(String colorName) {
        switch (colorName) {
            case "Серый": return I18nUtils.getText(project, "Серый", "Gray");
            case "Синий": return I18nUtils.getText(project, "Синий", "Blue");
            case "Зелёный": return I18nUtils.getText(project, "Зелёный", "Green");
            case "Красный": return I18nUtils.getText(project, "Красный", "Red");
            case "Оранжевый": return I18nUtils.getText(project, "Оранжевый", "Orange");
            case "Жёлтый": return I18nUtils.getText(project, "Жёлтый", "Yellow");
            case "Фиолетовый": return I18nUtils.getText(project, "Фиолетовый", "Purple");
            case "Розовый": return I18nUtils.getText(project, "Розовый", "Pink");
            default: return colorName;
        }
    }
    
    /**
     * Вариант цвета
     */
    private static class ColorOption {
        final String name;
        final String colorCode;
        final boolean isDefault;
        
        ColorOption(String name, String colorCode, boolean isDefault) {
            this.name = name;
            this.colorCode = colorCode;
            this.isDefault = isDefault;
        }
    }
}
