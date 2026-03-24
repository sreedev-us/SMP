package com.musicplayer;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;

public class FxSettingsWindow {

    // --- Services ---
    private final SettingsManager settings;
    private final AuthSystem auth;
    private final CacheManager cache;
    private Stage stage;
    private Runnable closeHandler;
    private Runnable saveHandler;

    // --- FXML Components ---
    @FXML private Slider volumeSlider;
    @FXML private ComboBox<String> audioQualityCombo;
    @FXML private CheckBox autoplayCheckbox;
    @FXML private CheckBox crossfadeCheckbox;

    @FXML private ComboBox<String> themeCombo;
    @FXML private Spinner<Integer> fontSizeSpinner;
    @FXML private CheckBox showAlbumArtCheckbox;
    @FXML private CheckBox animationsCheckbox;

    @FXML private ToggleGroup repeatGroup;
    @FXML private RadioButton repeatNone;
    @FXML private RadioButton repeatOne;
    @FXML private RadioButton repeatAll;
    @FXML private CheckBox shuffleCheckbox;
    @FXML private CheckBox resumePlaybackCheckbox;

    @FXML private Spinner<Integer> maxResultsSpinner;
    @FXML private CheckBox autoAddCheckbox;

    @FXML private Spinner<Integer> cacheSizeSpinner;
    @FXML private CheckBox autoClearCacheCheckbox;

    @FXML private VBox accountContentBox;

    public FxSettingsWindow(AuthSystem auth, SettingsManager settings, CacheManager cache) {
        this.auth = auth;
        this.settings = settings;
        this.cache = cache;
    }

