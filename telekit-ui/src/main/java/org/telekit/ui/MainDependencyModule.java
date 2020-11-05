package org.telekit.ui;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.telekit.base.Env;
import org.telekit.base.feather.Provides;
import org.telekit.base.plugin.DependencyModule;
import org.telekit.base.plugin.internal.PluginManager;
import org.telekit.base.preferences.ApplicationPreferences;
import org.telekit.base.preferences.Security;
import org.telekit.base.service.EncryptionService;
import org.telekit.base.service.Encryptor;
import org.telekit.base.service.KeyProvider;
import org.telekit.base.preferences.Vault;
import org.telekit.base.service.impl.DefaultEncryptionService;
import org.telekit.base.preferences.VaultKeyProvider;
import org.telekit.base.util.Mappers;

import javax.inject.Named;
import javax.inject.Singleton;

import static org.telekit.base.preferences.Vault.MASTER_KEY_ALIAS;

public class MainDependencyModule implements DependencyModule {

    private final PluginManager pluginManager;
    private final ApplicationPreferences preferences;
    private final Vault vault;

    public MainDependencyModule(ApplicationPreferences preferences,
                                PluginManager pluginManager,
                                Vault vault) {
        this.pluginManager = pluginManager;
        this.preferences = preferences;
        this.vault = vault;
    }

    @Provides
    @Singleton
    public ApplicationPreferences applicationPreferences() {
        return preferences;
    }

    @Provides
    @Singleton
    public Vault vault() {
        return vault;
    }

    @Provides
    @Singleton
    public PluginManager pluginManager() {
        return pluginManager;
    }

    @Provides
    @Singleton
    @Named("masterEncryptionService")
    public EncryptionService masterEncryptionService() {
        Security securityPrefs = preferences.getSecurity();
        KeyProvider keyProvider = new VaultKeyProvider(vault, securityPrefs, MASTER_KEY_ALIAS);
        Encryptor encryptor = Encryptor.createEncryptor(Env.DEFAULT_ENCRYPTION_ALG);
        return new DefaultEncryptionService(encryptor, keyProvider);
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
