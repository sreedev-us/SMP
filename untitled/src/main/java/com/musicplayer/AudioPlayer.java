// AudioPlayer.java
package com.musicplayer;

import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

import java.io.File;

/**
 * Audio player backed by JavaFX MediaPlayer.
 * Supports both local file paths and HTTP(S) stream URLs.
 */
public class AudioPlayer {

    private MediaPlayer mediaPlayer;
    private boolean isPlaying = false;
    private boolean isPaused = false;
    private double volume = 0.70; // 0.0 – 1.0
    private Runnable onEndOfMedia;
    private Runnable onReady;

    // ── Playback ──────────────────────────────────────────────────────────────

    /**
     * @param pathOrUrl  absolute file path OR an http(s):// stream URL
     */
    public void play(String pathOrUrl) {
        stop();

        String mediaUri;
        if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
            mediaUri = pathOrUrl;
        } else {
            // Convert OS path to URI
            mediaUri = new File(pathOrUrl).toURI().toString();
        }

        System.out.println("AudioPlayer: Loading media from URI: " + mediaUri);
        Media media = new Media(mediaUri);
        mediaPlayer = new MediaPlayer(media);
        mediaPlayer.setVolume(volume);

        mediaPlayer.setOnError(() -> {
            String errorMsg = mediaPlayer.getError() != null ? mediaPlayer.getError().getMessage() : "Unknown MediaPlayer Error";
            System.err.println("--- MediaPlayer CRITICAL Error ---");
            System.err.println("Message: " + errorMsg);
            if (mediaPlayer.getError() != null) {
                System.err.println("Error Type: " + mediaPlayer.getError().getType());
            }
            System.err.println("Media URI: " + mediaUri);
        });

        // Wire end-of-media callback (auto-advance)
        if (onEndOfMedia != null) {
            mediaPlayer.setOnEndOfMedia(onEndOfMedia);
        }
        if (onReady != null) {
            mediaPlayer.setOnReady(onReady);
        }

        mediaPlayer.play();
        isPlaying = true;
        isPaused  = false;
    }

    public void pause() {
        if (mediaPlayer != null && isPlaying) {
            mediaPlayer.pause();
            isPaused  = true;
            isPlaying = false;
        }
    }

    public void resume() {
        if (mediaPlayer != null && isPaused) {
            mediaPlayer.play();
            isPaused  = false;
            isPlaying = true;
        }
    }

    public void stop() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
            mediaPlayer = null;
        }
        isPlaying = false;
        isPaused  = false;
    }

    public void seek(long positionMs) {
        if (mediaPlayer != null) {
            mediaPlayer.seek(Duration.millis(positionMs));
        }
    }

    // ── Volume ────────────────────────────────────────────────────────────────

    /** @param volume  0 – 100 */
    public void setVolume(int volume) {
        this.volume = volume / 100.0;
        if (mediaPlayer != null) {
            mediaPlayer.setVolume(this.volume);
        }
    }

    // ── Position / Duration ───────────────────────────────────────────────────

    /** @return current playback position in milliseconds */
    public long getCurrentPosition() {
        if (mediaPlayer != null) {
            Duration pos = mediaPlayer.getCurrentTime();
            return pos == null ? 0 : (long) pos.toMillis();
        }
        return 0;
    }

    /** @return total duration in milliseconds (0 if unknown/buffering) */
    public long getDuration() {
        if (mediaPlayer != null) {
            Duration dur = mediaPlayer.getTotalDuration();
            if (dur != null && !dur.isUnknown() && !dur.isIndefinite()) {
                return (long) dur.toMillis();
            }
        }
        return 0;
    }

    // ── State ─────────────────────────────────────────────────────────────────

    public boolean isPlaying() { return isPlaying; }
    public boolean isPaused()  { return isPaused;  }

    /**
     * Register a callback that fires when the current track ends naturally.
     * Must be called BEFORE play().
     */
    public void setOnEndOfMedia(Runnable callback) {
        this.onEndOfMedia = callback;
        if (mediaPlayer != null) {
            mediaPlayer.setOnEndOfMedia(callback);
        }
    }

    public void setOnReady(Runnable callback) {
        this.onReady = callback;
        if (mediaPlayer != null) {
            mediaPlayer.setOnReady(callback);
        }
    }
}