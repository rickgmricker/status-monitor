package com.gmricker.monitor.theme;

import java.util.Locale;

public enum Theme {
    SIMPLE("Simple"), GRAFITE("Graphite"), RED("Red"), GREEN("Green"), BLUE("Blue");

    private final String label;
    Theme(String label) { this.label = label; }
    @Override public String toString() { return label; }

    public String id() { return name().toLowerCase(Locale.ROOT); }
    public boolean isDark() { return this != SIMPLE; }

    public static Theme fromId(String id) {
        if (id != null) {
            for (var theme : values()) {
                if (theme.id().equalsIgnoreCase(id.trim())) return theme;
            }
        }
        return SIMPLE;
    }
}
