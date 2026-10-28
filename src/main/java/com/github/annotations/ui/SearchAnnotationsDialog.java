package com.github.annotations.ui;

import com.github.annotations.services.AnnotationService;
import com.github.annotations.services.MappingLibraryService;
import com.github.annotations.utils.I18nUtils;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
// Цвет фиксированный, от темы оформления не зависит
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Диалог поиска по заметкам
 * Ищет по всем заметкам проекта
 */
public class SearchAnnotationsDialog extends DialogWrapper {
    
    private final Project project;
    private final AnnotationService annotationService;
    private final MappingLibraryService mappingLibraryService;
    
    private JBTextField searchField;
    private JBList<SearchResult> resultsList;
    private DefaultListModel<SearchResult> listModel;
    private JLabel statusLabel;
    
    public SearchAnnotationsDialog(@NotNull Project project) {
        super(project);
        this.project = project;
        this.annotationService = AnnotationService.getInstance(project);
        this.mappingLibraryService = MappingLibraryService.getInstance();
        
        setTitle(I18nUtils.getText(project, "Поиск по заметкам дерева проекта", "Search Project Tree Annotations"));
        setSize(850, 600);  // ← размер окна: ширина и высота в пикселях
        init();
    }
    
    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(JBUI.Borders.empty(10));
        
        // Поле поиска
        JPanel searchPanel = createSearchPanel();
        mainPanel.add(searchPanel, BorderLayout.NORTH);
        
        // Список результатов
        JPanel resultsPanel = createResultsPanel();
        mainPanel.add(resultsPanel, BorderLayout.CENTER);
        
        // Строка состояния
        statusLabel = new JLabel(I18nUtils.getText(project, "Введите слово для поиска", "Please enter search keywords"));
        statusLabel.setBorder(JBUI.Borders.empty(5, 0, 0, 0));
        mainPanel.add(statusLabel, BorderLayout.SOUTH);
        
