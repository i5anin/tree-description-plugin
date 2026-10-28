package com.github.annotations.actions;

import com.github.annotations.services.AnnotationService;
import com.github.annotations.ui.ExtractCommentDialog;
import com.github.annotations.utils.I18nUtils;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Действие «Извлечь комментарий в заметку»
 * Берёт первую строку Javadoc-комментария Java-файла и делает её заметкой
 */
public class ExtractCommentAnnotationAction extends AnAction {
    
    // Заранее скомпилированное выражение для первой строки Javadoc
    private static final Pattern JAVADOC_PATTERN = Pattern.compile(
        "/\\*\\*\\s*\\n\\s*\\*\\s*(.+?)\\s*(?:\\n|\\*/)", 
        Pattern.DOTALL | Pattern.MULTILINE
    );
    
    // ID группы уведомлений; сам NotificationGroup получаем лениво
    private static final String NOTIFICATION_GROUP_ID = "Extract.Comment.Notification";
    
    public ExtractCommentAnnotationAction() {
        super();
    }
    
    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            e.getPresentation().setEnabledAndVisible(false);
            return;
        }
        
        // Пункт меню показывается всегда
        e.getPresentation().setVisible(true);
        e.getPresentation().setEnabled(true);
        
        // Обновляем текст действия
        String text = I18nUtils.getText(project, "Извлечь комментарий в заметку", "Extract Comment as Remark");
        e.getPresentation().setText(text);
    }
    
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        
        if (project == null || files == null || files.length == 0) {
            return;
        }
        
        // Извлекаем комментарии в фоновом потоке
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            List<VirtualFile> javaFiles = collectJavaFiles(files);
            Map<VirtualFile, String> extractedComments = new HashMap<>();
            
            // Комментарии всех файлов
            for (VirtualFile file : javaFiles) {
                String comment = extractCommentFromFile(project, file);
                if (comment != null && !comment.trim().isEmpty()) {
                    extractedComments.put(file, comment.trim());
                }
            }
            
            // Диалог показываем в потоке EDT
            ApplicationManager.getApplication().invokeLater(() -> {
                if (extractedComments.isEmpty()) {
                    showNotification(project, 0, javaFiles.size());
                    return;
                }
                
                // Диалог извлечения комментариев
                List<String> commentList = new ArrayList<>(extractedComments.values());
                ExtractCommentDialog dialog = new ExtractCommentDialog(project, commentList, extractedComments.size());
                
                if (dialog.showAndGet()) {
                    String selectedColor = dialog.getSelectedColor();
                    
                    // Сохраняем все извлечённые комментарии
                    AnnotationService service = AnnotationService.getInstance(project);
                    if (service != null) {
                        int successCount = 0;
                        for (Map.Entry<VirtualFile, String> entry : extractedComments.entrySet()) {
                            VirtualFile file = entry.getKey();
                            String comment = entry.getValue();
                            String relativePath = getRelativePath(project, file);
                            
                            if (relativePath != null) {
                                service.setAnnotationAndRefresh(relativePath, comment, selectedColor);
                                successCount++;
                            }
                        }
                        
                        // Уведомление об успехе
                        showNotification(project, successCount, javaFiles.size());
                    }
                }
            });
        });
    }
    
    /**
     * Есть ли среди файлов Java-файлы
     */
    private boolean hasJavaFiles(VirtualFile[] files) {
        for (VirtualFile file : files) {
            if (isJavaFileOrDirectory(file)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Собирает все Java-файлы, включая файлы внутри папок
     */
    private List<VirtualFile> collectJavaFiles(VirtualFile[] files) {
        List<VirtualFile> javaFiles = new ArrayList<>();
        for (VirtualFile file : files) {
            collectJavaFilesRecursively(file, javaFiles);
        }
        return javaFiles;
    }
    
    /**
     * Рекурсивно собирает Java-файлы
     */
    private void collectJavaFilesRecursively(VirtualFile file, List<VirtualFile> javaFiles) {
        if (file.isDirectory()) {
            VirtualFile[] children = file.getChildren();
            if (children != null) {
                for (VirtualFile child : children) {
                    collectJavaFilesRecursively(child, javaFiles);
                }
            }
        } else if ("java".equalsIgnoreCase(file.getExtension())) {
            javaFiles.add(file);
        }
    }
    
    /**
     * Java-файл или папка, где они могут быть
     */
    private boolean isJavaFileOrDirectory(VirtualFile file) {
        if (file.isDirectory()) {
            return true; // в папке могут быть Java-файлы
        }
        return "java".equalsIgnoreCase(file.getExtension());
    }
    
    /**
     * Извлекает комментарий из одного файла
     */
    private String extractCommentFromFile(Project project, VirtualFile file) {
        try {
            // Кодировка файла
            Charset charset = file.getCharset();
            
            // Читаем первые 100 строк файла
            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), charset))) {
                
                String line;
                int lineCount = 0;
                while ((line = reader.readLine()) != null && lineCount < 100) {
                    content.append(line).append("\n");
                    lineCount++;
                }
            }
            
            // Первая строка Javadoc-комментария
            return extractFirstJavadocLine(content.toString());
        } catch (IOException e) {
            // Ошибку ввода-вывода пропускаем, идём к следующему файлу
        }
        return null;
    }
    
    /**
     * Извлекает первую строку Javadoc-комментария
     */
    private String extractFirstJavadocLine(String content) {
        Matcher matcher = JAVADOC_PATTERN.matcher(content);
        if (matcher.find()) {
            String firstLine = matcher.group(1);
            if (firstLine != null) {
                // Чистим текст: лишние пробелы и служебные символы
                return firstLine.replaceAll("\s+", " ").trim();
            }
        }
        return null;
    }
    
    /**
     * Показывает уведомление с итогом
     */
    private void showNotification(Project project, int successCount, int totalCount) {
        String message;
        NotificationType type;
        
        if (successCount == 0) {
            message = I18nUtils.getText(project, 
                "Комментарии для извлечения не найдены",
                "No extractable comments found");
            type = NotificationType.WARNING;
        } else if (successCount == totalCount) {
            message = I18nUtils.getText(project, 
                String.format("Комментарии %d файлов извлечены в заметки", successCount),
                String.format("Successfully extracted comments from %d files as remarks", successCount));
            type = NotificationType.INFORMATION;
        } else {
            message = I18nUtils.getText(project, 
                String.format("Комментарии %d из %d файлов извлечены в заметки", successCount, totalCount),
                String.format("Successfully extracted comments from %d/%d files as remarks", successCount, totalCount));
            type = NotificationType.INFORMATION;
        }
        
        // NotificationGroup получаем по требованию, чтобы не зависеть от сервисов при загрузке класса
        NotificationGroup notificationGroup = NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID);
        
        Notification notification = notificationGroup.createNotification(
            I18nUtils.getText(project, "Извлечь комментарий в заметку", "Extract Comment as Remark"),
            message,
            type
        );
        notification.notify(project);
    }
    
    /**
     * Путь относительно корня проекта
     */
    @Nullable
    private String getRelativePath(@NotNull Project project, @NotNull VirtualFile file) {
        String projectPath = project.getBasePath();
        if (projectPath == null) {
            return null;
        }
        String filePath = file.getPath();
        
        if (filePath.startsWith(projectPath)) {
            if (filePath.length() > projectPath.length()) {
                String relativePath = filePath.substring(projectPath.length());
                return relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;
            } else if (filePath.equals(projectPath)) {
                return "";
            }
        }
        
        return null;
    }
}