package com.gmricker.monitor.window;

/** Tracks only visibility changes owned by the fullscreen feature. */
final class PinVisibility {
    private boolean hiddenForFullscreen;

    enum Action { NONE, HIDE, SHOW }

    Action update(boolean fullscreen, boolean visible, boolean minimized) {
        if (minimized) {
            hiddenForFullscreen = false;
            return Action.NONE;
        }
        if (fullscreen) {
            if (visible) {
                hiddenForFullscreen = true;
                return Action.HIDE;
            }
            return Action.NONE;
        }
        return release();
    }

    Action release() {
        if (!hiddenForFullscreen) {
            return Action.NONE;
        }
        hiddenForFullscreen = false;
        return Action.SHOW;
    }
}
