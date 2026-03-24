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

import java.io.IOException;

public class FxLoginWindow extends Application {

    private GoogleAuthentication googleAuth;
    private AuthSystem activeAuth;

    @FXML
    private Label statusLabel;
    @FXML
    private Button closeButton;

    private Stage primaryStage;
    private Scene primaryScene;
    private FxMusicPlayer playerController;

    public FxLoginWindow() {
        this.googleAuth = new GoogleAuthentication();
    }

    /**
     * The main entry point for all JavaFX applications.
     */
    @Override
    public void start(Stage primaryStage) throws Exception {
        this.primaryStage = primaryStage;

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/musicplayer/LoginView.fxml"));
        loader.setController(this);
        Parent root = loader.load();

        primaryScene = new Scene(root, 450, 600);
        primaryScene.getStylesheets().add(getClass().getResource("/com/musicplayer/login-ui.css").toExternalForm());
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
        if (closeButton != null) {
            boolean desktop = !AppPlatform.isMobile();
            closeButton.setVisible(desktop);
            closeButton.setManaged(desktop);
        }
    }

    /**
     * Real Google OAuth 2.0 login — opens the browser.
     * Requires a valid credentials.json on the classpath.
     */
    @FXML
    private void handleGoogleLogin() {
        statusLabel.setText("Opening browser for Google login...");
        new Thread(() -> {
            boolean success = googleAuth.googleLogin();
            Platform.runLater(() -> {
                if (success) {
                    statusLabel.setText("Logged in. Loading player...");
                    activeAuth = googleAuth;
                    launchMainPlayer(googleAuth.getCurrentUser());
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
    private void handleDemoLogin() {
        statusLabel.setText("Connecting in Demo Mode...");
        new Thread(() -> {
            DemoAuthSystem demo = new DemoAuthSystem();
            boolean success = demo.googleLogin(); // always true
            Platform.runLater(() -> {
                if (success) {
                    statusLabel.setText("Demo mode active. Loading player...");
                    activeAuth = demo;
                    launchMainPlayer(demo.getCurrentUser());
                } else {
                    statusLabel.setText("Demo login failed.");
                }
            });
        }).start();
    }

    /**
     * Launches the FxMusicPlayer on the JavaFX thread.
     * @param username The username from the successful login.
     */
    private void launchMainPlayer(String username) {
        try {
            SettingsManager settings = new SettingsManager();

            playerController = new FxMusicPlayer(activeAuth, settings);

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/musicplayer/PlayerView.fxml"));
            loader.setController(playerController);
            Parent root = loader.load();

            primaryScene.getStylesheets().clear();
            primaryScene.getStylesheets().add(getClass().getResource("/com/musicplayer/player-styles.css").toExternalForm());
            primaryScene.setRoot(root);
            primaryStage.setTitle("Harmony Pro - Welcome, " + username);
            AppPlatform.configurePrimaryStage(primaryStage);

        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("Failed to load main player.");
        }
    }


    /**
     * This method is called by FXML when the "Close" button is clicked.
     */
    @FXML
    private void handleClose() {
        if (playerController != null) {
            playerController.shutdown();
        }
        Platform.exit();
    }

    // Main method to launch the app
    public static void main(String[] args) {
        launch(args);
    }
}
