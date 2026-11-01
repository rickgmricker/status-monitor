package com.gmricker.monitor.ingame;

import java.util.ArrayDeque;
import java.util.Arrays;

/** Current FPS over one second; low over the last 60 s (at most 30,000 frames); mean since capture began. */
public final class FrameStatistics {
    public record Snapshot(double currentFps, double averageFps, double lowFps, double frameMs,
                           double[] history, int samples) {
        public static final Snapshot EMPTY = new Snapshot(Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, new double[0], 0);
    }
    private record Frame(double time, double ms) { }
    private final ArrayDeque<Frame> frames = new ArrayDeque<>();
    private double totalMs;
    private long totalFrames, lastArrival;
    private Snapshot rates = Snapshot.EMPTY;
    public synchronized void add(double timeSeconds, double ms) {
        if (!Double.isFinite(ms) || ms <= 0 || !Double.isFinite(timeSeconds) || timeSeconds < 0) return;
        if (!frames.isEmpty() && timeSeconds < frames.getLast().time) return;
        frames.addLast(new Frame(timeSeconds, ms));
        totalMs += ms;
        totalFrames++;
        lastArrival = System.nanoTime();
        while (frames.size() > 30_000 || timeSeconds - frames.getFirst().time > 60) frames.removeFirst();
    }
    public synchronized Snapshot snapshot() {
        return snapshot(true);
    }
    public synchronized Snapshot snapshot(boolean updateRates) {
        if (frames.isEmpty()) return Snapshot.EMPTY;
        var last = frames.getLast();
        boolean fresh = System.nanoTime() - lastArrival < 2_000_000_000L;
        double[] history = new double[Math.min(120, frames.size())];
        var recent = frames.descendingIterator();
        for (int index = history.length - 1; index >= 0; index--) history[index] = recent.next().ms;
        if (!updateRates && rates.samples() > 0) {
            return new Snapshot(fresh ? rates.currentFps() : Double.NaN, rates.averageFps(), rates.lowFps(),
                    fresh ? last.ms : Double.NaN, history, frames.size());
        }
        double recentMs = 0;
        int recentCount = 0;
        for (var iterator = frames.descendingIterator(); iterator.hasNext();) {
            var frame = iterator.next();
            if (last.time - frame.time > 1) break;
            recentMs += frame.ms;
            recentCount++;
        }
        var sorted = frames.stream().mapToDouble(Frame::ms).toArray();
        Arrays.sort(sorted);
        int slowest = Math.max(1, (int) Math.ceil(sorted.length * .01));
        double slowMs = 0;
        for (int i = sorted.length - slowest; i < sorted.length; i++) slowMs += sorted[i];
        rates = new Snapshot(fresh ? recentCount * 1000.0 / recentMs : Double.NaN,
                totalFrames * 1000.0 / totalMs, sorted.length >= 100 ? slowest * 1000.0 / slowMs : Double.NaN,
                fresh ? last.ms : Double.NaN, history, frames.size());
        return rates;
    }
}
