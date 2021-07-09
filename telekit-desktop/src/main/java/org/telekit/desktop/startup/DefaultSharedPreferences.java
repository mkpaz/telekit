package org.telekit.desktop.startup;

import org.jetbrains.annotations.Nullable;
import org.telekit.base.domain.Proxy;
import org.telekit.base.preferences.SharedPreferences;
import org.telekit.base.preferences.internal.ApplicationPreferences;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class DefaultSharedPreferences implements SharedPreferences {

    private final ApplicationPreferences preferences;

    @Inject
    public DefaultSharedPreferences(ApplicationPreferences preferences) {
        this.preferences = preferences;
    }

    @Override
    public @Nullable Proxy getProxy() {
        return preferences.getActiveProxy();
    }
}
