package com.gmricker.monitor.ui;

import java.awt.BasicStroke;
import com.gmricker.monitor.theme.ThemeColors;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import javax.swing.AbstractButton;
import javax.swing.Icon;

/** Vector controls remain sharp under Windows display scaling. */
final class WindowControlIcon implements Icon {
    enum Kind { PIN, SETTINGS, CLOSE, COMPACT, EXPAND }

    private final Kind kind;
    private final int size;

    WindowControlIcon(Kind kind) {
        this(kind, 18);
    }

    WindowControlIcon(Kind kind, int size) {
        this.kind = kind;
        this.size = size;
    }

    @Override
    public int getIconWidth() { return size; }

    @Override
    public int getIconHeight() { return size; }

    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
        var g = (Graphics2D) graphics.create();
        try {
            g.translate(x, y);
            g.scale(size / 18.0, size / 18.0);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            boolean selected = component instanceof AbstractButton button && button.isSelected();
            g.setColor(selected ? ThemeColors.accent() : component.getForeground());
            switch (kind) {
                case COMPACT -> {
                    g.drawLine(13, 5, 5, 13);
                    g.drawLine(5, 7, 5, 13);
                    g.drawLine(5, 13, 11, 13);
                }
                case EXPAND -> {
                    g.drawLine(5, 13, 13, 5);
                    g.drawLine(7, 5, 13, 5);
                    g.drawLine(13, 5, 13, 11);
                }
                case CLOSE -> {
                    g.drawLine(5, 5, 13, 13);
                    g.drawLine(13, 5, 5, 13);
                }
                case PIN -> {
                    var path = new Path2D.Double();
                    path.moveTo(6, 3);
                    path.lineTo(12, 3);
                    path.moveTo(7, 3);
                    path.lineTo(7, 8);
                    path.lineTo(4, 11);
                    path.lineTo(14, 11);
                    path.lineTo(11, 8);
                    path.lineTo(11, 3);
                    path.moveTo(9, 11);
                    path.lineTo(9, 16);
                    g.draw(path);
                }
                case SETTINGS -> {
                    for (int angle = 0; angle < 360; angle += 45) {
                        double radians = Math.toRadians(angle);
                        g.drawLine((int) Math.round(9 + 5 * Math.cos(radians)),
                                (int) Math.round(9 + 5 * Math.sin(radians)),
                                (int) Math.round(9 + 7 * Math.cos(radians)),
                                (int) Math.round(9 + 7 * Math.sin(radians)));
                    }
                    g.drawOval(4, 4, 10, 10);
                    g.drawOval(7, 7, 4, 4);
                }
            }
        } finally {
            g.dispose();
        }
    }
}
