package com.gmricker.monitor.stress;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ThrottleDetectorTest {
    @Test void sustainedDropIsReported() {
        var detector = new ThrottleDetector(0.10, 3_000, 5_000);
        long time = 0;
        for (; time <= 10_000; time += 1_000) detector.sample(time, 4_500);
        for (; time <= 25_000; time += 1_000) detector.sample(time, 3_600);
        assertTrue(detector.throttled());
        assertEquals(4_500, detector.peak(), 1e-9);
        assertEquals(3_600, detector.lowest(), 1e-9);
        assertEquals(0.20, detector.drop(), 1e-9);
    }

    @Test void briefDipAndWarmupAreIgnored() {
        var detector = new ThrottleDetector(0.10, 3_000, 5_000);
        detector.sample(0, 5_500); // Turbo spike before the load settles.
        for (long time = 3_000; time <= 30_000; time += 1_000) {
            detector.sample(time, time == 12_000 || time == 13_000 ? 3_000 : 4_500);
        }
        assertFalse(detector.throttled());
        assertEquals(0, detector.drop());
    }

    @Test void invalidReadingsAndResetClearState() {
        var detector = new ThrottleDetector(0.10, 0, 0);
        detector.sample(0, -1);
        detector.sample(1, Double.NaN);
        assertEquals(-1, detector.peak());
        detector.sample(2, 1_000);
        detector.sample(3, 100);
        detector.sample(4, 100);
        detector.sample(5, 100);
        assertTrue(detector.throttled());
        detector.reset();
        assertFalse(detector.throttled());
        assertEquals(-1, detector.peak());
    }
}
