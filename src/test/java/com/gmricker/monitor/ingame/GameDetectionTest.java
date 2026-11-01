package com.gmricker.monitor.ingame;

import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GameDetectionTest {
    private boolean game(String path, String command, Set<String> modules, Set<String> files) {
        return GameDetection.likely(path, command, modules, files);
    }
    @Test void rejectsBrowsersEvenWhenTheyUseGraphicsOrSitInGameFolders() {
        for (String executable : Set.of("chrome.exe", "msedge.exe", "firefox.exe", "brave.exe", "opera.exe", "msedgewebview2.exe")) {
            assertFalse(game("D:/Steam/steamapps/common/Example/" + executable, "", Set.of("dxgi.dll", "d3d11.dll"), Set.of()));
        }
    }
    @Test void rejectsWindowsUtilitiesAndGameLaunchers() {
        assertFalse(game("C:/Windows/System32/calc.exe", "", Set.of("d3d11.dll"), Set.of()));
        assertFalse(game("C:/Program Files/Epic Games/Launcher/Portal/Binaries/Win64/EpicGamesLauncher.exe", "", Set.of(), Set.of()));
        assertFalse(game("D:/Steam/steamapps/common/Example/steamwebhelper.exe", "", Set.of(), Set.of()));
        assertFalse(game("D:/Games/Unity.exe", "", Set.of("unityplayer.dll"), Set.of()));
    }
    @Test void desktopUiStacksAreRejectedEvenWhenTheyLoadRenderingLibraries() {
        assertFalse(game("C:/Apps/CalculatorApp.exe", "", Set.of("d3d11.dll", "dxgi.dll"), Set.of()));
        assertFalse(game("C:/Apps/Editor.exe", "", Set.of("opengl32.dll", "vulkan-1.dll", "wpfgfx_cor3.dll"), Set.of()));
        assertFalse(game("C:/Apps/Video.exe", "", Set.of("d3d11.dll", "dxgi.dll", "windows.ui.xaml.dll"), Set.of()));
        assertFalse(game("C:/Apps/BrowserWrapper.exe", "", Set.of("d3d11.dll", "dxgi.dll", "chrome_elf.dll"), Set.of()));
        assertFalse(game("C:/Apps/Unknown.exe", "", Set.of(), Set.of()));
    }
    @Test void recognizesLibraryAndShippingExecutables() {
        assertTrue(game("D:\\SteamLibrary\\steamapps\\common\\Example\\Game.exe", "", Set.of(), Set.of()));
        assertTrue(game("D:/Epic Games/Example/Example.exe", "", Set.of(), Set.of()));
        assertTrue(game("D:/XboxGames/Example/Content/Game.exe", "", Set.of(), Set.of()));
        assertTrue(game("D:/Custom/Example-Win64-Shipping.exe", "", Set.of(), Set.of()));
    }
    @Test void recognizesPortableGamesByEngineEvidence() {
        assertTrue(game("D:/Portable/Example.exe", "", Set.of("UnityPlayer.dll"), Set.of()));
        assertTrue(game("D:/Portable/Example.exe", "", Set.of(), Set.of("unityplayer.dll", "example_data")));
        assertTrue(game("D:/Portable/Example.exe", "", Set.of(), Set.of("example.pck")));
        assertTrue(game("D:/Portable/Example.exe", "", Set.of("engine.dll", "tier0.dll", "vstdlib.dll"), Set.of()));
        assertFalse(game("D:/Portable/Example.exe", "", Set.of("engine.dll"), Set.of()));
    }
    @Test void recognizesMinecraftWithoutClassifyingEveryJavaApplicationAsAGame() {
        assertTrue(game("C:/Java/bin/javaw.exe", "javaw -cp libs net.minecraft.client.main.Main", Set.of(), Set.of()));
        assertFalse(game("C:/Java/bin/javaw.exe", "javaw com.example.Editor", Set.of("opengl32.dll"), Set.of()));
    }
    @Test void excludesHelpersWithinGameInstallations() {
        assertFalse(game("D:/Epic Games/Example/Helper.exe", "Helper.exe --type=gpu-process", Set.of(), Set.of()));
    }
    @Test void recognizesCustomEnginesAndPortableRenderersOutsideKnownLibraries() {
        assertTrue(game("D:/MeusJogos/Example.exe", "", Set.of("d3d9.dll"), Set.of()));
        assertTrue(game("D:/Portable/Example.exe", "", Set.of("d3d11.dll", "dxgi.dll"), Set.of()));
        assertTrue(game("D:/Portable/Example.exe", "", Set.of("d3d12.dll", "dxgi.dll"), Set.of()));
        assertTrue(game("D:/Portable/Example.exe", "", Set.of("opengl32.dll"), Set.of()));
        assertTrue(game("D:/Portable/Example.exe", "", Set.of("vulkan-1.dll"), Set.of()));
        assertFalse(game("D:/Portable/Example.exe", "", Set.of("dxgi.dll"), Set.of()));
    }
    @Test void recognizesEngineWindowClassesWithoutRequiringModuleAccess() {
        assertTrue(GameDetection.engineWindow("UnityWndClass"));
        assertTrue(GameDetection.engineWindow("UnrealWindow"));
        assertTrue(GameDetection.engineWindow("grcWindow"));
        assertFalse(GameDetection.engineWindow("Chrome_WidgetWin_1"));
        assertFalse(GameDetection.engineWindow("Windows.UI.Core.CoreWindow"));
    }
    @Test void supportsJavaGameRuntimesWithoutAcceptingEveryJavaGui() {
        assertTrue(game("C:/Java/bin/javaw.exe", "", Set.of("lwjgl_opengl.dll", "opengl32.dll"), Set.of()));
        assertFalse(game("C:/Java/bin/javaw.exe", "", Set.of("d3d11.dll", "dxgi.dll", "opengl32.dll"), Set.of()));
    }
    @Test void supportsReportedGamesOutsideKnownLauncherFolders() {
        assertTrue(game("D:/Jogos/FlatOut 2/FlatOut2.exe", "", Set.of("d3d9.dll", "dinput8.dll"), Set.of()));
        assertTrue(game("D:/Jogos/PES 2017/PES2017.exe", "", Set.of("d3d9.dll", "dxgi.dll"), Set.of()));
        // Community ports can use different backends; neither requires a known engine or launcher.
        assertTrue(game("D:/Jogos/Black/Black.exe", "", Set.of("vulkan-1.dll"), Set.of()));
        assertTrue(game("D:/Jogos/Black/Black.exe", "", Set.of("d3d12.dll", "dxgi.dll"), Set.of()));
    }
}
