package com.gmricker.monitor.ui;

import static com.gmricker.monitor.i18n.I18n.tr;
import com.gmricker.monitor.i18n.I18n;
import com.gmricker.monitor.i18n.Language;
import com.gmricker.monitor.i18n.LanguageAware;

import com.gmricker.monitor.metrics.SystemMetrics;
import com.gmricker.monitor.metrics.SystemSnapshot;
import com.gmricker.monitor.window.WindowPinController;
import com.gmricker.monitor.window.WindowTaskbar;
import com.gmricker.monitor.settings.MonitorSettings;
import com.gmricker.monitor.settings.SettingsStore;
import com.gmricker.monitor.startup.WindowsStartup;
import com.gmricker.monitor.theme.Theme;
import com.gmricker.monitor.theme.ThemeAware;
import com.gmricker.monitor.theme.ThemeColors;
import com.gmricker.monitor.theme.ThemeManager;
import java.awt.AWTException;
import java.awt.Frame;
import java.io.IOException;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Point;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.JToggleButton;
import javax.swing.JCheckBox;
import javax.swing.JOptionPane;
import javax.swing.JTabbedPane;
import javax.swing.JComboBox;

public final class MonitorWindow extends JFrame implements ThemeAware, LanguageAware {
    private final JLabel status = new JLabel(tr("Atualização a cada 1 segundo"));
    private final JLabel pinStatus = new JLabel(" ");
    private final StatusPanel statusPanel = new StatusPanel(status, pinStatus);
    private final JLabel compactUsage = new JLabel("CPU: —   |   RAM: —");
    private final JToggleButton pin = new JToggleButton(new WindowControlIcon(WindowControlIcon.Kind.PIN));
    private final JCheckBox compact = new JCheckBox(tr("Modo compacto"));
    private final JCheckBox reducedCompact = new JCheckBox(tr("Modo compacto reduzido"));
    private final JCheckBox closeToTray = new JCheckBox(tr("Ao clicar no X, manter na bandeja"));
    private final JCheckBox startWithWindows = new JCheckBox(tr("Iniciar com o Windows em modo compacto"));
    private final JComboBox<Theme> themes = new JComboBox<>(Theme.values());
    private final JComboBox<Language> languages = new JComboBox<>(Language.values());
    private final SettingsStore settingsStore;
    private final WindowPinController pinController;
    private final MonitorTray tray;
    private final WindowTaskbar taskbar = new WindowTaskbar(this);
    private SettingsDialog settingsDialog;
    private boolean loadingSettings = true;
    private Dimension normalSize;
    private Point compactLocation;
    private final javax.swing.Timer positionSaveTimer = new javax.swing.Timer(400, event -> saveSettings());
    private WindowTitleBar titleBar;
    private final SpecsPanel specs = new SpecsPanel();
    private final StressPanel stress = new StressPanel();
    private final IngamePanel ingame;
    private SystemSnapshot lastSnapshot;

    public MonitorWindow(SystemMetrics metrics) {
        this(metrics, false);
    }

    public MonitorWindow(SystemMetrics metrics, boolean startCompact) {
        this(metrics, SettingsStore.forCurrentUser(), startCompact);
    }

    MonitorWindow(SystemMetrics metrics, SettingsStore settingsStore) {
        this(metrics, settingsStore, false);
    }

