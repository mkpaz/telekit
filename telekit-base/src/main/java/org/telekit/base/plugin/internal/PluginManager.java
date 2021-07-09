package org.telekit.base.plugin.internal;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.telekit.base.di.Injector;
import org.telekit.base.event.DefaultEventBus;
import org.telekit.base.event.EventBus;
import org.telekit.base.i18n.BundleLoader;
import org.telekit.base.i18n.I18n;
import org.telekit.base.plugin.Extension;
import org.telekit.base.plugin.Plugin;
import org.telekit.base.preferences.internal.ApplicationPreferences;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.ClassUtils.getCanonicalName;
import static org.telekit.base.Env.PLUGINS_DIR;
import static org.telekit.base.i18n.BaseMessages.*;
import static org.telekit.base.plugin.internal.PluginState.*;

public class PluginManager {

    private static final Logger LOGGER = Logger.getLogger(PluginManager.class.getName());

    private static final Comparator<PluginBox> ALPHABETICAL_COMPARATOR =
            Comparator.comparing(box -> box.getPlugin().getMetadata().getName());

    private final PluginRepository pluginRepository;
    private final PluginLoader pluginLoader;
    private final PluginInstaller pluginInstaller;
    private final ApplicationPreferences preferences;
    private final EventBus eventBus = new DefaultEventBus();

    public PluginManager(ApplicationPreferences preferences) {
        this.pluginLoader = new PluginLoader();
        this.pluginRepository = new PluginRepository();
        this.pluginInstaller = new PluginInstaller(pluginRepository, pluginLoader);
        this.preferences = preferences;
    }

    /**
     * Searches plugin directory and loads all found plugins. If loaded plugin class name is not
     * listed in <b>disabledPlugins</b> set it will end up with {@link PluginState#LOADED} state,
     * otherwise its state will be {@link PluginState#DISABLED}. Disabled plugins won't be started
     * and only started plugin can provide its extensions during application runtime.
     */
    public void loadAllPlugins() {
        LOGGER.info(String.format("Searching %s for plugins", PLUGINS_DIR));

        Set<Path> scanPath;
        try {
            scanPath = Files.walk(PLUGINS_DIR)
                    .filter(path -> Files.isRegularFile(path) && path.toString().toLowerCase().endsWith(".jar"))
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            LOGGER.severe("Error while scanning plugins directory.");
            throw new RuntimeException(e);
        }
        Iterable<Plugin> plugins = pluginLoader.load(scanPath);

        Set<String> disabledPlugins = preferences.getDisabledPlugins();
        for (Plugin plugin : plugins) {
            PluginState status = !disabledPlugins.contains(getCanonicalName(plugin)) ?
                    LOADED :
                    DISABLED;

            PluginBox pluginBox = new PluginBox(plugin, status);
            LOGGER.info(status + ": " + plugin.getMetadata().toString());
            pluginRepository.put(pluginBox);
        }
    }

    /**
     * Starts all plugins in {@link PluginState#LOADED} state. This is method is called only once
     * at application startup. To restart individual plugin it should be disabled and them enabled
     * manually.
     * <p>
     * If any errors occurs while starting plugin it will be moved to {@link PluginState#FAILED}
     * state.
     * <p>
     * If one or more plugins were failed to start {@link PluginException} will be thrown.
     */
    public void startAllPlugins() throws PluginException {
        List<PluginBox> loadedPlugins = pluginRepository.find(pluginBox -> pluginBox.getState() == LOADED);
        if (loadedPlugins.isEmpty()) { return; }

        LOGGER.fine("Starting plugins");
        boolean errorFlag = false;
        for (PluginBox pluginBox : loadedPlugins) {
            try {
                startPlugin(pluginBox);
            } catch (PluginException e) {
                errorFlag = true;
            }
        }
        if (errorFlag) { throw new PluginException(PLUGIN_MSG_SOME_PLUGINS_WERE_NOT_STARTED); }
    }

