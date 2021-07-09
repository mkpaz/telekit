package org.telekit.base.preferences.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.telekit.base.BaseSetup;
import org.telekit.base.domain.exception.TelekitException;
import org.telekit.base.domain.exception.VaultLockedException;
import org.telekit.base.preferences.internal.PKCS12Vault;

import java.nio.file.Path;
import java.security.Key;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.telekit.base.TestUtils.getTempDir;
import static org.telekit.base.preferences.internal.Vault.VaultType;
import static org.telekit.base.service.Encryptor.Algorithm;
import static org.telekit.base.service.Encryptor.generateKey;
import static org.telekit.base.util.FileUtils.deleteFile;
import static org.telekit.base.util.PasswordGenerator.ASCII_LOWER_DIGITS;
import static org.telekit.base.util.PasswordGenerator.random;

@ExtendWith(BaseSetup.class)
@TestMethodOrder(MethodOrderer.DisplayName.class)
public class PKCS12VaultTest {

    public static final VaultType VAULT_TYPE = VaultType.PKCS12;
    public static final Algorithm DEFAULT_ALG = Algorithm.AES_GCM;

    @Test
    @DisplayName("unlock empty vault and assert no exceptions thrown")
    public void unlock_EmptyVault_NoExceptionsThrown() {
        Path vaultPath = null;
        try {
            byte[] vaultPass = "qwerty".getBytes();
            PKCS12Vault vault = createVault();
            vaultPath = vault.getPath();

            vault.unlock(vaultPass);
            assertThat(vault.isUnlocked()).isTrue();
        } finally {
            cleanup(vaultPath);
        }
    }

    @Test
    @DisplayName("try to unlock vault using invalid password and assert exception thrown")
    public void unlock_InvalidPassword_ExceptionThrown() {
        Path vaultPath = null;
        try {
            byte[] validPass = "validPass".getBytes();
            String alias = "K";
            PKCS12Vault vaultOrig = createVault();
            vaultPath = vaultOrig.getPath();

            vaultOrig.unlock(validPass);
            Key origKey = generateKey(DEFAULT_ALG);
            vaultOrig.putKey(alias, validPass, origKey);
            vaultOrig.save(validPass);

            byte[] invalidPass = "invalidPass".getBytes();
            PKCS12Vault vaultSaved = new PKCS12Vault(vaultPath);
            assertThatThrownBy(() -> vaultSaved.unlock(invalidPass)).isInstanceOf(TelekitException.class);
        } finally {
            cleanup(vaultPath);
        }
    }

    @Test
    @DisplayName("save vault to disk and assert keys can be extracted")
    public void save_NewVault_KeysCanBeExtracted() {
        Path vaultPath = null;
        try {
            byte[] vaultPass = "qwerty".getBytes();
            String alias = "K";
            PKCS12Vault vaultOrig = createVault();
            vaultPath = vaultOrig.getPath();

            vaultOrig.unlock(vaultPass);
            Key origKey = generateKey(DEFAULT_ALG);
            vaultOrig.putKey(alias, vaultPass, origKey);
            vaultOrig.save(vaultPass);

            PKCS12Vault vaultSaved = new PKCS12Vault(vaultPath);
            vaultSaved.unlock(vaultPass);

            Optional<Key> keyOpt = vaultSaved.getKey(alias, vaultPass);
            assertThat(keyOpt).isPresent().get().isEqualTo(origKey);
        } finally {
            cleanup(vaultPath);
        }
    }

    @Test
    @DisplayName("get exiting key from vault and assert it can be extracted")
    public void getKey_ExistingSymmetricKey_ExtractedSuccessfully() {
        Path vaultPath = null;
        try {
            byte[] vaultPass = "qwerty".getBytes();
            String alias = "K";
            PKCS12Vault vault = createVault();
            vaultPath = vault.getPath();

            vault.unlock(vaultPass);
            Key origKey = generateKey(DEFAULT_ALG);
            vault.putKey(alias, vaultPass, origKey);

            Optional<Key> keyOpt = vault.getKey(alias, vaultPass);
            assertThat(vault.isUnlocked()).isTrue();
            assertThat(keyOpt).isPresent().get().isEqualTo(origKey);
        } finally {
            cleanup(vaultPath);
        }
    }

    @Test
    @DisplayName("get non-exiting key from vault and assert nothing found")
    public void getKey_NotExistingKey_NotPresent() {
        Path vaultPath = null;
        try {
            byte[] vaultPass = "qwerty".getBytes();
            String aliasK1 = "K1";
            String aliasK2 = "K2";
            PKCS12Vault vault = createVault();
            vaultPath = vault.getPath();

            vault.unlock(vaultPass);
            Key origKey = generateKey(DEFAULT_ALG);
            vault.putKey(aliasK1, vaultPass, origKey);

            Optional<Key> keyOpt = vault.getKey(aliasK2, vaultPass);
            assertThat(vault.isUnlocked()).isTrue();
            assertThat(keyOpt).isNotPresent();
        } finally {
            cleanup(vaultPath);
        }
    }

    @Test
    @DisplayName("try to get key from locked vault and assert exception thrown")
    public void getKey_LockedVault_ExceptionThrown() {
        Path vaultPath = null;
        try {
            byte[] vaultPass = "qwerty".getBytes();
            String alias = "K";
            PKCS12Vault vault = createVault();
            vaultPath = vault.getPath();

            assertThat(vault.isUnlocked()).isFalse();
            assertThatThrownBy(() -> vault.getKey(alias, vaultPass)).isInstanceOf(VaultLockedException.class);
        } finally {
            cleanup(vaultPath);
        }
    }

