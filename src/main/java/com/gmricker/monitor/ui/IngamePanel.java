package com.gmricker.monitor.ui;

import static com.gmricker.monitor.i18n.I18n.tr;
import com.gmricker.monitor.i18n.LanguageAware;
import java.util.function.Supplier;

import com.gmricker.monitor.ingame.*;
import com.gmricker.monitor.metrics.SystemSnapshot;
import com.gmricker.monitor.theme.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.swing.*;

final class IngamePanel extends JPanel implements AutoCloseable, ThemeAware, LanguageAware {
    private final OverlaySettingsStore store;
    private final EnumMap<OverlaySettings.Item, JCheckBox> items = new EnumMap<>(OverlaySettings.Item.class);
    private final JComboBox<GameWindows.Target> games = new JComboBox<>();
    private final JButton refresh = new JButton(tr("Atualizar"));
    private final JCheckBox showAllApplications = new JCheckBox(tr("Mostrar todos os aplicativos"));
    private final JCheckBox automaticDetection = new JCheckBox(tr("Detecção automática"));
    private final JButton start = new JButton(tr("Iniciar overlay"));
    private final JButton stop = new JButton(tr("Parar"));
    private final JComboBox<OverlaySettings.Corner> corner = new JComboBox<>(OverlaySettings.Corner.values());
    private final JSpinner fontSize = new JSpinner(new SpinnerNumberModel(14, 10, 28, 1));
    private final JSlider opacity = new JSlider(0, 100, 70);
    private final JButton color = new JButton(tr("Cor do texto"));
    private final JButton shortcutButton = new JButton();
    private final JButton resetShortcut = new JButton("Reset");
    private final OverlayCanvas preview = new OverlayCanvas();
    private final JTextArea state = text(tr("Selecione o jogo aberto e inicie o overlay."));
    private Supplier<String> stateText = () -> tr("Selecione o jogo aberto e inicie o overlay.");
    static final int UPDATE_INTERVAL_MS = 500;
    static final int FRAMETIME_INTERVAL_MS = 50;
    private final Timer timer = new Timer(FRAMETIME_INTERVAL_MS, event -> tick());
    private long lastFrameRatesUpdate;
    private final Timer saveTimer = new Timer(400, event -> save());
    private SystemSnapshot system;
    private FrameStatistics.Snapshot frames = FrameStatistics.Snapshot.EMPTY;
    private PresentMonCapture capture;
    private GameOverlay overlay;
    private GameWindows.Target target;
    private int textRgb;
    private long captureStarted;
    private boolean refreshing;
    private OverlayShortcut shortcut;
    private GlobalOverlayHotkey hotkey;
    private boolean shortcutBusy, closed;
    private boolean overlayEnabled = true;
    private volatile boolean collecting;
    private ScheduledExecutorService automaticSampler;
    private GameWindows.Target automaticTarget;
    private boolean pendingStart;
    private int listGeneration, automaticGeneration;

