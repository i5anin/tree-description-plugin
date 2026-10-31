package com.github.annotations.model;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.Assert.*;

public class MappingLibraryStoreTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    private static final String VALID = "{\"mappings\":{\"files\":{\"App.java\":\"Заметка\"}}}";

    @Test public void savesUtf8AndNeverOverwritesExistingLibrary() throws Exception {
        Path directory = folder.getRoot().toPath();
        MappingLibraryStore.saveNew(directory, "library.json", VALID);
        assertEquals(VALID, Files.readString(directory.resolve("library.json")));
        assertThrows(IOException.class, () -> MappingLibraryStore.saveNew(directory, "library.json", "{\"mappings\":{}}"));
        assertEquals(VALID, Files.readString(directory.resolve("library.json")));
    }

    @Test public void rejectsUnsafeNamesAndLocalNotes() {
        for (String name : new String[]{"../notes.json", "a/b.json", "a\\b.json", "C:notes.json", "local-description.json"}) {
            assertThrows(name, IOException.class, () -> MappingLibraryStore.saveNew(folder.getRoot().toPath(), name, VALID));
        }
        assertEquals(0, folder.getRoot().list().length);
    }

    @Test public void rejectsInvalidContentBeforeCreatingFile() {
        assertThrows(IOException.class, () -> MappingLibraryStore.saveNew(folder.getRoot().toPath(), "bad.json", "{}"));
        assertFalse(Files.exists(folder.getRoot().toPath().resolve("bad.json")));
    }
}
