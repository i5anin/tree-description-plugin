package com.github.annotations.services;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

/**
 * Создаёт AnnotationService при открытии проекта.
 *
 * Сервис при создании читает заметки из .idea/tree-description и переносит
 * старую папку .td-maps; без этой активности он создавался бы лениво — при
 * первой отрисовке дерева, прямо в потоке интерфейса.
 */
public class ProjectInitActivity implements StartupActivity {

    private static final Logger LOG = Logger.getInstance(ProjectInitActivity.class);

    @Override
    public void runActivity(@NotNull Project project) {
        try {
            project.getService(AnnotationService.class);
        } catch (RuntimeException e) {
            LOG.warn("Не удалось создать сервис заметок при открытии проекта", e);
        }
    }
}