    IngamePanel(Path directory) {
        super(new BorderLayout(0, 5));
        store = new OverlaySettingsStore(directory);
        OverlaySettings saved;
        try { saved = store.load(); }
        catch (IOException failure) { saved = OverlaySettings.DEFAULT; setState(() -> tr("Não foi possível carregar as preferências Ingame.")); }
        textRgb = saved.textRgb();
        shortcut = saved.shortcut();
        automaticDetection.setSelected(saved.automaticDetection());
        shortcutButton.setText(shortcut.toString());
        shortcutButton.getAccessibleContext().setAccessibleName(tr("Atalho do overlay"));
        shortcutButton.setToolTipText(tr("Clique para trocar a tecla que mostra ou oculta o overlay."));
        resetShortcut.setToolTipText(tr("Restaurar o atalho padrão: F3."));
        corner.setSelectedItem(saved.corner());
        fontSize.setValue(saved.fontSize());
        opacity.setValue(saved.opacity());
        saveTimer.setRepeats(false);
        games.getAccessibleContext().setAccessibleName(tr("Jogo"));
        games.setPrototypeDisplayValue(new GameWindows.Target(1234, tr("Selecione um jogo")));
        games.setMinimumSize(new Dimension(0, games.getPreferredSize().height));
        corner.getAccessibleContext().setAccessibleName(tr("Posição do overlay"));
        fontSize.getAccessibleContext().setAccessibleName(tr("Tamanho do texto"));
        opacity.getAccessibleContext().setAccessibleName(tr("Opacidade do fundo"));
        opacity.setToolTipText(tr("Opacidade do fundo: 0% transparente, 100% opaco."));
        var body = new Content();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        automaticDetection.setToolTipText(tr("Detecta o jogo em foco ocupando o monitor inteiro. A seleção manual fica desativada."));
        body.add(automaticDetection);
        body.add(text(tr("Jogo (abra-o antes de atualizar a lista):")));
        var chooser = new JPanel(new BorderLayout(5, 0));
        chooser.add(games); chooser.add(refresh, BorderLayout.EAST);
        chooser.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        body.add(chooser);
        showAllApplications.setToolTipText(tr("Mostra também aplicativos e jogos não reconhecidos pelo filtro automático."));
        body.add(showAllApplications);
        var buttons = new JPanel(new GridLayout(1, 2, 5, 0));
        buttons.add(start); buttons.add(stop);
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        body.add(Box.createVerticalStrut(6)); body.add(buttons);
        var shortcutControls = new JPanel(new BorderLayout(5, 0));
        shortcutControls.add(shortcutButton); shortcutControls.add(resetShortcut, BorderLayout.EAST);
        body.add(row(tr("Atalho:"), shortcutControls));
        body.add(Box.createVerticalStrut(8)); body.add(text(tr("Exibir no overlay:")));
        var selection = new JPanel(new GridLayout(3, 2, 4, 0));
        for (var item : OverlaySettings.Item.values()) {
            String label = switch (item) { case FPS -> "FPS"; case FRAMETIME -> "Frametime"; default -> item.toString(); };
            var checkbox = new JCheckBox(label, saved.items().contains(item));
            checkbox.getAccessibleContext().setAccessibleName(item.toString());
            checkbox.setAlignmentX(LEFT_ALIGNMENT);
            if (item == OverlaySettings.Item.LOW) checkbox.setToolTipText(tr("Média dos 1% de quadros mais lentos dos últimos 60 s (máximo 30 mil quadros). Requer 100 quadros."));
            if (item == OverlaySettings.Item.FPS) checkbox.setToolTipText(tr("Médio desde o início da coleta; atual no último segundo. FPS de apresentações do jogo."));
            if (item == OverlaySettings.Item.FRAMETIME) checkbox.setToolTipText(tr("Tempo do último quadro e gráfico dos últimos 120 quadros."));
            items.put(item, checkbox); selection.add(checkbox);
            checkbox.addActionListener(event -> changed());
        }
        selection.setMaximumSize(new Dimension(Integer.MAX_VALUE, selection.getPreferredSize().height));
        body.add(selection);
        body.add(Box.createVerticalStrut(8));
        body.add(row(tr("Posição:"), corner));
        body.add(row(tr("Texto (px):"), fontSize));
        body.add(row(tr("Fundo (%):"), opacity));
        var colorRow = new JPanel(new BorderLayout()); colorRow.add(color, BorderLayout.WEST);
        colorRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32)); body.add(colorRow);
        body.add(Box.createVerticalStrut(8)); body.add(text(tr("Prévia — usa as mesmas opções do overlay:")));
        var previewHolder = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 5));
        previewHolder.add(preview); body.add(previewHolder);
