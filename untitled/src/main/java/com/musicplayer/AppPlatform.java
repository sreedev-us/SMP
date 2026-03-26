package com.musicplayer;

import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.util.Locale;

public final class AppPlatform {

    private static final String OS_NAME = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    private static final String JAVA_FX_PLATFORM = System.getProperty("javafx.platform", "").toLowerCase(Locale.ROOT);
    private static final String GLUON_PLATFORM = System.getProperty("gluon.platform", "").toLowerCase(Locale.ROOT);
    private static final String JAVA_RUNTIME_NAME = System.getProperty("java.runtime.name", "").toLowerCase(Locale.ROOT);
    private static final String JAVA_VM_NAME = System.getProperty("java.vm.name", "").toLowerCase(Locale.ROOT);

    private AppPlatform() {
    }

    public static boolean isAndroid() {
        return hasAndroidRuntime()
            || OS_NAME.contains("android")
            || JAVA_FX_PLATFORM.contains("android")
            || GLUON_PLATFORM.contains("android")
            || JAVA_RUNTIME_NAME.contains("android")
            || JAVA_VM_NAME.contains("dalvik");
    }

    public static boolean isMobile() {
        return isAndroid();
    }

    public static void configurePrimaryStage(Stage stage) {
        Rectangle2D visualBounds = Screen.getPrimary().getVisualBounds();

        if (isMobile()) {
            stage.setWidth(visualBounds.getWidth());
            stage.setHeight(visualBounds.getHeight());
            stage.setMaximized(true);
            return;
        }

        double targetWidth = Math.min(1180, visualBounds.getWidth() * 0.92);
        double targetHeight = Math.min(760, visualBounds.getHeight() * 0.9);
        stage.setMinWidth(960);
        stage.setMinHeight(640);
        stage.setWidth(targetWidth);
        stage.setHeight(targetHeight);
        stage.setMaxWidth(visualBounds.getWidth());
        stage.setMaxHeight(visualBounds.getHeight());
        stage.centerOnScreen();
    }

    private static boolean hasAndroidRuntime() {
        try {
            Class.forName("android.os.Build");
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }
}
