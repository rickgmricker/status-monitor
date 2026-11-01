package com.gmricker.monitor.installer;

import com.gmricker.monitor.i18n.Language;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;

/** Initial defaults only. Explicit preferences in settings.properties always win. */
public final class InstallerPreferences {
    public static final String REGISTRY_KEY = "Software\\GMRicker\\Status Monitor\\Installer";
    private InstallerPreferences() {}

    public static Language initialLanguage() {
        if (System.getProperty("jpackage.app-path") == null) return Language.PORTUGUESE;
        try {
            if (Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, REGISTRY_KEY, "InitialLanguage"))
                return Language.fromId(Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, REGISTRY_KEY, "InitialLanguage"));
        } catch (RuntimeException | LinkageError ignored) { }
        return Language.PORTUGUESE;
    }

    public static boolean managedCollector() {
        if (!System.getProperty("os.name", "").startsWith("Windows")) return false;
        try {
            return Advapi32Util.registryValueExists(WinReg.HKEY_LOCAL_MACHINE, REGISTRY_KEY, "ManagedCollector")
                    && Advapi32Util.registryGetIntValue(WinReg.HKEY_LOCAL_MACHINE, REGISTRY_KEY, "ManagedCollector") == 1;
        } catch (RuntimeException | LinkageError ignored) { return false; }
    }
}
