package org.telekit.base.domain.security;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class PasswordCredentials extends Credentials {

    protected final char[] password;

    public PasswordCredentials(String name, char[] password) {
        super(name);
        this.password = password;

        if (password.length == 0) { throw new IllegalArgumentException("Password can't be empty."); }
    }

    // always clone mutable passwords, because some tools clean-up array
    // after it has been used for security reasons
    public char[] getPassword() { return password.clone(); }

    @JsonIgnore
    public String getPasswordAsString() { return new String(password); }

    @Override
    public String toString() {
        return "PasswordCredentials{" +
                "name='" + name + '\'' +
                ", password=********" +
                '}';
    }
}
