package com.gmricker.monitor.window;

import com.sun.jna.LastErrorException;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinUser;
import javax.swing.JFrame;

/** Changes taskbar visibility without disposing the Swing window or its collectors. */
public final class WindowTaskbar {
    private static final int APP_WINDOW = 0x00040000;
    private static final int TOOL_WINDOW = 0x00000080;
    private static final int TASKBAR_BITS = APP_WINDOW | TOOL_WINDOW;
    private final JFrame window;
    private Integer originalBits;

    public WindowTaskbar(JFrame window) {
        this.window = window;
    }

    public void setHidden(boolean hidden) {
        if (!window.isDisplayable() || !System.getProperty("os.name", "").startsWith("Windows")) {
            return;
        }
        var user32 = User32.INSTANCE;
        var handle = new HWND(Native.getComponentPointer(window));
        int current = user32.GetWindowLong(handle, WinUser.GWL_EXSTYLE);
        if (originalBits == null) {
            originalBits = current & TASKBAR_BITS;
        }
        int target = (current & ~TASKBAR_BITS) | (hidden ? TOOL_WINDOW : originalBits);
        if (target == current) {
            return;
        }
        boolean visible = window.isVisible();
        // Windows requires hide/change/show to update an existing taskbar button.
        if (visible) {
            user32.ShowWindow(handle, WinUser.SW_HIDE);
        }
        try {
            Native.setLastError(0);
            int previous = user32.SetWindowLong(handle, WinUser.GWL_EXSTYLE, target);
            if (previous == 0 && Native.getLastError() != 0) {
                throw new LastErrorException(Native.getLastError());
            }
            user32.SetWindowPos(handle, null, 0, 0, 0, 0,
                    WinUser.SWP_NOMOVE | WinUser.SWP_NOSIZE | WinUser.SWP_NOZORDER
                            | WinUser.SWP_NOACTIVATE | WinUser.SWP_FRAMECHANGED);
        } finally {
            if (visible) {
                user32.ShowWindow(handle, WinUser.SW_SHOWNOACTIVATE);
            }
        }
    }
}
