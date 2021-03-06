package org.telekit.base.plugin.internal;

import de.skuzzle.semantic.Version;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.telekit.base.Env;
import org.telekit.base.domain.exception.TelekitException;
import org.telekit.base.i18n.Messages;
import org.telekit.base.plugin.Metadata;
import org.telekit.base.plugin.Plugin;
import org.telekit.base.util.FileUtils;
import org.telekit.base.util.ZipUtils;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;

import static java.nio.file.Files.isDirectory;
import static java.nio.file.Files.isRegularFile;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.apache.commons.lang3.StringUtils.endsWithAny;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.telekit.base.Env.*;
import static org.telekit.base.i18n.BaseMessageKeys.*;
import static org.telekit.base.util.CommonUtils.hush;
import static org.telekit.base.util.FileUtils.*;

public class PluginInstaller {

    private static final Logger LOGGER = Logger.getLogger(PluginManager.class.getName());
    private static final String[] supportedExtensions = {".zip"};

    private final PluginRepository pluginRepository;
    private final PluginLoader pluginLoader;
    private final PluginCleaner pluginCleaner;

    public PluginInstaller(PluginRepository pluginRepository,
                           PluginLoader pluginLoader
    ) {
        this.pluginRepository = pluginRepository;
        this.pluginLoader = pluginLoader;
        this.pluginCleaner = new PluginCleaner();
    }

    public PluginBox install(Path sourcePath) {
        Path installDir = null;
        boolean needCleanup = false;

        LOGGER.info("Trying to install plugin from " + sourcePath.toAbsolutePath());
        if (isRegularFile(sourcePath) && endsWithAny(sourcePath.toString().toLowerCase(), supportedExtensions)) {
            installDir = extractToTempDir(sourcePath);
            needCleanup = true;
        } else if (isDirectory(sourcePath)) {
            installDir = sourcePath;
        } else {
            fireInstallFailed(PLUGIN_MSG_PATH_DOES_NOT_CONTAIN_PLUGINS);
        }

        try {
            return installFromDirectory(installDir);
        } finally {
            // it's not really necessary to delete temp dir
            try {
                if (needCleanup) {
                    LOGGER.info("Assigning cleanup tasks");
                    // use cleaner because Windows won't allow to delete loaded JAR file
                    pluginCleaner.appendTask(installDir);
                }
            } catch (Exception ignored) {}
        }
    }

    public void uninstall(Class<? extends Plugin> pluginClass, boolean deleteResources) {
        Optional<PluginBox> pluginBoxOpt = pluginRepository.get(pluginClass);
        if (pluginBoxOpt.isEmpty() || pluginBoxOpt.get().getState() == PluginState.UNINSTALLED) return;

        LOGGER.info("Uninstalling " + pluginClass);
        PluginBox pluginBox = pluginBoxOpt.get();
        Plugin plugin = pluginBox.getPlugin();
        pluginBox.setState(PluginState.UNINSTALLED);

        Path pluginDataDir = getPluginDataDir(plugin.getClass());
        Path pluginJarPath = Objects.requireNonNull(pluginBox.getJarPath());
        Path pluginLibPath = getPluginLibDir(plugin.getClass());
        Path pluginConfigPath = getPluginConfigDir(plugin.getClass());
        Path pluginDocsPath = getPluginDocsDir(plugin.getClass());
        LOGGER.info(pluginClass + " code resides in " + pluginJarPath.toAbsolutePath());
        LOGGER.info(pluginClass + " configs reside in " + pluginConfigPath.toAbsolutePath());
        LOGGER.info(pluginClass + " documentation resides in " + pluginDocsPath.toAbsolutePath());

        PluginCleaner cleaner = new PluginCleaner();

        // delete lib
        if (pluginJarPath.startsWith(pluginLibPath)) {
            cleaner.appendTask(pluginLibPath);
        } else {
            cleaner.appendTask(pluginJarPath);
        }

        // delete docs
        hush(() -> FileUtils.deleteDir(pluginDocsPath));

        // delete config
        if (Files.exists(pluginConfigPath) && isDirEmpty(pluginConfigPath)) {
            hush(() -> FileUtils.deleteDir(pluginDocsPath));
        }
        if (deleteResources) {
            cleaner.appendTask(pluginDataDir);
        }

        LOGGER.info("Uninstallation finished. Some resources will be deleted on next application startup");
    }

