package org.telekit.base.preferences.internal;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.telekit.base.Env;
import org.telekit.base.domain.exception.TelekitException;
import org.telekit.base.i18n.I18n;

import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;
import static org.telekit.base.Env.CONFIG_DIR;
import static org.telekit.base.i18n.BaseMessages.MGG_UNABLE_TO_LOAD_DATA_FROM_FILE;
import static org.telekit.base.i18n.BaseMessages.MGG_UNABLE_TO_SAVE_DATA_TO_FILE;
import static org.telekit.base.util.CommonUtils.hush;
import static org.telekit.base.util.FileUtils.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ApplicationPreferences {

    public static final Path CONFIG_PATH = CONFIG_DIR.resolve("preferences.yaml");

    private Language language = Language.EN;
    private SecurityPreferences securityPreferences = new SecurityPreferences();
    private ProxyPreferences proxyPreferences = new ProxyPreferences();
    private Set<String> disabledPlugins = new HashSet<>();

    // API for java.util.prefs.Preferences, they aren't stored in the application config
    private final SystemPreferences systemPreferences = new SystemPreferences();

    // indicates that preferences changes has been made
    private boolean dirty = false;

    public ApplicationPreferences() {}

    public Language getLanguage() {
        return language;
    }

    public void setLanguage(Language language) {
        this.language = defaultIfNull(language, Language.EN);
    }

    public SecurityPreferences getSecurityPreferences() {
        return securityPreferences;
    }

    public void setSecurityPreferences(SecurityPreferences security) {
        this.securityPreferences = defaultIfNull(security, new SecurityPreferences());
    }

    public ProxyPreferences getProxyPreferences() {
        return proxyPreferences;
    }

    public void setProxyPreferences(ProxyPreferences proxyPreferences) {
        this.proxyPreferences = proxyPreferences;
    }

    public Set<String> getDisabledPlugins() {
        return disabledPlugins;
    }

    public void setDisabledPlugins(Set<String> disabledPlugins) {
        this.disabledPlugins = defaultIfNull(disabledPlugins, new HashSet<>());
    }

    @JsonIgnore
    public Locale getLocale() {
        // env variable is only needed to simplify app testing
        return defaultIfNull(Env.LOCALE, language.getLocale());
    }

    @JsonIgnore
    public SystemPreferences getSystemPreferences() {
        return systemPreferences;
    }

    @JsonIgnore
    public boolean isDirty() {
        return dirty;
    }

    public void setDirty() {
        this.dirty = true;
    }

    public void resetDirty() {
        this.dirty = false;
    }

    ///////////////////////////////////////////////////////////////////////////

    public static ApplicationPreferences load(YAMLMapper mapper) {
        return load(mapper, CONFIG_PATH);
    }

    public static ApplicationPreferences load(YAMLMapper mapper, Path path) {
        try {
            return mapper.readValue(path.toFile(), ApplicationPreferences.class);
        } catch (Exception e) {
            throw new TelekitException(I18n.t(MGG_UNABLE_TO_LOAD_DATA_FROM_FILE), e);
        }
    }

    public static void save(ApplicationPreferences preferences, YAMLMapper mapper) {
        save(preferences, mapper, CONFIG_PATH);
    }

    public static void save(ApplicationPreferences preferences, YAMLMapper mapper, Path path) {
        Path backup = backupFile(path);

        try {
            mapper.writeValue(path.toFile(), preferences);
        } catch (Exception e) {
            if (backup != null) {
                copyFile(backup, path, StandardCopyOption.REPLACE_EXISTING);
            }
            throw new TelekitException(I18n.t(MGG_UNABLE_TO_SAVE_DATA_TO_FILE), e);
        } finally {
            if (backup != null) { hush(() -> deleteFile(backup)); }
        }
    }
}
