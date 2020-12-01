package org.telekit.ui.tools.common;

import org.telekit.controls.glyphs.FontAwesome;
import org.telekit.controls.glyphs.FontAwesomeIcon;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableRow;
import javafx.scene.image.Image;
import org.telekit.base.CompletionRegistry;

public class ParamIndicatorTableCell extends TableCell<Param, Image> {

    private final CompletionRegistry completionRegistry;

    public ParamIndicatorTableCell(CompletionRegistry completionRegistry) {
        this.completionRegistry = completionRegistry;
    }

    @Override
    public void updateItem(Image image, boolean empty) {
        super.updateItem(image, empty);
        setGraphic(null);

        TableRow<Param> row = getTableRow();
        if (row == null) return;

        Param param = row.getItem();
        if (param == null) return;

        if (Param.allowsCompletion(param, completionRegistry)) {
            setGraphic(new FontAwesomeIcon(FontAwesome.LIGHTBULB_ALT));
            return;
        }

        if (param.isAutoGenerated()) {
            setGraphic(new FontAwesomeIcon(FontAwesome.RANDOM));
        }
    }
}