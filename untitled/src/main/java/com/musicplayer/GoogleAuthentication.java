package com.musicplayer;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponseException;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
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

    private static final List<String> SCOPES = List.of(
            "https://www.googleapis.com/auth/userinfo.profile",
            "https://www.googleapis.com/auth/userinfo.email",
            "https://www.googleapis.com/auth/youtube.readonly");

    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static HttpTransport HTTP_TRANSPORT;

    private final Preferences authPrefs;
    private FileDataStoreFactory dataStoreFactory;
    private Credential credential;
    private String lastErrorMessage = "";

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

    private InputStream getCredentialsStream() {
        String googleCredentials = System.getenv("GOOGLE_CREDENTIALS");
        if (googleCredentials != null && !googleCredentials.isBlank()) {
            return new ByteArrayInputStream(googleCredentials.getBytes(StandardCharsets.UTF_8));
        }
        return GoogleAuthentication.class.getResourceAsStream(CLIENT_SECRETS_RESOURCE);
    }

    private void loadCurrentUser() {
        currentUser = authPrefs.get("currentUser", null);
        currentEmail = authPrefs.get("currentEmail", null);
        if (currentEmail == null) {
            return;
        }

        try {
            GoogleAuthorizationCodeFlow flow = buildAuthorizationFlow();
            this.credential = flow.loadCredential("user");
        } catch (Exception e) {
            System.err.println("Could not load stored credential: " + e.getMessage());
            clearStoredCredentialState();
        }
    }

    @Override
    public boolean googleLogin() {
        lastErrorMessage = "";
        boolean retriedFreshSignIn = false;
        while (true) {
            try {
                GoogleAuthorizationCodeFlow flow = buildAuthorizationFlow();
                this.credential = authorizeWithFreshFallback(flow);
                if (this.credential == null || !ensureFreshAccessToken()) {
                    lastErrorMessage = "Google sign-in did not return a usable token.";
                    return false;
                }

                String accessToken = credential.getAccessToken();
                URL url = new URL("https://www.googleapis.com/oauth2/v3/userinfo");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("Authorization", "Bearer " + accessToken);
                conn.connect();

                int code = conn.getResponseCode();
                if (code != 200) {
                    if (code == 401) {
                        System.err.println("401 Unauthorized at UserInfo. Clearing credentials...");
                        clearStoredCredentialState();
                    }

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
                lastErrorMessage = "";
                saveUsers();
                return true;
            } catch (Exception e) {
                if (!retriedFreshSignIn) {
                    retriedFreshSignIn = true;
                    System.err.println("Google login failed once. Clearing cached auth state and retrying fresh sign-in...");
                    clearStoredCredentialState();
                    continue;
                }

                if (isRevokedTokenError(e)) {
                    System.err.println("Google login detected a revoked or expired token. Clearing cached credentials.");
                    clearStoredCredentialState();
                }
                lastErrorMessage = buildUserFacingError(e);
                System.err.println("Google login error: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        }
    }

    private GoogleAuthorizationCodeFlow buildAuthorizationFlow() throws IOException {
        InputStream is = getCredentialsStream();
        if (is == null) {
            throw new RuntimeException(
                    "credentials.json not found on classpath (" + CLIENT_SECRETS_RESOURCE
                            + ") or GOOGLE_CREDENTIALS not set. Make sure it is in src/main/resources/com/musicplayer/");
        }
        GoogleClientSecrets secrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(is));
        return new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, secrets, SCOPES)
                .setDataStoreFactory(dataStoreFactory)
                .setAccessType("offline")
                .build();
    }

    private Credential authorizeWithFreshFallback(GoogleAuthorizationCodeFlow flow) throws IOException {
        AuthorizationCodeInstalledApp app = new AuthorizationCodeInstalledApp(
                flow, new LocalServerReceiver.Builder().setPort(0).build());
        try {
            return app.authorize("user");
        } catch (Exception ex) {
            if (!isRevokedTokenError(ex)) {
                throw ex;
            }
            System.err.println("Stored Google token is invalid. Retrying with a fresh sign-in...");
            clearStoredCredentialState();
            return app.authorize("user");
        }
    }

    private boolean ensureFreshAccessToken() throws IOException {
        if (credential == null) {
            return false;
        }
        if (credential.getAccessToken() == null
                || (credential.getExpiresInSeconds() != null && credential.getExpiresInSeconds() < 30)) {
            System.out.println("Refreshing OAuth2 token...");
            try {
                if (!credential.refreshToken()) {
                    clearStoredCredentialState();
                    return false;
                }
            } catch (TokenResponseException ex) {
                if (!isRevokedTokenError(ex)) {
                    throw ex;
                }
                System.err.println("Stored Google refresh token expired or revoked. Starting a fresh sign-in...");
                clearStoredCredentialState();
                this.credential = authorizeWithFreshFallback(buildAuthorizationFlow());
                return this.credential != null && ensureFreshAccessToken();
            }
        }
        return credential.getAccessToken() != null;
    }

    private boolean isRevokedTokenError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof TokenResponseException tokenError) {
                String message = tokenError.getMessage() == null ? "" : tokenError.getMessage().toLowerCase();
                String details = tokenError.getDetails() == null ? "" : tokenError.getDetails().toPrettyString().toLowerCase();
                if (message.contains("invalid_grant") || message.contains("expired or revoked")
                        || details.contains("invalid_grant") || details.contains("expired or revoked")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private void saveUsers() {
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

    private void clearStoredCredentialState() {
        this.currentUser = null;
        this.currentEmail = null;
        this.credential = null;
        saveUsers();
        try {
            File storedCredential = new File(DATA_STORE_DIR, "StoredCredential");
            if (storedCredential.exists()) {
                storedCredential.delete();
            }
        } catch (Exception e) {
            System.err.println("Failed to clear stored Google credential: " + e.getMessage());
        }
    }

    private String buildUserFacingError(Exception error) {
        String message = error.getMessage() == null ? "" : error.getMessage();
        String lower = message.toLowerCase();
        if (lower.contains("credentials.json")) {
            return "Google credentials are missing. Check credentials.json.";
        }
        if (lower.contains("invalid_grant") || lower.contains("expired or revoked")) {
            return "Your previous Google token expired. Please sign in again.";
        }
        if (lower.contains("access_denied")) {
            return "Google sign-in was cancelled.";
        }
        if (lower.contains("connection") || lower.contains("timed out") || lower.contains("http")) {
            return "Google sign-in could not reach Google's servers.";
        }
        return "Google login failed. Please try again.";
    }

    public String getLastErrorMessage() {
        return lastErrorMessage == null || lastErrorMessage.isBlank()
                ? "Google login failed. Please try again."
                : lastErrorMessage;
    }

    @Override
    public void logout() {
        clearStoredCredentialState();
    }

    @Override
    public Map<String, Object> getCurrentUserData() {
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
        return this.credential != null && this.currentUser != null;
    }

    @Override
    public boolean hasCredentials() {
        String googleCredentials = System.getenv("GOOGLE_CREDENTIALS");
        if (googleCredentials != null && !googleCredentials.isBlank()) {
            return true;
        }
        return GoogleAuthentication.class.getResourceAsStream(CLIENT_SECRETS_RESOURCE) != null;
    }

    @Override
    public Credential getCredential() {
        return this.credential;
    }

    @Override
    public boolean isRealLogin() {
        return true;
    }
}
