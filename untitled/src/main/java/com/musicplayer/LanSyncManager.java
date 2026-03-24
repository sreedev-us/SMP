package com.musicplayer;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Manages LAN group playback sessions.
 *
 * HOST mode:
 *   - HTTP server on :8766   → serves the downloaded audio file to clients
 *   - TCP server on :8765    → broadcasts play/pause/seek control commands
 *   - UDP broadcaster on :8767 → lets clients auto-discover the host
 *
 * CLIENT mode:
 *   - Connects to host TCP :8765 → receives control commands
 *   - MediaPlayer streams audio from http://hostIp:8766/audio
 *
 * Protocol (one command per line over TCP):
 *   PLAY <videoId> <audioUrl> <startMs>
 *   PAUSE <posMs>
 *   RESUME <posMs>
 *   SEEK <posMs>
 *   STOP
 */
public class LanSyncManager {

    public enum Mode { IDLE, HOST, CLIENT }

    private static final int TCP_PORT   = 8081;
    private static final int HTTP_PORT  = 8080;
    static final int UDP_PORT  = 8767;
    private static final String UDP_MAGIC = "HARMONY_HOST:";

    private Mode mode = Mode.IDLE;
    private LanSyncListener listener;

    // HOST state
    private ServerSocket tcpServer;
    private HttpServer httpServer;
    private Thread udpBroadcastThread;
    private Thread tcpAcceptThread;
    private final List<PrintWriter> clients = new CopyOnWriteArrayList<>();
    private final List<PrintWriter> sseClients = new CopyOnWriteArrayList<>();
    private String currentAudioPath;
    private String localIp;
    
    // State Tracking for Late Joiners
    private String lastPlayVideoId;
    private String lastPlayAudioUrl;
    private long hostPositionMs;
    private boolean isPaused = false;
    private String currentTrackTitle = "";
    private String currentTrackArtist = "";
    private LyricsData currentLyricsData;

    // CLIENT state
    private Socket clientSocket;
    private Thread clientReaderThread;

    // TUNNEL state
    private Process tunnelProcess;

    // ── Public API ──────────────────────────────────────────────────────────────

    public void setListener(LanSyncListener listener) {
        this.listener = listener;
    }

    public Mode getMode() { return mode; }

    /**
     * Start hosting a session. Returns the local IP for display.
     * @throws Exception if ports are already in use.
     */
    public String startHosting() throws Exception {
        if (mode != Mode.IDLE) stopSession();

        localIp = getLocalIp();
        System.out.println("[DIAGNOSTIC] Detected Local IP: " + localIp);

        try {
            // 1. HTTP audio server + Web Player
            httpServer = HttpServer.create(new InetSocketAddress(HTTP_PORT), 0);
            httpServer.createContext("/", this::handleWebPlayerRequest);
            httpServer.createContext("/audio", this::handleAudioRequest);
            httpServer.createContext("/events", this::handleSseEvents);
            httpServer.createContext("/control", this::handleControlRequest);
            httpServer.createContext("/lyrics", this::handleLyricsRequest);
            httpServer.createContext("/manifest.json", ex -> serveResource(ex, "/com/musicplayer/manifest.json", "application/json"));
            httpServer.createContext("/sw.js", ex -> serveResource(ex, "/com/musicplayer/sw.js", "application/javascript"));
            httpServer.createContext("/app-icon.png", ex -> serveResource(ex, "/com/musicplayer/app-icon.png", "image/png"));
            httpServer.setExecutor(Executors.newCachedThreadPool());
            httpServer.start();
            System.out.println("[LAN] Web Player Server successfully started on port " + HTTP_PORT);
            System.out.println("[LAN] Share this link with your friends: http://" + localIp + ":" + HTTP_PORT);
        } catch (Exception e) {
            System.err.println("[LAN] CRITICAL: FAILED to start HTTP Server on port " + HTTP_PORT + ": " + e.getMessage());
            throw e;
        }

        try {
            // 2. TCP control server
            tcpServer = new ServerSocket(TCP_PORT);
            tcpAcceptThread = new Thread(this::tcpAcceptLoop, "lan-tcp-accept");
            tcpAcceptThread.setDaemon(true);
            tcpAcceptThread.start();
            System.out.println("[LAN] TCP Control Server started on port " + TCP_PORT);
        } catch (Exception e) {
            System.err.println("[LAN] CRITICAL: FAILED to start TCP Server on port " + TCP_PORT + ": " + e.getMessage());
            throw e;
        }

        // 3. UDP broadcaster (announce host presence every 2 sec)
        udpBroadcastThread = new Thread(this::udpBroadcastLoop, "lan-udp-broadcast");
        udpBroadcastThread.setDaemon(true);
        udpBroadcastThread.start();

        mode = Mode.HOST;
        System.out.println("[LAN] 🎉 Hosting mode is ACTIVE on http://" + localIp + ":" + HTTP_PORT);
        return localIp;
    }

