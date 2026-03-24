// SettingsManager.java
package com.musicplayer;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.prefs.Preferences;

public class SettingsManager {
    private String configFile;
    private Map<String, Object> settings;
    private Preferences prefs;

    public SettingsManager() {
        this("harmony_pro_config");
    }

    public SettingsManager(String configName) {
        this.configFile = configName + ".properties";
        this.settings = loadDefaultSettings();
        this.prefs = Preferences.userRoot().node("com/musicplayer/harmonypro");
        loadSettings();
    }

    private Map<String, Object> loadDefaultSettings() {
        Map<String, Object> defaultSettings = new HashMap<>();

        // Audio settings
        Map<String, Object> audio = new HashMap<>();
        audio.put("volume", 70);
        audio.put("autoplay", true);
        audio.put("crossfade", false);
        audio.put("crossfade_duration", 3);
        audio.put("audio_quality", "high");
        defaultSettings.put("audio", audio);

        // Interface settings
        Map<String, Object> interfaceSettings = new HashMap<>();
        interfaceSettings.put("theme", "dark");
        interfaceSettings.put("font_size", 11);
        interfaceSettings.put("font_family", "Segoe UI");
        interfaceSettings.put("show_album_art", true);
        interfaceSettings.put("smooth_scrolling", true);
        interfaceSettings.put("animations", true);
        defaultSettings.put("interface", interfaceSettings);

        // YouTube settings
        Map<String, Object> youtube = new HashMap<>();
        youtube.put("max_search_results", 15);
        youtube.put("audio_quality", "best");
        youtube.put("auto_add_to_playlist", false);
        youtube.put("enable_comments", false);
        defaultSettings.put("youtube", youtube);

        // Playback settings
        Map<String, Object> playback = new HashMap<>();
        playback.put("repeat_mode", "none");
        playback.put("shuffle", false);
        playback.put("resume_playback", true);
        playback.put("fade_duration", 2);
        playback.put("gapless_playback", true);
        defaultSettings.put("playback", playback);

        // Storage settings
        Map<String, Object> storage = new HashMap<>();
        storage.put("cache_size", 500);
        storage.put("auto_clear_cache", false);
        storage.put("save_playlists", true);
        storage.put("backup_interval", 7);
        defaultSettings.put("storage", storage);

        // Privacy settings
        Map<String, Object> privacy = new HashMap<>();
        privacy.put("collect_analytics", false);
        privacy.put("share_usage_data", false);
        privacy.put("remember_search_history", true);
        defaultSettings.put("privacy", privacy);

        return defaultSettings;
    }

    public void loadSettings() {
        // Try to load from Java Preferences first (more reliable)
        loadFromPreferences();

        // Fallback to file-based loading
        Path configPath = Paths.get(configFile);
        if (Files.exists(configPath)) {
            loadFromFile();
        }
    }

