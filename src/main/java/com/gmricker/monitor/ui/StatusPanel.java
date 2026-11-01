package com.gmricker.monitor.ui;

import static com.gmricker.monitor.i18n.I18n.tr;
import com.gmricker.monitor.i18n.LanguageAware;

import com.gmricker.monitor.metrics.SystemSnapshot;
import com.gmricker.monitor.theme.ThemeAware;
import com.gmricker.monitor.theme.ThemeColors;
import java.awt.*;
import javax.swing.*;

/** Lightweight overview: no extra collectors or animation timers. */
final class StatusPanel extends JPanel implements ThemeAware, LanguageAware {
    private SystemSnapshot lastSnapshot;
    private final MetricCard cpu = new MetricCard("CPU");
    private final MetricCard memory = new MetricCard("RAM");
    private final MetricCard gpu = new MetricCard("GPU");
    private final JLabel status;
    private final JLabel pinStatus;

    StatusPanel(JLabel status, JLabel pinStatus) {
        super(new BorderLayout(0, 8));
        this.status = status;
        this.pinStatus = pinStatus;
        setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        var cards = new JPanel(new GridLayout(3, 1, 0, 6));
        cards.setOpaque(false);
        cards.add(cpu);
        cards.add(memory);
        cards.add(gpu);
        var scroll = new JScrollPane(cards);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        add(scroll, BorderLayout.CENTER);
        var footer = new JPanel();
        footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
        footer.setOpaque(false);
        footer.add(status);
        footer.add(pinStatus);
        pinStatus.setVisible(!pinStatus.getText().isBlank());
        pinStatus.addPropertyChangeListener("text", event -> pinStatus.setVisible(!pinStatus.getText().isBlank()));
        add(footer, BorderLayout.SOUTH);
        cpu.setToolTipText(tr("Clock médio estimado pelo Windows, incluindo turbo."));
        gpu.setToolTipText(tr("Uso do motor mais ocupado e memória dedicada (VRAM) em uso na mesma GPU."));
        updateReadings(null);
        applyTheme();
    }

    void updateReadings(SystemSnapshot snapshot) {
        lastSnapshot = snapshot;
        double cpuLoad = snapshot == null ? -1 : snapshot.cpuLoad();
        double gpuLoad = snapshot == null ? -1 : snapshot.gpuLoad();
        double ramLoad = -1;
        String frequency = tr("Clock indisponível");
        String ramDetail = tr("Memória indisponível");
        if (snapshot != null) {
            if (Double.isFinite(snapshot.cpuFrequencyMhz()) && snapshot.cpuFrequencyMhz() > 0)
                frequency = "Clock · %.2f GHz".formatted(snapshot.cpuFrequencyMhz() / 1000);
            if (snapshot.totalMemory() > 0 && snapshot.freeMemory() >= 0 && snapshot.freeMemory() <= snapshot.totalMemory()) {
                long used = snapshot.totalMemory() - snapshot.freeMemory();
                ramLoad = (double) used / snapshot.totalMemory();
                ramDetail = tr("%.2f / %.2f GiB em uso").formatted(used / 1073741824.0, snapshot.totalMemory() / 1073741824.0);
            }
        }
        cpu.update(cpuLoad, frequency);
        memory.update(ramLoad, ramDetail);
        String vramDetail = snapshot != null && snapshot.gpuMemoryUsed() >= 0
                ? tr("VRAM · %.2f GiB em uso").formatted(snapshot.gpuMemoryUsed() / 1073741824.0)
                : tr("VRAM indisponível");
        gpu.update(gpuLoad, vramDetail);
    }

    @Override public void applyLanguage() { updateReadings(lastSnapshot); }

    @Override public void applyTheme() {
        setBackground(ThemeColors.background());
        var font = UIManager.getFont("Label.font");
        status.setFont(font.deriveFont(Math.max(10f, font.getSize2D() - 1)));
        pinStatus.setFont(status.getFont());
        status.setForeground(ThemeColors.secondary());
        pinStatus.setForeground(ThemeColors.secondary());
        cpu.applyTheme();
        memory.applyTheme();
        gpu.applyTheme();
        repaint();
    }

    private static final class MetricCard extends JPanel implements ThemeAware {
        private final JLabel title;
        private final JLabel value = new JLabel("—");
        private final JLabel detail = new JLabel();

        MetricCard(String name) {
            super(new BorderLayout(0, 3));
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(6, 10, 7, 10));
            title = new JLabel(name);
            var heading = new JPanel(new BorderLayout());
            heading.setOpaque(false);
            heading.add(title, BorderLayout.WEST);
            heading.add(value, BorderLayout.EAST);
            add(heading, BorderLayout.NORTH);
            add(detail, BorderLayout.CENTER);
        }

        void update(double load, String description) {
            boolean available = Double.isFinite(load) && load >= 0;
            value.setText(available ? "%.0f%%".formatted(Math.min(1, load) * 100) : "—");
            detail.setText(description);
            value.getAccessibleContext().setAccessibleName(title.getText() + ": " + value.getText());
        }

        @Override public void applyTheme() {
            var font = UIManager.getFont("Label.font");
            title.setFont(font.deriveFont(Font.BOLD));
            value.setFont(font.deriveFont(Font.BOLD, font.getSize2D() + 3));
            detail.setFont(font.deriveFont(Math.max(10f, font.getSize2D() - 1)));
            title.setForeground(ThemeColors.text());
            value.setForeground(ThemeColors.text());
            detail.setForeground(ThemeColors.secondary());
            repaint();
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            var g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(ThemeColors.header());
                g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g.setColor(ThemeColors.headerBorder());
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            } finally { g.dispose(); }
        }
    }

}
