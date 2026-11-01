package com.gmricker.monitor.metrics;

import static com.gmricker.monitor.i18n.I18n.tr;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.Structure.FieldOrder;
import com.sun.jna.platform.win32.BaseTSD.DWORD_PTR;
import com.sun.jna.platform.win32.Pdh;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.platform.win32.WinNT.HANDLEByReference;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.W32APIOptions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Uses the busiest physical GPU engine, summing its usage across processes. */
final class WindowsGpuUsage implements AutoCloseable {
    private static final Pattern ENGINE = Pattern.compile(
            "(?:^|_)luid_(0x[0-9a-f]+_0x[0-9a-f]+)_phys_(\\d+)_eng_(\\d+)(?:_|$)");
    private static final Pattern ADAPTER = Pattern.compile(
            "^luid_(0x[0-9a-f]+_0x[0-9a-f]+)_phys_(\\d+)$");

    public interface CounterApi extends Pdh {
        int PdhGetFormattedCounterArrayW(HANDLE counter, int format,
                IntByReference bytes, IntByReference count, Pointer buffer);
    }

    @FieldOrder({"status", "value"})
    public static class CounterValue extends Structure {
        public int status;
        public double value;
    }

    @FieldOrder({"name", "value"})
    public static class CounterItem extends Structure {
        public Pointer name;
        public CounterValue value = new CounterValue();

        public CounterItem() {}

        CounterItem(Pointer pointer) {
            super(pointer);
            read();
        }
    }

    record CounterReading(String instance, int status, double value) {}
    record Sample(double load, long memoryUsed) {
        static final Sample UNAVAILABLE = new Sample(-1, -1);
    }

    private final CounterApi pdh = Native.load("pdh", CounterApi.class, W32APIOptions.DEFAULT_OPTIONS);
    private HANDLE query;
    private HANDLE counter;
    private HANDLE memoryCounter;
    private boolean primed;

    WindowsGpuUsage() {
        var queryRef = new HANDLEByReference();
        requireSuccess(pdh.PdhOpenQuery(null, new DWORD_PTR(0), queryRef));
        query = queryRef.getValue();
        try {
            var counterRef = new HANDLEByReference();
            requireSuccess(pdh.PdhAddEnglishCounter(query, "\\GPU Engine(*)\\Utilization Percentage",
                    new DWORD_PTR(0), counterRef));
            counter = counterRef.getValue();
            // Optional: drivers may expose engine usage without dedicated memory.
            var memoryRef = new HANDLEByReference();
            if (pdh.PdhAddEnglishCounter(query, "\\GPU Adapter Memory(*)\\Dedicated Usage",
                    new DWORD_PTR(0), memoryRef) == 0) {
                memoryCounter = memoryRef.getValue();
            }
        } catch (RuntimeException | LinkageError failure) {
            close();
            throw failure;
        }
    }

    Sample sample() {
        if (query == null) {
            return Sample.UNAVAILABLE;
        }
        if (pdh.PdhCollectQueryData(query) != 0) {
            primed = false;
            return Sample.UNAVAILABLE;
        }
        if (!primed) {
            primed = true;
            return Sample.UNAVAILABLE;
        }
        return aggregate(readCounter(counter), readCounter(memoryCounter));
    }

    private List<CounterReading> readCounter(HANDLE handle) {
        if (handle == null) return List.of();
        int format = Pdh.PDH_FMT_DOUBLE | Pdh.PDH_FMT_NOCAP100;
        for (int attempt = 0; attempt < 3; attempt++) {
            var bytes = new IntByReference();
            var count = new IntByReference();
            int result = pdh.PdhGetFormattedCounterArrayW(handle, format, bytes, count, null);
            if (result != Pdh.PDH_MORE_DATA || bytes.getValue() <= 0 || bytes.getValue() > 32 * 1024 * 1024) {
                return List.of();
            }
            try (var buffer = new Memory(bytes.getValue())) {
                result = pdh.PdhGetFormattedCounterArrayW(handle, format, bytes, count, buffer);
                if (result == Pdh.PDH_MORE_DATA) {
                    continue; // New instances may have appeared; query the size again.
                }
                int stride = new CounterItem().size();
                if (result != 0 || count.getValue() <= 0 || (long) count.getValue() * stride > buffer.size()) {
                    return List.of();
                }
                var readings = new ArrayList<CounterReading>(count.getValue());
                for (int index = 0; index < count.getValue(); index++) {
                    var item = new CounterItem(buffer.share((long) index * stride));
                    if (item.name != null) {
                        readings.add(new CounterReading(item.name.getWideString(0), item.value.status, item.value.value));
                    }
                }
                return readings;
            }
        }
        return List.of();
    }

    static Sample aggregate(Iterable<CounterReading> readings, Iterable<CounterReading> memoryReadings) {
        var engines = new HashMap<String, Map<String, Double>>();
        for (var reading : readings) {
            if (!valid(reading)) {
                continue;
            }
            var matcher = ENGINE.matcher(reading.instance().toLowerCase(Locale.ROOT));
            if (matcher.find()) {
                String adapter = matcher.group(1) + ":" + matcher.group(2);
                engines.computeIfAbsent(adapter, key -> new HashMap<>())
                        .merge(matcher.group(3), reading.value(), Double::sum);
            }
        }
        String busiest = null;
        double maximum = -1;
        for (var adapter : engines.entrySet()) {
            double load = adapter.getValue().values().stream().mapToDouble(Double::doubleValue).max().orElse(-1);
            if (load > maximum || (load == maximum && (busiest == null || adapter.getKey().compareTo(busiest) < 0))) {
                busiest = adapter.getKey();
                maximum = load;
            }
        }
        long memory = -1;
        for (var reading : memoryReadings) {
            if (!valid(reading) || reading.value() >= Long.MAX_VALUE) continue;
            var matcher = ADAPTER.matcher(reading.instance().toLowerCase(Locale.ROOT));
            if (matcher.matches() && (matcher.group(1) + ":" + matcher.group(2)).equals(busiest)) {
                memory = (long) reading.value();
            }
        }
        return new Sample(maximum < 0 ? -1 : Math.min(100, maximum) / 100, memory);
    }

    private static boolean valid(CounterReading reading) {
        return reading.instance() != null && (reading.status() == 0 || reading.status() == 1)
                && Double.isFinite(reading.value()) && reading.value() >= 0;
    }

    private static void requireSuccess(int status) {
        if (status != 0) {
            throw new IllegalStateException(tr("Contador de GPU indisponível (0x%08X)").formatted(status));
        }
    }

    @Override
    public void close() {
        if (query != null) {
            pdh.PdhCloseQuery(query);
            query = null;
        }
    }
}