    @Test
    @DisplayName("try to put multiple keys of the same alg into vault and assert they can be extracted")
    public void putKey_MultipleKeysSameAlg_ExtractedSuccessfully() {
        Path vaultPath = null;
        try {
            byte[] vaultPass = "qwerty".getBytes();
            String aliasK1 = "K1", aliasK2 = "K2", aliasK3 = "K3";
            Key key1 = generateKey(DEFAULT_ALG), key2 = generateKey(DEFAULT_ALG), key3 = generateKey(DEFAULT_ALG);

            PKCS12Vault vault = createVault();
            vaultPath = vault.getPath();
            vault.unlock(vaultPass);

            vault.putKey(aliasK1, vaultPass, key1);
            vault.putKey(aliasK2, vaultPass, key2);
            vault.putKey(aliasK3, vaultPass, key3);

            assertThat(vault.isUnlocked()).isTrue();
            Optional<Key> keyOpt1 = vault.getKey(aliasK1, vaultPass);
            assertThat(keyOpt1).isPresent().get().isEqualTo(key1);
            Optional<Key> keyOpt2 = vault.getKey(aliasK2, vaultPass);
            assertThat(keyOpt2).isPresent().get().isEqualTo(key2);
            Optional<Key> keyOpt3 = vault.getKey(aliasK3, vaultPass);
            assertThat(keyOpt3).isPresent().get().isEqualTo(key3);
        } finally {
            cleanup(vaultPath);
        }
    }

    @Test
    @DisplayName("try to put multiple keys of different alg into vault and assert they can be extracted")
    public void putKey_MultipleKeysDifferentAlg_ExtractedSuccessfully() {
        Path vaultPath = null;
        try {
            byte[] vaultPass = "qwerty".getBytes();
            String aliasAesGcm = "K1", aliasChaCha20 = "K2";
            Key keyAesGcm = generateKey(Algorithm.AES_GCM);
            Key keyChaCha20 = generateKey(Algorithm.CHACHA20);

            PKCS12Vault vault = createVault();
            vaultPath = vault.getPath();
            vault.unlock(vaultPass);

            assertThat(vault.isUnlocked()).isTrue();

            // PKCS#12 from OpenJDK supports AES keys
            vault.putKey(aliasAesGcm, vaultPass, keyAesGcm);
            Optional<Key> keyOpt1 = vault.getKey(aliasAesGcm, vaultPass);
            assertThat(keyOpt1).isPresent().get().isEqualTo(keyAesGcm);

            // PKCS#12 from OpenJDK does NOT support ChaCha20 keys (tested with OpenJDK 15)
            assertThatThrownBy(() -> vault.putKey(aliasChaCha20, vaultPass, keyChaCha20))
                    .isInstanceOf(TelekitException.class);
        } finally {
            cleanup(vaultPath);
        }
    }

    @Test
    @DisplayName("try to put multiple identical keys into vault and assert they can be extracted")
    public void putKey_MultipleIdenticalKeys_ExtractedSuccessfully() {
        Path vaultPath = null;
        try {
            byte[] vaultPass = "qwerty".getBytes();
            String alias1 = "K1", alias2 = "K2";
            Key key = generateKey(DEFAULT_ALG);

            PKCS12Vault vault = createVault();
            vaultPath = vault.getPath();
            vault.unlock(vaultPass);

            vault.putKey(alias1, vaultPass, key);
            vault.putKey(alias2, vaultPass, key);

            assertThat(vault.isUnlocked()).isTrue();
            Optional<Key> keyOpt1 = vault.getKey(alias1, vaultPass);
            assertThat(keyOpt1).isPresent().get().isEqualTo(key);
            Optional<Key> keyOpt2 = vault.getKey(alias2, vaultPass);
            assertThat(keyOpt2).isPresent().get().isEqualTo(key);
        } finally {
            cleanup(vaultPath);
        }
    }

    @Test
    @DisplayName("try to put key into locked vault and assert exception thrown")
    public void putKey_LockedVault_ExceptionThrown() {
        Path vaultPath = null;
        try {
            byte[] vaultPass = "qwerty".getBytes();
            String alias = "K";
            Key key = generateKey(DEFAULT_ALG);
            PKCS12Vault vault = createVault();
            vaultPath = vault.getPath();

            assertThat(vault.isUnlocked()).isFalse();
            assertThatThrownBy(() -> vault.putKey(alias, vaultPass, key)).isInstanceOf(VaultLockedException.class);
        } finally {
            cleanup(vaultPath);
        }
    }

    @Test
    @DisplayName("try to save locked vault and assert exception thrown")
    public void save_LockedVault_ExceptionThrown() {
        Path vaultPath = null;
        try {
            byte[] vaultPass = "qwerty".getBytes();
            PKCS12Vault vault = createVault();
            vaultPath = vault.getPath();

            assertThat(vault.isUnlocked()).isFalse();
            assertThatThrownBy(() -> vault.save(vaultPass)).isInstanceOf(VaultLockedException.class);
        } finally {
            cleanup(vaultPath);
        }
    }

    private PKCS12Vault createVault() {
        String fileName = random(32, ASCII_LOWER_DIGITS) + VAULT_TYPE.getFileExtension();
        Path vaultPath = getTempDir().resolve(fileName);
        return new PKCS12Vault(vaultPath);
    }

    private void cleanup(Path vaultPath) {
        if (vaultPath != null) {
            deleteFile(vaultPath);
        }
    }
}