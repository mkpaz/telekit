package org.telekit.desktop.views.system;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import org.jetbrains.annotations.Nullable;
import org.telekit.base.desktop.mvvm.*;
import org.telekit.base.di.Initializable;
import org.telekit.base.domain.Proxy;
import org.telekit.base.domain.security.UsernamePasswordCredentials;
import org.telekit.base.domain.exception.InvalidInputException;
import org.telekit.base.domain.exception.TelekitException;
import org.telekit.base.event.DefaultEventBus;
import org.telekit.base.net.UriUtils;
import org.telekit.base.net.connection.Scheme;
import org.telekit.base.plugin.internal.PluginBox;
import org.telekit.base.plugin.internal.PluginException;
import org.telekit.base.plugin.internal.PluginManager;
import org.telekit.base.plugin.internal.PluginState;
import org.telekit.base.preferences.internal.ApplicationPreferences;
import org.telekit.base.preferences.internal.Language;
import org.telekit.base.preferences.internal.ManualProxy;
import org.telekit.controls.util.TransformationListHandle;
import org.telekit.desktop.event.PendingRestartEvent;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.URI;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.apache.commons.lang3.ClassUtils.getCanonicalName;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
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
        setProxyProperties();

        plugins.getFilteredList().setPredicate(p -> p.getState() != UNINSTALLED);
        updatePluginsList();
    }

    private void setProxyProperties() {
        activeProxyId.set(Proxy.NO_PROXY);

        for (Proxy proxy : preferences.getProxies()) {
            if (proxy instanceof ManualProxy manualProxy) {
                if (proxy.isActive()) { activeProxyId.set(proxy.id()); }

                proxyScheme.set(manualProxy.getScheme());
                proxyHost.set(manualProxy.getHost());
                proxyPort.set(manualProxy.getPort());
                proxyExceptions.set(String.join(";", manualProxy.getExceptions()));

                if (manualProxy.getCredentials() != null) {
                    proxyUsername.set(manualProxy.getCredentials().getUsername());
                    proxyPassword.set(manualProxy.getCredentials().getPasswordAsString());
                }
            }
        }
    }

    private @Nullable ManualProxy createManualProxyFromProperties() {
        if (isBlank(proxyHost.get())) { return null; }

        URI uri;
        try {
            uri = UriUtils.create(proxyScheme.get().toString(), proxyHost.get(), proxyPort.get());
        } catch (InvalidInputException e) {
            throw new TelekitException(e.getMessage(), e);
        }

        List<String> exceptions = null;
        if (isNotBlank(proxyExceptions.get())) {
            exceptions = Arrays.asList(proxyExceptions.get().split(COMMA_OR_SEMICOLON));
        }

        UsernamePasswordCredentials credentials = null;
        if (isNotBlank(proxyUsername.get()) && isNotBlank(proxyPassword.get())) {
            credentials = UsernamePasswordCredentials.of(proxyUsername.get(), proxyPassword.get());
        }

        return new ManualProxy(uri, credentials, exceptions);
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

    private final ObjectProperty<String> activeProxyId = new SimpleObjectProperty<>(this, "activeProxyId");
    public ObjectProperty<String> activeProxyIdProperty() { return activeProxyId; }

    private final ObjectProperty<Scheme> proxyScheme = new SimpleObjectProperty<>(this, "proxyScheme", Scheme.HTTP);
    public ObjectProperty<Scheme> proxySchemeProperty() { return proxyScheme; }

    private final StringProperty proxyHost = new SimpleStringProperty(this, "proxyHost");
    public StringProperty proxyHostProperty() { return proxyHost; }

    private final ObjectProperty<Integer> proxyPort = new SimpleObjectProperty<>(this, "proxyPort", 0);
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

            Proxy manualProxy = createManualProxyFromProperties();
            if (manualProxy != null) { preferences.updateProxy(manualProxy); }
            preferences.setActiveProxy(activeProxyId.get());

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
