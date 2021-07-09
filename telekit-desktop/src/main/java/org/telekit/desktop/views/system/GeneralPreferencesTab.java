package org.telekit.desktop.views.system;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.collections.FXCollections;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.util.StringConverter;
import org.telekit.base.net.connection.Scheme;
import org.telekit.base.preferences.internal.Language;
import org.telekit.base.preferences.internal.ManualProxy;
import org.telekit.controls.custom.RevealablePasswordField;
import org.telekit.controls.util.Containers;
import org.telekit.controls.util.Controls;
import org.telekit.controls.util.IntegerStringConverter;
import org.telekit.desktop.i18n.DesktopMessages;

import java.util.Arrays;

import static javafx.geometry.Pos.CENTER_LEFT;
import static org.telekit.base.i18n.I18n.t;
import static org.telekit.controls.i18n.ControlsMessages.EXAMPLE;
import static org.telekit.controls.i18n.ControlsMessages.PORT;
import static org.telekit.controls.util.BindUtils.bindToggleGroup;
import static org.telekit.controls.util.Containers.*;
import static org.telekit.controls.util.Controls.gridLabel;
import static org.telekit.desktop.i18n.DesktopMessages.*;
import static org.telekit.desktop.views.system.PreferencesView.RESTART_MARK;

public class GeneralPreferencesTab extends Tab {

    ComboBox<Language> langChoice;

    ToggleGroup proxyModeToggle;
    RadioButton noProxyToggle;
    RadioButton manualProxyToggle;

    ComboBox<Scheme> proxySchemeChoice;
    TextField proxyHostText;
    Spinner<Integer> proxyPortSpinner;
    TextField proxyUsernameText;
    RevealablePasswordField proxyPasswordText;
    TextArea proxyExceptionsText;

    private final PreferencesViewModel model;

    public GeneralPreferencesTab(PreferencesViewModel model) {
        this.model = model;

        createView();
    }

