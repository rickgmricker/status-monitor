package com.gmricker.monitor.ui;

import static com.gmricker.monitor.i18n.I18n.tr;

import com.gmricker.monitor.ingame.*;
import com.gmricker.monitor.metrics.SystemSnapshot;
import javax.swing.JComponent;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import static com.gmricker.monitor.ingame.OverlaySettings.Item.*;

/** The exact same renderer is used by the settings preview and the native overlay. */
final class OverlayCanvas extends JComponent {
    private OverlaySettings settings = OverlaySettings.DEFAULT;
    private SystemSnapshot system;
    private FrameStatistics.Snapshot frames = FrameStatistics.Snapshot.EMPTY;

    OverlayCanvas() { setOpaque(false); }
    void update(OverlaySettings settings, SystemSnapshot system, FrameStatistics.Snapshot frames) {
        this.settings = settings;
        this.system = system;
        this.frames = frames;
        setFont(new Font(Font.MONOSPACED, Font.BOLD, settings.fontSize()));
        revalidate();
        repaint();
    }
    /** Hardware rows: name, usage and detail; columns are padded so they line up in the monospaced font. */
    private record HardwareRow(String name, String usage, String detail) { }
    private HardwareRow hardware(OverlaySettings.Item item) {
        return switch (item) {
            case CPU -> new HardwareRow("CPU", percent(system == null ? -1 : system.cpuLoad()),
                    system != null && Double.isFinite(system.cpuFrequencyMhz()) && system.cpuFrequencyMhz() > 0
                    ? "%.2f GHz".formatted(system.cpuFrequencyMhz() / 1000) : "");
            case GPU -> new HardwareRow("GPU", percent(system == null ? -1 : system.gpuLoad()),
                     (system != null && system.gpuMemoryUsed() >= 0 ? "%.2f GiB".formatted(system.gpuMemoryUsed() / 1073741824.0) : "—"));
            case RAM -> system != null && system.totalMemory() > 0 && system.freeMemory() >= 0
                    ? new HardwareRow("RAM", percent((double) (system.totalMemory() - system.freeMemory()) / system.totalMemory()),
                    "%.1f GiB".formatted((system.totalMemory() - system.freeMemory()) / 1073741824.0))
                    : new HardwareRow("RAM", "—", "");
            default -> null;
        };
    }
    List<String> lines() {
        var rows = new java.util.EnumMap<OverlaySettings.Item, HardwareRow>(OverlaySettings.Item.class);
        for (var item : List.of(CPU, GPU, RAM)) if (settings.items().contains(item)) rows.put(item, hardware(item));
        // At least "99%" wide, so single-digit loads don't shift the columns.
        int usageWidth = Math.max(3, rows.values().stream().mapToInt(row -> row.usage().length()).max().orElse(0));
        int detailWidth = rows.values().stream().mapToInt(row -> row.detail().length()).max().orElse(0);
        // Frame rows: values start in one column, right after the widest selected label.
        int labelWidth = java.util.stream.Stream.of(LOW, AVERAGE_FPS, FPS, FRAMETIME)
                .filter(settings.items()::contains).mapToInt(item -> frameLabel(item).length()).max().orElse(0);
        var result = new ArrayList<String>();
        for (var item : OverlaySettings.Item.values()) {
            if (!settings.items().contains(item)) continue;
            result.add(switch (item) {
                case CPU, GPU, RAM -> {
                    var row = rows.get(item);
                    yield row.name() + "  " + " ".repeat(usageWidth - row.usage().length()) + row.usage()
                            + (row.detail().isEmpty() ? "" : " · " + " ".repeat(detailWidth - row.detail().length()) + row.detail());
                }
                case LOW, AVERAGE_FPS, FPS, FRAMETIME -> {
                    String label = frameLabel(item);
                    yield label + " ".repeat(labelWidth - label.length() + 2) + frameValue(item);
                }
            });
        }
        return result;
    }
    private static String frameLabel(OverlaySettings.Item item) {
        return switch (item) {
            case LOW -> "1% low";
            case AVERAGE_FPS -> tr("Média");
            case FPS -> "FPS";
            default -> "Frametime";
        };
    }
    private String frameValue(OverlaySettings.Item item) {
        return switch (item) {
            case LOW -> number(frames.lowFps(), " FPS");
            case AVERAGE_FPS -> number(frames.averageFps(), " FPS");
            case FPS -> number(frames.currentFps(), "");
            default -> Double.isFinite(frames.frameMs()) ? "%.2f ms".formatted(frames.frameMs()) : "—";
        };
    }
    private static String percent(double value) { return Double.isFinite(value) && value >= 0 ? "%.0f%%".formatted(value * 100) : "—"; }
    private static String number(double value, String suffix) { return Double.isFinite(value) ? "%.0f".formatted(value) + suffix : "—"; }
    @Override public Dimension getPreferredSize() {
        var font = getFont() == null ? new Font(Font.MONOSPACED, Font.BOLD, settings.fontSize()) : getFont();
        var metrics = getFontMetrics(font);
        int width = lines().stream().mapToInt(metrics::stringWidth).max().orElse(120) + 20;
        int height = Math.max(1, lines().size()) * metrics.getHeight() + 16;
        if (settings.items().contains(FRAMETIME)) height += 48;
        return new Dimension(width, height);
    }
    @Override protected void paintComponent(Graphics graphics) {
        var g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(12, 14, 17, settings.opacity() * 255 / 100));
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
            g.setFont(new Font(Font.MONOSPACED, Font.BOLD, settings.fontSize()));
            g.setColor(new Color(settings.textRgb()));
            var metrics = g.getFontMetrics();
            int y = 8 + metrics.getAscent();
            for (var line : lines()) { g.drawString(line, 10, y); y += metrics.getHeight(); }
            if (settings.items().contains(FRAMETIME)) {
                int top = y - metrics.getAscent() + 3;
                int bottom = Math.min(getHeight() - 8, top + 40);
                g.setColor(new Color(settings.textRgb() | 0x40000000, true));
                g.drawLine(10, bottom, getWidth() - 10, bottom);
                var history = frames.history();
                double max = 33.33;
                for (double value : history) max = Math.max(max, value);
                g.setColor(new Color(settings.textRgb()));
                for (int i = 1; i < history.length; i++) {
                    int x1 = 10 + (i - 1) * (getWidth() - 20) / Math.max(1, history.length - 1);
                    int x2 = 10 + i * (getWidth() - 20) / Math.max(1, history.length - 1);
                    g.drawLine(x1, bottom - (int) (history[i - 1] / max * (bottom - top)),
                            x2, bottom - (int) (history[i] / max * (bottom - top)));
                }
            }
        } finally { g.dispose(); }
    }
}