    /**
     * Stops all plugins in {@link PluginState#STARTED} state. This is method is called only once
     * at application shutdown.
     * <p>
     * If any errors occurs while stopping plugin it will be moved to {@link PluginState#FAILED}
     * state.
     * <p>
     * If one or more plugins were failed to start {@link PluginException} will be thrown.
     */
    public void stopAllPlugins() throws PluginException {
        List<PluginBox> startedPlugins = pluginRepository.find(pluginBox -> pluginBox.getState() == STARTED);
        if (startedPlugins.isEmpty()) { return; }

        LOGGER.fine("Stopping plugins");
        boolean errorFlag = false;
        for (PluginBox pluginBox : startedPlugins) {
            try {
                stopPlugin(pluginBox);
            } catch (PluginException e) {
                errorFlag = true;
            }
        }
        if (errorFlag) { throw new PluginException(PLUGIN_MSG_SOME_PLUGINS_WERE_NOT_STOPPED); }
    }

    /**
     * Returns all plugins registered in plugins repository in any {@link PluginState}.
     */
    public List<PluginBox> getAllPlugins() {
        List<PluginBox> plugins = pluginRepository.findAll();
        plugins.sort(ALPHABETICAL_COMPARATOR);
        return plugins;
    }

    /** Returns plugin info */
    public Optional<PluginBox> find(Class<? extends Plugin> pluginClass) {
        return pluginRepository.get(pluginClass);
    }

    /**
     * Installs plugin from specified path which can be archive file or directory.
     * More info in {@link PluginInstaller#install}.
     *
     * @param sourcePath archive file or directory
     */
    public void installPlugin(Path sourcePath) {
        pluginInstaller.install(sourcePath);
    }

    public void uninstallPlugin(Class<? extends Plugin> pluginClass, boolean deleteResources) {
        Optional<PluginBox> pluginBoxOpt = pluginRepository.get(pluginClass);
        if (pluginBoxOpt.isEmpty()) { return; }

        PluginBox pluginBox = pluginBoxOpt.get();

        // it's always annoying when something that you want to uninstall
        // don't allow it for whatever reason, so uninstall is completely silent

        try {
            stopPlugin(pluginBox);
        } catch (PluginException ignored) {}

        try {
            // notify about plugin stopped to reload UI
            eventBus.publish(new PluginStateChangedEvent(pluginBox.getPluginClass(), STOPPED));

            pluginInstaller.uninstall(pluginClass, deleteResources);
        } catch (Throwable t) {
            LOGGER.severe("Error while deleting some plugin resources");
            LOGGER.severe(ExceptionUtils.getStackTrace(t));
        }

        pluginBox.setState(UNINSTALLED);
    }

    public void enablePlugin(Class<? extends Plugin> pluginClass) throws PluginException {
        Optional<PluginBox> pluginBoxOpt = pluginRepository.get(pluginClass);
        if (pluginBoxOpt.isEmpty()) { return; }

        PluginBox pluginBox = pluginBoxOpt.get();
        LOGGER.fine("Enabling plugin: " + getCanonicalName(pluginBox.getPluginClass()));
        pluginBox.setState(LOADED);
        startPlugin(pluginBox);

        // notify about plugin started to reload UI
        eventBus.publish(new PluginStateChangedEvent(pluginBox.getPluginClass(), STARTED));
    }

    public void disablePlugin(Class<? extends Plugin> pluginClass) throws PluginException {
        Optional<PluginBox> pluginBoxOpt = pluginRepository.get(pluginClass);
        if (pluginBoxOpt.isEmpty()) { return; }

        PluginBox pluginBox = pluginBoxOpt.get();
        LOGGER.fine("Disabling plugin: " + getCanonicalName(pluginBox.getPluginClass()));
        if (pluginBox.getState() == STARTED) {
            stopPlugin(pluginBox);
        }

        // notify about plugin stopped to reload UI
        eventBus.publish(new PluginStateChangedEvent(pluginBox.getPluginClass(), STOPPED));

        pluginBox.setState(DISABLED);
    }

