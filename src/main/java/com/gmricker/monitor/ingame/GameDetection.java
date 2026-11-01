package com.gmricker.monitor.ingame;

import com.sun.jna.platform.win32.Kernel32Util;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.COM.COMUtils;
import com.sun.jna.platform.win32.COM.WbemcliUtil.WmiQuery;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Combines engine/install evidence with a rendering fallback excluding desktop/browser UI stacks. */
final class GameDetection {
    private enum ProcessProperty { COMMANDLINE }
    private record CommandCache(String executable, String command, long expires) { }
    private static final Map<Long, CommandCache> JAVA_COMMANDS = new LinkedHashMap<>();
    private static final Set<String> DESKTOP_APPS = Set.of(
            "chrome.exe", "msedge.exe", "firefox.exe", "brave.exe", "opera.exe", "opera_gx.exe",
            "vivaldi.exe", "chromium.exe", "arc.exe", "zen.exe", "thorium.exe", "browser.exe",
            "calculator.exe", "calculatorapp.exe", "calc.exe", "applicationframehost.exe", "explorer.exe", "systemsettings.exe",
            "searchhost.exe", "startmenuexperiencehost.exe", "shellexperiencehost.exe", "textinputhost.exe",
            "msedgewebview2.exe", "steam.exe", "steamwebhelper.exe", "gameoverlayui.exe", "epicgameslauncher.exe",
            "epicwebhelper.exe", "galaxyclient.exe", "galaxyclient helper.exe", "riotclientservices.exe",
            "riotclientux.exe", "riotclientuxrender.exe", "ubisoftconnect.exe", "upc.exe", "uplay.exe",
            "battle.net.exe", "agent.exe", "eadesktop.exe", "ealauncher.exe", "origin.exe",
            "discord.exe", "obs64.exe", "obs32.exe", "code.exe", "devenv.exe", "dwm.exe", "codex.exe",
            "video.ui.exe", "vlc.exe", "mpv.exe", "wmplayer.exe", "mediaplayer.exe", "notepad.exe",
            "blender.exe", "photoshop.exe", "ms-teams.exe", "teams.exe", "slack.exe", "spotify.exe",
            "unity.exe", "unity hub.exe", "unityhub.exe", "unrealeditor.exe", "ue4editor.exe", "godot.exe");

    static boolean detect(long pid) {
        return detect(pid, "");
    }
    static boolean detect(long pid, String windowClass) {
        boolean engineWindow = engineWindow(windowClass);
        try {
            var process = ProcessHandle.of(pid);
            if (process.isEmpty()) return false;
            var info = process.get().info();
            String executable = info.command().orElse("");
            if (executable.isBlank()) {
                try { executable = Kernel32Util.QueryFullProcessImageName((int) pid, 0); }
                catch (RuntimeException unavailable) { return engineWindow; }
            }
            String commandLine = info.commandLine().orElse("");
            String executableName = filename(normalized(executable));
            if (commandLine.isBlank() && (executableName.equals("java.exe") || executableName.equals("javaw.exe"))) {
                commandLine = javaCommandLine(pid, executable);
            }
            if (excluded(executable, commandLine)) return false;
            if (engineWindow) return true;
            if (likely(executable, commandLine, Set.of(), Set.of())) return true;
            var markers = new HashSet<String>();
            var file = Path.of(executable);
            var parent = file.getParent();
            if (parent != null) {
                String name = file.getFileName().toString();
                String stem = name.replaceFirst("(?i)\\.exe$", "");
                for (var marker : List.of("UnityPlayer.dll", "GameAssembly.dll", stem + "_Data", stem + ".pck",
                        "renpy", "game", "www/js/rpg_core.js", "js/rmmz_core.js", "www/js/rmmz_core.js",
                        "steam_api.dll", "steam_api64.dll", "Galaxy.dll", "Galaxy64.dll")) {
                    if (Files.exists(parent.resolve(marker))) markers.add(marker.toLowerCase(Locale.ROOT));
                }
            }
            if (likely(executable, commandLine, Set.of(), markers)) return true;
            try {
                var modules = WindowsProcessModules.read(pid);
                return likely(executable, commandLine, modules, markers);
            } catch (RuntimeException unavailable) {
                return false; // Protected processes remain selectable through "Show all".
            }
        } catch (RuntimeException inaccessible) { return engineWindow; }
    }
    static boolean engineWindow(String windowClass) {
        return Set.of("unitywndclass", "unrealwindow", "valve001", "cryengine", "grcwindow").contains(windowClass.toLowerCase(Locale.ROOT));
    }

