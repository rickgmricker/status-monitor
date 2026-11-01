package com.gmricker.monitor.i18n;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;
import java.util.HashMap;
import java.util.Map;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.Window;
import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;
import javax.swing.text.JTextComponent;
import javax.swing.JComponent;
import javax.swing.UIManager;

/** Portuguese source messages are the fallback; names and protocol strings bypass translation. */
public final class I18n {
    private static final Properties ENGLISH = load();
    private static final Map<String, String> SOURCES = sources();
    private static volatile Language current = Language.PORTUGUESE;
    private I18n() { }
    public static Language current() { return current; }
    public static void setLanguage(Language language) {
        current = language == null ? Language.PORTUGUESE : language;
        Locale.setDefault(Locale.Category.FORMAT, current.locale());
        JComponent.setDefaultLocale(current.locale());
        UIManager.getDefaults().setDefaultLocale(current.locale());
    }
    public static String tr(String source) {
        return current == Language.ENGLISH ? ENGLISH.getProperty(source, source) : source;
    }

    public static void apply(Language language) {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Change language on the UI thread");
        setLanguage(language);
        for (var window : Window.getWindows()) {
            if (!window.isDisplayable()) continue;
            refresh(window);
            if (window instanceof Dialog dialog) dialog.pack();
            window.validate();
            window.repaint();
        }
    }

    /** Only catalogued interface text is translated; arbitrary device and game names stay intact. */
    public static void refresh(Component component) {
        if (component instanceof JTabbedPane tabs) {
            for (int index = 0; index < tabs.getTabCount(); index++) {
                tabs.setTitleAt(index, translateDisplayed(tabs.getTitleAt(index)));
            }
        }
        if (component instanceof JLabel label) label.setText(translateDisplayed(label.getText()));
        if (component instanceof AbstractButton button) button.setText(translateDisplayed(button.getText()));
        if (component instanceof JTextComponent text) text.setText(translateDisplayed(text.getText()));
        if (component instanceof JProgressBar progress) progress.setString(translateDisplayed(progress.getString()));
        if (component instanceof Dialog dialog) dialog.setTitle(translateDisplayed(dialog.getTitle()));
        if (component instanceof Frame frame) frame.setTitle(translateDisplayed(frame.getTitle()));
        if (component instanceof JComponent swing) {
            swing.setToolTipText(translateDisplayed(swing.getToolTipText()));
            var accessible = swing.getAccessibleContext();
            if (accessible != null) accessible.setAccessibleName(translateDisplayed(accessible.getAccessibleName()));
            if (swing.getBorder() instanceof TitledBorder border) border.setTitle(translateDisplayed(border.getTitle()));
            if (swing.getComponentPopupMenu() != null) refresh(swing.getComponentPopupMenu());
        }
        if (component instanceof Container container) {
            for (var child : container.getComponents()) refresh(child);
        }
        if (component instanceof LanguageAware aware) aware.applyLanguage();
        component.invalidate();
        component.repaint();
    }

    private static String translateDisplayed(String displayed) {
        if (displayed == null) return null;
        String source = ENGLISH.containsKey(displayed) ? displayed : SOURCES.get(displayed);
        return source == null ? displayed : tr(source);
    }

    private static Map<String, String> sources() {
        var result = new HashMap<String, String>();
        for (var source : ENGLISH.stringPropertyNames()) result.put(ENGLISH.getProperty(source), source);
        return Map.copyOf(result);
    }
    private static Properties load() {
        try (var input = I18n.class.getResourceAsStream("en.properties")) {
            if (input == null) throw new IllegalStateException("Missing English translations");
            var result = new Properties();
            result.load(new InputStreamReader(input, StandardCharsets.UTF_8));
            return result;
        } catch (IOException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }
}
