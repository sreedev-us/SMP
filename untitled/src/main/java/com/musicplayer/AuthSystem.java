// AuthSystem.java
package com.musicplayer;

import java.util.Map;
import com.google.api.client.auth.oauth2.Credential;

public interface AuthSystem {
    String getCurrentUser();
    boolean isLoggedIn();
    Map<String, Object> getCurrentUserData();
    void logout();
    boolean hasCredentials();
    boolean googleLogin();
    Credential getCredential();
    default boolean isRealLogin() { return false; }
}