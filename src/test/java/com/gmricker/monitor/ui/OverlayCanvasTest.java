package com.gmricker.monitor.ui;

import com.gmricker.monitor.ingame.*;
import com.gmricker.monitor.metrics.SystemSnapshot;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OverlayCanvasTest {
    @Test void preservesOrderAndRemovesUncheckedRows() {
        var canvas = new OverlayCanvas();
        var all = new OverlaySettings(EnumSet.allOf(OverlaySettings.Item.class), OverlaySettings.Corner.TOP_LEFT, 14, 70, 0xFFFFFF);
        canvas.update(all, new SystemSnapshot(.5, 16L << 30, 8L << 30, 8, 3500, .8, 2L << 30), FrameStatistics.Snapshot.EMPTY);
        assertEquals("GPU  80%% · %.2f GiB".formatted(2.0), canvas.lines().get(1));
        assertEquals(List.of("CPU", "GPU", "RAM", "1%", "Média", "FPS", "Frametime"),
                canvas.lines().stream().map(line -> line.split(" ")[0]).toList());
        canvas.update(new OverlaySettings(Set.of(OverlaySettings.Item.GPU, OverlaySettings.Item.FPS),
                all.corner(), 14, 70, all.textRgb()), null, FrameStatistics.Snapshot.EMPTY);
        assertEquals(List.of("GPU    — · —", "FPS  —"), canvas.lines());
    }
    @Test void alignsHardwareColumns() {
        var canvas = new OverlayCanvas();
        var hardware = new OverlaySettings(Set.of(OverlaySettings.Item.CPU, OverlaySettings.Item.GPU, OverlaySettings.Item.RAM),
                OverlaySettings.Corner.TOP_LEFT, 14, 70, 0xFFFFFF);
        // CPU 6%, RAM 80% (12.8 of 16 GiB), GPU 8% with no VRAM used.
        long total = 16L << 30, used = (long) (12.8 * (1L << 30));
        canvas.update(hardware, new SystemSnapshot(.06, total, total - used, 8, 3830, .08, 0), FrameStatistics.Snapshot.EMPTY);
        var lines = canvas.lines();
        assertEquals(List.of(
                "CPU   6% · " + "%.2f GHz".formatted(3.83),
                "GPU   8% · " + "%.2f GiB".formatted(0.0),
                "RAM  80% · " + "%.1f GiB".formatted(12.8)), lines);
    }
    @Test void showsCurrentAndAverageFpsIndependently() {
        var canvas = new OverlayCanvas();
        var frames = new FrameStatistics.Snapshot(144.4, 98.6, Double.NaN, 6.9, new double[0], 1);
        canvas.update(new OverlaySettings(Set.of(OverlaySettings.Item.AVERAGE_FPS), OverlaySettings.Corner.TOP_LEFT,
                14, 70, 0xFFFFFF), null, frames);
        assertEquals(List.of("Média  99 FPS"), canvas.lines());
        canvas.update(new OverlaySettings(Set.of(OverlaySettings.Item.FPS, OverlaySettings.Item.AVERAGE_FPS),
                OverlaySettings.Corner.TOP_LEFT, 14, 70, 0xFFFFFF), null, frames);
        assertEquals(List.of("Média  99 FPS", "FPS    144"), canvas.lines());
    }
    @Test void alignsFrameValuesWithTheWidestLabel() {
        var canvas = new OverlayCanvas();
        var frames = new FrameStatistics.Snapshot(144.4, 98.6, 51.2, 6.9, new double[0], 1);
        canvas.update(new OverlaySettings(Set.of(OverlaySettings.Item.LOW, OverlaySettings.Item.AVERAGE_FPS,
                OverlaySettings.Item.FPS, OverlaySettings.Item.FRAMETIME), OverlaySettings.Corner.TOP_LEFT, 14, 70, 0xFFFFFF), null, frames);
        assertEquals(List.of("1% low     51 FPS", "Média      99 FPS", "FPS        144",
                "Frametime  " + "%.2f ms".formatted(6.9)), canvas.lines());
    }
    @Test void reservesGraphSpaceOnlyWhenSelected() {
        var canvas = new OverlayCanvas();
        var cpu = new OverlaySettings(Set.of(OverlaySettings.Item.CPU), OverlaySettings.Corner.TOP_LEFT, 14, 70, 0xFFFFFF);
        canvas.update(cpu, null, FrameStatistics.Snapshot.EMPTY);
        int height = canvas.getPreferredSize().height;
        canvas.update(new OverlaySettings(Set.of(OverlaySettings.Item.FRAMETIME), cpu.corner(), 14, 70, cpu.textRgb()), null, FrameStatistics.Snapshot.EMPTY);
        assertEquals(height + 48, canvas.getPreferredSize().height);
    }
}
