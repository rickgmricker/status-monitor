package com.gmricker.monitor.ui;

import java.awt.Rectangle;

final class WindowPlacement {
    private WindowPlacement() { }

    static Rectangle fit(Rectangle window, Rectangle workArea) {
        int width = Math.min(window.width, workArea.width);
        int height = Math.min(window.height, workArea.height);
        int x = Math.max(workArea.x, Math.min(window.x, workArea.x + workArea.width - width));
        int y = Math.max(workArea.y, Math.min(window.y, workArea.y + workArea.height - height));
        return new Rectangle(x, y, width, height);
    }
}