    MonitorWindow(SystemMetrics metrics, SettingsStore settingsStore, boolean startCompact) {
        super(titleFor(settingsStore));
        setIconImages(ApplicationIcon.windowImages());
        this.settingsStore = settingsStore;
        ingame = new IngamePanel(settingsStore.directory());
        var saved = loadSettings();
        languages.setSelectedItem(saved.language());
        languages.getAccessibleContext().setAccessibleName(tr("Idioma"));
        languages.addActionListener(event -> {
            if (loadingSettings) return;
            I18n.apply((Language) languages.getSelectedItem());
            saveSettings();
        });
        reducedCompact.setSelected(saved.reducedCompact());
        reducedCompact.setToolTipText(tr("Reduz fonte, ícones e tamanho da janela no modo compacto."));
        if (saved.compactX() != null && saved.compactY() != null) {
            compactLocation = new Point(saved.compactX(), saved.compactY());
        }
        positionSaveTimer.setRepeats(false);
        themes.getAccessibleContext().setAccessibleName(tr("Tema"));
        themes.setSelectedItem(saved.theme());
        themes.addActionListener(event -> {
            var selected = (Theme) themes.getSelectedItem();
            var previous = ThemeManager.current();
            try {
                if (selected != previous) ThemeManager.apply(selected);
                saveSettings();
            } catch (RuntimeException failure) {
                themes.setSelectedItem(previous);
                JOptionPane.showMessageDialog(this, failure.getMessage(), tr("Tema"), JOptionPane.ERROR_MESSAGE);
            }
        });
        setUndecorated(true);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(330, 320);
        setMinimumSize(new Dimension(330, 260));
        setLocationRelativeTo(null);
        setAutoRequestFocus(false);

        pinController = new WindowPinController(this, message -> {
            pin.setSelected(false);
            pinStatus.setText(message);
            pinStatus.setToolTipText(message);
            pin.setToolTipText(message);
        });
        pin.setEnabled(pinController.isSupported());
        pin.setSelected(saved.pinned() && pin.isEnabled());
        pin.setToolTipText(tr("Sobrepor outros programas"));
        pin.addActionListener(event -> {
            pinStatus.setText(" ");
            pinStatus.setToolTipText(null);
            pinController.setPinned(pin.isSelected());
            saveSettings();
        });
        tray = new MonitorTray(this::restoreFromTray, () -> {
            restoreFromTray();
            openSettings();
        }, this::dispose);
        closeToTray.setEnabled(MonitorTray.isSupported());
        closeToTray.setSelected(saved.closeToTray() && closeToTray.isEnabled());
        closeToTray.setToolTipText(tr("Continue executando em segundo plano; use Sair na bandeja para encerrar."));
        closeToTray.addActionListener(event -> saveSettings());
        var startup = new WindowsStartup(settingsStore.directory());
        startWithWindows.setEnabled(WindowsStartup.isSupported());
        if (startWithWindows.isEnabled()) {
            try {
                startWithWindows.setSelected(startup.isEnabled());
            } catch (RuntimeException | LinkageError failure) {
                startWithWindows.setEnabled(false);
                startWithWindows.setToolTipText(tr("Não foi possível consultar a inicialização do Windows."));
            }
        }
        startWithWindows.addActionListener(event -> {
            boolean enabled = startWithWindows.isSelected();
            try {
                startup.setEnabled(enabled);
            } catch (IOException | RuntimeException | LinkageError failure) {
                startWithWindows.setSelected(!enabled);
                JOptionPane.showMessageDialog(this, tr("Não foi possível alterar a inicialização.\n")
                        + failure.getMessage(), tr("Inicialização do Windows"), JOptionPane.ERROR_MESSAGE);
            }
        });

        var tabs = new JTabbedPane();
        tabs.getAccessibleContext().setAccessibleName(tr("Abas do Monitor"));
        tabs.addTab("Status", statusPanel);
        tabs.addTab("Specs", specs);
        tabs.addTab("Stress", stress);
        tabs.addTab("Ingame", ingame);
        tabs.addTab(tr("Sobre"), new AboutPanel());
        tabs.addChangeListener(event -> {
            if (tabs.getSelectedIndex() == 0 && !compact.isSelected() && isShowing()) {
                specs.ensureLoaded();
            }
            if (tabs.getSelectedIndex() == 3 && !compact.isSelected() && isShowing()) {
                ingame.ensureLoaded();
            }
        });
        var normalContent = new JPanel(new BorderLayout());
        normalContent.add(tabs, BorderLayout.CENTER);
        var resizeRow = new JPanel(new BorderLayout());
        resizeRow.add(new WindowResizeGrip(this), BorderLayout.EAST);
        normalContent.add(resizeRow, BorderLayout.SOUTH);
        var root = new JPanel(new BorderLayout());
        root.setBorder(BorderFactory.createLineBorder(ThemeColors.frameBorder()));
        titleBar = new WindowTitleBar(this, pin, this::openSettings, () -> compact.doClick(0), compactUsage);
        stress.addPropertyChangeListener("running", event -> {
            boolean enabled = !Boolean.TRUE.equals(event.getNewValue());
            compact.setEnabled(enabled);
            titleBar.setCompactingEnabled(enabled);
        });
        titleBar.setReducedCompact(reducedCompact.isSelected());
        reducedCompact.addActionListener(event -> {
            titleBar.setReducedCompact(reducedCompact.isSelected());
            if (compact.isSelected()) {
                var workArea = currentWorkArea();
                fitCompactSize();
                fitInWorkArea(workArea);
            }
            saveSettings();
        });
        root.add(titleBar, BorderLayout.NORTH);
        root.add(normalContent, BorderLayout.CENTER);
        setContentPane(root);
        compact.addActionListener(event -> {
            var workArea = currentWorkArea();
            boolean enabled = compact.isSelected();
            titleBar.setCompact(enabled);
            normalContent.setVisible(!enabled);
            if (enabled) {
                stress.stop();
                normalSize = getSize();
                fitCompactSize();
                if (compactLocation != null) {
                    setLocation(compactLocation);
                    workArea = currentWorkArea();
                }
            } else if (normalSize != null) {
                compactLocation = getLocation();
                setMinimumSize(new Dimension(330, 260));
                setSize(normalSize);
                if (tabs.getSelectedIndex() == 0 && isShowing()) {
                    specs.ensureLoaded();
                }
            }
            fitInWorkArea(workArea);
            revalidate();
            repaint();
            updateTaskbarAndTray();
            saveSettings();
        });
        if (saved.compact() || startCompact) {
            compact.doClick(0);
        }
        loadingSettings = false;

        var sampler = Executors.newSingleThreadScheduledExecutor(task -> {
            var thread = new Thread(task, "monitor-metrics");
            thread.setDaemon(true);
            return thread;
        });
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent event) {
                if (pin.isSelected()) {
                    pinController.setPinned(true);
                }
                if (!compact.isSelected() && tabs.getSelectedIndex() == 0) {
                    specs.ensureLoaded();
                }
            }