        return mainPanel;
    }
    
    private JPanel createSearchPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        JLabel label = new JLabel(I18nUtils.getText(project, "Искать:", "Search Keywords:"));
        label.setBorder(JBUI.Borders.empty(0, 0, 5, 5));
        
        searchField = new JBTextField();
        searchField.setToolTipText(I18nUtils.getText(project, "Введите слово для поиска по заметкам дерева проекта", "Enter keywords to search project tree annotations"));
        
        // Поиск по мере ввода
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { performSearch(); }
            
            @Override
            public void removeUpdate(DocumentEvent e) { performSearch(); }
            
            @Override
            public void changedUpdate(DocumentEvent e) { performSearch(); }
        });
        
        panel.add(label, BorderLayout.WEST);
        panel.add(searchField, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createResultsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        JLabel resultsLabel = new JLabel(I18nUtils.getText(project, "Результаты:", "Search Results:"));
        resultsLabel.setBorder(JBUI.Borders.empty(10, 0, 5, 0));
        
        listModel = new DefaultListModel<>();
        resultsList = new JBList<>(listModel);
        resultsList.setCellRenderer(new SearchResultCellRenderer());
        resultsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        
        // Двойной щелчок — переход к файлу
        resultsList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    navigateToSelectedFile();
                }
            }
        });
        
        // Enter — переход к файлу
        resultsList.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    navigateToSelectedFile();
                }
            }
        });
        
        JBScrollPane scrollPane = new JBScrollPane(resultsList);
        scrollPane.setPreferredSize(new Dimension(800, 400));  // ← предпочтительный размер области прокрутки
        
        panel.add(resultsLabel, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * Запускает поиск по введённому слову; вызывается на каждое изменение поля поиска.
     *
     * Поиск обходит файлы проекта, поэтому выполняется в фоне (ReadAction.nonBlocking),
     * а в поток интерфейса возвращается только готовый список (showResults).
     * coalesceBy(this) отменяет предыдущий поиск, если пользователь продолжает печатать;
     * expireWith — если окно закрыто раньше, чем поиск закончился.
     */
    private void performSearch() {
        String keyword = searchField.getText().trim();
        if (keyword.isEmpty()) {
            listModel.clear();
            statusLabel.setText(I18nUtils.getText(project, "Введите слово для поиска", "Please enter search keywords"));
            return;
        }

        ReadAction.nonBlocking(() -> findResults(keyword))
            .coalesceBy(this)
            .expireWith(getDisposable())
            .finishOnUiThread(ModalityState.stateForComponent(searchField), results -> showResults(keyword, results))
            .submit(AppExecutorUtil.getAppExecutorService());
    }

    /**
     * Показывает найденное и итог в строке состояния; только поток интерфейса.
     */
    private void showResults(@NotNull String keyword, @NotNull List<SearchResult> results) {
        listModel.clear();
        for (SearchResult result : results) {
            listModel.addElement(result);
        }
        if (results.isEmpty()) {
            statusLabel.setText(I18nUtils.getText(project,
                "Заметок со словом \"" + keyword + "\" не найдено",
                "No annotations found containing \"" + keyword + "\""));
        } else {
            statusLabel.setText(I18nUtils.getText(project,
                "Найдено заметок: " + results.size(),
                "Found " + results.size() + " matching annotations"));
        }
    }

    /**
     * Собирает и сортирует результаты поиска; выполняется в фоне внутри чтения модели,
     * поэтому не трогает компоненты окна.
     */
    @NotNull
    private List<SearchResult> findResults(@NotNull String keyword) {
        List<SearchResult> results = new ArrayList<>();
        Set<String> userAnnotatedPaths = new HashSet<>(); // пути файлов, у которых уже есть пользовательская заметка
        
        // 1. Пользовательские заметки файлов (files)
        Map<String, String> userAnnotations = annotationService.getAllAnnotations();
        for (Map.Entry<String, String> entry : userAnnotations.entrySet()) {
            String filePath = entry.getKey();
            String annotation = entry.getValue();
            
            // Есть ли слово в тексте заметки (без учёта регистра)
            if (annotation.toLowerCase().contains(keyword.toLowerCase())) {
                                                SearchResult result = new SearchResult(filePath, annotation, keyword, "Пользовательский файл");
                results.add(result);
                userAnnotatedPaths.add(filePath); // у этого пути уже есть пользовательская заметка
            }
        }
        
        // 2. Пользовательские заметки пакетов (packages)
        Map<String, String> userPackageAnnotations = annotationService.getAllPackageAnnotations();
        for (Map.Entry<String, String> entry : userPackageAnnotations.entrySet()) {
            String packagePath = entry.getKey();
            String annotation = entry.getValue();
            
            if (annotation.toLowerCase().contains(keyword.toLowerCase())) {
                SearchResult result = new SearchResult(packagePath, annotation, keyword, "Пользовательский пакет");
                results.add(result);
                userAnnotatedPaths.add(packagePath);
            }
        }
        
        // 3. Файлы под пользовательскими шаблонами файлов
        Map<String, String> userFileMatchAnnotations = annotationService.getAllFileMatchAnnotations();
        for (Map.Entry<String, String> entry : userFileMatchAnnotations.entrySet()) {
            String pattern = entry.getKey();
            String annotation = entry.getValue();
            
            if (annotation.toLowerCase().contains(keyword.toLowerCase())) {
                // Все файлы, подходящие под шаблон
                List<String> matchedFiles = findFilesMatchingPattern(pattern);
                for (String filePath : matchedFiles) {
                    if (!userAnnotatedPaths.contains(filePath)) {
                        SearchResult result = new SearchResult(filePath, annotation, keyword, "Пользовательский шаблон файла");
                        results.add(result);
                        userAnnotatedPaths.add(filePath);
                    }
                }
            }
        }
        
        // 4. Пакеты под пользовательскими шаблонами пакетов
        Map<String, String> userPackageMatchAnnotations = annotationService.getAllPackageMatchAnnotations();
        for (Map.Entry<String, String> entry : userPackageMatchAnnotations.entrySet()) {
            String pattern = entry.getKey();
            String annotation = entry.getValue();
            
            if (annotation.toLowerCase().contains(keyword.toLowerCase())) {
                // Все пакеты, подходящие под шаблон
                List<String> matchedPackages = findPackagesMatchingPattern(pattern);
                for (String packagePath : matchedPackages) {
                    if (!userAnnotatedPaths.contains(packagePath)) {
                        SearchResult result = new SearchResult(packagePath, annotation, keyword, "Пользовательский шаблон пакета");
                        results.add(result);
                        userAnnotatedPaths.add(packagePath);
                    }
                }
            }
        }
        
        // 5. files и packages встроенной библиотеки (только реальные файлы и пакеты, без шаблонов)
        // Ищем, только если встроенная библиотека включена
        if (mappingLibraryService.isBuiltinMappingsEnabled()) {
            Map<String, String> builtinMappings = mappingLibraryService.getAllMappings();
            for (Map.Entry<String, String> entry : builtinMappings.entrySet()) {
                String keyName = entry.getKey();
                String annotation = entry.getValue();
                
                // Есть ли слово в тексте заметки (без учёта регистра)
                if (annotation.toLowerCase().contains(keyword.toLowerCase())) {
                    // Имя файла или имя пакета
                    if (isFileName(keyName)) {
                        // Для имени файла ищем все подходящие полные пути в проекте
                        List<String> allPaths = findAllFilesInProject(keyName);
                        if (!allPaths.isEmpty()) {
                            for (String fullPath : allPaths) {
                                // Встроенный результат добавляем, только если у пути нет пользовательской заметки
                                if (!userAnnotatedPaths.contains(fullPath)) {
                                    // Ещё раз проверяем, что файл существует
                                    if (fileExistsInProject(fullPath)) {
                                        SearchResult result = new SearchResult(fullPath, annotation, keyword, "Встроенный файл");
                                        results.add(result);
                                    }
                                }
                            }
                        } else {
                            // Файлов не нашлось, но сопоставление есть — добавляем общий результат
                            if (fileExistsInProject(keyName)) {
                                if (!userAnnotatedPaths.contains(keyName)) {
                                    SearchResult result = new SearchResult(keyName, annotation, keyword, "Встроенный файл");
                                    results.add(result);
                                }
                            }
                        }
                    } else {
                        // Для имени пакета проверяем, что такой путь есть в проекте
                        if (!userAnnotatedPaths.contains(keyName) && packageExistsInProject(keyName)) {
                            SearchResult result = new SearchResult(keyName, annotation, keyword, "Встроенный пакет");
                            results.add(result);
                            userAnnotatedPaths.add(keyName);
                        }
                    }
                }
            }
        }
        
        // 6. Файлы под шаблонами fileMatch встроенной библиотеки
        // Ищем, только если встроенная библиотека включена
        if (mappingLibraryService.isBuiltinMappingsEnabled()) {
            Map<String, String> builtinFileMatchPatterns = mappingLibraryService.getAllFileMatchPatterns();
            for (Map.Entry<String, String> entry : builtinFileMatchPatterns.entrySet()) {
                String pattern = entry.getKey();
                String annotation = entry.getValue();
                
                if (annotation.toLowerCase().contains(keyword.toLowerCase())) {
                    // Все файлы, подходящие под шаблон
                    List<String> matchedFiles = findFilesMatchingPattern(pattern);
                    for (String filePath : matchedFiles) {
                        if (!userAnnotatedPaths.contains(filePath)) {
                            SearchResult result = new SearchResult(filePath, annotation, keyword, "Встроенный шаблон файла");
                            results.add(result);
                            userAnnotatedPaths.add(filePath);
                        }
                    }
                }
            }
        }
        
        // 7. Пакеты под шаблонами packageMatch встроенной библиотеки
        // Ищем, только если встроенная библиотека включена
        if (mappingLibraryService.isBuiltinMappingsEnabled()) {
            Map<String, String> builtinPackageMatchPatterns = mappingLibraryService.getAllPackageMatchPatterns();
            for (Map.Entry<String, String> entry : builtinPackageMatchPatterns.entrySet()) {
                String pattern = entry.getKey();
                String annotation = entry.getValue();
                
                if (annotation.toLowerCase().contains(keyword.toLowerCase())) {
                    // Все пакеты, подходящие под шаблон
                    List<String> matchedPackages = findPackagesMatchingPattern(pattern);
                    for (String packagePath : matchedPackages) {
                        if (!userAnnotatedPaths.contains(packagePath)) {
                            SearchResult result = new SearchResult(packagePath, annotation, keyword, "Встроенный шаблон пакета");
                            results.add(result);
                            userAnnotatedPaths.add(packagePath);
                        }
                    }
                }
            }
        }
        
        // Сортировка по типу и пути — пользовательские сопоставления первыми
        results.sort((a, b) -> {
            // Порядок типов: пользовательский файл > пользовательский пакет > пользовательский шаблон файла > пользовательский шаблон пакета > встроенный файл > встроенный пакет > встроенный шаблон файла > встроенный шаблон пакета
            String[] priorityOrder = {"Пользовательский файл", "Пользовательский пакет", "Пользовательский шаблон файла", "Пользовательский шаблон пакета", "Встроенный файл", "Встроенный пакет", "Встроенный шаблон файла", "Встроенный шаблон пакета"};
            int aPriority = getPriority(a.source, priorityOrder);
            int bPriority = getPriority(b.source, priorityOrder);
            
            if (aPriority != bPriority) {
                return Integer.compare(aPriority, bPriority);
            }
            
            // Внутри одного типа — по пути файла
            return a.filePath.compareToIgnoreCase(b.filePath);
        });
        return results;
    }
    
    /**
     * Позиция типа в порядке сортировки
     */
    private int getPriority(String source, String[] priorityOrder) {
        for (int i = 0; i < priorityOrder.length; i++) {
            if (source.equals(priorityOrder[i])) {
                return i;
            }
        }
        return priorityOrder.length; // неизвестный тип — в конец
    }
    
    /**
     * Ищет все файлы, подходящие под шаблон
     */
    private List<String> findFilesMatchingPattern(String pattern) {
        List<String> matchedFiles = new ArrayList<>();
        
        String projectPath = project.getBasePath();
        if (projectPath == null) return matchedFiles;
        
        VirtualFile projectRoot = VirtualFileManager.getInstance().findFileByUrl("file://" + projectPath);
        if (projectRoot == null) return matchedFiles;
        
        // Рекурсивно обходим все файлы проекта
        findFilesMatchingPatternRecursive(projectRoot, pattern, matchedFiles);
        
        return matchedFiles;
    }
    
    /**
     * Рекурсивно ищет файлы, подходящие под шаблон
     */
    private void findFilesMatchingPatternRecursive(VirtualFile directory, String pattern, List<String> matchedFiles) {
        if (directory == null || !directory.isDirectory()) return;
        
        for (VirtualFile child : directory.getChildren()) {
            ProgressManager.checkCanceled(); // поиск отменён новым вводом — прервать обход
            if (child.isDirectory()) {
                // Обходим вложенные папки
                findFilesMatchingPatternRecursive(child, pattern, matchedFiles);
            } else {
                // Имя файла должно совпасть с шаблоном целиком, а не просто содержать его
                String fileName = child.getName();
                if (fileName.equalsIgnoreCase(pattern)) {
                    // Путь относительно корня проекта
                    String projectPath = project.getBasePath();
                    if (projectPath != null) {
                        VirtualFile projectRoot = VirtualFileManager.getInstance().findFileByUrl("file://" + projectPath);
                        if (projectRoot != null) {
                            String relativePath = getRelativePath(projectRoot, child);
                            if (relativePath != null) {
                                matchedFiles.add(relativePath);
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Ищет все пакеты, подходящие под шаблон
     */
    private List<String> findPackagesMatchingPattern(String pattern) {
        List<String> matchedPackages = new ArrayList<>();
        
        String projectPath = project.getBasePath();
        if (projectPath == null) return matchedPackages;
        
        VirtualFile projectRoot = VirtualFileManager.getInstance().findFileByUrl("file://" + projectPath);
        if (projectRoot == null) return matchedPackages;
        
        // Рекурсивно обходим все папки проекта
        findPackagesMatchingPatternRecursive(projectRoot, pattern, matchedPackages);
        
        return matchedPackages;
    }
    
    /**
     * Рекурсивно ищет пакеты, подходящие под шаблон
     */
    private void findPackagesMatchingPatternRecursive(VirtualFile directory, String pattern, List<String> matchedPackages) {
        if (directory == null || !directory.isDirectory()) return;
        
        for (VirtualFile child : directory.getChildren()) {
            ProgressManager.checkCanceled(); // поиск отменён новым вводом — прервать обход
            if (child.isDirectory()) {
                // Имя папки должно совпасть с шаблоном целиком, а не просто содержать его
                String dirName = child.getName();
                if (dirName.equalsIgnoreCase(pattern)) {
                    // Путь относительно корня проекта
                    String projectPath = project.getBasePath();
                    if (projectPath != null) {
                        VirtualFile projectRoot = VirtualFileManager.getInstance().findFileByUrl("file://" + projectPath);
                        if (projectRoot != null) {
                            String relativePath = getRelativePath(projectRoot, child);
                            if (relativePath != null) {
                                matchedPackages.add(relativePath);
                            }
                        }
                    }
                }
                
                // Обходим вложенные папки
                findPackagesMatchingPatternRecursive(child, pattern, matchedPackages);
            }
        }
    }
    
    /**
     * Путь файла относительно корня проекта
     */
    private String getRelativePath(VirtualFile baseDir, VirtualFile file) {
        String basePath = baseDir.getPath();
        String filePath = file.getPath();
        
        if (filePath.startsWith(basePath)) {
            String relativePath = filePath.substring(basePath.length());
            if (relativePath.startsWith("/")) {
                relativePath = relativePath.substring(1);
            }
            return relativePath;
        }
        return null;
    }
    
    /**
     * Имя файла это или имя пакета
     */
    private boolean isFileName(String name) {
        // Есть расширение — точно файл
        if (name.contains(".") && (name.endsWith(".xml") || name.endsWith(".yml") || name.endsWith(".yaml") || 
            name.endsWith(".properties") || name.endsWith(".json") || name.endsWith(".gradle") || 
            name.endsWith(".md") || name.endsWith(".txt") || name.endsWith(".gitignore") || 
            name.endsWith(".java") || name.endsWith(".kt") || name.endsWith(".js") || name.endsWith(".ts"))) {
            return true;
        }
        
        // Особые имена файлов без расширения
        if (name.equals(".gitignore") || name.equals("Dockerfile") || name.equals("Jenkinsfile") || 
            name.equals("Makefile") || name.equals("README")) {
            return true;
        }
        
        // Несколько точек — обычно имя пакета (например, org.springframework.boot)
        if (name.contains(".") && name.split("\\.").length > 2) {
            return false;
        }
        

        return false;
    }
    
    /**
     * Есть ли файл в текущем проекте
     */
    private boolean fileExistsInProject(String relativePath) {
        try {
            String fullPath = project.getBasePath() + "/" + relativePath;
            VirtualFile file = VirtualFileManager.getInstance().findFileByUrl("file://" + fullPath);
            return file != null && file.exists();
        } catch (Exception ex) {
            return false;
        }
    }
    
    /**
     * Есть ли путь пакета в проекте
     * Имя пакета превращается в путь папки, например: org.springframework.boot -> org/springframework/boot
     * Производительность: меньше обращений к файловой системе
     */
    private boolean packageExistsInProject(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return false;
        }
        
        try {
            // Имя пакета в виде пути
            String packagePath = packageName.replace(".", "/");
            String projectBasePath = project.getBasePath();
            if (projectBasePath == null) {
                return false;
            }
            
            // Проверяем обычные папки исходников (в порядке вероятности)
            String[] basePaths = {
                "src/main/java/" + packagePath,  // самый частый случай
                "src/" + packagePath,            // упрощённая структура
                "src/test/java/" + packagePath,
                packagePath                      // корень проекта
            };
            
            VirtualFileManager vfm = VirtualFileManager.getInstance();
            for (String basePath : basePaths) {
                VirtualFile file = vfm.findFileByUrl("file://" + projectBasePath + "/" + basePath);
                if (file != null && file.exists() && file.isDirectory()) {
                    return true;
                }
            }
            
            return false;
        } catch (Exception ex) {
            return false;
        }
    }
    
    /**
     * Рекурсивно ищет файл в проекте
     */
    private boolean searchFileInProject(VirtualFile directory, String fileName) {
        if (directory == null || !directory.isDirectory()) {
            return false;
        }
        
        for (VirtualFile child : directory.getChildren()) {
            ProgressManager.checkCanceled(); // поиск отменён новым вводом — прервать обход
            if (child.getName().equals(fileName)) {
                return true;
            }
            if (child.isDirectory() && searchFileInProject(child, fileName)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Ищет файл в проекте и возвращает полный относительный путь
     */
    private String findFileInProject(String fileName) {
        // Сначала проверяем корень
        String rootPath = fileName;
        if (fileExistsInProject(rootPath)) {
            return rootPath;
        }
        
        // Обходим весь проект, возвращаем первый подходящий относительный путь
        String projectPath = project.getBasePath();
        if (projectPath == null) return null;
        VirtualFile projectRoot = VirtualFileManager.getInstance().findFileByUrl("file://" + projectPath);
        if (projectRoot == null) return null;
        return searchFilePathInProject(projectRoot, fileName, "");
    }
    
    /**
     * Ищет все подходящие файлы в проекте и возвращает список относительных путей
     */
    private List<String> findAllFilesInProject(String fileName) {
        Set<String> allPaths = new LinkedHashSet<>();
        String projectPath = project.getBasePath();
        if (projectPath == null) return new ArrayList<>();
        VirtualFile projectRoot = VirtualFileManager.getInstance().findFileByUrl("file://" + projectPath);
        if (projectRoot == null) return new ArrayList<>();
        searchAllFilePathsInProject(projectRoot, fileName, "", allPaths);
        return new ArrayList<>(allPaths);
    }
    
    /**
     * Рекурсивно ищет файл и возвращает относительный путь
     */
    private String searchFilePathInProject(VirtualFile directory, String fileName, String currentPath) {
        if (directory == null || !directory.isDirectory()) {
            return null;
        }
        
        for (VirtualFile child : directory.getChildren()) {
            ProgressManager.checkCanceled(); // поиск отменён новым вводом — прервать обход
            String childPath = currentPath.isEmpty() ? child.getName() : currentPath + "/" + child.getName();
            
            if (child.getName().equals(fileName)) {
                return childPath;
            }
            
            if (child.isDirectory()) {
                String result = searchFilePathInProject(child, fileName, childPath);
                if (result != null) {
                    return result;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Рекурсивно ищет все подходящие файлы и собирает пути
     */
    private void searchAllFilePathsInProject(VirtualFile directory, String fileName, String currentPath, Set<String> results) {
        if (directory == null || !directory.isDirectory()) {
            return;
        }
        
        for (VirtualFile child : directory.getChildren()) {
            ProgressManager.checkCanceled(); // поиск отменён новым вводом — прервать обход
            String childPath = currentPath.isEmpty() ? child.getName() : currentPath + "/" + child.getName();
            
            if (child.getName().equals(fileName)) {
                results.add(childPath);
            }
            
            if (child.isDirectory()) {
                searchAllFilePathsInProject(child, fileName, childPath, results);
            }
        }
    }
    
    private void navigateToSelectedFile() {
        SearchResult selected = resultsList.getSelectedValue();
        if (selected == null) return;
        
        try {
            // Полный путь файла
            String fullPath = project.getBasePath() + "/" + selected.filePath;
            VirtualFile file = VirtualFileManager.getInstance().findFileByUrl("file://" + fullPath);
            
            if (file != null && file.exists()) {
                // Открываем файл в редакторе
                FileEditorManager.getInstance(project).openFile(file, true);
                
                // Выделяем файл в Project View
                ProjectView.getInstance(project).select(null, file, true);
                
                // Закрываем диалог
                close(OK_EXIT_CODE);
            } else {
                statusLabel.setText("Файл не найден: " + selected.filePath);
            }
        } catch (Exception ex) {
            statusLabel.setText("Не удалось открыть файл: " + ex.getMessage());
        }
    }
    
    @Override
    protected @NotNull Action[] createActions() {
        return new Action[]{
            new AbstractAction(I18nUtils.getText(project, "Перейти к выбранному результату", "Navigate to Selected Result")) {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    navigateToSelectedFile();
                }
            },
            getCancelAction()
        };
    }
    
    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return searchField;
    }
    
    /**
     * Данные одного результата поиска
     */
    public static class SearchResult {
        public final String filePath;
        public final String annotation;
        public final String keyword;
        public final String source; // тип источника, например "Пользовательский файл" или "Встроенный файл"
        
        public SearchResult(String filePath, String annotation, String keyword, String source) {
            this.filePath = filePath;
            this.annotation = annotation;
            this.keyword = keyword;
            this.source = source;
        }
        
        @Override
        public String toString() {
            return filePath + " - " + annotation + " [" + source + "]";
        }
    }
    
    /**
     * Отрисовка результата поиска с подсветкой слова
     */
    private class SearchResultCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            
            if (value instanceof SearchResult) {
                SearchResult result = (SearchResult) value;
                
                // Цвет текста зависит от выделения и темы
                String textColor = isSelected ? "#FFFFFF" : getUnselectedTextColor(); // выделено — белый, иначе по теме
                String annotationColor = isSelected ? "#FFFFFF" : getUnselectedAnnotationColor(); // выделено — белый, иначе по теме
                
                // HTML-текст с подсвеченным словом
                String highlightedText = highlightKeyword(result.annotation, result.keyword, isSelected);
                
                // Цвет зависит от источника — фиксированная схема
                String sourceColor = "#6088F0"; // как у пути
                
                // В выделенной строке метка белая для контраста, иначе серая
                String labelColor = isSelected ? "#FFFFFF" : "#787A7C";
                
                String displayText = "<html>" +
                    "<b style='color: " + textColor + ";'>" + result.filePath + "</b><br/>" +
                    "<span style='color: " + annotationColor + ";'>" + highlightedText + "</span>" +
                    "</html>";
                
                setText(displayText);
                setToolTipText(I18nUtils.getText(project, 
                    "Двойной щелчок — перейти к: " + result.filePath + " (" + result.source + ")",
                    "Double-click to navigate to: " + result.filePath + " (" + result.source + ")"));
            }
            
            return this;
        }
        
        private String highlightKeyword(String text, String keyword, boolean isSelected) {
            if (keyword.isEmpty()) return text;
            
            // Стиль подсветки зависит от выделения
            String highlightStyle;
            if (isSelected) {
                // В выделенной строке: жёлтый фон и чёрный жирный текст (контраст на синем выделении)
                highlightStyle = "background-color: #FFD700; color: #000000; font-weight: bold;";
            } else {
                // В невыделенной строке: светло-жёлтый фон и чёрный жирный текст
                highlightStyle = "background-color: #FFFF99; color: #000000; font-weight: bold;";
            }
            
            String pattern = "(?i)" + java.util.regex.Pattern.quote(keyword);
            return text.replaceAll(pattern, "<span style='" + highlightStyle + "'>$0</span>");
        }
    }
    
    /**
     * Переводит Color в шестнадцатеричную строку
     */
    private static String colorToHex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }
    
        /**
     * Цвет пути файла в невыделенной строке (фиксированная схема)
     */
    private static String getUnselectedTextColor() {
        // Фиксированный синий, читается на любом фоне
        return "#6088F0"; // синий
    }

    /**
     * Цвет текста заметки в невыделенной строке (фиксированная схема)
     */
    private static String getUnselectedAnnotationColor() {
        // Фиксированный серый, текст заметки хорошо читается
        return "#787A7C"; // серый
    }
}
