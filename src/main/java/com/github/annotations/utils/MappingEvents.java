package com.github.annotations.utils;

import java.util.Locale;

/** Проверка путей событий без обращения к уже удалённому VirtualFile. */
public final class MappingEvents {
    private MappingEvents() {}

    public static boolean affects(String directory, String path) {
        if (directory == null || path == null) return false;
        String dir = directory.replace('\\', '/');
        String changed = path.replace('\\', '/');
        if (dir.equals(changed) || dir.startsWith(changed + "/")) return true;
        if (!changed.startsWith(dir + "/")) return false;
        String name = changed.substring(dir.length() + 1);
        return !name.contains("/") && name.toLowerCase(Locale.ROOT).endsWith(".json");
    }
}
