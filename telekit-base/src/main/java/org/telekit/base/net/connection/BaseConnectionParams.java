package org.telekit.base.net.connection;

import org.jetbrains.annotations.Nullable;
import org.telekit.base.domain.exception.TelekitException;
import org.telekit.base.domain.security.Credentials;

import java.util.Objects;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.telekit.base.i18n.BaseMessages.MSG_INVALID_PARAM;
import static org.telekit.base.i18n.I18n.t;

public class BaseConnectionParams implements ConnectionParams {

    protected final Scheme scheme;
    protected final String host;
    protected final int port;
    protected final Credentials credentials;

    public BaseConnectionParams(Scheme scheme, String host, int port) {
        this(scheme, host, port, null);
    }

    public BaseConnectionParams(Scheme scheme, String host, int port, Credentials credentials) {
        this.scheme = Objects.requireNonNull(scheme);

        if (isBlank(host)) { throw new TelekitException(t(MSG_INVALID_PARAM, host)); }
        this.host = host;

        if (port < 0) { throw new TelekitException(t(MSG_INVALID_PARAM, port)); }
        this.port = port;

        this.credentials = credentials;
    }

    @Override
    public Scheme scheme() { return scheme; }

    @Override
    public String host() { return host; }

    @Override
    public int port() { return port; }

    @Override
    public @Nullable Credentials credentials() { return credentials; }

    @Override
    public String toString() {
        return "BaseConnectionParams{" +
                "scheme=" + scheme +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", credential=" + credentials +
                '}';
    }
}