//        body.add(text(tr("CPU, GPU e RAM: uso geral do PC. Overlay em janela ou sem bordas; tela cheia exclusiva não é suportada.")));
        for (var component : body.getComponents()) {
            if (component instanceof JComponent child) child.setAlignmentX(LEFT_ALIGNMENT);
        }
        var scroll = new JScrollPane(body);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll);
        state.setBorder(BorderFactory.createEmptyBorder(3, 10, 5, 10));
        state.setRows(3);
        add(state, BorderLayout.SOUTH);
        refresh.addActionListener(event -> refreshGames());
        showAllApplications.addActionListener(event -> refreshGames());
        automaticDetection.addActionListener(event -> automaticModeChanged());
        start.addActionListener(event -> toggleOverlay());
        shortcutButton.addActionListener(event -> chooseShortcut());
        resetShortcut.addActionListener(event -> bindShortcut(OverlayShortcut.DEFAULT, true));
        stop.addActionListener(event -> stop(() -> tr("Overlay parado.")));
        games.addActionListener(event -> {
            var selected = (GameWindows.Target) games.getSelectedItem();
            games.setToolTipText(selected == null ? null : selected.toString());
            updateButtons();
        });
        corner.addActionListener(event -> changed());
        fontSize.addChangeListener(event -> changed());
        opacity.addChangeListener(event -> changed());
        color.addActionListener(event -> {
            var selected = JColorChooser.showDialog(this, tr("Cor do overlay"), new Color(textRgb));
            if (selected != null) { textRgb = selected.getRGB() & 0xFFFFFF; changed(); }
        });
        if (automaticDetection.isSelected()) setState(() -> tr("Aguardando jogo em tela cheia ou janela sem bordas. Use ") + shortcut + tr(" para ativar o overlay."));
        updatePreview(); updateButtons(); applyTheme();
    }
    private static JPanel row(String title, JComponent control) {
        var row = new JPanel(new BorderLayout(5, 0));
        row.add(new JLabel(title), BorderLayout.WEST); row.add(control);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        row.setAlignmentX(LEFT_ALIGNMENT);
        return row;
    }
    private static JTextArea text(String value) {
        var text = new JTextArea(value) {
            @Override public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        text.setColumns(24); text.setEditable(false); text.setOpaque(false);
        text.setLineWrap(true); text.setWrapStyleWord(true);
        text.setFont(UIManager.getFont("Label.font")); text.setAlignmentX(LEFT_ALIGNMENT);
        return text;
    }
    OverlaySettings settings() {
        var enabled = EnumSet.noneOf(OverlaySettings.Item.class);
        items.forEach((item, checkbox) -> { if (checkbox.isSelected()) enabled.add(item); });
        return new OverlaySettings(enabled, (OverlaySettings.Corner) corner.getSelectedItem(),
                (Integer) fontSize.getValue(), opacity.getValue(), textRgb, shortcut, automaticDetection.isSelected());
    }
    private void changed() {
        opacity.setToolTipText(tr("Opacidade do fundo: ") + opacity.getValue() + "%");
        updatePreview(); updateButtons(); saveTimer.restart();
        if (overlay != null) tick();
    }
    private void save() {
        try { store.save(settings()); }
        catch (IOException failure) { setState(() -> tr("Não foi possível salvar o Ingame: ") + failure.getMessage()); }
    }
    private void updatePreview() { preview.update(settings(), system, frames); }
    private void setState(Supplier<String> message) {
        stateText = message;
        state.setText(message.get());
    }
    @Override public void applyLanguage() {
        opacity.setToolTipText(tr("Opacidade do fundo: ") + opacity.getValue() + "%");
        state.setText(stateText.get());
        updateButtons();
        updatePreview();
        if (overlay != null) tick();
    }
    void updateReadings(SystemSnapshot snapshot) { system = snapshot; updatePreview(); }
    boolean isCollecting() { return collecting; }
    void ensureLoaded() {
        if (automaticDetection.isSelected()) startAutomaticDetection();
        else if (games.getItemCount() == 0) refreshGames();
    }
    private void refreshGames() {
        if (refreshing || overlay != null || automaticDetection.isSelected()) return;
        refreshing = true; updateButtons();
        int generation = ++listGeneration;
        boolean showAll = showAllApplications.isSelected();
        var selected = (GameWindows.Target) games.getSelectedItem();
        Thread.ofVirtual().name("ingame-games").start(() -> {
            try {
                var targets = GameWindows.list(showAll);
                SwingUtilities.invokeLater(() -> {
                    if (closed || generation != listGeneration || automaticDetection.isSelected()) return;
                    games.removeAllItems();
                    targets.forEach(games::addItem);
                    if (selected != null) targets.stream().filter(game -> game.pid() == selected.pid()).findFirst().ifPresent(games::setSelectedItem);
                    refreshing = false; updateButtons();
                    setState(() -> targets.isEmpty()
                            ? (showAll ? tr("Nenhum aplicativo com janela encontrado.")
                            : tr("Nenhum jogo detectado. Use Atualizar ou marque Mostrar todos os aplicativos."))
                            : (showAll ? targets.size() + tr(" aplicativos encontrados.") : targets.size() + tr(" jogos prováveis encontrados.")));
                });
            } catch (RuntimeException | LinkageError failure) {
                SwingUtilities.invokeLater(() -> { if (closed || generation != listGeneration || automaticDetection.isSelected()) return; refreshing = false; updateButtons(); setState(() -> tr("Não foi possível listar as janelas: ") + failure.getMessage()); });
            }
        });
    }
    private void start() {
        start(true);
    }
    private void start(boolean visible) {
        if (refreshing) return;
        target = automaticDetection.isSelected() ? automaticTarget : (GameWindows.Target) games.getSelectedItem();
        if (target == null || settings().items().isEmpty()) return;
        try {
            overlay = new GameOverlay();
            capture = new PresentMonCapture(target.pid());
            collecting = true;
            captureStarted = System.nanoTime();
            frames = FrameStatistics.Snapshot.EMPTY;
            lastFrameRatesUpdate = 0;
            overlayEnabled = visible;
            pendingStart = false;
            timer.start(); tick(); updateButtons();
        } catch (IOException | RuntimeException | LinkageError failure) { stop(() -> tr("Não foi possível iniciar: ") + failure.getMessage()); }
    }
    private void tick() {
        if (capture == null || overlay == null) return;
        if (ProcessHandle.of(target.pid()).filter(ProcessHandle::isAlive).isEmpty()) {
            stop(() -> tr("Coleta encerrada: o jogo foi fechado."));
            return;
        }
        long now = System.nanoTime();
        boolean updateRates = lastFrameRatesUpdate == 0
                || now - lastFrameRatesUpdate >= TimeUnit.MILLISECONDS.toNanos(UPDATE_INTERVAL_MS);
        frames = capture.snapshot(updateRates);
        if (updateRates) lastFrameRatesUpdate = now;
        updatePreview();
        if (!capture.isRunning()) { stop(capture.messageText()); return; }
        try {
            if (overlayEnabled) overlay.update(target.pid(), settings(), system, frames);
            else overlay.hide();
            setState(() -> !overlayEnabled ? tr("Overlay oculto. ") + shortcut + tr(" para exibir; a medição continua.")
                    : frames.samples() == 0 ? (System.nanoTime() - captureStarted > 8_000_000_000L
                    ? tr("Sem quadros recebidos. Confira o jogo selecionado e as permissões de rastreamento do Windows.")
                    : capture.message())
                    : tr("Coletando: ") + target.title() + tr(". O overlay aparece quando o jogo está em foco."));
        } catch (RuntimeException | LinkageError failure) { stop(() -> tr("Erro no overlay: ") + failure.getMessage()); }
    }
    private void updateButtons() {
        boolean active = overlay != null;
        boolean automatic = automaticDetection.isSelected();
        start.setText(active ? (overlayEnabled ? tr("Ocultar overlay") : tr("Mostrar overlay"))
                : pendingStart ? tr("Cancelar espera") : tr("Iniciar overlay"));
        start.setEnabled(!refreshing && (active || (!settings().items().isEmpty() && (automatic || games.getSelectedItem() != null))));
        stop.setEnabled(active || pendingStart);
        games.setEnabled(!automatic && !active && !refreshing);
        refresh.setEnabled(!automatic && !active && !refreshing);
        showAllApplications.setEnabled(!automatic && !active && !refreshing);
    }
    private void stop(Supplier<String> message) {
        collecting = false;
        pendingStart = false;
        timer.stop();
        if (capture != null) { capture.close(); capture = null; }
        if (overlay != null) { overlay.close(); overlay = null; }
        setState(message); updateButtons();
    }
    private void toggleOverlay() {
        if (closed || shortcutBusy) return;
        if (overlay == null) {
            if (settings().items().isEmpty()) { setState(() -> tr("Marque ao menos uma informação para exibir no overlay.")); return; }
            if (automaticDetection.isSelected() && automaticTarget == null) {
                pendingStart = !pendingStart;
                setState(() -> pendingStart ? tr("Overlay ativado para o próximo jogo detectado em tela cheia ou sem bordas.")
                        : tr("Espera cancelada. Aguardando jogo em tela cheia ou sem bordas."));
                updateButtons();
            } else if (games.getSelectedItem() == null) setState(() -> tr("Selecione um jogo na aba Ingame antes de usar ") + shortcut + ".");
            else start();
        } else {
            overlayEnabled = !overlayEnabled;
            tick(); updateButtons();
        }
    }
    @Override public void addNotify() {
        super.addNotify();
        if (hotkey == null && !closed) {
            hotkey = new GlobalOverlayHotkey(() -> SwingUtilities.invokeLater(this::toggleOverlay));
            bindShortcut(shortcut, false);
        }
        if (automaticDetection.isSelected()) startAutomaticDetection();
    }
    private void automaticModeChanged() {
        ++listGeneration;
        refreshing = false;
        stopAutomaticDetection();
        pendingStart = false;
        automaticTarget = null;
        if (automaticDetection.isSelected()) {
            games.removeAllItems();
            if (overlay == null) setState(() -> tr("Aguardando jogo em tela cheia ou janela sem bordas. Use ") + shortcut + tr(" para ativar o overlay."));
            if (isDisplayable()) startAutomaticDetection();
        } else if (overlay == null && isDisplayable()) refreshGames();
        else if (overlay != null && games.getItemCount() == 0) games.addItem(target);
        changed();
    }
    private void startAutomaticDetection() {
        if (closed || !automaticDetection.isSelected() || automaticSampler != null || !isDisplayable()) return;
        int generation = ++automaticGeneration;
        var detector = new AutomaticGameDetector();
        automaticSampler = Executors.newSingleThreadScheduledExecutor(task -> {
            var thread = new Thread(task, "ingame-auto-detection"); thread.setDaemon(true); return thread;
        });
        automaticSampler.scheduleWithFixedDelay(() -> {
            try {
                var detected = detector.detect();
                SwingUtilities.invokeLater(() -> {
                    if (!closed && generation == automaticGeneration && automaticDetection.isSelected()) acceptDetectedGame(detected);
                });
            } catch (RuntimeException | LinkageError failure) {
                SwingUtilities.invokeLater(() -> {
                    if (!closed && generation == automaticGeneration)
                        setState(() -> tr("Falha na detecção automática. Desmarque a opção para selecionar manualmente."));
                });
            }
        }, 0, 750, TimeUnit.MILLISECONDS);
    }
    void acceptDetectedGame(GameWindows.Target detected) {
        if (closed || !automaticDetection.isSelected()) return;
        if (detected == null) {
            if (automaticTarget != null && ProcessHandle.of(automaticTarget.pid()).filter(ProcessHandle::isAlive).isEmpty()) {
                automaticTarget = null; games.removeAllItems(); updateButtons();
            }
            return; // Keep the last live game during Alt+Tab, without restarting its measurement.
        }
        boolean changedGame = automaticTarget == null || automaticTarget.pid() != detected.pid();
        automaticTarget = detected;
        if (changedGame || !detected.equals(games.getSelectedItem())) {
            games.removeAllItems(); games.addItem(detected);
        }
        boolean switchCapture = overlay != null && target.pid() != detected.pid();
        if (overlay != null && !switchCapture) target = detected;
        boolean startWhenDetected = pendingStart;
        boolean visible = !switchCapture || overlayEnabled;
        if (switchCapture) stop(() -> tr("Trocando para o jogo detectado…"));
        if (switchCapture || startWhenDetected) {
            start(visible);
        } else if (changedGame && overlay == null) setState(() -> tr("Detectado: ") + detected.title() + tr(". Use ") + shortcut + tr(" para iniciar."));
        updateButtons();
    }
    private void stopAutomaticDetection() {
        ++automaticGeneration;
        if (automaticSampler != null) { automaticSampler.shutdownNow(); automaticSampler = null; }
    }
    private void shortcutBusy(boolean busy) {
        shortcutBusy = busy;
        shortcutButton.setEnabled(!busy); resetShortcut.setEnabled(!busy);
    }
    private void chooseShortcut() {
        shortcutBusy(true);
        if (hotkey == null) { showShortcutDialog(); return; }
        // Release the binding while listening, so the current key can also be selected.
        hotkey.setShortcut(null).whenComplete((success, failure) -> SwingUtilities.invokeLater(() -> {
            if (closed) return;
            if (failure != null || !Boolean.TRUE.equals(success)) {
                shortcutBusy(false); setState(() -> tr("Não foi possível liberar o atalho para edição."));
            } else showShortcutDialog();
        }));
    }
    private void showShortcutDialog() {
        var selected = OverlayShortcutDialog.choose(this);
        if (!closed) bindShortcut(selected == null ? shortcut : selected, selected != null);
    }
    private void bindShortcut(OverlayShortcut candidate, boolean persist) {
        shortcutBusy(true);
        if (hotkey == null) { acceptShortcut(candidate, persist); return; }
        hotkey.setShortcut(candidate).whenComplete((success, failure) -> SwingUtilities.invokeLater(() -> {
            if (closed) return;
            if (failure == null && Boolean.TRUE.equals(success)) acceptShortcut(candidate, persist);
            else {
                hotkey.setShortcut(shortcut).whenComplete((restored, restoreFailure) -> SwingUtilities.invokeLater(() -> {
                    if (closed) return;
                    shortcutBusy(false);
                    setState(() -> tr("Atalho ") + candidate + tr(" indisponível. ")
                            + (Boolean.TRUE.equals(restored) ? tr("Mantido ") + shortcut + "." : tr("Escolha outra tecla.")));
                }));
            }
        }));
    }
    private void acceptShortcut(OverlayShortcut candidate, boolean persist) {
        shortcut = candidate; shortcutButton.setText(shortcut.toString()); shortcutBusy(false);
        if (persist) { changed(); setState(() -> tr("Atalho do overlay: ") + shortcut + "."); }
    }
    @Override public void close() {
        if (closed) return;
        closed = true;
        stopAutomaticDetection();
        if (hotkey != null) hotkey.close();
        saveTimer.stop(); save(); stop(() -> tr("Overlay encerrado."));
    }
    @Override public void applyTheme() { state.setForeground(ThemeColors.secondary()); }
    private static final class Content extends JPanel implements Scrollable {
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 16; }
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) { return visible.height; }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() { return false; }
    }
}
