package org.telekit.base.preferences.internal;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeName;
import org.telekit.base.domain.Proxy;
import org.telekit.base.domain.exception.TelekitException;
import org.telekit.base.domain.security.UsernamePasswordCredentials;
import org.telekit.base.net.connection.BaseConnectionParams;
import org.telekit.base.net.connection.ConnectionParams;
import org.telekit.base.net.connection.Scheme;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.apache.commons.collections4.ListUtils.defaultIfNull;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.telekit.base.i18n.BaseMessages.MSG_INVALID_PARAM;
import static org.telekit.base.i18n.I18n.t;

@JsonTypeName("manual")
public class ManualProxy extends ProxyBase {

    public static final String ID = "manual";
    public static final Set<Scheme> SUPPORTED_SCHEMES = Set.of(Scheme.HTTP);

    private final URI uri;
    private final UsernamePasswordCredentials credential;
    private final List<String> exceptions;

    @JsonCreator
    public ManualProxy(boolean active,
                       URI uri,
                       UsernamePasswordCredentials credential,
                       List<String> exceptions) {
        super(ID, active);

        this.uri = uri;
        this.credential = credential;
        this.exceptions = defaultIfNull(exceptions, Collections.emptyList());

        Scheme scheme = getScheme();
        if (scheme == null || !SUPPORTED_SCHEMES.contains(scheme)) {
            throw new TelekitException(t(MSG_INVALID_PARAM, "scheme: " + scheme));
        }

        if (isBlank(getHost())) {
            throw new TelekitException(t(MSG_INVALID_PARAM, "host: " + getHost()));
        }

        if (getPort() < 0) {
            throw new TelekitException(t(MSG_INVALID_PARAM, "port: " + getPort()));
        }
    }

    public URI getUri() { return uri; }

    public UsernamePasswordCredentials getCredential() { return credential; }

    public List<String> getExceptions() { return exceptions; }

    @Override
    public ConnectionParams getConnectionParams(String ipOrHostname) {
        boolean shouldNotProxy = exceptions.stream().anyMatch(e -> Proxy.globMatch(e, ipOrHostname));
        if (shouldNotProxy) { return null; }

        return new BaseConnectionParams(getScheme(), getHost(), getPort(), credential);
    }

    @JsonIgnore
    public Scheme getScheme() { return Scheme.fromString(uri.getScheme()); }

    @JsonIgnore
    public String getHost() { return uri.getHost(); }

    @JsonIgnore
    public int getPort() { return uri.getPort(); }

    @Override
    public String toString() {
        return "ManualProxy{" +
                "id=" + id +
                ", active=" + active +
                ", uri=" + uri +
                ", credential=" + credential +
                ", exceptions=" + exceptions +
                '}';
    }
}
