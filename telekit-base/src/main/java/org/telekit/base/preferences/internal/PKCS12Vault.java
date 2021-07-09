package org.telekit.base.preferences.internal;

import org.telekit.base.domain.exception.TelekitException;
import org.telekit.base.domain.exception.VaultLockedException;
import org.telekit.base.i18n.I18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.Objects;
import java.util.Optional;

import static org.telekit.base.i18n.BaseMessages.MGG_CRYPTO_GENERIC_ERROR;
import static org.telekit.base.i18n.BaseMessages.MGG_UNABLE_TO_LOAD_DATA_FROM_FILE;
import static org.telekit.base.util.StringUtils.bytesToChars;

public class PKCS12Vault implements Vault {

    private static final VaultType vaultType = VaultType.PKCS12;

    private final KeyStore keyStore;
    private final Path keyStorePath;
    private volatile boolean loaded;

    public PKCS12Vault(Path keyStorePath) {
        try {
            this.keyStorePath = Objects.requireNonNull(keyStorePath);
            this.keyStore = KeyStore.getInstance(vaultType.getKeyStoreType());
        } catch (KeyStoreException e) {
            throw new TelekitException(I18n.t(MGG_CRYPTO_GENERIC_ERROR, e));
        }
    }

    public Path getPath() {
        return keyStorePath;
    }

    @Override
    public VaultType getVaultType() {
        return vaultType;
    }

    @Override
    public boolean isUnlocked() {
        return loaded;
    }

    @Override
    public Optional<Key> getKey(String alias, byte[] password) {
        if (!loaded) {
            throw new VaultLockedException();
        }

        Objects.requireNonNull(alias);
        Objects.requireNonNull(password);

        try {
            char[] passwordSeq = bytesToChars(password);
            return Optional.ofNullable(keyStore.getKey(alias, passwordSeq));
        } catch (NoSuchAlgorithmException e) {
            throw new TelekitException(I18n.t(MGG_CRYPTO_GENERIC_ERROR, e));
        } catch (KeyStoreException | UnrecoverableKeyException e) {
            throw new TelekitException(I18n.t(MGG_UNABLE_TO_LOAD_DATA_FROM_FILE, e));
        }
    }

    @Override
    public void putKey(String alias, byte[] password, Key key) {
        if (!loaded) {
            throw new VaultLockedException();
        }

        Objects.requireNonNull(alias);
        Objects.requireNonNull(password);
        Objects.requireNonNull(key);

        try {
            char[] passwordSeq = bytesToChars(password);
            keyStore.setKeyEntry(alias, key, passwordSeq, null);
        } catch (KeyStoreException e) {
            throw new TelekitException(I18n.t(MGG_UNABLE_TO_LOAD_DATA_FROM_FILE, e));
        }
    }

    @Override
    public void unlock(byte[] password) {
        Objects.requireNonNull(password);

        try {
            char[] passwordSeq = bytesToChars(password);

            // keystore.load() will fail with IOException if file already exists
            // even if it's an empty file created with Files.createTempFile()
            if (!Files.exists(keyStorePath)) {
                keyStore.load(null, passwordSeq);
                this.loaded = true;
                return;
            }

            try (InputStream inputStream = Files.newInputStream(keyStorePath)) {
                keyStore.load(inputStream, passwordSeq);
                this.loaded = true;
            }
        } catch (NoSuchAlgorithmException e) {
            throw new TelekitException(I18n.t(MGG_CRYPTO_GENERIC_ERROR, e));
        } catch (IOException | CertificateException e) {
            throw new TelekitException(I18n.t(MGG_UNABLE_TO_LOAD_DATA_FROM_FILE, e));
        }
    }

    @Override
    public void lock(byte[] password) {
        // there is no unload() method for Java key stores
    }

    @Override
    public void save(byte[] password) {
        if (!loaded) {
            throw new VaultLockedException();
        }

        Objects.requireNonNull(password);

        try (OutputStream outputStream = Files.newOutputStream(keyStorePath)) {
            char[] passwordSeq = bytesToChars(password);
            keyStore.store(outputStream, passwordSeq);
        } catch (NoSuchAlgorithmException e) {
            throw new TelekitException(I18n.t(MGG_CRYPTO_GENERIC_ERROR, e));
        } catch (CertificateException | KeyStoreException | IOException e) {
            throw new TelekitException(I18n.t(MGG_UNABLE_TO_LOAD_DATA_FROM_FILE, e));
        }
    }
}
