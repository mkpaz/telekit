package org.telekit.base.preferences.internal;

import com.fasterxml.jackson.annotation.*;
import org.telekit.base.domain.security.SecuredData;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.telekit.base.Env.CONFIG_DIR;
import static org.telekit.base.preferences.internal.Vault.VaultType;
import static org.telekit.base.preferences.internal.Vault.deriveFromPassword;

@JsonRootName(value = "security")
@JsonIgnoreProperties(ignoreUnknown = true)
public class SecurityPreferences {

    public static final String DEFAULT_VAULT_FILE_NAME = "vault";

    // cannot be null
    private String vaultPath = DEFAULT_VAULT_FILE_NAME + VaultType.PKCS12.getFileExtension();
    private VaultType vaultType = VaultType.PKCS12;
    private @JsonProperty("kdType") int keyDerivationType = 1;
    private boolean autoUnlock = true;

    // key can be null, which means that vault have to be unlocked manually
    private @JsonUnwrapped(suffix = "VaultPass") SecuredData vaultPassword;

    public SecurityPreferences() {}

    public String getVaultPath() {
        return vaultPath;
    }

    public void setVaultPath(String vaultPath) {
        this.vaultPath = vaultPath != null ? vaultPath : DEFAULT_VAULT_FILE_NAME;
    }

    public VaultType getVaultType() {
        return vaultType;
    }

    public void setVaultType(VaultType vaultType) {
        this.vaultType = vaultType != null ? vaultType : VaultType.PKCS12;
    }

    public SecuredData getVaultPassword() {
        return vaultPassword;
    }

    public void setVaultPassword(SecuredData vaultPassword) {
        this.vaultPassword = vaultPassword;
    }

    public int getKeyDerivationType() {
        return keyDerivationType;
    }

    public void setKeyDerivationType(int keyDerivationType) {
        // disallow to use password without derivation
        this.keyDerivationType = 1;
    }

    public boolean isAutoUnlock() {
        return autoUnlock;
    }

    public void setAutoUnlock(boolean autoUnlock) {
        // TODO: implement manual unlock
        this.autoUnlock = true;
    }

    @JsonIgnore
    public Path getVaultFilePath() {
        Path tmpPath = Paths.get(vaultPath);
        return tmpPath.isAbsolute() ? tmpPath : CONFIG_DIR.resolve(tmpPath);
    }

    @JsonIgnore
    public byte[] getDerivedVaultPassword() {
        return vaultPassword != null ? deriveFromPassword(vaultPassword.getData(), keyDerivationType) : null;
    }

    @Override
    public String toString() {
        return "Security{" +
                "vaultPath='" + vaultPath + '\'' +
                ", vaultType=" + vaultType +
                ", keyDerivationType=" + keyDerivationType +
                ", autoUnlock=" + autoUnlock +
                ", vaultPassword=" + vaultPassword +
                '}';
    }
}
