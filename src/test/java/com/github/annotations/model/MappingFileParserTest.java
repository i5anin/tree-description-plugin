package com.github.annotations.model;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MappingFileParserTest {

    private static final String LOCAL = "local-description.json";
    private static final String VALID_JSON =
        "{\"mappings\":{\"files\":{\"src/App.java\":\"Точка входа\"}}}";
    private static final String BROKEN_JSON = "{\"mappings\":{\"files\":{\"src/App.java\":";

    @Test
    public void readsNotesFromValidFile() {
        MappingFileParser.Result result = MappingFileParser.parseAll(Map.of(LOCAL, VALID_JSON), Map.of());

        assertEquals("Точка входа", noteOf(result, LOCAL, "src/App.java"));
        assertTrue(result.failedFileNames().isEmpty());
    }

    @Test
    public void keepsLastGoodNotesWhenFileBecomesInvalidWhileEditing() {
        Map<String, LocalMappingFile> before = MappingFileParser.parseAll(Map.of(LOCAL, VALID_JSON), Map.of()).files();

        MappingFileParser.Result result = MappingFileParser.parseAll(Map.of(LOCAL, BROKEN_JSON), before);

        assertEquals("Точка входа", noteOf(result, LOCAL, "src/App.java"));
        assertEquals(List.of(LOCAL), result.failedFileNames());
    }

    @Test
    public void keepsLastGoodNotesWhenFileIsEmpty() {
        Map<String, LocalMappingFile> before = MappingFileParser.parseAll(Map.of(LOCAL, VALID_JSON), Map.of()).files();

        MappingFileParser.Result result = MappingFileParser.parseAll(Map.of(LOCAL, ""), before);

        assertEquals("Точка входа", noteOf(result, LOCAL, "src/App.java"));
        assertEquals(List.of(LOCAL), result.failedFileNames());
    }

    @Test
    public void reportsInvalidFileThatNeverParsedAndSkipsIt() {
        MappingFileParser.Result result = MappingFileParser.parseAll(Map.of(LOCAL, BROKEN_JSON), Map.of());

        assertFalse(result.files().containsKey(LOCAL));
        assertEquals(List.of(LOCAL), result.failedFileNames());
    }

    @Test
    public void dropsNotesOfFileDeletedFromDiskAndKeepsTheRest() {
        String library = "spring.json";
        Map<String, LocalMappingFile> before = MappingFileParser.parseAll(
            Map.of(LOCAL, VALID_JSON, library, VALID_JSON), Map.of()).files();

        MappingFileParser.Result result = MappingFileParser.parseAll(Map.of(library, VALID_JSON), before);

        assertFalse(result.files().containsKey(LOCAL));
        assertEquals("Точка входа", noteOf(result, library, "src/App.java"));
    }

    @Test
    public void keepsLastGoodNotesForInvalidSchemaAndNullValues() {
        Map<String, LocalMappingFile> before = MappingFileParser.parseAll(Map.of(LOCAL, VALID_JSON), Map.of()).files();
        for (String bad : List.of("{}", "{\"mappings\":null}", "{\"mappings\":{\"files\":null}}",
                "{\"mappings\":{\"files\":{\"App.java\":null}}}",
                "{\"mappings\":{\"files\":{\"App.java\":42}}}")) {
            MappingFileParser.Result result = MappingFileParser.parseAll(Map.of(LOCAL, bad), before);
            assertEquals(bad, "Точка входа", noteOf(result, LOCAL, "src/App.java"));
            assertEquals(List.of(LOCAL), result.failedFileNames());
        }
    }

    @Test
    public void explicitEmptyMappingsCanClearNotes() {
        Map<String, LocalMappingFile> before = MappingFileParser.parseAll(Map.of(LOCAL, VALID_JSON), Map.of()).files();
        MappingFileParser.Result result = MappingFileParser.parseAll(Map.of(LOCAL, "{\"mappings\":{}}"), before);
        assertTrue(result.files().get(LOCAL).getMappings().getFiles().isEmpty());
        assertTrue(result.failedFileNames().isEmpty());
    }

    private static String noteOf(MappingFileParser.Result result, String fileName, String path) {
        LocalMappingFile file = result.files().get(fileName);
        return file == null ? null : file.getMappings().getFiles().get(path);
    }
}
