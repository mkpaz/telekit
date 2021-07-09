package org.telekit.base.domain.security;

import java.util.Objects;

public abstract class Credentials {

    protected final String name;

    public Credentials(String name) {
        this.name = Objects.requireNonNull(name);
    }

    /**
     * Any name that helps to identify corresponding credentials.
     * Can be empty or represent an ID or service name.
     */
    public String getName() { return name; }

    @Override
    public String toString() {
        return "Credentials{" +
                "name='" + name + '\'' +
                '}';
    }
}
