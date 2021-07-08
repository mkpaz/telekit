package org.telekit.desktop.views.system;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.telekit.base.desktop.mvvm.View;
import org.telekit.base.di.Initializable;
import org.telekit.controls.util.Containers;
import org.telekit.controls.util.Controls;
import org.telekit.controls.widgets.OverlayDialog;
import org.telekit.desktop.i18n.DesktopMessages;

import javax.inject.Inject;
import javax.inject.Singleton;

import static org.telekit.base.i18n.I18n.t;
import static org.telekit.controls.i18n.ControlsMessages.*;

@Singleton
public class PreferencesView extends OverlayDialog implements Initializable, View<PreferencesViewModel> {

    static final String RESTART_MARK = "* ";

    GeneralPreferencesTab commonTab;
    PluginPreferencesTab pluginTab;

    TabPane tabs;
    Button commitBtn;

    private final PreferencesViewModel model;

    @Inject
    public PreferencesView(PreferencesViewModel model) {
        super();

        this.model = model;
        createContent();
    }

    private void createContent() {
        commonTab = new GeneralPreferencesTab(model);
        pluginTab = new PluginPreferencesTab(this, model);

        tabs = Containers.stretchedTabPane(commonTab, pluginTab);
        tabs.setId("preferences");
        VBox.setVgrow(tabs, Priority.ALWAYS);

        commitBtn = Controls.create(() -> new Button(t(ACTION_OK)), "form-action");
        commitBtn.setDefaultButton(true);

        footerBox.getChildren().add(0,
                new Label(RESTART_MARK + " - " + t(DesktopMessages.PREFERENCES_REQUIRES_RESTART))
        );
        footerBox.getChildren().add(2, commitBtn);
        bottomCloseBtn.setText(t(ACTION_CANCEL));

        setContent(tabs);
        setTitle(t(PREFERENCES));
        setPrefWidth(600);
        setPrefHeight(500);
    }

    @Override
    public void initialize() {
        commitBtn.setOnAction(e -> {
            model.commitCommand().execute();
            close();
        });
    }

    @Override
    public Region getRoot() {
        return this;
    }

    @Override
    public void reset() {}

    @Override
    public PreferencesViewModel getViewModel() {
        return model;
    }
}
