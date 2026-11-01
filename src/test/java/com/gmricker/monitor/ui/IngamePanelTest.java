package com.gmricker.monitor.ui;

import java.awt.*;
import java.nio.file.Path;
import java.util.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import com.gmricker.monitor.ingame.*;

class IngamePanelTest {
    @TempDir Path directory;
    @Test void checkboxesApplyImmediatelyAndSurviveReopening() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = new IngamePanel(directory);
            var checkboxes = new ArrayList<JCheckBox>();
            collect(panel, checkboxes);
            assertEquals(8, checkboxes.size());
            assertFalse(checkboxes.stream().filter(box -> box.getText().equals("Mostrar todos os aplicativos")).findFirst().orElseThrow().isSelected());
            assertEquals(5, checkboxes.stream().filter(JCheckBox::isSelected).count());
            for (var checkbox : checkboxes) if (checkbox.isSelected()) checkbox.doClick(0);
            assertTrue(panel.settings().items().isEmpty());
            panel.close();
            var reopened = new IngamePanel(directory);
            try { assertTrue(reopened.settings().items().isEmpty()); }
            finally { reopened.close(); }
        });
    }
    private static void collect(Container container, java.util.List<JCheckBox> result) {
        for (var component : container.getComponents()) {
            if (component instanceof JCheckBox checkbox) result.add(checkbox);
            if (component instanceof Container child) collect(child, result);
        }
    }
    @Test void resetRestoresOnlyShortcutAndSavesIt() throws Exception {
        var defaults = OverlaySettings.DEFAULT;
        var custom = new OverlaySettings(Set.of(OverlaySettings.Item.FRAMETIME), OverlaySettings.Corner.BOTTOM_RIGHT,
                20, 40, 0x123456, new OverlayShortcut(0x78, 3));
        new OverlaySettingsStore(directory).save(custom);
        SwingUtilities.invokeAndWait(() -> {
            var panel = new IngamePanel(directory);
            try {
                assertEquals(custom, panel.settings());
                findReset(panel).doClick(0);
                assertEquals(OverlayShortcut.DEFAULT, panel.settings().shortcut());
                assertEquals(custom.items(), panel.settings().items());
                assertEquals(custom.corner(), panel.settings().corner());
                assertEquals(custom.textRgb(), panel.settings().textRgb());
            } finally { panel.close(); }
        });
        assertEquals(OverlayShortcut.DEFAULT, new OverlaySettingsStore(directory).load().shortcut());
    }
    private static JButton findReset(Container container) {
        for (var component : container.getComponents()) {
            if (component instanceof JButton button && button.getText().equals("Reset")) return button;
            if (component instanceof Container child) {
                var found = findReset(child);
                if (found != null) return found;
            }
        }
        return null;
    }
    @Test void automaticModeDisablesManualSelectionRemembersGameAcrossAltTabAndPersists() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = new IngamePanel(directory);
            try {
                var boxes = new ArrayList<JCheckBox>(); collect(panel, boxes);
                var automatic = boxes.stream().filter(box -> box.getText().equals("Detecção automática")).findFirst().orElseThrow();
                var showAll = boxes.stream().filter(box -> box.getText().equals("Mostrar todos os aplicativos")).findFirst().orElseThrow();
                var combo = findGames(panel);
                assertTrue(automatic.isSelected());
                assertFalse(combo.isEnabled());
                assertFalse(showAll.isEnabled());
                var game = new GameWindows.Target(ProcessHandle.current().pid(), "Jogo de teste");
                panel.acceptDetectedGame(game);
                assertEquals(game, combo.getSelectedItem());
                panel.acceptDetectedGame(null);
                assertEquals(game, combo.getSelectedItem(), "Alt+Tab should preserve the last live game");
                automatic.doClick(0);
                assertTrue(combo.isEnabled());
                assertTrue(showAll.isEnabled());
                automatic.doClick(0);
                assertTrue(panel.settings().automaticDetection());
            } finally { panel.close(); }
            var reopened = new IngamePanel(directory);
            try {
                assertTrue(reopened.settings().automaticDetection());
                assertFalse(findGames(reopened).isEnabled());
            } finally { reopened.close(); }
        });
    }
    private static JComboBox<?> findGames(Container container) {
        for (var component : container.getComponents()) {
            if (component instanceof JComboBox<?> combo && "Jogo".equals(combo.getAccessibleContext().getAccessibleName())) return combo;
            if (component instanceof Container child) {
                var found = findGames(child); if (found != null) return found;
            }
        }
        return null;
    }
    @Test void automaticWaitCanBeArmedAndCancelledBeforeAnyGameIsDetected() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = new IngamePanel(directory);
            try {
                var boxes = new ArrayList<JCheckBox>(); collect(panel, boxes);
                assertTrue(boxes.stream().filter(box -> box.getText().equals("Detecção automática")).findFirst().orElseThrow().isSelected());
                findButton(panel, "Iniciar overlay").doClick(0);
                assertTrue(findButton(panel, "Parar").isEnabled());
                findButton(panel, "Cancelar espera").doClick(0);
                assertFalse(findButton(panel, "Parar").isEnabled());
                findButton(panel, "Iniciar overlay").doClick(0);
                findButton(panel, "Parar").doClick(0);
                assertNotNull(findButton(panel, "Iniciar overlay"));
                assertFalse(findButton(panel, "Parar").isEnabled());
            } finally { panel.close(); }
        });
    }
    private static JButton findButton(Container container, String label) {
        for (var component : container.getComponents()) {
            if (component instanceof JButton button && label.equals(button.getText())) return button;
            if (component instanceof Container child) {
                var found = findButton(child, label); if (found != null) return found;
            }
        }
        return null;
    }
}
