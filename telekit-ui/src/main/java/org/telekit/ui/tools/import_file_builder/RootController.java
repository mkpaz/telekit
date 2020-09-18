package org.telekit.ui.tools.import_file_builder;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
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
import javafx.scene.input.Clipboard;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import javafx.util.converter.DefaultStringConverter;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.telekit.base.EventBus;
import org.telekit.base.EventBus.Listener;
import org.telekit.base.Messages;
import org.telekit.base.Settings;
import org.telekit.base.UILoader;
import org.telekit.base.domain.NamedBean;
import org.telekit.base.domain.ProgressIndicatorEvent;
import org.telekit.base.domain.TelekitException;
import org.telekit.base.fx.Controller;
import org.telekit.base.fx.Dialogs;
import org.telekit.base.fx.FXBindings;
import org.telekit.base.util.CSVUtils;
import org.telekit.base.util.DesktopUtils;
import org.telekit.base.util.FileUtils;
import org.telekit.ui.domain.ExceptionCaughtEvent;
import org.telekit.ui.main.Views;
import org.telekit.ui.tools.Action;
import org.telekit.ui.tools.import_file_builder.ParamModalController.ParamUpdateEvent;
import org.telekit.ui.tools.import_file_builder.TemplateModalController.TemplateUpdateEvent;

import javax.inject.Inject;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.trim;
import static org.telekit.base.Settings.ICON_APP;
import static org.telekit.ui.main.AllMessageKeys.*;
import static org.telekit.ui.tools.Action.NEW;

public class RootController extends Controller {

    private static final Path DEFAULT_OUTPUT_FILE_PATH = Settings.HOME_DIR.resolve("import.txt");
    private static final String PREVIEW_FILE_NAME = "import-file-builder.preview.html";

    private static final String PREDEFINED = "predefined";
    private static final String DYNAMIC = "dynamic";
    private static final String AUTOGENERATED = "<autogenerated>";

    public @FXML GridPane rootPane;
    public @FXML ComboBox<Template> cmbTemplate;
    public @FXML MenuItem previewTemplate;
    public @FXML MenuItem editTemplate;
    public @FXML MenuItem duplicateTemplate;
    public @FXML MenuItem deleteTemplate;
    public @FXML MenuItem exportTemplate;
    public @FXML TableView<Param> tblParams;
    public @FXML TableColumn<Param, String> tcolParamName;
    public @FXML TableColumn<Param, String> tcolParamValue;
    public @FXML MenuItem miAddParam;
    public @FXML MenuItem miRemoveParam;
    public @FXML Label lbListLineCount;
    public @FXML TextArea taCsv;
    public @FXML TextField tfDestPath;
    public @FXML Button btnGenerate;
    public @FXML ToggleGroup toggleSaveType;
    public @FXML RadioButton rbSaveDynamic;
    public @FXML RadioButton rbSavePredefined;
    public @FXML CheckBox cbAppendFile;
    public @FXML CheckBox cbOpenAfterGeneration;

    private TemplateModalController templateModalController = null;
    private ParamModalController paramModalController = null;
    private TemplateRepository templateRepository;
    private volatile boolean ongoing = false;

    private XmlMapper xmlMapper;

    @Inject
    public RootController(XmlMapper xmlMapper) {
        this.xmlMapper = xmlMapper;
    }

    @FXML
    public void initialize() {
        EventBus.getInstance().subscribe(TemplateUpdateEvent.class, this::updateTemplate);
        EventBus.getInstance().subscribe(ParamUpdateEvent.class, this::addParam);

        // init controls
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

        tfDestPath.setText(DEFAULT_OUTPUT_FILE_PATH.toFile().getAbsolutePath());
        rbSaveDynamic.setUserData(DYNAMIC);
        rbSavePredefined.setUserData(PREDEFINED);

        btnGenerate.disableProperty().bind(Bindings.or(
                cmbTemplate.valueProperty().isNull(),
                FXBindings.isBlank(taCsv.textProperty())
        ));

        // load data
        templateRepository = new TemplateRepository(xmlMapper);
        reloadTemplates(null);
    }

    private void countCsvLines() {
        // avoid binding this directly to the label text property
        // if csv text size is big enough, it will lead to extensive memory usage on multiple subsequent edits
        int count = CSVUtils.countNotBlankLines(taCsv.getText().trim());
        lbListLineCount.setText(String.valueOf(count));
    }

