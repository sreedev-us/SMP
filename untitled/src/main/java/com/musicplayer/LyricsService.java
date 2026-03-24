package com.musicplayer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class LyricsService {
    private static final String LRCLIB_GET_URL = "https://lrclib.net/api/get";
    private static final String LRCLIB_SEARCH_URL = "https://lrclib.net/api/search";

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .build();

    public LyricsData fetchLyrics(SongData song, long durationMs) {
        if (song == null) {
            return new LyricsData(List.of(), false, "None");
        }

        if (cleanTitle(song.getTitle()).isBlank()) {
            return new LyricsData(List.of(), false, "None");
        }

        for (LookupAttempt attempt : buildLookupAttempts(song, durationMs)) {
            LyricsData exact = tryExactLookup(attempt);
            if (!exact.isEmpty()) {
                return exact;
            }

            LyricsData search = trySearchLookup(attempt);
            if (!search.isEmpty()) {
                return search;
            }
        }

        return new LyricsData(List.of(), false, "None");
    }

    private LyricsData tryExactLookup(LookupAttempt attempt) {
        try {
            JSONObject exactMatch = requestJsonObject(buildLookupUri(attempt.title(), attempt.artist(), attempt.durationMs()));
            return parseLyrics(exactMatch);
        } catch (Exception ignored) {
            return new LyricsData(List.of(), false, "None");
        }
    }

    private LyricsData trySearchLookup(LookupAttempt attempt) {
        try {
            JSONArray searchResults = requestJsonArray(buildSearchUri(attempt.title(), attempt.artist(), attempt.durationMs()));
            JSONObject bestMatch = selectBestMatch(searchResults, attempt.title(), attempt.artist());
            return bestMatch == null ? new LyricsData(List.of(), false, "None") : parseLyrics(bestMatch);
        } catch (Exception ignored) {
            return new LyricsData(List.of(), false, "None");
        }
    }

    private List<LookupAttempt> buildLookupAttempts(SongData song, long durationMs) {
        String cleanedTitle = cleanTitle(song.getTitle());
        String cleanedArtist = cleanArtist(song.getChannel(), song.getType());
        String cleanedHint = cleanSearchHint(song.getLyricsSearchHint());

        Set<String> seen = new LinkedHashSet<>();
        List<LookupAttempt> attempts = new ArrayList<>();

        addAttempt(attempts, seen, cleanedTitle, cleanedArtist, durationMs);
        addAttempt(attempts, seen, cleanedTitle, cleanedArtist, 0);

        for (String titleVariant : buildTitleVariants(song.getTitle(), cleanedHint)) {
            addAttempt(attempts, seen, titleVariant, cleanedArtist, durationMs);
            addAttempt(attempts, seen, titleVariant, cleanedArtist, 0);
            addAttempt(attempts, seen, titleVariant, "", durationMs);
            addAttempt(attempts, seen, titleVariant, "", 0);
        }

        if (!cleanedHint.isBlank()) {
            addAttempt(attempts, seen, cleanedHint, cleanedArtist, durationMs);
            addAttempt(attempts, seen, cleanedHint, cleanedArtist, 0);
            addAttempt(attempts, seen, cleanedHint, "", durationMs);
            addAttempt(attempts, seen, cleanedHint, "", 0);
        }

        return attempts;
    }

    private void addAttempt(List<LookupAttempt> attempts, Set<String> seen, String title, String artist, long durationMs) {
        String cleanedTitle = cleanTitle(title);
        String cleanedArtist = cleanArtist(artist, "youtube");
        if (cleanedTitle.isBlank()) {
            return;
        }

        String key = cleanedTitle + "|" + cleanedArtist + "|" + durationMs;
        if (seen.add(key)) {
            attempts.add(new LookupAttempt(cleanedTitle, cleanedArtist, durationMs));
        }
    }

    private List<String> buildTitleVariants(String rawTitle, String hint) {
        Set<String> variants = new LinkedHashSet<>();
        variants.add(cleanTitle(rawTitle));
        variants.add(stripFeaturedArtists(cleanTitle(rawTitle)));

        String dashedTitle = rawTitle == null ? "" : rawTitle;
        if (dashedTitle.contains(" - ")) {
            String[] parts = dashedTitle.split("\\s+-\\s+", 2);
            if (parts.length == 2) {
                variants.add(cleanTitle(parts[1]));
                variants.add(stripFeaturedArtists(cleanTitle(parts[1])));
                variants.add(cleanTitle(parts[0]));
            }
        }

        if (!hint.isBlank()) {
            variants.add(cleanTitle(hint));
            variants.add(stripFeaturedArtists(cleanTitle(hint)));
            if (hint.contains(" - ")) {
                String[] parts = hint.split("\\s+-\\s+", 2);
                if (parts.length == 2) {
                    variants.add(cleanTitle(parts[1]));
                    variants.add(stripFeaturedArtists(cleanTitle(parts[1])));
                }
            }
        }

        return variants.stream()
            .filter(value -> value != null && !value.isBlank())
            .toList();
    }

    private URI buildLookupUri(String title, String artist, long durationMs) {
        StringBuilder url = new StringBuilder(LRCLIB_GET_URL)
            .append("?track_name=").append(encode(title));

        if (!artist.isBlank()) {
            url.append("&artist_name=").append(encode(artist));
        }
        if (durationMs > 0) {
            url.append("&duration=").append(durationMs);
        }
        return URI.create(url.toString());
    }

    private URI buildSearchUri(String title, String artist, long durationMs) {
        StringBuilder url = new StringBuilder(LRCLIB_SEARCH_URL)
            .append("?track_name=").append(encode(title));

        if (!artist.isBlank()) {
            url.append("&artist_name=").append(encode(artist));
        }
        if (durationMs > 0) {
            url.append("&duration=").append(durationMs);
        }
        return URI.create(url.toString());
    }

    private JSONObject requestJsonObject(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(10))
            .header("Accept", "application/json")
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300 || response.body() == null || response.body().isBlank()) {
            throw new IOException("Lyrics lookup failed with status " + response.statusCode());
        }
        return new JSONObject(response.body());
    }

    private JSONArray requestJsonArray(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(10))
            .header("Accept", "application/json")
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300 || response.body() == null || response.body().isBlank()) {
            throw new IOException("Lyrics search failed with status " + response.statusCode());
        }
        return new JSONArray(response.body());
    }

    private JSONObject selectBestMatch(JSONArray results, String title, String artist) {
        if (results == null || results.isEmpty()) {
            return null;
        }

        List<JSONObject> candidates = new ArrayList<>();
        for (int i = 0; i < results.length(); i++) {
            candidates.add(results.getJSONObject(i));
        }

        return candidates.stream()
            .max(Comparator.comparingInt(candidate -> matchScore(candidate, title, artist)))
            .orElse(null);
    }

    private int matchScore(JSONObject candidate, String title, String artist) {
        String track = normalize(candidate.optString("trackName"));
        String artistName = normalize(candidate.optString("artistName"));
        int score = 0;

        String normalizedTitle = normalize(title);
        String normalizedArtist = normalize(artist);

        if (!normalizedTitle.isBlank()) {
            if (track.equals(normalizedTitle)) {
                score += 5;
            } else if (track.contains(normalizedTitle) || normalizedTitle.contains(track)) {
                score += 3;
            }
        }

        if (!normalizedArtist.isBlank()) {
            if (artistName.equals(normalizedArtist)) {
                score += 4;
            } else if (artistName.contains(normalizedArtist) || normalizedArtist.contains(artistName)) {
                score += 2;
            }
        }

        if (!candidate.optString("syncedLyrics").isBlank()) {
            score += 3;
        }
        return score;
    }

    private LyricsData parseLyrics(JSONObject json) {
        if (json == null) {
            return new LyricsData(List.of(), false, "None");
        }

        String syncedLyrics = json.optString("syncedLyrics", "");
        if (!syncedLyrics.isBlank()) {
            List<LyricsLine> parsedSynced = parseSyncedLyrics(syncedLyrics);
            if (!parsedSynced.isEmpty()) {
                return new LyricsData(parsedSynced, true, "LRCLIB");
            }
        }

        String plainLyrics = json.optString("plainLyrics", "");
        if (!plainLyrics.isBlank()) {
            List<LyricsLine> parsedPlain = parsePlainLyrics(plainLyrics);
            if (!parsedPlain.isEmpty()) {
                return new LyricsData(parsedPlain, false, "LRCLIB");
            }
        }

        return new LyricsData(List.of(), false, "None");
    }

    private List<LyricsLine> parseSyncedLyrics(String rawLyrics) {
        List<LyricsLine> lines = new ArrayList<>();
        String[] splitLines = rawLyrics.split("\\R");

        for (String rawLine : splitLines) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isBlank() || !line.startsWith("[")) {
                continue;
            }

            int closingIndex = line.indexOf(']');
            if (closingIndex <= 1 || closingIndex >= line.length() - 1) {
                continue;
            }

            String timestamp = line.substring(1, closingIndex);
            String text = line.substring(closingIndex + 1).trim();
            if (text.isBlank()) {
                continue;
            }

            long timestampMs = parseTimestamp(timestamp);
            if (timestampMs >= 0) {
                lines.add(new LyricsLine(timestampMs, text));
            }
        }

        return lines;
    }

    private List<LyricsLine> parsePlainLyrics(String rawLyrics) {
        List<LyricsLine> lines = new ArrayList<>();
        String[] splitLines = rawLyrics.split("\\R");
        for (String rawLine : splitLines) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (!line.isBlank()) {
                lines.add(new LyricsLine(-1, line));
            }
        }
        return lines;
    }

    private long parseTimestamp(String timestamp) {
        try {
            String[] minuteAndRest = timestamp.split(":");
            if (minuteAndRest.length != 2) {
                return -1;
            }

            long minutes = Long.parseLong(minuteAndRest[0]);
            String[] secondAndFraction = minuteAndRest[1].split("\\.");
            long seconds = Long.parseLong(secondAndFraction[0]);
            long hundredths = secondAndFraction.length > 1
                ? Long.parseLong((secondAndFraction[1] + "00").substring(0, 3))
                : 0;

            return minutes * 60_000 + seconds * 1_000 + hundredths;
        } catch (Exception e) {
            return -1;
        }
    }

    private String cleanTitle(String title) {
        if (title == null) {
            return "";
        }

        return title
            .replaceFirst("\\.[A-Za-z0-9]{2,4}$", "")
            .replaceAll("(?i)\\b(official video|official audio|lyrics|lyric video|music video|hd|4k|audio)\\b", "")
            .replaceAll("\\(.*?\\)|\\[.*?\\]", "")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private String cleanArtist(String artist, String type) {
        if (artist == null || artist.isBlank() || "local".equalsIgnoreCase(type) && "Local File".equalsIgnoreCase(artist)) {
            return "";
        }
        return artist
            .replaceAll("(?i)\\b(vevo|topic|official|records|music|channel)\\b", "")
            .replaceAll("(?i)\\s+-\\s+topic$", "")
            .replaceAll("(?i)\\b(ft\\.?|feat\\.?|featuring)\\b.*$", "")
            .replaceAll("[|]", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private String cleanSearchHint(String hint) {
        if (hint == null) {
            return "";
        }
        return hint
            .replaceAll("\\s+", " ")
            .trim();
    }

    private String stripFeaturedArtists(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replaceAll("(?i)\\b(ft\\.?|feat\\.?|featuring|with)\\b.*$", "")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private String normalize(String value) {
        return value == null
            ? ""
            : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record LookupAttempt(String title, String artist, long durationMs) {}
}
