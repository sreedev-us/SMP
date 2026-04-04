package com.musicplayer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Lightweight persistence for user-curated shelves such as liked songs.
 */
public class PlaylistShelfStore {

    private static final String NODE = "com/musicplayer/harmonypro/shelves";
    private static final String LIKED_KEY = "likedSongs";

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

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
