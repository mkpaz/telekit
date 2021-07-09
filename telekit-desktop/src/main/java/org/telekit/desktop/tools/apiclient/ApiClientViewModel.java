package org.telekit.desktop.tools.apiclient;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import javafx.beans.property.*;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import org.apache.commons.lang3.StringUtils;
import org.telekit.base.CompletionRegistry;
import org.telekit.base.desktop.mvvm.*;
import org.telekit.base.di.Initializable;
import org.telekit.base.domain.KeyValue;
import org.telekit.base.domain.LineSeparator;
import org.telekit.base.domain.security.UsernamePasswordCredentials;
import org.telekit.base.domain.event.Notification;
import org.telekit.base.domain.event.TaskProgressEvent;
import org.telekit.base.domain.exception.TelekitException;
import org.telekit.base.event.DefaultEventBus;
import org.telekit.base.preferences.SharedPreferences;
import org.telekit.base.service.CompletionProvider;
import org.telekit.base.service.impl.KeyValueCompletionProvider;
import org.telekit.controls.util.BindUtils;
import org.telekit.controls.util.HastyObjectProperty;
import org.telekit.controls.util.Promise;
import org.telekit.controls.util.TransformationListHandle;
import org.telekit.desktop.tools.common.Param;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.lang3.StringUtils.trim;
import static org.telekit.base.i18n.BaseMessages.MGG_UNABLE_TO_SAVE_DATA_TO_FILE;
import static org.telekit.base.i18n.I18n.t;
import static org.telekit.base.net.HttpConstants.AuthScheme;
import static org.telekit.base.util.CSVUtils.COMMA_OR_SEMICOLON;
import static org.telekit.base.util.CSVUtils.textToTable;
import static org.telekit.controls.util.BindUtils.isNotBlank;

@Singleton
public final class ApiClientViewModel implements Initializable, ViewModel {

    static final String PREVIEW_FILE_NAME = "api-client.preview.html";

    private final SharedPreferences preferences;
    private final CompletionRegistry completionRegistry;
    private final TemplateRepository templateRepository;
    private final ExecutorService threadPool;

    private Executor executor;

    @Inject
    public ApiClientViewModel(SharedPreferences preferences,
                              YAMLMapper yamlMapper,
                              CompletionRegistry completionRegistry,
                              ExecutorService threadPool) {
        this.preferences = preferences;
        this.completionRegistry = completionRegistry;
        this.templateRepository = new TemplateRepository(yamlMapper);
        this.threadPool = threadPool;
    }

    @Override
    public void initialize() {
        templates.getSortedList().setComparator(Template::compareTo);
        log.getItems().addListener(new LogStatListener());

        logErrorsOnly.addListener((obs, old, value) -> {
            if (value != null) { log.getFilteredList().setPredicate(request -> !value || !request.isSucceeded()); }
        });

        // Note: double check if you will want to run this from another thread
        // can cause ComboBox updating issues
        templateRepository.reloadAll();
        templates.getItems().setAll(templateRepository.getAll());
        selectFirstTemplate();
    }

    public void selectFirstTemplate() {
        if (!templates.getItems().isEmpty()) {
            selectedTemplateProperty().set(templates.getSortedList().get(0));
        }
    }

    public Set<String> getUsedTemplateNames() {
        return templateRepository.getNames();
    }

    public KeyValueCompletionProvider getCompletionProvider(Param param) {
        CompletionProvider<?> provider = completionRegistry.getProviderFor(param.getName()).orElse(null);
        return provider instanceof KeyValueCompletionProvider value ? value : null;
    }

    public CompletionRegistry getCompletionRegistry() {
        return completionRegistry;
    }

    public List<KeyValue<String, String>> getParamCompletion(Param param) {
        KeyValueCompletionProvider provider = getCompletionProvider(param);
        return provider != null ? new ArrayList<>(provider.find(e -> true)) : Collections.emptyList();
    }

    public boolean doesParamSupportCompletion(Param param) {
        return Param.doesSupportCompletion(param, completionRegistry);
    }

    public List<String> validate() {
        return Executor.validate(selectedTemplate.get(), textToTable(csvText.get(), COMMA_OR_SEMICOLON));
    }

    private void updateTemplate(Consumer<Template> mutator) {
        Template source = selectedTemplate.get();
        Template copy = selectedTemplate.get().deepCopy();
        mutator.accept(copy);
        updateTemplate(source, copy);
    }

    private void updateTemplate(Template source, Template copy) {
        Promise.runAsync(() -> {
            templateRepository.update(copy);
            templateRepository.beginTransaction(source).rollbackOnException(templateRepository::saveAll);
        }).then(() -> reloadTemplates(copy))
                .start(threadPool);
    }

    // Note: ComboBox is bugged. Do not try to update list items partially: add(), set(),
    // remove() etc. You will get issues with duplicated items and dropdown resize. The only
    // working solution is to fully reload list content and restore selection after that.
    private void reloadTemplates(Template templateToSelect) {
        templates.getItems().setAll(templateRepository.getAll());
        if (templateToSelect != null) {
            selectedTemplate.set(templateToSelect);
        } else {
            selectFirstTemplate();
        }
    }

