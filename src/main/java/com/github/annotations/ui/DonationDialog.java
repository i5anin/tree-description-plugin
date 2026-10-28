package com.github.annotations.ui;

import com.github.annotations.utils.I18nUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URI;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;


public class DonationDialog extends DialogWrapper {
    
    private final Project project;
    
    public DonationDialog(@NotNull Project project) {
        super(project);
        this.project = project;
        
        setTitle(I18nUtils.getText(project, "О плагине", "About"));
        setSize(650, 450);
        setResizable(false);
        init();
    }
    
    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(JBUI.Borders.empty(20));
        
        // Основная панель
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        
        // Заголовок
        JLabel titleLabel = new JLabel(I18nUtils.getText(project, "Спасибо, что пользуетесь Tree Description!", "Thank you for using Tree Description!!"));
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 18));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        titleLabel.setBorder(JBUI.Borders.emptyBottom(15));
        
        // Благодарность
        JTextArea thankText = new JTextArea();
        thankText.setText(I18nUtils.getText(project,
                "Если плагин вам помог, поддержите разработчика.\n\n" +
                "Поддержка поможет:\n" +
                "• развивать возможности плагина\n" +
                "• исправлять ошибки и проблемы совместимости\n" +
                "• добавлять полезные функции\n",
                "If this plugin helps you, welcome to support the developer.\n\n" +
                "Your support will help us:\n" +
                "• Continuously improve plugin functionality\n" +
                "• Fix bugs and compatibility issues\n" +
                "• Add more useful features\n"));
        thankText.setEditable(false);
        thankText.setLineWrap(true);
        thankText.setWrapStyleWord(true);
        thankText.setBackground(UIManager.getColor("Panel.background"));
        thankText.setBorder(JBUI.Borders.empty(10));
        thankText.setFont(thankText.getFont().deriveFont(Font.PLAIN, 14));
        
        // Сведения о проекте
        JPanel openSourcePanel = new JPanel(new BorderLayout());
        openSourcePanel.setBorder(BorderFactory.createTitledBorder(I18nUtils.getText(project, "О проекте", "Project Information")));
        
        JTextPane openSourceText = new JTextPane();
        openSourceText.setEditable(false);
        openSourceText.setBackground(openSourcePanel.getBackground());
        openSourceText.setFont(openSourceText.getFont().deriveFont(Font.PLAIN, 13));
        
        // Документ со стилями
        StyledDocument doc = openSourceText.getStyledDocument();
        SimpleAttributeSet normalStyle = new SimpleAttributeSet();
        SimpleAttributeSet linkStyle = new SimpleAttributeSet();
        StyleConstants.setForeground(linkStyle, new Color(0, 102, 204));
        StyleConstants.setUnderline(linkStyle, true);
        
        try {
            // Обычный текст
            doc.insertString(doc.getLength(), I18nUtils.getText(project, "Исходный код открыт, участие приветствуется!\n\n", "Project is open source, welcome to contribute!\n\n"), normalStyle);
            doc.insertString(doc.getLength(), I18nUtils.getText(project, "Основной проект:\n", "Main project address:\n"), normalStyle);
            doc.insertString(doc.getLength(), "GitHub: ", normalStyle);
            doc.insertString(doc.getLength(), "https://github.com/JaysonX-Tech/tree-description-plugin", linkStyle);
            doc.insertString(doc.getLength(), "\n\n", normalStyle);
            doc.insertString(doc.getLength(), I18nUtils.getText(project, "Открытая библиотека сопоставлений:\n", "Open source mapping library address:\n"), normalStyle);
            doc.insertString(doc.getLength(), "GitHub: ", normalStyle);
            doc.insertString(doc.getLength(), "https://github.com/JaysonX-Tech/tree-description-repository", linkStyle);
            doc.insertString(doc.getLength(), "\n\n", normalStyle);
            doc.insertString(doc.getLength(), I18nUtils.getText(project, "Давайте вместе наполнять открытую библиотеку сопоставлений,\n", "Let's build a rich open source mapping library together,\n"), normalStyle);
            doc.insertString(doc.getLength(), I18nUtils.getText(project, "чтобы разработчикам было проще разбираться в проектах!\n\n", "providing better project understanding tools for developers!\n\n"), normalStyle);
            doc.insertString(doc.getLength(), I18nUtils.getText(project, "Если плагин полезен — поставьте звезду на GitHub!\n", "If you find it useful, welcome to give a Star support!!!\n"), normalStyle);
        } catch (BadLocationException e) {
            // Исключение игнорируем
        }
        
        // Слушатель гиперссылок
        openSourceText.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                openGitHubPage();
            }
        });
        
        openSourcePanel.add(openSourceText, BorderLayout.CENTER);
        
        // Панель кнопок
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        buttonPanel.setBorder(JBUI.Borders.emptyTop(20));
        
        // Кнопка «Страница на GitHub»
        JButton githubButton = new JButton(I18nUtils.getText(project, "🌐 Страница на GitHub", "🌐 GitHub Homepage"));
        githubButton.addActionListener(e -> openGitHubPage());
        
        // Кнопка «Поставить звезду»
        JButton starButton = new JButton(I18nUtils.getText(project, "⭐ Поставить звезду", "⭐ Give a Star"));
        starButton.addActionListener(e -> openGitHubPage());
        
        buttonPanel.add(githubButton);
        buttonPanel.add(starButton);
        
        // Собираем панель
        contentPanel.add(titleLabel);
        contentPanel.add(thankText);
        contentPanel.add(Box.createVerticalStrut(15));
        contentPanel.add(openSourcePanel);
        contentPanel.add(Box.createVerticalStrut(10));
        contentPanel.add(buttonPanel);
        
        mainPanel.add(contentPanel, BorderLayout.CENTER);
        return mainPanel;
    }
    
    /**
     * Открывает страницу пожертвований
     */
    private void openDonationPage() {
        try {
            // Сюда можно подставить свою ссылку для пожертвований
            String donationUrl = "https://jaysonx-tech.github.io/TreeDescriptionPage/TreeDescription.html";
            
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(donationUrl));
            } else {
                JOptionPane.showMessageDialog(
                    getContentPane(),
                    I18nUtils.getText(project, "Откройте ссылку вручную:\n\n", "Please manually visit the following link:\n\n") + donationUrl,
                    I18nUtils.getText(project, "Ссылка для пожертвований", "Donation Link"),
                    JOptionPane.INFORMATION_MESSAGE
                );
            }
        } catch (IOException | UnsupportedOperationException e) {
            JOptionPane.showMessageDialog(
                getContentPane(),
                I18nUtils.getText(project, "Не удалось открыть страницу пожертвований, откройте вручную:\n\n", "Unable to open donation page, please visit manually:\n\n") +
                "https://jaysonx-tech.github.io/TreeDescriptionPage/TreeDescription.html",
                I18nUtils.getText(project, "Не удалось открыть", "Open Failed"),
                JOptionPane.ERROR_MESSAGE
            );
        }
    }
    
    /**
     * Открывает страницу на GitHub
     */
    private void openGitHubPage() {
        try {
            String githubUrl = "https://github.com/JaysonX-Tech/tree-description-repository";
            
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(githubUrl));
            } else {
                JOptionPane.showMessageDialog(
                    getContentPane(),
                    I18nUtils.getText(project, "Откройте ссылку вручную:\n\n", "Please manually visit the following link:\n\n") + githubUrl,
                    I18nUtils.getText(project, "Ссылка на GitHub", "GitHub Link"),
                    JOptionPane.ERROR_MESSAGE
                );
            }
        } catch (IOException | UnsupportedOperationException e) {
            JOptionPane.showMessageDialog(
                getContentPane(),
                I18nUtils.getText(project, "Не удалось открыть страницу GitHub, откройте вручную:\n\n", "Unable to open GitHub page, please visit manually:\n\n") +
                "https://github.com/JaysonX-Tech/tree-description-repository",
                I18nUtils.getText(project, "Не удалось открыть", "Open Failed"),
                JOptionPane.ERROR_MESSAGE
            );
        }
    }
}
