package org.telekit.base.preferences.internal;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Base64;
import java.util.Optional;

import static org.apache.commons.codec.digest.DigestUtils.sha256;

public interface Vault {

    String MASTER_KEY_ALIAS = "masterKey";
    int MAX_PASSWORD_LENGTH = 64;

    VaultType getVaultType();

    boolean isUnlocked();

    Optional<Key> getKey(String alias, byte[] password);

    void putKey(String alias, byte[] password, Key key);

    void unlock(byte[] password);

    void lock(byte[] password);

    void save(byte[] password);

    enum VaultType {

        PKCS12("PKCS12", ".p12");

        private final String keyStoreType;
        private final String fileExtension;

        VaultType(String keyStoreType, String fileExtension) {
            this.keyStoreType = keyStoreType;
            this.fileExtension = fileExtension;
        }

        public String getKeyStoreType() {
            return keyStoreType;
        }

        public String getFileExtension() {
            return fileExtension;
        }

        @Override
        public String toString() {
            return "VaultType{" +
                    "keyStoreType='" + keyStoreType + '\'' +
                    ", fileExtension='" + fileExtension + '\'' +
                    "} " + super.toString();
        }
    }

    /**
     * If vault is password protected vault key is derived from password
     * as Base64 encoded double hashed string.
     */
    static byte[] deriveFromPassword(byte[] password, int derivationType) {
        if (derivationType == 1) {
            // Base64 guaranties absence of non ASCII chars
            String hashedPass = new String(
                    Base64.getUrlEncoder().encode(sha256(sha256(password))),
                    StandardCharsets.UTF_8
            );

            // Many tools that work with key stores limit password length and hardcode that limit:
            // http://openssl.6102.n7.nabble.com/openssl-dev-Maximum-length-of-passwords-td49294.html
            // Current value (64) is still too big for some GUI tools that limit text input length.
            return hashedPass.length() <= MAX_PASSWORD_LENGTH ?
                    hashedPass.getBytes() :
                    hashedPass.substring(0, MAX_PASSWORD_LENGTH).getBytes();
        }
        return password;
    }
}
