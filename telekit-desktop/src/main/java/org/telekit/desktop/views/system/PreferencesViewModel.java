package org.telekit.desktop.views.system;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import org.telekit.base.desktop.mvvm.*;
import org.telekit.base.di.Initializable;
import org.telekit.base.domain.Proxy;
import org.telekit.base.domain.exception.TelekitException;
import org.telekit.base.event.DefaultEventBus;
import org.telekit.base.i18n.I18n;
import org.telekit.base.net.Scheme;
import org.telekit.base.plugin.internal.PluginBox;
import org.telekit.base.plugin.internal.PluginException;
import org.telekit.base.plugin.internal.PluginManager;
import org.telekit.base.plugin.internal.PluginState;
import org.telekit.base.preferences.ApplicationPreferences;
import org.telekit.base.preferences.Language;
import org.telekit.controls.util.TransformationListHandle;
import org.telekit.desktop.event.PendingRestartEvent;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

import static org.apache.commons.lang3.ClassUtils.getCanonicalName;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.telekit.base.i18n.BaseMessages.MSG_INVALID_PARAM;
import static org.telekit.base.plugin.internal.PluginState.DISABLED;
import static org.telekit.base.plugin.internal.PluginState.UNINSTALLED;
import static org.telekit.base.util.CSVUtils.COMMA_OR_SEMICOLON;

@Singleton
public class PreferencesViewModel implements Initializable, ViewModel {

    private final ApplicationPreferences preferences;
    private final PluginManager pluginManager;
    private final YAMLMapper yamlMapper;

    @Inject
    public PreferencesViewModel(ApplicationPreferences preferences,
                                PluginManager pluginManager,
                                YAMLMapper yamlMapper) {
        this.preferences = preferences;
        this.pluginManager = pluginManager;
        this.yamlMapper = yamlMapper;
    }

    @Override
    public void initialize() {
        language.set(preferences.getLanguage());
        setPropertiesFromProxy(preferences.getProxy());

        plugins.getFilteredList().setPredicate(p -> p.getState() != UNINSTALLED);
        updatePluginsList();
    }

    private void setPropertiesFromProxy(Proxy proxy) {
        proxyMode.set(proxy.getMode());
        proxyScheme.set(Scheme.HTTP);
        proxyHost.set(proxy.getHost());
        proxyPort.set(proxy.getPort());
        proxyUsername.set(proxy.getUsername());
        proxyPassword.set(proxy.getPasswordAsString());
        proxyExceptions.set(String.join(";", proxy.getExceptions()));
    }

    private Proxy getProxyFromProperties() {
        Proxy proxy = new Proxy();
        proxy.setMode(proxyMode.get());

        if (isNotBlank(proxyHost.get())) {
            try {
                proxy.setUri(new URI(proxyScheme.get().toString(),
                                     null, proxyHost.get(), proxyPort.get(),
                                     null, null, null
                ));
            } catch (URISyntaxException e) {
                throw new TelekitException(I18n.t(MSG_INVALID_PARAM, proxyHost.get()));
            }
        }

        if (isNotBlank(proxyExceptions.get())) {
            proxy.setExceptions(Arrays.asList(proxyExceptions.get().split(COMMA_OR_SEMICOLON)));
        }

        String username = proxyUsername.get();
        String password = proxyPassword.get();
        if (isNotBlank(username) && isNotBlank(password)) {
            proxy.setUsername(username);
            proxy.setPassword(password.toCharArray());
        }

        return proxy;
    }

    private void updatePluginsList() {
        plugins.getItems().setAll(pluginManager.getAllPlugins());
    }

    private void savePreferences() {
        ApplicationPreferences.save(preferences, yamlMapper);
        preferences.resetDirty();
    }

    ///////////////////////////////////////////////////////////////////////////
    // Properties                                                            //
    ///////////////////////////////////////////////////////////////////////////

    //@formatter:off
    private final ObjectProperty<Language> language = new SimpleObjectProperty<>(this, "language");
    public ObjectProperty<Language> languageProperty() { return language; }

    private final ObjectProperty<Integer> proxyMode = new SimpleObjectProperty<>(this, "proxyMode");
    public ObjectProperty<Integer> proxyModeProperty() { return proxyMode; }

