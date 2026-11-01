package com.gmricker.monitor.ingame;

import static com.gmricker.monitor.i18n.I18n.tr;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class OverlaySettingsStore {
    private final Path path;
    public OverlaySettingsStore(Path directory) { path = directory.resolve("ingame.properties"); }
    public OverlaySettings load() throws IOException {
        if (Files.notExists(path)) return OverlaySettings.DEFAULT;
        var values = new Properties();
        try (var input = Files.newInputStream(path)) { values.load(input); }
        catch (IllegalArgumentException invalid) { throw new IOException(tr("Preferências Ingame inválidas."), invalid); }
        var defaults = OverlaySettings.DEFAULT;
        var items = EnumSet.noneOf(OverlaySettings.Item.class);
        for (var item : OverlaySettings.Item.values()) {
            if (Boolean.parseBoolean(values.getProperty(item.name(), "" + defaults.items().contains(item)))) items.add(item);
        }
        OverlaySettings.Corner corner;
        try { corner = OverlaySettings.Corner.valueOf(values.getProperty("corner", defaults.corner().name())); }
        catch (IllegalArgumentException invalid) { corner = defaults.corner(); }
        OverlayShortcut shortcut;
        try { shortcut = new OverlayShortcut(integer(values, "shortcutKey", 0x72), integer(values, "shortcutModifiers", 0)); }
        catch (IllegalArgumentException invalid) { shortcut = OverlayShortcut.DEFAULT; }
        return new OverlaySettings(items, corner, integer(values, "fontSize", defaults.fontSize()),
                integer(values, "opacity", defaults.opacity()), integer(values, "textRgb", defaults.textRgb()), shortcut,
                Boolean.parseBoolean(values.getProperty("automaticDetection", Boolean.toString(defaults.automaticDetection()))));
    }
    private static int integer(Properties values, String key, int fallback) {
        try { return Integer.parseInt(values.getProperty(key)); }
        catch (NumberFormatException invalid) { return fallback; }
    }
    public void save(OverlaySettings settings) throws IOException {
        Files.createDirectories(path.getParent());
        var values = new Properties();
        for (var item : OverlaySettings.Item.values()) values.setProperty(item.name(), "" + settings.items().contains(item));
        values.setProperty("corner", settings.corner().name());
        values.setProperty("fontSize", "" + settings.fontSize());
        values.setProperty("opacity", "" + settings.opacity());
        values.setProperty("textRgb", "" + settings.textRgb());
        values.setProperty("shortcutKey", "" + settings.shortcut().virtualKey());
        values.setProperty("shortcutModifiers", "" + settings.shortcut().modifiers());
        values.setProperty("automaticDetection", "" + settings.automaticDetection());
        var temporary = Files.createTempFile(path.getParent(), "ingame-", ".tmp");
        try {
            try (var output = Files.newOutputStream(temporary)) { values.store(output, "Status Monitor - Ingame"); }
            try { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
