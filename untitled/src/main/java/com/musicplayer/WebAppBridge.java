package com.musicplayer;

import org.json.JSONObject;

/**
 * Bridge between the browser-hosted web app and the JavaFX player state.
 */
public interface WebAppBridge {
    JSONObject getState();
    JSONObject search(String query) throws Exception;
    JSONObject addToQueue(String videoId, boolean playNow);
    JSONObject playQueueIndex(int index);
    JSONObject removeQueueIndex(int index);
    JSONObject clearQueue();
    JSONObject setToggle(String toggle, boolean enabled);
    JSONObject addRelated();
    JSONObject toggleLiked(String videoId);
    JSONObject playLiked(String videoId);
    JSONObject removeLiked(String videoId);
}
