package com.gmricker.monitor.ingame;

import static com.gmricker.monitor.i18n.I18n.tr;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.*;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import java.awt.Rectangle;
import java.util.*;

public final class GameWindows {
    private interface ExtraUser32 extends StdCallLibrary {
        ExtraUser32 INSTANCE = Native.load("user32", ExtraUser32.class, W32APIOptions.DEFAULT_OPTIONS);
        boolean IsIconic(HWND window);
        boolean ClientToScreen(HWND window, POINT point);
    }
    private GameWindows() { }
    public record Target(long pid, String title, String windowClass) {
        public Target(long pid, String title) { this(pid, title, ""); }
        @Override public String toString() { return title + " (" + pid + ")"; }
    }
    public static List<Target> list() {
        return list(false);
    }
    public static List<Target> list(boolean showAllApplications) {
        var windows = new LinkedHashMap<Long, Target>();
        User32.INSTANCE.EnumWindows((handle, data) -> {
            if (!User32.INSTANCE.IsWindowVisible(handle)) return true;
            var pid = new IntByReference();
            User32.INSTANCE.GetWindowThreadProcessId(handle, pid);
            long id = Integer.toUnsignedLong(pid.getValue());
            if (id == ProcessHandle.current().pid()) return true;
            char[] title = new char[512];
            User32.INSTANCE.GetWindowText(handle, title, title.length);
            String text = Native.toString(title).strip();
            char[] windowClass = new char[256];
            User32.INSTANCE.GetClassName(handle, windowClass, windowClass.length);
            if (!text.isBlank()) windows.putIfAbsent(id, new Target(id, text, Native.toString(windowClass)));
            return true;
        }, null);
        return windows.values().stream()
                .filter(target -> showAllApplications || GameDetection.detect(target.pid(), target.windowClass()))
                .sorted(Comparator.comparing(Target::title, String.CASE_INSENSITIVE_ORDER)).toList();
    }
    public static Rectangle foregroundBounds(long targetPid) {
        var api = User32.INSTANCE;
        var handle = api.GetForegroundWindow();
        if (handle == null || ExtraUser32.INSTANCE.IsIconic(handle)) return null;
        var pid = new IntByReference();
        api.GetWindowThreadProcessId(handle, pid);
        if (Integer.toUnsignedLong(pid.getValue()) != targetPid) return null;
        var bounds = new RECT();
        var origin = new POINT();
        if (!api.GetClientRect(handle, bounds) || !ExtraUser32.INSTANCE.ClientToScreen(handle, origin)) return null;
        return new Rectangle(origin.x, origin.y, bounds.right - bounds.left, bounds.bottom - bounds.top);
    }
    public static Target foregroundFullscreenWindow() {
        return fullscreenWindow(User32.INSTANCE.GetForegroundWindow());
    }
    static Target fullscreenWindow(HWND handle) {
        var api = User32.INSTANCE;
        if (handle == null || !api.IsWindowVisible(handle) || ExtraUser32.INSTANCE.IsIconic(handle)) return null;
        var pid = new IntByReference();
        api.GetWindowThreadProcessId(handle, pid);
        long id = Integer.toUnsignedLong(pid.getValue());
        if (id == 0 || id == ProcessHandle.current().pid()) return null;
        var client = new RECT();
        var origin = new POINT();
        var monitor = api.MonitorFromWindow(handle, WinUser.MONITOR_DEFAULTTONEAREST);
        var info = new WinUser.MONITORINFO();
        if (monitor == null || !api.GetMonitorInfo(monitor, info).booleanValue()
                || !api.GetClientRect(handle, client) || !ExtraUser32.INSTANCE.ClientToScreen(handle, origin)) return null;
        if (!fillsMonitor(new Rectangle(origin.x, origin.y, client.right - client.left, client.bottom - client.top),
                info.rcMonitor.toRectangle())) return null;
        char[] title = new char[512];
        api.GetWindowText(handle, title, title.length);
        String text = Native.toString(title).strip();
        char[] windowClass = new char[256];
        api.GetClassName(handle, windowClass, windowClass.length);
        return new Target(id, text.isBlank() ? tr("Jogo detectado") : text, Native.toString(windowClass));
    }
    static boolean fillsMonitor(Rectangle client, Rectangle monitor) {
        return monitor.width > 0 && monitor.height > 0 && client.equals(monitor);
    }
    public static void makeClickThrough(java.awt.Window window) {
        var api = User32.INSTANCE;
        var handle = new HWND(Native.getComponentPointer(window));
        int style = api.GetWindowLong(handle, WinUser.GWL_EXSTYLE);
        Native.setLastError(0);
        int previous = api.SetWindowLong(handle, WinUser.GWL_EXSTYLE,
                style | 0x00080000 | 0x00000020 | 0x00000080 | 0x08000000);
        if (previous == 0 && Native.getLastError() != 0) throw new IllegalStateException(tr("Falha ao configurar overlay: ") + Native.getLastError());
    }
}
