package org.telekit.base.net.connection;

import org.jetbrains.annotations.Nullable;
import org.telekit.base.domain.security.Credentials;

public interface ConnectionParams {

    Scheme scheme();

    String host();

    int port();

    @Nullable Credentials credentials();
}
