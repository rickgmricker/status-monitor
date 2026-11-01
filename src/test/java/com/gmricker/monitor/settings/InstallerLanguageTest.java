package com.gmricker.monitor.settings;

import com.gmricker.monitor.i18n.Language;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class InstallerLanguageTest {
    @TempDir Path directory;
    @Test void firstLaunchUsesInstallerLanguageWithoutCreatingPreferences() throws Exception {
        Path file = directory.resolve("settings.properties");
        assertEquals(Language.ENGLISH, new SettingsStore(file, () -> Language.ENGLISH).load().language());
        assertFalse(Files.exists(file));
    }
    @Test void existingExplicitChoiceWinsWithoutConsultingInstaller() throws Exception {
        Path file = directory.resolve("settings.properties");
        Files.writeString(file, "language=pt-BR\ncompact=true\n");
        var settings = new SettingsStore(file, () -> { throw new AssertionError("Installer should not override user choice"); }).load();
        assertEquals(Language.PORTUGUESE, settings.language());
        assertTrue(settings.compact());
    }
    @Test void legacyPreferencesUseInstallerLanguageAndPreserveOtherOptions() throws Exception {
        Path file = directory.resolve("settings.properties");
        Files.writeString(file, "pinned=true\nreducedCompact=true\ncompactX=10\ncompactY=20\n");
        var settings = new SettingsStore(file, () -> Language.ENGLISH).load();
        assertEquals(Language.ENGLISH, settings.language());
        assertTrue(settings.pinned());
        assertTrue(settings.reducedCompact());
        assertEquals(10, settings.compactX());
    }
}