    private void createView() {
        langChoice = new ComboBox<>();
        langChoice.getItems().addAll(Language.values());
        langChoice.setConverter(new StringConverter<>() {
            @Override
            public String toString(Language lang) {
                return lang.getDisplayName();
            }

            @Override
            public Language fromString(String displayName) {
                return Arrays.stream(Language.values())
                        .filter(lang -> lang.getDisplayName().equals(displayName))
                        .findFirst()
                        .orElse(Language.EN);
            }
        });
        langChoice.valueProperty().bindBidirectional(model.languageProperty());

        // PROXY

        HBox proxyGroupHeader = Containers.create(HBox::new, "group-header");
        proxyGroupHeader.setAlignment(Pos.BASELINE_LEFT);
        proxyGroupHeader.getChildren().addAll(new Label(t(DesktopMessages.PREFERENCES_PROXY)), horizontalSeparator());

        proxyModeToggle = new ToggleGroup();

        noProxyToggle = new RadioButton(t(PREFERENCES_NO_PROXY));
        noProxyToggle.setToggleGroup(proxyModeToggle);
        noProxyToggle.setUserData(PreferencesViewModel.PROXY_DISABLED);

        manualProxyToggle = new RadioButton(t(PREFERENCES_MANUAL_PROXY_CONFIGURATION));
        manualProxyToggle.setToggleGroup(proxyModeToggle);
        manualProxyToggle.setPadding(new Insets(0, 0, 5, 0));
        manualProxyToggle.setUserData(PreferencesViewModel.PROXY_MANUAL);

        BooleanBinding disableManualSettingsBinding = Bindings.createBooleanBinding(
                () -> proxyModeToggle.getSelectedToggle() == noProxyToggle, proxyModeToggle.selectedToggleProperty()
        );
        bindToggleGroup(proxyModeToggle, model.proxyModeProperty());

        // MANUAL PROXY

        proxySchemeChoice = new ComboBox<>(FXCollections.observableArrayList(ManualProxy.SUPPORTED_SCHEMES));
        proxySchemeChoice.setPrefWidth(100);
        proxySchemeChoice.valueProperty().bindBidirectional(model.proxySchemeProperty());
        proxySchemeChoice.disableProperty().bind(disableManualSettingsBinding);

        proxyHostText = new TextField();
        HBox.setHgrow(proxyHostText, Priority.ALWAYS);
        proxyHostText.textProperty().bindBidirectional(model.proxyHostProperty());
        proxyHostText.disableProperty().bind(disableManualSettingsBinding);

        Label proxyPortLabel = new Label(t(PORT));
        proxyPortLabel.setPadding(new Insets(0, 5, 0, 5));

        proxyPortSpinner = new Spinner<>(0, 65535, 3128);
        proxyPortSpinner.setPrefWidth(100);
        proxyPortSpinner.setEditable(true);
        IntegerStringConverter.createFor(proxyPortSpinner);
        proxyPortSpinner.getValueFactory().valueProperty().bindBidirectional(model.proxyPortProperty());
        proxyPortSpinner.disableProperty().bind(disableManualSettingsBinding);

        HBox proxyUrlBox = Containers.hbox(0, CENTER_LEFT, Insets.EMPTY);
        proxyUrlBox.getChildren().setAll(
                proxySchemeChoice,
                proxyHostText,
                proxyPortLabel,
                proxyPortSpinner
        );

        proxyUsernameText = new TextField();
        proxyUsernameText.textProperty().bindBidirectional(model.proxyUsernameProperty());
        proxyUsernameText.disableProperty().bind(disableManualSettingsBinding);

        proxyPasswordText = Controls.passwordField();
        proxyPasswordText.textProperty().bindBidirectional(model.proxyPasswordProperty());
        proxyPasswordText.disableProperty().bind(disableManualSettingsBinding);

        proxyExceptionsText = Controls.create(TextArea::new, "monospace");
        proxyExceptionsText.setWrapText(true);
        proxyExceptionsText.setPrefHeight(50);
        proxyExceptionsText.textProperty().bindBidirectional(model.proxyExceptionsProperty());
        proxyExceptionsText.disableProperty().bind(disableManualSettingsBinding);

        Label proxyExceptionsExample = new Label(t(EXAMPLE) + ": *.domain.com, 192.168.*");

        // GRID

        GridPane grid = gridPane(20, 10, new Insets(10), "grid");

        grid.add(gridLabel(RESTART_MARK + t(DesktopMessages.LANGUAGE), HPos.RIGHT, langChoice), 0, 0, 2, 1);
        grid.add(langChoice, 2, 0);

        grid.add(proxyGroupHeader, 0, 1, GridPane.REMAINING, 1);

        grid.add(noProxyToggle, 1, 2, GridPane.REMAINING, 1);
        grid.add(manualProxyToggle, 1, 3, GridPane.REMAINING, 1);

        grid.add(gridLabel("URL", HPos.RIGHT, proxyHostText), 1, 4);
        grid.add(proxyUrlBox, 2, 4);

        grid.add(gridLabel(t(DesktopMessages.USERNAME), HPos.RIGHT, proxyUsernameText), 1, 5);
        grid.add(proxyUsernameText, 2, 5);

        grid.add(gridLabel(t(DesktopMessages.PASSWORD), HPos.RIGHT, proxyPasswordText), 1, 6);
        grid.add(proxyPasswordText.getParent(), 2, 6);

        grid.add(gridLabel(t(TOOLS_EXCEPTIONS), HPos.RIGHT, proxyExceptionsText), 1, 7);
        grid.add(proxyExceptionsText, 2, 7);

        grid.add(proxyExceptionsExample, 2, 8);

        grid.getColumnConstraints().addAll(
                columnConstraints(10, Priority.NEVER),  // imitates padding for nested properties
                columnConstraints(80, Priority.NEVER),  // property name
                columnConstraints(Priority.ALWAYS)              // property value
        );

        setText(t(DesktopMessages.PREFERENCES_GENERAL));
        setContent(grid);
    }
}
