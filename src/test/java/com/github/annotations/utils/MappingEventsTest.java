package com.github.annotations.utils;

import org.junit.Test;
import static org.junit.Assert.*;

public class MappingEventsTest {
    private static final String DIR = "C:/project/.idea/tree-description";

    @Test public void acceptsDeletedFilesAndDirectoryAncestorsWithoutVirtualFile() {
        assertTrue(MappingEvents.affects(DIR, DIR + "/notes.json"));
        assertTrue(MappingEvents.affects(DIR, DIR));
        assertTrue(MappingEvents.affects(DIR, "C:/project/.idea"));
        assertTrue(MappingEvents.affects(DIR, DIR + "/notes.JSON"));
    }

    @Test public void ignoresNullsSiblingsAndNestedFilesNotLoadedByScanner() {
        assertFalse(MappingEvents.affects(DIR, null));
        assertFalse(MappingEvents.affects(null, DIR));
        assertFalse(MappingEvents.affects(DIR, DIR + "-old/notes.json"));
        assertFalse(MappingEvents.affects(DIR, DIR + "/nested/notes.json"));
        assertFalse(MappingEvents.affects(DIR, DIR + "/notes.txt"));
    }
}
