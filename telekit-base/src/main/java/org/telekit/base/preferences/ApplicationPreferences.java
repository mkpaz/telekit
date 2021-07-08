package org.telekit.base.preferences;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.jetbrains.annotations.Nullable;
import org.telekit.base.Env;
import org.telekit.base.desktop.Dimension;
import org.telekit.base.domain.Proxy;
import org.telekit.base.domain.exception.TelekitException;
import org.telekit.base.i18n.I18n;

import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.prefs.Preferences;

import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;
import static org.telekit.base.Env.CONFIG_DIR;
import static org.telekit.base.Env.WINDOW_MAXIMIZED;
import static org.telekit.base.i18n.BaseMessages.MGG_UNABLE_TO_LOAD_DATA_FROM_FILE;
import static org.telekit.base.i18n.BaseMessages.MGG_UNABLE_TO_SAVE_DATA_TO_FILE;
import static org.telekit.base.util.CommonUtils.hush;
import static org.telekit.base.util.FileUtils.*;

@JacksonXmlRootElement
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApplicationPreferences {

    private Language language = Language.EN;
    private Proxy proxy;
    private Security security = new Security();

    @JacksonXmlElementWrapper(localName = "disabledPlugins")
    @JacksonXmlProperty(localName = "item")
    private Set<String> disabledPlugins = new HashSet<>();

    // indicates that preferences changes has been made
    private boolean dirty = false;

    public ApplicationPreferences() {}

    //@formatter:off
    public Language getLanguage() { return language; }
    public void setLanguage(Language language) { this.language = defaultIfNull(language, Language.EN); }

    public Proxy getProxy() { return proxy; }
    public void setProxy(Proxy proxy) { this.proxy = proxy; }

    public Security getSecurity() { return security; }
    public void setSecurity(Security security) { this.security = defaultIfNull(security, new Security()); }

    public Set<String> getDisabledPlugins() { return disabledPlugins; }
    public void setDisabledPlugins(Set<String> disabledPlugins) { this.disabledPlugins = defaultIfNull(disabledPlugins, new HashSet<>()); }
    //@formatter:on

    @JsonIgnore
    public Locale getLocale() {
        // env variable is only needed to simplify app testing
        return defaultIfNull(Env.LOCALE, language.getLocale());
    }

    @JsonIgnore
    public boolean isDirty() { return dirty; }

    public void setDirty() { this.dirty = true; }

    public void resetDirty() { this.dirty = false; }

    @Override
    public String toString() {
        return "UserPreferences{" +
                "language=" + language +
                ", proxy=" + proxy +
                ", security=" + security +
                ", disabledPlugins=" + disabledPlugins +
                '}';
    }

    ///////////////////////////////////////////////////////////////////////////
    //  System preferences                                                   //
    ///////////////////////////////////////////////////////////////////////////

    private final Preferences systemPreferences = Preferences.userRoot().node(Env.APP_NAME);
    private static final String WINDOW_WIDTH = "windowWidth";
    private static final String WINDOW_HEIGHT = "windowHeight";

    @JsonIgnore
    public @Nullable Dimension getMainWindowSize() {
        try {
            Objects.requireNonNull(systemPreferences);
            int width = systemPreferences.getInt(WINDOW_WIDTH, (int) WINDOW_MAXIMIZED.width());
            int height = systemPreferences.getInt(WINDOW_HEIGHT, (int) WINDOW_MAXIMIZED.height());
            return new Dimension(width, height);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void setMainWindowSize(Dimension dimension) {
        try {
            Objects.requireNonNull(systemPreferences);
            Objects.requireNonNull(dimension);

            int width = Math.max((int) dimension.width(), 800);
            int height = Math.max((int) dimension.height(), 600);

            if (WINDOW_MAXIMIZED.equals(dimension)) {
                width = (int) WINDOW_MAXIMIZED.width();
                height = (int) WINDOW_MAXIMIZED.height();
            }

            systemPreferences.putInt(WINDOW_WIDTH, width);
            systemPreferences.putInt(WINDOW_HEIGHT, height);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    public static final Path CONFIG_PATH = CONFIG_DIR.resolve("preferences.yaml");

    public static ApplicationPreferences load(YAMLMapper mapper) {
        return load(mapper, CONFIG_PATH);
    }

    public static ApplicationPreferences load(YAMLMapper mapper, Path path) {
        Objects.requireNonNull(mapper);
        Objects.requireNonNull(path);
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
