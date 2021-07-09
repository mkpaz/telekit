package org.telekit.base.preferences.internal;

import java.util.Locale;

public enum Language {

    EN("English", new Locale("en")),
    RU("Русский", new Locale("ru"));

    private final String displayName;
    private final Locale locale;

    Language(String displayName, Locale locale) {
        this.displayName = displayName;
        this.locale = locale;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Locale getLocale() {
        return locale;
    }
}