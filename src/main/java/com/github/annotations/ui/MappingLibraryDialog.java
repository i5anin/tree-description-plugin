package com.github.annotations.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.github.annotations.utils.MappingsPaths;
import com.github.annotations.services.GitHubMappingService;
import com.github.annotations.services.GitHubMappingService.GitHubMappingFile;
import com.github.annotations.services.GitHubMappingService.GitHubMappingLibrary;
import com.github.annotations.services.VFSRefreshService;
import com.github.annotations.utils.I18nUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.Nullable;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.util.List;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Диалог открытой библиотеки сопоставлений
 * Показывает содержимое открытой библиотеки сопоставлений на GitHub
 */
public class MappingLibraryDialog extends DialogWrapper {

    private static final Logger LOG = Logger.getInstance(MappingLibraryDialog.class);
    
    private final Project project;
    private JBTable mappingTable;
    private JComboBox<GitHubMappingFile> libraryComboBox;
    private JButton downloadButton;
    private final GitHubMappingService gitHubService;
    private final VFSRefreshService vfsRefreshService;
    
    public MappingLibraryDialog(@Nullable Project project) {
        super(project);
        this.project = project;
        this.gitHubService = new GitHubMappingService();
        this.vfsRefreshService = new VFSRefreshService(project);
        setTitle(I18nUtils.getText(project, "Открытая библиотека сопоставлений на GitHub", "View GitHub Open Source Mapping Library"));
        setSize(900, 700);
        init();
    }
    
    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        
        JPanel topPanel = createLibrarySelectionPanel();
        mainPanel.add(topPanel, BorderLayout.NORTH);
        
        JPanel centerPanel = createMappingTablePanel();
        mainPanel.add(centerPanel, BorderLayout.CENTER);
        
        JPanel bottomPanel = createInfoPanel();
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
        
        loadGitHubLibraries();
        
