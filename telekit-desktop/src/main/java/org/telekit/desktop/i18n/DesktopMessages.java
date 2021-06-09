package org.telekit.desktop.i18n;

import org.telekit.base.i18n.BaseMessages;
import org.telekit.base.i18n.BundleLoader;
import org.telekit.controls.i18n.ControlsMessages;

// TODO: Add all entries from messages file (for now ignore test warnings)
public interface DesktopMessages extends BaseMessages, ControlsMessages {

    String SYSTEM_HOME = "system.Home";
    String SYSTEM_PROJECT_PAGE = "system.ProjectPage";
    String SYSTEM_OPEN_DATA_DIR = "system.OpenDataDirectory";
    String SYSTEM_OPEN_PLUGINS_DIR = "system.OpenPluginsDirectory";
    String SYSTEM_MSG_ERROR_OCCURRED = "system.msg.error-occurred";
    String SYSTEM_RESTART_REQUIRED = "system.RestartRequired";

    String PREFERENCES_GENERAL = "preferences.General";
    String PREFERENCES_PROXY = "preferences.Proxy";
    String PREFERENCES_REQUIRES_RESTART = "preferences.requires-restart";

    String PLUGIN_MANAGER_MSG_INSTALL_SUCCESS = "plugin-manager.msg.install-success";
    String PLUGIN_MANAGER_MSG_UNINSTALL_CONFIRM = "plugin-manager.msg.uninstall-confirm";
    String PLUGIN_MANAGER_MSG_UNINSTALL_SUCCESS = "plugin-manager.msg.uninstall-success";

    String TOOLS_API_CLIENT = "tools.APIClient";
    String TOOLS_BASE64 = "tools.Base64Encoder";
    String TOOLS_FILE_BUILDER = "tools.ImportFileBuilder";
    String TOOLS_IP4_CALC = "tools.IPCalculator";
    String TOOLS_PASS_GEN = "tools.PasswordGenerator";
    String TOOLS_SEQ_GEN = "tools.SequenceGenerator";
    String TOOLS_SS7_CIC_TABLE = "tools.CICTable";
    String TOOLS_SS7_SPC_CONV = "tools.SPCConverter";
    String TOOLS_TRANSLIT = "tools.Transliterator";
    String TOOLS_GROUP_SS7 = "tools.group.SS7";

    String TOOLS_CHOOSE_VALUE = "tools.ChooseValue";
    String TOOLS_COMMA = "tools.comma";
    String TOOLS_COLON = "tools.colon";
    String TOOLS_PIPE = "tools.pipe";
    String TOOLS_SEMICOLON = "tools.semicolon";
    String TOOLS_TAB = "tools.tab";

    String TOOLS_ADD_PARAM = "tools.AddParam";
    String TOOLS_NEW_TEMPLATE = "tools.NewTemplate";
    String TOOLS_EDIT_TEMPLATE = "tools.EditTemplate";
    String TOOLS_ONLY_FIRST_N_ROWS_WILL_BE_SHOWN = "tools.only-first-N-rows-will-be-shown";
    String TOOLS_MSG_DELETE_TEMPLATE = "tools.msg.delete-template";
    String TOOLS_MSG_YOU_HAVE_NO_TEMPLATES_TO_PREVIEW = "tools.msg.you-have-no-templates-to-preview";
    String TOOLS_MSG_VALIDATION_HEAD = "tools.msg.validation.head";
    String TOOLS_MSG_VALIDATION_BLANK_PARAM_VALUES = "tools.msg.validation.blank-param-values";
    String TOOLS_MSG_VALIDATION_MIXED_CSV = "tools.msg.validation.mixed-csv";
    String TOOLS_MSG_VALIDATION_UNRESOLVED_PLACEHOLDERS = "tools.msg.validation.unresolved-placeholders";
    String TOOLS_MSG_VALIDATION_CSV_THRESHOLD_EXCEEDED = "tools.msg.validation.csv-threshold-exceeded";
    String TOOLS_MSG_VALIDATION_TAIL = "tools.msg.validation.tail";

    String TOOLS_APICLIENT_TASK_REPORT = "tools.api-client.task-report";
    String TOOLS_IPCALC_TASK_REPORT = "tools.ipcalc.IPFormatConverter";
    String TOOLS_IPCALC_MSG_INVALID_IP_ADDRESS = "tools.ipcalc.msg.invalid-ip-address";
    String TOOLS_SEQGEN_MSG_SEQUENCE_SIZE_EXCEEDS_LIMIT = "tools.seq-gen.msg.sequence-size-exceeds-limit";
    String TOOLS_SS7_MSG_INVALID_POINT_CODE = "tools.ss7.msg.invalid-point-code";

    static BundleLoader getLoader() { return BundleLoader.of(DesktopMessages.class); }
}
