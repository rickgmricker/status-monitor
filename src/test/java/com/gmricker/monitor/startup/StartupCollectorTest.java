package com.gmricker.monitor.startup;

import java.nio.file.*;
import java.util.List;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class StartupCollectorTest {
    @TempDir Path directory;
    @Test void includesCollectorAndLicenseInStartupCopy() throws Exception {
        var javaw = Files.writeString(directory.resolve("javaw.exe"), "test placeholder");
        var jar = directory.resolve("monitor.jar");
        try (var output = new JarOutputStream(Files.newOutputStream(jar))) { }
        var command = new String[1];
        var registry = new WindowsStartup.Registry() {
            public boolean exists() { return command[0] != null; }
            public void write(String value) { command[0] = value; }
            public void remove() { command[0] = null; }
        };
        new WindowsStartup(directory, javaw, List.of(jar), registry).setEnabled(true);
        assertTrue(Files.isRegularFile(directory.resolve("startup-app/tools/presentmon/PresentMon.exe")));
        assertTrue(Files.readString(directory.resolve("startup-app/tools/presentmon/LICENSE.txt")).contains("Intel Corporation"));
        assertTrue(command[0].contains("--startup"));
        assertTrue(command[0].contains("startup-app"));
    }
    @Test void installedAppUsesItsLauncherWithoutRequiringJavawOrDeployingJars() throws Exception {
        var installed = directory.resolve("Installed App"); Files.createDirectories(installed);
        var launcher = Files.writeString(installed.resolve("Status Monitor.exe"), "test placeholder");
        var command = new String[1];
        var registry = new WindowsStartup.Registry() {
            public boolean exists() { return command[0] != null; }
            public void write(String value) { command[0] = value; }
            public void remove() { command[0] = null; }
        };
        var startup = new WindowsStartup(directory, directory.resolve("missing-javaw.exe"), List.of(), registry, launcher);
        startup.setEnabled(true);
        assertEquals('"' + launcher.toString() + "\" --startup", command[0]);
        assertFalse(Files.exists(directory.resolve("startup-app")));
        startup.setEnabled(false);
        assertNull(command[0]);
    }
}
