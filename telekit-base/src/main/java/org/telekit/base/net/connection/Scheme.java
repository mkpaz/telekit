package org.telekit.base.net.connection;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

public enum Scheme {

    FILE,
    FTP,
    FTPS,
    HTTP,
    HTTPS,
    SFTP,
    SNMP,
    SOCKS4,
    SOCKS5,
    SSH,
    TELNET;

    public static @Nullable Scheme fromString(String s) {
        if (s == null) { return null; }
        for (Scheme scheme : Scheme.values()) {
            if (StringUtils.equalsIgnoreCase(scheme.toString(), s)) { return scheme; }
        }
        return null;
    }
}
