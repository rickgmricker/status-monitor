package com.gmricker.monitor.ingame;

import com.sun.jna.platform.win32.*;
import com.sun.jna.platform.win32.WinDef.DWORD;
import java.util.*;

/** Enumerates both native and WOW64 modules from the 64-bit monitor. */
final class WindowsProcessModules {
    private WindowsProcessModules() { }
    static final int SNAPSHOT_FLAGS = Tlhelp32.TH32CS_SNAPMODULE.intValue() | Tlhelp32.TH32CS_SNAPMODULE32.intValue();
    static Set<String> read(long pid) {
        var api = Kernel32.INSTANCE;
        for (int attempt = 0; attempt < 3; attempt++) {
            var snapshot = api.CreateToolhelp32Snapshot(new DWORD(SNAPSHOT_FLAGS), new DWORD(pid));
            if (snapshot == null || WinBase.INVALID_HANDLE_VALUE.equals(snapshot)) {
                int error = api.GetLastError();
                if (error == WinError.ERROR_BAD_LENGTH) continue;
                throw new Win32Exception(error);
            }
            try {
                var result = new HashSet<String>();
                var module = new Tlhelp32.MODULEENTRY32W();
                if (!api.Module32FirstW(snapshot, module)) {
                    int error = api.GetLastError();
                    if (error == WinError.ERROR_NO_MORE_FILES) return result;
                    if (error == WinError.ERROR_BAD_LENGTH) continue;
                    throw new Win32Exception(error);
                }
                do {
                    result.add(module.szModule().toLowerCase(Locale.ROOT));
                    module = new Tlhelp32.MODULEENTRY32W();
                } while (api.Module32NextW(snapshot, module));
                int error = api.GetLastError();
                if (error == WinError.ERROR_BAD_LENGTH) continue;
                if (error != 0 && error != WinError.ERROR_NO_MORE_FILES) throw new Win32Exception(error);
                return result;
            } finally { api.CloseHandle(snapshot); }
        }
        throw new Win32Exception(WinError.ERROR_BAD_LENGTH);
    }
}
