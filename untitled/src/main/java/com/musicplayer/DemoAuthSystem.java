package com.musicplayer;

import com.google.api.client.auth.oauth2.Credential;
import java.util.Collections;
import java.util.Map;

/**
 * A lightweight AuthSystem implementation that bypasses Google OAuth.
 * Used for Demo Mode — no credentials.json or network access needed.
 */
public class DemoAuthSystem implements AuthSystem {

    private static final String DEMO_USER = "Demo User";
    private boolean loggedIn = false;

    @Override
    public boolean googleLogin() {
        // Simulate instant login — no OAuth, no credentials file needed
        this.loggedIn = true;
        System.out.println("Demo mode: logged in as '" + DEMO_USER + "'");
        return true;
    }

    @Override
    public String getCurrentUser() {
        return loggedIn ? DEMO_USER : null;
    }

    @Override
    public boolean isLoggedIn() {
        return loggedIn;
    }

    @Override
    public Map<String, Object> getCurrentUserData() {
        return Collections.singletonMap("name", DEMO_USER);
    }

    @Override
    public void logout() {
        loggedIn = false;
    }

    @Override
    public boolean hasCredentials() {
        return true; // Demo always "has credentials" (none needed)
    }

    @Override
    public Credential getCredential() {
        return null; // No real credential — YouTubeService will not be initialised in demo
    }

    @Override
    public boolean isRealLogin() {
        return false; // This is a Guest/Demo mode
    }
}
