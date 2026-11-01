package com.gmricker.monitor.ui;

import static com.gmricker.monitor.i18n.I18n.tr;

import java.awt.AWTException;
import java.awt.EventQueue;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.beans.PropertyChangeListener;
import java.util.Arrays;

final class MonitorTray implements AutoCloseable {
    private final Runnable restore;
    private final Runnable settings;
    private final Runnable exit;
    private SystemTray systemTray;
    private TrayIcon icon;
    private final PropertyChangeListener trayChanged;

    MonitorTray(Runnable restore, Runnable settings, Runnable exit) {
        this.restore = restore;
        this.settings = settings;
        this.exit = exit;
        trayChanged = event -> EventQueue.invokeLater(() -> {
            if (icon != null && !Arrays.asList(systemTray.getTrayIcons()).contains(icon)) {
                removeIcon();
                restore.run(); // Do not strand the app if Explorer removes the tray icon.
            }
        });
    }

    static boolean isSupported() {
        return SystemTray.isSupported();
    }

    void show() throws AWTException {
        if (!isSupported()) {
            throw new AWTException(tr("A bandeja do sistema não está disponível."));
        }
        if (icon != null) {
            return;
        }
        if (systemTray == null) {
            systemTray = SystemTray.getSystemTray();
            systemTray.addPropertyChangeListener("trayIcons", trayChanged);
        }
        var menu = new PopupMenu();
        menu.add(item(tr("Abrir Monitor"), restore));
        menu.add(item(tr("Configurações"), settings));
        menu.addSeparator();
        menu.add(item(tr("Sair"), exit));
        var candidate = new TrayIcon(ApplicationIcon.trayImage(), "Status Monitor", menu);
        candidate.setImageAutoSize(true);
        candidate.addActionListener(event -> EventQueue.invokeLater(restore));
        systemTray.add(candidate);
        icon = candidate;
    }

    private static MenuItem item(String title, Runnable action) {
        var item = new MenuItem(title);
        item.addActionListener(event -> EventQueue.invokeLater(action));
        return item;
    }

    void applyLanguage() {
        if (icon == null) return;
        var menu = icon.getPopupMenu();
        menu.getItem(0).setLabel(tr("Abrir Monitor"));
        menu.getItem(1).setLabel(tr("Configurações"));
        menu.getItem(3).setLabel(tr("Sair"));
    }

    void removeIcon() {
        if (icon != null) {
            var previous = icon;
            icon = null;
            systemTray.remove(previous);
        }
    }

    @Override
    public void close() {
        removeIcon();
        if (systemTray != null) {
            systemTray.removePropertyChangeListener("trayIcons", trayChanged);
        }
    }
}
