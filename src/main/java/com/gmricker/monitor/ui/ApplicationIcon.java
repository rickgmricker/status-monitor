package com.gmricker.monitor.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;

/** Shared application artwork at native sizes for the taskbar, windows and tray. */
final class ApplicationIcon {
    private static final List<Image> IMAGES = List.of(
            create(16), create(20), create(24), create(32), create(48), create(64), create(128));

    private ApplicationIcon() {}

    static List<Image> windowImages() {
        return IMAGES;
    }

    static Image trayImage() {
        return IMAGES.get(3);
    }

    private static BufferedImage create(int size) {
        var image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try {
            g.scale(size / 32.0, size / 32.0);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(30, 41, 59));
            g.fillRoundRect(1, 1, 30, 30, 8, 8);
            g.setColor(new Color(96, 192, 255));
            g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawPolyline(new int[]{5, 11, 14, 18, 21, 27}, new int[]{18, 18, 9, 24, 15, 15}, 6);
        } finally {
            g.dispose();
        }
        return image;
    }
}
