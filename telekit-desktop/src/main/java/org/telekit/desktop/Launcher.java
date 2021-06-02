package org.telekit.desktop;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jetbrains.annotations.NotNull;
import org.telekit.base.Env;
import org.telekit.base.desktop.Dimension;
import org.telekit.base.desktop.ViewLoader;
import org.telekit.base.di.DependencyModule;
import org.telekit.base.di.Injector;
import org.telekit.base.domain.SecuredData;
import org.telekit.base.event.DefaultEventBus;
import org.telekit.base.event.Listener;
import org.telekit.base.i18n.BaseMessagesBundleProvider;
import org.telekit.base.i18n.Messages;
import org.telekit.base.plugin.Plugin;
import org.telekit.base.plugin.internal.PluginBox;
import org.telekit.base.plugin.internal.PluginCleaner;
import org.telekit.base.plugin.internal.PluginException;
import org.telekit.base.plugin.internal.PluginManager;
import org.telekit.base.preferences.ApplicationPreferences;
import org.telekit.base.preferences.PKCS12Vault;
import org.telekit.base.preferences.Security;
import org.telekit.base.preferences.Vault;
import org.telekit.base.util.Mappers;
import org.telekit.base.util.PasswordGenerator;
import org.telekit.controls.i18n.ControlsMessagesBundleProvider;
import org.telekit.controls.theme.ThemeLoader;
import org.telekit.desktop.domain.CloseEvent;
import org.telekit.desktop.main.FileCompletionMonitoringService;
import org.telekit.desktop.main.MainController;
import org.telekit.desktop.tools.apiclient.MigrationUtilsApiClient;
import org.telekit.desktop.tools.filebuilder.MigrationUtilsFileBuilder;

import javax.net.ssl.SSLServerSocketFactory;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Key;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import static org.telekit.base.Env.*;
import static org.telekit.base.preferences.Vault.MASTER_KEY_ALIAS;
import static org.telekit.base.service.Encryptor.generateKey;
import static org.telekit.base.util.DesktopUtils.xdgCurrentDesktopMatches;
import static org.telekit.base.util.FileUtils.createDir;
import static org.telekit.base.util.PasswordGenerator.ASCII_LOWER_UPPER_DIGITS;
import static org.telekit.desktop.IconCache.ICON_APP;
import static org.telekit.desktop.MessageKeys.MAIN_TRAY_OPEN;
import static org.telekit.desktop.MessageKeys.QUIT;

public class Launcher extends Application {

    public static final int RESTART_EXIT_CODE = 3;

    public static final String APP_ICON_PATH = "/org/telekit/desktop/assets/images/telekit.png";
    public static final String APP_PROPS_PATH = "/org/telekit/desktop/application.properties";
    public static final String LOG_CONFIG_FILE_NAME = "logging.properties";
    public static final String LOG_CONFIG_FALLBACK = "/org/telekit/desktop/logging.properties";
    public static final String LOG_OUTPUT_FILE_NAME = "telekit.log";
    public static final String I18N_RESOURCES_PATH = "org.telekit.desktop.i18n.messages";
    public static final Dimension STAGE_MIN_SIZE = new Dimension(800, 600);
    public static final Dimension STAGE_PREF_SIZE = new Dimension(1440, 900);

    private static int exitCode = 0;
    private final Injector injector = Injector.getInstance();
    private Logger logger;
    private ExceptionHandler exceptionHandler;
    private ApplicationPreferences preferences;
    private PluginManager pluginManager;

    public static void main(String[] args) {
        launch(args);
        System.exit(exitCode);
    }

