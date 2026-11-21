package com.gmricker.monitor.stress;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class CpuStressTestTest {
    @Test void blockIsDeterministicAndDependsOnSeed() {
        assertEquals(CpuStressTest.block(42), CpuStressTest.block(42));
        assertNotEquals(CpuStressTest.block(42), CpuStressTest.block(43));
    }

    @Test void correctCalculationsAreVerifiedUntilStopped() throws Exception {
        try (var test = new CpuStressTest()) {
            test.startIndefinitely(2);
            waitFor(() -> test.status().verifiedBlocks() > 10);
            test.stop();
            assertTrue(test.awaitStopped(Duration.ofSeconds(5)));
            var status = test.status();
            assertFalse(status.running());
            assertTrue(status.stopped());
            assertFalse(status.unstable());
        }
    }

    @Test void wrongAnswerUnderLoadMarksProcessorUnstableAndStops() throws Exception {
        var calls = new AtomicLong();
        // The first 64 calls build the reference; a later answer goes wrong, as on an unstable CPU.
        try (var test = new CpuStressTest(seed -> calls.incrementAndGet() == 200 ? ~seed : seed)) {
            test.startIndefinitely(2);
            assertTrue(test.awaitStopped(Duration.ofSeconds(5)));
            var status = test.status();
            assertFalse(status.running());
            assertTrue(status.unstable());
            assertFalse(status.stopped());
        }
    }

    @Test void rejectsInvalidDurationAndSecondRun() throws Exception {
        try (var test = new CpuStressTest()) {
            assertThrows(IllegalArgumentException.class, () -> test.start(Duration.ZERO));
            assertThrows(IllegalArgumentException.class, () -> test.start(Duration.ofMinutes(11)));
            test.startIndefinitely(1);
            assertThrows(IllegalStateException.class, () -> test.startIndefinitely(1));
            test.stop();
            assertTrue(test.awaitStopped(Duration.ofSeconds(5)));
        }
    }

    @Test void closedTestCannotStartAgain() {
        var test = new CpuStressTest();
        test.close();
        assertThrows(IllegalStateException.class, () -> test.startIndefinitely(1));
    }

    static void waitFor(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!condition.getAsBoolean()) {
            assertTrue(System.nanoTime() < deadline, "timed out");
            Thread.sleep(10);
        }
    }
}
