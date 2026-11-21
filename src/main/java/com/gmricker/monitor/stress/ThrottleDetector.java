package com.gmricker.monitor.stress;

/**
 * Flags a sustained drop of a performance reading (CPU clock, GPU frame rate) below the best level
 * reached under the same load. Thermal and power limits both show up this way.
 */
public final class ThrottleDetector {
    private static final int WINDOW = 3;

    private final double dropFraction;
    private final long warmupMillis;
    private final long sustainMillis;
    private final double[] recent = new double[WINDOW];
    private int count;
    private double peak = -1;
    private double lowest = -1;
    private double current = -1;
    private long belowSince = -1;
    private boolean throttled;

    public ThrottleDetector() { this(0.10, 3_000, 5_000); }

    ThrottleDetector(double dropFraction, long warmupMillis, long sustainMillis) {
        this.dropFraction = dropFraction;
        this.warmupMillis = warmupMillis;
        this.sustainMillis = sustainMillis;
    }

    public void reset() {
        count = 0;
        peak = lowest = current = belowSince = -1;
        throttled = false;
    }

    /** elapsedMillis is measured from the start of the load; invalid readings are ignored. */
    public void sample(long elapsedMillis, double value) {
        if (!Double.isFinite(value) || value <= 0 || elapsedMillis < warmupMillis) return;
        recent[count++ % WINDOW] = value;
        int filled = Math.min(count, WINDOW);
        double sum = 0;
        for (int index = 0; index < filled; index++) sum += recent[index];
        current = sum / filled; // A short moving average keeps one noisy reading from counting as a drop.
        if (current > peak) peak = current;
        if (current < peak * (1 - dropFraction)) {
            if (belowSince < 0) belowSince = elapsedMillis;
            if (elapsedMillis - belowSince >= sustainMillis) throttled = true;
        } else {
            belowSince = -1;
        }
        if (throttled && (lowest < 0 || current < lowest)) lowest = current;
    }

    public boolean throttled() { return throttled; }

    /** Best smoothed reading, or -1 before any valid sample. */
    public double peak() { return peak; }

    /** Lowest smoothed reading after throttling was confirmed, or -1. */
    public double lowest() { return lowest; }

    public double current() { return current; }

    /** Largest confirmed drop from the peak, as a fraction in [0, 1]. */
    public double drop() { return throttled && peak > 0 ? 1 - lowest / peak : 0; }
}