    @Override
    public void start(Stage primaryStage) {
        // set exception handler first
        this.exceptionHandler = new ExceptionHandler(primaryStage);
        Thread.currentThread().setUncaughtExceptionHandler(
                (thread, throwable) -> this.exceptionHandler.showErrorDialog(throwable)
        );

        // initialize application and set class-level variables
        initialize();

        // create main controller
        MainController controller = ViewLoader.load(MainController.class);
        controller.setPrimaryStage(primaryStage);

        // populate icon cache
        IconCache.put(ICON_APP, new Image(getResourceAsStream(APP_ICON_PATH)));
        primaryStage.getIcons().add(IconCache.get(ICON_APP));

        // handle application close events
        DefaultEventBus.getInstance().subscribe(CloseEvent.class, this::close);
        primaryStage.setOnCloseRequest(t -> {
            if (preferences != null) {
                preferences.setMainWindowSize(Dimension.of(primaryStage));
            }
            Platform.exit();
        });

        Rectangle2D screenSize = Screen.getPrimary().getVisualBounds();
        Dimension prefWindowSize = STAGE_PREF_SIZE.lt(screenSize) ? STAGE_PREF_SIZE : STAGE_MIN_SIZE;
        Dimension storedWindowSize = this.preferences.getMainWindowSize(); // previous window size

        // use last closed window size if possible
        if (storedWindowSize != null) prefWindowSize = storedWindowSize;
        // override stored window size if it was forced via env variable
        if (FORCED_WINDOW_SIZE != null) prefWindowSize = FORCED_WINDOW_SIZE;
        // if special dimension value (0, 0) is used, maximize the stage
        if (WINDOW_MAXIMIZED.equals(prefWindowSize)) primaryStage.setMaximized(true);

        Scene scene = new Scene(controller.getRoot(), prefWindowSize.width(), prefWindowSize.height());

        // apply theme
        ThemeLoader themeLoader = new ThemeLoader();
        scene.getStylesheets().addAll(themeLoader.getStylesheets(""));

        // show primary stage
        primaryStage.setTitle(Env.APP_NAME);
        primaryStage.setMinWidth(STAGE_MIN_SIZE.width());
        primaryStage.setMinHeight(STAGE_MIN_SIZE.height());
        primaryStage.setScene(scene);
        primaryStage.show();
        Platform.runLater(() -> {
            primaryStage.toFront();
            primaryStage.requestFocus();
        });

        // create tray icon (won't work in some Linux DE)
        if (this.preferences.isSystemTray()) {
            createTrayIcon(primaryStage);
        }
    }

    @Override
    public void stop() {
        try {
            this.pluginManager.stopAllPlugins();
        } catch (PluginException ignored) {
            // even if some plugin wasn't stopped, it shouldn't prevent application from shutting down
        }
    }

    @NotNull
    public static URL getResource(String resource) {
        return Objects.requireNonNull(Launcher.class.getResource(resource));
    }

    @NotNull
    public static InputStream getResourceAsStream(String resource) {
        return Objects.requireNonNull(Launcher.class.getResourceAsStream(resource));
    }

    @Listener
    public void close(CloseEvent event) {
        exitCode = event.getExitCode();
        if (this.preferences != null && event.getWindowSize() != null) {
            this.preferences.setMainWindowSize(event.getWindowSize());
        }
        Platform.exit();
    }

    ///////////////////////////////////////////////////////////////////////////

