package com.musicplayer;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public final class RemoteStreamResolver {

    private RemoteStreamResolver() {
    }

    public static boolean isConfigured(SettingsManager settings) {
        return settings != null && !settings.getString("youtube.stream_resolver_url", "").isBlank();
    }

    public static String resolve(SettingsManager settings, String videoId) throws Exception {
        if (settings == null) {
            throw new IllegalArgumentException("Settings are required for remote stream resolution");
        }

        String baseUrl = settings.getString("youtube.stream_resolver_url", "").trim();
        if (baseUrl.isBlank()) {
            throw new IllegalStateException("youtube.stream_resolver_url is not configured");
        }
        if (videoId == null || videoId.isBlank()) {
            throw new IllegalArgumentException("Missing YouTube video ID");
        }

        String separator = baseUrl.contains("?") ? "&" : "?";
        String requestUrl = baseUrl + separator + "videoId=" + URLEncoder.encode(videoId, StandardCharsets.UTF_8);

        HttpURLConnection connection = (HttpURLConnection) new URL(requestUrl).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("Accept", "application/json");

        String token = settings.getString("youtube.stream_resolver_token", "").trim();
        if (!token.isBlank()) {
            connection.setRequestProperty("Authorization", "Bearer " + token);
        }

        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 300
            ? connection.getInputStream()
            : connection.getErrorStream();

        String body = "{}";
        if (stream != null) {
            try (Scanner scanner = new Scanner(stream, StandardCharsets.UTF_8)) {
                scanner.useDelimiter("\\A");
                body = scanner.hasNext() ? scanner.next() : "{}";
            }
        }

        if (status < 200 || status >= 300) {
            throw new RuntimeException("Resolver service returned HTTP " + status + ": " + body);
        }

        JSONObject json = new JSONObject(body);
        String streamUrl = firstNonBlank(
            json.optString("streamUrl", ""),
            json.optString("audioUrl", ""),
            json.optString("url", "")
        );
        if (streamUrl == null) {
            throw new RuntimeException("Resolver response did not contain streamUrl/audioUrl/url");
        }
        return streamUrl;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
