package com.gmricker.monitor;

import static com.gmricker.monitor.i18n.I18n.tr;
import com.gmricker.monitor.i18n.I18n;

import com.gmricker.monitor.metrics.SystemMetrics;
import com.gmricker.monitor.ui.MonitorWindow;
import com.gmricker.monitor.instance.SingleInstance;
import com.gmricker.monitor.settings.SettingsStore;
import com.gmricker.monitor.theme.Theme;
import com.gmricker.monitor.theme.ThemeManager;
import java.io.IOException;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

public final class MonitorApplication {
    private MonitorApplication() {}

    public static void main(String[] args) {
        // A console mode allows checking collection without opening a window.
        if (args.length == 1 && "--snapshot".equals(args[0])) {
            try (var metrics = new SystemMetrics()) {
                System.out.println(metrics.sample());
            }
            return;
        }
        final SingleInstance instance;
        try { I18n.setLanguage(SettingsStore.forCurrentUser().load().language()); }
        catch (IOException ignored) { }
        try {
            instance = SingleInstance.start(SettingsStore.forCurrentUser().directory());
        } catch (IOException failure) {
            JOptionPane.showMessageDialog(null,
                    tr("Não foi possível verificar se o Monitor já está aberto.\n") + failure.getMessage(),
                    "Status Monitor", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (instance == null) {
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(instance::close, "monitor-instance-cleanup"));
        SwingUtilities.invokeLater(() -> {
            SystemMetrics metrics = null;
            try {
                metrics = new SystemMetrics();
                try {
                    ThemeManager.apply(SettingsStore.forCurrentUser().load().theme());
                } catch (IOException | IllegalStateException failure) {
                    ThemeManager.apply(Theme.SIMPLE);
                }
                boolean startup = java.util.Arrays.asList(args).contains("--startup");
                var window = new MonitorWindow(metrics, startup);
                window.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosed(WindowEvent event) {
                        instance.close();
                    }
                });
                window.setVisible(true);
                instance.onActivate(() -> SwingUtilities.invokeLater(window::activateExistingWindow));
            } catch (RuntimeException | Error failure) {
                instance.close();
                if (metrics != null) {
                    metrics.close();
                }
                throw failure;
            }
        });
    }
}
