package org.telekit.base.preferences;

import org.jetbrains.annotations.Nullable;
import org.telekit.base.domain.Proxy;

public interface SharedPreferences {

    @Nullable Proxy getProxy();
}
