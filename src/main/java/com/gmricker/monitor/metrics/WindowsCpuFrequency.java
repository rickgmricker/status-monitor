package com.gmricker.monitor.metrics;

import static com.gmricker.monitor.i18n.I18n.tr;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.Structure.FieldOrder;
import com.sun.jna.platform.win32.Pdh;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.platform.win32.WinNT.HANDLEByReference;
import com.sun.jna.platform.win32.BaseTSD.DWORD_PTR;
import com.sun.jna.platform.win32.PowrProf;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import java.util.function.DoubleSupplier;

/** Estimates current mean clock from interval performance, including turbo. */
final class WindowsCpuFrequency implements AutoCloseable {
    private static final int PROCESSOR_INFORMATION = 11;
    private static final int RECORD_BYTES = 6 * Integer.BYTES;

    public interface ProcessorApi extends StdCallLibrary {
        int GetActiveProcessorCount(short groupNumber);
    }

    public interface CounterApi extends Pdh {
        int PdhGetFormattedCounterValue(HANDLE counter, int format, Pointer type, CounterValue value);
    }

    // PDH_FMT_DOUBLE selects the double member of PDH_FMT_COUNTERVALUE's union.
    @FieldOrder({"status", "value"})
    public static class CounterValue extends Structure {
        public int status;
        public double value;
    }

    private final CounterApi pdh;
    private final DoubleSupplier referenceSource;
    private HANDLE query;
    private HANDLE counter;
    private boolean primed;
    private double referenceMhz = -1;

    WindowsCpuFrequency() {
        this(Native.load("pdh", CounterApi.class, W32APIOptions.DEFAULT_OPTIONS),
                WindowsCpuFrequency::readReferenceMhz);
    }

    WindowsCpuFrequency(CounterApi pdh, DoubleSupplier referenceSource) {
        this.pdh = pdh;
        this.referenceSource = referenceSource;
        var queryRef = new HANDLEByReference();
        requireSuccess(pdh.PdhOpenQuery(null, new DWORD_PTR(0), queryRef));
        query = queryRef.getValue();
        try {
            var counterRef = new HANDLEByReference();
            requireSuccess(pdh.PdhAddEnglishCounter(query,
                    "\\Processor Information(_Total)\\% Processor Performance", new DWORD_PTR(0), counterRef));
            counter = counterRef.getValue();
        } catch (RuntimeException | LinkageError failure) {
            close();
            throw failure;
        }
    }

    double sampleMhz() {
        if (query == null) {
            return -1;
        }
        if (pdh.PdhCollectQueryData(query) != 0) {
            primed = false;
            return -1;
        }
        if (!primed) {
            primed = true;
            return -1; // A rate counter needs two samples; never sleep on the UI thread.
        }
        var reading = new CounterValue();
        int result = pdh.PdhGetFormattedCounterValue(counter,
                Pdh.PDH_FMT_DOUBLE | Pdh.PDH_FMT_NOCAP100, null, reading);
        if (result != 0 || (reading.status != 0 && reading.status != 1)) {
            return -1;
        }
        if (referenceMhz <= 0) {
            referenceMhz = referenceSource.getAsDouble();
        }
        return estimateMhz(referenceMhz, reading.value);
    }

    static double estimateMhz(double referenceMhz, double performancePercent) {
        double mhz = referenceMhz * performancePercent / 100.0;
        return referenceMhz > 0 && performancePercent > 0 && Double.isFinite(mhz) ? mhz : -1;
    }

    private static double readReferenceMhz() {
        int processorCount = Native.load("kernel32", ProcessorApi.class)
                .GetActiveProcessorCount((short) 0xffff); // ALL_PROCESSOR_GROUPS
        if (processorCount <= 0) {
            return -1;
        }
        try (var buffer = new Memory(Math.multiplyExact((long) processorCount, RECORD_BYTES))) {
            int result = PowrProf.INSTANCE.CallNtPowerInformation(PROCESSOR_INFORMATION,
                    null, 0, buffer, Math.toIntExact(buffer.size()));
            return result == 0 ? averageReferenceMhz(buffer, processorCount) : -1;
        }
    }

    static double averageReferenceMhz(Memory buffer, int count) {
        long total = 0;
        int valid = 0;
        for (int index = 0; index < count; index++) {
            // Number, MaxMhz, CurrentMhz, MhzLimit, MaxIdleState, CurrentIdleState.
            long mhz = Integer.toUnsignedLong(buffer.getInt((long) index * RECORD_BYTES + 4));
            if (mhz > 0) {
                total += mhz;
                valid++;
            }
        }
        return valid > 0 ? (double) total / valid : -1;
    }

    private static void requireSuccess(int status) {
        if (status != 0) {
            throw new IllegalStateException(tr("Contador de clock indisponível (0x%08X)").formatted(status));
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
