package org.telekit.base.preferences.internal;

import org.telekit.base.domain.Proxy;

public abstract class ProxyBase implements Proxy {

    protected final String id;
    protected boolean active;

    public ProxyBase(String id, boolean active) {
        this.id = id;
        this.active = active;
    }

    public String id() { return id; }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void setActive(boolean active) {
        this.active = active;
    }
}
