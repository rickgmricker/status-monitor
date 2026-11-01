package com.gmricker.monitor.ui;

import static com.gmricker.monitor.i18n.I18n.tr;
import com.gmricker.monitor.i18n.LanguageAware;

import com.gmricker.monitor.metrics.PcSpecs;
import java.awt.BorderLayout;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import com.gmricker.monitor.theme.ThemeAware;

final class SpecsPanel extends JPanel implements AutoCloseable, ThemeAware, LanguageAware {
    private final JTextArea details = new JTextArea(tr("Abra esta aba para carregar as especificações."));
    private final JButton refresh = new JButton(tr("Atualizar"));
    private SwingWorker<String, Void> loader;
    private boolean loaded;
    private int generation;

    SpecsPanel() {
        super(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        details.setEditable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        details.setFont(UIManager.getFont("Label.font"));
        details.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        add(new JScrollPane(details), BorderLayout.CENTER);
        var controls = new JPanel(new BorderLayout());
        controls.add(refresh, BorderLayout.EAST);
        add(controls, BorderLayout.SOUTH);
        refresh.addActionListener(event -> load());
    }

    void ensureLoaded() {
        if (!loaded && loader == null) {
            load();
        }
    }

    private void load() {
        if (loader != null) {
            return;
        }
        refresh.setEnabled(false);
        details.setText(tr("Carregando especificações…"));
        int request = ++generation;
        loader = new SwingWorker<>() {
            @Override
            protected String doInBackground() { return PcSpecs.read(); }

            @Override
            protected void done() {
                if (request != generation) return;
                try {
                    details.setText(get());
                    details.setCaretPosition(0);
                    loaded = true;
                } catch (CancellationException ignored) {
                    // The app closed while inventory was loading.
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException failure) {
                    details.setText(tr("Não foi possível carregar as especificações."));
                } finally {
                    loader = null;
                    refresh.setEnabled(true);
                }
            }
        };
        loader.execute();
    }

    @Override
    public void applyTheme() {
        details.setFont(UIManager.getFont("Label.font"));
    }

    @Override public void applyLanguage() {
        if (!loaded && loader == null) return;
        generation++;
        if (loader != null) loader.cancel(true);
        loader = null;
        loaded = false;
        load();
    }

    @Override
    public void close() {
        if (loader != null) {
            loader.cancel(true);
        }
    }
}