    /**
     * Join an existing session hosted at hostIp.
     * @throws Exception if unable to connect.
     */
    public void joinSession(String hostIp) throws Exception {
        if (mode != Mode.IDLE) stopSession();

        clientSocket = new Socket(hostIp, TCP_PORT);
        clientReaderThread = new Thread(() -> clientReaderLoop(clientSocket), "lan-client-reader");
        clientReaderThread.setDaemon(true);
        clientReaderThread.start();

        mode = Mode.CLIENT;
        System.out.println("LAN joined session at " + hostIp);
    }

    /**
     * Auto-discover host on the local network via UDP.
     * Blocks for up to timeoutMs. Returns discovered host IP or null.
     */
    public static String discoverHost(int timeoutMs) {
        try (DatagramSocket socket = new DatagramSocket(null)) {
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(UDP_PORT));
            socket.setSoTimeout(timeoutMs);
            byte[] buf = new byte[128];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            socket.receive(packet);
            String msg = new String(packet.getData(), 0, packet.getLength());
            if (msg.startsWith(UDP_MAGIC)) {
                return packet.getAddress().getHostAddress();
            }
        } catch (SocketTimeoutException ignored) {
            // No host found in time
        } catch (Exception e) {
            System.err.println("UDP discovery error: " + e.getMessage());
        }
        return null;
    }

    /** Stop whatever mode is active. */
    public void stopSession() {
        try {
            if (httpServer != null) { httpServer.stop(0); httpServer = null; }
            if (tcpServer != null) { tcpServer.close(); tcpServer = null; }
            if (udpBroadcastThread != null) { udpBroadcastThread.interrupt(); }
            if (clientSocket != null) { clientSocket.close(); clientSocket = null; }
            for (PrintWriter pw : clients) { try { pw.close(); } catch (Exception ignored) {} }
            clients.clear();
            for (PrintWriter pw : sseClients) { try { pw.close(); } catch (Exception ignored) {} }
            sseClients.clear();
            
            if (tunnelProcess != null) {
                tunnelProcess.destroyForcibly();
                tunnelProcess = null;
            }
            
            lastPlayVideoId = null;
            lastPlayAudioUrl = null;
            hostPositionMs = 0;
            isPaused = false;
            currentTrackTitle = "";
            currentTrackArtist = "";
            currentLyricsData = null;
        } catch (Exception e) {
            System.err.println("Error stopping LAN session: " + e.getMessage());
        }
        mode = Mode.IDLE;
        System.out.println("LAN session stopped.");
    }

    // ── Host commands ────────────────────────────────────────────────────────────

    /** Called by host when it starts playing a new song. */
    public void notifyPlay(String videoId, String localFilePath, long startMs) {
        if (mode != Mode.HOST) return;
        currentAudioPath = localFilePath;
        lastPlayAudioUrl = "http://" + localIp + ":" + HTTP_PORT + "/audio";
        lastPlayVideoId = videoId;
        hostPositionMs = startMs;
        isPaused = false;
        broadcast("PLAY " + videoId + " " + lastPlayAudioUrl + " " + startMs);
    }

    public void notifyPause(long posMs) {
        if (mode == Mode.HOST) {
            hostPositionMs = posMs;
            isPaused = true;
            broadcast("PAUSE " + posMs);
        }
    }

    public void notifyResume(long posMs) {
        if (mode == Mode.HOST) {
            hostPositionMs = posMs;
            isPaused = false;
            broadcast("RESUME " + posMs);
        }
    }

    public void notifySeek(long posMs) {
        if (mode == Mode.HOST) {
            hostPositionMs = posMs;
            broadcast("SEEK " + posMs);
        }
    }

    public void notifySync(long posMs) {
        // Broadcasts exact time to correct any network-induced drift
        if (mode == Mode.HOST && !isPaused) {
            hostPositionMs = posMs;
            broadcast("SYNC " + posMs);
        }
    }

    public void notifyStop() {
        if (mode == Mode.HOST) {
            lastPlayVideoId = null;
            lastPlayAudioUrl = null;
            isPaused = false;
            broadcast("STOP");
        }
    }

    public void updateHostPosition(long posMs) {
        if (mode == Mode.HOST && !isPaused) {
            this.hostPositionMs = posMs;
        }
    }

    public void updateWebTrackInfo(String title, String artist) {
        currentTrackTitle = title == null ? "" : title;
        currentTrackArtist = artist == null ? "" : artist;
        currentLyricsData = null;
        broadcastSseOnly("META");
    }

    public void updateWebLyrics(LyricsData lyricsData) {
        currentLyricsData = lyricsData;
        broadcastSseOnly("LYRICS");
    }

    public void clearWebTrackInfo() {
        currentTrackTitle = "";
        currentTrackArtist = "";
        currentLyricsData = null;
        broadcastSseOnly("META");
    }

    public int getConnectedClientCount() { return clients.size(); }

    // ── Private: Host internals ──────────────────────────────────────────────────

    private void broadcast(String command) {
        System.out.println("[LAN] Broadcast → " + command);
        
        // 1. TCP Apps
        List<PrintWriter> deadTcp = new CopyOnWriteArrayList<>();
        for (PrintWriter pw : clients) {
            pw.println(command);
            if (pw.checkError()) deadTcp.add(pw);
        }
        clients.removeAll(deadTcp);
        
        // 2. Web Browser SSE Clients
        List<PrintWriter> deadSse = new CopyOnWriteArrayList<>();
        for (PrintWriter pw : sseClients) {
            pw.print("data: " + command + "\n\n");
            if (pw.checkError()) deadSse.add(pw);
        }
        sseClients.removeAll(deadSse);
    }

    private void broadcastSseOnly(String command) {
        List<PrintWriter> deadSse = new CopyOnWriteArrayList<>();
        for (PrintWriter pw : sseClients) {
            pw.print("data: " + command + "\n\n");
            if (pw.checkError()) deadSse.add(pw);
        }
        sseClients.removeAll(deadSse);
    }

    private void tcpAcceptLoop() {
        while (!Thread.currentThread().isInterrupted() && tcpServer != null && !tcpServer.isClosed()) {
            try {
                Socket s = tcpServer.accept();
                String clientName = s.getInetAddress().getHostAddress();
                PrintWriter pw = new PrintWriter(new OutputStreamWriter(s.getOutputStream()), true);
                clients.add(pw);
                System.out.println("[LAN] Client connected: " + clientName + " (" + clients.size() + " total)");
                if (listener != null)
                    listener.onClientConnected(clientName, clients.size());

                // Monitor client disconnect in background
                Thread monitor = new Thread(() -> {
                    try { s.getInputStream().read(); } catch (Exception ignored) {}
                    clients.remove(pw);
                    System.out.println("[LAN] Client disconnected: " + clientName);
                    if (listener != null)
                        listener.onClientDisconnected(clientName, clients.size());
                });
                monitor.setDaemon(true);
                monitor.start();
            } catch (Exception e) {
                if (!Thread.currentThread().isInterrupted())
                    System.err.println("[LAN] TCP accept error: " + e.getMessage());
            }
        }
    }

    public void startPublicTunnel(Consumer<String> onLinkReady) {
        new Thread(() -> {
            try {
                System.out.println("[TUNNEL] Starting Serveo SSH tunnel...");
                ProcessBuilder pb = new ProcessBuilder("ssh", "-o", "StrictHostKeyChecking=no", "-R", "80:localhost:" + HTTP_PORT, "serveo.net");
                pb.redirectErrorStream(true);
                tunnelProcess = pb.start();
                
                BufferedReader reader = new BufferedReader(new InputStreamReader(tunnelProcess.getInputStream()));
                String line;
                boolean found = false;
                while ((line = reader.readLine()) != null) {
                    // Strip ANSI escape codes
                    String cleanLine = line.replaceAll("\u001B\\[[;\\d]*[mK]", "");
                    System.out.println("[TUNNEL] " + cleanLine);
                    // Match: Forwarding HTTP traffic from https://[random].serveousercontent.com
                    if (!found && cleanLine.contains("https://")) {
                        int start = cleanLine.indexOf("https://");
                        int end = cleanLine.indexOf(" ", start);
                        if (end == -1) end = cleanLine.length();
                        String url = cleanLine.substring(start, end).trim();
                        // Ignore any non-serveo stuff just in case
                        if (url.contains("serveo")) {
                            found = true;
                            if (onLinkReady != null) onLinkReady.accept(url);
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[TUNNEL] Error starting public tunnel: " + e.getMessage());
                if (onLinkReady != null) onLinkReady.accept("Tunnel Failed (Requires SSH). Use local IP instead.");
            }
        }, "lan-tunnel-thread").start();
    }

    private void handleAudioRequest(HttpExchange exchange) throws IOException {
        if (currentAudioPath == null || !new File(currentAudioPath).exists()) {
            exchange.sendResponseHeaders(404, 0);
            exchange.close();
            return;
        }

        File audioFile = new File(currentAudioPath);
        long fileLength = audioFile.length();
        String contentType = guessAudioContentType(currentAudioPath);
        String rangeHeader = exchange.getRequestHeaders().getFirst("Range");

        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Accept-Ranges", "bytes");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");

        if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Content-Length", String.valueOf(fileLength));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
            return;
        }

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            long start = 0;
            long end = fileLength - 1;
            String[] bounds = rangeHeader.substring("bytes=".length()).split("-", 2);

            try {
                if (!bounds[0].isBlank()) {
                    start = Long.parseLong(bounds[0]);
                }
                if (bounds.length > 1 && !bounds[1].isBlank()) {
                    end = Long.parseLong(bounds[1]);
                }
            } catch (NumberFormatException ignored) {
                start = 0;
                end = fileLength - 1;
            }

            start = Math.max(0, Math.min(start, fileLength - 1));
            end = Math.max(start, Math.min(end, fileLength - 1));
            long contentLength = end - start + 1;

            exchange.getResponseHeaders().set("Content-Range", "bytes " + start + "-" + end + "/" + fileLength);
            exchange.getResponseHeaders().set("Content-Length", String.valueOf(contentLength));
            exchange.sendResponseHeaders(206, contentLength);

            try (RandomAccessFile raf = new RandomAccessFile(audioFile, "r");
                 OutputStream os = exchange.getResponseBody()) {
                raf.seek(start);
                byte[] buffer = new byte[16 * 1024];
                long remaining = contentLength;
                while (remaining > 0) {
                    int read = raf.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                    if (read == -1) {
                        break;
                    }
                    os.write(buffer, 0, read);
                    remaining -= read;
                }
            }
            return;
        }

        exchange.getResponseHeaders().set("Content-Length", String.valueOf(fileLength));
        exchange.sendResponseHeaders(200, fileLength);
        try (OutputStream os = exchange.getResponseBody()) {
            Files.copy(audioFile.toPath(), os);
        }
    }

    private String guessAudioContentType(String path) {
        String lower = path == null ? "" : path.toLowerCase();
        if (lower.endsWith(".m4a") || lower.endsWith(".mp4")) {
            return "audio/mp4";
        }
        if (lower.endsWith(".aac")) {
            return "audio/aac";
        }
        if (lower.endsWith(".ogg")) {
            return "audio/ogg";
        }
        if (lower.endsWith(".wav")) {
            return "audio/wav";
        }
        return "audio/mpeg";
    }

    private void handleSseEvents(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.getResponseHeaders().set("Connection", "keep-alive");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(200, 0); // chunked encoding
        
        PrintWriter writer = new PrintWriter(exchange.getResponseBody(), true);
        sseClients.add(writer);
        
        // If a song is currently playing, send it immediately so they sync on join!
        if (lastPlayVideoId != null && lastPlayAudioUrl != null) {
            writer.print("data: PLAY " + lastPlayVideoId + " " + lastPlayAudioUrl + " " + hostPositionMs + "\n\n");
            if (isPaused) {
                writer.print("data: PAUSE\n\n");
            }
        }
        
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Thread.sleep(5000); // SSE Ping to keep connection alive
                writer.print(": ping\n\n");
                if (writer.checkError()) break;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            sseClients.remove(writer);
            exchange.close();
        }
    }

    private void handleControlRequest(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        if (query != null && query.startsWith("cmd=")) {
            String cmd = query.substring(4);
            if (listener != null) listener.onRemoteCommand(cmd);
        }
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(200, 0);
        exchange.close();
    }

    private void handleLyricsRequest(HttpExchange exchange) throws IOException {
        JSONObject payload = new JSONObject();
        payload.put("title", currentTrackTitle == null ? "" : currentTrackTitle);
        payload.put("artist", currentTrackArtist == null ? "" : currentTrackArtist);
        payload.put("synced", currentLyricsData != null && currentLyricsData.isSynced());
        payload.put("source", currentLyricsData != null ? currentLyricsData.getSource() : "Waiting");

        JSONArray lines = new JSONArray();
        if (currentLyricsData != null) {
            for (LyricsLine line : currentLyricsData.getLines()) {
                JSONObject lineJson = new JSONObject();
                lineJson.put("timeMs", line.getTimestampMs());
                lineJson.put("text", line.getText());
                lines.put(lineJson);
            }
        }
        payload.put("lines", lines);

        byte[] bytes = payload.toString().getBytes();
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void handleWebPlayerRequest(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestURI().getPath().equals("/")) {
            exchange.sendResponseHeaders(404, 0);
            exchange.close();
            return;
        }
        InputStream htmlStream = getClass().getResourceAsStream("/com/musicplayer/web-player.html");
        if (htmlStream == null) {
            exchange.sendResponseHeaders(500, 0);
            exchange.close();
            return;
        }
        byte[] bytes;
        try (InputStream in = htmlStream) {
            bytes = in.readAllBytes();
        }
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void udpBroadcastLoop() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            String msg = UDP_MAGIC + java.net.InetAddress.getLocalHost().getHostName();
            byte[] data = msg.getBytes();
            InetAddress broadcastAddr = InetAddress.getByName("255.255.255.255");
            DatagramPacket packet = new DatagramPacket(data, data.length, broadcastAddr, UDP_PORT);
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    socket.send(packet);
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("[LAN] UDP broadcast error: " + e.getMessage());
        }
    }

    // ── Private: Client internals ────────────────────────────────────────────────

    private void clientReaderLoop(Socket socket) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[LAN] Received: " + line);
                parseCommand(line);
            }
        } catch (Exception e) {
            System.err.println("[LAN] Client read error: " + e.getMessage());
        }
        if (listener != null) listener.onSessionEnded();
    }

    private void parseCommand(String line) {
        if (listener == null) return;
        String[] parts = line.split(" ");
        String cmd = parts[0];
        try {
            switch (cmd) {
                case "PLAY" -> {
                    // PLAY <videoId> <audioUrl> <startMs>
                    String videoId  = parts[1];
                    String audioUrl = parts[2];
                    long   startMs  = Long.parseLong(parts[3]);
                    listener.onPlay(videoId, audioUrl, startMs);
                }
                case "PAUSE"  -> listener.onPause(Long.parseLong(parts[1]));
                case "RESUME" -> listener.onResume(Long.parseLong(parts[1]));
                case "SEEK"   -> listener.onSeek(Long.parseLong(parts[1]));
                case "STOP"   -> listener.onStop();
                default       -> System.err.println("[LAN] Unknown command: " + line);
            }
        } catch (Exception e) {
            System.err.println("[LAN] Error parsing command '" + line + "': " + e.getMessage());
        }
    }

    // ── Utility ──────────────────────────────────────────────────────────────────

    private String getLocalIp() throws SocketException {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 80);
            return socket.getLocalAddress().getHostAddress();
        } catch (Exception e) {
            // Fallback: iterate interfaces
            var interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                var ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) continue;
                var addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address) return addr.getHostAddress();
                }
            }
            return "127.0.0.1";
        }
    }
    private void serveResource(HttpExchange exchange, String resPath, String contentType) throws IOException {
        InputStream is = getClass().getResourceAsStream(resPath);
        if (is == null) {
            exchange.sendResponseHeaders(404, 0);
            exchange.close();
            return;
        }
        byte[] bytes;
        try (is) {
            bytes = is.readAllBytes();
        }
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
