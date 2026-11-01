package com.gmricker.monitor.metrics;

/** CPU and GPU use fractions from 0 to 1; a negative value means unavailable. */
public record SystemSnapshot(double cpuLoad, long totalMemory, long freeMemory,
                             int logicalProcessors, double cpuFrequencyMhz, double gpuLoad, long gpuMemoryUsed) {
    public SystemSnapshot(double cpuLoad, long totalMemory, long freeMemory,
                          int logicalProcessors, double cpuFrequencyMhz, double gpuLoad) {
        this(cpuLoad, totalMemory, freeMemory, logicalProcessors, cpuFrequencyMhz, gpuLoad, -1);
    }
}
