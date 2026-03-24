package com.musicplayer;

public class LyricsLine {
    private final long timestampMs;
    private final String text;

    public LyricsLine(long timestampMs, String text) {
        this.timestampMs = timestampMs;
        this.text = text;
    }

    public long getTimestampMs() {
        return timestampMs;
    }

    public String getText() {
        return text;
    }
}
