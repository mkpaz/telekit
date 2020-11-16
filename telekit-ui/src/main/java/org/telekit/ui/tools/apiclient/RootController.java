package org.telekit.ui.tools.apiclient;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import fontawesomefx.fa.FontAwesomeIconView;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ReadOnlyBooleanProperty;
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
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import javafx.util.converter.DefaultStringConverter;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.telekit.base.EventBus;
import org.telekit.base.EventBus.Listener;
import org.telekit.base.domain.AuthPrincipal;
import org.telekit.base.domain.LineSeparator;
import org.telekit.base.domain.ProgressIndicatorEvent;
import org.telekit.base.domain.TelekitException;
import org.telekit.base.i18n.Messages;
import org.telekit.base.preferences.ApplicationPreferences;
import org.telekit.base.ui.Controller;
import org.telekit.base.ui.Dialogs;
import org.telekit.base.ui.IconCache;
import org.telekit.base.ui.UILoader;
import org.telekit.base.util.DesktopUtils;
import org.telekit.base.util.FileUtils;
import org.telekit.base.util.TextBuilder;
import org.telekit.base.util.TextUtils;
import org.telekit.controls.util.ExtraBindings;
import org.telekit.ui.domain.ExceptionCaughtEvent;
import org.telekit.ui.domain.FXMLView;
import org.telekit.ui.tools.Action;
import org.telekit.ui.tools.apiclient.ParamModalController.ParamUpdateEvent;
import org.telekit.ui.tools.apiclient.TemplateModalController.TemplateUpdateEvent;

import javax.inject.Inject;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import static org.apache.commons.lang3.StringUtils.*;
import static org.telekit.base.Env.TEMP_DIR;
import static org.telekit.base.ui.IconCache.ICON_APP;
import static org.telekit.base.util.CSVUtils.COMMA_OR_SEMICOLON;
import static org.telekit.base.util.CSVUtils.textToTable;
import static org.telekit.ui.MessageKeys.*;
import static org.telekit.ui.tools.Action.NEW;
import static org.telekit.ui.tools.apiclient.Executor.*;

public class RootController extends Controller {

    private static final String PREVIEW_FILE_NAME = "api-client.preview.html";
    private static final String AUTOGENERATED = "<autogenerated>";
    private static final PseudoClass REQUEST_FAILED = PseudoClass.getPseudoClass("failed");
    private static final PseudoClass REQUEST_FORWARDED = PseudoClass.getPseudoClass("forwarded");

    public @FXML GridPane rootPane;
    public @FXML TabPane tpaneRight;
    public @FXML ComboBox<Template> cmbTemplate;
    public @FXML MenuItem previewTemplate;
    public @FXML MenuItem editTemplate;
    public @FXML MenuItem duplicateTemplate;
    public @FXML MenuItem deleteTemplate;
    public @FXML MenuItem exportTemplate;
    public @FXML TextField tfUsername;
    public @FXML PasswordField pfPassword;
    public @FXML FontAwesomeIconView imgShowPassword;
    public @FXML TextField tfPassword;
    public @FXML TableView<Param> tblParams;
    public @FXML TableColumn<Param, String> tcolParamName;
    public @FXML TableColumn<Param, String> tcolParamValue;
    public @FXML MenuItem miAddParam;
    public @FXML MenuItem miRemoveParam;
    public @FXML Label lbListLineCount;
    public @FXML TextArea taCsv;
    public @FXML Button btnStart;
    public @FXML Button btnStop;
    public @FXML Spinner<Integer> spnTimeout;
    public @FXML TableView<CompletedRequest> tblLog;
    public @FXML TableColumn<CompletedRequest, Integer> tcolLogIndex;
    public @FXML TableColumn<CompletedRequest, String> tcolLogStatus;
    public @FXML TableColumn<CompletedRequest, String> tcolLogRequestLine;
    public @FXML TextArea taRequestDetails;
    public @FXML CheckBox cbLogDisplayErrorsOnly;
    public @FXML Button btnExportLog;

