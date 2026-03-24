package com.musicplayer;

import com.github.kiulian.downloader.YoutubeDownloader;
import com.github.kiulian.downloader.downloader.request.RequestVideoInfo;
import com.github.kiulian.downloader.downloader.response.Response;
import com.github.kiulian.downloader.model.videos.VideoInfo;
import com.github.kiulian.downloader.model.videos.formats.AudioFormat;
import com.github.kiulian.downloader.model.videos.formats.Format;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * Downloads a YouTube video's audio to a temp M4A file using native Java.
 * (No longer requires yt-dlp!)
 */
public class YtDlpStreamResolver {

    private static final int TIMEOUT_SECONDS = 120;
    private static Path tempDir = null;

    public static String resolve(String videoId) throws Exception {
        // Create a temp directory once per app session
        if (tempDir == null || !tempDir.toFile().exists()) {
            tempDir = Files.createTempDirectory("harmony-audio-");
            tempDir.toFile().deleteOnExit();
        }

        File outputFile = tempDir.resolve(videoId + ".m4a").toFile();

        // Return cached file if already downloaded this session
        if (outputFile.exists() && outputFile.length() > 10_000) {
            System.out.println("Using cached audio: " + outputFile.getAbsolutePath());
            return outputFile.getAbsolutePath();
        }

        System.out.println("Native download started for: " + videoId);
        YoutubeDownloader downloader = new YoutubeDownloader();
        RequestVideoInfo request = new RequestVideoInfo(videoId);
        Response<VideoInfo> response = downloader.getVideoInfo(request);
        VideoInfo info = response.data();

        // Select the best M4A/AAC audio-only format
        List<AudioFormat> audioFormats = info.audioFormats();
        if (audioFormats.isEmpty()) throw new RuntimeException("No audio streams found for " + videoId);

        AudioFormat bestFormat = audioFormats.stream()
                .filter(f -> f.extension().toString().toLowerCase().contains("m4a") || f.extension().toString().toLowerCase().contains("mp4"))
                .max(Comparator.comparingInt(Format::bitrate))
                .orElse(audioFormats.get(0));

        System.out.println("Downloading best audio format: " + bestFormat.bitrate() + "kbps " + bestFormat.extension().toString());

        // Use the format URL to download manually (simple stream copy)
        URL url = new URL(bestFormat.url());
        try (InputStream in = url.openStream();
             OutputStream out = new FileOutputStream(outputFile)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }

        if (outputFile.exists() && outputFile.length() > 10_000) {
            System.out.println("Native download complete: " + outputFile.getName()
                    + " (" + outputFile.length() / 1024 + " KB)");
            return outputFile.getAbsolutePath();
        }

        throw new RuntimeException("Native download failed to produce a valid file for " + videoId);
    }

    /** Checks if the service is available (always true now). */
    public static boolean isAvailable() {
        return true;
    }

    /** Delete all temp audio files (call on app shutdown). */
    public static void cleanup() {
        if (tempDir != null) {
            File dir = tempDir.toFile();
            File[] files = dir.listFiles();
            if (files != null) for (File f : files) f.delete();
            dir.delete();
        }
    }
}
