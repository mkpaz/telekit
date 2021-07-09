package org.telekit.base.domain;

import org.junit.jupiter.api.Test;
import org.telekit.base.domain.Proxy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProxyTest {

    @Test
    void testShouldProxy() {
        assertTrue(Proxy.globMatch("192.168.1.1", "192.168.1.1"));
        assertTrue(Proxy.globMatch("192.168.*", "192.168.1.1"));
        assertTrue(Proxy.globMatch("192.168.0.0/16", "192.168.1.1"));

        assertFalse(Proxy.globMatch("192.162.*", "192.168.1.1"));
        assertFalse(Proxy.globMatch("192.168/16", "192.168.1.1"));

        assertTrue(Proxy.globMatch("example.com", "example.com"));
        assertTrue(Proxy.globMatch("*.example.com", "foo.example.com"));
        assertTrue(Proxy.globMatch("foo.*.example.com", "foo.bar.example.com"));

        assertFalse(Proxy.globMatch("*.example.com", "foo.example.org"));
        assertFalse(Proxy.globMatch("*.example.com", "example.com"));

        assertFalse(Proxy.globMatch("*.example.com", null));
        assertFalse(Proxy.globMatch(null, "example.com"));
        assertFalse(Proxy.globMatch(null, null));
    }
}