    private TemplateModalController templateModalController = null;
    private ParamModalController paramModalController = null;
    private TemplateRepository templateRepository;

    private final YAMLMapper yamlMapper;
    private final ApplicationPreferences preferences;
    private Executor executor;
    private ReadOnlyBooleanProperty ongoingProperty;
    private boolean passwordVisible = false;

    @Inject
    public RootController(ApplicationPreferences preferences, YAMLMapper yamlMapper) {
        this.preferences = preferences;
        this.yamlMapper = yamlMapper;
    }

    @FXML
    public void initialize() {
        EventBus.getInstance().subscribe(TemplateUpdateEvent.class, this::updateTemplate);
        EventBus.getInstance().subscribe(ParamUpdateEvent.class, this::addParam);

        // init controls
        pfPassword.textProperty().bindBidirectional(tfPassword.textProperty());

        cmbTemplate.setButtonCell(new TemplateListCell());
        cmbTemplate.setCellFactory(property -> new TemplateListCell());
        previewTemplate.disableProperty().bind(Bindings.isEmpty(cmbTemplate.getItems()));
        editTemplate.disableProperty().bind(Bindings.isEmpty(cmbTemplate.getItems()));
        duplicateTemplate.disableProperty().bind(Bindings.isEmpty(cmbTemplate.getItems()));
        deleteTemplate.disableProperty().bind(Bindings.isEmpty(cmbTemplate.getItems()));
        exportTemplate.disableProperty().bind(Bindings.isEmpty(cmbTemplate.getItems()));

        tblParams.getSortOrder().add(tcolParamName);
        tcolParamName.setCellValueFactory(new PropertyValueFactory<>("name"));
        tcolParamValue.setCellValueFactory(new PropertyValueFactory<>("value"));
        tcolParamValue.setCellFactory(t -> new ParamsTableCell(new DefaultStringConverter()));
        tcolParamValue.setOnEditCommit(event -> event.getTableView()
                .getItems()
                .get(event.getTablePosition().getRow())
                .setValue(event.getNewValue())
        );

        miAddParam.disableProperty().bind(
                cmbTemplate.valueProperty().isNull()
        );
        miRemoveParam.disableProperty().bind(
                Bindings.size(tblParams.getSelectionModel().getSelectedItems()).lessThan(1)
        );

        taCsv.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue) countCsvLines();
        });

        tblLog.getSortOrder().add(tcolLogIndex);
        tblLog.setRowFactory(tv -> new LogTableRow());
        tblLog.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        tblLog.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> displayRequestDetails(newValue));
        tcolLogIndex.setCellValueFactory(new PropertyValueFactory<>("processedRange"));
        tcolLogStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        tcolLogRequestLine.setCellValueFactory(new PropertyValueFactory<>("userData"));
        tblLog.setOnKeyPressed(event -> {
            if (new KeyCodeCombination(KeyCode.C, KeyCombination.CONTROL_ANY).match(event)) {
                copyLogsTableToClipboard();
            }
        });

        cbLogDisplayErrorsOnly.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && tblLog.getItems() instanceof FilteredList) {
                FilteredList<CompletedRequest> items = (FilteredList<CompletedRequest>) tblLog.getItems();
                items.setPredicate(displayLogErrorsOnlyPredicate(newValue));
            }
        });

        updateExecutorBindings();

        // load data
        templateRepository = new TemplateRepository(yamlMapper);
        templateRepository.reloadAll();
        reloadTemplates(null);
    }

    private void copyLogsTableToClipboard() {
        List<CompletedRequest> selectedRows = tblLog.getSelectionModel().getSelectedItems();
        if (selectedRows == null || selectedRows.isEmpty()) return;

        StringBuilder sb = new StringBuilder();
        String separator = " | ";
        for (CompletedRequest row : selectedRows) {
            sb.append(row.getProcessedRange())
                    .append(separator)
                    .append(row.getStatus())
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

    private void updateExecutorBindings() {
        // anything bound to the current executor instance
        // need to be rebound when executor instance changes
        ongoingProperty = executor == null ?
                new SimpleBooleanProperty(false) :
                executor.runningProperty();

        btnStart.disableProperty().unbind();
        btnStart.disableProperty().bind(or(
                cmbTemplate.valueProperty().isNull(),
                ExtraBindings.isBlank(taCsv.textProperty()),
                ongoingProperty
        ));

        btnStop.disableProperty().unbind();
        btnStop.disableProperty().bind(Bindings.not(ongoingProperty));

        btnExportLog.disableProperty().unbind();
        btnExportLog.disableProperty().bind(Bindings.or(
                Bindings.isEmpty(tblLog.getItems()),
                ongoingProperty
        ));
    }

    private BooleanBinding or(BooleanBinding b1, BooleanBinding b2, ReadOnlyBooleanProperty b3) {
        return b1.or(b2.or(b3));
    }

    private Predicate<CompletedRequest> displayLogErrorsOnlyPredicate(boolean errorsOnly) {
        return request -> !errorsOnly || !request.isSucceeded();
    }

    private void countCsvLines() {
        // avoid binding this directly to the label text property
        // if csv text size is big enough, it will lead to extensive memory usage on multiple subsequent edits
        int count = TextUtils.countNotBlankLines(taCsv.getText().trim());
        lbListLineCount.setText(String.valueOf(count));
    }

    private void reloadTemplates(Template selectedTemplate) {
        List<Template> templates = templateRepository.getAll();
        templates.sort(Template::compareTo);
        // don't use setItems(), there is binding that watches items changes
        cmbTemplate.getItems().clear();
        cmbTemplate.getItems().addAll(templates);

        if (templates.size() > 0) {
            int selectedIndex = selectedTemplate != null ? templates.indexOf(selectedTemplate) : 0;
            cmbTemplate.getSelectionModel().select(selectedIndex);
            displaySelectedTemplateParams();
        }
    }

    @FXML
    public void displaySelectedTemplateParams() {
        Template selectedTemplate = cmbTemplate.getSelectionModel().getSelectedItem();
        ObservableList<Param> params = FXCollections.observableArrayList();

        if (selectedTemplate != null &&
                selectedTemplate.getParams() != null &&
                selectedTemplate.getParams().size() > 0) {
            params.addAll(selectedTemplate.getParams());
            params.sort(Param.COMPARATOR);
        }
        tblParams.getItems().clear();
        tblParams.setItems(params);
        tblParams.getSelectionModel().selectFirst();
    }

    @FXML
    public void handleTemplateAction(ActionEvent event) {
        MenuItem source = (MenuItem) event.getSource();
        Action action = Action.valueOf(source.getId().replaceAll("Template", "").toUpperCase());
        Template selectedTemplate = cmbTemplate.getSelectionModel().getSelectedItem();

        switch (action) {
            case NEW:
            case DUPLICATE:
            case EDIT:
                TemplateModalController modalController = getOrCreateTemplateDialog();
                modalController.setData(
                        action,
                        action != NEW ? selectedTemplate : null,
                        templateRepository.getNames()
                );
                modalController.getStage().showAndWait();
                break;
            case DELETE:
                if (selectedTemplate != null) doDeleteTemplate(selectedTemplate);
                break;
            case PREVIEW:
                showPreview();
                break;
            case IMPORT:
                doImportTemplate();
                break;
            case EXPORT:
                doExportTemplate(selectedTemplate);
                break;
        }
    }

    private TemplateModalController getOrCreateTemplateDialog() {
        if (this.templateModalController != null) return this.templateModalController;

        Controller controller = UILoader.load(FXMLView.API_CLIENT_TEMPLATE.getLocation(), Messages.getInstance());
        Stage dialog = Dialogs.modal(controller.getParent())
                .owner(rootPane.getScene().getWindow(), true)
                .icon(IconCache.get(ICON_APP))
                .resizable(false)
                .build();
        controller.setStage(dialog);
        this.templateModalController = (TemplateModalController) controller;

        return this.templateModalController;
    }

    private void doDeleteTemplate(Template template) {
        Alert dialog = Dialogs.confirm()
                .title(Messages.get(CONFIRMATION))
                .content(Messages.get(TOOLS_MSG_DELETE_TEMPLATE, template.getName()))
                .owner(rootPane.getScene().getWindow())
                .build();
        Optional<ButtonType> confirmation = dialog.showAndWait();
        if (confirmation.isPresent() && confirmation.get() == ButtonType.OK) {
            templateRepository.beginTransaction(false).rollbackOnException(() -> {
                templateRepository.removeById(template.getId());
                templateRepository.saveAll();
                reloadTemplates(null);
            });
        }
    }

    public void showPreview() {
        Template selectedTemplate = cmbTemplate.getSelectionModel().getSelectedItem();
        if (selectedTemplate != null && Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            File outputFile = TEMP_DIR.resolve(PREVIEW_FILE_NAME).toFile();
            String html = PreviewRenderer.render(selectedTemplate);
            try {
                Files.writeString(outputFile.toPath(), html);
                DesktopUtils.browse(outputFile.toURI());
            } catch (IOException e) {
                throw new TelekitException(Messages.get(MSG_GENERIC_IO_ERROR), e);
            }
        } else {
            Dialogs.info()
                    .title(Messages.get(INFO))
                    .content(Messages.get(TOOLS_MSG_YOU_HAVE_NO_TEMPLATES_TO_PREVIEW))
                    .owner(rootPane.getScene().getWindow())
                    .build()
                    .showAndWait();
        }
    }

    private void doImportTemplate() {
        File inputFile = Dialogs.file()
                .addFilter(Messages.get(FILE_DIALOG_YAML), "*.yaml", "*.yml")
                .build()
                .showOpenDialog(rootPane.getScene().getWindow());

        if (inputFile != null) {
            templateRepository.beginTransaction(false).rollbackOnException(() -> {
                templateRepository.importFromFile(inputFile);
                templateRepository.saveAll();
                reloadTemplates(null);
            });
        }
    }

    private void doExportTemplate(Template template) {
        File outputFile = Dialogs.file()
                .addFilter(Messages.get(FILE_DIALOG_YAML), "*.yaml", "*.yml")
                .initialFilename(FileUtils.sanitizeFileName(template.getName()) + ".yaml")
                .build()
                .showSaveDialog(rootPane.getScene().getWindow());

        if (outputFile != null) {
            templateRepository.exportToFile(List.of(template), outputFile);
        }
    }

    @Listener
    private void updateTemplate(TemplateUpdateEvent event) {
        Template selectedTemplate = cmbTemplate.getSelectionModel().getSelectedItem();
        Template updatedTemplate = event.getTemplate();

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

        reloadTemplates(selectedTemplate);
    }

    @FXML
    public void showParamDialog() {
        ParamModalController modalController = getOrCreateParamDialog();
        Set<String> usedParamNames = new HashSet<>();
        if (tblParams.getItems() != null) {
            tblParams.getItems().forEach(param -> usedParamNames.add(param.getName()));
        }
        modalController.reset();
        modalController.setData(usedParamNames);
        modalController.getStage().showAndWait();
    }

    private ParamModalController getOrCreateParamDialog() {
        if (this.paramModalController != null) return this.paramModalController;

        Controller controller = UILoader.load(FXMLView.API_CLIENT_PARAM.getLocation(), Messages.getInstance());
        Stage dialog = Dialogs.modal(controller.getParent())
                .owner(rootPane.getScene().getWindow(), true)
                .title(Messages.get(TOOLS_ADD_PARAM))
                .icon(IconCache.get(ICON_APP))
                .resizable(false)
                .build();
        controller.setStage(dialog);
        this.paramModalController = (ParamModalController) controller;

        return this.paramModalController;
    }

    @Listener
    private void addParam(ParamUpdateEvent event) {
        Template selectedTemplate = cmbTemplate.getSelectionModel().getSelectedItem();
        if (selectedTemplate == null) return;

        Template updatedTemplate = selectedTemplate.deepCopy();
        updatedTemplate.addParam(event.getParam());
        templateRepository.update(updatedTemplate);

        templateRepository.beginTransaction(selectedTemplate)
                .rollbackOnException(() -> templateRepository.saveAll());

        tblParams.getItems().add(event.getParam());
        tblParams.getItems().sort(Param.COMPARATOR);
    }

    @FXML
    public void removeParam() {
        Template selectedTemplate = cmbTemplate.getSelectionModel().getSelectedItem();
        if (selectedTemplate == null) return;

        Template updatedTemplate = selectedTemplate.deepCopy();
        Param param = tblParams.getSelectionModel().getSelectedItem();
        if (param == null) return;
        updatedTemplate.removeParam(param);
        templateRepository.update(updatedTemplate);

        templateRepository.beginTransaction(selectedTemplate)
                .rollbackOnException(() -> templateRepository.saveAll());

        tblParams.getItems().remove(param);
    }

    @FXML
    public void startHTTPClient() {
        // protect from multiple concurrent starts
        if (ongoingProperty.get()) return;

        // generate button is blocked until both specified, but anyway
        if (cmbTemplate.getSelectionModel().isEmpty() || StringUtils.isBlank(taCsv.getText())) return;

        // init dict
        Template template = cmbTemplate.getSelectionModel().getSelectedItem();
        String[][] csv = textToTable(taCsv.getText(), COMMA_OR_SEMICOLON);

        // validate
        boolean inputValid = validateInputData(template, csv);
        if (!inputValid) return;

        // task
        executor = new Executor(template, csv);
        executor.setTimeoutBetweenRequests(spnTimeout.getValue());

        if (isNotBlank(tfUsername.getText()) && isNotBlank(pfPassword.getText())) {
            executor.setAuthData(
                    AuthType.BASIC,
                    new AuthPrincipal(trim(tfUsername.getText()), trim(pfPassword.getText()))
            );
        }

        if (preferences.getProxy() != null && preferences.getProxy().isValid()) {
            executor.setProxy(preferences.getProxy());
        }

        final ObservableList<CompletedRequest> result = executor.getPartialResults();
        final FilteredList<CompletedRequest> filteredResult = new FilteredList<>(result);
        filteredResult.setPredicate(displayLogErrorsOnlyPredicate(cbLogDisplayErrorsOnly.isSelected()));
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
                EventBus.getInstance().publish(new ExceptionCaughtEvent(exception));
            }
        });

        tpaneRight.getSelectionModel().selectLast();
        updateExecutorBindings();
        toggleProgressIndicator(true);

        new Thread(executor).start();
    }

    private void toggleProgressIndicator(boolean on) {
        EventBus.getInstance().publish(new ProgressIndicatorEvent(id, on));
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
        List<Warning> warnings = validate(template, csv);
        if (warnings.isEmpty()) return true;

        // some warning need to be shown
        Alert dialog = Dialogs.confirm()
                .title(Messages.get(WARNING))
                .owner(rootPane.getScene().getWindow())
                .content("")
                .build();

        TextBuilder text = new TextBuilder();
        text.appendLine(Messages.get(TOOLS_MSG_VALIDATION_HEAD));
        if (warnings.contains(Warning.BLANK_PARAM_VALUES))
            text.appendLine(Messages.get(TOOLS_MSG_VALIDATION_BLANK_PARAM_VALUES));
        if (warnings.contains(Warning.MIXED_CSV_SIZE))
            text.appendLine(Messages.get(TOOLS_MSG_VALIDATION_MIXED_CSV));
        if (warnings.contains(Warning.UNRESOLVED_PLACEHOLDERS))
            text.appendLine(Messages.get(TOOLS_MSG_VALIDATION_UNRESOLVED_PLACEHOLDERS));
        if (warnings.contains(Warning.CSV_THRESHOLD_EXCEEDED))
            text.appendLine(Messages.get(TOOLS_MSG_VALIDATION_CSV_THRESHOLD_EXCEEDED, MAX_CSV_SIZE));
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
        File outputFile = Dialogs.file()
                .addFilter(Messages.get(FILE_DIALOG_TEXT), "*.txt")
                .initialFilename(FileUtils.sanitizeFileName("api-client-log.txt"))
                .build()
                .showSaveDialog(rootPane.getScene().getWindow());

        if (outputFile == null) return;

        List<CompletedRequest> log = tblLog.getItems();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
        String separator = " | ";
        String eol = "\n";

        try (FileOutputStream fos = new FileOutputStream(outputFile);
             OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
             BufferedWriter out = new BufferedWriter(osw)) {

            for (CompletedRequest request : log) {
                out.write(request.getDateTime().format(formatter));
                out.write(separator);
                out.write(String.valueOf(request.getStatus()));
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
        String clipboardContent = Clipboard.getSystemClipboard().getString();
        if (clipboardContent != null) {
            String newText = trim(clipboardContent.replaceAll("\t", ","));
            taCsv.replaceText(0, taCsv.getText().length(), newText);
        }
    }

    @FXML
    public void pasteAsColumns() {
        String clipboardContent = trim(Clipboard.getSystemClipboard().getString());
        if (isBlank(clipboardContent)) return;

        int originalLength = taCsv.getText().length();
        String curText = trim(taCsv.getText());

        if (isBlank(curText)) {
            taCsv.replaceText(0, originalLength, clipboardContent);
            return;
        }

        String[][] curColumns = textToTable(curText, COMMA_OR_SEMICOLON);
        String[][] newColumns = textToTable(clipboardContent, COMMA_OR_SEMICOLON);
        StringBuilder sb = new StringBuilder();
        for (int rowIndex = 0; rowIndex < curColumns.length; rowIndex++) {
            String[] curRow = curColumns[rowIndex];

            if (rowIndex < newColumns.length) {
                String[] newRow = newColumns[rowIndex];
                sb.append(String.join(",", ArrayUtils.addAll(curRow, newRow)));
            } else {
                sb.append(String.join(",", curRow));
            }

            sb.append("\n");
        }

        // using replaceText() because setText() doesn't trigger creation of undo entry
        taCsv.replaceText(0, originalLength, sb.toString());
    }

    @FXML
    public void togglePassword() {
        if (!passwordVisible) {
            tfPassword.toFront();
            imgShowPassword.toFront();
            imgShowPassword.setGlyphName("EYE_SLASH");
        } else {
            pfPassword.toFront();
            imgShowPassword.toFront();
            imgShowPassword.setGlyphName("EYE");
        }
        passwordVisible = !passwordVisible;
    }

    @Override
    public void reset() { /* not yet implemented */ }

    ///////////////////////////////////////////////////////////////////////////

    private static class TemplateListCell extends ListCell<Template> {

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

    private static class ParamsTableCell extends TextFieldTableCell<Param, String> {

        public ParamsTableCell(StringConverter<String> converter) {
            super(converter);
        }

        @Override
        public void updateItem(String value, boolean empty) {
            super.updateItem(value, empty);

            TableRow<Param> row = getTableRow();
            if (row == null) return;

            Param param = row.getItem();
            if (param != null) {
                setEditable(!param.isAutoGenerated());
                if (param.isAutoGenerated()) setText(AUTOGENERATED);
            }
        }
    }

    private static class LogTableRow extends TableRow<CompletedRequest> {

        @Override
        protected void updateItem(CompletedRequest request, boolean empty) {
            super.updateItem(request, empty);

            pseudoClassStateChanged(REQUEST_FAILED, !empty && (request.isFailed() || !request.isResponded()));
            pseudoClassStateChanged(REQUEST_FORWARDED, !empty && request.isForwarded());
        }
    }
}