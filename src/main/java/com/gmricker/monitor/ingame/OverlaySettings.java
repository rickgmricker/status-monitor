package com.gmricker.monitor.ingame;

import static com.gmricker.monitor.i18n.I18n.tr;

import java.util.Set;
import java.util.EnumSet;

public record OverlaySettings(Set<Item> items, Corner corner, int fontSize, int opacity, int textRgb,
                              OverlayShortcut shortcut, boolean automaticDetection) {
    public enum Item {
        CPU("CPU"), GPU("GPU"), RAM("RAM"), LOW("1% low"), AVERAGE_FPS("FPS médio"),
        FPS("FPS atual"), FRAMETIME("Frametime — valor e gráfico");
        private final String label;
        Item(String label) { this.label = label; }
        @Override public String toString() { return tr(label); }
    }
    public enum Corner {
        TOP_LEFT("Superior esquerdo"), TOP_RIGHT("Superior direito"),
        BOTTOM_LEFT("Inferior esquerdo"), BOTTOM_RIGHT("Inferior direito");
        private final String label;
        Corner(String label) { this.label = label; }
        @Override public String toString() { return tr(label); }
    }
    public static final OverlaySettings DEFAULT = new OverlaySettings(
            EnumSet.of(Item.CPU, Item.GPU, Item.RAM, Item.FPS, Item.AVERAGE_FPS), Corner.TOP_LEFT, 14, 70, 0xE2E5E7,
            OverlayShortcut.DEFAULT, true);
    public OverlaySettings {
        items = Set.copyOf(items);
        if (corner == null) corner = Corner.TOP_LEFT;
        fontSize = Math.clamp(fontSize, 10, 28);
        opacity = Math.clamp(opacity, 0, 100);
        textRgb &= 0xFFFFFF;
        if (shortcut == null) shortcut = OverlayShortcut.DEFAULT;
    }
    public OverlaySettings(Set<Item> items, Corner corner, int fontSize, int opacity, int textRgb) {
        this(items, corner, fontSize, opacity, textRgb, OverlayShortcut.DEFAULT, false);
    }
    public OverlaySettings(Set<Item> items, Corner corner, int fontSize, int opacity, int textRgb, OverlayShortcut shortcut) {
        this(items, corner, fontSize, opacity, textRgb, shortcut, false);
    }
}
