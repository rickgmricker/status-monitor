package com.gmricker.monitor.ingame;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.Structure.FieldOrder;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.Guid.GUID;
import com.sun.jna.win32.StdCallLibrary;

/** Requests timely delivery from our own ETW session, without changing other sessions. */
final class WindowsTraceFlush implements AutoCloseable {
    interface TraceApi extends StdCallLibrary {
        TraceApi INSTANCE = Native.load("advapi32", TraceApi.class);
        int ControlTraceW(long handle, WString name, Pointer properties, int control);
    }

    @FieldOrder({"bufferSize", "providerId", "historicalContext", "timestamp", "guid", "clientContext", "flags"})
    public static class Wnode extends Structure {
        public int bufferSize, providerId;
        public long historicalContext, timestamp;
        public GUID guid = new GUID();
        public int clientContext, flags;
    }

    @FieldOrder({"wnode", "bufferSize", "minimumBuffers", "maximumBuffers", "maximumFileSize", "logFileMode",
            "flushTimer", "enableFlags", "ageLimit", "numberOfBuffers", "freeBuffers", "eventsLost",
            "buffersWritten", "logBuffersLost", "realTimeBuffersLost", "loggerThreadId", "logFileNameOffset", "loggerNameOffset"})
    public static class Properties extends Structure {
        public Wnode wnode = new Wnode();
        public int bufferSize, minimumBuffers, maximumBuffers, maximumFileSize, logFileMode, flushTimer,
                enableFlags, ageLimit, numberOfBuffers, freeBuffers, eventsLost, buffersWritten,
                logBuffersLost, realTimeBuffersLost;
        public Pointer loggerThreadId;
        public int logFileNameOffset, loggerNameOffset;
    }

    private final Memory buffer;
    private final Properties properties = new Properties();
    private final WString session;
    private final TraceApi api;
    private boolean unsupported;

    WindowsTraceFlush(String session) {
        this(session, TraceApi.INSTANCE);
    }

    WindowsTraceFlush(String session, TraceApi api) {
        this.api = api;
        this.session = new WString(session);
        buffer = new Memory(properties.size() + 4096);
        properties.wnode.bufferSize = (int) buffer.size();
        properties.loggerNameOffset = properties.size();
        properties.logFileNameOffset = properties.size() + 2048;
        properties.write();
    }

    void flush() {
        if (unsupported) return;
        buffer.clear();
        buffer.write(0, properties.getPointer().getByteArray(0, properties.size()), 0, properties.size());
        int result = api.ControlTraceW(0, session, buffer, 3); // EVENT_TRACE_CONTROL_FLUSH
        // The session may not exist yet while PresentMon is starting. Retry later.
        if (result != 0 && result != 4201) unsupported = true;
    }

    @Override public void close() { buffer.close(); }
}
