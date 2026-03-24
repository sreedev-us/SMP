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

    public String getDisplayName() {
        return title != null ? title : "Unknown Song";
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
}
