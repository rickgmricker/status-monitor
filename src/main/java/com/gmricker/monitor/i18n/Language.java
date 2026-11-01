package com.gmricker.monitor.i18n;

import java.util.Locale;

public enum Language {
    ENGLISH("en", "English", Locale.ENGLISH),
    PORTUGUESE("pt-BR", "Português", Locale.forLanguageTag("pt-BR"));

    private final String id, label;
    private final Locale locale;
    Language(String id, String label, Locale locale) {
        this.id = id;
        this.label = label;
        this.locale = locale;
    }
    public String id() { return id; }
    public Locale locale() { return locale; }
    @Override public String toString() { return label; }
    public static Language fromId(String id) {
        if (id != null && (id.equalsIgnoreCase("en") || id.toLowerCase(Locale.ROOT).startsWith("en-"))) return ENGLISH;
        return PORTUGUESE;
    }
}
