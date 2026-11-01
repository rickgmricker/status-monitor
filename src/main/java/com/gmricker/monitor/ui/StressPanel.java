package com.gmricker.monitor.ui;

import static com.gmricker.monitor.i18n.I18n.tr;
import com.gmricker.monitor.i18n.LanguageAware;

import com.gmricker.monitor.metrics.SystemSnapshot;
import com.gmricker.monitor.stress.CpuStressTest;
import com.gmricker.monitor.stress.GpuStressTest;
import com.gmricker.monitor.theme.ThemeAware;
import com.gmricker.monitor.theme.ThemeColors;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.time.Duration;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.Timer;
import javax.swing.BoxLayout;
import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import javax.swing.Scrollable;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Insets;

final class StressPanel extends JPanel implements AutoCloseable, ThemeAware, LanguageAware {
    private final CpuStressTest cpuTest = new CpuStressTest();
    private final GpuStressTest gpuTest = new GpuStressTest();
    private boolean gpuSelected;
    private boolean running;
    private final JCheckBox limitDuration = new JCheckBox(tr("Duração (min):"), true);
    private final JSpinner duration = new JSpinner(new SpinnerNumberModel(5, 1, 10, 1));
    private final JButton cpuButton = new JButton(tr("Testar CPU"));
    private final JButton gpuButton = new JButton(tr("Testar GPU"));
    private final JButton stop = new JButton(tr("Parar"));
    private final JLabel state = new JLabel(tr("Pronto para iniciar"));
    private final JLabel readings = new JLabel("CPU: —   |   GPU: —   |   RAM: —");
    private final JTextArea details = text(tr("Escolha o componente para iniciar o teste."), 3);
    private final JProgressBar progress = new JProgressBar(0, 100);
    private final Timer timer = new Timer(200, event -> updateState());

    StressPanel() {
        super(new BorderLayout());
        getAccessibleContext().setAccessibleName(tr("Teste de stress"));
        int available = Runtime.getRuntime().availableProcessors();
        duration.getAccessibleContext().setAccessibleName(tr("Duração em minutos"));
        limitDuration.getAccessibleContext().setAccessibleName(tr("Limitar duração"));
        limitDuration.setToolTipText(tr("Desmarque para executar sem limite de tempo, até parar o teste."));
        limitDuration.addActionListener(event -> updateState());
        var content = new ScrollableContent();
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(new JLabel(tr("Teste de stress")), BorderLayout.NORTH);
        var body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
//        body.add(text("CPU: carga máxima em " + available + " threads.\nGPU: carga gráfica contínua no adaptador do driver.", 3));
        body.add(new JLabel(tr("Pode elevar a temperatura e o consumo.")));
        var inputs = new JPanel(new GridLayout(1, 2, 8, 6));
        inputs.add(limitDuration);
        inputs.add(duration);
        body.add(inputs);
        var buttons = new JPanel(new GridLayout(1, 3, 5, 0));
        cpuButton.setMargin(new Insets(4, 4, 4, 4));
        gpuButton.setMargin(new Insets(4, 4, 4, 4));
        stop.setMargin(new Insets(4, 4, 4, 4));
        buttons.add(cpuButton);
        buttons.add(gpuButton);
        buttons.add(stop);
        body.add(buttons);
        progress.setStringPainted(true);
        progress.getAccessibleContext().setAccessibleName(tr("Progresso do teste"));
        body.add(progress);
        body.add(state);
        body.add(readings);
        body.add(details);
        var rows = body.getComponents();
        body.removeAll();
        for (var row : rows) {
            if (row instanceof JComponent component) {
                component.setAlignmentX(LEFT_ALIGNMENT);
                component.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
            }
            body.add(row);
            body.add(Box.createVerticalStrut(6));
        }
        content.add(body, BorderLayout.CENTER);
        add(new JScrollPane(content), BorderLayout.CENTER);
        stop.setEnabled(false);
        cpuButton.addActionListener(event -> startTest(false));
        gpuButton.addActionListener(event -> startTest(true));
        stop.addActionListener(event -> stop());
    }

    private static JTextArea text(String value, int rows) {
        var area = new JTextArea(value, rows, 1);
        area.setEditable(false);
        area.setOpaque(false);
        area.setFont(UIManager.getFont("Label.font"));
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        return area;
    }

    private void startTest(boolean gpu) {
        if (isRunning()) return;
        try {
            gpuSelected = gpu;
            if (limitDuration.isSelected()) {
                var selectedDuration = Duration.ofMinutes((Integer) duration.getValue());
                if (gpu) gpuTest.start(selectedDuration);
                else cpuTest.start(selectedDuration);
            } else {
                if (gpu) gpuTest.startIndefinitely();
                else cpuTest.startIndefinitely();
            }
            timer.start();
            updateState();
        } catch (RuntimeException | LinkageError failure) {
            updateState();
            if (isRunning()) timer.start();
            state.setText(tr("Não foi possível iniciar o teste."));
            details.setText(failure.getMessage());
        }
    }

