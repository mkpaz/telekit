package org.telekit.ui.tools.apiclient;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.*;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TableView.TableViewSelectionModel;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.input.*;
import javafx.scene.layout.GridPane;
import javafx.util.converter.DefaultStringConverter;
import org.telekit.base.CompletionRegistry;
import org.telekit.base.domain.KeyValue;
import org.telekit.base.domain.LineSeparator;
import org.telekit.base.domain.UsernamePasswordCredential;
import org.telekit.base.domain.exception.TelekitException;
import org.telekit.base.event.*;
import org.telekit.base.i18n.Messages;
import org.telekit.base.preferences.ApplicationPreferences;
import org.telekit.base.service.CompletionProvider;
import org.telekit.base.service.impl.KeyValueCompletionProvider;
import org.telekit.base.ui.Controller;
import org.telekit.base.ui.Dimension;
import org.telekit.base.ui.IconCache;
import org.telekit.base.ui.UILoader;
import org.telekit.base.util.DesktopUtils;
import org.telekit.base.util.FileUtils;
import org.telekit.base.util.TextBuilder;
import org.telekit.controls.components.RevealablePasswordField;
import org.telekit.controls.components.dialogs.Dialogs;
import org.telekit.controls.glyphs.FontAwesomeIcon;
import org.telekit.controls.util.BooleanBindings;
import org.telekit.controls.views.FilterTable;
import org.telekit.ui.domain.ApplicationEvent;
import org.telekit.ui.domain.ExceptionCaughtEvent;
import org.telekit.ui.domain.FXMLView;
import org.telekit.ui.tools.Action;
import org.telekit.ui.tools.SubmitActionEvent;
import org.telekit.ui.tools.common.Param;
import org.telekit.ui.tools.common.ParamIndicatorTableCell;
import org.telekit.ui.tools.common.ParamModalController;
import org.telekit.ui.tools.common.ParamValueTableCell;

import javax.inject.Inject;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.*;
import java.util.function.Predicate;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.trim;
import static org.telekit.base.Env.TEMP_DIR;
import static org.telekit.base.net.HttpConstants.AuthScheme;
import static org.telekit.base.ui.IconCache.ICON_APP;
import static org.telekit.base.util.CSVUtils.*;
import static org.telekit.base.util.CollectionUtils.isNotEmpty;
import static org.telekit.base.util.TextUtils.countNotBlankLines;
import static org.telekit.controls.util.BindUtils.rebind;
import static org.telekit.controls.util.BooleanBindings.isBlank;
import static org.telekit.ui.MessageKeys.*;
import static org.telekit.ui.domain.ApplicationEvent.Type.COMPLETION_REGISTRY_UPDATED;
import static org.telekit.ui.tools.Action.NEW;
import static org.telekit.ui.tools.apiclient.Executor.validate;
import static org.telekit.ui.tools.common.Controllers.paramCompletionController;

public class RootController extends Controller {

    private static final String PREVIEW_FILE_NAME = "api-client.preview.html";

    public @FXML GridPane rootPane;
    public @FXML TabPane tpaneRight;

    // template
    public @FXML ComboBox<Template> cmbTemplate;
    public @FXML MenuItem itemPreviewTemplate;
    public @FXML MenuItem itemNewTemplate;
    public @FXML MenuItem itemEditTemplate;
    public @FXML MenuItem itemDuplicateTemplate;
    public @FXML MenuItem itemDeleteTemplate;
    public @FXML MenuItem itemImportTemplate;
    public @FXML MenuItem itemExportTemplate;

    // auth
    public @FXML ComboBox<AuthScheme> cmbAuthType;
    public @FXML TextField tfUsername;
    public @FXML RevealablePasswordField pfPassword;
    public @FXML FontAwesomeIcon toggleRevealPassword;

    // params
    public @FXML TableView<Param> tblParams;
    public @FXML TableColumn<Param, Image> colParamIndicator;
    public @FXML TableColumn<Param, String> colParamName;
    public @FXML TableColumn<Param, String> colParamValue;
    public @FXML MenuItem itemAddParam;
    public @FXML MenuItem itemRemoveParam;
    public @FXML MenuItem itemParamCompletion;

