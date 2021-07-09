package org.telekit.base.domain.security;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.net.PasswordAuthentication;
import java.util.Objects;

public class UsernamePasswordCredentials extends PasswordCredentials {

    private final String username;

    public UsernamePasswordCredentials(String username, char[] password) {
        this("", username, password);
    }

    public UsernamePasswordCredentials(String name, String username, char[] password) {
        super(name, password);
        this.username = Objects.requireNonNull(username);
    }

    @Override
    public String getName() { return name; }

    public String getUsername() { return username; }

    @JsonIgnore
    public PasswordAuthentication toPasswordAuthentication() { return new PasswordAuthentication(username, password); }

    @Override
    public String toString() {
        return "UsernamePasswordCredentials{" +
                "name='" + name + '\'' +
                ", username='" + username + '\'' +
                ", password=********" +
                '}';
    }

    public static UsernamePasswordCredentials of(String username, String password) {
        return new UsernamePasswordCredentials(username.trim(), password.trim().toCharArray());
    }
}
