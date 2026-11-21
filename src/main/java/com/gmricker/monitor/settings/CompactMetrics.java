package com.gmricker.monitor.settings;

import static com.gmricker.monitor.i18n.I18n.tr;

import java.util.Locale;

/** The pair of readings shown in compact mode. */
public enum CompactMetrics {
    CPU_RAM("CPU", "RAM"), CPU_GPU("CPU", "GPU"), GPU_RAM("GPU", "RAM");

    private final String first, second;
    CompactMetrics(String first, String second) {
        this.first = first;
        this.second = second;
    }

    public String first() { return first; }
    public String second() { return second; }
    public String id() { return name().toLowerCase(Locale.ROOT); }
    @Override public String toString() { return first + " " + tr("e") + " " + second; }

    public static CompactMetrics fromId(String id) {
        if (id != null) {
            for (var metrics : values()) {
                if (metrics.id().equalsIgnoreCase(id.trim())) return metrics;
            }
        }
        return CPU_RAM;
    }
}
