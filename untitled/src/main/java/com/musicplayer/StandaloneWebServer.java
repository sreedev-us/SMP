package com.musicplayer;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

public class StandaloneWebServer {

    private final HttpServer server;
    private final StandaloneWebService service;
    private final List<OutputStream> sseClients = new CopyOnWriteArrayList<>();

    public StandaloneWebServer(int port) throws Exception {
        this.service = new StandaloneWebService();
        this.service.setStateListener(this::broadcastState);
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.server.createContext("/", this::handleApp);
        this.server.createContext("/player", this::handleApp);
        this.server.createContext("/audio/current", this::handleAudio);
        this.server.createContext("/events", this::handleEvents);
        this.server.createContext("/api/state", ex -> writeJson(ex, service.getState()));
        this.server.createContext("/api/search", ex -> writeSafeJson(ex, () -> service.search(queryParam(ex, "q"))));
        this.server.createContext("/api/queue/add", ex -> writeJson(ex, service.addToQueue(queryParam(ex, "videoId"), boolParam(ex, "play"))));
        this.server.createContext("/api/queue/play", ex -> writeJson(ex, service.playIndex(intParam(ex, "index"))));
        this.server.createContext("/api/queue/remove", ex -> writeJson(ex, service.removeQueueIndex(intParam(ex, "index"))));
        this.server.createContext("/api/queue/clear", ex -> writeJson(ex, service.clearQueue()));
        this.server.createContext("/api/toggle", ex -> writeJson(ex, service.setToggle(queryParam(ex, "name"), boolParam(ex, "enabled"))));
        this.server.createContext("/api/related", ex -> writeSafeJson(ex, service::addRelatedSongs));
        this.server.createContext("/api/playpause", ex -> writeJson(ex, service.togglePlayPause(longParam(ex, "positionMs"))));
        this.server.createContext("/api/next", ex -> writeJson(ex, service.next(longParam(ex, "positionMs"))));
        this.server.createContext("/api/prev", ex -> writeJson(ex, service.previous(longParam(ex, "positionMs"))));
        this.server.createContext("/api/seek", ex -> writeJson(ex, service.seek(longParam(ex, "positionMs"))));
        this.server.createContext("/api/lyrics", ex -> writeJson(ex, service.getLyricsPayload()));
        this.server.createContext("/manifest.json", ex -> serveResource(ex, "/com/musicplayer/manifest.json", "application/json"));
        this.server.createContext("/sw.js", ex -> serveResource(ex, "/com/musicplayer/sw.js", "application/javascript"));
        this.server.createContext("/app-icon.png", ex -> serveResource(ex, "/com/musicplayer/app-icon.png", "image/png"));
        this.server.setExecutor(Executors.newCachedThreadPool());
    }

    public static void main(String[] args) throws Exception {
        int port = 8090;
        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.isBlank()) {
            try {
                port = Integer.parseInt(envPort.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
            }
        }
        StandaloneWebServer app = new StandaloneWebServer(port);
        app.start();
    }

    public void start() {
        server.start();
        System.out.println("Harmony Pro standalone web app running on http://localhost:" + server.getAddress().getPort());
        System.out.println("Open /player if you want the dedicated browser-player route.");
    }

    private void handleApp(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (!"/".equals(path) && !"/player".equals(path)) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }
        serveResource(exchange, "/com/musicplayer/standalone-web.html", "text/html; charset=UTF-8");
    }

    private void handleAudio(HttpExchange exchange) throws IOException {
        String source = service.getCurrentAudioSource();
        if (source == null || source.isBlank()) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }

        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Accept-Ranges", "bytes");

        if (source.startsWith("http://") || source.startsWith("https://")) {
            proxyRemoteAudio(exchange, source);
        } else {
            proxyLocalAudio(exchange, source);
        }
    }

    private void proxyLocalAudio(HttpExchange exchange, String path) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(path, "r")) {
            long fileLength = file.length();
            exchange.getResponseHeaders().set("Content-Type", guessContentType(path));
            exchange.getResponseHeaders().set("Content-Length", String.valueOf(fileLength));
            exchange.sendResponseHeaders(200, fileLength);
            try (OutputStream os = exchange.getResponseBody()) {
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = file.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
            }
        }
    }

    private void proxyRemoteAudio(HttpExchange exchange, String source) throws IOException {
        URLConnection connection = new URL(source).openConnection();
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
        String contentType = connection.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = "audio/mpeg";
        }
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, 0);
        try (InputStream in = connection.getInputStream();
             OutputStream os = exchange.getResponseBody()) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                os.write(buffer, 0, read);
            }
        }
    }

    private void handleEvents(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.getResponseHeaders().set("Connection", "keep-alive");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(200, 0);

        OutputStream os = exchange.getResponseBody();
        sseClients.add(os);
        writeSse(os, "STATE");

        try {
            while (true) {
                Thread.sleep(15000);
                os.write(": ping\n\n".getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
        } catch (Exception ignored) {
        } finally {
            sseClients.remove(os);
            exchange.close();
        }
    }

    private void broadcastState() {
        for (OutputStream os : sseClients) {
            try {
                writeSse(os, "STATE");
            } catch (IOException e) {
                sseClients.remove(os);
                try {
                    os.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void writeSse(OutputStream os, String payload) throws IOException {
        os.write(("data: " + payload + "\n\n").getBytes(StandardCharsets.UTF_8));
        os.flush();
    }

    private void writeSafeJson(HttpExchange exchange, ThrowingJsonSupplier supplier) throws IOException {
        try {
            writeJson(exchange, supplier.get());
        } catch (Exception e) {
            JSONObject error = new JSONObject();
            error.put("ok", false);
            error.put("message", e.getMessage());
            writeJson(exchange, error);
        }
    }

    private void writeJson(HttpExchange exchange, JSONObject payload) throws IOException {
        byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void serveResource(HttpExchange exchange, String path, String contentType) throws IOException {
        InputStream in = getClass().getResourceAsStream(path);
        if (in == null) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }
        byte[] bytes;
        try (in) {
            bytes = in.readAllBytes();
        }
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String queryParam(HttpExchange exchange, String key) {
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw == null || raw.isBlank()) {
            return "";
        }
        for (String pair : raw.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length > 0 && java.net.URLDecoder.decode(parts[0], StandardCharsets.UTF_8).equals(key)) {
                return parts.length > 1 ? java.net.URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            }
        }
        return "";
    }

    private boolean boolParam(HttpExchange exchange, String key) {
        String value = queryParam(exchange, key);
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }

    private int intParam(HttpExchange exchange, String key) {
        try {
            return Integer.parseInt(queryParam(exchange, key));
        } catch (Exception e) {
            return -1;
        }
    }

    private long longParam(HttpExchange exchange, String key) {
        try {
            return Long.parseLong(queryParam(exchange, key));
        } catch (Exception e) {
            return 0L;
        }
    }

    private String guessContentType(String path) {
        String lower = path == null ? "" : path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".m4a") || lower.endsWith(".mp4")) return "audio/mp4";
        if (lower.endsWith(".ogg")) return "audio/ogg";
        if (lower.endsWith(".wav")) return "audio/wav";
        if (lower.endsWith(".aac")) return "audio/aac";
        return "audio/mpeg";
    }

    @FunctionalInterface
    private interface ThrowingJsonSupplier {
        JSONObject get() throws Exception;
    }
}
