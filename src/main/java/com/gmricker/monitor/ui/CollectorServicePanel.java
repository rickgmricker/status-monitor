package com.gmricker.monitor.ui;

import static com.gmricker.monitor.i18n.I18n.tr;
import com.gmricker.monitor.i18n.LanguageAware;
import java.util.function.Supplier;

import com.gmricker.monitor.collector.WindowsCollectorService;
import java.awt.BorderLayout;
import java.util.concurrent.ExecutionException;
import javax.swing.*;

/** Management is explicit and asynchronous; capture never prompts for elevation. */
final class CollectorServicePanel extends JPanel implements LanguageAware {
    private final JLabel state = new JLabel(tr("Consultando coletor…"));
    private Supplier<String> stateText = () -> tr("Consultando coletor…");
    private final JButton manage = new JButton(tr("Instalar"));
    private boolean installed;
    private boolean busy;

    private void setState(Supplier<String> message) {
        stateText = message;
        state.setText(message.get());
    }
    @Override public void applyLanguage() { state.setText(stateText.get()); }

    CollectorServicePanel() {
        super(new BorderLayout(8, 4));
        setBorder(BorderFactory.createTitledBorder(tr("Serviço coletor")));
        var descriptions = new JPanel();
        descriptions.setLayout(new BoxLayout(descriptions, BoxLayout.Y_AXIS));
        descriptions.add(state);
        descriptions.add(new JLabel(tr("Inicia na coleta Ingame e para quando fica sem uso.")));
        add(descriptions, BorderLayout.CENTER);
        add(manage, BorderLayout.EAST);
        manage.setToolTipText(tr("Instalar ou remover exige autorização de administrador."));
        manage.setEnabled(false);
        manage.addActionListener(event -> changeInstallation());
    }
    void refresh() {
        if (busy) return;
        busy = true;
        manage.setEnabled(false);
        new SwingWorker<WindowsCollectorService.State, Void>() {
            protected WindowsCollectorService.State doInBackground() { return WindowsCollectorService.state(); }
            protected void done() {
                try { showState(get()); }
                catch (InterruptedException failure) { Thread.currentThread().interrupt(); showState(WindowsCollectorService.State.UNAVAILABLE); }
                catch (ExecutionException failure) { showState(WindowsCollectorService.State.UNAVAILABLE); }
                finally { busy = false; }
            }
        }.execute();
    }
    private void showState(WindowsCollectorService.State value) {
        installed = value == WindowsCollectorService.State.RUNNING || value == WindowsCollectorService.State.STOPPED;
        setState(() -> switch (value) {
            case NOT_INSTALLED -> tr("Não instalado · coleta sem elevação");
            case STOPPED -> tr("Instalado · parado (sob demanda)");
            case RUNNING -> tr("Instalado · em execução");
            case UNAVAILABLE -> tr("Não foi possível consultar o serviço");
        });
        manage.setText(installed ? tr("Remover") : tr("Instalar"));
        manage.setEnabled(value != WindowsCollectorService.State.UNAVAILABLE);
    }
    private void changeInstallation() {
        if (busy) return;
        boolean install = !installed;
        busy = true;
        manage.setEnabled(false);
        setState(() -> tr("Aguardando autorização e ") + (install ? tr("instalação…") : tr("remoção…")));
        new SwingWorker<Void, Void>() {
            protected Void doInBackground() throws Exception { WindowsCollectorService.manage(install); return null; }
            protected void done() {
                try { get(); }
                catch (InterruptedException failure) { Thread.currentThread().interrupt(); }
                catch (ExecutionException failure) {
                    JOptionPane.showMessageDialog(CollectorServicePanel.this, failure.getCause().getMessage(),
                            tr("Serviço coletor"), JOptionPane.ERROR_MESSAGE);
                } finally { busy = false; refresh(); }
            }
        }.execute();
    }
}
