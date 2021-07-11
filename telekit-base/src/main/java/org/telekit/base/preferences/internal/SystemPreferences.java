package org.telekit.base.preferences.internal;

import org.jetbrains.annotations.Nullable;
import org.telekit.base.Env;
import org.telekit.base.desktop.Dimension;

import java.util.Objects;
import java.util.prefs.Preferences;

import static org.telekit.base.Env.WINDOW_MAXIMIZED;

public class SystemPreferences {

    private final Preferences userRoot = Preferences.userRoot().node(Env.APP_NAME);
    private static final String WINDOW_WIDTH = "windowWidth";
    private static final String WINDOW_HEIGHT = "windowHeight";

    public Preferences getUserRoot() {
        return userRoot;
    }

    public @Nullable Dimension getMainWindowSize() {
        try {
            int width = userRoot.getInt(WINDOW_WIDTH, (int) WINDOW_MAXIMIZED.width());
            int height = userRoot.getInt(WINDOW_HEIGHT, (int) WINDOW_MAXIMIZED.height());
            return new Dimension(width, height);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void setMainWindowSize(Dimension dimension) {
        try {
            Objects.requireNonNull(dimension);

            int width = Math.max((int) dimension.width(), 800);
            int height = Math.max((int) dimension.height(), 600);

            if (WINDOW_MAXIMIZED.equals(dimension)) {
                width = (int) WINDOW_MAXIMIZED.width();
                height = (int) WINDOW_MAXIMIZED.height();
            }

            userRoot.putInt(WINDOW_WIDTH, width);
            userRoot.putInt(WINDOW_HEIGHT, height);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