    // replacement list
    public @FXML TextArea taCsv;
    public @FXML Label lbCsvLineCount;

    // control
    public @FXML Button btnStart;
    public @FXML Button btnStop;
    public @FXML Spinner<Integer> spnTimeout;

    // log
    public @FXML TableView<CompletedRequest> tblLog;
    public @FXML TableColumn<CompletedRequest, Integer> colLogIndex;
    public @FXML TableColumn<CompletedRequest, String> colLogStatus;
    public @FXML TableColumn<CompletedRequest, String> colLogRequestLine;
    public @FXML TextArea taRequestDetails;
    public @FXML CheckBox cbDisplayErrorsOnly;
    public @FXML Button btnExportLog;

    private final ApplicationPreferences preferences;
    private final YAMLMapper yamlMapper;
    private final CompletionRegistry completionRegistry;

    private final ObservableList<Template> templates = FXCollections.observableArrayList();
    private final BooleanBinding noTemplatesProperty = Bindings.isEmpty(templates);
    private final SimpleBooleanProperty ongoingProperty = new SimpleBooleanProperty(false);

    private TemplateRepository templateRepository;
    private Executor executor;
    private TemplateModalController templateController = null;
    private ParamModalController paramController = null;
    private FilterTable<KeyValue<String, String>> paramCompletionController = null;

    @Inject
    public RootController(ApplicationPreferences preferences,
                          YAMLMapper yamlMapper,
                          CompletionRegistry completionRegistry) {
        this.preferences = preferences;
        this.yamlMapper = yamlMapper;
        this.completionRegistry = completionRegistry;
    }

