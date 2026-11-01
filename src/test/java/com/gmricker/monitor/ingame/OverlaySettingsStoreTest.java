package com.gmricker.monitor.ingame;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class OverlaySettingsStoreTest {
    @TempDir Path directory;
    @Test void persistsEveryChoiceIncludingAllUnchecked() throws Exception {
        var store = new OverlaySettingsStore(directory);
        assertEquals(OverlaySettings.DEFAULT, store.load());
        var settings = new OverlaySettings(EnumSet.allOf(OverlaySettings.Item.class), OverlaySettings.Corner.BOTTOM_RIGHT, 22, 33, 0xABCDEF);
        store.save(settings);
        assertEquals(settings, new OverlaySettingsStore(directory).load());
        var empty = new OverlaySettings(Set.of(), settings.corner(), 10, 0, 0xFFFFFF);
        store.save(empty);
        assertEquals(empty, store.load());
    }
    @Test void recoversMissingAndInvalidFields() throws Exception {
        Files.writeString(directory.resolve("ingame.properties"), "fontSize=invalid\nopacity=200\ncorner=invalid\ntextRgb=bad\n");
        var loaded = new OverlaySettingsStore(directory).load();
        assertEquals(14, loaded.fontSize());
        assertEquals(100, loaded.opacity());
        assertEquals(OverlaySettings.Corner.TOP_LEFT, loaded.corner());
        assertEquals(OverlaySettings.DEFAULT.items(), loaded.items());
    }
    @Test void keepsAgreedDisplayOrder() {
        assertEquals(List.of("CPU", "GPU", "RAM", "LOW", "FPS", "FRAMETIME"),
                Arrays.stream(OverlaySettings.Item.values()).map(Enum::name).toList());
    }
    @Test void persistsCustomShortcutAndDefaultsForOldOrReservedValues() throws Exception {
        var store = new OverlaySettingsStore(directory);
        var defaults = OverlaySettings.DEFAULT;
        var custom = new OverlaySettings(defaults.items(), defaults.corner(), defaults.fontSize(), defaults.opacity(),
                defaults.textRgb(), new OverlayShortcut(0x78, 3));
        store.save(custom);
        assertEquals(custom, store.load());
        Files.writeString(directory.resolve("ingame.properties"), "CPU=true\n");
        assertEquals(OverlayShortcut.DEFAULT, store.load().shortcut());
        Files.writeString(directory.resolve("ingame.properties"), "shortcutKey=123\nshortcutModifiers=0\n");
        assertEquals(OverlayShortcut.DEFAULT, store.load().shortcut());
    }
    @Test void defaultsToAutomaticDetectionButPreservesExplicitManualMode() throws Exception {
        var store = new OverlaySettingsStore(directory);
        var defaults = OverlaySettings.DEFAULT;
        var automatic = new OverlaySettings(defaults.items(), defaults.corner(), 14, 70, defaults.textRgb(), defaults.shortcut(), true);
        store.save(automatic);
        assertEquals(automatic, store.load());
        Files.writeString(directory.resolve("ingame.properties"), "CPU=true\n");
        assertTrue(store.load().automaticDetection());
        var manual = new OverlaySettings(defaults.items(), defaults.corner(), 14, 70, defaults.textRgb(), defaults.shortcut(), false);
        store.save(manual);
        assertFalse(store.load().automaticDetection());
    }
}