    // FIX 1: Added annotation
    @SuppressWarnings("unchecked")
    private void loadFromPreferences() {
        try {
            // Load simple settings directly from Preferences
            for (String category : settings.keySet()) {
                Map<String, Object> categorySettings = (Map<String, Object>) settings.get(category);
                for (String key : categorySettings.keySet()) {
                    String prefKey = category + "." + key;
                    Object defaultValue = categorySettings.get(key);

                    if (defaultValue instanceof Integer) {
                        int value = prefs.getInt(prefKey, (Integer) defaultValue);
                        categorySettings.put(key, value);
                    } else if (defaultValue instanceof Boolean) {
                        boolean value = prefs.getBoolean(prefKey, (Boolean) defaultValue);
                        categorySettings.put(key, value);
                    } else if (defaultValue instanceof String) {
                        String value = prefs.get(prefKey, (String) defaultValue);
                        categorySettings.put(key, value);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error loading settings from preferences: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void loadFromFile() {
        Properties props = new Properties();
        try (FileReader reader = new FileReader(configFile)) {
            props.load(reader);

            // Convert Properties to nested Map structure
            for (String key : props.stringPropertyNames()) {
                set(key, parseValue(props.getProperty(key)));
            }
        } catch (Exception e) {
            System.err.println("Error loading settings from file: " + e.getMessage());
        }
    }

    private Object parseValue(String value) {
        if (value == null) return null;

        // Try to parse as integer
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            // Not an integer
        }

        // Try to parse as boolean
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;

        // Return as string
        return value;
    }

    // FIX 2: Added annotation
    @SuppressWarnings("unchecked")
    public void mergeSettings(Map<String, Object> newSettings) {
        for (Map.Entry<String, Object> entry : newSettings.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (settings.containsKey(key) && value instanceof Map && settings.get(key) instanceof Map) {
                mergeSettingsDict((Map<String, Object>) settings.get(key), (Map<String, Object>) value);
            } else {
                settings.put(key, value);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void mergeSettingsDict(Map<String, Object> target, Map<String, Object> source) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (target.containsKey(key) && value instanceof Map && target.get(key) instanceof Map) {
                mergeSettingsDict((Map<String, Object>) target.get(key), (Map<String, Object>) value);
            } else {
                target.put(key, value);
            }
        }
    }

    public boolean saveSettings() {
        try {
            saveToPreferences();
            saveToFile();
            return true;
        } catch (Exception e) {
            System.err.println("Error saving settings: " + e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private void saveToPreferences() {
        try {
            for (String category : settings.keySet()) {
                Map<String, Object> categorySettings = (Map<String, Object>) settings.get(category);
                for (Map.Entry<String, Object> entry : categorySettings.entrySet()) {
                    String prefKey = category + "." + entry.getKey();
                    Object value = entry.getValue();

                    if (value instanceof Integer) {
                        prefs.putInt(prefKey, (Integer) value);
                    } else if (value instanceof Boolean) {
                        prefs.putBoolean(prefKey, (Boolean) value);
                    } else if (value instanceof String) {
                        prefs.put(prefKey, (String) value);
                    }
                }
            }
            prefs.flush();
        } catch (Exception e) {
            System.err.println("Error saving to preferences: " + e.getMessage());
        }
    }

    private void saveToFile() {
        Properties props = new Properties();

        // Flatten the nested settings structure
        flattenSettings(settings, "", props);

        try (FileWriter writer = new FileWriter(configFile)) {
            props.store(writer, "Harmony Pro Settings");
        } catch (Exception e) {
            System.err.println("Error saving to file: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void flattenSettings(Map<String, Object> settings, String prefix, Properties props) {
        for (Map.Entry<String, Object> entry : settings.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();

            if (value instanceof Map) {
                flattenSettings((Map<String, Object>) value, key, props);
            } else {
                props.setProperty(key, value.toString());
            }
        }
    }

    @SuppressWarnings("unchecked")
    public Object get(String keyPath, Object defaultValue) {
        String[] keys = keyPath.split("\\.");
        Object value = settings;

        try {
            for (String key : keys) {
                value = ((Map<String, Object>) value).get(key);
            }
            return value;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public int getInt(String keyPath, int defaultValue) {
        Object value = get(keyPath, defaultValue);
        if (value instanceof Integer) {
            return (Integer) value;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public boolean getBoolean(String keyPath, boolean defaultValue) {
        Object value = get(keyPath, defaultValue);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(value.toString());
    }

    public String getString(String keyPath, String defaultValue) {
        Object value = get(keyPath, defaultValue);
        return value != null ? value.toString() : defaultValue;
    }

    @SuppressWarnings("unchecked")
    public void set(String keyPath, Object value) {
        String[] keys = keyPath.split("\\.");
        Map<String, Object> current = settings;

        // Navigate to the parent of the final key
        for (int i = 0; i < keys.length - 1; i++) {
            String key = keys[i];
            if (!current.containsKey(key) || !(current.get(key) instanceof Map)) {
                current.put(key, new HashMap<String, Object>());
            }
            current = (Map<String, Object>) current.get(key);
        }

        // Set the final key
        current.put(keys[keys.length - 1], value);
    }

    public Map<String, Object> getSettings() {
        return new HashMap<>(settings);
    }

    public void setSettings(Map<String, Object> settings) {
        this.settings = new HashMap<>(settings);
    }
}