    // ATTENTION: code order matters, some of these methods initialize class level variables
    private void initialize() {
        this.logger = setupLogging(); // initializes logger variable
        logEnvironmentInfo();
        loadApplicationProperties();  // load properties from application.properties file
        createUserResources();

        // cleanup previously uninstalled plugins
        PluginCleaner cleaner = new PluginCleaner();
        cleaner.executeAllSilently();

        // create app preferences, they will be required later
        YAMLMapper yamlMapper = Mappers.createYamlMapper();
        this.preferences = loadApplicationPreferences(yamlMapper);

        // load master key vault
        Vault vault = loadKeyVault();

        // set default locale and load resource bundles
        // after that any component can just call Locale.getDefault()
        Locale.setDefault(preferences.getLocale());
        Messages.getInstance().load(
                ControlsMessagesBundleProvider.getBundle(Locale.getDefault()),
                ControlsMessagesBundleProvider.class.getName()
        );
        Messages.getInstance().load(
                BaseMessagesBundleProvider.getBundle(Locale.getDefault()),
                BaseMessagesBundleProvider.class.getName()
        );
        Messages.getInstance().load(
                ResourceBundle.getBundle(I18N_RESOURCES_PATH, Locale.getDefault(), Launcher.class.getModule()),
                Launcher.class.getName()
        );

        // save preferences if changes were made
        if (this.preferences.isDirty()) {
            ApplicationPreferences.save(this.preferences, yamlMapper);
            preferences.resetDirty();
        }

        // find and load all plugins (but don't start them)
        this.pluginManager = new PluginManager(this.preferences);
        this.pluginManager.loadAllPlugins();

        // collect all modules and initialize injector
        List<DependencyModule> modules = new ArrayList<>();
        modules.add(new MainDependencyModule(
                this.preferences,
                this.pluginManager,
                vault
        ));
        for (PluginBox container : this.pluginManager.getAllPlugins()) {
            Plugin plugin = container.getPlugin();
            modules.addAll(plugin.getModules());
        }
        this.injector.configure(modules);

        // start plugins
        try {
            // TODO: notify user if some plugins weren't started
            // NOTE: plugins should be started BEFORE MainController initialization
            //       because it queries extensions to build-up menu bar
            this.pluginManager.startAllPlugins();
        } catch (PluginException ignored) {
            // even if some plugin wasn't started, it shouldn't prevent application
            // from loading because it may work without plugins
        }

        // migrate data between versions, if required
        executeMigrationTasks();

        // register completion providers and start monitoring for changes
        activateCompletionMonitoring();
    }

