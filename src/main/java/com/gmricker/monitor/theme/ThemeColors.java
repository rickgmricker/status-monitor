package com.gmricker.monitor.theme;

import java.awt.Color;
import javax.swing.UIManager;

public final class ThemeColors {
    private ThemeColors() {}

    private static Color color(String key, String fallback) {
        var value = UIManager.getColor("Monitor." + key);
        return value != null ? value : UIManager.getColor(fallback);
    }

    public static Color background() { return color("background", "Panel.background"); }
    public static Color header() { return color("headerBackground", "Panel.background"); }
    public static Color frameBorder() { return color("frameBorderColor", "Separator.foreground"); }
    public static Color headerBorder() { return color("headerBorderColor", "Separator.foreground"); }
    public static Color text() { return UIManager.getColor("Label.foreground"); }
    public static Color secondary() { return color("secondaryForeground", "Label.foreground"); }
    public static Color error() { return color("errorForeground", "Label.foreground"); }
    public static Color hover() { return color("hoverBackground", "Button.background"); }
    public static Color selected() { return color("selectedBackground", "Button.background"); }
    public static Color accent() { return color("accentForeground", "Label.foreground"); }
    public static Color closeHover() { return color("closeHoverBackground", "Button.background"); }
    public static Color closeText() { return color("closeHoverForeground", "Label.foreground"); }
    public static Color grip() { return color("gripForeground", "Label.foreground"); }
}
