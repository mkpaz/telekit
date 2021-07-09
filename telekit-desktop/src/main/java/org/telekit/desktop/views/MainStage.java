package org.telekit.desktop.views;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.Region;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.telekit.base.desktop.Dimension;
import org.telekit.base.preferences.internal.ApplicationPreferences;
import org.telekit.controls.theme.DefaultTheme;
import org.telekit.desktop.IconCache;
import org.telekit.desktop.startup.config.Config;

import java.util.Objects;

import static org.telekit.base.Env.APP_NAME;
import static org.telekit.base.Env.WINDOW_MAXIMIZED;
import static org.telekit.controls.util.Containers.getAnchors;
import static org.telekit.controls.util.Containers.setAnchors;
import static org.telekit.desktop.IconCache.ICON_APP;
import static org.telekit.desktop.startup.config.Config.DESKTOP_MODULE_PATH;

public class MainStage {

    public static final String APP_ICON_PATH = "assets/images/telekit.png";
    public static final Dimension MIN_SIZE = new Dimension(800, 600);
    public static final Dimension PREF_SIZE = new Dimension(1280, 800);

    // set these values carefully, mouse cursor have to switch its type
    // from RESIZE to DEFAULT when hovering over title bar border
    static final int DROP_SHADOW_OFFSET = 10;

    // drag and resize
    private Rectangle2D latestBounds = null;
    private boolean maximized;
    private double dragOffsetX = 0;
    private double dragOffsetY = 0;

    final Stage stage;
    final Scene scene;

    private MainStage(Stage primaryStage, Scene scene) {
        this.stage = Objects.requireNonNull(primaryStage);
        this.scene = scene;

        stage.setScene(scene);
        stage.setOnCloseRequest(t -> Platform.exit());
        stage.setMinWidth(MIN_SIZE.width());
        stage.setMinHeight(MIN_SIZE.height());
        stage.setTitle(APP_NAME);

        IconCache.put(ICON_APP, new Image(Objects.requireNonNull(
                getClass().getResourceAsStream(DESKTOP_MODULE_PATH.concat(APP_ICON_PATH).toString()))
        ));
        primaryStage.getIcons().add(IconCache.get(ICON_APP));

        scene.getStylesheets().addAll(new DefaultTheme().getResources());
        scene.getStylesheets().addAll(
                Config.getResource("assets/css/layout.css"),
                Config.getResource("assets/css/system.css"),
                Config.getResource("assets/css/tools.css")
        );
    }

    public Stage getStage() { return stage; }

    public Scene getScene() { return scene; }

    public Parent getContent() {
        if (scene instanceof TransparentScene transparentScene) {
            return transparentScene.getContent();
        } else {
            return scene.getRoot();
        }
    }

    public void setContent(Parent parent) {
        if (scene instanceof TransparentScene transparentScene) {
            transparentScene.setContent(parent);
        } else {
            scene.setRoot(parent);
        }
    }

    public boolean isDecorated() { return stage.getStyle() == StageStyle.DECORATED; }

    public void attachDragHandlers(Region region) {
        region.setOnMousePressed(event -> {
            dragOffsetX = event.getSceneX();
            dragOffsetY = event.getSceneY();
        });

        region.setOnMouseDragged(event -> {
            if (maximized) { return; }
            scene.setCursor(Cursor.MOVE);

            // TODO: Move dragged window outside screen (OpenJFX bug, Linux only)
            // https://bugs.openjdk.java.net/browse/JDK-8134278

            stage.setX(event.getScreenX() - dragOffsetX);
            stage.setY(event.getScreenY() - dragOffsetY);
        });

        region.setOnMouseDragReleased(event -> {
            scene.setCursor(Cursor.DEFAULT);
            dragOffsetX = event.getSceneX();
            dragOffsetY = event.getSceneY();
        });
    }

    public void minimize() {
        stage.setIconified(true);
    }

    public void maximize() {
        Screen screen = getScreenForStage(stage);
        Rectangle2D bounds = screen.getVisualBounds();

        // FIXME: Undecorated stage cannot be maximized on Linux
        // Normally, stage.setMaximized(!stage.isMaximized()) is enough
        // https://bugs.openjdk.java.net/browse/JDK-8237491

        if (!maximized) {
            latestBounds = getAnchors(stage);
            setAnchors(stage, bounds);
            maximized = true;
        } else {
            if (latestBounds != null) {
                // FIXME setY() isn't working
                // Probably another bug in OpenJFX Glass/GTK+ backend
                setAnchors(stage, latestBounds);
            }
            maximized = false;
        }

        if (scene instanceof TransparentScene transparentScene) {
            transparentScene.toggleShadow(!maximized);
        }
    }

    public void close() { stage.close(); }

    public void show() { stage.show(); }

    public void toFront() { stage.toFront(); }

    public void toBack() { stage.toBack(); }

    public static MainStage createUndecorated(Stage primaryStage, ApplicationPreferences preferences) {
        Dimension initialSize = getSceneSize(preferences);

        TransparentScene scene = new TransparentScene(initialSize.width(), initialSize.height(), DROP_SHADOW_OFFSET);
        scene.toggleShadow(true);

        MainStage mainStage = create(primaryStage, scene, preferences);

        // scene must be set to stage BEFORE attaching resize handler
        UndecoratedStageResizeHandler.attach(mainStage.getStage(), DROP_SHADOW_OFFSET, new Insets(2));
        mainStage.getStage().initStyle(StageStyle.TRANSPARENT);

        return mainStage;
    }

    private static MainStage create(Stage primaryStage, Scene scene, ApplicationPreferences preferences) {
        MainStage mainStage = new MainStage(primaryStage, scene);

        mainStage.getStage().setOnCloseRequest(t -> {
            if (preferences != null) {
                preferences.setMainWindowSize(Dimension.of(mainStage.getStage()));
            }
            Platform.exit();
        });

        if (WINDOW_MAXIMIZED.equals(Dimension.of(scene))) { mainStage.getStage().setMaximized(true); }

        return mainStage;
    }

    private static Dimension getSceneSize(ApplicationPreferences preferences) {
        // use last remembered window size
        Dimension storedSize = preferences.getMainWindowSize();
        if (storedSize != null) { return storedSize; }

        // or compute window size in depends of the screen size
        Dimension visualBounds = Dimension.of(Screen.getPrimary().getVisualBounds());

        // make sure that window size less than visual screen bounds
        // the latter is calculated based on display resolution, output scale and taskbar height
        return PREF_SIZE.gt(visualBounds) ? visualBounds : PREF_SIZE;
    }

    public static Screen getScreenForStage(Stage stage) {
        for (Screen screen : Screen.getScreensForRectangle(stage.getX(), stage.getY(), 1, 1)) {
            return screen;
        }
        return Screen.getPrimary();
    }
}
