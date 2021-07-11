package org.telekit.base.preferences.internal;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonRootName;
import org.jetbrains.annotations.Nullable;
import org.telekit.base.domain.Proxy;

import java.util.List;
import java.util.Objects;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@JsonRootName(value = "proxy")
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProxyPreferences {

    private String activeProfile;
    private List<Proxy> profiles;

    public ProxyPreferences() {}

    public String getActiveProfile() {
        return activeProfile;
    }

    public void setActiveProfile(String activeProfile) {
        this.activeProfile = activeProfile;
    }

    public List<Proxy> getProfiles() {
        return profiles;
    }

    public void setProfiles(List<Proxy> profiles) {
        this.profiles = profiles;
    }

    @JsonIgnore
    public @Nullable Proxy getActiveProxy() {
        return profiles.stream()
                .filter(Proxy::isActive)
                .findFirst()
                .orElse(null);
    }

    public void setActiveProxy(String id) {
        if (isNotBlank(id)) {
            profiles.forEach(proxy -> proxy.setActive(id.equals(proxy.id())));
        }
    }

    public void updateProxy(Proxy proxy) {
        int pos = -1;
        for (int i = 0; i < profiles.size(); i++) {
            if (Objects.equals(profiles.get(i).id(), proxy.id())) {
                pos = i;
                break;
            }
        }

        if (pos < 0) {
            profiles.add(proxy);
        } else {
            profiles.set(pos, proxy);
        }
    }

    @Override
    public String toString() {
        return "ProxyPreferences{" +
                "activeProfile='" + activeProfile + '\'' +
                ", profiles=" + profiles +
                '}';
    }
}
