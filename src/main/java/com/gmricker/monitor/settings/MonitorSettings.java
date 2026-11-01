package com.gmricker.monitor.settings;

import com.gmricker.monitor.theme.Theme;
import com.gmricker.monitor.i18n.Language;

public record MonitorSettings(boolean compact, boolean pinned, boolean closeToTray, Theme theme,
                              Integer compactX, Integer compactY, boolean reducedCompact, Language language) {
    public static final MonitorSettings DEFAULT = new MonitorSettings(false, false, false, Theme.SIMPLE);

    public MonitorSettings {
        if (theme == null) theme = Theme.SIMPLE;
        if (language == null) language = Language.PORTUGUESE;
        if (compactX == null || compactY == null) {
            compactX = null;
            compactY = null;
        }
    }

    public MonitorSettings(boolean compact, boolean pinned, boolean closeToTray, Theme theme,
                           Integer compactX, Integer compactY, boolean reducedCompact) {
        this(compact, pinned, closeToTray, theme, compactX, compactY, reducedCompact, Language.PORTUGUESE);
    }

    public MonitorSettings(boolean compact, boolean pinned, boolean closeToTray, Theme theme) {
        this(compact, pinned, closeToTray, theme, null, null);
    }

    public MonitorSettings(boolean compact, boolean pinned, boolean closeToTray, Theme theme,
                           Integer compactX, Integer compactY) {
        this(compact, pinned, closeToTray, theme, compactX, compactY, false);
    }

    public MonitorSettings(boolean compact, boolean pinned, boolean closeToTray) {
        this(compact, pinned, closeToTray, Theme.SIMPLE);
    }
}
