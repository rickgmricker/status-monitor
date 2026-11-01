package com.gmricker.monitor.window;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.POINT;
import com.sun.jna.platform.win32.WinDef.RECT;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.W32APIOptions;
import java.awt.Rectangle;
import java.awt.Window;

/** Queries Win32 coordinates throughout, avoiding Java/Win32 DPI conversion. */
public final class WindowsFullscreenDetector {
    public interface WindowApi extends User32 {
        boolean IsIconic(HWND window);
        boolean ClientToScreen(HWND window, POINT point);
    }

    private final WindowApi user32 = Native.load("user32", WindowApi.class, W32APIOptions.DEFAULT_OPTIONS);
    private final HWND monitorWindow;

    public WindowsFullscreenDetector(Window window) {
        monitorWindow = new HWND(Native.getComponentPointer(window));
    }

    public boolean isFullscreenOnSameMonitor() {
        var foreground = user32.GetForegroundWindow();
        if (foreground == null || !user32.IsWindowVisible(foreground)
                || user32.IsIconic(foreground)) {
            return false;
        }
        var processId = new IntByReference();
        user32.GetWindowThreadProcessId(foreground, processId);
        if (Integer.toUnsignedLong(processId.getValue()) == ProcessHandle.current().pid()) {
            return false;
        }
        var className = new char[256];
        if (user32.GetClassName(foreground, className, className.length) == 0) {
            return false; // The foreground window can disappear during a query.
        }
        if (switch (Native.toString(className)) {
            case "Progman", "WorkerW", "Shell_TrayWnd", "Shell_SecondaryTrayWnd" -> true;
            default -> false;
        }) {
            return false;
        }
        var screen = user32.MonitorFromWindow(foreground, WinUser.MONITOR_DEFAULTTONEAREST);
        var ownScreen = user32.MonitorFromWindow(monitorWindow, WinUser.MONITOR_DEFAULTTONEAREST);
        if (screen == null || ownScreen == null || !screen.equals(ownScreen)) {
            return false;
        }
        var info = new WinUser.MONITORINFO();
        var client = new RECT();
        if (!user32.GetMonitorInfo(screen, info).booleanValue()
                || !user32.GetClientRect(foreground, client)) {
            return false;
        }
        var origin = new POINT();
        if (!user32.ClientToScreen(foreground, origin)) {
            return false;
        }
        return FullscreenGeometry.coversMonitor(
                new Rectangle(origin.x, origin.y, client.right - client.left, client.bottom - client.top),
                info.rcMonitor.toRectangle());
    }
}
