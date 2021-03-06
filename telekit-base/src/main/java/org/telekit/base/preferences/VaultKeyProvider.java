package org.telekit.base.preferences;

import org.jetbrains.annotations.Nullable;
import org.telekit.base.service.KeyProvider;

import java.security.Key;
import java.util.Objects;

public class VaultKeyProvider implements KeyProvider {

    private final String alias;
    private final Security security;
    private final Vault vault;

    public VaultKeyProvider(Vault vault, Security security, String keyAlias) {
        this.vault = Objects.requireNonNull(vault);
        this.security = Objects.requireNonNull(security);
        this.alias = Objects.requireNonNull(keyAlias);
    }

    @Override
    public @Nullable Key getKey() {
        return vault.getKey(alias, security.getDerivedVaultPassword())
                .orElse(null);
    }
}
