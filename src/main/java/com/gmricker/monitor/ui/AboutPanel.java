package com.gmricker.monitor.ui;

import static com.gmricker.monitor.i18n.I18n.tr;

import com.gmricker.monitor.theme.ThemeAware;
import com.gmricker.monitor.theme.ThemeColors;
import java.awt.Font;
import java.awt.Dimension;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import javax.swing.*;

final class AboutPanel extends JPanel implements ThemeAware {
    private final JLabel name;

    AboutPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(20, 18, 20, 18));
        var info = metadata();
        name = new JLabel(info.getProperty("name"));
        name.setAlignmentX(LEFT_ALIGNMENT);
        add(name);
        add(Box.createVerticalStrut(16));
        add(row(tr("Versão:"), info.getProperty("version")));
        add(Box.createVerticalStrut(8));
        add(row(tr("Autor:"), info.getProperty("author")));
        add(Box.createVerticalGlue());
        applyTheme();
    }

    private static JPanel row(String label, String value) {
        var row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.add(new JLabel(label));
        row.add(Box.createHorizontalStrut(6));
        row.add(new JLabel(value));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }

    @Override public void applyTheme() {
        setBackground(ThemeColors.background());
        name.setFont(UIManager.getFont("Label.font").deriveFont(Font.BOLD, 20f));
        name.setForeground(ThemeColors.text());
    }

    private static Properties metadata() {
        try (var input = AboutPanel.class.getResourceAsStream("/com/gmricker/monitor/application.properties")) {
            if (input == null) throw new IllegalStateException("Missing application metadata");
            var info = new Properties();
            info.load(new InputStreamReader(input, StandardCharsets.UTF_8));
            return info;
        } catch (IOException failure) {
            throw new IllegalStateException("Could not load application metadata", failure);
        }
    }
}
