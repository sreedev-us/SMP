package com.musicplayer;

public class GuestPlaybackUnavailableException extends Exception {

    public GuestPlaybackUnavailableException(String message) {
        super(message);
    }

    public GuestPlaybackUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
