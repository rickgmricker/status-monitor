package com.gmricker.monitor.ui;

import com.gmricker.monitor.i18n.I18n;
import com.gmricker.monitor.i18n.Language;
import com.gmricker.monitor.ingame.OverlaySettings;
import com.gmricker.monitor.metrics.SystemSnapshot;
import java.awt.Container;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LanguageUiTest {
    @TempDir Path directory;
    @Test void translatesExistingControlsAndDynamicReadingsWithoutResettingOptions() throws Exception {
        var previous = I18n.current();
        var format = Locale.getDefault(Locale.Category.FORMAT);
        try {
            SwingUtilities.invokeAndWait(() -> {
                I18n.setLanguage(Language.PORTUGUESE);
                var panel = new IngamePanel(directory);
                var status = new StatusPanel(new JLabel(I18n.tr("Atualização a cada 1 segundo")), new JLabel());
                status.updateReadings(new SystemSnapshot(.5, 16L << 30, 8L << 30, 8, 3500, .8, 5L << 29));
                var options = panel.settings();
                var tabs = new JTabbedPane();
                tabs.addTab("Status", status);
                tabs.addTab(I18n.tr("Sobre"), new JPanel());
                try {
                    I18n.setLanguage(Language.ENGLISH);
                    I18n.refresh(panel);
                    I18n.refresh(tabs);
                    assertEquals("About", tabs.getTitleAt(1));
                    assertEquals("Status", tabs.getTitleAt(0));
                    assertTrue(texts(panel).contains("Automatic detection"));
                    assertTrue(texts(panel).contains("Start overlay"));
                    assertTrue(texts(panel).stream().anyMatch(text -> text.startsWith("Waiting for a fullscreen or borderless game. Press F3")));
                    assertTrue(texts(status).contains("VRAM · 2.50 GiB in use"));
                    assertEquals(options, panel.settings());
                    assertEquals("Top left", OverlaySettings.Corner.TOP_LEFT.toString());
                    I18n.setLanguage(Language.PORTUGUESE);
                    I18n.refresh(panel);
                    I18n.refresh(tabs);
                    assertEquals("Sobre", tabs.getTitleAt(1));
                    assertEquals("Status", tabs.getTitleAt(0));
                    assertTrue(texts(panel).contains("Detecção automática"));
                    assertTrue(texts(panel).contains("Iniciar overlay"));
                    assertTrue(texts(status).contains("VRAM · 2,50 GiB em uso"));
                    assertEquals(options, panel.settings());
                    assertEquals("Superior esquerdo", OverlaySettings.Corner.TOP_LEFT.toString());
                } finally { panel.close(); }
            });
        } finally {
            I18n.setLanguage(previous);
            Locale.setDefault(Locale.Category.FORMAT, format);
        }
    }

    private static List<String> texts(Container container) {
        var result = new ArrayList<String>();
        for (var component : container.getComponents()) {
            if (component instanceof AbstractButton button) result.add(button.getText());
            if (component instanceof JLabel label) result.add(label.getText());
            if (component instanceof JTextArea text) result.add(text.getText());
            if (component instanceof Container nested) result.addAll(texts(nested));
        }
        return result;
    }
}
