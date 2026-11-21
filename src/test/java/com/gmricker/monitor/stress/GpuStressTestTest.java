package com.gmricker.monitor.stress;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GpuStressTestTest {
    private static final class FakeWorkload implements GpuStressTest.Workload {
        final AtomicInteger verifications = new AtomicInteger();
        final AtomicBoolean closed = new AtomicBoolean();
        final int failAt;
        final RuntimeException initFailure;

        FakeWorkload(int failAt, RuntimeException initFailure) {
            this.failAt = failAt;
            this.initFailure = initFailure;
        }

        @Override public void initialize() { if (initFailure != null) throw initFailure; }
        @Override public void render() { Thread.onSpinWait(); }
        @Override public boolean verify() { return verifications.incrementAndGet() != failAt; }
        @Override public String device() { return "Fake GPU"; }
        @Override public void close() { closed.set(true); }
    }

    @Test void healthyGpuIsVerifiedUntilStopped() throws Exception {
        var workload = new FakeWorkload(-1, null);
        try (var test = new GpuStressTest(() -> workload, Duration.ofMillis(1))) {
            test.startIndefinitely();
            CpuStressTestTest.waitFor(() -> test.status().verifications() > 3);
            test.stop();
            assertTrue(test.awaitStopped(Duration.ofSeconds(5)));
            var status = test.status();
            assertFalse(status.unstable());
            assertNull(status.error());
            assertEquals("Fake GPU", status.device());
            assertTrue(status.frames() > 0);
            assertTrue(workload.closed.get());
        }
    }

    @Test void mismatchedFrameMarksGpuUnstable() throws Exception {
        var workload = new FakeWorkload(3, null);
        try (var test = new GpuStressTest(() -> workload, Duration.ofMillis(1))) {
            test.startIndefinitely();
            assertTrue(test.awaitStopped(Duration.ofSeconds(5)));
            var status = test.status();
            assertTrue(status.unstable());
            assertNotNull(status.error());
            assertEquals(2, status.verifications());
            assertTrue(workload.closed.get());
        }
    }

    @Test void driverFailureIsReportedWithoutInstability() throws Exception {
        var workload = new FakeWorkload(-1, new IllegalStateException("no driver"));
        try (var test = new GpuStressTest(() -> workload, Duration.ofMillis(1))) {
            test.startIndefinitely();
            assertTrue(test.awaitStopped(Duration.ofSeconds(5)));
            var status = test.status();
            assertEquals("no driver", status.error());
            assertFalse(status.unstable());
            assertTrue(workload.closed.get());
        }
    }
}
