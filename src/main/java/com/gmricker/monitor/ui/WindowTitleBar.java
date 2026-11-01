package com.gmricker.monitor.ui;

import static com.gmricker.monitor.i18n.I18n.tr;
import com.gmricker.monitor.i18n.I18n;
import com.gmricker.monitor.i18n.LanguageAware;

import java.awt.BorderLayout;
import com.gmricker.monitor.theme.ThemeAware;
import com.gmricker.monitor.theme.ThemeColors;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.CardLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

final class WindowTitleBar extends JPanel implements ThemeAware, LanguageAware {
    private final CardLayout titleLayout = new CardLayout();
    private final JPanel titles = new JPanel(titleLayout);
    private final JToggleButton pin;
    private final JButton close;
    private final JButton settings;
    private final JButton mode;
    private final JPopupMenu compactMenu = new JPopupMenu();
    private boolean compact;
    private boolean reducedCompact;
    private final JLabel compactUsage;

    WindowTitleBar(JFrame window, JToggleButton pin, Runnable openSettings, Runnable toggleMode, JLabel compactUsage) {
        super(new BorderLayout());
        this.pin = pin;
        this.compactUsage = compactUsage;
        setBackground(ThemeColors.header());
        setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ThemeColors.headerBorder()));

        var title = new JLabel(window.getTitle());
        title.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
        compactUsage.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        titles.setOpaque(false);
        titles.add(title, "normal");
        titles.add(compactUsage, "compact");
        add(titles, BorderLayout.CENTER);

        mode = new JButton(new WindowControlIcon(WindowControlIcon.Kind.COMPACT));
        mode.addActionListener(event -> {
            compactMenu.setVisible(false);
            toggleMode.run();
        });
        settings = new JButton(new WindowControlIcon(WindowControlIcon.Kind.SETTINGS));
        settings.addActionListener(event -> {
            if (compact) {
                compactMenu.show(settings, settings.getWidth() - compactMenu.getPreferredSize().width,
                        settings.getHeight());
            } else {
                openSettings.run();
            }
        });
        close = new JButton(new WindowControlIcon(WindowControlIcon.Kind.CLOSE));
        close.addActionListener(event -> window.dispatchEvent(
                new WindowEvent(window, WindowEvent.WINDOW_CLOSING)));
        var togglePin = new JMenuItem(tr("Fixar"), new WindowControlIcon(WindowControlIcon.Kind.PIN));
        togglePin.addActionListener(event -> {
            compactMenu.setVisible(false);
            pin.doClick(0);
        });
        var configItem = new JMenuItem(tr("Configurações"), new WindowControlIcon(WindowControlIcon.Kind.SETTINGS));
        configItem.addActionListener(event -> {
            compactMenu.setVisible(false);
            openSettings.run();
        });
        var closeItem = new JMenuItem(tr("Fechar"), new WindowControlIcon(WindowControlIcon.Kind.CLOSE));
        closeItem.addActionListener(event -> {
            compactMenu.setVisible(false);
            close.doClick(0);
        });
        compactMenu.add(togglePin);
        compactMenu.add(configItem);
        compactMenu.add(closeItem);
        compactMenu.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent event) {
                togglePin.setText(pin.isSelected() ? tr("Desfixar") : tr("Fixar"));
                togglePin.setEnabled(pin.isEnabled());
                updateMenuSizing();
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent event) { }

            @Override
            public void popupMenuCanceled(PopupMenuEvent event) { }
        });
        configure(pin, tr("Fixar no topo"), false);
        configure(mode, tr("Compactar"), false);
        configure(settings, tr("Configurações"), false);
        configure(close, tr("Fechar"), true);
        var controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        controls.setOpaque(false);
        controls.add(pin);
        controls.add(mode);
        controls.add(settings);
        controls.add(close);
        add(controls, BorderLayout.EAST);

        var drag = new MouseAdapter() {
            private Point pressPoint;
            private Point windowLocation;

            @Override
            public void mousePressed(MouseEvent event) {
                if (event.getButton() == MouseEvent.BUTTON1) {
                    pressPoint = event.getLocationOnScreen();
                    windowLocation = window.getLocation();
                }
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                if (pressPoint != null) {
                    var point = event.getLocationOnScreen();
                    window.setLocation(windowLocation.x + point.x - pressPoint.x,
                            windowLocation.y + point.y - pressPoint.y);
                }
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                pressPoint = null;
            }
        };
        addMouseListener(drag);
        addMouseMotionListener(drag);
        title.addMouseListener(drag);
        title.addMouseMotionListener(drag);
        compactUsage.addMouseListener(drag);
        compactUsage.addMouseMotionListener(drag);
        applyTheme();
    }

    void setCompactingEnabled(boolean enabled) {
        mode.setEnabled(enabled);
    }

    @Override public void applyLanguage() {
        I18n.refresh(compactMenu);
        setCompact(compact);
    }

    void setCompact(boolean compact) {
        this.compact = compact;
        compactMenu.setVisible(false);
        mode.setIcon(new WindowControlIcon(compact ? WindowControlIcon.Kind.EXPAND : WindowControlIcon.Kind.COMPACT));
        String modeName = compact ? tr("Modo normal") : tr("Compactar");
        mode.setToolTipText(modeName);
        mode.getAccessibleContext().setAccessibleName(modeName);
        pin.setVisible(!compact);
        close.setVisible(!compact);
        settings.setComponentPopupMenu(compact ? compactMenu : null);
        titleLayout.show(titles, compact ? "compact" : "normal");
        setBorder(compact ? BorderFactory.createEmptyBorder()
                : BorderFactory.createMatteBorder(0, 0, 1, 0, ThemeColors.headerBorder()));
        updateSizing();
    }

    void setReducedCompact(boolean reduced) {
        reducedCompact = reduced;
        updateSizing();
    }

    private void updateSizing() {
        boolean reduced = compact && reducedCompact;
        var font = javax.swing.UIManager.getFont("Label.font");
        compactUsage.setFont(font.deriveFont(reduced ? Math.max(9f, font.getSize2D() - 2f) : font.getSize2D()));
        int padding = reduced ? 5 : 8;
        compactUsage.setBorder(BorderFactory.createEmptyBorder(0, padding, 0, padding));
        for (var button : new AbstractButton[] {pin, mode, settings, close}) {
            button.setPreferredSize(new Dimension(reduced ? 24 : 32, reduced ? 22 : 28));
            button.setBorder(BorderFactory.createEmptyBorder(reduced ? 2 : 4, reduced ? 4 : 8,
                    reduced ? 2 : 4, reduced ? 4 : 8));
        }
        int iconSize = reduced ? 14 : 18;
        mode.setIcon(new WindowControlIcon(compact ? WindowControlIcon.Kind.EXPAND : WindowControlIcon.Kind.COMPACT, iconSize));
        mode.setDisabledIcon(mode.getIcon());
        settings.setIcon(new WindowControlIcon(WindowControlIcon.Kind.SETTINGS, iconSize));
        updateMenuSizing();
        revalidate();
        repaint();
    }

    private void updateMenuSizing() {
        boolean reduced = compact && reducedCompact;
        var baseFont = javax.swing.UIManager.getFont("MenuItem.font");
        var font = baseFont.deriveFont(reduced ? Math.max(9f, baseFont.getSize2D() - 2f) : baseFont.getSize2D());
        var kinds = new WindowControlIcon.Kind[] {WindowControlIcon.Kind.PIN,
                WindowControlIcon.Kind.SETTINGS, WindowControlIcon.Kind.CLOSE};
        for (int index = 0; index < compactMenu.getComponentCount(); index++) {
            var item = (JMenuItem) compactMenu.getComponent(index);
            item.setFont(font);
            item.setIcon(new WindowControlIcon(kinds[index], reduced ? 14 : 18));
            item.setIconTextGap(reduced ? 4 : javax.swing.UIManager.getInt("MenuItem.textIconGap"));
            item.setBorder(reduced ? BorderFactory.createEmptyBorder(2, 6, 2, 6)
                    : javax.swing.UIManager.getBorder("MenuItem.border"));
            var metrics = item.getFontMetrics(font);
            // Reserve space for the look and feel's checkmark and arrow columns.
            item.setPreferredSize(reduced ? new Dimension(metrics.stringWidth(item.getText()) + 14 + 4 + 12 + 20,
                    Math.max(metrics.getHeight(), 14) + 4) : null);
        }
        compactMenu.revalidate();
        compactMenu.repaint();
    }

    int compactWidth() {
        return compactUsage.getFontMetrics(compactUsage.getFont()).stringWidth("CPU: 100%   |   RAM: 100%")
                + compactUsage.getInsets().left + compactUsage.getInsets().right
                + mode.getPreferredSize().width + settings.getPreferredSize().width + 2;
    }

    @Override
    public void applyTheme() {
        setBackground(ThemeColors.header());
        setBorder(compact ? BorderFactory.createEmptyBorder()
                : BorderFactory.createMatteBorder(0, 0, 1, 0, ThemeColors.headerBorder()));
        updateButton(pin, false);
        updateButton(mode, false);
        updateButton(settings, false);
        updateButton(close, true);
        SwingUtilities.updateComponentTreeUI(compactMenu);
        updateSizing();
        revalidate();
        repaint();
    }

    private static void configure(AbstractButton button, String name, boolean close) {
        button.setToolTipText(name);
        button.getAccessibleContext().setAccessibleName(name);
        button.setPreferredSize(new Dimension(32, 28));
        button.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        button.setContentAreaFilled(false);
        button.setOpaque(true);
        button.setRolloverEnabled(true);
        button.getModel().addChangeListener(event -> updateButton(button, close));
        updateButton(button, close);
    }

    private static void updateButton(AbstractButton button, boolean close) {
        if (!button.isEnabled()) {
            button.setBackground(ThemeColors.header().darker());
            button.setForeground(ThemeColors.text().darker());
            return;
        }
        boolean hover = button.getModel().isRollover() || button.getModel().isPressed();
        button.setBackground(hover ? (close ? ThemeColors.closeHover() : ThemeColors.hover())
                : button.isSelected() ? ThemeColors.selected() : ThemeColors.header());
        button.setForeground(close && hover ? ThemeColors.closeText() : ThemeColors.text());
    }
}
