package com.github.annotations.decorators;

import com.github.annotations.services.AnnotationService;
import com.github.annotations.services.MappingLibraryService;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ProjectViewNodeDecorator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.ui.SimpleTextAttributes;
// Цвет фиксированный, от темы оформления не зависит
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

    /**
     * Оформление узлов дерева проекта
     * Показывает заметки рядом с именами в дереве проекта
     * 
     * Производительность:
     * - быстрая проверка в начале отсекает лишние вычисления
     * - состояние переключателя библиотеки сопоставлений проверяется при каждом вызове
     */
public class AnnotationDecorator implements ProjectViewNodeDecorator {
    
    // Фиксированный цвет
    private static SimpleTextAttributes getAnnotationAttributes() {
        return new SimpleTextAttributes(SimpleTextAttributes.STYLE_ITALIC, new Color(187, 187, 187)); // светло-серый #BBBBBB
    }
    
    // Атрибуты текста по настроенному цвету
    private static SimpleTextAttributes getAnnotationAttributesWithColor(String colorHex) {
        if (colorHex != null && !colorHex.trim().isEmpty()) {
            try {
                Color color = Color.decode(colorHex);
                return new SimpleTextAttributes(SimpleTextAttributes.STYLE_ITALIC, color);
            } catch (NumberFormatException e) {
                // Если цвет не разобрался — цвет по умолчанию
                return getAnnotationAttributes();
            }
        }
        return getAnnotationAttributes();
    }
    
    // Кэш не используем, чтобы переключатели срабатывали сразу
    
    @Override
    public void decorate(ProjectViewNode<?> node, PresentationData data) {
        Project project = node.getProject();
        if (project == null) {
            return;
        }
        
        // Включён ли показ заметок в дереве проекта
        AnnotationService annotationService = AnnotationService.getInstance(project);
        if (!annotationService.isProjectTreeAnnotationsEnabled()) {
            return; // переключатель выключен — заметок не показываем
        }
        
        VirtualFile file = getVirtualFile(node);
        if (file == null) {
            return;
        }
        
        String annotation = getAnnotationForFile(project, file);
        if (annotation != null && !annotation.trim().isEmpty()) {
            // Дописываем заметку после имени узла
            String originalText = data.getPresentableText();
            if (originalText != null) {
                data.clearText();
                data.addText(originalText, SimpleTextAttributes.REGULAR_ATTRIBUTES);
                
                // Цвет заметки (передаём текст заметки, чтобы не было циклического вызова)
                String textColor = getTextColorForFile(project, file, annotation);
                SimpleTextAttributes annotationAttributes = getAnnotationAttributesWithColor(textColor);
                
                data.addText("  " + annotation, annotationAttributes);
            }
        }
    }
    
    /**
     * Возвращает виртуальный файл узла
     */
    @Nullable
    private VirtualFile getVirtualFile(@NotNull ProjectViewNode<?> node) {
        Object value = node.getValue();
        
        if (value instanceof PsiDirectory) {
            return ((PsiDirectory) value).getVirtualFile();
        } else if (value instanceof PsiFile) {
            return ((PsiFile) value).getVirtualFile();
        } else if (value instanceof VirtualFile) {
            return (VirtualFile) value;
        }
        
        return node.getVirtualFile();
    }
    