    /**
     * This is entry point for any application tools that support extensions mechanism. This method
     * searches in repository of registered plugins for extensions classes of specified type and
     * instantiates them. Only plugins in {@link PluginState#STARTED} state can provide extensions.
     * <p>
     * Extension may need to use some services or want to be a singleton. To create an extension
     * {@link Injector} is used. If object is not found in {@link Injector}
     * it will be created via no-arg constructor.
     */
    public List<ExtensionBox> getExtensionsOfType(Class<? extends Extension> extensionType) {
        LOGGER.fine("Querying extension of type: " + getCanonicalName(extensionType));
        Collection<PluginBox> matchedPlugins = pluginRepository.findPluginsThatProvide(extensionType);
        if (matchedPlugins.isEmpty()) { return Collections.emptyList(); }

        LOGGER.fine("Extension providers found: " + matchedPlugins);
        return matchedPlugins.stream()
                .filter(pluginBox -> pluginBox.getState() == STARTED)
                .map(pluginBox -> createExtensionsOfType(pluginBox, extensionType))
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    public Optional<Class<? extends Plugin>> whatPluginProvides(Class<? extends Extension> implClass) {
        return pluginRepository.whatPluginProvides(implClass)
                .map(PluginBox::getPluginClass);
    }

    public void addEventListener(Consumer<PluginStateChangedEvent> listener) {
        eventBus.subscribe(PluginStateChangedEvent.class, Objects.requireNonNull(listener));
    }

    private void startPlugin(PluginBox pluginBox) throws PluginException {
        try {
            LOGGER.fine("Starting plugin: " + getCanonicalName(pluginBox.getPluginClass()));
            pluginBox.getPlugin().start();
            pluginBox.setState(STARTED);

            // if started, load resource bundle
            BundleLoader bundleLoader = pluginBox.getPlugin().getBundleLoader();
            if (bundleLoader != null) {
                I18n.getInstance().register(bundleLoader);
                I18n.getInstance().reload();
            }
        } catch (Throwable t) {
            LOGGER.severe("Failed to start plugin: " + getCanonicalName(pluginBox.getPluginClass()));
            LOGGER.severe(ExceptionUtils.getStackTrace(t));
            pluginBox.setState(PluginState.FAILED);
            throw new PluginException(I18n.t(PLUGIN_MSG_ERROR_WHILE_START), t);
        }
    }

    private void stopPlugin(PluginBox pluginBox) throws PluginException {
        try {
            LOGGER.fine("Stopping plugin: " + getCanonicalName(pluginBox.getPluginClass()));
            pluginBox.getPlugin().stop();
            pluginBox.setState(STOPPED);

            // cleanup i18n resources
            BundleLoader bundleLoader = pluginBox.getPlugin().getBundleLoader();
            if (bundleLoader != null) {
                I18n.getInstance().unregister(bundleLoader.id());
                I18n.getInstance().reload();
            }
        } catch (Throwable t) {
            LOGGER.severe("Failed to stop plugin: " + getCanonicalName(pluginBox.getPluginClass()));
            LOGGER.severe(ExceptionUtils.getStackTrace(t));
            pluginBox.setState(PluginState.FAILED);
            throw new PluginException(I18n.t(PLUGIN_MSG_ERROR_WHILE_STOP), t);
        }
    }

    private Collection<ExtensionBox> createExtensionsOfType(
            PluginBox pluginBox,
            Class<? extends Extension> extensionType
    ) {
        Collection<Class<? extends Extension>> extensionClasses = pluginBox.getExtensionsOfType(extensionType);
        if (extensionClasses.isEmpty()) { return Collections.emptyList(); }

        List<ExtensionBox> extensions = new ArrayList<>();
        Injector injector = Injector.getInstance();

        for (Class<? extends Extension> cls : extensionClasses) {
            LOGGER.fine("Instantiating extension: " + getCanonicalName(extensionType));
            // even if object is not provided by the injector (as a singleton)
            // it will be created via no-arg constructor, this is how getBean() method works
            Extension extension = injector.getBean(cls);
            extensions.add(new ExtensionBox(extension, pluginBox.getPluginClass()));
        }
        return extensions;
    }
}
