package org.telekit.ui.tools.common;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jetbrains.annotations.NotNull;
import org.telekit.base.CompletionRegistry;
import org.telekit.base.domain.KeyValue;
import org.telekit.base.util.PasswordGenerator;

import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;

import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.telekit.base.util.NumberUtils.ensureRange;
import static org.telekit.base.util.PasswordGenerator.ASCII_LOWER_UPPER_DIGITS;
import static org.telekit.base.util.StringUtils.toBase64;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Param implements Comparable<Param>, Cloneable {

    public static final Comparator<Param> COMPARATOR = Comparator.comparing(Param::getName);

    public static final int MIN_PASSWORD_LENGTH = 8;
    public static final int MAX_PASSWORD_LENGTH = 32;
    public static final int DEFAULT_PASSWORD_LENGTH = 16;

    private String name;
    private Type type = Type.CONSTANT;
    private Integer length;
    private @JsonIgnore String value;

    public Param() {}

    public Param(String name, Type type, Integer length) {
        this.name = name;
        this.type = type;
        this.length = length;
    }

    public Param(Param param) {
        this.name = param.name;
        this.type = param.type;
        this.length = param.length;
        this.value = param.value;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Integer getLength() {
        return length;
    }

    public void setLength(Integer length) {
        this.length = length;
    }

    @JsonIgnore
    public boolean isAutoGenerated() {
        return type != Type.CONSTANT;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Param param = (Param) o;
        return name.equals(param.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return "Param{" +
                "name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", value='" + value + '\'' +
                ", length=" + length +
                '}';
    }

    @Override
    public int compareTo(@NotNull Param other) {
        return COMPARATOR.compare(this, other);
    }

    public enum Type {
        CONSTANT,
        PASSWORD,
        PASSWORD_BASE64,
        UUID
    }

    public static boolean allowsCompletion(Param param, CompletionRegistry registry) {
        return param != null &&
                param.getType() != null &&
                param.getType() == Type.CONSTANT &&
                isNotBlank(param.getName()) &&
                registry != null &&
                registry.isSupported(param.getName());
    }

    public static KeyValue<String, String> resolve(Param param) {
        Objects.requireNonNull(param);
        String paramName = param.getName();
        switch (param.getType()) {
            case CONSTANT -> {
                // put params into replacements even if param value is empty
                // because it might not be an error, but intended behavior
                return new KeyValue<>(paramName, defaultString(param.getValue(), ""));
            }
            case PASSWORD -> {
                return new KeyValue<>(paramName, generatePassword(param.getLength()));
            }
            case PASSWORD_BASE64 -> {
                return new KeyValue<>(paramName, toBase64(generatePassword(param.getLength())));
            }
            case UUID -> {
                return new KeyValue<>(paramName, String.valueOf(UUID.randomUUID()));
            }
        }
        throw new IllegalArgumentException("Unknown param type.");
    }

    private static String generatePassword(int length) {
        length = ensureRange(length, MIN_PASSWORD_LENGTH, MAX_PASSWORD_LENGTH, DEFAULT_PASSWORD_LENGTH);
        return PasswordGenerator.random(length, ASCII_LOWER_UPPER_DIGITS);
    }
}