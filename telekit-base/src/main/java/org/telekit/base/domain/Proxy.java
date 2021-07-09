package org.telekit.base.domain;

import inet.ipaddr.HostName;
import org.jetbrains.annotations.Nullable;
import org.telekit.base.net.connection.ConnectionParams;

import java.util.Objects;
import java.util.regex.Pattern;

public interface Proxy {

    String id();

    @Nullable ConnectionParams getConnectionParams(String ipOrHostname);

    boolean isActive();

    void setActive(boolean state);

    /**
     * Verifies whether provided strings has parent-child relationship.
     * Input strings can be IP address of equal type, which means if
     * parent string is a hostname, child string must also be a hostname.
     * <pre>
     * {@code
     *     192.168.1.1 ∋ 192.168.1.1 = true
     *     192.168.* ∋ 192.168.1.1 = true
     *     172.21.* ∋ 192.168.1.1 = false
     *     172.21.* ∋ null = false
     *     172.21.1.0/24 ∋ 172.21.1.0 = true
     *     *.example.com ∋ foo.example.com = true
     *     *.example.com ∋ google.com = false
     *     *.example.com ∋ com = false
     * }
     * </pre>
     * <p>
     * See tests for more example
     */
    static boolean globMatch(String parent, String child) {
        if (parent == null || child == null) { return false; }

        String parentClean = parent.trim().toLowerCase();
        String childClean = child.trim().toLowerCase();

        if (Objects.equals(parentClean, childClean)) { return true; }

        HostName parentHost = new HostName(parent);
        HostName childHost = new HostName(child);
        if (parentHost.isAddress() != childHost.isAddress()) { return false; }

        if (parentHost.isAddress()) {
            return parentHost.asAddress().contains(childHost.asAddress());
        } else {
            String globPattern = "^" + Pattern.quote(parentClean).replace("*", "\\E.*\\Q") + "$";
            return childClean.matches(globPattern);
        }
    }
}