    @FXML
    public void initialize() {
        cmbTemplate.setButtonCell(new TemplateListCell());
        cmbTemplate.setCellFactory(property -> new TemplateListCell());
        cmbTemplate.setItems(templates);
        taCsv.focusedProperty().addListener((obs, oldVal, newVal) -> { if (!newVal) countCsvLines(); });

        cmbAuthType.setItems(FXCollections.observableArrayList(AuthScheme.BASIC));
        cmbAuthType.getSelectionModel().select(AuthScheme.BASIC);
        // TODO: Replace with custom ToggleIcon component
        pfPassword.revealPasswordProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal) {
                toggleRevealPassword.setGlyphName("EYE");
            } else {
                toggleRevealPassword.setGlyphName("EYE_SLASH");
            }
        });

        initTemplatesMenu();
        initParamsTable();
        initLogTab();
        initControlButtons();

        // load data
        templateRepository = new TemplateRepository(yamlMapper);
        templateRepository.reloadAll();
        reloadTemplates(null);
    }

    private void initTemplatesMenu() {
        itemPreviewTemplate.setUserData(Action.PREVIEW);
        itemNewTemplate.setUserData(Action.NEW);
        itemEditTemplate.setUserData(Action.EDIT);
        itemDuplicateTemplate.setUserData(Action.DUPLICATE);
        itemDeleteTemplate.setUserData(Action.DELETE);
        itemImportTemplate.setUserData(Action.IMPORT);
        itemExportTemplate.setUserData(Action.EXPORT);

        itemPreviewTemplate.disableProperty().bind(noTemplatesProperty);
        itemEditTemplate.disableProperty().bind(noTemplatesProperty);
        itemDuplicateTemplate.disableProperty().bind(noTemplatesProperty);
        itemDeleteTemplate.disableProperty().bind(noTemplatesProperty);
        itemExportTemplate.disableProperty().bind(noTemplatesProperty);
    }

    private void initParamsTable() {
        final TableViewSelectionModel<Param> selectionModel = tblParams.getSelectionModel();
        tblParams.getSortOrder().add(colParamName);

        colParamIndicator.setCellFactory(cell -> new ParamIndicatorTableCell(completionRegistry));
        colParamName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colParamValue.setCellValueFactory(new PropertyValueFactory<>("value"));
        colParamValue.setCellFactory(t -> new ParamValueTableCell(new DefaultStringConverter()));
        colParamValue.setOnEditCommit(event -> event.getTableView()
                .getItems()
                .get(event.getTablePosition().getRow())
                .setValue(event.getNewValue())
        );

        itemAddParam.disableProperty().bind(cmbTemplate.valueProperty().isNull());
        itemRemoveParam.disableProperty().bind(Bindings.isEmpty(selectionModel.getSelectedItems()));
        selectionModel.selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> itemParamCompletion.setVisible(Param.allowsCompletion(newVal, completionRegistry))
        );

        DefaultEventBus.getInstance().subscribe(ApplicationEvent.class, event -> {
            if (COMPLETION_REGISTRY_UPDATED.isSameTypeAs(event)) tblParams.refresh();
        });
    }

    private void initLogTab() {
        final TableViewSelectionModel<CompletedRequest> selectionModel = tblLog.getSelectionModel();

        tblLog.getSortOrder().add(colLogIndex);
        tblLog.setRowFactory(tv -> new LogTableRow());
        selectionModel.setSelectionMode(SelectionMode.MULTIPLE);
        selectionModel.selectedItemProperty().addListener((obs, oldVal, newVal) -> displayRequestDetails(newVal));

        colLogIndex.setCellValueFactory(new PropertyValueFactory<>("processedRange"));
        colLogStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colLogRequestLine.setCellValueFactory(new PropertyValueFactory<>("userData"));
        tblLog.setOnKeyPressed(event -> {
            if (new KeyCodeCombination(KeyCode.C, KeyCombination.CONTROL_ANY).match(event)) {
                copyLogsTableToClipboard();
            }
        });

        cbDisplayErrorsOnly.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && tblLog.getItems() instanceof FilteredList) {
                FilteredList<CompletedRequest> items = (FilteredList<CompletedRequest>) tblLog.getItems();
                items.setPredicate(createLogPredicate(newVal));
            }
        });
    }

    private void initControlButtons() {
        btnStart.disableProperty().bind(BooleanBindings.or(
                cmbTemplate.valueProperty().isNull(),
                isBlank(taCsv.textProperty()),
                ongoingProperty
        ));
        btnStop.disableProperty().bind(Bindings.not(ongoingProperty));
        btnExportLog.disableProperty().bind(BooleanBindings.or(
                Bindings.isEmpty(tblLog.getItems()),
                ongoingProperty
        ));
    }

    private void copyLogsTableToClipboard() {
        List<CompletedRequest> selectedRows = tblLog.getSelectionModel().getSelectedItems();
        if (selectedRows == null || selectedRows.isEmpty()) return;

        StringBuilder sb = new StringBuilder();
        String separator = " | ";
        for (CompletedRequest row : selectedRows) {
            sb.append(row.getProcessedRange())
                    .append(separator)
                    .append(row.getStatusCode())
                    .append(separator)
                    .append(row.getUserData())
                    .append(LineSeparator.UNIX.getCharacters());
        }

        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(sb.toString());
        clipboard.setContent(content);
    }

    private void displayRequestDetails(CompletedRequest request) {
        if (request != null) taRequestDetails.setText(request.print());
    }

    private Predicate<CompletedRequest> createLogPredicate(boolean errorsOnly) {
        return request -> !errorsOnly || !request.isSucceeded();
    }

    private void countCsvLines() {
        // WARNING: avoid binding counting method to the observable property.
        // If text area size is big enough, it will lead to extensive memory usage on
        // multiple subsequent edits.
        int count = countNotBlankLines(taCsv.getText().trim());
        lbCsvLineCount.setText(String.valueOf(count));
    }

    private void reloadTemplates(Template selectedTemplate) {
        List<Template> loadedTemplates = templateRepository.getAll();
        loadedTemplates.sort(Template::compareTo);
        templates.setAll(loadedTemplates); // even if list is empty it have to be set
        if (templates.isEmpty()) return;

        int selectedIndex = selectedTemplate != null ? templates.indexOf(selectedTemplate) : 0;
        cmbTemplate.getSelectionModel().select(selectedIndex);
        displaySelectedTemplateParams();
    }

    @FXML
    public void displaySelectedTemplateParams() {
        Template selectedTemplate = cmbTemplate.getSelectionModel().getSelectedItem();
        List<Param> params = new ArrayList<>();
        if (selectedTemplate != null && isNotEmpty(selectedTemplate.getParams())) {
            params.addAll(selectedTemplate.getParams());
            params.sort(Param.COMPARATOR);
        }
        tblParams.getItems().setAll(params);
        tblParams.getSelectionModel().selectFirst();
    }

    @FXML
    public void handleTemplateAction(ActionEvent event) {
        MenuItem source = (MenuItem) event.getSource();
        if (!(source.getUserData() instanceof Action)) return;
        Action action = (Action) source.getUserData();
        Template selectedTemplate = cmbTemplate.getSelectionModel().getSelectedItem();

        switch (action) {
            case NEW:
            case DUPLICATE:
            case EDIT:
                TemplateModalController controller = getOrCreateTemplateDialog();
                controller.setData(
                        action,
                        action != NEW ? selectedTemplate : null,
                        templateRepository.getNames()
                );
                Dialogs.showAndWait(controller);
                break;
            case DELETE:
                if (selectedTemplate != null) deleteTemplate(selectedTemplate);
                break;
            case PREVIEW:
                showPreview();
                break;
            case IMPORT:
                importTemplate();
                break;
            case EXPORT:
                exportTemplate(selectedTemplate);
                break;
        }
    }

    private TemplateModalController getOrCreateTemplateDialog() {
        if (templateController != null) return templateController;

        Controller controller = UILoader.load(FXMLView.API_CLIENT_TEMPLATE.getLocation(), Messages.getInstance());
        controller.subscribe(SubmitActionEvent.class, this::updateTemplate);
        controller.subscribe(CancelEvent.class, event -> Dialogs.hide(templateController));

        Dialogs.modal(controller.getParent(), rootPane.getScene().getWindow())
                .icon(IconCache.get(ICON_APP))
                .resizable(false)
                .build();

        templateController = (TemplateModalController) controller;
        return templateController;
    }

    private void deleteTemplate(Template template) {
        Alert dialog = Dialogs.confirm()
                .title(Messages.get(CONFIRMATION))
                .content(Messages.get(TOOLS_MSG_DELETE_TEMPLATE, template.getName()))
                .owner(rootPane.getScene().getWindow())
                .build();
        Optional<ButtonType> confirmation = dialog.showAndWait();

        confirmation.ifPresent(buttonType -> {
            if (buttonType != ButtonType.OK) return;
            templateRepository.beginTransaction(false).rollbackOnException(() -> {
                templateRepository.removeById(template.getId());
                templateRepository.saveAll();
                reloadTemplates(null);
            });
        });
    }

    public void showPreview() {
        Template selectedTemplate = cmbTemplate.getSelectionModel().getSelectedItem();
        if (selectedTemplate == null || !DesktopUtils.isSupported(Desktop.Action.BROWSE)) return;

        File outputFile = TEMP_DIR.resolve(PREVIEW_FILE_NAME).toFile();
        String html = PreviewRenderer.render(selectedTemplate);
        try {
            Files.writeString(outputFile.toPath(), html);
            DesktopUtils.browse(outputFile.toURI());
        } catch (IOException e) {
            throw new TelekitException(Messages.get(MSG_GENERIC_IO_ERROR), e);
        }
    }

    private void importTemplate() {
        File inputFile = Dialogs.fileChooser()
                .addFilter(Messages.get(FILE_DIALOG_YAML), "*.yaml", "*.yml")
                .build()
                .showOpenDialog(rootPane.getScene().getWindow());
        if (inputFile == null) return;

        templateRepository.beginTransaction(false).rollbackOnException(() -> {
            templateRepository.importFromFile(inputFile);
            templateRepository.saveAll();
            reloadTemplates(null);
        });
    }

    private void exportTemplate(Template template) {
        File outputFile = Dialogs.fileChooser()
                .addFilter(Messages.get(FILE_DIALOG_YAML), "*.yaml", "*.yml")
                .initialFileName(FileUtils.sanitizeFileName(template.getName()) + ".yaml")
                .build()
                .showSaveDialog(rootPane.getScene().getWindow());
        if (outputFile == null) return;

        templateRepository.exportToFile(List.of(template), outputFile);
    }

    @Listener
    private void updateTemplate(SubmitActionEvent<Template> event) {
        Template selectedTemplate = cmbTemplate.getSelectionModel().getSelectedItem();
        Template updatedTemplate = Objects.requireNonNull(event.getData());

        switch (event.getAction()) {
            case NEW, DUPLICATE -> {
                templateRepository.beginTransaction(false).rollbackOnException(() -> {
                    templateRepository.add(updatedTemplate);
                    templateRepository.saveAll();
                });
                selectedTemplate = updatedTemplate;
            }
            case EDIT -> templateRepository.beginTransaction(updatedTemplate).rollbackOnException(() -> {
                templateRepository.update(updatedTemplate);
                templateRepository.saveAll();
            });
        }

        Dialogs.hide(templateController);
        reloadTemplates(selectedTemplate);
    }

    @FXML
    public void showParamDialog() {
        ParamModalController controller = getOrCreateParamDialog();
        Set<String> usedParamNames = new HashSet<>();
        if (tblParams.getItems() != null) {
            tblParams.getItems().forEach(param -> usedParamNames.add(param.getName()));
        }
        controller.setData(usedParamNames);
        Dialogs.showAndWait(controller);
    }

    private ParamModalController getOrCreateParamDialog() {
        if (paramController != null) {
            paramController.reset();
            return paramController;
        }

        ParamModalController controller = ParamModalController.create(rootPane.getScene().getWindow());
        controller.subscribe(SubmitEvent.class, this::addParam);
        controller.subscribe(CancelEvent.class, event -> Dialogs.hide(paramController));

        paramController = controller;
        return controller;
    }

    private void addParam(SubmitEvent<Param> event) {
        Template selectedTemplate = cmbTemplate.getSelectionModel().getSelectedItem();
        if (selectedTemplate == null || event.getData() == null) return;

        Template updatedTemplate = selectedTemplate.deepCopy();
        updatedTemplate.addParam(event.getData());
        templateRepository.update(updatedTemplate);
        templateRepository.beginTransaction(selectedTemplate)
                .rollbackOnException(() -> templateRepository.saveAll());

        Dialogs.hide(paramController);
        reloadTemplates(updatedTemplate);
    }

    @FXML
    public void removeParam() {
        Template selectedTemplate = cmbTemplate.getSelectionModel().getSelectedItem();
        Param selectedParam = tblParams.getSelectionModel().getSelectedItem();
        if (selectedTemplate == null || selectedParam == null) return;

        Template updatedTemplate = selectedTemplate.deepCopy();
        updatedTemplate.removeParam(selectedParam);
        templateRepository.update(updatedTemplate);
        templateRepository.beginTransaction(selectedTemplate)
                .rollbackOnException(() -> templateRepository.saveAll());

        reloadTemplates(updatedTemplate);
    }

    @FXML
    public void showParamCompletions(ActionEvent event) {
        Param selectedParam = tblParams.getSelectionModel().getSelectedItem();
        if (selectedParam == null) return;

        CompletionProvider<?> provider = completionRegistry.getProviderFor(selectedParam.getName()).orElse(null);
        if (!(provider instanceof KeyValueCompletionProvider)) return;

        FilterTable<KeyValue<String, String>> controller = getOrCreateCompletionDialog();
        List<KeyValue<String, String>> data = new ArrayList<>(((KeyValueCompletionProvider) provider).find(s -> true));
        controller.setData(data);
        Dialogs.showAndWait(controller);
    }

    private FilterTable<KeyValue<String, String>> getOrCreateCompletionDialog() {
        if (paramCompletionController != null) {
            paramCompletionController.reset();
            return paramCompletionController;
        }

        FilterTable<KeyValue<String, String>> controller = paramCompletionController();
        Dialogs.modal(controller.getParent(), rootPane.getScene().getWindow())
                .title(Messages.get(TOOLS_CHOOSE_VALUE))
                .icon(IconCache.get(ICON_APP))
                .preferredSize(Dimension.of(480, 400))
                .resizable(false)
                .build();
        controller.subscribe(SubmitEvent.class, this::setParamValue);
        controller.subscribe(CancelEvent.class, event -> Dialogs.hide(paramCompletionController));

        this.paramCompletionController = controller;
        return controller;
    }

    private void setParamValue(SubmitEvent<KeyValue<String, String>> event) {
        KeyValue<String, String> kv = event.getData();
        if (kv != null) {
            tblParams.getSelectionModel().getSelectedItem().setValue(kv.getValue());
            tblParams.refresh();
        }
        Dialogs.hide(paramCompletionController);
    }

    @FXML
    public void startHTTPClient() {
        // protect from multiple concurrent starts
        if (ongoingProperty.get()) return;

        // replacement source data
        Template template = cmbTemplate.getSelectionModel().getSelectedItem();
        String text = taCsv.getText();
        if (template == null || isBlank(text)) return;
        String[][] csv = textToTable(text, COMMA_OR_SEMICOLON);

        // validate
        boolean inputValid = validateInputData(template, csv);
        if (!inputValid) return;

        // task
        executor = new Executor(template, csv);
        executor.setTimeoutBetweenRequests(spnTimeout.getValue());
        executor.setProxy(preferences.getProxy());

        String username = trim(tfUsername.getText());
        String password = trim(pfPassword.getText());
        if (isNotBlank(username) && isNotBlank(password)) {
            executor.setPasswordBasedAuth(AuthScheme.BASIC, UsernamePasswordCredential.of(username, password));
        }

        final ObservableList<CompletedRequest> result = executor.getPartialResults();
        final FilteredList<CompletedRequest> filteredResult = new FilteredList<>(result);
        filteredResult.setPredicate(createLogPredicate(cbDisplayErrorsOnly.isSelected()));
        tblLog.setItems(filteredResult);
        taRequestDetails.setText("");

        executor.setOnSucceeded(event -> {
            toggleProgressIndicator(false);
            Platform.runLater(() -> reportTaskDone(Messages.get(MSG_TASK_COMPLETED), executor.getPartialResults()));
        });
        executor.setOnCancelled(event -> {
            toggleProgressIndicator(false);
            Platform.runLater(() -> reportTaskDone(Messages.get(MSG_TASK_CANCELED), executor.getPartialResults()));
        });
        executor.setOnFailed(event -> {
            toggleProgressIndicator(false);
            Throwable exception = event.getSource().getException();
            if (exception != null) {
                DefaultEventBus.getInstance().publish(new ExceptionCaughtEvent(exception));
            }
        });

        tpaneRight.getSelectionModel().selectLast();
        rebind(ongoingProperty, executor.runningProperty());
        toggleProgressIndicator(true);

        new Thread(executor).start();
    }

    private void toggleProgressIndicator(boolean on) {
        DefaultEventBus.getInstance().publish(new ProgressIndicatorEvent(id, on));
    }

    private void reportTaskDone(String message, List<CompletedRequest> result) {
        int successCount = (int) result.stream().filter(CompletedRequest::isSucceeded).count();

        Alert dialog = Dialogs.info()
                .title(Messages.get(INFO))
                .content("")
                .owner(rootPane.getScene().getWindow())
                .build();

        TextBuilder text = new TextBuilder();
        text.appendLine(message);
        text.newLine();
        text.appendLine(Messages.get(STATUS), ":");
        text.appendLine(Messages.get(TOOLS_APICLIENT_TASK_REPORT, result.size(), successCount, result.size() - successCount));

        Label label = new Label(text.toString());
        label.setWrapText(true);
        dialog.getDialogPane().setContent(label);
        dialog.getDialogPane().setMinWidth(300);

        dialog.showAndWait();
    }

    @FXML
    public void stopHTTPClient() {
        if (executor != null) executor.cancel();
    }

    private boolean validateInputData(Template template, String[][] csv) {
        List<String> warnings = validate(template, csv);
        if (warnings.isEmpty()) return true;

        Alert dialog = Dialogs.confirm()
                .title(Messages.get(WARNING))
                .owner(rootPane.getScene().getWindow())
                .content("")
                .build();

        TextBuilder text = new TextBuilder();
        text.appendLine(Messages.get(TOOLS_MSG_VALIDATION_HEAD));
        text.newLine();
        text.appendLines(warnings);
        text.newLine();
        text.append(Messages.get(TOOLS_MSG_VALIDATION_TAIL));

        Label label = new Label(text.toString());
        label.setWrapText(true);
        dialog.getDialogPane().setContent(label);

        Optional<ButtonType> confirmation = dialog.showAndWait();
        return confirmation.isPresent() && confirmation.get() == ButtonType.OK;
    }

    @FXML
    public void exportLogFile() {
        File outputFile = Dialogs.fileChooser()
                .addFilter(Messages.get(FILE_DIALOG_TEXT), "*.txt")
                .initialFileName(FileUtils.sanitizeFileName("api-client-log.txt"))
                .build()
                .showSaveDialog(rootPane.getScene().getWindow());
        if (outputFile == null) return;

        List<CompletedRequest> log = tblLog.getItems();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
        final String separator = " | ";
        final String eol = LineSeparator.UNIX.getCharacters();

        try (FileOutputStream outputStream = new FileOutputStream(outputFile);
             OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
             BufferedWriter out = new BufferedWriter(writer)) {

            for (CompletedRequest request : log) {
                out.write(request.getDateTime().format(formatter));
                out.write(separator);
                out.write(String.valueOf(request.getStatusCode()));
                out.write(separator);
                out.write(request.getUserData());
                out.write(eol);
                out.write(eol);
                out.write(request.print().replaceAll("(?m)^", "\t"));
                out.write(eol);
                out.write(eol);
            }
        } catch (Exception e) {
            throw new TelekitException(Messages.get(MGG_UNABLE_TO_SAVE_DATA_TO_FILE), e);
        }
    }

    @FXML
    public void pasteFromExcel() {
        String clipboardText = Clipboard.getSystemClipboard().getString();
        if (isBlank(clipboardText)) return;

        String newText = trim(clipboardText.replaceAll("\t", ","));
        taCsv.replaceText(0, taCsv.getText().length(), newText);
    }

    @FXML
    public void pasteAsColumns() {
        String clipboardText = trim(Clipboard.getSystemClipboard().getString());
        if (isBlank(clipboardText)) return;

        int origLen = taCsv.getText().length();
        String origText = trim(taCsv.getText());

        String newText = addColumnsTheRight(origText, clipboardText, COMMA_OR_SEMICOLON);
        taCsv.replaceText(0, origLen, newText);
    }

    @FXML
    public void togglePassword() {
        boolean state = pfPassword.revealPasswordProperty().get();
        pfPassword.revealPasswordProperty().set(!state);
    }

    public static class TemplateListCell extends ListCell<Template> {

        @Override
        protected void updateItem(Template template, boolean empty) {
            super.updateItem(template, empty);

            if (template != null) {
                setText(template.getName());
            } else {
                setText(null);
            }
        }
    }

    public static class LogTableRow extends TableRow<CompletedRequest> {

        private static final PseudoClass REQUEST_FAILED = PseudoClass.getPseudoClass("failed");
        private static final PseudoClass REQUEST_FORWARDED = PseudoClass.getPseudoClass("forwarded");

        @Override
        protected void updateItem(CompletedRequest request, boolean empty) {
            super.updateItem(request, empty);
            pseudoClassStateChanged(REQUEST_FAILED, !empty && (request.isFailed() || !request.isResponded()));
            pseudoClassStateChanged(REQUEST_FORWARDED, !empty && request.isForwarded());
        }
    }
}
