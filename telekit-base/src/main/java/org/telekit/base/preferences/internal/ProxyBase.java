package org.telekit.base.preferences.internal;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.telekit.base.domain.Proxy;

public abstract class ProxyBase implements Proxy {

    protected String id;
    protected boolean active;

    @JsonCreator
    public ProxyBase(/*@JsonProperty("id") String id*/) {
        /*this.id = id;*/
    }

    public String id() { return id; }

    @Override
    public boolean isActive() { return active; }

    @Override
    public void setActive(boolean active) { this.active = active; }
}
