package com.musicplayer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

/**
 * Lightweight persistence for user-curated shelves such as liked songs.
 */
public class PlaylistShelfStore {

    private static final String NODE = "com/musicplayer/harmonypro/shelves";
    private static final String LIKED_KEY = "likedSongs";
    private static final String RECENT_KEY = "recentPlayedSongs";
    private static final String CUSTOM_PLAYLISTS_KEY = "customPlaylists";

    private final Preferences prefs;

    public PlaylistShelfStore() {
        Preferences resolved;
        try {
            resolved = Preferences.userRoot().node(NODE);
        } catch (Exception | LinkageError ex) {
            resolved = null;
        }
        this.prefs = resolved;
    }

    public List<SongData> loadLikedSongs() {
        List<SongData> likedSongs = new ArrayList<>();
        if (prefs == null) {
            return likedSongs;
        }

        String raw = prefs.get(LIKED_KEY, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                SongData song = new SongData();
                song.setVideoId(item.optString("videoId", ""));
                song.setTitle(item.optString("title", ""));
                song.setChannel(item.optString("channel", ""));
                song.setThumbnailUrl(item.optString("thumbnailUrl", ""));
                song.setType(item.optString("type", ""));
                song.setPath(item.optString("path", ""));
                song.setLyricsSearchHint(item.optString("lyricsSearchHint", ""));
                likedSongs.add(song);
            }
        } catch (Exception ignored) {
        }
        return likedSongs;
    }

    public void saveLikedSongs(List<SongData> likedSongs) {
        if (prefs == null) {
            return;
        }
        JSONArray array = new JSONArray();
        for (SongData song : likedSongs) {
            JSONObject item = new JSONObject();
            item.put("videoId", safe(song.getVideoId()));
            item.put("title", safe(song.getTitle()));
            item.put("channel", safe(song.getChannel()));
            item.put("thumbnailUrl", safe(song.getThumbnailUrl()));
            item.put("type", safe(song.getType()));
            item.put("path", safe(song.getPath()));
            item.put("lyricsSearchHint", safe(song.getLyricsSearchHint()));
            array.put(item);
        }
        prefs.put(LIKED_KEY, array.toString());
        try {
            prefs.flush();
        } catch (Exception ignored) {
        }
    }

    public List<SongData> loadRecentPlayedSongs() {
        return loadSongList(RECENT_KEY);
    }

    public void saveRecentPlayedSongs(List<SongData> recentPlayedSongs) {
        saveSongList(RECENT_KEY, recentPlayedSongs);
    }

    public Map<String, List<SongData>> loadCustomPlaylists() {
        Map<String, List<SongData>> playlists = new LinkedHashMap<>();
        if (prefs == null) {
            return playlists;
        }

        String raw = prefs.get(CUSTOM_PLAYLISTS_KEY, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String name = item.optString("name", "").trim();
                if (name.isBlank()) {
                    continue;
                }
                JSONArray songsArray = item.optJSONArray("songs");
                List<SongData> songs = new ArrayList<>();
                if (songsArray != null) {
                    for (int j = 0; j < songsArray.length(); j++) {
                        JSONObject songJson = songsArray.optJSONObject(j);
                        SongData song = fromJson(songJson);
                        if (song != null) {
                            songs.add(song);
                        }
                    }
                }
                playlists.put(name, songs);
            }
        } catch (Exception ignored) {
        }
        return playlists;
    }

    public void saveCustomPlaylists(Map<String, ? extends List<SongData>> playlists) {
        if (prefs == null) {
            return;
        }

        JSONArray array = new JSONArray();
        for (Map.Entry<String, ? extends List<SongData>> entry : playlists.entrySet()) {
            String name = entry.getKey() == null ? "" : entry.getKey().trim();
            if (name.isBlank()) {
                continue;
            }
            JSONObject playlistJson = new JSONObject();
            playlistJson.put("name", name);
            JSONArray songsArray = new JSONArray();
            for (SongData song : entry.getValue()) {
                songsArray.put(toJson(song));
            }
            playlistJson.put("songs", songsArray);
            array.put(playlistJson);
        }
        prefs.put(CUSTOM_PLAYLISTS_KEY, array.toString());
        flushPrefs();
    }

    private List<SongData> loadSongList(String key) {
        List<SongData> songs = new ArrayList<>();
        if (prefs == null) {
            return songs;
        }

        String raw = prefs.get(key, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                SongData song = fromJson(array.optJSONObject(i));
                if (song != null) {
                    songs.add(song);
                }
            }
        } catch (Exception ignored) {
        }
        return songs;
    }

    private void saveSongList(String key, List<SongData> songs) {
        if (prefs == null) {
            return;
        }
        JSONArray array = new JSONArray();
        for (SongData song : songs) {
            array.put(toJson(song));
        }
        prefs.put(key, array.toString());
        flushPrefs();
    }

    private JSONObject toJson(SongData song) {
        JSONObject item = new JSONObject();
        item.put("videoId", safe(song.getVideoId()));
        item.put("title", safe(song.getTitle()));
        item.put("channel", safe(song.getChannel()));
        item.put("thumbnailUrl", safe(song.getThumbnailUrl()));
        item.put("type", safe(song.getType()));
        item.put("path", safe(song.getPath()));
        item.put("lyricsSearchHint", safe(song.getLyricsSearchHint()));
        return item;
    }

    private SongData fromJson(JSONObject item) {
        if (item == null) {
            return null;
        }
        SongData song = new SongData();
        song.setVideoId(item.optString("videoId", ""));
        song.setTitle(item.optString("title", ""));
        song.setChannel(item.optString("channel", ""));
        song.setThumbnailUrl(item.optString("thumbnailUrl", ""));
        song.setType(item.optString("type", ""));
        song.setPath(item.optString("path", ""));
        song.setLyricsSearchHint(item.optString("lyricsSearchHint", ""));
        return song;
    }

    private void flushPrefs() {
        try {
            prefs.flush();
        } catch (Exception ignored) {
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
