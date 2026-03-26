package com.musicplayer;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.prefs.Preferences;

import org.json.JSONObject;

public class GoogleAuthentication implements AuthSystem {

    private static final String APPLICATION_NAME = "Harmony Pro";
    private static final String CLIENT_SECRETS_RESOURCE = "/com/musicplayer/credentials.json";
    private static final File DATA_STORE_DIR = new File(
            System.getProperty("user.home"), ".harmony-pro-credentials");

    // Scopes: Get user's email/profile AND access YouTube
    private static final List<String> SCOPES = List.of(
            "https://www.googleapis.com/auth/userinfo.profile",
            "https://www.googleapis.com/auth/userinfo.email",
            "https://www.googleapis.com/auth/youtube.readonly");

    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static HttpTransport HTTP_TRANSPORT;

    private final Preferences authPrefs;
    private FileDataStoreFactory dataStoreFactory;
    private Credential credential;

    private String currentUser;
    private String currentEmail;

    public GoogleAuthentication() {
        if (AppPlatform.isMobile()) {
            throw new UnsupportedOperationException("Google login is not supported on Android yet.");
        }
        this.authPrefs = Preferences.userRoot().node("com/musicplayer/harmonypro/auth");
        try {
            HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            this.dataStoreFactory = new FileDataStoreFactory(DATA_STORE_DIR);
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("Could not initialize Google authentication", e);
        }
        loadCurrentUser();
    }

    private void loadCurrentUser() {
        currentUser = authPrefs.get("currentUser", null);
        currentEmail = authPrefs.get("currentEmail", null);

        // If we have a user stored, try to load their credential
        if (currentEmail != null) {
            try {
                InputStream is = GoogleAuthentication.class.getResourceAsStream(CLIENT_SECRETS_RESOURCE);
                if (is == null)
                    throw new RuntimeException("credentials.json not found on classpath");
                GoogleClientSecrets secrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(is));
                GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                        HTTP_TRANSPORT, JSON_FACTORY, secrets, SCOPES)
                        .setDataStoreFactory(dataStoreFactory)
                        .setAccessType("offline").build();

                // "user" is the generic ID for a single-user desktop app
                this.credential = flow.loadCredential("user");

            } catch (Exception e) {
                System.err.println("Could not load stored credential: " + e.getMessage());
                this.credential = null;
            }
        }
    }

    /**
     * Executes the real Google OAuth 2.0 flow.
     * This will open a web browser for the user to sign in.
     */
    @Override
    public boolean googleLogin() {
        try {
            InputStream is = GoogleAuthentication.class.getResourceAsStream(CLIENT_SECRETS_RESOURCE);
            if (is == null) {
                throw new RuntimeException(
                        "credentials.json not found on classpath (" + CLIENT_SECRETS_RESOURCE + "). "
                                + "Make sure it is in src/main/resources/com/musicplayer/");
            }
            GoogleClientSecrets secrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(is));

            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                    HTTP_TRANSPORT, JSON_FACTORY, secrets, SCOPES)
                    .setDataStoreFactory(dataStoreFactory)
                    .setAccessType("offline").build();

            // Port 0 = let the OS pick any available port automatically.
            // Make sure your Google Cloud "Desktop app" credentials have
            // "http://localhost" as an allowed redirect URI (no port needed).
            LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(0).build();
            this.credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");

            if (this.credential == null) {
                return false;
            }

            // --- Login successful — fetch user info via plain HTTPS ---
            // Ensure we have a fresh token (refresh if expired)
            if (credential.getAccessToken() == null
                    || (credential.getExpiresInSeconds() != null && credential.getExpiresInSeconds() < 30)) {
                System.out.println("Refreshing OAuth2 token...");
                credential.refreshToken();
            }

            String accessToken = credential.getAccessToken();
            URL url = new URL("https://www.googleapis.com/oauth2/v3/userinfo");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            conn.connect();

            int code = conn.getResponseCode();
            if (code != 200) {
                // If 401, the stored credential might be totally invalid (revoked)
                if (code == 401) {
                    System.err.println("401 Unauthorized at UserInfo. Clearing credentials...");
                    logout(); // Wipe the bad state
                }

                // Read error stream for debugging
                InputStream es = conn.getErrorStream();
                if (es != null) {
                    try (Scanner sc = new Scanner(es)) {
                        sc.useDelimiter("\\A");
                        String errorBody = sc.hasNext() ? sc.next() : "No error body";
                        System.err.println("Google Auth Error Body: " + errorBody);
                    }
                }
                throw new IOException("Google UserInfo failed with HTTP " + code);
            }

            String json;
            try (Scanner sc = new Scanner(conn.getInputStream())) {
                sc.useDelimiter("\\A");
                json = sc.hasNext() ? sc.next() : "{}";
            }

            JSONObject userInfoJson = new JSONObject(json);
            this.currentUser = userInfoJson.optString("name", "Unknown User");
            this.currentEmail = userInfoJson.optString("email", "");

            // Save user to preferences
            saveUsers();
            return true;

        } catch (Exception e) {
            System.err.println("Google login error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private void saveUsers() {
        // Save current user to preferences
        if (currentUser != null) {
            authPrefs.put("currentUser", currentUser);
            authPrefs.put("currentEmail", currentEmail);
        } else {
            authPrefs.remove("currentUser");
            authPrefs.remove("currentEmail");
        }
        try {
            authPrefs.flush();
        } catch (Exception e) {
            System.err.println("Error saving auth preferences: " + e.getMessage());
        }
    }

    @Override
    public void logout() {
        this.currentUser = null;
        this.currentEmail = null;
        this.credential = null;
        saveUsers(); // Clears prefs
        // Delete the stored credential file
        try {
            new File(DATA_STORE_DIR, "StoredCredential").delete();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public Map<String, Object> getCurrentUserData() {
        // This method is now less important, but we can fill it
        Map<String, Object> userData = new java.util.HashMap<>();
        userData.put("name", currentUser);
        userData.put("email", currentEmail);
        userData.put("playlists", Collections.emptyList());
        return userData;
    }

    @Override
    public String getCurrentUser() {
        return currentUser;
    }

    @Override
    public boolean isLoggedIn() {
        // Real login means we have a valid credential
        return this.credential != null && this.currentUser != null;
    }

    @Override
    public boolean hasCredentials() {
        return GoogleAuthentication.class.getResourceAsStream(CLIENT_SECRETS_RESOURCE) != null;
    }

    /**
     * Returns the active OAuth 2.0 credential.
     */
    @Override
    public Credential getCredential() {
        return this.credential;
    }

    @Override
    public boolean isRealLogin() {
        return true;
    }
}
