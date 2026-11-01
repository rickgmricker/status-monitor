package com.gmricker.monitor.settings;

import com.gmricker.monitor.i18n.Language;
import com.gmricker.monitor.theme.Theme;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LanguageSettingsTest {
    @TempDir Path directory;
    @Test void savesLanguageTogetherWithAllOtherPreferences() throws Exception {
        var store = new SettingsStore(directory.resolve("settings.properties"));
        var saved = new MonitorSettings(true, true, true, Theme.BLUE, 150, 200, true, Language.ENGLISH);
        store.save(saved);
        assertEquals(saved, store.load());
        assertTrue(Files.readString(directory.resolve("settings.properties")).contains("language=en"));
    }
    @Test void olderAndUnknownLanguagePreferencesKeepPortuguese() throws Exception {
        var path = directory.resolve("settings.properties");
        var store = new SettingsStore(path);
        assertEquals(Language.PORTUGUESE, store.load().language());
        for (var language : new String[] {"", "language=unknown\n", "language=pt-BR\n"}) {
            Files.writeString(path, "compact=true\ntheme=green\n" + language);
            var loaded = store.load();
            assertEquals(Language.PORTUGUESE, loaded.language());
            assertTrue(loaded.compact());
            assertEquals(Theme.GREEN, loaded.theme());
        }
    }
}
