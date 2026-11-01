package com.gmricker.monitor.ui;
import static com.gmricker.monitor.i18n.I18n.tr;

import com.gmricker.monitor.theme.ThemeColors;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;
import javax.swing.JFrame;

final class WindowResizeGrip extends JComponent {
    WindowResizeGrip(JFrame window) {
        setPreferredSize(new Dimension(16, 16));
        setCursor(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR));
        setToolTipText(tr("Arraste para redimensionar"));
        var resize = new MouseAdapter() {
            private Point start;
            private Dimension size;

            @Override
            public void mousePressed(MouseEvent event) {
                if (event.getButton() == MouseEvent.BUTTON1) {
                    start = event.getLocationOnScreen();
                    size = window.getSize();
                }
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                if (start != null) {
                    var current = event.getLocationOnScreen();
                    var minimum = window.getMinimumSize();
                    window.setSize(Math.max(minimum.width, size.width + current.x - start.x),
                            Math.max(minimum.height, size.height + current.y - start.y));
                }
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                start = null;
            }
        };
        addMouseListener(resize);
        addMouseMotionListener(resize);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        graphics.setColor(ThemeColors.grip());
        for (int offset = 3; offset <= 9; offset += 3) {
            graphics.drawLine(getWidth() - offset - 1, getHeight() - 2,
                    getWidth() - 2, getHeight() - offset - 1);
        }
    }
}
