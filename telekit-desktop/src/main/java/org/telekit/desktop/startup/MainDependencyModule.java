package org.telekit.desktop.startup;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.telekit.base.CompletionRegistry;
import org.telekit.base.Env;
import org.telekit.base.desktop.Overlay;
import org.telekit.base.desktop.routing.Router;
import org.telekit.base.di.DependencyModule;
import org.telekit.base.di.Provides;
import org.telekit.base.plugin.internal.PluginManager;
import org.telekit.base.preferences.internal.ApplicationPreferences;
import org.telekit.base.preferences.internal.SecurityPreferences;
import org.telekit.base.preferences.internal.Vault;
import org.telekit.base.preferences.internal.VaultKeyProvider;
import org.telekit.base.service.EncryptionService;
import org.telekit.base.service.Encryptor;
import org.telekit.base.service.KeyProvider;
import org.telekit.base.service.impl.DefaultEncryptionService;
import org.telekit.base.util.Mappers;
import org.telekit.desktop.startup.config.*;
import org.telekit.desktop.views.MainStage;
import org.telekit.desktop.views.layout.OverlayBase;
import org.telekit.desktop.views.system.WelcomeView;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.telekit.base.preferences.internal.Vault.MASTER_KEY_ALIAS;

public final class MainDependencyModule implements DependencyModule {

    private final MainStage mainStage;
    private final PreferencesConfig preferencesConfig;
    private final SecurityConfig securityConfig;
    private final PluginConfig pluginConfig;
    private final ServicesConfig servicesConfig;

    public MainDependencyModule(MainStage mainStage,
                                PreferencesConfig preferencesConfig,
                                SecurityConfig securityConfig,
                                PluginConfig pluginConfig,
                                ServicesConfig servicesConfig) {
        this.mainStage = mainStage;
        this.preferencesConfig = Objects.requireNonNull(preferencesConfig);
        this.securityConfig = Objects.requireNonNull(securityConfig);
        this.pluginConfig = Objects.requireNonNull(pluginConfig);
        this.servicesConfig = Objects.requireNonNull(servicesConfig);
    }

    @Provides
    @Singleton
    public MainStage mainStage() { return mainStage; }

    @Provides
    @Singleton
    public ApplicationPreferences preferences() { return preferencesConfig.getPreferences(); }

    @Provides
    @Singleton
    public Vault vault() { return securityConfig.getVault(); }

    @Provides
    @Singleton
    public PluginManager pluginManager() { return pluginConfig.getPluginManager(); }

    @Provides
    @Singleton
    public Router router() {
        Router router = new Router();
        router.registerRoute(WelcomeView.ROUTE.getName(), WelcomeView.class);
        Config.getBuiltinTools().forEach(tool -> router.registerRoute(tool.id(), tool.getComponent()));
        return router;
    }

    @Provides
    @Singleton
    public Overlay overlay() {
        return new OverlayBase();
    }

    @Provides
    @Singleton
    @Named("masterEncryptionService")
    public EncryptionService masterEncryptionService() {
        SecurityPreferences securityPreferences = preferences().getSecurityPreferences();
        KeyProvider keyProvider = new VaultKeyProvider(vault(), securityPreferences, MASTER_KEY_ALIAS);
        Encryptor encryptor = Encryptor.createEncryptor(Env.DEFAULT_ENCRYPTION_ALG);
        return new DefaultEncryptionService(encryptor, keyProvider);
    }

    @Provides
    @Singleton
    public ExecutorService executorService() {
        return Executors.newCachedThreadPool();
    }

    @Provides
    @Singleton
    public CompletionRegistry completionRegistry() {
        return servicesConfig.getCompletionRegistry();
    }

    @Provides
    @Singleton
    public FileCompletionMonitoringService fileCompletionMonitoringService() {
        return servicesConfig.getCompletionMonitoringService();
    }

    @Provides
    @Singleton
    public XmlMapper xmlMapper() {
        return Mappers.createXmlMapper();
    }

    @Provides
    @Singleton
    public YAMLMapper yamlMapper() {
        return Mappers.createYamlMapper();
    }
}