    public Parent createView() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/musicplayer/SettingsView.fxml"));
        loader.setController(this);
        Parent root = loader.load();
        root.getStylesheets().add(getClass().getResource("/com/musicplayer/player-styles.css").toExternalForm());
        loadSettings();
        return root;
    }

    public void setOnClose(Runnable closeHandler) {
        this.closeHandler = closeHandler;
    }

    public void setOnSaved(Runnable saveHandler) {
        this.saveHandler = saveHandler;
    }

    public void display() {
        try {
            stage = new Stage();
            Parent root = createView();
            stage.setTitle("Settings");
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initStyle(StageStyle.UTILITY);
            stage.setResizable(false);
            stage.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Called by FXML after components are injected
     */
    @FXML
    public void initialize() {
        // Populate ComboBoxes
        audioQualityCombo.setItems(FXCollections.observableArrayList("High", "Medium", "Low"));
        themeCombo.setItems(FXCollections.observableArrayList("Dark", "Light", "Auto"));

        // Configure Spinners
        fontSizeSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(8, 20, 11));
        maxResultsSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(5, 50, 15));
        cacheSizeSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(100, 2000, 500, 100));

        // Build the dynamic "Account" tab
        buildAccountTab();
    }

    /**
     * Loads all current settings from SettingsManager into the UI fields.
     */
    private void loadSettings() {
        // Audio
        volumeSlider.setValue(settings.getInt("audio.volume", 70));
        audioQualityCombo.setValue(getQualityString(settings.getString("audio.audio_quality", "high")));
        autoplayCheckbox.setSelected(settings.getBoolean("audio.autoplay", true));
        crossfadeCheckbox.setSelected(settings.getBoolean("audio.crossfade", false));

        // Interface
        themeCombo.setValue(getThemeString(settings.getString("interface.theme", "dark")));
        fontSizeSpinner.getValueFactory().setValue(settings.getInt("interface.font_size", 11));
        showAlbumArtCheckbox.setSelected(settings.getBoolean("interface.show_album_art", true));
        animationsCheckbox.setSelected(settings.getBoolean("interface.animations", true));

        // Playback
        setRepeatMode(settings.getString("playback.repeat_mode", "none"));
        shuffleCheckbox.setSelected(settings.getBoolean("playback.shuffle", false));
        resumePlaybackCheckbox.setSelected(settings.getBoolean("playback.resume_playback", true));

        // YouTube
        maxResultsSpinner.getValueFactory().setValue(settings.getInt("youtube.max_search_results", 15));
        autoAddCheckbox.setSelected(settings.getBoolean("youtube.auto_add_to_playlist", false));

        // Storage
        cacheSizeSpinner.getValueFactory().setValue(settings.getInt("storage.cache_size", 500));
        autoClearCacheCheckbox.setSelected(settings.getBoolean("storage.auto_clear_cache", false));
    }

    /**
     * Saves all UI field values back into the SettingsManager.
     */
    @FXML
    private void handleSave() {
        // Audio
        settings.set("audio.volume", (int) volumeSlider.getValue());
        settings.set("audio.audio_quality", getQualityValue(audioQualityCombo.getValue()));
        settings.set("audio.autoplay", autoplayCheckbox.isSelected());
        settings.set("audio.crossfade", crossfadeCheckbox.isSelected());

        // Interface
        settings.set("interface.theme", getThemeValue(themeCombo.getValue()));
        settings.set("interface.font_size", fontSizeSpinner.getValue());
        settings.set("interface.show_album_art", showAlbumArtCheckbox.isSelected());
        settings.set("interface.animations", animationsCheckbox.isSelected());

        // Playback
        settings.set("playback.repeat_mode", getRepeatMode());
        settings.set("playback.shuffle", shuffleCheckbox.isSelected());
        settings.set("playback.resume_playback", resumePlaybackCheckbox.isSelected());

        // YouTube
        settings.set("youtube.max_search_results", maxResultsSpinner.getValue());
        settings.set("youtube.auto_add_to_playlist", autoAddCheckbox.isSelected());

        // Storage
        settings.set("storage.cache_size", cacheSizeSpinner.getValue());
        settings.set("storage.auto_clear_cache", autoClearCacheCheckbox.isSelected());

        // Save to file and close
        settings.saveSettings();
        if (saveHandler != null) {
            saveHandler.run();
        }
        close();
    }

    @FXML
    private void handleCancel() {
        close();
    }

    @FXML
    private void handleReset() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Reset all settings to default?", ButtonType.YES, ButtonType.NO);
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                settings.setSettings(new SettingsManager().getSettings()); // Load defaults
                loadSettings(); // Re-populate UI
            }
        });
    }

    @FXML
    private void handleClearCache() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Are you sure you want to clear the cache?", ButtonType.YES, ButtonType.NO);
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                if (cache.clearAll()) {
                    new Alert(Alert.AlertType.INFORMATION, "Cache cleared successfully.").show();
                } else {
                    new Alert(Alert.AlertType.ERROR, "Failed to clear cache.").show();
                }
            }
        });
    }

    /**
     * Dynamically builds the Account tab based on login status.
     */
    private void buildAccountTab() {
        accountContentBox.getChildren().clear();
        if (auth.isLoggedIn()) {
            Label badge = new Label("CONNECTED");
            badge.getStyleClass().add("account-badge");

            Label userLabel = new Label("Signed in as " + auth.getCurrentUser());
            userLabel.getStyleClass().add("settings-card-title");

            Label helpLabel = new Label("Your account is ready for synced searches and personalized playback.");
            helpLabel.setWrapText(true);
            helpLabel.getStyleClass().add("settings-card-copy");

            Button logoutButton = new Button("Logout");
            logoutButton.getStyleClass().add("control-button");
            logoutButton.setOnAction(e -> {
                auth.logout();
                buildAccountTab();
            });

            accountContentBox.getChildren().addAll(badge, userLabel, helpLabel, logoutButton);
        } else {
            Label badge = new Label("GUEST MODE");
            badge.getStyleClass().add("account-badge");

            Label userLabel = new Label("You are not signed in.");
            userLabel.getStyleClass().add("settings-card-title");

            Label helpLabel = new Label("You can keep using the player locally, or connect Google to unlock online account features.");
            helpLabel.setWrapText(true);
            helpLabel.getStyleClass().add("settings-card-copy");

            Button loginButton = new Button("Login with Google");
            loginButton.setOnAction(e -> {
                auth.googleLogin();
                buildAccountTab();
            });
            loginButton.getStyleClass().add("add-button");

            accountContentBox.getChildren().addAll(badge, userLabel, helpLabel, loginButton);
        }
    }

    // --- Helper methods for conversion ---

    private String getQualityString(String value) {
        return switch (value) {
            case "high" -> "High";
            case "medium" -> "Medium";
            case "low" -> "Low";
            default -> "High";
        };
    }

    private String getQualityValue(String string) {
        return switch (string) {
            case "High" -> "high";
            case "Medium" -> "medium";
            case "Low" -> "low";
            default -> "high";
        };
    }

    private String getThemeString(String value) {
        return switch (value) {
            case "dark" -> "Dark";
            case "light" -> "Light";
            case "auto" -> "Auto";
            default -> "Dark";
        };
    }

    private String getThemeValue(String string) {
        return switch (string) {
            case "Dark" -> "dark";
            case "Light" -> "light";
            case "Auto" -> "auto";
            default -> "dark";
        };
    }

    private String getRepeatMode() {
        if (repeatOne.isSelected()) return "one";
        if (repeatAll.isSelected()) return "all";
        return "none";
    }

    private void setRepeatMode(String mode) {
        switch (mode) {
            case "one" -> repeatOne.setSelected(true);
            case "all" -> repeatAll.setSelected(true);
            default -> repeatNone.setSelected(true);
        }
    }

    private void close() {
        if (stage != null) {
            stage.close();
        }
        if (closeHandler != null) {
            closeHandler.run();
        }
    }
}