    @Nullable
    private String getAnnotationForFile(@NotNull Project project, @NotNull VirtualFile file) {
        String name = file.getName();
        if (name.isEmpty() || name.startsWith(".") && name.length() < 3) {
            return null;
        }
        
        AnnotationService annotationService = AnnotationService.getInstance(project);
        
        // Сначала берём данные из сервиса живого обновления (если он доступен)
        String realTimeAnnotation = getRealTimeAnnotation(project, file);
        if (realTimeAnnotation != null && !realTimeAnnotation.isEmpty()) {
            return realTimeAnnotation.trim();
        }
        
        // 1. Пользовательские сопоставления файлов
        String userFileAnnotation = annotationService.getAnnotation(file);
        if (userFileAnnotation != null && !userFileAnnotation.isEmpty()) {
            return userFileAnnotation.trim();
        }
        
        // 2. Пользовательские сопоставления пакетов
        String userPackageAnnotation = annotationService.getPackageAnnotation(file);
        if (userPackageAnnotation != null && !userPackageAnnotation.isEmpty()) {
            return userPackageAnnotation.trim();
        }
        
        // 3. Пользовательские шаблоны файлов (только для файлов)
        if (!file.isDirectory()) {
            // Полный относительный путь для смешанного сопоставления
            String relativePath = getRelativePathForMatching(project, file);
            String userFileMatchAnnotation = getUserFilePatternAnnotation(annotationService, name, relativePath);
            if (userFileMatchAnnotation != null && !userFileMatchAnnotation.isEmpty()) {
                return userFileMatchAnnotation.trim();
            }
        }
        
        // 4. Пользовательские шаблоны пакетов (только для папок и пакетов)
        if (file.isDirectory()) {
            // Полный относительный путь для сопоставления пакетов
            String relativePath = getRelativePathForMatching(project, file);
            String userPackageMatchAnnotation = getUserPackagePatternAnnotation(annotationService, relativePath != null ? relativePath : name);
            if (userPackageMatchAnnotation != null && !userPackageMatchAnnotation.isEmpty()) {
                return userPackageMatchAnnotation.trim();
            }
        }
        
        // 5. Встроенная библиотека сопоставлений (если включена)
        MappingLibraryService mappingService = getMappingService();
        if (mappingService.isBuiltinMappingsEnabled()) {
            // 5.1 Встроенные сопоставления файлов
            String builtinFileMapping = mappingService.searchFileMapping(name);
            if (builtinFileMapping != null) {
                return builtinFileMapping;
            }
            
            // 5.2 Встроенные сопоставления пакетов
            String builtinPackageMapping = mappingService.searchPackageMapping(name);
            if (builtinPackageMapping != null) {
                return builtinPackageMapping;
            }
            
            // 5.3 Встроенные шаблоны файлов (только для файлов)
            if (!file.isDirectory()) {
                String builtinFileMatchMapping = mappingService.searchFileMatchMapping(name);
                if (builtinFileMatchMapping != null) {
                    return builtinFileMatchMapping;
                }
            }
            
            // 5.4 Встроенные шаблоны пакетов (только для папок и пакетов)
            if (file.isDirectory()) {
                // Полный относительный путь для сопоставления пакетов
                String relativePath = getRelativePathForMatching(project, file);
                String builtinPackageMatchMapping = mappingService.searchPatternMapping(relativePath != null ? relativePath : name);
                if (builtinPackageMatchMapping != null) {
                    return builtinPackageMatchMapping;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Возвращает заметку по пользовательскому шаблону файла
     */
    @Nullable
    private String getUserFilePatternAnnotation(@NotNull AnnotationService annotationService, @NotNull String fileName, @Nullable String relativePath) {
        return annotationService.getFileMatchAnnotation(fileName, relativePath);
    }
    
    /**
     * Возвращает заметку по пользовательскому шаблону пакета
     */
    @Nullable
    private String getUserPackagePatternAnnotation(@NotNull AnnotationService annotationService, @NotNull String packageName) {
        return annotationService.getPackageMatchAnnotation(packageName);
    }
    
    /**
     * Возвращает цвет текста для файла
     * Цвет берётся по источнику заметки, которая реально показана
     */
    private String getTextColorForFile(@NotNull Project project, @NotNull VirtualFile file, @NotNull String annotation) {
        AnnotationService annotationService = AnnotationService.getInstance(project);
        
        // Цвет зависит от источника заметки
        String name = file.getName();
        
        // 1. Цвет пользовательского сопоставления файла
        String userFileAnnotation = annotationService.getAnnotation(file);
        if (userFileAnnotation != null && userFileAnnotation.trim().equals(annotation.trim())) {
            String relativePath = getRelativePathForMatching(project, file);
            if (relativePath != null) {
                String fileColor = annotationService.getFileTextColor(relativePath);
                if (fileColor != null) {
                    return fileColor;
                }
            }
        }
        
        // 2. Цвет пользовательского сопоставления пакета
        String userPackageAnnotation = annotationService.getPackageAnnotation(file);
        if (userPackageAnnotation != null && userPackageAnnotation.trim().equals(annotation.trim())) {
            String relativePath = getRelativePathForMatching(project, file);
            if (relativePath != null) {
                String packageColor = annotationService.getPackageTextColor(relativePath);
                if (packageColor != null) {
                    return packageColor;
                }
            }
        }
        
        // 3. Цвет пользовательского шаблона файла
        if (!file.isDirectory()) {
            String relativePath = getRelativePathForMatching(project, file);
            String userFileMatchAnnotation = getUserFilePatternAnnotation(annotationService, name, relativePath);
            if (userFileMatchAnnotation != null && userFileMatchAnnotation.trim().equals(annotation.trim())) {
                String fileColor = annotationService.getFileTextColor(name);
                if (fileColor != null) {
                    return fileColor;
                }
            }
        }
        
        // 4. Цвет пользовательского шаблона пакета
        if (file.isDirectory()) {
            String relativePath = getRelativePathForMatching(project, file);
            if (relativePath != null) {
                String userPackageMatchAnnotation = getUserPackagePatternAnnotation(annotationService, relativePath);
                if (userPackageMatchAnnotation != null && userPackageMatchAnnotation.trim().equals(annotation.trim())) {
                    String packageColor = annotationService.getPackageTextColor(relativePath);
                    if (packageColor != null) {
                        return packageColor;
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * Берёт заметку из сервиса живого обновления
     */
    @Nullable
    private String getRealTimeAnnotation(@NotNull Project project, @NotNull VirtualFile file) {
        try {
            // Сервис получаем через рефлексию, чтобы не было циклической зависимости
            Class<?> realTimeServiceClass = Class.forName("com.github.annotations.services.RealTimeAnnotationService");
            Object realTimeService = project.getService(realTimeServiceClass);
            
            if (realTimeService != null) {
                // Путь файла
                String relativePath = getRelativePathForMatching(project, file);
                if (relativePath != null) {
                    // Пробуем получить заметку файла
                    if (!file.isDirectory()) {
                        try {
                            java.lang.reflect.Method getFileAnnotationMethod = 
                                realTimeServiceClass.getMethod("getFileAnnotation", String.class);
                            String annotation = (String) getFileAnnotationMethod.invoke(realTimeService, relativePath);
                            if (annotation != null && !annotation.isEmpty()) {
                                return annotation;
                            }
                        } catch (Exception e) {
                            // Ошибку рефлексии игнорируем, переходим к обычному сервису
                        }
                    } else {
                        // Заметка папки или пакета
                        try {
                            java.lang.reflect.Method getPackageAnnotationMethod = 
                                realTimeServiceClass.getMethod("getPackageAnnotation", String.class);
                            String annotation = (String) getPackageAnnotationMethod.invoke(realTimeService, relativePath);
                            if (annotation != null && !annotation.isEmpty()) {
                                return annotation;
                            }
                        } catch (Exception e) {
                            // Ошибку рефлексии игнорируем, переходим к обычному сервису
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Сервис живого обновления недоступен — переходим к обычному сервису
        }
        
        return null;
    }
    
    /**
     * Возвращает сервис библиотеки сопоставлений
     * Экземпляр берётся заново при каждом вызове, чтобы переключатель срабатывал сразу
     */
    private static MappingLibraryService getMappingService() {
        // Каждый раз берём свежий экземпляр, чтобы переключатель срабатывал сразу
        return MappingLibraryService.getInstance();
    }
    
    /**
     * Путь файла относительно корня проекта, для сопоставления с шаблонами
     * @param project проект
     * @param file виртуальный файл
     * @return относительный путь с разделителем / (как в файле настроек)
     */
    @Nullable
    private String getRelativePathForMatching(@NotNull Project project, @NotNull VirtualFile file) {
        String projectPath = project.getBasePath();
        if (projectPath == null) {
            return null;
        }
        String filePath = file.getPath();
        
        if (filePath.startsWith(projectPath)) {
            // Обрезаем, только если есть вложенный путь
            if (filePath.length() > projectPath.length()) {
                String relativePath = filePath.substring(projectPath.length());

                return relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;
            } else if (filePath.equals(projectPath)) {
                // Это сам корень проекта
                return "";
            }
        }
        
        return null;
    }
}

