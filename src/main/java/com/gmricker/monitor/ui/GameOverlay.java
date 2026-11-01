package com.gmricker.monitor.ui;

import com.gmricker.monitor.ingame.*;
import com.gmricker.monitor.metrics.SystemSnapshot;
import java.awt.*;
import javax.swing.JWindow;

/** Independent of the main frame: remains available when the monitor is compact or in the tray. */
final class GameOverlay implements AutoCloseable {
    private final JWindow window = new JWindow();
    private final OverlayCanvas canvas = new OverlayCanvas();
    private boolean styled;
    void hide() { window.setVisible(false); }
    GameOverlay() {
        window.setName("Status Monitor Ingame Overlay");
        window.setFocusableWindowState(false);
        window.setAutoRequestFocus(false);
        window.setAlwaysOnTop(true);
        window.setType(Window.Type.UTILITY);
        window.setBackground(new Color(0, 0, 0, 0));
        window.setContentPane(canvas);
    }
    void update(long pid, OverlaySettings settings, SystemSnapshot system, FrameStatistics.Snapshot frames) {
        var bounds = GameWindows.foregroundBounds(pid);
        if (bounds == null || bounds.width <= 0 || bounds.height <= 0 || settings.items().isEmpty()) {
            window.setVisible(false);
            return;
        }
        canvas.update(settings, system, frames);
        window.pack();
        if (!styled) { GameWindows.makeClickThrough(window); styled = true; }
        int width = Math.min(window.getWidth(), bounds.width);
        int height = Math.min(window.getHeight(), bounds.height);
        int marginX = Math.min(12, Math.max(0, (bounds.width - width) / 2));
        int marginY = Math.min(12, Math.max(0, (bounds.height - height) / 2));
        boolean right = settings.corner() == OverlaySettings.Corner.TOP_RIGHT || settings.corner() == OverlaySettings.Corner.BOTTOM_RIGHT;
        boolean bottom = settings.corner() == OverlaySettings.Corner.BOTTOM_LEFT || settings.corner() == OverlaySettings.Corner.BOTTOM_RIGHT;
        window.setBounds(right ? bounds.x + bounds.width - width - marginX : bounds.x + marginX,
                bottom ? bounds.y + bounds.height - height - marginY : bounds.y + marginY, width, height);
        if (!window.isVisible()) window.setVisible(true);
    }
    @Override public void close() { window.dispose(); }
}
