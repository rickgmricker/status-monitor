package com.gmricker.monitor.settings;

import static com.gmricker.monitor.i18n.I18n.tr;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import com.gmricker.monitor.theme.Theme;
import com.gmricker.monitor.i18n.Language;
import com.gmricker.monitor.installer.InstallerPreferences;
import java.util.function.Supplier;

public final class SettingsStore {
    private final Path path;
    private final Supplier<Language> initialLanguage;

    public SettingsStore(Path path) {
        this(path, InstallerPreferences::initialLanguage);
    }

    SettingsStore(Path path, Supplier<Language> initialLanguage) {
        this.path = path.toAbsolutePath();
        this.initialLanguage = initialLanguage;
    }

    public Path directory() {
        return path.getParent();
    }

    public static SettingsStore forCurrentUser() {
        String localAppData = System.getenv("LOCALAPPDATA");
        Path directory = localAppData != null && !localAppData.isBlank()
                ? Path.of(localAppData, "Status Monitor")
                : Path.of(System.getProperty("user.home"), "Status Monitor");
        return new SettingsStore(directory.resolve("settings.properties"));
    }

    public MonitorSettings load() throws IOException {
        var values = new Properties();
        if (!Files.notExists(path)) {
            try (var input = Files.newInputStream(path)) {
                values.load(input);
            } catch (IllegalArgumentException malformed) {
                throw new IOException(tr("Arquivo de configurações inválido."), malformed);
            }
        }
        String language = values.getProperty("language");
        if (language == null) language = initialLanguage.get().id();
        return new MonitorSettings(Boolean.parseBoolean(values.getProperty("compact", "false")),
                Boolean.parseBoolean(values.getProperty("pinned", "false")),
                Boolean.parseBoolean(values.getProperty("closeToTray", "false")),
                Theme.fromId(values.getProperty("theme")),
                coordinate(values.getProperty("compactX")), coordinate(values.getProperty("compactY")),
                Boolean.parseBoolean(values.getProperty("reducedCompact", "false")),
                Language.fromId(language));
    }

    private static Integer coordinate(String value) {
        try {
            return value == null ? null : Integer.valueOf(value);
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    public void save(MonitorSettings settings) throws IOException {
        Files.createDirectories(path.getParent());
        var temporary = Files.createTempFile(path.getParent(), "settings-", ".tmp");
        try {
            var values = new Properties();
            values.setProperty("compact", Boolean.toString(settings.compact()));
            values.setProperty("reducedCompact", Boolean.toString(settings.reducedCompact()));
            values.setProperty("pinned", Boolean.toString(settings.pinned()));
            values.setProperty("closeToTray", Boolean.toString(settings.closeToTray()));
            values.setProperty("theme", settings.theme().id());
            values.setProperty("language", settings.language().id());
            if (settings.compactX() != null && settings.compactY() != null) {
                values.setProperty("compactX", settings.compactX().toString());
                values.setProperty("compactY", settings.compactY().toString());
            }
            try (var output = Files.newOutputStream(temporary)) {
                values.store(output, "Status Monitor");
            }
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
