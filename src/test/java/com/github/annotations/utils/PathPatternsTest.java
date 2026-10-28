package com.github.annotations.utils;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PathPatternsTest {

    // --- файлы ---

    @Test
    public void fileMatchesExactNameIgnoringCase() {
        assertTrue(PathPatterns.matchesFile("POM.xml", "pom.xml", null));
        assertFalse(PathPatterns.matchesFile("pom.xml.bak", "pom.xml", null));
    }

    @Test
    public void fileMatchesRegexAgainstWholeName() {
        assertTrue(PathPatterns.matchesFile("UserController.java", ".*Controller\\.java$", null));
        assertFalse(PathPatterns.matchesFile("UserService.java", ".*Controller\\.java$", null));
    }

    @Test
    public void filePatternWithFolderRequiresFileToLieInThatFolder() {
        assertTrue(PathPatterns.matchesFile("pom.xml", "com/common/pom.xml", "src/com/common/pom.xml"));
        assertFalse(PathPatterns.matchesFile("pom.xml", "com/common/pom.xml", "src/com/other/pom.xml"));
    }

    @Test
    public void filePatternWithFolderComparesOnlyNameWhenPathUnknown() {
        assertTrue(PathPatterns.matchesFile("pom.xml", "com/common/pom.xml", null));
        assertFalse(PathPatterns.matchesFile("build.xml", "com/common/pom.xml", null));
    }

    @Test
    public void invalidRegexFallsBackToContains() {
        assertTrue(PathPatterns.matchesFile("xa[b$y", "a[b$", null));
        assertFalse(PathPatterns.matchesFile("abc", "a[b$", null));
    }

    @Test(timeout = 2000)
    public void catastrophicRegexFromSharedLibraryGivesUpInsteadOfFreezing() {
        String evilPattern = "^(.*a){12}$";
        String name = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa!";

        assertFalse(PathPatterns.matchesFile(name, evilPattern, null));
        assertFalse(PathPatterns.matchesPackage(name, evilPattern));
    }

    // --- пакеты ---

    @Test
    public void singleSegmentPackagePatternMatchesFolderNameOnly() {
        assertTrue(PathPatterns.matchesPackage("com/app/Service", "service"));
        assertFalse(PathPatterns.matchesPackage("com/service/impl", "service"));
    }

    @Test
    public void multiSegmentPackagePatternMatchesPathEnding() {
        assertTrue(PathPatterns.matchesPackage("org/springframework/core", "springframework.core"));
        assertFalse(PathPatterns.matchesPackage("org/springframework/core/io", "springframework.core"));
        assertFalse(PathPatterns.matchesPackage("core", "springframework.core"));
    }

    @Test
    public void packageRegexRunsAgainstDottedPath() {
        assertTrue(PathPatterns.matchesPackage("com/app/service/impl", ".*\\.service\\..*"));
        assertFalse(PathPatterns.matchesPackage("com/app/services/impl", ".*\\.service\\..*"));
    }
}