    private void reloadTemplates(Template selectedTemplate) {
        List<Template> templates = templateRepository.getAll();
        templates.sort(NamedBean::compareTo);
        // don't use setItems(), there're binding that watches items changes
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

        Controller controller = UILoader.load(Views.IMPORT_FILE_BUILDER_TEMPLATE.getLocation(), Messages.getInstance());
        Stage dialog = Dialogs.modal(controller.getParent())
                .owner(rootPane.getScene().getWindow())
                .icon(Settings.getIcon(ICON_APP))
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
                .build();
        Optional<ButtonType> confirmation = dialog.showAndWait();
        if (confirmation.isPresent() && confirmation.get() == ButtonType.OK) {
            templateRepository.delete(new Template(template.getId()));
            reloadTemplates(null);
        }
    }

    public void showPreview() {
        Template selectedTemplate = cmbTemplate.getSelectionModel().getSelectedItem();
        if (selectedTemplate != null && Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            File outputFile = Settings.TEMP_DIR.resolve(PREVIEW_FILE_NAME).toFile();
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
                    .build()
                    .showAndWait();
        }
    }

    private void doImportTemplate() {
        File inputFile = Dialogs.file()
                .addFilter(Messages.get(FILE_DIALOG_XML), "*.xml")
                .build()
                .showOpenDialog(rootPane.getScene().getWindow());

        if (inputFile != null) {
            try {
                templateRepository.loadFromXML(Files.readString(inputFile.toPath()));
                reloadTemplates(null);
            } catch (IOException e) {
                throw new TelekitException(Messages.get(MSG_UNABLE_TO_IMPORT_DATA), e);
            }
        }
    }

    private void doExportTemplate(Template template) {
        File outputFile = Dialogs.file()
                .addFilter(Messages.get(FILE_DIALOG_XML), "*.xml")
                .initialFilename(FileUtils.sanitizeFileName(template.getName()) + ".xml")
                .build()
                .showSaveDialog(rootPane.getScene().getWindow());

        if (outputFile != null) {
            templateRepository.getAsXML(template.getId()).ifPresent(
                    xml -> {
                        try {
                            Files.writeString(outputFile.toPath(), xml);
                        } catch (Exception e) {
                            throw new TelekitException(Messages.get(MGG_UNABLE_TO_EXPORT_DATA), e);
                        }
                    });
        }
    }

