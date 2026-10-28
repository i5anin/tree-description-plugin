package com.github.annotations.services;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * Сервис обновления VFS
 * Принудительно обновляет состояние виртуальной файловой системы IDE
 */
public class VFSRefreshService {
    
    private final Project project;
    
    public VFSRefreshService(Project project) {
        this.project = project;
    }
    
    /**
     * Принудительно обновляет указанный JSON-файл
     */
    public void refreshJsonFile(@NotNull String jsonPath) {
        try {
            // Принудительно обновляем состояние файла в VFS
            VirtualFile jsonFile = LocalFileSystem.getInstance()
                .refreshAndFindFileByPath(jsonPath);
            if (jsonFile != null) {
                jsonFile.refresh(false, false); // принудительное обновление
            }
        } catch (Exception e) {
            // Записываем ошибку, но не прерываем работу
            com.intellij.openapi.diagnostic.Logger.getInstance(VFSRefreshService.class)
                .warn("Не удалось обновить VFS: " + e.getMessage());
        }
    }
    
    /**
     * Обновляет всю папку .td-maps
     */
    public void refreshMappingsDirectory() {
        try {
            String basePath = project.getBasePath();
            if (basePath != null) {
                String mappingsPath = basePath + "/.td-maps";
                VirtualFile mappingsDir = LocalFileSystem.getInstance()
                    .refreshAndFindFileByPath(mappingsPath);
                if (mappingsDir != null) {
                    mappingsDir.refresh(false, false);
                }
            }
        } catch (Exception e) {
            com.intellij.openapi.diagnostic.Logger.getInstance(VFSRefreshService.class)
                .warn("Не удалось обновить папку .td-maps: " + e.getMessage());
        }
    }
}
