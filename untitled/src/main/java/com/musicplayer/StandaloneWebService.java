package com.musicplayer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Server-side state for the standalone browser application.
 * This runs independently from the JavaFX app.
 */
public class StandaloneWebService {

    private final YouTubeService youtubeService;
    private final LyricsService lyricsService = new LyricsService();
    private final PlaylistShelfStore shelfStore = new PlaylistShelfStore();
    private final List<SongData> queue = new ArrayList<>();
    private final List<SongData> likedSongs = new ArrayList<>();
    private final List<SongData> recommendations = new ArrayList<>();
    private final Map<String, SongData> searchCache = new ConcurrentHashMap<>();
    private final AtomicLong audioVersion = new AtomicLong();

    private int currentSongIndex = -1;
    private boolean shuffleEnabled;
    private boolean repeatEnabled;
    private boolean autoRadioEnabled;
    private boolean paused = true;
    private long playbackStartedAtMs;
    private long pausedPositionMs;
    private long requestedStartPositionMs;
    private long knownDurationMs;
    private String currentResolvedSource;
    private LyricsData currentLyricsData;
    private String lastStatus = "Standalone web player ready.";
    private Runnable stateListener;

    public StandaloneWebService() throws Exception {
        DemoAuthSystem auth = new DemoAuthSystem();
        auth.googleLogin();
        this.youtubeService = new YouTubeService(auth);
        this.likedSongs.addAll(shelfStore.loadLikedSongs());
    }

    public synchronized void setStateListener(Runnable stateListener) {
        this.stateListener = stateListener;
    }

    public synchronized JSONObject getState() {
        JSONObject json = ok("State loaded.");
        json.put("status", lastStatus);
        json.put("shuffleEnabled", shuffleEnabled);
        json.put("repeatEnabled", repeatEnabled);
        json.put("autoRadioEnabled", autoRadioEnabled);
        json.put("isPaused", paused);
        json.put("isPlaying", currentSongIndex >= 0 && !paused);
        json.put("currentSongIndex", currentSongIndex);
        json.put("currentTimeMs", getCurrentPositionMs());
        json.put("requestedStartMs", requestedStartPositionMs);
        json.put("durationMs", knownDurationMs);
        json.put("audioVersion", audioVersion.get());
        json.put("audioUrl", currentSongIndex >= 0 ? "/audio/current?v=" + audioVersion.get() : "");

        JSONArray queueJson = new JSONArray();
        for (int i = 0; i < queue.size(); i++) {
            SongData song = queue.get(i);
            queueJson.put(songToJson(song, i == currentSongIndex, i));
        }
        json.put("queue", queueJson);
        JSONArray likedJson = new JSONArray();
        for (int i = 0; i < likedSongs.size(); i++) {
            likedJson.put(songToJson(likedSongs.get(i), false, i));
        }
        json.put("likedSongs", likedJson);
        JSONArray recommendationsJson = new JSONArray();
        for (int i = 0; i < recommendations.size(); i++) {
            recommendationsJson.put(songToJson(recommendations.get(i), false, i));
        }
        json.put("recommendations", recommendationsJson);
        json.put("currentSong", currentSongIndex >= 0 && currentSongIndex < queue.size()
                ? songToJson(queue.get(currentSongIndex), true, currentSongIndex)
                : JSONObject.NULL);
        return json;
    }

    public synchronized JSONObject getLyricsPayload() {
        JSONObject payload = new JSONObject();
        SongData current = currentSongIndex >= 0 && currentSongIndex < queue.size() ? queue.get(currentSongIndex) : null;
        payload.put("title", current != null ? safe(current.getTitle()) : "");
        payload.put("artist", current != null ? safe(current.getChannel()) : "");
        payload.put("synced", currentLyricsData != null && currentLyricsData.isSynced());
        payload.put("source", currentLyricsData != null ? safe(currentLyricsData.getSource()) : "Waiting");
        JSONArray lines = new JSONArray();
        if (currentLyricsData != null) {
            for (LyricsLine line : currentLyricsData.getLines()) {
                JSONObject item = new JSONObject();
                item.put("timeMs", line.getTimestampMs());
                item.put("text", line.getText());
                lines.put(item);
            }
        }
        payload.put("lines", lines);
        return payload;
    }

