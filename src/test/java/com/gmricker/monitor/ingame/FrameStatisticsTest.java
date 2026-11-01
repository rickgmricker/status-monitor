package com.gmricker.monitor.ingame;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrameStatisticsTest {
    @Test void refreshesFrametimeWithoutChangingFpsUntilRequested() {
        var stats = new FrameStatistics();
        stats.add(.01, 10);
        var initial = stats.snapshot(false);
        stats.add(.03, 20);
        var fast = stats.snapshot(false);
        assertEquals(20, fast.frameMs());
        assertArrayEquals(new double[] {10, 20}, fast.history());
        assertEquals(initial.currentFps(), fast.currentFps());
        assertEquals(initial.averageFps(), fast.averageFps());
        var slow = stats.snapshot(true);
        assertEquals(2000.0 / 30, slow.currentFps(), .00001);
        assertEquals(2000.0 / 30, slow.averageFps(), .00001);
    }
    @Test void usesFrameTimesForMeanAndSlowestOnePercent() {
        var stats = new FrameStatistics();
        for (int i = 1; i <= 99; i++) stats.add(i * .01, 10);
        assertTrue(Double.isNaN(stats.snapshot().lowFps()));
        stats.add(1.09, 100);
        var snapshot = stats.snapshot();
        assertEquals(100_000.0 / 1090, snapshot.averageFps(), .00001);
        assertEquals(10, snapshot.lowFps(), .00001);
        assertEquals(100, snapshot.frameMs());
        assertEquals(100, snapshot.samples());
    }
    @Test void rejectsInvalidFramesAndBoundsHistory() {
        var stats = new FrameStatistics();
        stats.add(1, 0); stats.add(1, Double.NaN); stats.add(Double.NaN, 10);
        assertEquals(0, stats.snapshot().samples());
        for (int i = 0; i < 1000; i++) stats.add(i * .1, 100);
        assertTrue(stats.snapshot().samples() <= 601);
        assertEquals(120, stats.snapshot().history().length);
        assertEquals(10, stats.snapshot().averageFps(), .00001);
        stats.add(1, 1000);
        assertEquals(100, stats.snapshot().frameMs());
    }
    @Test void supportsHighFrameRatesWithBoundedStorage() {
        var stats = new FrameStatistics();
        for (int i = 0; i < 40_000; i++) stats.add(i * .001, 1);
        assertEquals(30_000, stats.snapshot().samples());
        assertEquals(1000, stats.snapshot().currentFps(), .00001);
    }
}