    private static String javaCommandLine(long pid, String executable) {
        long now = System.nanoTime();
        synchronized (JAVA_COMMANDS) {
            var cached = JAVA_COMMANDS.get(pid);
            if (cached != null && cached.executable().equals(executable) && now < cached.expires()) return cached.command();
        }
        var result = new CompletableFuture<String>();
        // COM must stay on one native thread, including when called from the virtual refresh thread.
        Thread.ofPlatform().daemon(true).name("ingame-java-command").start(() -> {
            boolean initialized = false;
            String command = "";
            try {
                COMUtils.checkRC(Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_MULTITHREADED));
                initialized = true;
                var query = new WmiQuery<>("Win32_Process WHERE ProcessId = " + pid, ProcessProperty.class).execute(1000);
                if (query.getResultCount() > 0) {
                    Object value = query.getValue(ProcessProperty.COMMANDLINE, 0);
                    if (value != null) command = value.toString();
                }
            } catch (RuntimeException | LinkageError | TimeoutException unavailable) {
                // Unrecognized/protected games remain available through manual selection.
            } finally {
                if (initialized) Ole32.INSTANCE.CoUninitialize();
                result.complete(command);
            }
        });
        String command = "";
        try { command = result.get(1500, TimeUnit.MILLISECONDS); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        catch (ExecutionException | TimeoutException unavailable) { }
        synchronized (JAVA_COMMANDS) {
            if (JAVA_COMMANDS.size() >= 64) JAVA_COMMANDS.remove(JAVA_COMMANDS.keySet().iterator().next());
            JAVA_COMMANDS.put(pid, new CommandCache(executable, command,
                    System.nanoTime() + (command.isBlank() ? 5_000_000_000L : 30_000_000_000L)));
        }
        return command;
    }

    private static String normalized(String value) { return value.replace('\\', '/').toLowerCase(Locale.ROOT); }
    private static String filename(String path) { return path.substring(path.lastIndexOf('/') + 1); }

    static boolean excluded(String executable, String commandLine) {
        String path = normalized(executable);
        String name = filename(path);
        String command = normalized(commandLine);
        return path.isBlank() || DESKTOP_APPS.contains(name)
                || path.matches("^[a-z]:/windows/.*")
                || path.contains("/microsoft.windowscalculator_") || path.contains("/microsoft.zunevideo_")
                || path.contains("/microsoft.windows.photos_")
                || command.contains("--type=renderer") || command.contains("--type=gpu-process")
                || command.contains("--type=utility");
    }

    static boolean likely(String executable, String commandLine, Collection<String> loadedModules, Set<String> nearby) {
        if (excluded(executable, commandLine)) return false;
        String path = normalized(executable), name = filename(path), command = normalized(commandLine);
        if (path.contains("/steamapps/common/") || path.contains("/epic games/")
                || path.contains("/ubisoft game launcher/games/") || path.contains("/gog galaxy/games/")
                || path.contains("/xboxgames/") || path.contains("/amazon games/library/")) return true;
        if (name.endsWith("-win64-shipping.exe") || name.endsWith("-win32-shipping.exe")) return true;
        if ((name.equals("java.exe") || name.equals("javaw.exe"))
                && (command.contains("net.minecraft.client.main.main") || command.contains("net.minecraft.launchwrapper.launch")
                || command.contains("-dminecraft.launcher.brand=") || command.contains("net.fabricmc.loader.impl.launch.knot.knotclient"))) return true;
        var modules = new HashSet<String>();
        loadedModules.forEach(module -> modules.add(normalized(module)));
        if (modules.contains("unityplayer.dll") || modules.contains("crysystem.dll")
                || modules.contains("cryaction.dll") || modules.contains("engine2.dll")) return true;
        if (modules.contains("engine.dll") && modules.contains("tier0.dll") && modules.contains("vstdlib.dll")) return true;
        String stem = name.replaceFirst("\\.exe$", "");
        if (nearby.contains("unityplayer.dll") && (nearby.contains(stem + "_data") || nearby.contains("gameassembly.dll"))) return true;
        if (nearby.contains(stem + ".pck") || (nearby.contains("renpy") && nearby.contains("game"))
                || nearby.contains("www/js/rpg_core.js") || nearby.contains("js/rmmz_core.js") || nearby.contains("www/js/rmmz_core.js")) return true;
        boolean renders = modules.contains("d3d9.dll") || modules.contains("d3d8.dll") || modules.contains("ddraw.dll")
                || modules.contains("opengl32.dll") || modules.contains("vulkan-1.dll")
                || (modules.contains("dxgi.dll") && (modules.contains("d3d11.dll") || modules.contains("d3d12.dll") || modules.contains("d3d10.dll")));
        if (!renders) return false;
        boolean gameSdk = java.util.stream.Stream.of("steam_api.dll", "steam_api64.dll", "galaxy.dll", "galaxy64.dll")
                .anyMatch(module -> modules.contains(module) || nearby.contains(module));
        if (gameSdk) return true;
        if (modules.stream().anyMatch(module -> Set.of("windows.ui.xaml.dll", "microsoft.ui.xaml.dll", "chrome_elf.dll",
                "chrome.dll", "msedge.dll", "xul.dll", "libcef.dll", "wpfgfx_cor3.dll", "wpfgfx_v0400.dll",
                "presentationnative_cor3.dll", "presentationnative_v0400.dll", "jfxwebkit.dll").contains(module))) return false;
        if (name.equals("java.exe") || name.equals("javaw.exe"))
            return modules.stream().anyMatch(module -> module.startsWith("lwjgl") || module.contains("glfw"));
        // Custom engines and portable games need not live in a known launcher directory.
        return true;
    }
}