    @Listener
    private void updateTemplate(TemplateUpdateEvent event) {
        Template selectedTemplate = cmbTemplate.getSelectionModel().getSelectedItem();
        switch (event.getAction()) {
            case NEW:
            case DUPLICATE:
                Template template = event.getTemplate();
                template.setId(UUID.randomUUID().toString());
                templateRepository.create(template);
                selectedTemplate = template;
            case EDIT:
                templateRepository.update(event.getTemplate());
                break;
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

        Controller controller = UILoader.load(Views.IMPORT_FILE_BUILDER_PARAM.getLocation(), Messages.getInstance());
        Stage dialog = Dialogs.modal(controller.getParent())
                .owner(rootPane.getScene().getWindow())
                .title(Messages.get(TOOLS_ADD_PARAM))
                .icon(Settings.getIcon(ICON_APP))
                .resizable(false)
                .build();
        controller.setStage(dialog);
        this.paramModalController = (ParamModalController) controller;

        return this.paramModalController;
    }

    @Listener
    private void addParam(ParamUpdateEvent event) {
        Template selectedTemplate = cmbTemplate.getSelectionModel().getSelectedItem();
        selectedTemplate.addParam(event.getParam());
        templateRepository.update(selectedTemplate);

        tblParams.getItems().add(event.getParam());
        tblParams.getItems().sort(Param.COMPARATOR);
    }

    @FXML
    public void removeParam() {
        Template selectedTemplate = cmbTemplate.getSelectionModel().getSelectedItem();
        Param param = tblParams.getSelectionModel().getSelectedItem();
        if (param != null) {
            selectedTemplate.removeParam(param);
            templateRepository.update(selectedTemplate);
            tblParams.getItems().remove(param);
        }
    }

    @FXML
    public void selectDestFile() {
        File destFile = Dialogs.file()
                .addFilter(Messages.get(FILE_DIALOG_TEXT), "*.txt")
                .build()
                .showSaveDialog(rootPane.getScene().getWindow());

        if (destFile != null) {
            tfDestPath.setText(destFile.getAbsolutePath());
        }
    }

    @FXML
    public void generateImportFile() {
        // protect from multiple concurrent starts
        if (this.ongoing) return;

        // generate button is blocked until both specified, but anyway
        if (cmbTemplate.getSelectionModel().isEmpty() || StringUtils.isBlank(taCsv.getText())) return;

        Template template = cmbTemplate.getSelectionModel().getSelectedItem();
        String[][] csv = CSVUtils.splitToTable(taCsv.getText());

        // validate
        boolean inputValid = validateInputData(template, csv);
        if (!inputValid) return;

        String saveType = (String) toggleSaveType.getSelectedToggle().getUserData();
        File outputFile = PREDEFINED.equals(saveType) ?
                new File(tfDestPath.getText()) :
                Dialogs.file()
                        .initialFilename(DEFAULT_OUTPUT_FILE_PATH.getFileName().toString())
                        .build()
                        .showSaveDialog(rootPane.getScene().getWindow());
        if (outputFile == null) return;

        boolean append = PREDEFINED.equals(saveType) &&
                cbAppendFile.isSelected() &&
                outputFile.exists();

        // print
        Generator generator = new Generator(template, csv, outputFile);
        generator.setAppend(append);
        generator.setLineSeparator(template.getLineSeparator().getCharacters());
        generator.setCharset(template.getEncoding().getCanonicalName());
        generator.setBom(template.getEncoding().requiresBOM());

        this.ongoing = true;
        EventBus.getInstance().publish(new ProgressIndicatorEvent(id, true));

        CompletableFuture<Void> task = CompletableFuture.runAsync(generator);
        task.whenComplete((result, exception) -> {
            this.ongoing = false;
            EventBus.getInstance().publish(new ProgressIndicatorEvent(id, false));

            if (exception != null) {
                EventBus.getInstance().publish(new ExceptionCaughtEvent(exception));
                return;
            }
            if (cbOpenAfterGeneration.isSelected()) {
                DesktopUtils.openQuietly(outputFile);
            } else {
                Platform.runLater(() -> Dialogs.info()
                        .title(Messages.get(INFO))
                        .content(Messages.get(MSG_TASK_COMPLETED))
                        .build()
                        .showAndWait());
            }
        });
    }

    private boolean validateInputData(Template template, String[][] csv) {
        List<Generator.Warning> warnings = Generator.validate(template, csv);
        if (warnings.isEmpty()) return true;

        // some warning need to be shown
        Alert dialog = Dialogs.confirm()
                .title(Messages.get(WARNING))
                .content("")
                .build();
        StringBuilder message = new StringBuilder();
        message.append(Messages.get(TOOLS_MSG_VALIDATION_HEAD_0) + ":\n");

        if (warnings.contains(Generator.Warning.BLANK_LINES))
            message.append("- " + Messages.get(TOOLS_MSG_VALIDATION_BLANK_LINES) + ";\n");
        if (warnings.contains(Generator.Warning.MIXED_CSV_SIZE))
            message.append("- " + Messages.get(TOOLS_MSG_VALIDATION_MIXED_CSV) + ";\n");
        if (warnings.contains(Generator.Warning.UNRESOLVED_PLACEHOLDERS))
            message.append("- " + Messages.get(TOOLS_MSG_VALIDATION_UNRESOLVED_PLACEHOLDERS) + ";\n");
        if (warnings.contains(Generator.Warning.CSV_THRESHOLD_EXCEEDED))
            message.append("- " + Messages.get(TOOLS_MSG_VALIDATION_CSV_THRESHOLD_EXCEEDED, Generator.MAX_CSV_SIZE) + ";\n");

        message.append("\n");
        message.append(Messages.get(TOOLS_MSG_VALIDATION_TAIL_0) + ".\n");
        message.append("\n");
        message.append(Messages.get(TOOLS_MSG_VALIDATION_TAIL_1) + ".\n");

        Label label = new Label(message.toString());
        label.setWrapText(true);
        dialog.getDialogPane().setContent(label);

        Optional<ButtonType> confirmation = dialog.showAndWait();
        return confirmation.isPresent() && confirmation.get() == ButtonType.OK;
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

        String[][] curColumns = CSVUtils.splitToTable(curText);
        String[][] newColumns = CSVUtils.splitToTable(clipboardContent);
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
}
