package com.musicplayer;

import com.github.kiulian.downloader.Config;
import com.github.kiulian.downloader.YoutubeDownloader;
import com.github.kiulian.downloader.downloader.client.ClientType;
import com.github.kiulian.downloader.downloader.request.RequestVideoInfo;
import com.github.kiulian.downloader.downloader.response.Response;
import com.github.kiulian.downloader.model.videos.VideoInfo;
import com.github.kiulian.downloader.model.videos.formats.AudioFormat;
import com.github.kiulian.downloader.model.videos.formats.Format;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class YtDlpStreamResolver {

    private static final int TIMEOUT_SECONDS = 120;
    private static final Path CACHE_DIR = Path.of(
        System.getProperty("user.home"),
        ".harmony-pro-audio-cache");
    private static final Config downloaderConfig = new Config.Builder()
        .maxRetries(10)
        .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36")
        .build();
    private static final YoutubeDownloader downloader = new YoutubeDownloader(downloaderConfig);

    public static String resolve(String videoId) throws Exception {
        if (videoId == null || videoId.isBlank()) {
            throw new IllegalArgumentException("Missing YouTube video ID");
        }

        // Prefer direct/native stream resolution first on every platform. It avoids
        // the frequent yt-dlp 429/bot-check path and is much snappier when it works.
        try {
            return resolveStreamUrl(videoId);
        } catch (Throwable e) {
            System.err.println("Direct streaming resolution failed, trying download fallbacks: " + e.getMessage());
        }

        ensureCacheDirectory();

        File outputFile = CACHE_DIR.resolve(videoId + ".m4a").toFile();
        if (outputFile.exists() && outputFile.length() > 10_000) {
            System.out.println("Using cached audio: " + outputFile.getAbsolutePath());
            return outputFile.getAbsolutePath();
        }
        File cachedMp4 = CACHE_DIR.resolve(videoId + ".mp4").toFile();
        if (cachedMp4.exists() && cachedMp4.length() > 10_000) {
            System.out.println("Using cached audio: " + cachedMp4.getAbsolutePath());
            return cachedMp4.getAbsolutePath();
        }

        try {
            return resolveWithJavaDownloader(videoId, outputFile);
        } catch (Exception javaFailure) {
            System.err.println("Native Java downloader failed, falling back to yt-dlp: " + javaFailure.getMessage());
        }

        if (isAvailable()) {
            try {
                return resolveWithYtDlp(videoId, outputFile);
            } catch (Exception ytDlpFailure) {
                System.err.println("yt-dlp resolver failed after native fallback: " + ytDlpFailure.getMessage());
            }
        }

        throw new RuntimeException("Could not resolve playable audio for this YouTube track right now.");
    }

    public static String resolveStreamUrl(String videoId) throws Exception {
        // Ordered by likelihood of bypassing YouTube's guest-mode restrictions.
        // TVHTML5_SIMPLY_EMBEDDED_PLAYER and IOS clients typically don't need PO tokens.
        ClientType[] clients = {
            ClientType.TVHTML5_SIMPLY_EMBEDDED_PLAYER,
            ClientType.IOS,
            ClientType.ANDROID,
            ClientType.WEB
        };
        Throwable lastError = null;

        for (ClientType client : clients) {
            try {
                System.out.println("Resolving direct stream URL via [" + client + "] for: " + videoId);
                RequestVideoInfo request = new RequestVideoInfo(videoId).clientType(client);
                Response<VideoInfo> response = downloader.getVideoInfo(request);

                VideoInfo info = response.data();
                if (info == null) {
                    System.err.println("Client [" + client + "] returned no metadata.");
                    continue;
                }

                List<AudioFormat> audioFormats = info.audioFormats();
                if (audioFormats == null || audioFormats.isEmpty()) {
                    System.err.println("Client [" + client + "] found no audio streams.");
                    continue;
                }

                // Prefer m4a/mp4 for JavaFX MediaPlayer compatibility
                AudioFormat bestFormat = audioFormats.stream()
                    .filter(f -> f.extension().toString().toLowerCase().contains("m4a")
                        || f.extension().toString().toLowerCase().contains("mp4"))
                    .max(Comparator.comparingInt(Format::bitrate))
                    .orElse(audioFormats.get(0));

                String formatUrl = bestFormat.url();
                if (formatUrl == null || formatUrl.isBlank()) {
                    System.err.println("Client [" + client + "] selected stream has no URL.");
                    continue;
                }

                // Basic sanity check — must be an https URL
                if (!formatUrl.startsWith("http://") && !formatUrl.startsWith("https://")) {
                    System.err.println("Client [" + client + "] returned a non-HTTP URL, skipping.");
                    continue;
                }

                System.out.println("Direct stream resolved via [" + client + "]: "
                    + formatUrl.substring(0, Math.min(formatUrl.length(), 100)) + "...");
                return formatUrl;

            } catch (Throwable t) {
                lastError = t;
                System.err.println("Client [" + client + "] error: " + t.getMessage());
            }
        }

        throw createPlaybackException(videoId,
                lastError != null ? lastError.getMessage() : "All streaming clients failed",
                lastError);
    }

    private static String resolveWithYtDlp(String videoId, File outputFile) throws Exception {
        File partFile = CACHE_DIR.resolve(videoId + ".m4a.part").toFile();
        if (partFile.exists()) {
            partFile.delete();
        }
        File webmFile = CACHE_DIR.resolve(videoId + ".webm").toFile();
        if (webmFile.exists() && webmFile.length() < 100_000) {
            webmFile.delete();
        }

        String videoUrl = "https://www.youtube.com/watch?v=" + videoId;
        String outputTemplate = CACHE_DIR.resolve(videoId + ".%(ext)s").toString();
        Exception lastFailure = null;
        for (List<String> cookieArgs : buildCookieStrategies()) {
            cleanupPartialDownloads(outputFile, videoId);
            try {
                return runYtDlpAttempt(videoId, outputFile, outputTemplate, videoUrl, cookieArgs);
            } catch (Exception ex) {
                lastFailure = ex;
                System.err.println("yt-dlp attempt failed" + formatCookieAttempt(cookieArgs) + ": " + ex.getMessage());
            }
        }

        throw lastFailure != null
            ? lastFailure
            : new RuntimeException("yt-dlp could not produce an audio file.");
    }

    private static String runYtDlpAttempt(String videoId, File outputFile, String outputTemplate, String videoUrl,
            List<String> cookieArgs) throws Exception {
        List<String> command = new ArrayList<>(List.of(
            "yt-dlp",
            "-f", "bestaudio[ext=m4a]/bestaudio[ext=mp4]/bestaudio",
            "--no-playlist",
            "--no-part",
            "--retries", "1",
            "--buffer-size", "16k"
        ));
        command.addAll(cookieArgs);
        command.addAll(List.of("-o", outputTemplate, videoUrl));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(false);

        System.out.println("Downloading audio with yt-dlp for: " + videoId + formatCookieAttempt(cookieArgs));
        Process process = pb.start();

        Thread stdoutThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[yt-dlp] " + line);
                }
            } catch (Exception ignored) {
            }
        });
        Thread stderrThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.err.println("[yt-dlp] " + line);
                }
            } catch (Exception ignored) {
            }
        });
        stdoutThread.start();
        stderrThread.start();

        boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        stdoutThread.join(3000);
        stderrThread.join(3000);

        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("yt-dlp timed out after " + TIMEOUT_SECONDS + "s");
        }

        if (outputFile.exists() && outputFile.length() > 10_000) {
            System.out.println("Download complete: " + outputFile.getName()
                + " (" + outputFile.length() / 1024 + " KB)");
            return outputFile.getAbsolutePath();
        }

        File mp4File = CACHE_DIR.resolve(videoId + ".mp4").toFile();
        if (mp4File.exists() && mp4File.length() > 10_000) {
            System.out.println("Download complete (mp4): " + mp4File.getName());
            return mp4File.getAbsolutePath();
        }

        int exitCode = process.exitValue();
        throw new RuntimeException(
            "yt-dlp did not produce an audio file (exit=" + exitCode + "). Tip: install ffmpeg for better format support."
        );
    }

    private static List<List<String>> buildCookieStrategies() {
        List<List<String>> strategies = new ArrayList<>();
        strategies.add(List.of());
        for (String browser : List.of("edge", "chrome", "brave", "firefox")) {
            strategies.add(List.of("--cookies-from-browser", browser));
        }
        return strategies;
    }

    private static String formatCookieAttempt(List<String> cookieArgs) {
        if (cookieArgs == null || cookieArgs.isEmpty()) {
            return "";
        }
        return " using " + String.join(" ", cookieArgs);
    }

    private static void cleanupPartialDownloads(File outputFile, String videoId) {
        if (outputFile.exists() && outputFile.length() < 10_000) {
            outputFile.delete();
        }
        for (String ext : List.of("mp4", "webm", "m4a", "part")) {
            File candidate = CACHE_DIR.resolve(videoId + "." + ext).toFile();
            if (candidate.exists() && candidate.length() < 10_000) {
                candidate.delete();
            }
        }
    }

    private static void ensureCacheDirectory() throws Exception {
        if (!Files.exists(CACHE_DIR)) {
            Files.createDirectories(CACHE_DIR);
        }
    }

    private static String resolveWithJavaDownloader(String videoId, File outputFile) throws Exception {
        System.out.println("Native Java download started for: " + videoId);
        ClientType[] clients = {
            ClientType.TVHTML5_SIMPLY_EMBEDDED_PLAYER,
            ClientType.IOS,
            ClientType.ANDROID,
            ClientType.WEB
        };
        Throwable lastError = null;

        for (ClientType client : clients) {
            try {
                RequestVideoInfo request = new RequestVideoInfo(videoId).clientType(client);
                Response<VideoInfo> response = downloader.getVideoInfo(request);
                VideoInfo info = response.data();
                if (info == null) continue;

                List<AudioFormat> audioFormats = info.audioFormats();
                if (audioFormats == null || audioFormats.isEmpty()) continue;

                AudioFormat bestFormat = audioFormats.stream()
                    .filter(f -> f.extension().toString().toLowerCase().contains("m4a")
                        || f.extension().toString().toLowerCase().contains("mp4"))
                    .max(Comparator.comparingInt(Format::bitrate))
                    .orElse(audioFormats.get(0));

                String formatUrl = bestFormat.url();
                if (formatUrl == null || formatUrl.isBlank()) continue;

                URL url = new URL(formatUrl);
                try (InputStream in = url.openStream();
                     OutputStream out = new FileOutputStream(outputFile)) {
                    byte[] buffer = new byte[64 * 1024];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                }

                if (outputFile.exists() && outputFile.length() > 10_000) {
                    System.out.println("Native download complete via [" + client + "]: " + outputFile.getName());
                    return outputFile.getAbsolutePath();
                }
            } catch (Throwable t) {
                lastError = t;
                System.err.println("Native client [" + client + "] error: " + t.getMessage());
            }
        }

        throw createPlaybackException(videoId,
                lastError != null ? lastError.getMessage() : "All download clients failed",
                lastError);
    }

    private static Exception createPlaybackException(String videoId, String message, Throwable cause) {
        String fullMessage = "Could not load audio info for " + videoId + ": " + message;
        if (isGuestPlaybackRestriction(message)) {
            return new GuestPlaybackUnavailableException(
                "This video can't be played in Guest Mode. Try another result or sign in for fuller playback access.",
                cause);
        }
        return cause == null ? new RuntimeException(fullMessage) : new RuntimeException(fullMessage, cause);
    }

    private static boolean isGuestPlaybackRestriction(String message) {
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase();
        return normalized.contains("login_required")
            || normalized.contains("sign in to confirm you're not a bot")
            || normalized.contains("streamingdata not found")
            || normalized.contains("playabilitystatus")
            || normalized.contains("403")
            || normalized.contains("forbidden")
            || normalized.contains("429");
    }

    public static boolean isAvailable() {
        try {
            Process process = new ProcessBuilder("yt-dlp", "--version")
                .redirectErrorStream(true)
                .start();
            process.waitFor(5, TimeUnit.SECONDS);
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static void cleanup() {
        try {
            ensureCacheDirectory();
            File[] files = CACHE_DIR.toFile().listFiles();
            if (files == null) {
                return;
            }
            for (File file : files) {
                if (file.getName().endsWith(".part") || file.length() < 10_000) {
                    file.delete();
                }
            }
        } catch (Exception e) {
            System.err.println("Audio cache cleanup skipped: " + e.getMessage());
        }
    }
}
