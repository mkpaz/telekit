package org.telekit.example;

import org.telekit.base.plugin.DependencyModule;
import org.telekit.base.plugin.Includes;
import org.telekit.base.plugin.Metadata;
import org.telekit.base.plugin.Plugin;
import org.telekit.example.service.ExampleDependencyModule;
import org.telekit.example.tools.DummyOneTool;
import org.telekit.example.tools.DummyTwoTool;
import org.telekit.example.tools.HelloTool;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Includes({
        HelloTool.class,
        DummyOneTool.class,
        DummyTwoTool.class
})
public class ExamplePlugin implements Plugin {

    public static final String ASSETS_PATH = "/org/telekit/example/assets/";
    public static final String I18N_MESSAGES_PATH = "org.telekit.example.i18n.messages";
    public static final String PLUGIN_PROPERTIES_FILE_NAME = "plugin.properties";
    public static final String SAMPLE_PROPERTIES_FILE_NAME = "sample.properties";

    private final Metadata metadata;

    public ExamplePlugin() throws Exception {
        metadata = new Metadata();

        Properties properties = new Properties();
        properties.load(new InputStreamReader(
                ExamplePlugin.class.getResourceAsStream(ASSETS_PATH + PLUGIN_PROPERTIES_FILE_NAME),
                StandardCharsets.UTF_8
        ));

        metadata.setName(properties.getProperty(METADATA_NAME));
        metadata.setAuthor(properties.getProperty(METADATA_AUTHOR));
        metadata.setVersion(properties.getProperty(METADATA_VERSION));
        metadata.setDescription(properties.getProperty(METADATA_DESCRIPTION));
        metadata.setHomePage(properties.getProperty(METADATA_HOMEPAGE));
        metadata.setPlatformVersion(properties.getProperty(METADATA_PLATFORM_VERSION));
    }

    @Override
    public Metadata getMetadata() {
        return metadata;
    }

    @Override
    public List<DependencyModule> getModules() {
        return Collections.singletonList(new ExampleDependencyModule());
    }

    @Override
    public ResourceBundle getBundle(Locale locale) {
        return ResourceBundle.getBundle(I18N_MESSAGES_PATH, locale, ExamplePlugin.class.getModule());
    }

    @Override
    public void start() {}

    @Override
    public void stop() {}
}
