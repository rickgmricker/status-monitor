package com.gmricker.monitor.ingame;

import java.awt.Rectangle;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AutomaticGameDetectorTest {
    @Test void acceptsOnlyGamesAndCachesExpensiveInspection() {
        var foreground = new AtomicReference<>(new GameWindows.Target(1, "Browser"));
        var clock = new AtomicLong(); var calls = new AtomicInteger();
        var detector = new AutomaticGameDetector(foreground::get, pid -> { calls.incrementAndGet(); return pid == 2; }, clock::get);
        assertNull(detector.detect()); assertNull(detector.detect()); assertEquals(1, calls.get());
        var game = new GameWindows.Target(2, "Game"); foreground.set(game);
        assertEquals(game, detector.detect()); assertEquals(2, calls.get());
        assertEquals(game, detector.detect()); assertEquals(2, calls.get());
        foreground.set(null); assertNull(detector.detect()); assertEquals(2, calls.get());
        foreground.set(game); assertEquals(game, detector.detect()); assertEquals(2, calls.get());
        clock.set(2_000_000_001L); assertEquals(game, detector.detect()); assertEquals(3, calls.get());
    }
    @Test void rechecksGamesStillLoadingTheirEngine() {
        var ready = new AtomicBoolean(); var clock = new AtomicLong();
        var game = new GameWindows.Target(42, "Loading");
        var detector = new AutomaticGameDetector(() -> game, pid -> ready.get(), clock::get);
        assertNull(detector.detect()); ready.set(true);
        assertNull(detector.detect());
        clock.set(2_000_000_001L); assertEquals(game, detector.detect());
    }
    @Test void distinguishesFullscreenFromMaximizedWorkAreaUsingMonitorCoordinates() {
        var monitor = new Rectangle(-1920, 0, 1920, 1080);
        assertTrue(GameWindows.fillsMonitor(new Rectangle(-1920, 0, 1920, 1080), monitor));
        assertFalse(GameWindows.fillsMonitor(new Rectangle(-1920, 0, 1920, 1040), monitor));
        assertFalse(GameWindows.fillsMonitor(new Rectangle(-1800, 100, 800, 600), monitor));
        assertFalse(GameWindows.fillsMonitor(new Rectangle(0, 0, 1920, 1080), monitor));
        assertFalse(GameWindows.fillsMonitor(new Rectangle(), new Rectangle()));
    }
}