        return mainPanel;
    }
    
    /**
     * Панель выбора библиотеки сопоставлений
     */
    private JPanel createLibrarySelectionPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        
        panel.add(new JLabel(I18nUtils.getText(project, "Библиотека сопоставлений:", "Select Mapping Library:")));
        
        libraryComboBox = new JComboBox<>();
        libraryComboBox.addActionListener(e -> loadSelectedLibraryMappings());
        panel.add(libraryComboBox);
        
        downloadButton = new JButton(I18nUtils.getText(project, "Скачать в проект", "Download to Local"));
        downloadButton.addActionListener(e -> downloadSelectedLibraryToLocal());
        panel.add(downloadButton);
        
        JButton refreshButton = new JButton(I18nUtils.getText(project, "Обновить", "Refresh"));
        refreshButton.addActionListener(e -> loadGitHubLibraries());
        panel.add(refreshButton);
        
        return panel;
    }
    
    /**
     * Панель с таблицей сопоставлений
     */
    private JPanel createMappingTablePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        String[] columnNames = {
            I18nUtils.getText(project, "Тип", "Type"),
            I18nUtils.getText(project, "Имя", "Name"),
            I18nUtils.getText(project, "Описание", "Mapping")
        };
        DefaultTableModel tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        
        mappingTable = new JBTable(tableModel);
        mappingTable.getTableHeader().setReorderingAllowed(false);
        mappingTable.setRowHeight(25);
        
        mappingTable.getColumnModel().getColumn(0).setPreferredWidth(100);
        mappingTable.getColumnModel().getColumn(1).setPreferredWidth(250);
        mappingTable.getColumnModel().getColumn(2).setPreferredWidth(250);
        
        panel.add(new JBScrollPane(mappingTable), BorderLayout.CENTER);
        return panel;
    }
    
    /**
     * Панель с пояснением
     */
    private JPanel createInfoPanel() {
        JPanel infoPanel = new JPanel(new BorderLayout());
        
        JTextPane infoArea = new JTextPane();
        infoArea.setEditable(false);
        infoArea.setBackground(infoPanel.getBackground());
        infoArea.setBorder(JBUI.Borders.empty(10));
        infoArea.setFont(infoArea.getFont().deriveFont(Font.PLAIN, 13));
        
        // Документ со стилями
        StyledDocument doc = infoArea.getStyledDocument();
        SimpleAttributeSet normalStyle = new SimpleAttributeSet();
        SimpleAttributeSet linkStyle = new SimpleAttributeSet();
        StyleConstants.setForeground(linkStyle, new Color(0, 102, 204));
        StyleConstants.setUnderline(linkStyle, true);
        
        try {
            // Обычный текст
            doc.insertString(doc.getLength(), I18nUtils.getText(project, "Библиотека сопоставлений берётся из открытого репозитория на GitHub: ", "Mapping library comes from GitHub open source repository: "), normalStyle);
            doc.insertString(doc.getLength(), "https://github.com/JaysonX-Tech/tree-description-repository", linkStyle);
            doc.insertString(doc.getLength(), " \n" + I18nUtils.getText(project, "Если скачивание не работает, скачайте файл с GitHub вручную", "If you encounter download issues, please go to Github to download manually."), normalStyle);
            doc.insertString(doc.getLength(), " \n" + I18nUtils.getText(project, "Пул-реквесты приветствуются!", "Welcome to submit PR to contribute!!"), normalStyle);
        } catch (BadLocationException e) {
            // Вставка в конец документа не может выйти за границы — это ошибка в коде
            LOG.error(e);
        }
        
        // Слушатель гиперссылок
        infoArea.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                openGitHubPage();
            }
        });
        
        infoPanel.add(infoArea, BorderLayout.CENTER);
 
        return infoPanel;
    }
    
    /**
     * Загружает список библиотек сопоставлений с GitHub
     */
    private void loadGitHubLibraries() {
        SwingUtilities.invokeLater(() -> {
            libraryComboBox.removeAllItems();
            libraryComboBox.addItem(new GitHubMappingFile("", I18nUtils.getText(project, "Загрузка...", "Loading..."), false));
            
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                try {
                    List<GitHubMappingFile> files = gitHubService.getRepositoryContents();
                    
                    SwingUtilities.invokeLater(() -> {
                        libraryComboBox.removeAllItems();
                        
                        if (files.isEmpty()) {
                            libraryComboBox.addItem(new GitHubMappingFile("", "(нет доступных библиотек сопоставлений)", false));
                            clearMappingTable();
                        } else {
                            for (GitHubMappingFile file : files) {
                                libraryComboBox.addItem(file);
                            }
                            
                            if (libraryComboBox.getItemCount() > 0) {
                                libraryComboBox.setSelectedIndex(0);
                                loadSelectedLibraryMappings();
                            }
                        }
                    });
                } catch (Exception e) {
                    LOG.warn("Не удалось получить список библиотек с GitHub: " + e.getMessage());
                    SwingUtilities.invokeLater(() -> {
                        libraryComboBox.removeAllItems();
                        String errorMessage;
                        if (e.getMessage() != null && e.getMessage().contains("лимит запросов")) {
                            errorMessage = "(превышен лимит запросов GitHub API, повторите позже)";
                        } else if (e.getMessage() != null && e.getMessage().contains("отказал в доступе")) {
                            errorMessage = "(GitHub API отказал в доступе)";
                        } else {
                            errorMessage = "(не удалось загрузить: проверьте сетевое подключение)";
                        }
                        libraryComboBox.addItem(new GitHubMappingFile("", errorMessage, false));
                        clearMappingTable();
                    });
                }
            });
        });
    }
    
    /**
     * Загружает сопоставления выбранной библиотеки
     */
    private void loadSelectedLibraryMappings() {
        GitHubMappingFile selectedFile = (GitHubMappingFile) libraryComboBox.getSelectedItem();
        if (selectedFile == null || selectedFile.path.isEmpty() || 
            selectedFile.path.contains("Загрузка") || selectedFile.path.contains("нет доступных") ||
            selectedFile.path.contains("не удалось загрузить")) {
            clearMappingTable();
            return;
        }
        
        clearMappingTable();
        DefaultTableModel model = (DefaultTableModel) mappingTable.getModel();
        model.addRow(new Object[]{"Загрузка", selectedFile.path, "Скачивание..."});
        
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                String jsonContent = gitHubService.downloadMappingFile(selectedFile.path);
                if (jsonContent != null) {
                    GitHubMappingLibrary library = gitHubService.parseMappingFile(jsonContent);
                    if (library != null) {
                        SwingUtilities.invokeLater(() -> loadLibraryMappings(library));
                    } else {
                        SwingUtilities.invokeLater(() -> {
                            clearMappingTable();
                            model.addRow(new Object[]{"Ошибка", selectedFile.path, "Не удалось разобрать"});
                        });
                    }
                } else {
                    SwingUtilities.invokeLater(() -> {
                        clearMappingTable();
                        model.addRow(new Object[]{"Ошибка", selectedFile.path, "Не удалось скачать"});
                    });
                }
            } catch (Exception e) {
                LOG.warn("Не удалось загрузить библиотеку " + selectedFile.path + ": " + e.getMessage());
                SwingUtilities.invokeLater(() -> {
                    clearMappingTable();
                    model.addRow(new Object[]{"Ошибка", selectedFile.path, "Не удалось загрузить"});
                });
            }
        });
    }
    
    /**
     * Заполняет таблицу данными указанной библиотеки сопоставлений
     */
    private void loadLibraryMappings(GitHubMappingLibrary library) {
        DefaultTableModel model = (DefaultTableModel) mappingTable.getModel();
        model.setRowCount(0);
        
        if (library.mappings == null) {
            model.addRow(new Object[]{"Сведения", library.name != null ? library.name : "Неизвестно", "Нет сопоставлений"});
            return;
        }
        
        if (library.mappings.packages != null) {
            for (java.util.Map.Entry<String, String> entry : library.mappings.packages.entrySet()) {
                model.addRow(new Object[]{
                    "Пакет",
                    entry.getKey(),
                    entry.getValue()
                });
            }
        }
        
        if (library.mappings.files != null) {
            for (java.util.Map.Entry<String, String> entry : library.mappings.files.entrySet()) {
                model.addRow(new Object[]{
                    "Файл",
                    entry.getKey(),
                    entry.getValue()
                });
            }
        }
        
        if (library.mappings.packageMatch != null) {
            for (java.util.Map.Entry<String, String> entry : library.mappings.packageMatch.entrySet()) {
                model.addRow(new Object[]{
                    "Шаблон пакета",
                    entry.getKey(),
                    entry.getValue()
                });
            }
        }
        
        if (library.mappings.fileMatch != null) {
            for (java.util.Map.Entry<String, String> entry : library.mappings.fileMatch.entrySet()) {
                model.addRow(new Object[]{
                    "Шаблон файла",
                    entry.getKey(),
                    entry.getValue()
                });
            }
        }
        
        if (model.getRowCount() == 0) {
            model.addRow(new Object[]{"Сведения", library.name != null ? library.name : "Неизвестно", "Нет сопоставлений"});
        }
    }
    
    /**
     * Очищает таблицу сопоставлений
     */
    private void clearMappingTable() {
        DefaultTableModel model = (DefaultTableModel) mappingTable.getModel();
        model.setRowCount(0);
    }
    
    @Override
    protected Action[] createActions() {
        return new Action[]{getOKAction()};
    }

    /**
     * Скачивает выбранную библиотеку сопоставлений в проект
     */
    private void downloadSelectedLibraryToLocal() {
        GitHubMappingFile selectedFile = (GitHubMappingFile) libraryComboBox.getSelectedItem();
        if (selectedFile == null || selectedFile.path.isEmpty() || 
            selectedFile.path.contains("Загрузка") || selectedFile.path.contains("нет доступных") ||
            selectedFile.path.contains("не удалось загрузить")) {
            JOptionPane.showMessageDialog(getContentPane(), 
                I18nUtils.getText(project, "Сначала выберите файл библиотеки сопоставлений", "Please select a valid mapping library file first"),
                I18nUtils.getText(project, "Подсказка", "Tip"), JOptionPane.PLAIN_MESSAGE);
            return;
        }
        
        // Окно подтверждения
        int result = JOptionPane.showConfirmDialog(getContentPane(), 
            I18nUtils.getText(project,
                "Скачать \"" + selectedFile.displayName + "\" в папку " + MappingsPaths.MAPPINGS_DIR + " проекта?\n\n" +
                "Имя файла: " + selectedFile.displayName + "\n" +
                "Куда: " + MappingsPaths.MAPPINGS_DIR + "/",
            "Are you sure you want to download \"" + selectedFile.displayName + "\" to the " + MappingsPaths.MAPPINGS_DIR + " folder?\n\n" +
                "File will be saved as: " + selectedFile.displayName + "\n" +
                "Save location: " + MappingsPaths.MAPPINGS_DIR + "/"), 
            I18nUtils.getText(project, "Подтверждение скачивания", "Download Confirmation"), JOptionPane.YES_NO_OPTION, JOptionPane.PLAIN_MESSAGE);
        
        if (result != JOptionPane.YES_OPTION) {
            return;
        }
        
        if (downloadButton != null) {
            downloadButton.setEnabled(false);
            downloadButton.setText(I18nUtils.getText(project, "Скачивание...", "Downloading..."));
        }
        
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                String jsonContent = gitHubService.downloadMappingFile(selectedFile.path);
                if (jsonContent != null) {
                    GitHubMappingLibrary library = gitHubService.parseMappingFile(jsonContent);
                    String fileName = selectedFile.displayName;
                    
                    String formattedJsonContent = formatJsonContent(jsonContent);
                    
                    boolean success = saveToLocalMappings(fileName, formattedJsonContent);
                    
                    SwingUtilities.invokeLater(() -> {
                        if (downloadButton != null) {
                            downloadButton.setEnabled(true);
                            downloadButton.setText("Скачать в проект");
                        }
                        
                        if (success) {
                            // Обновляем дерево проекта, чтобы показать скачанный файл
                            vfsRefreshService.refreshMappingsDirectory();
                            
                            JOptionPane.showMessageDialog(getContentPane(), 
                                I18nUtils.getText(project,
                                    "Скачано!\n\n" +
                                    "Файл сохранён в: " + MappingsPaths.MAPPINGS_DIR + "/" + fileName + "\n",
                                    "Download successful!\n\n" +
                                    "File saved to: " + MappingsPaths.MAPPINGS_DIR + "/" + fileName + "\n"), 
                                I18nUtils.getText(project, "Скачано!", "Download Successful!"), JOptionPane.PLAIN_MESSAGE);
                        } else {
                            JOptionPane.showMessageDialog(getContentPane(), 
                                I18nUtils.getText(project, "Не удалось скачать!\n\n", "Download failed!\n\n"),
                                I18nUtils.getText(project, "Не удалось скачать", "Download Failed"), JOptionPane.PLAIN_MESSAGE);
                        }
                    });
                } else {
                    SwingUtilities.invokeLater(() -> {
                        if (downloadButton != null) {
                            downloadButton.setEnabled(true);
                            downloadButton.setText("Скачать в проект");
                        }
                        JOptionPane.showMessageDialog(getContentPane(), 
                            I18nUtils.getText(project,
                                "Не удалось скачать!\n\n" +
                                "Не удалось получить содержимое файла, проверьте сетевое подключение",
                                "Download failed!\n\n" +
                                "Unable to get file content, please check network connection"), 
                            I18nUtils.getText(project, "Не удалось скачать", "Download Failed"), JOptionPane.PLAIN_MESSAGE);
                    });
                }
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    if (downloadButton != null) {
                        downloadButton.setEnabled(true);
                        downloadButton.setText("Скачать в проект");
                    }
                                            JOptionPane.showMessageDialog(getContentPane(), 
                            I18nUtils.getText(project,
                                "Не удалось скачать!\n\n" +
                                "Текст ошибки: " + e.getMessage(),
                                "Download failed!\n\n" +
                                "Error message: " + e.getMessage()), 
                            I18nUtils.getText(project, "Не удалось скачать", "Download Failed"), JOptionPane.PLAIN_MESSAGE);
                });
            }
        });
    }
    
    /**
     * Сохраняет библиотеку сопоставлений в папку рабочих JSON проекта (MappingsPaths)
     */
    private boolean saveToLocalMappings(String fileName, String jsonContent) {
        try {
            java.nio.file.Path mappingsPath = project == null ? null : MappingsPaths.getMappingsDir(project);
            if (mappingsPath == null) {
                return false;
            }

            java.io.File mappingsDir = mappingsPath.toFile();
            if (!mappingsDir.exists()) {
                if (!mappingsDir.mkdirs()) {
                    return false;
                }
            }
            
            java.io.File targetFile = new java.io.File(mappingsDir, fileName);
            try (java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(
                    new java.io.FileOutputStream(targetFile), java.nio.charset.StandardCharsets.UTF_8)) {
                writer.write(jsonContent);
            }
            
            return true;
        } catch (java.io.IOException | RuntimeException e) {
            LOG.warn("Не удалось сохранить библиотеку " + fileName + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Форматирует JSON для удобного чтения
     */
    private String formatJsonContent(String jsonContent) {
        try {
            com.google.gson.Gson gson = new com.google.gson.GsonBuilder()
                .setPrettyPrinting()
                .create();
            
            Object jsonObject = gson.fromJson(jsonContent, Object.class);
            
            return gson.toJson(jsonObject);
            
        } catch (Exception e) {
            return jsonContent;
        }
    }

    /**
     * Открывает страницу на GitHub
     */
    private void openGitHubPage() {
        try {
            java.awt.Desktop.getDesktop().browse(new java.net.URI("https://github.com/JaysonX-Tech/tree-description-repository"));
        } catch (Exception e) {
            JOptionPane.showMessageDialog(getContentPane(), "Не удалось открыть ссылку на GitHub: " + e.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
    }
}
