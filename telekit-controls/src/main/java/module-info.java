module telekit.controls {

    requires java.logging;

    requires javafx.controls;
    requires javafx.fxml;

    exports fontawesomefx;
    opens fontawesomefx;

    exports fontawesomefx.fa;
    opens fontawesomefx.fa;
}