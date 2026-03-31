package com.musicplayer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class RelatedTrackHelper {

    private RelatedTrackHelper() {
    }

    public static List<String> buildQueries(SongData baseSong) {
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        String title = cleanSongTitle(baseSong == null ? null : baseSong.getTitle());
        String channel = normalizeWhitespace(baseSong == null ? null : baseSong.getChannel());

        addIfPresent(queries, "songs like " + title);
        addIfPresent(queries, title + " radio");
        addIfPresent(queries, title + " similar music");
        addIfPresent(queries, title + " playlist");
        addIfPresent(queries, title + " mix");
        addIfPresent(queries, channel + " songs");
        addIfPresent(queries, channel + " music mix");

        return new ArrayList<>(queries);
    }

    public static boolean isTooSimilar(SongData baseSong, String candidateTitle, String candidateChannel) {
        String baseTitle = normalizeSongSignature(baseSong == null ? null : baseSong.getTitle());
        String otherTitle = normalizeSongSignature(candidateTitle);
        if (baseTitle.isBlank() || otherTitle.isBlank()) {
            return false;
        }

        if (baseTitle.equals(otherTitle)) {
            return true;
        }

        if (baseTitle.contains(otherTitle) || otherTitle.contains(baseTitle)) {
            return true;
        }

        Set<String> baseTokens = tokenizeTitle(baseTitle);
        Set<String> otherTokens = tokenizeTitle(otherTitle);
        if (!baseTokens.isEmpty() && !otherTokens.isEmpty()) {
            int shared = 0;
            for (String token : baseTokens) {
                if (otherTokens.contains(token)) {
                    shared++;
                }
            }
            double overlap = shared / (double) Math.max(baseTokens.size(), otherTokens.size());
            if (overlap >= 0.6) {
                return true;
            }
        }

        String baseChannel = normalizeChannelName(baseSong == null ? null : baseSong.getChannel());
        String otherChannel = normalizeChannelName(candidateChannel);
        return !baseChannel.isBlank()
                && baseChannel.equals(otherChannel)
                && baseTokens.equals(otherTokens);
    }

    private static void addIfPresent(Set<String> queries, String value) {
        String normalized = normalizeWhitespace(value);
        if (!normalized.isBlank()) {
            queries.add(normalized);
        }
    }

    public static String cleanSongTitle(String title) {
        String cleaned = normalizeWhitespace(title)
                .replaceAll("\\(.*?\\)|\\[.*?\\]", " ")
                .replaceAll("(?i)\\b(feat|ft|featuring|official|lyrics|lyric video|video|audio|hd|4k|remaster(ed)?|live|cover|version|visualizer)\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return cleaned.isBlank() ? normalizeWhitespace(title) : cleaned;
    }

    public static String normalizeSongSignature(String title) {
        return cleanSongTitle(title)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public static String normalizeChannelName(String channel) {
        return normalizeWhitespace(channel)
                .toLowerCase(Locale.ROOT)
                .replaceAll("(?i)\\b(topic|official|vevo|music|records|recordings)\\b", " ")
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public static String normalizeWhitespace(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private static Set<String> tokenizeTitle(String title) {
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        for (String token : title.split("\\s+")) {
            if (token.length() >= 3) {
                tokens.add(token);
            }
        }
        return tokens;
    }
}
