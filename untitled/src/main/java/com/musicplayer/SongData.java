// SongData.java
package com.musicplayer;

public class SongData {
    private String videoId;
    private String title;
    private String channel;
    private String thumbnailUrl;
    private String path;
    private String type;
    private String lyricsSearchHint;
    private long duration;
    private boolean guestPlaybackBlocked;
    private String playbackIssue;

    public String getDisplayName() {
        String base = title != null ? title : "Unknown Song";
        return guestPlaybackBlocked ? base + " (guest playback blocked)" : base;
    }

    // Getters and setters
    public String getVideoId() { return videoId; }
    public void setVideoId(String videoId) { this.videoId = videoId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getLyricsSearchHint() { return lyricsSearchHint; }
    public void setLyricsSearchHint(String lyricsSearchHint) { this.lyricsSearchHint = lyricsSearchHint; }

    public long getDuration() { return duration; }
    public void setDuration(long duration) { this.duration = duration; }

    public boolean isGuestPlaybackBlocked() { return guestPlaybackBlocked; }
    public void setGuestPlaybackBlocked(boolean guestPlaybackBlocked) { this.guestPlaybackBlocked = guestPlaybackBlocked; }

    public String getPlaybackIssue() { return playbackIssue; }
    public void setPlaybackIssue(String playbackIssue) { this.playbackIssue = playbackIssue; }
}
