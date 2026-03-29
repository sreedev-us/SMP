package com.musicplayer;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import com.gluonhq.attach.browser.BrowserService;

import java.lang.reflect.Method;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public class FxLoginWindow extends Application {

    private GoogleAuthentication googleAuth;
    private AuthSystem activeAuth;

    @FXML
    private Label statusLabel;
    @FXML
    private Button googleLoginBtn;
    @FXML
    private Button closeButton;

    private Stage primaryStage;
    private Scene primaryScene;
    private FxMusicPlayer playerController;

    /**
     * The main entry point for all JavaFX applications.
     */
    @Override
    public void start(Stage primaryStage) throws Exception {
        this.primaryStage = primaryStage;

        FXMLLoader loader = new FXMLLoader();
        loader.setLocation(resolveResourceIfPresent("/com/musicplayer/LoginView.fxml"));
        loader.setController(this);
        Parent root = loadFromResource(loader, "/com/musicplayer/LoginView.fxml");

        primaryScene = new Scene(root, 450, 600);
        addStylesheet(primaryScene, "/com/musicplayer/login-ui.css");
        primaryStage.setTitle("Harmony Pro Login");
        primaryStage.setScene(primaryScene);
        primaryStage.setOnCloseRequest(event -> {
            if (playerController != null) {
                playerController.shutdown();
            }
        });
        primaryStage.show();
    }

    @FXML
    public void initialize() {
        if (googleLoginBtn != null && AppPlatform.isMobile()) {
            googleLoginBtn.setDisable(true);
            googleLoginBtn.setText("Google Login Coming Soon");
        }

        if (closeButton != null) {
            boolean desktop = !AppPlatform.isMobile();
            closeButton.setVisible(desktop);
            closeButton.setManaged(desktop);
        }

        if (statusLabel != null && AppPlatform.isMobile()) {
            statusLabel.setText("Mobile build ready. Use Demo Mode while Google login is being adapted for Android.");
        }
    }

    /**
     * Real Google OAuth 2.0 login — opens the browser.
     * Requires a valid credentials.json on the classpath.
     */
    @FXML
    public void handleGoogleLogin() {
        if (AppPlatform.isMobile()) {
            statusLabel.setText("Google login is not available on Android yet. Please use Demo Mode for now.");
            return;
        }

        statusLabel.setText("Opening browser for Google login...");
        new Thread(() -> {
            GoogleAuthentication auth = getGoogleAuth();
            if (auth == null) {
                Platform.runLater(() -> statusLabel.setText("Google login is unavailable. Check your desktop configuration."));
                return;
            }

            boolean success = auth.googleLogin();
            Platform.runLater(() -> {
                if (success) {
                    statusLabel.setText("Logged in. Loading player...");
                    activeAuth = auth;
                    launchMainPlayer(auth.getCurrentUser());
                } else {
                    statusLabel.setText("Google login failed. Check credentials.json.");
                }
            });
        }).start();
    }

    /**
     * Demo Mode — uses DemoAuthSystem, no credentials or network needed.
     * YouTube search won't work (no OAuth token) but the player UI opens instantly.
     */
    @FXML
    public void handleDemoLogin() {
        statusLabel.setText("Connecting in Demo Mode...");
        DemoAuthSystem demo = new DemoAuthSystem();
        boolean success = demo.googleLogin(); // always true
        if (success) {
            statusLabel.setText("Demo mode active. Loading player...");
            activeAuth = demo;
            launchMainPlayer(demo.getCurrentUser());
        } else {
            statusLabel.setText("Demo login failed.");
        }
    }

    /**
     * Launches the FxMusicPlayer on the JavaFX thread.
     * @param username The username from the successful login.
     */
    private void launchMainPlayer(String username) {
        try {
            SettingsManager settings = new SettingsManager();

            playerController = new FxMusicPlayer(activeAuth, settings);
            playerController.setExternalUrlOpener(url -> {
                System.out.println("ExternalOpener: Attempting to open " + url);
                try {
                    if (AppPlatform.isAndroid()) {
                        // Use Gluon BrowserService which fires a real Android Intent.
                        // Requires <queries> in AndroidManifest.xml for Android 11+.
                        BrowserService.create().ifPresentOrElse(
                            browser -> {
                                try {
                                    browser.launchExternalBrowser(url);
                                    System.out.println("ExternalOpener: Android BrowserService SUCCESS.");
                                } catch (Exception ex) {
                                    System.err.println("ExternalOpener BrowserService error: " + ex.getMessage() + ". Trying reflection...");
                                    fireAndroidIntent(url);
                                }
                            },
                            () -> {
                                System.err.println("ExternalOpener: BrowserService not available. Trying reflection...");
                                fireAndroidIntent(url);
                            }
                        );
                    } else {
                        getHostServices().showDocument(url);
                    }
                } catch (Exception ex) {
                    System.err.println("ExternalOpener error: " + ex.getMessage());
                    ex.printStackTrace();
                    Platform.runLater(() -> playerController.updateStatus("Error: Can't open browser - " + ex.getClass().getSimpleName()));
                }
            });

            String playerView = AppPlatform.isMobile()
                ? "/com/musicplayer/PlayerViewMobile.fxml"
                : "/com/musicplayer/PlayerView.fxml";
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(resolveResourceIfPresent(playerView));
            loader.setController(playerController);
            Parent root = loadFromResource(loader, playerView);

            primaryScene.getStylesheets().clear();
            addStylesheet(primaryScene, "/com/musicplayer/player-styles.css");
            primaryScene.setRoot(root);
            primaryStage.setTitle("Harmony Pro - Welcome, " + username);
            AppPlatform.configurePrimaryStage(primaryStage);

        } catch (Exception e) {
            System.err.println("--- CRITICAL FXML LOAD ERROR ---");
            e.printStackTrace();
            if (e.getCause() != null) {
                System.err.println("CAUSE:");
                e.getCause().printStackTrace();
            }
            String detail = e.getMessage();
            statusLabel.setText("Failed to load player: " + e.getClass().getSimpleName()
                + (detail == null || detail.isBlank() ? "" : " - " + detail));
        }
    }


    /**
     * This method is called by FXML when the "Close" button is clicked.
     */
    @FXML
    public void handleClose() {
        if (playerController != null) {
            playerController.shutdown();
        }
        Platform.exit();
    }

    private GoogleAuthentication getGoogleAuth() {
        if (googleAuth != null) {
            return googleAuth;
        }

        try {
            googleAuth = new GoogleAuthentication();
            return googleAuth;
        } catch (Exception | LinkageError ex) {
            ex.printStackTrace();
            return null;
        }
    }

    // Main method to launch the app
    public static void main(String[] args) {
        launch(args);
    }

    private Parent loadFromResource(FXMLLoader loader, String path) throws IOException {
        InputStream stream = openResource(path);
        try (stream) {
            return loader.load(stream);
        }
    }

    private void addStylesheet(Scene scene, String path) {
        URL url = resolveResourceIfPresent(path);
        if (url != null) {
            scene.getStylesheets().add(url.toExternalForm());
        } else {
            System.err.println("Stylesheet not found: " + path);
        }
    }

    private void fireAndroidIntent(String url) {
        try {
            System.out.println("ExternalOpener: Firing Android VIEW intent for: " + url);

            // Strategy 1: Use ActivityThread.currentActivity() — universally available on
            // all Android builds, not reliant on any Gluon-specific class names.
            Object activity = null;
            try {
                Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
                Method currentActivityMethod = activityThreadClass.getMethod("currentActivity");
                activity = currentActivityMethod.invoke(null);
                if (activity != null) {
                    System.out.println("ExternalOpener: Got activity via ActivityThread.currentActivity().");
                }
            } catch (Throwable ignored) {
                System.err.println("ExternalOpener: ActivityThread approach failed, trying Gluon class scan...");
            }

            // Strategy 2: Scan known Gluon/JFXPorts Activity class names as fallback.
            if (activity == null) {
                String[] activityClasses = {
                    "com.gluonhq.substrate.android.FXActivity",
                    "com.gluonhq.impl.substratevm.android.SubstrateActivity",
                    "com.gluonhq.substrate.android.SubstrateActivity",
                    "com.gluonhq.glass.ui.android.MainActivity",
                    "org.javafxports.android.FXActivity"
                };
                for (String className : activityClasses) {
                    try {
                        Class<?> clazz = Class.forName(className);
                        for (String methodName : new String[]{ "getInstance", "currentActivity" }) {
                            try {
                                Object result = clazz.getMethod(methodName).invoke(null);
                                if (result != null) {
                                    activity = result;
                                    System.out.println("ExternalOpener: Found Activity via "
                                        + className + "." + methodName + "()");
                                    break;
                                }
                            } catch (Throwable ignored) {}
                        }
                        if (activity != null) break;
                    } catch (Throwable ignored) {}
                }
            }

            if (activity == null) {
                throw new RuntimeException("Could not obtain Android Activity via any strategy.");
            }

            // Build android.content.Intent for ACTION_VIEW with the URL
            Class<?> intentClass = Class.forName("android.content.Intent");
            Class<?> uriClass    = Class.forName("android.net.Uri");
            Object   uri         = uriClass.getMethod("parse", String.class).invoke(null, url);

            Object intent = intentClass
                .getConstructor(String.class, uriClass)
                .newInstance("android.intent.action.VIEW", uri);

            // Add FLAG_ACTIVITY_NEW_TASK so it works from non-activity contexts
            try {
                java.lang.reflect.Field flagField = intentClass.getField("FLAG_ACTIVITY_NEW_TASK");
                int flag = (int) flagField.get(null);
                intentClass.getMethod("addFlags", int.class).invoke(intent, flag);
            } catch (Throwable ignored) {}

            activity.getClass().getMethod("startActivity", intentClass).invoke(activity, intent);
            System.out.println("ExternalOpener: Android VIEW intent fired successfully.");

        } catch (Exception ex) {
            System.err.println("ExternalOpener: Intent firing FAILED: " + ex.getClass().getSimpleName()
                + ": " + ex.getMessage());
            ex.printStackTrace();
            Platform.runLater(() -> playerController.updateStatus(
                "Can't open browser: " + ex.getMessage()));
        }
    }

    private InputStream openResource(String path) {
        InputStream stream = FxLoginWindow.class.getResourceAsStream(path);
        if (stream == null) {
            String classLoaderPath = path.startsWith("/") ? path.substring(1) : path;
            stream = FxLoginWindow.class.getClassLoader().getResourceAsStream(classLoaderPath);
        }
        if (stream == null) {
            throw new IllegalStateException("Missing resource stream: " + path);
        }
        return stream;
    }

    private URL resolveResourceIfPresent(String path) {
        URL url = FxLoginWindow.class.getResource(path);
        if (url == null) {
            String classLoaderPath = path.startsWith("/") ? path.substring(1) : path;
            url = FxLoginWindow.class.getClassLoader().getResource(classLoaderPath);
        }
        return url;
    }
}