            @Override
            public void windowClosing(WindowEvent event) {
                stress.stop();
                if (!closeToTray.isSelected()) {
                    dispose();
                    return;
                }
                try {
                    // Register the icon before hiding, so the app always remains reachable.
                    tray.show();
                    if (settingsDialog != null) {
                        settingsDialog.setVisible(false);
                    }
                    pinController.suspend();
                    setVisible(false);
                } catch (AWTException | UnsupportedOperationException failure) {
                    JOptionPane.showMessageDialog(MonitorWindow.this,
                            tr("Não foi possível enviar para a bandeja. A janela permanecerá aberta.\n")
                                    + failure.getMessage(), tr("Bandeja indisponível"), JOptionPane.WARNING_MESSAGE);
                }
            }

            @Override
            public void windowClosed(WindowEvent event) {
                stress.close();
                specs.close();
                sampler.execute(metrics::close);
                sampler.shutdown();
                pinController.close();
                tray.close();
                if (settingsDialog != null) {
                    settingsDialog.dispose();
                }
            }
        });
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent event) {
                if (!loadingSettings && compact.isSelected()) {
                    compactLocation = getLocation();
                    positionSaveTimer.restart();
                }
            }

            @Override
            public void componentHidden(ComponentEvent event) {
                stress.stop();
            }
        });
        sampler.schedule(new Runnable() {
            private long lastStatusUpdate;
            private boolean statusRendered;

            @Override public void run() {
                if (sampler.isShutdown()) return;
                try {
                    var snapshot = metrics.sample();
                    long now = System.nanoTime();
                    boolean updateStatus = !statusRendered || now - lastStatusUpdate >= TimeUnit.SECONDS.toNanos(1);
                    if (updateStatus) { statusRendered = true; lastStatusUpdate = now; }
                    SwingUtilities.invokeLater(() -> {
                        ingame.updateReadings(snapshot);
                        if (updateStatus) render(snapshot);
                    });
                } catch (RuntimeException failure) {
                    SwingUtilities.invokeLater(() -> {
                        status.setText(tr("Falha na coleta: ") + failure.getMessage());
                        compactUsage.setText("CPU: —   |   RAM: —");
                        compactUsage.setToolTipText(status.getText());
                    });
                } finally {
                    int delay = ingame.isCollecting() ? IngamePanel.UPDATE_INTERVAL_MS : 1000;
                    try { sampler.schedule(this, delay, TimeUnit.MILLISECONDS); }
                    catch (java.util.concurrent.RejectedExecutionException closing) {
                        if (!sampler.isShutdown()) throw closing;
                    }
                }
            }
        }, 0, TimeUnit.MILLISECONDS);
        applyTheme();
    }

    private void fitCompactSize() {
        int width = Math.max(reducedCompact.isSelected() ? 180 : 220, titleBar.compactWidth());
        var size = new Dimension(width, titleBar.getPreferredSize().height + 2);
        setMinimumSize(size);
        setSize(size);
    }

    private Rectangle currentWorkArea() {
        var window = getBounds();
        var selected = getGraphicsConfiguration();
        long largestOverlap = -1;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (var screen : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            var configuration = screen.getDefaultConfiguration();
            var bounds = configuration.getBounds();
            var overlap = bounds.intersection(window);
            long area = overlap.isEmpty() ? 0 : (long) overlap.width * overlap.height;
            double dx = Math.max(bounds.x - window.getCenterX(),
                    Math.max(0, window.getCenterX() - bounds.getMaxX()));
            double dy = Math.max(bounds.y - window.getCenterY(),
                    Math.max(0, window.getCenterY() - bounds.getMaxY()));
            double distance = dx * dx + dy * dy;
            if (area > largestOverlap || (area == largestOverlap && distance < nearestDistance)) {
                selected = configuration;
                largestOverlap = area;
                nearestDistance = distance;
            }
        }
        var bounds = new Rectangle(selected.getBounds());
        var insets = Toolkit.getDefaultToolkit().getScreenInsets(selected);
        bounds.x += insets.left;
        bounds.y += insets.top;
        bounds.width -= insets.left + insets.right;
        bounds.height -= insets.top + insets.bottom;
        return bounds;
    }

    private void fitInWorkArea(Rectangle workArea) {
        var fitted = WindowPlacement.fit(getBounds(), workArea);
        var minimum = getMinimumSize();
        if (minimum.width > fitted.width || minimum.height > fitted.height) {
            setMinimumSize(new Dimension(Math.min(minimum.width, fitted.width),
                    Math.min(minimum.height, fitted.height)));
        }
        setBounds(fitted);
    }

    @Override
    public void applyTheme() {
        getContentPane().setBackground(ThemeColors.background());
        ((JPanel) getContentPane()).setBorder(BorderFactory.createLineBorder(ThemeColors.frameBorder()));
        status.setForeground(ThemeColors.secondary());
        pinStatus.setForeground(ThemeColors.secondary());
        if (titleBar != null) {
            titleBar.setReducedCompact(reducedCompact.isSelected());
            if (compact.isSelected()) {
                fitCompactSize();
                fitInWorkArea(currentWorkArea());
            }
        }
        revalidate();
        repaint();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        // Apply the compact style before Windows shows the native window.
        if (tray != null) {
            updateTaskbarAndTray();
        }
    }

    private void updateTaskbarAndTray() {
        if (!isDisplayable()) {
            return;
        }
        try {
            if (compact.isSelected()) {
                tray.show(); // Keep a working tray entry before removing the taskbar button.
                taskbar.setHidden(true);
            } else {
                taskbar.setHidden(false);
                tray.removeIcon();
            }
        } catch (AWTException | RuntimeException | LinkageError failure) {
            // Retain a taskbar entry if the tray is unavailable or native styling fails.
            try {
                taskbar.setHidden(false);
            } catch (RuntimeException | LinkageError ignored) {
                // The compact window itself stays visible and usable.
            }
            pinStatus.setText(tr("Não foi possível representar o modo compacto só na bandeja."));
            pinStatus.setToolTipText(failure.getMessage());
        }
    }

    private MonitorSettings loadSettings() {
        try {
            return settingsStore.load();
        } catch (IOException failure) {
            pinStatus.setText(tr("Não foi possível carregar as configurações."));
            pinStatus.setToolTipText(failure.getMessage());
            return MonitorSettings.DEFAULT;
        }
    }

    private void saveSettings() {
        if (loadingSettings) {
            return;
        }
        if (compact.isSelected()) compactLocation = getLocation();
        try {
            settingsStore.save(new MonitorSettings(compact.isSelected(), pin.isSelected(), closeToTray.isSelected(),
                    (Theme) themes.getSelectedItem(),
                    compactLocation == null ? null : compactLocation.x,
                    compactLocation == null ? null : compactLocation.y, reducedCompact.isSelected(),
                    (Language) languages.getSelectedItem()));
        } catch (IOException failure) {
            JOptionPane.showMessageDialog(this,
                    tr("A alteração foi aplicada, mas não pôde ser salva.\n") + failure.getMessage(),
                    tr("Configurações"), JOptionPane.ERROR_MESSAGE);
        }
    }

    @Override
    public void dispose() {
        positionSaveTimer.stop();
        ingame.close();
        saveSettings();
        super.dispose();
    }

    private void openSettings() {
        if (settingsDialog == null) {
            settingsDialog = new SettingsDialog(this, reducedCompact, pin, closeToTray, startWithWindows, themes, languages);
        }
        settingsDialog.open();
    }

    private void restoreFromTray() {
        if (!isDisplayable()) {
            return;
        }
        setExtendedState(getExtendedState() & ~Frame.ICONIFIED);
        setAutoRequestFocus(true);
        setVisible(true);
        toFront();
        requestFocus();
        setAutoRequestFocus(false);
        updateTaskbarAndTray();
        if (pin.isSelected()) {
            pinController.setPinned(true);
        }
    }

    public void activateExistingWindow() {
        restoreFromTray();
    }

    private static String titleFor(SettingsStore store) {
        try { I18n.setLanguage(store.load().language()); }
        catch (IOException invalid) { I18n.setLanguage(Language.PORTUGUESE); }
        return "Status Monitor";
    }

    private void render(SystemSnapshot snapshot) {
        lastSnapshot = snapshot;
        stress.updateReadings(snapshot);
        statusPanel.updateReadings(snapshot);
        String cpuPercent = Double.isFinite(snapshot.cpuLoad()) && snapshot.cpuLoad() >= 0
                ? "%.0f%%".formatted(snapshot.cpuLoad() * 100) : "—";
        String ramPercent = snapshot.totalMemory() > 0 && snapshot.freeMemory() >= 0
                ? "%.0f%%".formatted((snapshot.totalMemory() - snapshot.freeMemory()) * 100.0 / snapshot.totalMemory()) : "—";
        compactUsage.setText("CPU: " + cpuPercent + "   |   RAM: " + ramPercent);
        String frequency = Double.isFinite(snapshot.cpuFrequencyMhz()) && snapshot.cpuFrequencyMhz() > 0
                ? " · %.2f GHz".formatted(snapshot.cpuFrequencyMhz() / 1000) : "";
        compactUsage.setToolTipText("CPU: " + cpuPercent + frequency + " · RAM: " + ramPercent);
        status.setText(tr("Atualização a cada 1 segundo"));
    }

    @Override public void applyLanguage() {
        tray.applyLanguage();
        if (lastSnapshot != null) render(lastSnapshot);
        languages.repaint();
        themes.repaint();
    }
}
