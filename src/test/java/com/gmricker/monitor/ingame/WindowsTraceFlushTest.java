package com.gmricker.monitor.ingame;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WindowsTraceFlushTest {
    @Test void retriesStartupAndFlushesOnlyTheNamedSessionWithNativeLayout() {
        var calls = new AtomicInteger();
        try (var flush = new WindowsTraceFlush("StatusMonitor-test", (handle, name, properties, control) -> {
            assertEquals(0, handle);
            assertEquals("StatusMonitor-test", name.toString());
            assertEquals(3, control);
            // EVENT_TRACE_PROPERTIES offsets on Windows x64, including WNODE_HEADER.
            assertEquals(4216, properties.getInt(0));
            assertEquals(2168, properties.getInt(112));
            assertEquals(120, properties.getInt(116));
            assertEquals(0, properties.getLong(24)); // no other session's GUID
            properties.setInt(116, 999); // Native output must not corrupt the next request.
            return calls.incrementAndGet() == 1 ? 4201 : 0;
        })) {
            flush.flush();
            flush.flush();
            flush.flush();
            assertEquals(3, calls.get());
        }
    }

    @Test void preservesDefaultDeliveryWhenFlushingIsNotPermitted() {
        var calls = new AtomicInteger();
        try (var flush = new WindowsTraceFlush("StatusMonitor-test", (handle, name, properties, control) -> {
            calls.incrementAndGet();
            return 5; // ERROR_ACCESS_DENIED
        })) {
            flush.flush();
            flush.flush();
            assertEquals(1, calls.get());
        }
    }
}
