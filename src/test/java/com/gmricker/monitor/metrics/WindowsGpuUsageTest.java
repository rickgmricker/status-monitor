package com.gmricker.monitor.metrics;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WindowsGpuUsageTest {
    private static WindowsGpuUsage.CounterReading reading(String name, double value) {
        return new WindowsGpuUsage.CounterReading(name, 0, value);
    }

    @Test void memoryMatchesBusiestGpuRatherThanLargestMemoryUsage() {
        var sample = WindowsGpuUsage.aggregate(List.of(
                reading("pid_1_luid_0x0_0x1_phys_0_eng_0_engtype_3D", 30),
                reading("pid_2_luid_0x0_0x1_phys_0_eng_0_engtype_3D", 40),
                reading("pid_1_luid_0x0_0x1_phys_0_eng_1_engtype_Copy", 60),
                reading("pid_3_luid_0x0_0x2_phys_0_eng_0_engtype_3D", 65)), List.of(
                reading("luid_0x0_0x1_phys_0", 2L << 30),
                reading("luid_0x0_0x2_phys_0", 6L << 30)));
        assertEquals(.7, sample.load(), .0001);
        assertEquals(2L << 30, sample.memoryUsed());
    }

    @Test void unavailableMemoryPreservesGpuLoadAndZeroMemoryIsValid() {
        var engines = List.of(reading("pid_1_luid_0x0_0x1_phys_0_eng_0_engtype_3D", 110));
        assertEquals(new WindowsGpuUsage.Sample(1, -1), WindowsGpuUsage.aggregate(engines, List.of()));
        assertEquals(0, WindowsGpuUsage.aggregate(engines,
                List.of(reading("luid_0x0_0x1_phys_0", 0))).memoryUsed());
        assertEquals(WindowsGpuUsage.Sample.UNAVAILABLE, WindowsGpuUsage.aggregate(List.of(),
                List.of(reading("luid_0x0_0x1_phys_0", 100))));
    }

    @Test void ignoresInvalidMemoryReadings() {
        var engines = List.of(reading("pid_1_luid_0x0_0x1_phys_0_eng_0_engtype_3D", 10));
        var sample = WindowsGpuUsage.aggregate(engines, List.of(
                new WindowsGpuUsage.CounterReading("luid_0x0_0x1_phys_0", 5, 1024),
                reading("luid_0x0_0x1_phys_0", Double.NaN),
                reading("luid_0x0_0x1_phys_0", -1),
                reading("luid_0x0_0x1_phys_1", 1024)));
        assertEquals(-1, sample.memoryUsed());
    }
}