    public void validate(Plugin plugin) {
        Metadata meta = plugin.getMetadata();

        // check metadata
        if (meta == null) fireInstallFailed(PLUGIN_MSG_INVALID_METADATA);
        if (isBlank(meta.getName())) fireInstallFailed(PLUGIN_MSG_INVALID_NAME);
        if (isBlank(meta.getVersion())) fireInstallFailed(PLUGIN_MSG_INVALID_VERSION);

        // check required platform version
        if (!isSupportedPlatformVersion(meta.getPlatformVersion())) {
            fireInstallFailed(PLUGIN_MSG_HIGHER_PLATFORM_VERSION_REQUIRED, meta.getPlatformVersion());
        }

        // check for duplicates
        if (pluginRepository.contains(plugin.getClass())) {
            fireInstallFailed(PLUGIN_MSG_ALREADY_INSTALLED);
        }
    }

    public boolean isSupportedPlatformVersion(String requiredPlatformVersion) {
        // bypass check, plugin has no version requirements
        if (isBlank(requiredPlatformVersion)) return true;
        if (!Version.isValidVersion(requiredPlatformVersion)) fireInstallFailed(PLUGIN_MSG_INVALID_METADATA);
        return Version.parseVersion(Objects.requireNonNull(Env.getAppVersion()))
                .compareTo(Version.parseVersion(requiredPlatformVersion)) >= 0;
    }

    private PluginBox installFromDirectory(Path installDir) {
        List<Plugin> foundPlugins = new ArrayList<>();
        pluginLoader.load(Set.of(installDir))
                .iterator()
                .forEachRemaining(foundPlugins::add);

        LOGGER.info("Installation begin");
        if (foundPlugins.isEmpty()) fireInstallFailed(PLUGIN_MSG_PATH_DOES_NOT_CONTAIN_PLUGINS);
        if (foundPlugins.size() > 1) fireInstallFailed(PLUGIN_MSG_ONLY_ONE_PLUGIN_PER_DIR_ALLOWED);

        Plugin plugin = foundPlugins.get(0);
        if (plugin.getMetadata() != null) {
            LOGGER.info(plugin.getMetadata().toString());
        }
        validate(plugin);

        URL location = plugin.getLocation();
        Path sourceJarPath = urlToFile(Objects.requireNonNull(location)).toPath();
        Path sourceConfigPath = installDir.resolve(CONFIG_DIR_NAME);
        Path sourceDocsPath = installDir.resolve(DOCS_DIR_NAME);

        Path pluginDataDir = getPluginDataDir(plugin.getClass());
        Path destLibPath = getPluginLibDir(plugin.getClass());
        Path destConfigPath = getPluginConfigDir(plugin.getClass());
        Path destDocsPath = getPluginDocsDir(plugin.getClass());

        try {
            // create plugin root directory first
            createDirs(pluginDataDir);

            LOGGER.info("Copying JAR file");
            if (Files.exists(sourceJarPath)) {
                createDir(destLibPath);
                copyFile(sourceJarPath, destLibPath.resolve(sourceJarPath.getFileName()), REPLACE_EXISTING);
            }

            LOGGER.info("Copying plugin resources");
            if (Files.exists(sourceConfigPath)) copyDir(sourceConfigPath, destConfigPath, false);
            if (Files.exists(sourceDocsPath)) copyDir(sourceDocsPath, destDocsPath, false);

        } catch (Throwable t) {
            LOGGER.severe("Unable to copy plugin files to application directory");
            LOGGER.severe(ExceptionUtils.getStackTrace(t));
            hush(() -> deleteFile(pluginDataDir));
            fireInstallFailed(MSG_GENERIC_IO_ERROR);
        }

        PluginBox pluginBox = new PluginBox(plugin, PluginState.INSTALLED);
        pluginRepository.put(pluginBox);

        LOGGER.info("Installation finished");

        return pluginBox;
    }

    private static Path extractToTempDir(Path archiveFile) {
        try {
            Path tempDir = Files.createTempDirectory("telekit_plugin_");
            LOGGER.info("Extracting to " + tempDir.toAbsolutePath());

            if (!ZipUtils.isExtractable(archiveFile)) {
                throw new TelekitException(Messages.get(MGG_UNABLE_TO_EXTRACT_FILE));
            }
            ZipUtils.unzip(archiveFile, tempDir);

            return tempDir;
        } catch (IOException e) {
            throw new TelekitException(Messages.get(MGG_UNABLE_TO_EXTRACT_FILE), e);
        }
    }

    private static void fireInstallFailed(String reason) {
        throw new TelekitException(Messages.get(PLUGIN_MSG_PREFIX_INSTALLATION_FAILED) + " " + Messages.get(reason));
    }

    private static void fireInstallFailed(String reason, Object... args) {
        throw new TelekitException(Messages.get(PLUGIN_MSG_PREFIX_INSTALLATION_FAILED) + " " + Messages.get(reason, args));
    }
}
