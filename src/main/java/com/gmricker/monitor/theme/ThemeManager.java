package com.gmricker.monitor.theme;

import static com.gmricker.monitor.i18n.I18n.tr;

import com.formdev.flatlaf.FlatPropertiesLaf;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Window;
import java.io.IOException;
import java.util.Properties;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.plaf.ColorUIResource;

public final class ThemeManager {
    private static Theme current = Theme.SIMPLE;
    private ThemeManager() {}
    public static Theme current() { return current; }

    public static void apply(Theme theme) {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException(tr("Troque o tema na thread da interface."));
        }
        try {
            if (theme.isDark()) {
                var palette = loadPalette("Grafite.properties");
                if (theme != Theme.GRAFITE) palette.putAll(loadPalette(theme + ".properties"));
                UIManager.setLookAndFeel(new FlatPropertiesLaf(theme.toString(), palette));
            } else {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                simpleDefaults();
            }
        } catch (IOException | ReflectiveOperationException | UnsupportedLookAndFeelException failure) {
            throw new IllegalStateException(tr("Não foi possível aplicar o tema ") + theme + ".", failure);
        }
        current = theme;
        for (var window : Window.getWindows()) {
            SwingUtilities.updateComponentTreeUI(window);
            refreshCustom(window);
            if (window instanceof Dialog && window.isDisplayable()) window.pack();
            nativeCaption(window);
            window.validate();
            window.repaint();
        }
    }

    private static Properties loadPalette(String resource) throws IOException {
        try (var input = ThemeManager.class.getResourceAsStream(resource)) {
            if (input == null) throw new IOException(tr("O tema ") + resource + tr(" não foi encontrado."));
            var palette = new Properties();
            palette.load(input);
            return palette;
        }
    }

    private static void simpleDefaults() {
        var defaults = UIManager.getLookAndFeelDefaults();
        defaults.put("Monitor.background", UIManager.getColor("Panel.background"));
        defaults.put("Monitor.headerBackground", UIManager.getColor("Panel.background"));
        defaults.put("Monitor.secondaryForeground", UIManager.getColor("Label.foreground"));
        defaults.put("Monitor.errorForeground", UIManager.getColor("Label.foreground"));
        put("frameBorderColor", new Color(180, 186, 194));
        put("headerBorderColor", new Color(210, 214, 220));
        put("hoverBackground", new Color(220, 225, 233));
        put("selectedBackground", new Color(224, 235, 255));
        put("accentForeground", new Color(37, 99, 235));
        put("closeHoverBackground", new Color(196, 43, 28));
        put("closeHoverForeground", Color.WHITE);
        put("gripForeground", new Color(150, 156, 164));
    }

    private static void put(String key, Color color) {
        UIManager.getLookAndFeelDefaults().put("Monitor." + key, new ColorUIResource(color));
    }

    private static void refreshCustom(Component component) {
        if (component instanceof ThemeAware aware) aware.applyTheme();
        if (component instanceof Container container) {
            for (var child : container.getComponents()) refreshCustom(child);
        }
    }

    public static void nativeCaption(Window window) {
        if (!(window instanceof Dialog) || !window.isDisplayable()
                || !System.getProperty("os.name", "").startsWith("Windows")) return;
        try {
            var handle = new HWND(Native.getComponentPointer(window));
            Dwm.INSTANCE.DwmSetWindowAttribute(handle, 20,
                    new IntByReference(current.isDark() ? 1 : 0), Integer.BYTES);
        } catch (RuntimeException | LinkageError ignored) {
            // Older Windows versions can still theme the dialog contents.
        }
    }

    public interface Dwm extends StdCallLibrary {
        Dwm INSTANCE = Native.load("dwmapi", Dwm.class);
        int DwmSetWindowAttribute(HWND window, int attribute, IntByReference value, int size);
    }
}