    public synchronized JSONObject search(String query) throws Exception {
        String normalized = RelatedTrackHelper.normalizeWhitespace(query);
        if (normalized.isBlank()) {
            return error("Search query is empty.");
        }

        List<YouTubeVideo> videos = youtubeService.searchVideos(normalized, 15);
        JSONArray results = new JSONArray();
        for (YouTubeVideo v : videos) {
            SongData song = new SongData();
            song.setVideoId(v.getId());
            song.setTitle(v.getTitle());
            song.setChannel(v.getChannel());
            song.setThumbnailUrl(v.getThumbnailUrl());
            song.setType("youtube");
            song.setLyricsSearchHint(normalized);
            searchCache.put(song.getVideoId(), song);
            results.put(songToJson(song, false, -1));
        }

        JSONObject json = ok("Found " + results.length() + " results.");
        json.put("results", results);
        return json;
    }

    public synchronized JSONObject addToQueue(String videoId, boolean playNow) {
        SongData cached = searchCache.get(videoId);
        if (cached == null) {
            return error("Song was not found in recent search results.");
        }

        int existingIndex = indexOfSong(videoId);
        if (existingIndex == -1) {
            queue.add(copySong(cached));
            existingIndex = queue.size() - 1;
        }

        if (playNow) {
            return playIndex(existingIndex);
        }

        lastStatus = "Added to queue: " + safe(cached.getTitle());
        notifyStateChanged();
        return withState(ok(lastStatus));
    }

    public synchronized JSONObject playIndex(int index) {
        if (index < 0 || index >= queue.size()) {
            return error("Queue index out of range.");
        }
        SongData song = queue.get(index);
        try {
            resolveAndStart(song, index, 0);
            lastStatus = "Playing " + safe(song.getTitle());
            return withState(ok(lastStatus));
        } catch (Exception e) {
            lastStatus = "Playback failed: " + e.getMessage();
            return error(lastStatus);
        }
    }

    public synchronized JSONObject togglePlayPause(long positionMs) {
        if (currentSongIndex < 0 && !queue.isEmpty()) {
            return playIndex(0);
        }
        if (currentSongIndex < 0) {
            return error("Queue is empty.");
        }
        if (paused) {
            paused = false;
            requestedStartPositionMs = Math.max(0, positionMs);
            playbackStartedAtMs = System.currentTimeMillis() - requestedStartPositionMs;
            lastStatus = "Resumed playback.";
        } else {
            pausedPositionMs = Math.max(0, positionMs);
            requestedStartPositionMs = pausedPositionMs;
            paused = true;
            lastStatus = "Paused playback.";
        }
        notifyStateChanged();
        return withState(ok(lastStatus));
    }

    public synchronized JSONObject next(long positionMs) {
        if (queue.isEmpty()) {
            return error("Queue is empty.");
        }
        pausedPositionMs = Math.max(0, positionMs);
        if (repeatEnabled && currentSongIndex >= 0) {
            return playIndex(currentSongIndex);
        }
        int nextIndex;
        if (shuffleEnabled && queue.size() > 1) {
            nextIndex = currentSongIndex;
            while (nextIndex == currentSongIndex) {
                nextIndex = (int) (Math.random() * queue.size());
            }
        } else {
            nextIndex = currentSongIndex + 1;
            if (nextIndex >= queue.size()) {
                if (autoRadioEnabled) {
                    JSONObject related = addRelatedSongs();
                    if (!related.optBoolean("ok")) {
                        return related;
                    }
                    nextIndex = currentSongIndex + 1;
                }
                if (nextIndex >= queue.size()) {
                    nextIndex = 0;
                }
            }
        }
        return playIndex(nextIndex);
    }

    public synchronized JSONObject previous(long positionMs) {
        if (queue.isEmpty()) {
            return error("Queue is empty.");
        }
        if (positionMs > 5000 && currentSongIndex >= 0) {
            return playIndex(currentSongIndex);
        }
        return playIndex(Math.max(0, currentSongIndex - 1));
    }

    public synchronized JSONObject seek(long positionMs) {
        pausedPositionMs = Math.max(0, positionMs);
        requestedStartPositionMs = pausedPositionMs;
        if (!paused) {
            playbackStartedAtMs = System.currentTimeMillis() - pausedPositionMs;
        }
        notifyStateChanged();
        return withState(ok("Seeked playback."));
    }

    public synchronized JSONObject removeQueueIndex(int index) {
        if (index < 0 || index >= queue.size()) {
            return error("Queue index out of range.");
        }
        queue.remove(index);
        if (queue.isEmpty()) {
            currentSongIndex = -1;
            paused = true;
            pausedPositionMs = 0;
            requestedStartPositionMs = 0;
            currentResolvedSource = null;
            currentLyricsData = null;
            knownDurationMs = 0;
            audioVersion.incrementAndGet();
        } else if (index == currentSongIndex) {
            currentSongIndex = Math.min(index, queue.size() - 1);
            paused = true;
            pausedPositionMs = 0;
            requestedStartPositionMs = 0;
            currentResolvedSource = null;
            currentLyricsData = null;
            knownDurationMs = 0;
            audioVersion.incrementAndGet();
        } else if (index < currentSongIndex) {
            currentSongIndex--;
        }
        lastStatus = "Queue item removed.";
        notifyStateChanged();
        return withState(ok(lastStatus));
    }

