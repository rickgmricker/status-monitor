package com.gmricker.monitor.metrics;

import static com.gmricker.monitor.i18n.I18n.tr;

import com.sun.jna.platform.win32.COM.COMUtils;
import com.sun.jna.platform.win32.COM.WbemcliUtil.WmiQuery;
import com.sun.jna.platform.win32.Ole32;
import java.lang.management.ManagementFactory;
import java.util.concurrent.TimeoutException;

/** On-demand inventory; WMI runs on its own COM-initialized background thread. */
public final class PcSpecs {
    private enum Cpu { NAME, NUMBEROFCORES, NUMBEROFLOGICALPROCESSORS }
    private enum Memory { DEVICELOCATOR, CAPACITY, CONFIGUREDCLOCKSPEED }
    private enum Gpu { NAME, DRIVERVERSION }
    private enum Board { MANUFACTURER, PRODUCT }
    private enum SystemInfo { CAPTION, VERSION, BUILDNUMBER, OSARCHITECTURE }
    private enum Disk { MODEL, SIZE, MEDIATYPE }

    private PcSpecs() {}

    public static String read() {
        var report = new StringBuilder();
        var os = ManagementFactory.getOperatingSystemMXBean();
        report.append(tr("Sistema: ")).append(os.getName()).append(" · ").append(os.getArch()).append('\n');
        report.append(tr("Processadores lógicos disponíveis: ")).append(os.getAvailableProcessors()).append('\n');
        if (os instanceof com.sun.management.OperatingSystemMXBean physical) {
            report.append(tr("RAM total utilizável: %.2f GiB%n").formatted(physical.getTotalMemorySize() / 1073741824.0));
        }
        if (!System.getProperty("os.name", "").startsWith("Windows")) {
            return report.toString();
        }
        boolean initialized = false;
        try {
            COMUtils.checkRC(Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_MULTITHREADED));
            initialized = true;
            append(report, tr("Processador"), "Win32_Processor", Cpu.class);
            append(report, "GPU", "Win32_VideoController", Gpu.class);
            append(report, tr("RAM — módulos instalados"), "Win32_PhysicalMemory", Memory.class);
            append(report, tr("Placa-mãe"), "Win32_BaseBoard", Board.class);
            append(report, "Windows", "Win32_OperatingSystem", SystemInfo.class);
            append(report, tr("Discos físicos"), "Win32_DiskDrive", Disk.class);
        } catch (RuntimeException | LinkageError failure) {
            report.append(tr("\nDetalhes do hardware indisponíveis neste ambiente.\n"));
        } finally {
            if (initialized) {
                Ole32.INSTANCE.CoUninitialize();
            }
        }
        return report.toString();
    }

    private static <T extends Enum<T>> void append(StringBuilder report, String title, String wmiClass, Class<T> properties) {
        if (Thread.currentThread().isInterrupted()) {
            return;
        }
        report.append('\n').append(title).append('\n');
        try {
            var result = new WmiQuery<>(wmiClass, properties).execute(2000);
            if (result.getResultCount() == 0) {
                report.append(tr("  Não informado\n"));
            }
            for (int row = 0; row < result.getResultCount(); row++) {
                if (row > 0) {
                    report.append('\n');
                }
                for (var property : properties.getEnumConstants()) {
                    Object value = result.getValue(property, row);
                    String text = value == null || value.toString().isBlank() ? tr("Não informado") : value.toString().trim();
                    if ((property.name().equals("SIZE") || property.name().equals("CAPACITY")) && value != null) {
                        try {
                            text = "%.1f GiB".formatted(Double.parseDouble(value.toString()) / 1073741824.0);
                        } catch (NumberFormatException ignored) { }
                    }
                    if (property.name().equals("CONFIGUREDCLOCKSPEED")) {
                        text = value instanceof Number speed && speed.longValue() > 0
                                ? speed.longValue() + " MHz" : tr("Não informado");
                    }
                    report.append("  ").append(label(property.name())).append(": ").append(text).append('\n');
                }
            }
        } catch (RuntimeException | TimeoutException failure) {
            report.append(tr("  Indisponível\n"));
        }
    }

    private static String label(String property) {
        return switch (property) {
            case "NAME", "MODEL" -> tr("Modelo");
            case "NUMBEROFCORES" -> tr("Núcleos");
            case "NUMBEROFLOGICALPROCESSORS" -> "Threads";
            case "DEVICELOCATOR" -> "Slot";
            case "CONFIGUREDCLOCKSPEED" -> tr("Frequência configurada");
            case "DRIVERVERSION" -> "Driver";
            case "MANUFACTURER" -> tr("Fabricante");
            case "PRODUCT" -> tr("Produto");
            case "CAPTION" -> tr("Edição");
            case "VERSION" -> tr("Versão");
            case "BUILDNUMBER" -> "Build";
            case "OSARCHITECTURE" -> tr("Arquitetura");
            case "SIZE", "CAPACITY" -> tr("Capacidade");
            case "MEDIATYPE" -> tr("Tipo informado");
            default -> property;
        };
    }
}
