package com.gmricker.monitor.metrics;

import static com.gmricker.monitor.i18n.I18n.tr;

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;

/** Uses JVM management APIs without starting external commands. */
public final class SystemMetrics implements AutoCloseable {
    private final OperatingSystemMXBean operatingSystem;
    private WindowsCpuFrequency frequency;
    private boolean frequencyUnsupported = !System.getProperty("os.name", "").startsWith("Windows");
    private WindowsGpuUsage gpu;
    private boolean gpuUnsupported = !System.getProperty("os.name", "").startsWith("Windows");

    public SystemMetrics() {
        var bean = ManagementFactory.getOperatingSystemMXBean();
        if (!(bean instanceof OperatingSystemMXBean supported)) {
            throw new IllegalStateException(tr("Esta JVM não oferece as métricas necessárias."));
        }
        operatingSystem = supported;
    }

    public synchronized SystemSnapshot sample() {
        var gpuSample = sampleGpu();
        return new SystemSnapshot(operatingSystem.getCpuLoad(),
                operatingSystem.getTotalMemorySize(), operatingSystem.getFreeMemorySize(),
                operatingSystem.getAvailableProcessors(), sampleFrequencyMhz(), gpuSample.load(), gpuSample.memoryUsed());
    }

    private WindowsGpuUsage.Sample sampleGpu() {
        if (gpuUnsupported) {
            return WindowsGpuUsage.Sample.UNAVAILABLE;
        }
        try {
            if (gpu == null) {
                gpu = new WindowsGpuUsage();
            }
            return gpu.sample();
        } catch (LinkageError failure) {
            gpuUnsupported = true;
            return WindowsGpuUsage.Sample.UNAVAILABLE;
        } catch (RuntimeException failure) {
            if (gpu == null) {
                gpuUnsupported = true;
            }
            return WindowsGpuUsage.Sample.UNAVAILABLE;
        }
    }

    private double sampleFrequencyMhz() {
        if (frequencyUnsupported) {
            return -1;
        }
        try {
            // Initialize the native library on the sampling thread, after the window opens.
            if (frequency == null) {
                frequency = new WindowsCpuFrequency();
            }
            return frequency.sampleMhz();
        } catch (LinkageError failure) {
            frequencyUnsupported = true;
            return -1;
        } catch (RuntimeException failure) {
            // A missing frequency must not prevent collecting CPU and memory.
            if (frequency == null) {
                frequencyUnsupported = true;
            }
            return -1;
        }
    }

    @Override
    public synchronized void close() {
        frequencyUnsupported = true;
        gpuUnsupported = true;
        if (gpu != null) {
            gpu.close();
            gpu = null;
        }
        if (frequency != null) {
            frequency.close();
            frequency = null;
        }
    }
}