    public synchronized JSONObject clearQueue() {
        queue.clear();
        currentSongIndex = -1;
        paused = true;
        pausedPositionMs = 0;
        requestedStartPositionMs = 0;
        knownDurationMs = 0;
        currentResolvedSource = null;
        currentLyricsData = null;
        audioVersion.incrementAndGet();
        lastStatus = "Queue cleared.";
        notifyStateChanged();
        return withState(ok(lastStatus));
    }

    public synchronized JSONObject setToggle(String toggle, boolean enabled) {
        switch (safe(toggle).toLowerCase(Locale.ROOT)) {
            case "shuffle" -> shuffleEnabled = enabled;
            case "repeat" -> repeatEnabled = enabled;
            case "autoradio" -> autoRadioEnabled = enabled;
            default -> {
                return error("Unknown toggle: " + toggle);
            }
        }
        lastStatus = toggle + " " + (enabled ? "enabled" : "disabled") + ".";
        notifyStateChanged();
        return withState(ok(lastStatus));
    }

    public synchronized JSONObject addRelatedSongs() {
        SongData baseSong = currentSongIndex >= 0 && currentSongIndex < queue.size()
                ? queue.get(currentSongIndex)
                : (queue.isEmpty() ? null : queue.get(queue.size() - 1));
        if (baseSong == null) {
            return error("Queue is empty.");
        }

        try {
            int added = appendRelatedSongs(baseSong);
            lastStatus = added > 0 ? "Added " + added + " related songs." : "No new related songs found.";
            notifyStateChanged();
            return withState(ok(lastStatus));
        } catch (Exception e) {
            lastStatus = "Related songs failed: " + e.getMessage();
            return error(lastStatus);
        }
    }

    public synchronized String getCurrentAudioSource() {
        return currentResolvedSource;
    }

    private int appendRelatedSongs(SongData baseSong) throws Exception {
        int added = 0;
        recommendations.clear();
        for (String query : RelatedTrackHelper.buildQueries(baseSong)) {
            if (added >= 5) {
                break;
            }
            List<YouTubeVideo> videos = youtubeService.searchVideos(query, 8);
            for (YouTubeVideo v : videos) {
                if (!isRelatedCandidate(baseSong, v)) {
                    continue;
                }
                SongData song = new SongData();
                song.setVideoId(v.getId());
                song.setTitle(v.getTitle());
                song.setChannel(v.getChannel());
                song.setThumbnailUrl(v.getThumbnailUrl());
                song.setType("youtube");
                song.setLyricsSearchHint(query);
                recommendations.add(copySong(song));
                queue.add(song);
                added++;
                if (added >= 5) {
                    break;
                }
            }
        }
        return added;
    }

    private boolean isRelatedCandidate(SongData baseSong, YouTubeVideo candidate) {
        if (candidate == null || safe(candidate.getId()).isBlank()) {
            return false;
        }
        if (safe(candidate.getId()).equals(safe(baseSong.getVideoId()))) {
            return false;
        }
        if (indexOfSong(candidate.getId()) != -1) {
            return false;
        }
        return !RelatedTrackHelper.isTooSimilar(baseSong, candidate.getTitle(), candidate.getChannel());
    }

    private void resolveAndStart(SongData song, int index, long startAtMs) throws Exception {
        currentResolvedSource = resolveAudioSource(song);
        currentSongIndex = index;
        paused = false;
        pausedPositionMs = Math.max(0, startAtMs);
        requestedStartPositionMs = pausedPositionMs;
        playbackStartedAtMs = System.currentTimeMillis() - pausedPositionMs;
        knownDurationMs = 0;
        audioVersion.incrementAndGet();
        refreshLyrics(song);
        notifyStateChanged();
    }

    private void refreshLyrics(SongData song) {
        try {
            currentLyricsData = lyricsService.fetchLyrics(song, knownDurationMs);
        } catch (Exception e) {
            currentLyricsData = null;
        }
    }

    private String resolveAudioSource(SongData song) throws Exception {
        if (song == null || safe(song.getVideoId()).isBlank()) {
            throw new IllegalArgumentException("Song is missing a video id.");
        }
        try {
            return YtDlpStreamResolver.resolveStreamUrl(song.getVideoId());
        } catch (Exception ignored) {
            return YtDlpStreamResolver.resolve(song.getVideoId());
        }
    }

