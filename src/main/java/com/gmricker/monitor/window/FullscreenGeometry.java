package com.gmricker.monitor.window;

import java.awt.Rectangle;

final class FullscreenGeometry {
    private FullscreenGeometry() {}

    static boolean coversMonitor(Rectangle client, Rectangle monitor) {
        // Client bounds exclude the title bar and invisible resize borders.
        return monitor.width > 0 && monitor.height > 0 && client.equals(monitor);
    }
}
