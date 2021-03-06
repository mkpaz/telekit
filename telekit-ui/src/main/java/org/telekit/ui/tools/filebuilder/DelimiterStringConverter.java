package org.telekit.ui.tools.filebuilder;

import javafx.util.StringConverter;
import org.telekit.base.i18n.Messages;

import java.util.Map;

import static org.telekit.ui.MessageKeys.*;

public class DelimiterStringConverter extends StringConverter<String> {

    public static final Map<String, String> VALUES = Map.of(
            ",", TOOLS_COMMA,
            ":", TOOLS_COLON,
            "|", TOOLS_PIPE,
            ";", TOOLS_SEMICOLON,
            "\\t", TOOLS_TAB
    );

    private static final String SEPARATOR = " - ";

    @Override
    public String toString(String punctuationMark) {
        if (punctuationMark == null || punctuationMark.isEmpty() || !VALUES.containsKey(punctuationMark)) return "";
        return punctuationMark + SEPARATOR + Messages.get(VALUES.get(punctuationMark));
    }

    @Override
    public String fromString(String comboBoxValue) {
        if (comboBoxValue == null || comboBoxValue.isEmpty()) return "";
        return comboBoxValue.split(SEPARATOR)[0];
    }
}