    private JSONObject songToJson(SongData song, boolean current, int index) {
        JSONObject item = new JSONObject();
        item.put("index", index);
        item.put("videoId", safe(song.getVideoId()));
        item.put("title", safe(song.getTitle()));
        item.put("channel", safe(song.getChannel()));
        item.put("thumbnailUrl", safe(song.getThumbnailUrl()));
        item.put("type", safe(song.getType()));
        item.put("current", current);
        item.put("liked", indexOfLikedSong(song.getVideoId()) >= 0);
        return item;
    }

    private SongData copySong(SongData original) {
        SongData copy = new SongData();
        copy.setVideoId(original.getVideoId());
        copy.setTitle(original.getTitle());
        copy.setChannel(original.getChannel());
        copy.setThumbnailUrl(original.getThumbnailUrl());
        copy.setType(original.getType());
        copy.setLyricsSearchHint(original.getLyricsSearchHint());
        copy.setPath(original.getPath());
        return copy;
    }

    private int indexOfSong(String videoId) {
        for (int i = 0; i < queue.size(); i++) {
            if (safe(queue.get(i).getVideoId()).equals(videoId)) {
                return i;
            }
        }
        return -1;
    }

    private int indexOfLikedSong(String videoId) {
        for (int i = 0; i < likedSongs.size(); i++) {
            if (safe(likedSongs.get(i).getVideoId()).equals(videoId)) {
                return i;
            }
        }
        return -1;
    }

    public synchronized JSONObject toggleLiked(String videoId) {
        SongData song = resolveSongForWishlist(videoId);
        if (song == null) {
            return error("Song not found for wishlist.");
        }
        int existingIndex = indexOfLikedSong(song.getVideoId());
        if (existingIndex >= 0) {
            likedSongs.remove(existingIndex);
            lastStatus = "Removed from wishlist: " + safe(song.getTitle());
        } else {
            likedSongs.add(0, copySong(song));
            lastStatus = "Saved to wishlist: " + safe(song.getTitle());
        }
        shelfStore.saveLikedSongs(likedSongs);
        notifyStateChanged();
        return withState(ok(lastStatus));
    }

    public synchronized JSONObject playLiked(String videoId) {
        int index = indexOfLikedSong(videoId);
        if (index < 0) {
            return error("Saved song not found.");
        }
        SongData song = copySong(likedSongs.get(index));
        int queueIndex = indexOfSong(song.getVideoId());
        if (queueIndex < 0) {
            queue.add(song);
            queueIndex = queue.size() - 1;
        }
        return playIndex(queueIndex);
    }

    public synchronized JSONObject removeLiked(String videoId) {
        int index = indexOfLikedSong(videoId);
        if (index < 0) {
            return error("Saved song not found.");
        }
        SongData removed = likedSongs.remove(index);
        shelfStore.saveLikedSongs(likedSongs);
        lastStatus = "Removed from wishlist: " + safe(removed.getTitle());
        notifyStateChanged();
        return withState(ok(lastStatus));
    }

    private SongData resolveSongForWishlist(String videoId) {
        String safeVideoId = safe(videoId);
        if (!safeVideoId.isBlank()) {
            SongData cached = searchCache.get(safeVideoId);
            if (cached != null) {
                return cached;
            }
            int queueIndex = indexOfSong(safeVideoId);
            if (queueIndex >= 0) {
                return queue.get(queueIndex);
            }
            int likedIndex = indexOfLikedSong(safeVideoId);
            if (likedIndex >= 0) {
                return likedSongs.get(likedIndex);
            }
            for (SongData song : recommendations) {
                if (safe(song.getVideoId()).equals(safeVideoId)) {
                    return song;
                }
            }
        }
        if (currentSongIndex >= 0 && currentSongIndex < queue.size()) {
            return queue.get(currentSongIndex);
        }
        return null;
    }

    private long getCurrentPositionMs() {
        if (currentSongIndex < 0) {
            return 0;
        }
        if (paused) {
            return pausedPositionMs;
        }
        return Math.max(0, System.currentTimeMillis() - playbackStartedAtMs);
    }

    private JSONObject ok(String message) {
        JSONObject json = new JSONObject();
        json.put("ok", true);
        json.put("message", message);
        return json;
    }

    private JSONObject error(String message) {
        JSONObject json = new JSONObject();
        json.put("ok", false);
        json.put("message", message);
        return json;
    }

    private JSONObject withState(JSONObject json) {
        json.put("state", getState());
        return json;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void notifyStateChanged() {
        if (stateListener != null) {
            stateListener.run();
        }
    }
}
