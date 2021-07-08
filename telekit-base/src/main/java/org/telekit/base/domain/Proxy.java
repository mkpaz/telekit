package org.telekit.base.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.jetbrains.annotations.Nullable;
import org.telekit.base.net.Scheme;

import java.net.PasswordAuthentication;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.telekit.base.util.CommonUtils.map;

public class Proxy {

    public static final Set<Scheme> SUPPORTED_SCHEMES = Set.of(Scheme.HTTP);
    public static final int MODE_NO_PROXY = 0;
    public static final int MODE_MANUAL = 1;

    private int mode = MODE_NO_PROXY;
    private URI uri;
    private String username;
    private char[] password;
    private List<String> exceptions = new ArrayList<>();

    public Proxy() {}

    public Proxy(Proxy proxy) {
        this.mode = proxy.getMode();
        this.uri = proxy.getUri();
        this.username = proxy.getUsername();
        this.password = proxy.getPassword();
        this.exceptions = proxy.getExceptions();
    }

    //@formatter:off
    public URI getUri() { return uri; }
    public void setUri(URI uri) { this.uri = uri; }

    public int getMode() { return mode; }
    public void setMode(int mode) { this.mode = mode; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public char[] getPassword() { return password; }
    public void setPassword(char[] password) { this.password = password; }

    public List<String> getExceptions() { return exceptions; }
    public void setExceptions(List<String> exceptions) { this.exceptions = exceptions; }
    //@formatter:on

    @JsonIgnore
    public @Nullable String getScheme() { return map(uri, URI::getScheme); }

    @JsonIgnore
    public @Nullable String getHost() { return map(uri, URI::getHost); }

    @JsonIgnore
    public int getPort() { return map(uri, u -> Math.max(uri.getPort(), 0), 0); }

    @JsonIgnore
    public @Nullable String getPasswordAsString() {
        return password != null && password.length > 0 ? new String(password) : null;
    }

    @JsonIgnore
    public @Nullable UsernamePasswordCredential getCredential() {
        return hasUsername() && hasPassword() ? new UsernamePasswordCredential(username, password) : null;
    }

    @JsonIgnore
    public @Nullable PasswordAuthentication getPasswordAuthentication() {
        return hasUsername() && hasPassword() ? new PasswordAuthentication(username, password) : null;
    }

    @JsonIgnore
    public boolean isValid() {
        return (mode == MODE_NO_PROXY) ||
                (mode == MODE_MANUAL && uri != null && SUPPORTED_SCHEMES.stream()
                        .anyMatch(scheme -> equalsIgnoreCase(scheme.toString(), uri.getScheme())));
    }

    private boolean hasUsername() { return isNotBlank(username); }

    private boolean hasPassword() { return password != null && password.length > 0; }

    @Override
    public String toString() {
        return "Proxy{" +
                "mode=" + mode +
                ", uri=" + uri +
                ", username='" + username + '\'' +
                ", password=" + Arrays.toString(password) +
                ", exceptions=" + exceptions +
                '}';
    }
}