    private void updateState() {
        var cpu = cpuTest.status();
        var gpu = gpuTest.status();
        state.setForeground(gpuSelected && gpu.error() != null ? ThemeColors.error() : ThemeColors.text());
        boolean running = cpu.running() || gpu.running();
        boolean previouslyRunning = this.running;
        this.running = running;
        firePropertyChange("running", previouslyRunning, running);
        cpuButton.setEnabled(!running);
        gpuButton.setEnabled(!running);
        stop.setEnabled(running);
        limitDuration.setEnabled(!running);
        duration.setEnabled(!running && limitDuration.isSelected());
        long elapsed = gpuSelected ? gpu.elapsedMillis() : cpu.elapsedMillis();
        long total = gpuSelected ? gpu.durationMillis() : cpu.durationMillis();
        if (total == 0) {
            progress.setIndeterminate(false);
            progress.setValue(0);
            progress.setString(limitDuration.isSelected() ? "0%" : tr("Sem limite"));
            return;
        }
        boolean stopped = gpuSelected ? gpu.stopped() : cpu.stopped();
        String phase = running ? stopped ? tr("Interrompendo") : tr("Em execução") : stopped ? tr("Interrompido") : tr("Concluído");
        if (gpuSelected && gpu.preparing() && !stopped) phase = tr("Preparando GPU");
        if (gpuSelected && gpu.error() != null) phase = tr("Falha no teste de GPU");
        boolean unlimited = total < 0;
        progress.setIndeterminate(running && unlimited);
        progress.setValue(unlimited ? 0 : (int) Math.min(100, elapsed * 100 / total));
        progress.setString(unlimited ? tr("Sem limite") : progress.getValue() + "%");
        state.setText(phase + " · " + (gpuSelected ? "GPU" : "CPU")
                + " · " + formatTime(elapsed) + (unlimited ? "" : " / " + formatTime(total)));
        String description = gpuSelected
                ? gpu.error() != null ? gpu.error()
                    : gpu.device() != null ? tr("GPU em teste: ") + gpu.device() : tr("Inicializando o driver gráfico…")
                : tr("CPU: todos os ") + cpu.threads() + tr(" processadores lógicos disponíveis.");
        if (!description.equals(details.getText())) details.setText(description);
        details.setToolTipText(description);
        if (!running) timer.stop();
    }

    private static String formatTime(long milliseconds) {
        long seconds = Math.max(0, milliseconds / 1000);
        return "%02d:%02d".formatted(seconds / 60, seconds % 60);
    }

    void updateReadings(SystemSnapshot snapshot) {
        String cpu = snapshot.cpuLoad() >= 0 ? "%.0f%%".formatted(snapshot.cpuLoad() * 100) : "—";
        String ram = snapshot.totalMemory() > 0 && snapshot.freeMemory() >= 0
                ? "%.0f%%".formatted((snapshot.totalMemory() - snapshot.freeMemory()) * 100.0 / snapshot.totalMemory()) : "—";
        String gpu = Double.isFinite(snapshot.gpuLoad()) && snapshot.gpuLoad() >= 0
                ? "%.0f%%".formatted(snapshot.gpuLoad() * 100) : "—";
        readings.setText("CPU: " + cpu + "   |   GPU: " + gpu + "   |   RAM: " + ram);
    }

    void stop() {
        cpuTest.stop();
        gpuTest.stop();
        updateState();
    }

    boolean isRunning() { return cpuTest.status().running() || gpuTest.status().running(); }

    @Override public void applyLanguage() { updateState(); }

    @Override
    public void applyTheme() {
        refreshTextFonts(this);
        updateState();
    }

    private static void refreshTextFonts(java.awt.Container container) {
        for (var child : container.getComponents()) {
            if (child instanceof JTextArea area) area.setFont(UIManager.getFont("Label.font"));
            if (child instanceof java.awt.Container nested) refreshTextFonts(nested);
        }
    }

    @Override
    public void close() {
        cpuTest.close();
        gpuTest.close();
        timer.stop();
        progress.setIndeterminate(false);
    }

    private static final class ScrollableContent extends JPanel implements Scrollable {
        ScrollableContent() { super(new BorderLayout(0, 10)); }

        @Override
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }

        @Override
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 16; }

        @Override
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return Math.max(16, visible.height - 16);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() { return true; }

        @Override
        public boolean getScrollableTracksViewportHeight() { return false; }
    }
}