    private final ObjectProperty<Scheme> proxyScheme = new SimpleObjectProperty<>(this, "proxyScheme");
    public ObjectProperty<Scheme> proxySchemeProperty() { return proxyScheme; }

    private final StringProperty proxyHost = new SimpleStringProperty(this, "proxyHost");
    public StringProperty proxyHostProperty() { return proxyHost; }

    private final ObjectProperty<Integer> proxyPort = new SimpleObjectProperty<>(this, "proxyPort");
    public ObjectProperty<Integer> proxyPortProperty() { return proxyPort; }

    private final StringProperty proxyUsername = new SimpleStringProperty(this, "proxyUsername");
    public StringProperty proxyUsernameProperty() { return proxyUsername; }

    private final StringProperty proxyPassword = new SimpleStringProperty(this, "proxyPassword");
    public StringProperty proxyPasswordProperty() { return proxyPassword; }

    private final StringProperty proxyExceptions = new SimpleStringProperty(this, "proxyExceptions");
    public StringProperty proxyExceptionsProperty() { return proxyExceptions; }

    private final TransformationListHandle<PluginBox> plugins = new TransformationListHandle<>();
    public ObservableList<PluginBox> getPlugins() { return plugins.getSortedList(); }

    private final ObjectProperty<PluginBox> selectedPlugin = new SimpleObjectProperty<>(this, "selectedPlugin");
    public ObjectProperty<PluginBox> selectedPluginProperty() { return selectedPlugin; }
    //@formatter:on

    ///////////////////////////////////////////////////////////////////////////
    // Commands                                                              //
    ///////////////////////////////////////////////////////////////////////////

    private final Command commitCommand = new CommandBase() {
        @Override
        protected void doExecute() {
            boolean restartRequired = false;

            if (!Objects.equals(preferences.getLanguage(), language.get())) {
                preferences.setLanguage(language.get());
                restartRequired = true;
            }

            preferences.setProxy(getProxyFromProperties());

            savePreferences();

            if (restartRequired) { DefaultEventBus.getInstance().publish(new PendingRestartEvent()); }
        }
    };

    public Command commitCommand() { return commitCommand; }

    private final Command togglePluginCommand = new CommandBase() {

        { executable.bind(selectedPlugin.isNotNull()); }

        @Override
        protected void doExecute() {
            PluginBox plugin = selectedPlugin.get();

            try {
                switch (plugin.getState()) {
                    case STARTED, FAILED -> {
                        pluginManager.disablePlugin(plugin.getPluginClass());
                        preferences.getDisabledPlugins().add(getCanonicalName(plugin.getPluginClass()));
                        preferences.setDirty();
                    }
                    case DISABLED -> {
                        pluginManager.enablePlugin(plugin.getPluginClass());
                        preferences.getDisabledPlugins().remove(getCanonicalName(plugin.getPluginClass()));
                        preferences.setDirty();
                    }
                }
            } catch (PluginException e) {
                // PluginException already contains internationalized message
                throw new TelekitException(e.getMessage(), e);
            }

            if (preferences.isDirty()) { savePreferences(); }

            updatePluginsList();
        }
    };

    public Command togglePluginCommand() { return togglePluginCommand; }

    private final ConsumerCommand<Path> installPluginCommand = new ConsumerCommandBase<>() {

        @Override
        protected void doExecute(Path path) {
            pluginManager.installPlugin(path);
            updatePluginsList();
            DefaultEventBus.getInstance().publish(new PendingRestartEvent());
        }
    };

    public ConsumerCommand<Path> installPluginCommand() { return installPluginCommand; }

    private final ConsumerCommand<Boolean> uninstallPluginCommand = new ConsumerCommandBase<>() {

        { executable.bind(selectedPlugin.isNotNull()); }

        @Override
        protected void doExecute(Boolean deleteResources) {
            PluginBox plugin = selectedPlugin.get();
            PluginState originalState = plugin.getState();

            pluginManager.uninstallPlugin(plugin.getPluginClass(), deleteResources);
            updatePluginsList();

            if (originalState == DISABLED) {
                preferences.getDisabledPlugins().remove(getCanonicalName(plugin.getPluginClass()));
                savePreferences();
            }

            DefaultEventBus.getInstance().publish(new PendingRestartEvent());
        }
    };

    public ConsumerCommand<Boolean> uninstallPluginCommand() { return uninstallPluginCommand; }
}
