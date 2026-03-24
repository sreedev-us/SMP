package com.musicplayer;

/**
 * Callback interface for LAN sync events received by a client
 * (or echoed locally by the host for self-application).
 */
public interface LanSyncListener {
    /** Host started playing a song. audioUrl is an HTTP URL to stream from. */
    void onPlay(String videoId, String audioUrl, long startPositionMs);

    /** Host paused at the given position. */
    void onPause(long positionMs);

    /** Host resumed from the given position. */
    void onResume(long positionMs);

    /** Host seeked to the given position. */
    void onSeek(long positionMs);

    /** Host stopped playback. */
    void onStop();

    /** A new client connected (host only). deviceName = client identifier. */
    void onClientConnected(String deviceName, int totalClients);

    /** A client disconnected (host only). */
    void onClientDisconnected(String deviceName, int totalClients);

    /** Session ended. */
    void onSessionEnded();

    /** Web Player sent a remote control command. */
    default void onRemoteCommand(String command) {}
}
