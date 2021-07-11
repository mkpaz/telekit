package org.telekit.base.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProxyTest {

    @Test
    void testShouldProxy() {
        assertTrue(Proxy.match("192.168.1.1", "192.168.1.1"));
        assertTrue(Proxy.match("192.168.*", "192.168.1.1"));
        assertTrue(Proxy.match("192.168.0.0/16", "192.168.1.1"));

        assertFalse(Proxy.match("192.162.*", "192.168.1.1"));
        assertFalse(Proxy.match("192.168/16", "192.168.1.1"));

        assertTrue(Proxy.match("example.com", "example.com"));
        assertTrue(Proxy.match("*.example.com", "foo.example.com"));
        assertTrue(Proxy.match("foo.*.example.com", "foo.bar.example.com"));

        assertFalse(Proxy.match("*.example.com", "foo.example.org"));
        assertFalse(Proxy.match("*.example.com", "example.com"));

        assertFalse(Proxy.match("*.example.com", null));
        assertFalse(Proxy.match(null, "example.com"));
        assertFalse(Proxy.match(null, null));
    }
}