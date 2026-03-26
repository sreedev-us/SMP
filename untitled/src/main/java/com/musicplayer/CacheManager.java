// CacheManager.java
package com.musicplayer;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.prefs.Preferences;

public class CacheManager {
    private Map<String, Object> searchCache;
    private Map<String, Object> videoCache;
    private String cacheDir = "cache";
    private Preferences cachePrefs;

    public CacheManager() {
        this.searchCache = new HashMap<>();
        this.videoCache = new HashMap<>();
        this.cachePrefs = initPreferences();
        initializeCache();
    }

    private Preferences initPreferences() {
        try {
            return Preferences.userRoot().node("com/musicplayer/harmonypro/cache");
        } catch (Exception | LinkageError e) {
            System.err.println("Cache preferences unavailable, using in-memory cache only: " + e.getMessage());
            return null;
        }
    }

    public void initializeCache() {
        try {
            Path cacheDirectory = Paths.get(cacheDir);
            if (!Files.exists(cacheDirectory)) {
                Files.createDirectories(cacheDirectory);
            }
            loadCacheFromPreferences();
        } catch (Exception e) {
            System.err.println("Cache initialization error: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void loadCacheFromPreferences() {
        if (cachePrefs == null) {
            return;
        }
        try {
            // Load search cache size
            int searchCacheSize = cachePrefs.getInt("searchCache.size", 0);
            for (int i = 0; i < searchCacheSize; i++) {
                String key = cachePrefs.get("searchCache.key." + i, "");
                if (!key.isEmpty()) {
                    String value = cachePrefs.get("searchCache.value." + i, "");
                    long timestamp = cachePrefs.getLong("searchCache.timestamp." + i, 0);

                    Map<String, Object> cacheEntry = new HashMap<>();
                    cacheEntry.put("value", value);
                    cacheEntry.put("timestamp", timestamp);
                    searchCache.put(key, cacheEntry);
                }
            }

            // Load video cache size
            int videoCacheSize = cachePrefs.getInt("videoCache.size", 0);
            for (int i = 0; i < videoCacheSize; i++) {
                String key = cachePrefs.get("videoCache.key." + i, "");
                if (!key.isEmpty()) {
                    String value = cachePrefs.get("videoCache.value." + i, "");
                    long timestamp = cachePrefs.getLong("videoCache.timestamp." + i, 0);

                    Map<String, Object> cacheEntry = new HashMap<>();
                    cacheEntry.put("value", value);
                    cacheEntry.put("timestamp", timestamp);
                    videoCache.put(key, cacheEntry);
                }
            }

            cleanExpiredCacheEntries();

        } catch (Exception e) {
            System.err.println("Error loading cache from preferences: " + e.getMessage());
        }
    }

    public void saveCache() {
        try {
            saveCacheToPreferences();
        } catch (Exception e) {
            System.err.println("Error saving cache: " + e.getMessage());
        }
    }

    private void saveCacheToPreferences() {
        if (cachePrefs == null) {
            return;
        }
        try {
            // Clear existing cache entries
            clearPreferencesCache();

            // Save search cache
            int searchIndex = 0;
            for (Map.Entry<String, Object> entry : searchCache.entrySet()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cacheEntry = (Map<String, Object>) entry.getValue();
                cachePrefs.put("searchCache.key." + searchIndex, entry.getKey());
                cachePrefs.put("searchCache.value." + searchIndex, cacheEntry.get("value").toString());
                cachePrefs.putLong("searchCache.timestamp." + searchIndex, (Long) cacheEntry.get("timestamp"));
                searchIndex++;
            }
            cachePrefs.putInt("searchCache.size", searchIndex);

            // Save video cache
            int videoIndex = 0;
            for (Map.Entry<String, Object> entry : videoCache.entrySet()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cacheEntry = (Map<String, Object>) entry.getValue();
                cachePrefs.put("videoCache.key." + videoIndex, entry.getKey());
                cachePrefs.put("videoCache.value." + videoIndex, cacheEntry.get("value").toString());
                cachePrefs.putLong("videoCache.timestamp." + videoIndex, (Long) cacheEntry.get("timestamp"));
                videoIndex++;
            }
            cachePrefs.putInt("videoCache.size", videoIndex);

            cachePrefs.flush();
        } catch (Exception e) {
            System.err.println("Error saving cache to preferences: " + e.getMessage());
        }
    }

    private void clearPreferencesCache() {
        if (cachePrefs == null) {
            return;
        }
        try {
            // Clear search cache
            int searchSize = cachePrefs.getInt("searchCache.size", 0);
            for (int i = 0; i < searchSize; i++) {
                cachePrefs.remove("searchCache.key." + i);
                cachePrefs.remove("searchCache.value." + i);
                cachePrefs.remove("searchCache.timestamp." + i);
            }

            // Clear video cache
            int videoSize = cachePrefs.getInt("videoCache.size", 0);
            for (int i = 0; i < videoSize; i++) {
                cachePrefs.remove("videoCache.key." + i);
                cachePrefs.remove("videoCache.value." + i);
                cachePrefs.remove("videoCache.timestamp." + i);
            }
        } catch (Exception e) {
            System.err.println("Error clearing preferences cache: " + e.getMessage());
        }
    }

    public void cleanExpiredCacheEntries() {
        long currentTime = System.currentTimeMillis();
        long cacheDuration = 24 * 60 * 60 * 1000; // 24 hours

        // Clean search cache
        searchCache.entrySet().removeIf(entry -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> cacheEntry = (Map<String, Object>) entry.getValue();
            long timestamp = (Long) cacheEntry.get("timestamp");
            return (currentTime - timestamp) > cacheDuration;
        });

        // Clean video cache
        videoCache.entrySet().removeIf(entry -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> cacheEntry = (Map<String, Object>) entry.getValue();
            long timestamp = (Long) cacheEntry.get("timestamp");
            return (currentTime - timestamp) > cacheDuration;
        });
    }

    public Object getSearchCache(String key) {
        @SuppressWarnings("unchecked")
        Map<String, Object> cacheEntry = (Map<String, Object>) searchCache.get(key);
        return cacheEntry != null ? cacheEntry.get("value") : null;
    }

    public void putSearchCache(String key, Object value) {
        Map<String, Object> cacheEntry = new HashMap<>();
        cacheEntry.put("value", value);
        cacheEntry.put("timestamp", System.currentTimeMillis());
        searchCache.put(key, cacheEntry);
        cleanExpiredCacheEntries();
    }

    public Object getVideoCache(String key) {
        @SuppressWarnings("unchecked")
        Map<String, Object> cacheEntry = (Map<String, Object>) videoCache.get(key);
        return cacheEntry != null ? cacheEntry.get("value") : null;
    }

    public void putVideoCache(String key, Object value) {
        Map<String, Object> cacheEntry = new HashMap<>();
        cacheEntry.put("value", value);
        cacheEntry.put("timestamp", System.currentTimeMillis());
        videoCache.put(key, cacheEntry);
        cleanExpiredCacheEntries();
    }

    public boolean clearAll() {
        try {
            searchCache.clear();
            videoCache.clear();
            clearPreferencesCache();
            saveCache();
            return true;
        } catch (Exception e) {
            System.err.println("Error clearing cache: " + e.getMessage());
            return false;
        }
    }

    public int getSearchCacheSize() {
        return searchCache.size();
    }

    public int getVideoCacheSize() {
        return videoCache.size();
    }
}
