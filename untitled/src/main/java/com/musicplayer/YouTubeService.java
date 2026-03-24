package com.musicplayer;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoListResponse;
import com.github.kiulian.downloader.YoutubeDownloader;
import com.github.kiulian.downloader.downloader.request.RequestSearchResult;
import com.github.kiulian.downloader.downloader.request.RequestVideoInfo;
import com.github.kiulian.downloader.downloader.response.Response;
import com.github.kiulian.downloader.model.search.SearchResult;
import com.github.kiulian.downloader.model.search.SearchResultVideoDetails;
import com.github.kiulian.downloader.model.videos.VideoDetails;
import com.github.kiulian.downloader.model.videos.VideoInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class YouTubeService {

    private static final String APPLICATION_NAME = "Harmony Pro";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static HttpTransport HTTP_TRANSPORT;

    private final YouTube youtube;
    private final YoutubeDownloader downloader = new YoutubeDownloader();
    private boolean initialized = false;
    private final AuthSystem auth;

    public YouTubeService(AuthSystem auth) throws Exception {
        this.auth = auth;
        
        if (auth != null && auth.isRealLogin() && auth.getCredential() != null) {
            HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            Credential credential = auth.getCredential();

            // Build the real YouTube service
            this.youtube = new YouTube.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                    .setApplicationName(APPLICATION_NAME)
                    .build();

            this.initialized = true;
            System.out.println("Real YouTube Service initialized for " + auth.getCurrentUser());
        } else {
            this.youtube = null;
            this.initialized = true; // "Initialized" in guest mode
            System.out.println("YouTube Service initialized in GUEST/DEMO mode (using scraper fallback).");
        }
    }

    public List<YouTubeVideo> searchVideos(String query, int maxResults) throws IOException {
        if (!initialized) {
            throw new RuntimeException("YouTube service not initialized.");
        }

        if (youtube != null) {
            return searchVideosRealApi(query, maxResults);
        } else {
            return searchVideosScraper(query, maxResults);
        }
    }

    private List<YouTubeVideo> searchVideosRealApi(String query, int maxResults) throws IOException {
        System.out.println("Searching YouTube with real API for: " + query);
        
        YouTube.Search.List searchRequest = youtube.search()
                .list(Collections.singletonList("snippet"));
        searchRequest.setQ(query);
        searchRequest.setType(Collections.singletonList("video"));
        searchRequest.setVideoCategoryId("10"); // 10 = Music category
        searchRequest.setMaxResults((long) maxResults);
        searchRequest.setFields("items(id/videoId,snippet/title,snippet/channelTitle,snippet/thumbnails/default/url)");

        com.google.api.services.youtube.model.SearchListResponse searchResponse = searchRequest.execute();
        List<com.google.api.services.youtube.model.SearchResult> searchResultList = searchResponse.getItems();

        List<YouTubeVideo> results = new ArrayList<>();
        if (searchResultList != null) {
            for (com.google.api.services.youtube.model.SearchResult result : searchResultList) {
                YouTubeVideo video = new YouTubeVideo();
                video.setId(result.getId().getVideoId());
                video.setTitle(result.getSnippet().getTitle());
                video.setChannel(result.getSnippet().getChannelTitle());
                video.setThumbnailUrl(result.getSnippet().getThumbnails().getDefault().getUrl());
                video.setDuration("N/A");
                results.add(video);
            }
        }
        return results;
    }

    private List<YouTubeVideo> searchVideosScraper(String query, int maxResults) throws IOException {
        System.out.println("Searching YouTube with native Java scraper for: " + query);
        List<YouTubeVideo> videos = new ArrayList<>();
        
        try {
            RequestSearchResult request = new RequestSearchResult(query);
            Response<SearchResult> response = downloader.search(request);
            SearchResult result = response.data();
            
            int count = 0;
            for (SearchResultVideoDetails videoItem : result.videos()) {
                YouTubeVideo v = new YouTubeVideo();
                v.setId(videoItem.videoId());
                v.setTitle(videoItem.title());
                v.setChannel(videoItem.author());
                    
                    // Format duration if possible
                    int durationSeconds = videoItem.lengthSeconds();
                    int mins = durationSeconds / 60;
                    int secs = durationSeconds % 60;
                    v.setDuration(String.format("%d:%02d", mins, secs));
                    
                    v.setThumbnailUrl("https://i.ytimg.com/vi/" + v.getId() + "/hqdefault.jpg");
                    videos.add(v);
                    
                    count++;
                    if (count >= maxResults) break;
                }
        } catch (Exception e) {
            System.err.println("Native scraper search failed: " + e.getMessage());
        }
        
        return videos;
    }

    // Note: The YouTube API does not provide audio stream URLs.
    // This functionality is not possible without third-party libraries.

    public YouTubeVideo getVideoDetails(String videoId) throws IOException {
        if (!initialized) {
            throw new RuntimeException("YouTube service not initialized.");
        }

        if (youtube != null) {
            YouTube.Videos.List videoRequest = youtube.videos()
                    .list(List.of("snippet", "contentDetails", "statistics"));
            videoRequest.setId(Collections.singletonList(videoId));
            videoRequest.setFields("items(snippet/title,snippet/channelTitle,snippet/description,snippet/thumbnails/high/url,contentDetails/duration,statistics/viewCount)");

            VideoListResponse response = videoRequest.execute();
            if (response.getItems() == null || response.getItems().isEmpty()) {
                return null;
            }

            Video video = response.getItems().get(0);
            YouTubeVideo details = new YouTubeVideo();
            details.setId(videoId);
            details.setTitle(video.getSnippet().getTitle());
            details.setChannel(video.getSnippet().getChannelTitle());
            details.setDescription(video.getSnippet().getDescription());
            details.setThumbnailUrl(video.getSnippet().getThumbnails().getHigh().getUrl());
            details.setViewCount(video.getStatistics().getViewCount().longValue());

            // Convert ISO 8601 Duration (e.g., "PT3M45S") to "3:45"
            details.setDuration(formatISODuration(video.getContentDetails().getDuration()));

            return details;
        } else {
            // Native scraper fallback for details
            try {
                RequestVideoInfo request = new RequestVideoInfo(videoId);
                Response<VideoInfo> response = downloader.getVideoInfo(request);
                VideoInfo info = response.data();
                VideoDetails video = info.details();
                
                YouTubeVideo details = new YouTubeVideo();
                details.setId(videoId);
                details.setTitle(video.title());
                details.setChannel(video.author());
                details.setDescription(video.description());
                details.setThumbnailUrl("https://i.ytimg.com/vi/" + videoId + "/maxresdefault.jpg");
                
                int durationSeconds = video.lengthSeconds();
                int mins = durationSeconds / 60;
                int secs = durationSeconds % 60;
                details.setDuration(String.format("%d:%02d", mins, secs));
                
                details.setViewCount(video.viewCount());
                return details;
            } catch (Exception e) {
                System.err.println("Native scraper details failed: " + e.getMessage());
            }
            return null;
        }
    }

    private String formatISODuration(String isoDuration) {
        // This is a simplified parser for PT#M#S format
        try {
            String time = isoDuration.substring(2);
            long minutes = 0;
            long seconds = 0;
            if (time.contains("M")) {
                minutes = Long.parseLong(time.substring(0, time.indexOf("M")));
                time = time.substring(time.indexOf("M") + 1);
            }
            if (time.contains("S")) {
                seconds = Long.parseLong(time.substring(0, time.indexOf("S")));
            }
            return String.format("%d:%02d", minutes, seconds);
        } catch (Exception e) {
            return "0:00";
        }
    }
}

// Keep the YouTubeVideo class at the bottom (or in its own file)
class YouTubeVideo {
    // ... (This class is unchanged from your version) ...
    private String id;
    private String title;
    private String channel;
    private String duration;
    private String thumbnailUrl;
    private long viewCount;
    private String publishedDate;
    private String description;

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }
    public long getViewCount() { return viewCount; }
    public void setViewCount(long viewCount) { this.viewCount = viewCount; }
    public String getPublishedDate() { return publishedDate; }
    public void setPublishedDate(String publishedDate) { this.publishedDate = publishedDate; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    @Override
    public String toString() {
        return title + " - " + channel + " (" + duration + ")";
    }
}