    private Logger setupLogging() {
        try {
            LogManager logManager = LogManager.getLogManager();
            Path configPath = DATA_DIR.resolve(LOG_CONFIG_FILE_NAME);
            if (Files.exists(configPath)) {
                String outputPath = LOGS_DIR.resolve(LOG_OUTPUT_FILE_NAME).toString();

                // JUL can't handle Windows-style paths properly
                if (SystemUtils.IS_OS_WINDOWS) {
                    outputPath = outputPath.replace("\\", "/");
                }

                List<String> configData = Files.readAllLines(configPath, StandardCharsets.UTF_8);
                configData.add("java.util.logging.FileHandler.pattern=" + outputPath);
                logManager.readConfiguration(
                        new ByteArrayInputStream(String.join("\n", configData).getBytes())
                );
            } else {
                // use console logger
                logManager.readConfiguration(getResourceAsStream(LOG_CONFIG_FALLBACK));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return Logger.getLogger(this.getClass().getName());
    }

    private void logEnvironmentInfo() {
        Logger log = this.logger;
        try {
            log.info("OS=" + System.getProperty("os.name"));
            log.info("OS arch=" + System.getProperty("os.arch"));
            Screen.getScreens().forEach(screen -> log.info(
                    "Screen: bounds=" + screen.getVisualBounds() + "; dpi=" + screen.getDpi()
            ));

            log.fine("Supported locales:");
            Locale[] locales = SimpleDateFormat.getAvailableLocales();
            for (Locale locale : locales) {
                log.fine(locale.toString() + " / " + locale.getDisplayName());
            }

            log.fine("Supported SSL/TLS ciphers:");
            SSLServerSocketFactory sslSocketFactory = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
            String[] ciphers = sslSocketFactory.getDefaultCipherSuites();
            for (String cipherSuite : ciphers) {
                log.fine(cipherSuite);
            }
        } catch (Throwable ignored) {}
    }

    // - JavaFX doesn't support system tray at all
    // - AWT system tray support is pretty outdated and it may not work in KDE and Gnome3+
    // - dorkbox.SystemTray library is the best option but it's not JDK11 compliant
    //   because it relies on JDK internal API (com.sun.*)
    // Don't hesitate to choose whatever you like :)
    private void createTrayIcon(Stage primaryStage) {
        boolean badTraySupport = SystemUtils.IS_OS_LINUX && xdgCurrentDesktopMatches("gnome", "kde");

        if (SystemTray.isSupported() && !badTraySupport) {
            Platform.setImplicitExit(false);
            primaryStage.setOnCloseRequest(t -> Platform.runLater(primaryStage::hide));

            PopupMenu trayMenu = new PopupMenu();

            MenuItem showItem = new MenuItem(Messages.get(MAIN_TRAY_OPEN));
            ActionListener showListener = e -> {
                if (primaryStage.isShowing()) {
                    Platform.runLater(primaryStage::toFront);
                } else {
                    Platform.runLater(primaryStage::show);
                }
            };
            showItem.addActionListener(showListener);
            trayMenu.add(showItem);

            MenuItem closeItem = new MenuItem(Messages.get(QUIT));
            ActionListener closeListener = e ->
                    Platform.runLater(() -> DefaultEventBus.getInstance().publish(new CloseEvent(exitCode)));
            closeItem.addActionListener(closeListener);
            trayMenu.add(closeItem);

            java.awt.Image trayImage = SwingFXUtils.fromFXImage(IconCache.get(ICON_APP), null);
            TrayIcon trayIcon = new TrayIcon(trayImage, APP_NAME, trayMenu);

            SystemTray tray = SystemTray.getSystemTray();
            try {
                tray.add(trayIcon);
            } catch (AWTException e) {
                logger.warning(ExceptionUtils.getStackTrace(e));
            }
        }
    }

    private void loadApplicationProperties() {
        try {
            Properties properties = new Properties();
            properties.load(new InputStreamReader(getResourceAsStream(APP_PROPS_PATH), StandardCharsets.UTF_8));
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

    private ApplicationPreferences loadApplicationPreferences(YAMLMapper yamlMapper) {
        if (Files.exists(ApplicationPreferences.CONFIG_PATH)) {
            // application can't work without preferences, don't make attempts to recover
            return ApplicationPreferences.load(yamlMapper);
        } else {
            ApplicationPreferences preferences = new ApplicationPreferences();
            preferences.setDirty();
            return preferences;
        }
    }

    private Vault loadKeyVault() {
        Security security = this.preferences.getSecurity();
        Path vaultFilePath = security.getVaultFilePath();

        Vault vault = new PKCS12Vault(vaultFilePath);
        if (!Files.exists(vaultFilePath)) {
            Key key = generateKey(DEFAULT_ENCRYPTION_ALG);

            // if vault file is deleted, create a new one and update password in security preferences
            security.setVaultPassword(SecuredData.fromString(
                    PasswordGenerator.random(16, ASCII_LOWER_UPPER_DIGITS)
            ));
            byte[] vaultPassword = security.getDerivedVaultPassword();

            vault.unlock(vaultPassword);
            vault.putKey(MASTER_KEY_ALIAS, vaultPassword, key);
            vault.save(vaultPassword);

            // vault password was updated
            this.preferences.setDirty();
        }

        return vault;
    }

    private void activateCompletionMonitoring() {
        FileCompletionMonitoringService completionMonitoringService =
                injector.getBean(FileCompletionMonitoringService.class);
        completionMonitoringService.registerAllProviders();
        completionMonitoringService.start();
    }

    @Deprecated
    private void executeMigrationTasks() {
        try {
            if (Files.exists(ApplicationPreferences.CONFIG_PATH_OLD)) {
                Files.delete(ApplicationPreferences.CONFIG_PATH_OLD);
            }
        } catch (IOException ignored) {}

        MigrationUtilsApiClient.migrateXmlConfigToYaml(this.injector);
        MigrationUtilsFileBuilder.migrateXmlConfigToYaml(this.injector);
    }

    private void createUserResources() {
        createDir(DATA_DIR);
        createDir(AUTOCOMPLETE_DIR);
        createDir(CONFIG_DIR);
        createDir(CACHE_DIR);
        createDir(PLUGINS_DIR);
    }
}