package org.telekit.desktop.i18n;

import org.telekit.base.i18n.BaseMessages;
import org.telekit.base.i18n.BundleLoader;
import org.telekit.controls.i18n.ControlsMessages;

public interface DesktopMessages extends BaseMessages, ControlsMessages {

    String SYSTEM_HOME = "system.Home";
    String SYSTEM_PROJECT_PAGE = "system.ProjectPage";
    String SYSTEM_OPEN_DATA_DIR = "system.OpenDataDirectory";
    String SYSTEM_OPEN_PLUGINS_DIR = "system.OpenPluginsDirectory";
    String SYSTEM_RESTART_REQUIRED = "system.RestartRequired";

    String PREFERENCES_GENERAL = "preferences.General";
    String PREFERENCES_MANUAL_PROXY_CONFIGURATION = "preferences.ManualProxyConfiguration";
    String PREFERENCES_NO_PROXY = "preferences.NoProxy";
    String PREFERENCES_PROXY = "preferences.Proxy";
    String PREFERENCES_REQUIRES_RESTART = "preferences.requires-restart";

    String PLUGIN_MANAGER_MSG_INSTALL_SUCCESS = "plugin-manager.msg.install-success";
    String PLUGIN_MANAGER_MSG_UNINSTALL_CONFIRM = "plugin-manager.msg.uninstall-confirm";
    String PLUGIN_MANAGER_MSG_UNINSTALL_SUCCESS = "plugin-manager.msg.uninstall-success";

    // TOOLS : SHARED

    String TOOLS_BATCH = "tools.Batch";
    String TOOLS_CHOOSE_VALUE = "tools.ChooseValue";
    String TOOLS_DOCUMENT_START = "tools.DocumentStart";
    String TOOLS_DOCUMENT_END = "tools.DocumentEnd";
    String TOOLS_ELEMENT = "tools.Element";
    String TOOLS_EXCEPTIONS = "tools.Exceptions";
    String TOOLS_LIST_FOR_REPLACEMENT = "tools.ListForReplacement";
    String TOOLS_SAVE_AS = "tools.SaveAs";
    String TOOLS_SHOW_SAVE_DIALOG = "tools.ShowSaveDialog";
    String TOOLS_TEMPLATE = "tools.Template";

    String TOOLS_APPEND_IF_EXISTS = "tools.append-if-exists";
    String TOOLS_CHARACTERS = "tools.characters";
    String TOOLS_COLON = "tools.colon";
    String TOOLS_COMMA = "tools.comma";
    String TOOLS_ITEMS = "tools.items";
    String TOOLS_LINES = "tools.lines";
    String TOOLS_PIPE = "tools.pipe";
    String TOOLS_SECONDS = "tools.seconds";
    String TOOLS_SEMICOLON = "tools.semicolon";
    String TOOLS_TAB = "tools.tab";
    String TOOLS_WORDS = "tools.words";

    String TOOLS_ADD_PARAM = "tools.AddParam";
    String TOOLS_NEW_TEMPLATE = "tools.NewTemplate";
    String TOOLS_EDIT_TEMPLATE = "tools.EditTemplate";
    String TOOLS_PASTE_COLUMNS_RIGHT = "tools.PasteColumnsRight";
    String TOOLS_PASTE_FROM_EXCEL = "tools.PasteFromExcel";
    String TOOLS_ONLY_FIRST_N_ROWS_WILL_BE_SHOWN = "tools.only-first-N-rows-will-be-shown";

    String TOOLS_MSG_DELETE_TEMPLATE = "tools.msg.delete-template";
    String TOOLS_MSG_LIST_IS_TOO_LARGE_TO_EXPORT = "tools.msg.list-is-too-large-to-export";
    String TOOLS_MSG_VALIDATION_HEAD = "tools.msg.validation.head";
    String TOOLS_MSG_VALIDATION_BLANK_PARAM_VALUES = "tools.msg.validation.blank-param-values";
    String TOOLS_MSG_VALIDATION_MIXED_CSV = "tools.msg.validation.mixed-csv";
    String TOOLS_MSG_VALIDATION_UNRESOLVED_PLACEHOLDERS = "tools.msg.validation.unresolved-placeholders";
    String TOOLS_MSG_VALIDATION_CSV_THRESHOLD_EXCEEDED = "tools.msg.validation.csv-threshold-exceeded";
    String TOOLS_MSG_VALIDATION_TAIL = "tools.msg.validation.tail";

    // TOOLS : SPECIFIC

    String TOOL_API_CLIENT = "tool.APIClient";
    String APICLIENT_BATCH_SIZE = "apiclient.BatchSize";
    String APICLIENT_BATCH_WRAPPER = "apiclient.BatchWrapper";
    String APICLIENT_COMPLETED_REQUESTS = "apiclient.CompletedRequests";
    String APICLIENT_BODY = "apiclient.Body";
    String APICLIENT_WAIT_TIMEOUT = "apiclient.WaitTimeout";
    String APICLIENT_HTTP_HEADERS = "apiclient.HTTPHeaders";
    String APICLIENT_REQUEST_LINE = "apiclient.RequestLine";
    String APICLIENT_SHOW_UNSUCCESSFUL_REQUESTS_ONLY = "apiclient.show-unsuccessful-requests-only";
    String APICLIENT_SHOW_TIMEOUT_BETWEEN_REQUESTS = "apiclient.timeout-between-requests";

    String TOOL_BASE64_ENCODER = "tool.Base64Encoder";
    String BASE64_AS_TEXT = "base64.as-text";
    String BASE64_LINE_BY_LINE = "base64.line-by-line";

    String TOOL_IMPORT_FILE_BUILDER = "tool.ImportFileBuilder";
    String FILEBUILDER_OPEN_FILE_AFTER_GENERATION = "filebuilder.open-file-after-generation";

    String TOOL_IPV4_CALCULATOR = "tool.IPv4Calculator";
    String IPCALC_IP_ADDRESS_CONVERTER = "ipcalc.IPAddressConverter";
    String IPCALC_GENERATE_IP_ADDRESSES = "ipcalc.GenerateIPAddressList";
    String IPCALC_SPLIT_TO_0 = "ipcalc.split-to.0";
    String IPCALC_SPLIT_TO_1 = "ipcalc.split-to.1";

    String TOOL_PASSWORD_GENERATOR = "tool.PasswordGenerator";
    String PASSGEN_EXCLUDE_SIMILAR_CHARS = "passgen.exclude-similar-chars";
    String PASSGEN_KATAKANA = "passgen.Katakana";
    String PASSGEN_XKCD = "passgen.XKCD";
    String PASSGEN_PASSWORD_TYPE = "passgen.PasswordType";

    String TOOL_SEQUENCE_GENERATOR = "tool.SequenceGenerator";
    String SEQGEN_PARAMETERS_HEADER = "seqgen.parameters-header";
    String SEQGEN_MSG_SEQUENCE_SIZE_EXCEEDS_LIMIT = "seqgen.msg.sequence-size-exceeds-limit";

    static BundleLoader getLoader() { return BundleLoader.of(DesktopMessages.class); }
}
