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
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class YtDlpStreamResolver {

    private static final int TIMEOUT_SECONDS = 120;
    private static Path tempDir = null;
    private static final Config downloaderConfig = new Config.Builder()
        .maxRetries(5)
        .build();
    private static final YoutubeDownloader downloader = new YoutubeDownloader(downloaderConfig);

    public static String resolve(String videoId) throws Exception {
        if (videoId == null || videoId.isBlank()) {
            throw new IllegalArgumentException("Missing YouTube video ID");
        }

        // If on mobile, prefer streaming URL directly to avoid slow downloads & permission issues
        if (AppPlatform.isMobile()) {
            try {
                return resolveStreamUrl(videoId);
            } catch (Throwable e) {
                System.err.println("--- RESOLVER CRITICAL THROWABLE ---");
                e.printStackTrace();
                System.err.println("Direct streaming resolution failed, falling back to download: " + e.getMessage());
            }
        }

        if (tempDir == null || !tempDir.toFile().exists()) {
            tempDir = Files.createTempDirectory("harmony-audio-");
            tempDir.toFile().deleteOnExit();
        }

        File outputFile = tempDir.resolve(videoId + ".m4a").toFile();
        if (outputFile.exists() && outputFile.length() > 10_000) {
            System.out.println("Using cached audio: " + outputFile.getAbsolutePath());
            return outputFile.getAbsolutePath();
        }

        if (isAvailable()) {
            try {
                return resolveWithYtDlp(videoId, outputFile);
            } catch (Exception ytDlpFailure) {
                System.err.println("yt-dlp resolver failed, falling back to Java downloader: " + ytDlpFailure.getMessage());
            }
        }

        return resolveWithJavaDownloader(videoId, outputFile);
    }

    public static String resolveStreamUrl(String videoId) throws Exception {
        ClientType[] clients = {ClientType.ANDROID, ClientType.WEB};
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

                // Prefer m4a/mp4 for JavaFX compatibility
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

                System.out.println("Direct stream resolved via [" + client + "]: " + formatUrl.substring(0, Math.min(formatUrl.length(), 100)) + "...");
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
        File partFile = tempDir.resolve(videoId + ".m4a.part").toFile();
        if (partFile.exists()) {
            partFile.delete();
        }
        File webmFile = tempDir.resolve(videoId + ".webm").toFile();
        if (webmFile.exists() && webmFile.length() < 100_000) {
            webmFile.delete();
        }

        String videoUrl = "https://www.youtube.com/watch?v=" + videoId;
        String outputTemplate = tempDir.resolve(videoId + ".%(ext)s").toString();

        ProcessBuilder pb = new ProcessBuilder(List.of(
            "yt-dlp",
            "-f", "bestaudio[ext=m4a]/bestaudio[ext=mp4]/bestaudio",
            "--no-playlist",
            "--no-part",
            "--retries", "1",
            "--buffer-size", "16k",
            "-o", outputTemplate,
            videoUrl
        ));
        pb.redirectErrorStream(false);

        System.out.println("Downloading audio with yt-dlp for: " + videoId);
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

        File mp4File = tempDir.resolve(videoId + ".mp4").toFile();
        if (mp4File.exists() && mp4File.length() > 10_000) {
            System.out.println("Download complete (mp4): " + mp4File.getName());
            return mp4File.getAbsolutePath();
        }

        int exitCode = process.exitValue();
        throw new RuntimeException(
            "yt-dlp did not produce an audio file (exit=" + exitCode + "). Tip: install ffmpeg for better format support."
        );
    }

    private static String resolveWithJavaDownloader(String videoId, File outputFile) throws Exception {
        System.out.println("Native Java download started for: " + videoId);
        ClientType[] clients = {ClientType.ANDROID, ClientType.WEB};
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
        if (tempDir != null) {
            File dir = tempDir.toFile();
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
            dir.delete();
        }
    }
}