    private void toggleProgressIndicator(boolean on) {
        DefaultEventBus.getInstance().publish(new TaskProgressEvent(getClass().getCanonicalName(), on));
    }

    ///////////////////////////////////////////////////////////////////////////
    // Properties                                                            //
    ///////////////////////////////////////////////////////////////////////////

    //@formatter:off
    private final TransformationListHandle<Template> templates = new TransformationListHandle<>();
    public ObservableList<Template> getTemplates() { return templates.getSortedList(); }

    private final ReadOnlyBooleanWrapper ongoing = new ReadOnlyBooleanWrapper(this, "ongoing", false);
    public ReadOnlyBooleanProperty ongoingProperty() { return ongoing.getReadOnlyProperty(); }

    private final ObjectProperty<Template> selectedTemplate = new HastyObjectProperty<>(this, "selectedTemplate");
    public ObjectProperty<Template> selectedTemplateProperty() { return selectedTemplate; }

    private final StringProperty csvText = new SimpleStringProperty(this, "csvText");
    public StringProperty csvTextProperty() { return csvText; }

    private final StringProperty authUsername = new SimpleStringProperty(this, "authUsername");
    public StringProperty authUsernameProperty() { return authUsername; }

    private final StringProperty authPassword = new SimpleStringProperty(this, "authPassword");
    public StringProperty authPasswordProperty() { return authPassword; }

    private final IntegerProperty timeout = new SimpleIntegerProperty(this, "timeout");
    public IntegerProperty timeoutProperty() { return timeout; }

    private final TransformationListHandle<CompletedRequest> log = new TransformationListHandle<>();
    public ObservableList<CompletedRequest> getFilteredLog() { return log.getFilteredList(); }
    public ObservableList<CompletedRequest> getFullLog() { return log.getItems(); }

    private final ReadOnlyObjectWrapper<LogStat> logStat = new ReadOnlyObjectWrapper<>(this, "logStat", LogStat.EMPTY);
    public ReadOnlyObjectProperty<LogStat> logStatProperty() { return logStat.getReadOnlyProperty(); }

    private final BooleanProperty logErrorsOnly = new SimpleBooleanProperty(this, "logErrorsOnly");
    public BooleanProperty logErrorsOnlyProperty() { return logErrorsOnly; }
    //@formatter:on

    ///////////////////////////////////////////////////////////////////////////
    // Commands                                                              //
    ///////////////////////////////////////////////////////////////////////////

    public ConsumerCommand<Template> addTemplateCommand() { return addTemplateCommand; }

    private final ConsumerCommand<Template> addTemplateCommand = new ConsumerCommandBase<>() {

        @Override
        protected void doExecute(Template template) {
            Promise.runAsync(() -> {
                // ensure formatting
                templateRepository.beginTransaction(false).rollbackOnException(() -> {
                    templateRepository.add(template);
                    templateRepository.saveAll();
                });
            }).then(() -> reloadTemplates(template))
                    .start(threadPool);
        }
    };

    // ~

    public ConsumerCommand<Template> updateTemplateCommand() { return updateTemplateCommand; }

    private final ConsumerCommand<Template> updateTemplateCommand = new ConsumerCommandBase<>() {

        { executable.bind(selectedTemplate.isNotNull()); }

        @Override
        protected void doExecute(Template template) {
            updateTemplate(selectedTemplate.get(), template);
        }
    };

    // ~

    public Command removeTemplateCommand() { return removeTemplateCommand; }

    private final Command removeTemplateCommand = new CommandBase() {

        { executable.bind(selectedTemplate.isNotNull()); }

        @Override
        protected void doExecute() {
            Template template = selectedTemplate.get();

            Promise.runAsync(() -> {
                // ensure formatting
                templateRepository.beginTransaction(false).rollbackOnException(() -> {
                    templateRepository.removeById(template.getId());
                    templateRepository.saveAll();
                });
            }).then(() -> reloadTemplates(null))
                    .start(threadPool);
        }
    };

    // ~

    public ConsumerCommand<File> importTemplateCommand() { return importTemplateCommand; }

    private final ConsumerCommand<File> importTemplateCommand = new ConsumerCommandBase<>() {

        @Override
        protected void doExecute(File file) {
            Promise.runAsync(() -> {
                // ensure formatting
                templateRepository.beginTransaction(false).rollbackOnException(() -> {
                    templateRepository.importFromFile(file);
                    templateRepository.saveAll();
                });
            }).then(() -> reloadTemplates(null))
                    .start(threadPool);
        }
    };

    // ~

    public ConsumerCommand<File> exportTemplateCommand() { return exportTemplateCommand; }

