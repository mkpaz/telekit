package org.telekit.base.domain.security;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.telekit.base.service.EncryptionService;
import org.telekit.base.service.impl.DefaultEncryptionService;

import java.nio.charset.StandardCharsets;

public class SecuredData {

    // All secured data objects share the same encryption service,
    // thus encryption key is unique for each app runtime.
    private static final EncryptionService ENCRYPTION_SERVICE = DefaultEncryptionService.create();

    @JsonProperty("secured")
    private byte[] data;

    public SecuredData() {}

    public SecuredData(byte[] data) {
        setData(data);
    }

    public byte[] getData() {
        return ENCRYPTION_SERVICE.decrypt(data);
    }

    public void setData(byte[] data) {
        this.data = ENCRYPTION_SERVICE.encrypt(data);
    }

    public static SecuredData fromString(String data) {
        return (data != null) ?
                new SecuredData(data.getBytes(StandardCharsets.UTF_8)) :
                new SecuredData();
    }

    @Override
    public int hashCode() {
        return -1;
    }

    @Override
    public boolean equals(Object o) {
        return false;
    }

    @Override
    public String toString() {
        return "********";
    }
}
