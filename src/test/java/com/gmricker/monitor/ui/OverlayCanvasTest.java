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
        assertEquals("GPU  80%% · VRAM %.2f GiB".formatted(2.0), canvas.lines().get(1));
        assertEquals(List.of("CPU", "GPU", "RAM", "1%", "FPS", "Frametime"),
                canvas.lines().stream().map(line -> line.split(" ")[0]).toList());
        canvas.update(new OverlaySettings(Set.of(OverlaySettings.Item.GPU, OverlaySettings.Item.FPS),
                all.corner(), 14, 70, all.textRgb()), null, FrameStatistics.Snapshot.EMPTY);
        assertEquals(List.of("GPU  — · VRAM —", "FPS  méd — · atual —"), canvas.lines());
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
