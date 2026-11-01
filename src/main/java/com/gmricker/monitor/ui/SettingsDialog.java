package com.gmricker.monitor.ui;

import static com.gmricker.monitor.i18n.I18n.tr;
import com.gmricker.monitor.i18n.Language;
import com.gmricker.monitor.installer.InstallerPreferences;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.JComboBox;
import com.gmricker.monitor.theme.Theme;
import com.gmricker.monitor.theme.ThemeAware;
import com.gmricker.monitor.theme.ThemeColors;
import com.gmricker.monitor.theme.ThemeManager;

final class SettingsDialog extends JDialog implements ThemeAware {
    private final CollectorServicePanel collector = new CollectorServicePanel();
    SettingsDialog(JFrame owner, JCheckBox reducedCompact, JToggleButton pin, JCheckBox closeToTray,
                   JCheckBox startWithWindows, JComboBox<Theme> themes, JComboBox<Language> languages) {
        super(owner, tr("Configurações"), Dialog.ModalityType.MODELESS);
        setIconImages(ApplicationIcon.windowImages());
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        setResizable(false);
        var content = new JPanel(new BorderLayout(0, 16));
        content.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        var options = new JPanel(new GridLayout(0, 1, 0, 8));
        var themeRow = new JPanel(new BorderLayout(12, 0));
        var themeLabel = new JLabel(tr("Tema:"));
        themeLabel.setLabelFor(themes);
        themeRow.add(themeLabel, BorderLayout.WEST);
        themeRow.add(themes, BorderLayout.CENTER);
        options.add(themeRow);
        var languageRow = new JPanel(new BorderLayout(12, 0));
        var languageLabel = new JLabel(tr("Idioma:"));
        languageLabel.setLabelFor(languages);
        languageRow.add(languageLabel, BorderLayout.WEST);
        languageRow.add(languages, BorderLayout.CENTER);

        options.add(reducedCompact);
        var pinned = new JCheckBox(tr("Sobrepor outros programas"), pin.isSelected());
        pinned.setEnabled(pin.isEnabled());
        pinned.setToolTipText(tr("Oculta temporariamente durante tela cheia no mesmo monitor"));
        pinned.addActionListener(event -> {
            if (pinned.isSelected() != pin.isSelected()) {
                pin.doClick();
            }
        });
        pin.addItemListener(event -> pinned.setSelected(pin.isSelected()));
        options.add(pinned);
        options.add(closeToTray);
        options.add(startWithWindows);
        options.add(languageRow);
        if (!closeToTray.isEnabled()) {
            options.add(new JLabel(tr("Bandeja do sistema indisponível neste ambiente.")));
        }
        var body = new JPanel(new BorderLayout(0, 12));
        body.add(options, BorderLayout.NORTH);
        if (!InstallerPreferences.managedCollector()) body.add(collector, BorderLayout.SOUTH);
        content.add(body, BorderLayout.CENTER);
        var footer = new JPanel(new BorderLayout(0, 12));
        footer.add(new JLabel(tr("Alterações aplicadas e salvas automaticamente.")), BorderLayout.NORTH);
        var done = new JButton(tr("Concluir"));
        done.addActionListener(event -> setVisible(false));
        var buttons = new JPanel();
        buttons.add(done);
        footer.add(buttons, BorderLayout.SOUTH);
        content.add(footer, BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setDefaultButton(done);
        pack();
        applyTheme();
        ThemeManager.nativeCaption(this);
    }

    @Override
    public void applyTheme() {
        getContentPane().setBackground(ThemeColors.background());
    }

    void open() {
        if (collector.getParent() != null) collector.refresh();
        setLocationRelativeTo(getOwner());
        setVisible(true);
        toFront();
    }
}
