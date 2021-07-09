package org.telekit.desktop.startup.config;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.telekit.base.i18n.BaseMessages;
import org.telekit.base.i18n.I18n;
import org.telekit.base.preferences.internal.ApplicationPreferences;
import org.telekit.base.util.Mappers;
import org.telekit.controls.i18n.ControlsMessages;
import org.telekit.desktop.i18n.DesktopMessages;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.telekit.base.Env.*;
import static org.telekit.base.util.FileUtils.createDir;

public final class PreferencesConfig implements Config {

    private static final String APP_PROPS_FILE_NAME = "application.properties";

    private final YAMLMapper yamlMapper;
    private ApplicationPreferences preferences;

    public PreferencesConfig() {
        this.yamlMapper = Mappers.createYamlMapper();
        initialize();
    }

    private void initialize() {
        loadApplicationProperties();
        createUserResources();

        preferences = loadApplicationPreferences();

        loadI18nResources();
    }

    private void loadApplicationProperties() {
        try {
            Properties properties = new Properties();
            InputStreamReader reader = new InputStreamReader(Config.getResourceAsStream(APP_PROPS_FILE_NAME), UTF_8);
            properties.load(reader);

            for (Map.Entry<Object, Object> entry : properties.entrySet()) {
                System.setProperty(
                        String.valueOf(entry.getKey()),
                        String.valueOf(entry.getValue())
                );
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void createUserResources() {
        createDir(DATA_DIR);
        createDir(AUTOCOMPLETE_DIR);
        createDir(CONFIG_DIR);
        createDir(CACHE_DIR);
        createDir(PLUGINS_DIR);
    }

    private ApplicationPreferences loadApplicationPreferences() {
        if (Files.exists(ApplicationPreferences.CONFIG_PATH)) {
            // application can't work without preferences, don't make attempts to recover
            return ApplicationPreferences.load(yamlMapper);
        } else {
            ApplicationPreferences preferences = new ApplicationPreferences();
            preferences.setDirty();
            return preferences;
        }
    }

    private void loadI18nResources() {
        // set default locale and load resource bundles
        // after that any component can just call Locale.getDefault()
        Locale.setDefault(preferences.getLocale());
        I18n.getInstance().register(BaseMessages.getLoader());
        I18n.getInstance().register(ControlsMessages.getLoader());
        I18n.getInstance().register(DesktopMessages.getLoader());
        I18n.getInstance().reload();
    }

    public ApplicationPreferences getPreferences() {
        return preferences;
    }

    public void savePreferences() {
        if (preferences.isDirty()) {
            ApplicationPreferences.save(preferences, yamlMapper);
            preferences.resetDirty();
        }
    }
}
