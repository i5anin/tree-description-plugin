package com.github.annotations.ui;

import com.github.annotations.utils.I18nUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Диалог извлечения комментария в заметку: выбор цвета и предпросмотр текста
 * Выбор цвета устроен так же, как в AddAnnotationDialog
 */
public class ExtractCommentDialog extends DialogWrapper {
    
    private final Project project;
    private final List<String> extractedComments;
    private final int fileCount;
    
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
    
    public ExtractCommentDialog(@NotNull Project project, @NotNull List<String> extractedComments, int fileCount) {
        super(project);
        this.project = project;
        this.extractedComments = extractedComments;
        this.fileCount = fileCount;
        
        setTitle(I18nUtils.getText(project, "Извлечь комментарий в заметку", "Extract Comment as Remark"));
        setSize(400, 200);
        init();
    }
    
    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(JBUI.Borders.empty(10));
        
        // Форма
        JPanel formPanel = FormBuilder.createFormBuilder()
                .addLabeledComponent(createInfoLabel(), createContentPreview())
                .addVerticalGap(8)
                .addLabeledComponent(I18nUtils.getText(project, "Цвет заметки:", "Annotation Color:"), createColorPanel())
                .getPanel();
        
        mainPanel.add(formPanel, BorderLayout.CENTER);
        return mainPanel;
    }
    
    private JComponent createInfoLabel() {
        String labelText;
        if (fileCount == 1) {
            labelText = I18nUtils.getText(project, "Заметка из комментария класса:", "Extract Class Annotation:");
        } else {
            labelText = I18nUtils.getText(project, 
                String.format("Заметок: %d", fileCount),
                String.format("Annotation for %d files:", fileCount));
        }
        return new JBLabel(labelText);
    }
    
    private JComponent createContentPreview() {
        JBTextArea textArea = new JBTextArea();
        textArea.setEditable(false);
        textArea.setBackground(UIManager.getColor("Panel.background"));
        textArea.setFont(UIManager.getFont("Label.font"));
        textArea.setBorder(JBUI.Borders.empty(6));
        
        StringBuilder content = new StringBuilder();
        if (extractedComments.size() == 1) {
            // Один файл — показываем извлечённый комментарий
            content.append(extractedComments.get(0));
        } else {
            // Несколько файлов — число файлов и начало содержимого
            content.append(I18nUtils.getText(project, 
                String.format("Найдены комментарии в %d файлах:\n\n", extractedComments.size()),
                String.format("Found comments in %d files:\n\n", extractedComments.size())));
            
            int previewCount = Math.min(3, extractedComments.size());
            for (int i = 0; i < previewCount; i++) {
                content.append("• ").append(extractedComments.get(i)).append("\n");
            }
            
            if (extractedComments.size() > 3) {
                content.append(I18nUtils.getText(project, 
                    String.format("... и ещё файлов: %d", extractedComments.size() - 3),
                    String.format("... and %d more files", extractedComments.size() - 3)));
            }
        }
        
        textArea.setText(content.toString());
        textArea.setCaretPosition(0);
        
        JBScrollPane scrollPane = new JBScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(450, 35));
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        
        return scrollPane;
    }
    
    private JComponent createColorPanel() {
        JPanel colorPanel = new JPanel();
        
        // GridLayout для точных отступов: 1 строка, 8 столбцов
        colorPanel.setLayout(new GridLayout(1, 8, 4, 0));
        colorPanel.setBorder(JBUI.Borders.empty(3));
        
        colorButtonGroup = new ButtonGroup();
        
        for (ColorOption colorOption : COLOR_OPTIONS) {
            JRadioButton colorButton = createColorButton(colorOption);
            colorButtonGroup.add(colorButton);
            colorPanel.add(colorButton);
            
            // По умолчанию выбран серый
            if (colorOption.isDefault) {
                colorButton.setSelected(true);
                selectedColor = colorOption.colorCode;
            }
        }
        
        return colorPanel;
    }
    
    private JRadioButton createColorButton(ColorOption colorOption) {
        JRadioButton button = new JRadioButton();
        button.setPreferredSize(new Dimension(36, 24));
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
    
    @Override
    protected ValidationInfo doValidate() {
        if (extractedComments.isEmpty()) {
            return new ValidationInfo(I18nUtils.getText(project, "Комментарии для извлечения не найдены", "No extractable comments found"));
        }
        return null;
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
     * Название цвета на текущем языке
     */
    private String getColorName(String chineseName) {
        switch (chineseName) {
            case "Серый": return I18nUtils.getText(project, "Серый", "Gray");
            case "Синий": return I18nUtils.getText(project, "Синий", "Blue");
            case "Зелёный": return I18nUtils.getText(project, "Зелёный", "Green");
            case "Красный": return I18nUtils.getText(project, "Красный", "Red");
            case "Оранжевый": return I18nUtils.getText(project, "Оранжевый", "Orange");
            case "Жёлтый": return I18nUtils.getText(project, "Жёлтый", "Yellow");
            case "Фиолетовый": return I18nUtils.getText(project, "Фиолетовый", "Purple");
            case "Розовый": return I18nUtils.getText(project, "Розовый", "Pink");
            default: return chineseName;
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