    private final ConsumerCommand<File> exportTemplateCommand = new ConsumerCommandBase<>() {

        { executable.bind(selectedTemplate.isNotNull()); }

        @Override
        protected void doExecute(File file) {
            Template template = selectedTemplate.get();
            Promise.runAsync(() -> templateRepository.exportToFile(List.of(template), file))
                    .start(threadPool);
        }
    };

    // ~

    public ConsumerCommand<Param> addParamCommand() { return addParamCommand; }

    private final ConsumerCommand<Param> addParamCommand = new ConsumerCommandBase<>() {

        { executable.bind(selectedTemplate.isNotNull()); }

        @Override
        protected void doExecute(Param param) {
            if (param == null) { return; }
            updateTemplate(template -> template.addParam(param));
        }
    };

    // ~

    public ConsumerCommand<Param> removeParamCommand() { return removeParamCommand; }

    private final ConsumerCommand<Param> removeParamCommand = new ConsumerCommandBase<>() {

        { executable.bind(selectedTemplate.isNotNull()); }

        @Override
        protected void doExecute(Param param) {
            if (param == null) { return; }
            updateTemplate(template -> template.removeParam(param));
        }
    };

    // ~

    public ConsumerCommand<File> exportLogCommand() { return exportLogCommand; }

    private final ConsumerCommand<File> exportLogCommand = new ConsumerCommandBase<>() {

        { executable.bind(selectedTemplate.isNotNull()); }

        @Override
        protected void doExecute(File file) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
            final String separator = " | ";
            final String EOL = LineSeparator.UNIX.getCharacters();

            Promise.runAsync(() -> {
                try (FileOutputStream outputStream = new FileOutputStream(file);
                     OutputStreamWriter writer = new OutputStreamWriter(outputStream, UTF_8);
                     BufferedWriter out = new BufferedWriter(writer)) {

                    for (CompletedRequest request : log.getItems()) {
                        out.write(request.getDateTime().format(formatter));
                        out.write(separator);
                        out.write(String.valueOf(request.getStatusCode()));
                        out.write(separator);
                        out.write(request.getUserData());
                        out.write(EOL);
                        out.write(EOL);
                        out.write(request.print().replaceAll("(?m)^", "\t"));
                        out.write(EOL);
                        out.write(EOL);
                    }
                } catch (Exception e) {
                    throw new TelekitException(t(MGG_UNABLE_TO_SAVE_DATA_TO_FILE), e);
                }
            }).start(threadPool);
        }
    };

    // ~

    public Command startCommand() { return startCommand; }

    private final Command startCommand = new CommandBase() {

        { executable.bind(BindUtils.and(ongoing.not(), selectedTemplate.isNotNull(), isNotBlank(csvText))); }

        @Override
        protected void doExecute() {
            Template template = selectedTemplate.get();
            String[][] csv = textToTable(csvText.get(), COMMA_OR_SEMICOLON);

            // configure task
            executor = new Executor(template, csv, log.getItems());
            executor.setTimeoutBetweenRequests(timeout.getValue());
            executor.setProxy(preferences.getProxy());

            String username = trim(authUsername.get());
            String password = trim(authPassword.get());
            if (StringUtils.isNotBlank(username) && StringUtils.isNotBlank(password)) {
                executor.setPasswordBasedAuth(AuthScheme.BASIC, UsernamePasswordCredentials.of(username, password));
            }

            // prepare properties
            BindUtils.rebind(ongoing, executor.runningProperty());
            log.getItems().clear(); // this will also set logStat value to LogStat.EMPTY
            logStat.set(new LogStat(executor.getPlannedRequestCount(), 0, 0));

            executor.setOnSucceeded(event -> toggleProgressIndicator(false));
            executor.setOnCancelled(event -> toggleProgressIndicator(false));
            executor.setOnFailed(event -> {
                toggleProgressIndicator(false);
                Throwable exception = event.getSource().getException();
                if (exception != null) { DefaultEventBus.getInstance().publish(Notification.error(exception)); }
            });

            // start task
            toggleProgressIndicator(true);
            threadPool.execute(executor);
        }
    };

    // ~

    public Command stopCommand() { return stopCommand; }

    private final Command stopCommand = new CommandBase() {

        { executable.bind(ongoing); }

        @Override
        protected void doExecute() {
            if (executor != null) { executor.cancel(); }
        }
    };

    ////////////////////////////////////////////////////////////

    class LogStatListener implements ListChangeListener<CompletedRequest> {

        @Override
        public void onChanged(Change<? extends CompletedRequest> change) {
            LogStat old = logStat.get();

            while (change.next()) {
                if (old == null) {
                    logStat.set(LogStat.EMPTY);
                    return;
                }

                // log must only be cleared or appended
                if (!change.wasAdded()) { return; }

                change.getAddedSubList().forEach(r -> {
                    if (r.isSucceeded() || r.isForwarded()) {
                        logStat.set(old.withIncrementedSuccessCount());
                    }
                    if (r.isFailed() || !r.isResponded()) {
                        logStat.set(old.withIncrementedFailedCount());
                    }
                });
            }
        }
    }
}
