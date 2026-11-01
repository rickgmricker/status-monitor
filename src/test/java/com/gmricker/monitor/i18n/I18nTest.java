package com.gmricker.monitor.i18n;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class I18nTest {
    @Test void translatesMessagesKeepsNamesAndUsesLanguageNumberFormat() {
        var previous = I18n.current();
        var format = Locale.getDefault(Locale.Category.FORMAT);
        try {
            I18n.setLanguage(Language.ENGLISH);
            assertEquals("Settings", I18n.tr("Configurações"));
            assertEquals("VRAM · 2.50 GiB in use", I18n.tr("VRAM · %.2f GiB em uso").formatted(2.5));
            for (var name : new String[] {"Status", "Specs", "Stress", "Ingame", "Status Monitor", "Frametime", "CPU", "GPU", "RAM", "VRAM", "FPS", "Graphite", "NVIDIA GeForce RTX"}) {
                assertEquals(name, I18n.tr(name));
            }
            I18n.setLanguage(Language.PORTUGUESE);
            assertEquals("Configurações", I18n.tr("Configurações"));
            assertEquals("VRAM · 2,50 GiB em uso", I18n.tr("VRAM · %.2f GiB em uso").formatted(2.5));
        } finally {
            I18n.setLanguage(previous);
            Locale.setDefault(Locale.Category.FORMAT, format);
        }
    }

    @Test void catalogPreservesFormattingAndWhitespaceInEveryMessage() throws Exception {
        var catalog = new Properties();
        try (var input = I18n.class.getResourceAsStream("en.properties")) {
            assertNotNull(input);
            catalog.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        }
        var placeholder = Pattern.compile("%(?:\\d+\\$)?[-#+0,(]*\\d*(?:\\.\\d+)?[a-zA-Z%]");
        assertTrue(catalog.size() >= 249);
        for (var source : catalog.stringPropertyNames()) {
            var translated = catalog.getProperty(source);
            assertFalse(translated.isBlank(), source);
            assertEquals(placeholder.matcher(source).results().map(result -> result.group()).toList(),
                    placeholder.matcher(translated).results().map(result -> result.group()).toList(), source);
            assertEquals(source.startsWith(" "), translated.startsWith(" "), source);
            assertEquals(source.endsWith(" "), translated.endsWith(" "), source);
        }
    }
}
