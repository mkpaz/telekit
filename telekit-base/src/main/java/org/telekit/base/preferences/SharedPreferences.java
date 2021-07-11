package org.telekit.base.preferences;

import org.jetbrains.annotations.Nullable;
import org.telekit.base.domain.Proxy;

import java.util.prefs.Preferences;

public interface SharedPreferences {

    @Nullable Proxy getProxy();

    Preferences getSystemPreferences();
}
