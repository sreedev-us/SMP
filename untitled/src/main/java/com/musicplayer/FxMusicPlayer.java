package com.musicplayer;

import org.json.JSONArray;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.Node;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.scene.control.TextInputDialog;

public class FxMusicPlayer {

    // --- Services ---
    private final AuthSystem auth;
    private final SettingsManager settings;
    private final AudioPlayer audioPlayer;
    private YouTubeService youtubeService = null;
    private final CacheManager cache;
    private final LanSyncManager lanSync = new LanSyncManager();
    private final LyricsService lyricsService = new LyricsService();
    private Consumer<String> externalUrlOpener;

    // --- FXML UI Components ---
    @FXML private Label userInfoLabel;
    @FXML private TextField searchField;
    @FXML private VBox searchHistorySection;
    @FXML private FlowPane searchHistoryContainer;
    @FXML private ListView<SongData> searchResultsList;
    @FXML private ListView<SongData> playlistView;
    @FXML private ImageView albumArtView;
    @FXML private Label songTitleLabel;
    @FXML private Label artistLabel;
    @FXML private Label currentTimeLabel;
    @FXML private Label totalTimeLabel;
    @FXML private Slider progressSlider;
    @FXML private ProgressBar progressBar;
    @FXML private Button playPauseButton;
    @FXML private Slider volumeSlider;
    @FXML private Label statusLabel;
    @FXML private Button shuffleButton;
    @FXML private Button repeatButton;
    @FXML private Button autoRadioButton;
    @FXML private ProgressIndicator loadingIndicator;
    @FXML private Button hostButton;
    @FXML private Button joinButton;
    @FXML private TextField publicLinkField;
    @FXML private Label peerCountLabel;
    @FXML private ScrollPane lyricsScrollPane;
    @FXML private VBox lyricsContainer;
    @FXML private Label lyricsHeadlineLabel;
    @FXML private Label lyricsSourceLabel;
    @FXML private VBox sidebar;
    @FXML private Button menuButton;
    @FXML private StackPane mobileSearchOverlay;
    @FXML private StackPane appRoot;
    @FXML private StackPane mobileNowPlayingOverlay;
    @FXML private StackPane settingsOverlay;
    @FXML private VBox settingsContainer;
    @FXML private ImageView expandedAlbumArtView;
    @FXML private Label expandedSongTitleLabel;
    @FXML private Label expandedArtistLabel;
    @FXML private Label expandedCurrentTimeLabel;
    @FXML private Label expandedTotalTimeLabel;
    @FXML private ProgressBar expandedProgressBar;
    @FXML private Button expandedPlayPauseButton;
    @FXML private Label expandedLyricsBadgeLabel;
    private Button mobileSettingsHostButton;
    private Button mobileSettingsJoinButton;
    private TextField mobileSettingsLinkField;
    private Label mobileSettingsPeerLabel;
    private Label mobileSettingsStatusLabel;

    // --- State ---
    private final ObservableList<SongData> playlist = FXCollections.observableArrayList();
    private int currentSongIndex = -1;
    private boolean isPlaying = false;
    private boolean isPaused = false;
    private boolean isDraggingSlider = false;
    private boolean shuffleEnabled = false;
    private boolean repeatEnabled = false;
    private boolean autoRadioEnabled = false;
    private Timeline progressTimer;
    private Image defaultAlbumArt;
    private long lastSyncTime = 0;
    private List<LyricsLine> currentLyrics = List.of();
    private final List<Label> lyricLineLabels = new ArrayList<>();
    private int currentLyricIndex = -1;
    private boolean currentLyricsSynced = false;
    private long lyricsRequestSequence = 0;
    private final List<String> searchHistory = new ArrayList<>();
    private static final int MAX_SEARCH_HISTORY = 8;

    public FxMusicPlayer(AuthSystem auth, SettingsManager settings) {
        this.auth = auth;
        this.settings = settings;
        this.cache = new CacheManager();
        this.audioPlayer = new AudioPlayer();

        // Auto-advance when a track ends
        this.audioPlayer.setOnEndOfMedia(() -> Platform.runLater(this::handleNext));

        if (auth != null && auth.isLoggedIn()) {
            try {
                this.youtubeService = new YouTubeService(auth);
            } catch (Exception e) {
                System.err.println("Failed to init YouTube Service: " + e.getMessage());
            }
        }

        // Wire LAN sync callbacks
        lanSync.setListener(new LanSyncListener() {
            @Override public void onPlay(String videoId, String audioUrl, long startMs) {
                Platform.runLater(() -> {
                    try {
                        audioPlayer.play(audioUrl);
                        audioPlayer.seek(startMs);
                        onPlaybackStarted();
                        updateStatus("LAN: Playing from host");
                    } catch (Exception e) {
                        updateStatus("LAN playback error: " + e.getMessage());
                    }
                });
            }
            @Override public void onPause(long posMs) {
                Platform.runLater(() -> { audioPlayer.pause();         updateStatus("Paused"); });
            }
            @Override public void onResume(long posMs) {
                Platform.runLater(() -> { audioPlayer.resume(); updateStatus("LAN: Resumed"); });
            }
            @Override public void onSeek(long posMs) {
                Platform.runLater(() -> audioPlayer.seek(posMs));
            }
            @Override public void onStop() {
                Platform.runLater(() -> { stopMusic(); updateStatus("LAN: Host stopped"); });
            }
            @Override public void onClientConnected(String name, int count) {
                Platform.runLater(() -> updatePeerLabel(count));
            }
            @Override public void onClientDisconnected(String name, int count) {
                Platform.runLater(() -> updatePeerLabel(count));
            }
            @Override public void onSessionEnded() {
                Platform.runLater(() -> {
                    updateStatus("LAN session ended.");
                    updateToggleButtonStyle(hostButton, false);
                    updateToggleButtonStyle(joinButton, false);
                    updateToggleButtonStyle(mobileSettingsHostButton, false);
                    updateToggleButtonStyle(mobileSettingsJoinButton, false);
                    updatePeerLabel(0);
                    updatePublicLinkFields("", "Generating tunnel link...", false);
                });
            }
            @Override public void onRemoteCommand(String cmd) {
                Platform.runLater(() -> {
                    switch (cmd) {
                        case "playpause": handlePlayPause(); break;
                        case "next": handleNext(); break;
                        case "prev": handlePrevious(); break;
                    }
                });
            }
        });
    }

    @FXML
    public void initialize() {
        // Bind playlist
        playlistView.setItems(playlist);
        configureListView(playlistView);
        configureListView(searchResultsList);
        loadSearchHistory();
        refreshSearchHistoryUi();

        // Volume
        int initialVolume = settings.getInt("audio.volume", 70);
        if (volumeSlider != null) {
            volumeSlider.setValue(initialVolume);
            volumeSlider.valueProperty().addListener((obs, o, n) -> setVolume(n.intValue()));
            setVolume((int) volumeSlider.getValue());
        } else {
            setVolume(initialVolume);
        }

        // Progress tracking
        setupProgressTimer();
        setupProgressSlider();

        // User info
        if (auth != null && auth.isLoggedIn()) {
            if (auth.isRealLogin()) {
                userInfoLabel.setText("User: " + auth.getCurrentUser());
            } else {
                userInfoLabel.setText("Guest Mode (Limited API)");
                updateStatus("Guest Mode: search works, and mobile playback opens through YouTube.");
            }
        }

        // Double-click to play from playlist
        playlistView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) handlePlaylistDoubleClick();
        });

        // Default album art
        try {
            defaultAlbumArt = new Image(
                getClass().getResourceAsStream("/com/musicplayer/default-album.png"));
        } catch (Exception e) {
            defaultAlbumArt = null;
        }
        setAlbumArt(null);

        // Hide loading spinner initially
        if (loadingIndicator != null) loadingIndicator.setVisible(false);
        renderLyricsPlaceholder("Pick a song to light up the lyrics panel.", "Waiting for track");
        updatePlayPauseLabel();
        updateSessionButtonLabels();

        // Shuffle / Repeat / Auto Radio button initial style
        updateToggleButtonStyle(shuffleButton, shuffleEnabled);
        updateToggleButtonStyle(repeatButton, repeatEnabled);
        updateToggleButtonStyle(autoRadioButton, autoRadioEnabled);

        // Single-click on a search result adds it to the queue and plays it immediately
        searchResultsList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 1) {
                SongData song = searchResultsList.getSelectionModel().getSelectedItem();
                if (song != null) addToQueueAndPlay(song);
            }
        });

        if (searchField != null) {
            searchField.setOnAction(event -> performSearch());
        }

        // Setup responsive layout listener (runs after scene is created)
        Platform.runLater(this::setupResponsiveListener);
    }

    private void setupResponsiveListener() {
        // Assuming 'sidebar' and 'menuButton' are FXML components declared elsewhere
        // For this code to compile, you would need to add:
        // @FXML private VBox sidebar;
        // @FXML private Button menuButton;
        if (sidebar == null || sidebar.getScene() == null) return;
        
        sidebar.getScene().widthProperty().addListener((obs, oldVal, newVal) -> {
            boolean isMobile = newVal.doubleValue() < 850;
            updateResponsiveUI(isMobile);
        });
        
        // Initial check
        updateResponsiveUI(sidebar.getScene().getWidth() < 850);
    }

    private void updateResponsiveUI(boolean mobile) {
        if (sidebar.getScene() == null) return;
        
        if (mobile) {
            if (!sidebar.getScene().getRoot().getStyleClass().contains("mobile")) {
                sidebar.getScene().getRoot().getStyleClass().add("mobile");
            }
            menuButton.setVisible(true);
            menuButton.setManaged(true);
            setMobileSearchVisible(false);
        } else {
            sidebar.getScene().getRoot().getStyleClass().remove("mobile");
            menuButton.setVisible(false);
            menuButton.setManaged(false);
            if (mobileSearchOverlay != null) {
                mobileSearchOverlay.setVisible(false);
                mobileSearchOverlay.setManaged(false);
            }
            sidebar.setVisible(true);
            sidebar.setManaged(true);
        }
    }

    @FXML
    public void handleToggleSidebar() {
        if (AppPlatform.isMobile() && mobileSearchOverlay != null) {
            setMobileSearchVisible(!mobileSearchOverlay.isVisible());
            return;
        }
        if (sidebar != null) {
            sidebar.setVisible(!sidebar.isVisible());
            sidebar.setManaged(sidebar.isVisible());
        }
    }

    // ── UI Helpers ─────────────────────────────────────────────────────────────

    private void configureListView(ListView<SongData> listView) {
        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(SongData song, boolean empty) {
                super.updateItem(song, empty);
                if (empty || song == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    String icon = "youtube".equals(song.getType()) ? "YouTube: " : "File: ";
                    String issue = song.isGuestPlaybackBlocked() ? " [Guest blocked]" : "";
                    setText(icon + song.getDisplayName() + issue);
                }
            }
        });
    }

    private void setAlbumArt(String thumbnailUrl) {
        try {
            if (thumbnailUrl != null && !thumbnailUrl.isEmpty()) {
                Image image = new Image(thumbnailUrl, true);
                albumArtView.setImage(image);
                if (expandedAlbumArtView != null) {
                    expandedAlbumArtView.setImage(image);
                }
            } else {
                albumArtView.setImage(defaultAlbumArt);
                if (expandedAlbumArtView != null) {
                    expandedAlbumArtView.setImage(defaultAlbumArt);
                }
            }
        } catch (Exception e) {
            albumArtView.setImage(defaultAlbumArt);
            if (expandedAlbumArtView != null) {
                expandedAlbumArtView.setImage(defaultAlbumArt);
            }
        }
    }

    private void updateToggleButtonStyle(Button btn, boolean active) {
        if (btn == null) return;
        if (active) {
            btn.getStyleClass().remove("icon-button");
            btn.getStyleClass().add("icon-button-active");
        } else {
            btn.getStyleClass().remove("icon-button-active");
            if (!btn.getStyleClass().contains("icon-button"))
                btn.getStyleClass().add("icon-button");
        }
    }

    private void updatePlayPauseLabel() {
        if (playPauseButton != null) {
            playPauseButton.setText(isPlaying ? "||" : "|>");
        }
        if (expandedPlayPauseButton != null) {
            expandedPlayPauseButton.setText(isPlaying ? "||" : "|>");
        }
    }

    private void updateSessionButtonLabels() {
        if (hostButton != null) {
            hostButton.setText(lanSync.getMode() == LanSyncManager.Mode.HOST ? "Stop Sync" : "Start Sync");
        }
        if (joinButton != null) {
            joinButton.setText(lanSync.getMode() == LanSyncManager.Mode.CLIENT ? "Leave" : "Connect");
        }
        if (mobileSettingsHostButton != null) {
            mobileSettingsHostButton.setText(lanSync.getMode() == LanSyncManager.Mode.HOST ? "Stop Sync" : "Host Session");
        }
        if (mobileSettingsJoinButton != null) {
            mobileSettingsJoinButton.setText(lanSync.getMode() == LanSyncManager.Mode.CLIENT ? "Leave Session" : "Join Session");
        }
    }

    private void setLoading(boolean loading) {
        if (loadingIndicator != null) {
            loadingIndicator.setVisible(loading);
        }
    }

    // ── Player Controls ────────────────────────────────────────────────────────

    @FXML
    public void handlePlayPause() {
        if (isPlaying) pauseMusic();
        else playMusic();
    }

    private void playMusic() {
        if (playlist.isEmpty()) {
            updateStatus("Playlist is empty. Add songs to play.");
            return;
        }
        if (currentSongIndex == -1) currentSongIndex = 0;

        if (isPaused) {
            audioPlayer.resume();
            isPlaying = true;
            isPaused  = false;
            updatePlayPauseLabel();
            progressTimer.play();
            lanSync.notifyResume(audioPlayer.getCurrentPosition());
            return;
        }

        // Fresh play
        audioPlayer.stop();
        SongData song = playlist.get(currentSongIndex);
        updateNowPlaying(song);

        if ("local".equals(song.getType())) {
            playLocal(song);
        } else {
            playYouTube(song);
        }
    }

    private void playLocal(SongData song) {
        try {
            audioPlayer.play(song.getPath());
            onPlaybackStarted();
        } catch (Exception e) {
            updateStatus("Error: " + e.getMessage());
            Platform.runLater(this::handleNext);
        }
    }

    /**
     * Resolves the YouTube stream URL via yt-dlp (on a background thread)
     * then hands the URL to MediaPlayer.
     */
    private void playYouTube(SongData song) {
        if (song.isGuestPlaybackBlocked()) {
            updateStatus(song.getPlaybackIssue() != null
                ? song.getPlaybackIssue()
                : "This track can't be played in Guest Mode. Try another result or sign in.");
            tryAdvanceAfterBlockedTrack(song);
            return;
        }

        updateStatus("Loading audio: " + song.getTitle() + " (may take a few seconds...)");
        setLoading(true);

        new Thread(() -> {
            try {
                String streamUrl = resolveOnlineStream(song);

                Platform.runLater(() -> {
                    setLoading(false);
                    try {
                        audioPlayer.play(streamUrl);
                        onPlaybackStarted();
                        // Notify LAN clients (host mode only — no-op if idle)
                        lanSync.notifyPlay(song.getVideoId(), streamUrl, 0);
                    } catch (Exception e) {
                        updateStatus("Playback error: " + e.getMessage());
                        handleNext();
                    }
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    setLoading(false);
                    if (shouldUseOfficialYouTubePlayback()) {
                        openSongInYouTube(song);
                        return;
                    }
                    handlePlaybackFailure(song, e);
                });
            }
        }, "ytdlp-resolver").start();
    }

    private void onPlaybackStarted() {
        isPlaying = true;
        isPaused = false;
        updatePlayPauseLabel();
        progressTimer.play();

        SongData currentSong = currentSongIndex >= 0 && currentSongIndex < playlist.size()
            ? playlist.get(currentSongIndex)
            : null;

        if (currentSong != null) {
                        updateStatus("Now Playing: " + currentSong.getTitle());
            loadLyricsForSong(currentSong);
        }
    }

    private void pauseMusic() {
        audioPlayer.pause();
        isPlaying = false;
        isPaused  = true;
        updatePlayPauseLabel();
        progressTimer.stop();
                updateStatus("Paused");
        lanSync.notifyPause(audioPlayer.getCurrentPosition());
    }

    private void stopMusic() {
        audioPlayer.stop();
        isPlaying = false;
        isPaused  = false;
        updatePlayPauseLabel();
        if (progressTimer != null) progressTimer.stop();
        if (progressSlider != null) {
            progressSlider.setValue(0);
        }
        if (progressBar != null) {
            progressBar.setProgress(0);
        }
        currentTimeLabel.setText("0:00");
        totalTimeLabel.setText("0:00");
        if (expandedProgressBar != null) {
            expandedProgressBar.setProgress(0);
        }
        if (expandedCurrentTimeLabel != null) {
            expandedCurrentTimeLabel.setText("0:00");
        }
        if (expandedTotalTimeLabel != null) {
            expandedTotalTimeLabel.setText("0:00");
        }
        resetLyricsProgress();
        lanSync.notifyStop();
    }

    @FXML
    public void handleNext() {
        if (playlist.isEmpty()) return;

        if (repeatEnabled) {
            // Replay same song
            isPaused = false;
            playMusic();
            return;
        }

        if (shuffleEnabled) {
            int next = currentSongIndex;
            if (playlist.size() > 1) while (next == currentSongIndex)
                next = (int) (Math.random() * playlist.size());
            currentSongIndex = next;
        } else {
            int next = currentSongIndex + 1;
            if (next >= playlist.size()) {
                // Reached end of queue
                if (autoRadioEnabled) {
                    autoFetchMoreSongs(); // will play next after fetching
                    return;
                }
                next = 0; // wrap around
            }
            currentSongIndex = next;
        }

        isPaused = false;
        playMusic();
    }

    @FXML
    public void handlePrevious() {
        if (playlist.isEmpty()) return;
        currentSongIndex = (currentSongIndex <= 0) ? 0 : currentSongIndex - 1;
        isPaused = false;
        playMusic();
    }

    // ── Auto Radio ─────────────────────────────────────────────────────────────

    @FXML
    public void handleToggleAutoRadio() {
        autoRadioEnabled = !autoRadioEnabled;
        updateToggleButtonStyle(autoRadioButton, autoRadioEnabled);
        
        if (autoRadioEnabled) {
                        updateStatus("Auto Radio ON - related songs will be added automatically");
            if (!playlist.isEmpty()) {
                autoFetchMoreSongs(playlist.get(playlist.size() - 1), false);
            }
        } else {
                        updateStatus("Auto Radio OFF");
        }
    }

    /**
     * Adds a song to the queue (if not already present) and starts playing it immediately.
     * Called when user single-clicks a search result.
     */
    private void addToQueueAndPlay(SongData song) {
        if (!playlist.contains(song)) {
            playlist.add(song);
        }
        currentSongIndex = playlist.indexOf(song);
        isPaused = false;
        isPlaying = false;
        audioPlayer.stop();
        playMusic();
                updateStatus("Playing: " + song.getTitle());
        if (AppPlatform.isMobile()) {
            setMobileSearchVisible(false);
        }
        
        if (autoRadioEnabled) {
            autoFetchMoreSongs(song, false);
        }
    }

    private void handlePlaybackFailure(SongData song, Exception error) {
        if (song != null && error instanceof GuestPlaybackUnavailableException) {
            song.setGuestPlaybackBlocked(true);
            song.setPlaybackIssue(error.getMessage());
            playlistView.refresh();
            searchResultsList.refresh();
            updateStatus(error.getMessage());
            tryAdvanceAfterBlockedTrack(song);
            return;
        }

        updateStatus("Stream error: " + error.getMessage());
        new Timeline(new KeyFrame(Duration.seconds(3), ev -> handleNext())).play();
    }

    private String resolveOnlineStream(SongData song) throws Exception {
        Exception remoteFailure = null;

        if (RemoteStreamResolver.isConfigured(settings)) {
            try {
                return RemoteStreamResolver.resolve(settings, song.getVideoId());
            } catch (Exception ex) {
                remoteFailure = ex;
                System.err.println("Remote stream resolver failed: " + ex.getMessage());
            }
        }

        try {
            return YtDlpStreamResolver.resolve(song.getVideoId());
        } catch (Exception localFailure) {
            if (remoteFailure != null) {
                localFailure.addSuppressed(remoteFailure);
            }
            throw localFailure;
        }
    }

    private void tryAdvanceAfterBlockedTrack(SongData failedSong) {
        if (playlist.isEmpty()) {
            return;
        }

        int nextPlayableIndex = findNextPlayableIndex(currentSongIndex);
        if (nextPlayableIndex == -1) {
            stopMusic();
            updateStatus("Guest Mode can't play the current queue. Try local files, another search result, or sign in.");
            return;
        }

        if (failedSong != null && playlist.get(nextPlayableIndex) == failedSong) {
            stopMusic();
            updateStatus("This track can't be played in Guest Mode. Try another result or sign in.");
            return;
        }

        currentSongIndex = nextPlayableIndex;
        isPaused = false;
        isPlaying = false;
        new Timeline(new KeyFrame(Duration.seconds(1), ev -> playMusic())).play();
    }

    private int findNextPlayableIndex(int startIndex) {
        if (playlist.isEmpty()) {
            return -1;
        }

        int safeStart = Math.max(0, startIndex);
        for (int offset = 1; offset <= playlist.size(); offset++) {
            int idx = (safeStart + offset) % playlist.size();
            SongData candidate = playlist.get(idx);
            if (!candidate.isGuestPlaybackBlocked()) {
                return idx;
            }
        }

        SongData current = playlist.get(safeStart % playlist.size());
        return current.isGuestPlaybackBlocked() ? -1 : safeStart % playlist.size();
    }

    /**
     * Auto-fetches more songs related to the given track.
     * @param baseSong The song to base the search on
     * @param playNext If true, advances the index and plays the first added song immediately
     */
    private void autoFetchMoreSongs(SongData baseSong, boolean playNext) {
        if (youtubeService == null || baseSong == null) return;
        
        String query = baseSong.getTitle().replaceAll("\\(.*?\\)|\\[.*?\\]", "").trim()
                + " " + baseSong.getChannel();

                updateStatus("Radio: finding songs like \"" + baseSong.getTitle() + "\"...");

        new Thread(() -> {
            try {
                List<YouTubeVideo> videos = youtubeService.searchVideos(query, 8);
                Platform.runLater(() -> {
                    int added = 0;
                    for (YouTubeVideo v : videos) {
                        boolean duplicate = playlist.stream()
                                .anyMatch(s -> v.getId().equals(s.getVideoId()));
                        if (!duplicate) {
                            SongData s = new SongData();
                            s.setVideoId(v.getId());
                            s.setTitle(v.getTitle());
                            s.setChannel(v.getChannel());
                            s.setThumbnailUrl(v.getThumbnailUrl());
                            s.setType("youtube");
                            s.setLyricsSearchHint(query);
                            playlist.add(s);
                            added++;
                        }
                    }
                    if (added > 0) {
                                                updateStatus("Radio added " + added + " songs - keep listening!");
                        if (playNext) {
                            currentSongIndex++;
                            isPaused = false;
                            playMusic();
                        }
                    } else if (playNext) {
                                                updateStatus("Radio: no new songs found.");
                    } else {
                                                updateStatus("Radio up to date.");
                    }
                });
            } catch (Exception e) {
                System.err.println("Auto-radio fetch failed: " + e.getMessage());
            }
        }, "auto-radio").start();
    }
    
    /** Triggered by handleNext() when queue is exhausted. */
    private void autoFetchMoreSongs() {
        if (playlist.isEmpty()) return;
        autoFetchMoreSongs(playlist.get(playlist.size() - 1), true);
    }

    private void handlePlaylistDoubleClick() {
        int idx = playlistView.getSelectionModel().getSelectedIndex();
        if (idx != -1) {
            currentSongIndex = idx;
            isPaused = false;
            playMusic();
        }
    }

    @FXML
    public void handleToggleShuffle() {
        shuffleEnabled = !shuffleEnabled;
        updateToggleButtonStyle(shuffleButton, shuffleEnabled);
                updateStatus(shuffleEnabled ? "Shuffle ON" : "Shuffle OFF");
    }

    @FXML
    public void handleToggleRepeat() {
        repeatEnabled = !repeatEnabled;
        updateToggleButtonStyle(repeatButton, repeatEnabled);
                updateStatus(repeatEnabled ? "Repeat ON" : "Repeat OFF");
    }

    // ── Volume & Progress ──────────────────────────────────────────────────────

    private void setVolume(double value) {
        audioPlayer.setVolume((int) value);
    }

    @FXML
    public void handleVolumeDown() {
        int nextVolume = Math.max(0, settings.getInt("audio.volume", 70) - 10);
        settings.set("audio.volume", nextVolume);
        syncSettingsToUi();
        updateStatus("Volume " + nextVolume + "%");
    }

    @FXML
    public void handleVolumeUp() {
        int nextVolume = Math.min(100, settings.getInt("audio.volume", 70) + 10);
        settings.set("audio.volume", nextVolume);
        syncSettingsToUi();
        updateStatus("Volume " + nextVolume + "%");
    }

    private void setupProgressTimer() {
        progressTimer = new Timeline(new KeyFrame(Duration.millis(500), event -> {
            if (isPlaying && !isDraggingSlider) {
                long current = audioPlayer.getCurrentPosition();
                lanSync.updateHostPosition(current);
                
                // Aggressive network syncing for remote tunnels
                if (System.currentTimeMillis() - lastSyncTime > 4000) {
                    lanSync.notifySync(current);
                    lastSyncTime = System.currentTimeMillis();
                }
                
                long total   = audioPlayer.getDuration();
                if (total > 0) {
                    currentTimeLabel.setText(formatTime(current));
                    totalTimeLabel.setText(formatTime(total));
                    double progress = (double) current / total;
                    if (progressSlider != null) {
                        progressSlider.setValue(progress * 100.0);
                    }
                    if (progressBar != null) {
                        progressBar.setProgress(progress);
                    }
                    if (expandedProgressBar != null) {
                        expandedProgressBar.setProgress(progress);
                    }
                    if (expandedCurrentTimeLabel != null) {
                        expandedCurrentTimeLabel.setText(formatTime(current));
                    }
                    if (expandedTotalTimeLabel != null) {
                        expandedTotalTimeLabel.setText(formatTime(total));
                    }
                }
                syncLyricsToPosition(current);
            }
        }));
        progressTimer.setCycleCount(Timeline.INDEFINITE);
    }

    private void setupProgressSlider() {
        if (progressSlider == null) {
            return;
        }
        progressSlider.setOnMousePressed(e  -> isDraggingSlider = true);
        progressSlider.setOnMouseReleased(e -> {
            long total = audioPlayer.getDuration();
            if (total > 0 && (isPlaying || isPaused)) {
                long newPosMs = (long) (progressSlider.getValue() / 100.0 * total);
                audioPlayer.seek(newPosMs);
                currentTimeLabel.setText(formatTime(newPosMs));
                syncLyricsToPosition(newPosMs);
                lanSync.notifySeek(newPosMs);
            }
            isDraggingSlider = false;
        });
    }

    // ── LAN Sync UI Handlers ────────────────────────────────────────────────

    @FXML
    public void handleHostSession() {
        if (lanSync.getMode() == LanSyncManager.Mode.HOST) {
            lanSync.stopSession();
            updateToggleButtonStyle(hostButton, false);
            updateToggleButtonStyle(mobileSettingsHostButton, false);
            updateSessionButtonLabels();
            updatePeerLabel(0);
                        updateStatus("LAN session stopped.");
            updatePublicLinkFields("", "Generating tunnel link...", false);
        } else {
            new Thread(() -> {
                try {
                    String ip = lanSync.startHosting();
                    Platform.runLater(() -> {
                        updateToggleButtonStyle(hostButton, true);
                        updateToggleButtonStyle(joinButton, false);
                        updateToggleButtonStyle(mobileSettingsHostButton, true);
                        updateToggleButtonStyle(mobileSettingsJoinButton, false);
                        updateSessionButtonLabels();
                                                updateStatus("Hosting on " + ip + " | Starting public tunnel...");
                        updatePeerLabel(0);
                        updatePublicLinkFields("", "Generating tunnel link...", true);
                    });
                    
                    // Automatically launch the public tunnel so users don't have to use CMD
                    lanSync.startPublicTunnel(url -> {
                        Platform.runLater(() -> {
                            if (url.startsWith("http")) {
                                updateStatus("Public link ready. Share it with friends.");
                                updatePublicLinkFields(url, "Public link ready", true);
                            } else {
                                updateStatus(url);
                            }
                        });
                    });
                    
                } catch (Exception e) {
                    Platform.runLater(() -> updateStatus("Could not start host: " + e.getMessage()));
                }
            }, "lan-host-start").start();
        }
    }

    @FXML
    public void handleJoinSession() {
        if (lanSync.getMode() == LanSyncManager.Mode.CLIENT) {
            lanSync.stopSession();
            updateToggleButtonStyle(joinButton, false);
            updateToggleButtonStyle(mobileSettingsJoinButton, false);
            updateSessionButtonLabels();
            updateStatus("Left LAN session.");
            updatePublicLinkFields("", "Generating tunnel link...", false);
            return;
        }
        // First try auto-discovery, then fall back to manual IP entry
        updateStatus("Scanning LAN for host...");
        new Thread(() -> {
            String discovered = LanSyncManager.discoverHost(3000);
            Platform.runLater(() -> {
                String hostIp = discovered;
                if (hostIp == null) {
                    // Manual IP dialog
                    TextInputDialog dlg = new TextInputDialog("192.168.1.");
                    dlg.setTitle("Join LAN Session");
                    dlg.setHeaderText("No host found automatically.");
                    dlg.setContentText("Enter host IP address:");
                    Optional<String> result = dlg.showAndWait();
                    if (result.isEmpty() || result.get().isBlank()) {
                        updateStatus("Join cancelled.");
                        return;
                    }
                    hostIp = result.get().trim();
                }
                final String finalIp = hostIp;
                updateStatus("Connecting to " + finalIp + "...");
                new Thread(() -> {
                    try {
                        lanSync.joinSession(finalIp);
                        Platform.runLater(() -> {
                            updateToggleButtonStyle(joinButton, true);
                            updateToggleButtonStyle(hostButton, false);
                            updateToggleButtonStyle(mobileSettingsJoinButton, true);
                            updateToggleButtonStyle(mobileSettingsHostButton, false);
                            updateSessionButtonLabels();
                            updateStatus("Joined LAN session at " + finalIp + ". Waiting for host to play...");
                            updatePublicLinkFields("", "Host session controls the share link", false);
                        });
                    } catch (Exception e) {
                        Platform.runLater(() -> updateStatus("Could not join: " + e.getMessage()));
                    }
                }, "lan-join").start();
            });
        }, "lan-discover").start();
    }

    private void updatePeerLabel(int count) {
        if (peerCountLabel != null) {
            peerCountLabel.setText(count > 0 ? count + " device" + (count == 1 ? "" : "s") + " connected" : "");
        }
        if (mobileSettingsPeerLabel != null) {
            mobileSettingsPeerLabel.setText(count > 0 ? count + " device" + (count == 1 ? "" : "s") + " connected" : "No peers connected yet");
        }
    }

    // ── Playlist & Search ──────────────────────────────────────────────────────

    @FXML
    public void handleSearch() {
        performSearch();
    }

    @FXML
    public void handleSearch(ActionEvent event) {
        performSearch();
    }

    private void performSearch() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) { updateStatus("Please enter a search query."); return; }
        if (youtubeService == null) { updateStatus("YouTube Service not connected."); return; }

        rememberSearchQuery(query);

                updateStatus("Searching YouTube for: " + query);
        setLoading(true);

        new Thread(() -> {
            try {
                List<YouTubeVideo> videos = youtubeService.searchVideos(query, 15);
                List<SongData> results = new ArrayList<>();
                for (YouTubeVideo v : videos) {
                    SongData s = new SongData();
                    s.setVideoId(v.getId());
                    s.setTitle(v.getTitle());
                    s.setChannel(v.getChannel());
                    s.setThumbnailUrl(v.getThumbnailUrl());
                    s.setType("youtube");
                    s.setLyricsSearchHint(query);
                    results.add(s);
                }
                Platform.runLater(() -> {
                    setLoading(false);
                    searchResultsList.setItems(FXCollections.observableArrayList(results));
                    updateStatus("Found " + results.size() + " results.");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    setLoading(false);
                    updateStatus("Search error: " + e.getMessage());
                });
            }
        }, "yt-search").start();
    }

    @FXML
    public void handleAddSearchResult() {
        SongData song = searchResultsList.getSelectionModel().getSelectedItem();
        if (song != null) {
            playlist.add(song);
            updateStatus("Added: " + song.getTitle());
            if (AppPlatform.isMobile()) {
                setMobileSearchVisible(false);
            }
            if (autoRadioEnabled) {
                autoFetchMoreSongs(song, false);
            }
        }
    }

    @FXML
    public void handleAddLocalFiles() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Add Audio Files");
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Audio Files", "*.mp3", "*.wav", "*.aac", "*.ogg", "*.flac"),
            new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        List<File> files = fc.showOpenMultipleDialog(playlistView.getScene().getWindow());
        if (files != null) {
            for (File f : files) {
                SongData s = new SongData();
                s.setTitle(f.getName());
                s.setPath(f.getAbsolutePath());
                s.setType("local");
                s.setChannel("Local File");
                playlist.add(s);
            }
            updateStatus("Added " + files.size() + " local file(s).");
        }
    }

    @FXML
    public void handleRemoveFromPlaylist() {
        SongData song = playlistView.getSelectionModel().getSelectedItem();
        if (song != null) {
            int idx = playlist.indexOf(song);
            if (idx == currentSongIndex && (isPlaying || isPaused)) stopMusic();
            playlist.remove(song);
            if (currentSongIndex >= playlist.size()) currentSongIndex = playlist.size() - 1;
            updateStatus("Removed: " + song.getTitle());
        }
    }

    @FXML
    public void handleClearPlaylist() {
        stopMusic();
        playlist.clear();
        currentSongIndex = -1;
        updateNowPlaying(null);
        updateStatus("Playlist cleared.");
    }

    @FXML
    public void handleOpenSettings() {
        if (AppPlatform.isMobile()) {
            setMobileSearchVisible(false);
            setMobileNowPlayingVisible(false);
        }
        FxSettingsWindow settingsView = new FxSettingsWindow(auth, settings, cache);
        settingsView.setOnClose(this::hideSettingsOverlay);
        settingsView.setOnSaved(() -> {
            syncSettingsToUi();
            updateStatus("Settings updated.");
        });

        try {
            Parent settingsRoot = settingsView.createView();
            settingsContainer.getChildren().setAll(buildSettingsOverlayContent(settingsRoot));
            settingsOverlay.setVisible(true);
            settingsOverlay.setManaged(true);
        } catch (IOException e) {
            updateStatus("Couldn't open settings: " + e.getMessage());
        }
    }

    @FXML
    public void handleDismissSettingsOverlay() {
        hideSettingsOverlay();
    }

    @FXML
    public void handleConsumeOverlayClick(MouseEvent event) {
        event.consume();
    }

    // ── Utility ────────────────────────────────────────────────────────────────

    private void updateStatus(String msg) {
        if (statusLabel != null) statusLabel.setText(msg);
        if (mobileSettingsStatusLabel != null) mobileSettingsStatusLabel.setText(msg);
    }

    private void setMobileSearchVisible(boolean visible) {
        if (mobileSearchOverlay != null) {
            mobileSearchOverlay.setVisible(visible);
            mobileSearchOverlay.setManaged(visible);
        }
        if (sidebar != null) {
            sidebar.setVisible(visible);
            sidebar.setManaged(visible);
        }
    }

    @FXML
    public void handleOpenNowPlaying() {
        if (!AppPlatform.isMobile()) {
            return;
        }
        setMobileSearchVisible(false);
        setMobileNowPlayingVisible(true);
    }

    @FXML
    public void handleOpenNowPlaying(MouseEvent event) {
        Object target = event.getTarget();
        if (target instanceof Node node) {
            while (node != null) {
                if (node instanceof ButtonBase) {
                    return;
                }
                node = node.getParent();
            }
        }
        handleOpenNowPlaying();
    }

    @FXML
    public void handleCloseNowPlaying() {
        setMobileNowPlayingVisible(false);
    }

    private void setMobileNowPlayingVisible(boolean visible) {
        if (mobileNowPlayingOverlay != null) {
            mobileNowPlayingOverlay.setVisible(visible);
            mobileNowPlayingOverlay.setManaged(visible);
        }
    }

    private void loadSearchHistory() {
        searchHistory.clear();
        if (!settings.getBoolean("privacy.remember_search_history", true)) {
            return;
        }

        String raw = settings.getString("privacy.search_history_entries", "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                String value = array.optString(i, "").trim();
                if (!value.isBlank()) {
                    searchHistory.add(value);
                }
            }
        } catch (Exception e) {
            System.err.println("Could not parse search history: " + e.getMessage());
        }
    }

    private void rememberSearchQuery(String query) {
        if (!settings.getBoolean("privacy.remember_search_history", true)) {
            return;
        }

        String trimmed = query == null ? "" : query.trim();
        if (trimmed.isBlank()) {
            return;
        }

        searchHistory.removeIf(item -> item.equalsIgnoreCase(trimmed));
        searchHistory.add(0, trimmed);
        while (searchHistory.size() > MAX_SEARCH_HISTORY) {
            searchHistory.remove(searchHistory.size() - 1);
        }
        saveSearchHistory();
        refreshSearchHistoryUi();
    }

    private void removeSearchHistoryEntry(String query) {
        if (query == null) {
            return;
        }
        searchHistory.removeIf(item -> item.equalsIgnoreCase(query));
        saveSearchHistory();
        refreshSearchHistoryUi();
    }

    private void saveSearchHistory() {
        JSONArray array = new JSONArray();
        for (String item : searchHistory) {
            array.put(item);
        }
        settings.set("privacy.search_history_entries", array.toString());
        settings.saveSettings();
    }

    private void refreshSearchHistoryUi() {
        if (searchHistorySection == null || searchHistoryContainer == null) {
            return;
        }

        boolean enabled = settings.getBoolean("privacy.remember_search_history", true);
        boolean visible = enabled && !searchHistory.isEmpty();
        searchHistorySection.setVisible(visible);
        searchHistorySection.setManaged(visible);
        searchHistoryContainer.getChildren().clear();

        if (!visible) {
            return;
        }

        for (String item : searchHistory) {
            HBox bubble = new HBox();
            bubble.getStyleClass().add("history-bubble");

            Button queryButton = new Button(item);
            queryButton.getStyleClass().add("history-chip-button");
            queryButton.setOnAction(event -> {
                searchField.setText(item);
                performSearch();
            });

            Button deleteButton = new Button("x");
            deleteButton.getStyleClass().add("history-delete-button");
            deleteButton.setOnAction(event -> removeSearchHistoryEntry(item));

            bubble.getChildren().addAll(queryButton, deleteButton);
            searchHistoryContainer.getChildren().add(bubble);
        }
    }

    public void setExternalUrlOpener(Consumer<String> externalUrlOpener) {
        this.externalUrlOpener = externalUrlOpener;
    }

    private boolean shouldUseOfficialYouTubePlayback() {
        return AppPlatform.isMobile()
            && auth != null
            && auth.isLoggedIn()
            && !auth.isRealLogin();
    }

    private void openSongInYouTube(SongData song) {
        if (song == null || song.getVideoId() == null || song.getVideoId().isBlank()) {
            updateStatus("This result is missing a YouTube video ID.");
            return;
        }
        if (externalUrlOpener == null) {
            updateStatus("YouTube playback is unavailable because no URL opener is configured.");
            return;
        }

        String youtubeUrl = "https://www.youtube.com/watch?v=" + song.getVideoId();
        externalUrlOpener.accept(youtubeUrl);
        isPlaying = false;
        isPaused = false;
        updatePlayPauseLabel();
        updateStatus("Opening YouTube: " + song.getTitle());
    }

    private void hideSettingsOverlay() {
        if (settingsOverlay == null || settingsContainer == null) {
            return;
        }
        settingsOverlay.setVisible(false);
        settingsOverlay.setManaged(false);
        settingsContainer.getChildren().clear();
        mobileSettingsHostButton = null;
        mobileSettingsJoinButton = null;
        mobileSettingsLinkField = null;
        mobileSettingsPeerLabel = null;
        mobileSettingsStatusLabel = null;
    }

    private Parent buildSettingsOverlayContent(Parent settingsRoot) {
        if (!AppPlatform.isMobile()) {
            return settingsRoot;
        }

        VBox shell = new VBox(16);
        shell.getStyleClass().add("mobile-settings-content");

        HBox header = new HBox(10);
        header.getStyleClass().add("mobile-settings-header");
        Label title = new Label("Settings");
        title.getStyleClass().add("settings-card-title");
        Label copy = new Label("Playback controls stay light on mobile. Sync actions live here.");
        copy.getStyleClass().add("settings-card-copy");
        copy.setWrapText(true);
        VBox heading = new VBox(4, title, copy);
        Button closeButton = new Button("Done");
        closeButton.getStyleClass().addAll("control-button", "mobile-settings-close");
        closeButton.setOnAction(event -> hideSettingsOverlay());
        HBox.setHgrow(heading, Priority.ALWAYS);
        header.getChildren().addAll(heading, closeButton);

        VBox syncCard = buildMobileSyncCard();

        ScrollPane settingsScroll = new ScrollPane(settingsRoot);
        settingsScroll.setFitToWidth(true);
        settingsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        settingsScroll.getStyleClass().add("mobile-settings-scroll");
        VBox.setVgrow(settingsScroll, Priority.ALWAYS);

        shell.getChildren().addAll(header, syncCard, settingsScroll);
        return shell;
    }

    private VBox buildMobileSyncCard() {
        VBox card = new VBox(10);
        card.getStyleClass().addAll("settings-card", "mobile-sync-card");

        Label eyebrow = new Label("SYNC");
        eyebrow.getStyleClass().add("section-label");

        Label title = new Label("Host or join together");
        title.getStyleClass().add("settings-card-title");

        Label help = new Label("These actions are moved into settings on mobile so the player stays uncluttered.");
        help.getStyleClass().add("settings-card-copy");
        help.setWrapText(true);

        mobileSettingsHostButton = new Button();
        mobileSettingsHostButton.getStyleClass().addAll("icon-button", "mobile-sync-button");
        mobileSettingsHostButton.setOnAction(event -> handleHostSession());

        mobileSettingsJoinButton = new Button();
        mobileSettingsJoinButton.getStyleClass().addAll("icon-button", "mobile-sync-button");
        mobileSettingsJoinButton.setOnAction(event -> handleJoinSession());

        HBox actions = new HBox(10, mobileSettingsHostButton, mobileSettingsJoinButton);
        HBox.setHgrow(mobileSettingsHostButton, Priority.ALWAYS);
        HBox.setHgrow(mobileSettingsJoinButton, Priority.ALWAYS);
        mobileSettingsHostButton.setMaxWidth(Double.MAX_VALUE);
        mobileSettingsJoinButton.setMaxWidth(Double.MAX_VALUE);

        mobileSettingsLinkField = new TextField();
        mobileSettingsLinkField.setEditable(false);
        mobileSettingsLinkField.setPromptText("Share link appears here when hosting");
        mobileSettingsLinkField.getStyleClass().add("mobile-sync-link");

        mobileSettingsPeerLabel = new Label("No peers connected yet");
        mobileSettingsPeerLabel.getStyleClass().add("mobile-sync-meta");

        mobileSettingsStatusLabel = new Label(statusLabel != null ? statusLabel.getText() : "Ready");
        mobileSettingsStatusLabel.setWrapText(true);
        mobileSettingsStatusLabel.getStyleClass().add("mobile-sync-status");

        card.getChildren().addAll(eyebrow, title, help, actions, mobileSettingsLinkField, mobileSettingsPeerLabel, mobileSettingsStatusLabel);

        updateSessionButtonLabels();
        updateToggleButtonStyle(mobileSettingsHostButton, lanSync.getMode() == LanSyncManager.Mode.HOST);
        updateToggleButtonStyle(mobileSettingsJoinButton, lanSync.getMode() == LanSyncManager.Mode.CLIENT);
        updatePeerLabel(peerCountLabel != null && !peerCountLabel.getText().isBlank() ? extractPeerCount(peerCountLabel.getText()) : 0);

        if (publicLinkField != null) {
            updatePublicLinkFields(publicLinkField.getText(), publicLinkField.getPromptText(), publicLinkField.isVisible());
        } else {
            updatePublicLinkFields("", "Share link appears here when hosting", false);
        }

        return card;
    }

    private void updatePublicLinkFields(String text, String prompt, boolean visible) {
        applyPublicLinkFieldState(publicLinkField, text, prompt, visible);
        applyPublicLinkFieldState(mobileSettingsLinkField, text, prompt, visible);
    }

    private void applyPublicLinkFieldState(TextField field, String text, String prompt, boolean visible) {
        if (field == null) {
            return;
        }
        field.setText(text == null ? "" : text);
        field.setPromptText(prompt == null ? "" : prompt);
        field.setVisible(visible);
        field.setManaged(visible);
    }

    private int extractPeerCount(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        String digits = text.replaceAll("[^0-9]", "");
        if (digits.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private void syncSettingsToUi() {
        int volume = settings.getInt("audio.volume", 70);
        setVolume(volume);
        if (volumeSlider != null) {
            volumeSlider.setValue(volume);
        }
    }

    private void updateNowPlaying(SongData song) {
        if (song == null) {
            songTitleLabel.setText("No song selected");
            artistLabel.setText("Select a song from the playlist");
            if (expandedSongTitleLabel != null) {
                expandedSongTitleLabel.setText("No song selected");
            }
            if (expandedArtistLabel != null) {
                expandedArtistLabel.setText("Select a song from the playlist");
            }
            setAlbumArt(null);
            playlistView.getSelectionModel().clearSelection();
            lanSync.clearWebTrackInfo();
            clearLyrics("Pick a song to light up the lyrics panel.", "Waiting for track");
        } else {
            songTitleLabel.setText(song.getTitle());
            artistLabel.setText(song.getChannel() != null ? song.getChannel() : "Unknown Artist");
            if (expandedSongTitleLabel != null) {
                expandedSongTitleLabel.setText(song.getTitle());
            }
            if (expandedArtistLabel != null) {
                expandedArtistLabel.setText(song.getChannel() != null ? song.getChannel() : "Unknown Artist");
            }
            playlistView.getSelectionModel().select(song);
            setAlbumArt(song.getThumbnailUrl());
            lanSync.updateWebTrackInfo(song.getTitle(), artistLabel.getText());
            renderLyricsPlaceholder("Fetching lyrics for " + song.getTitle(), "Searching lyrics");
        }
    }

    private void loadLyricsForSong(SongData song) {
        if (song == null) {
            clearLyrics("Pick a song to light up the lyrics panel.", "Waiting for track");
            return;
        }

        long requestId = ++lyricsRequestSequence;
        long duration = audioPlayer.getDuration();
        renderLyricsPlaceholder("Fetching lyrics for " + song.getTitle(), "Searching lyrics");

        new Thread(() -> {
            LyricsData lyricsData = lyricsService.fetchLyrics(song, duration);
            Platform.runLater(() -> {
                if (requestId != lyricsRequestSequence) {
                    return;
                }
                applyLyrics(lyricsData);
            });
        }, "lyrics-fetch").start();
    }

    private void applyLyrics(LyricsData lyricsData) {
        currentLyrics = lyricsData == null ? List.of() : lyricsData.getLines();
        currentLyricsSynced = lyricsData != null && lyricsData.isSynced();
        currentLyricIndex = -1;
        lyricLineLabels.clear();
        lyricsContainer.getChildren().clear();

        if (lyricsData == null || lyricsData.isEmpty()) {
            lanSync.updateWebLyrics(null);
            renderLyricsPlaceholder("Couldn't match lyrics from the current source for this track yet.", "Lyrics unavailable");
            return;
        }

        lyricsHeadlineLabel.setText(currentLyricsSynced
            ? "Lyrics move with the music."
            : "Lyrics found, but this track does not include timing.");
                lyricsSourceLabel.setText((currentLyricsSynced ? "Synced" : "Static") + " - " + lyricsData.getSource());
        if (expandedLyricsBadgeLabel != null) {
            expandedLyricsBadgeLabel.setText(currentLyricsSynced ? "Lyrics synced" : "Lyrics available");
        }

        for (LyricsLine line : currentLyrics) {
            Label lyricLine = new Label(line.getText());
            lyricLine.setWrapText(true);
            lyricLine.setMaxWidth(Double.MAX_VALUE);
            lyricLine.getStyleClass().add("lyrics-line");
            lyricLineLabels.add(lyricLine);
            lyricsContainer.getChildren().add(lyricLine);
        }

        if (currentLyricsSynced) {
            syncLyricsToPosition(audioPlayer.getCurrentPosition());
        } else {
            styleLyrics(-1);
            if (lyricsScrollPane != null) {
                lyricsScrollPane.setVvalue(0);
            }
        }
        lanSync.updateWebLyrics(lyricsData);
    }

    private void renderLyricsPlaceholder(String headline, String sourceText) {
        currentLyrics = List.of();
        currentLyricsSynced = false;
        currentLyricIndex = -1;
        lyricLineLabels.clear();

        if (lyricsContainer == null) {
            return;
        }

        lyricsHeadlineLabel.setText(headline);
        lyricsSourceLabel.setText(sourceText);
        if (expandedLyricsBadgeLabel != null) {
            expandedLyricsBadgeLabel.setText(sourceText);
        }
        lyricsContainer.getChildren().clear();

        Label placeholder = new Label("Timed lyrics will lock onto the song here when they are available.");
        placeholder.setWrapText(true);
        placeholder.setMaxWidth(Double.MAX_VALUE);
        placeholder.getStyleClass().addAll("lyrics-line", "lyrics-line-active");
        lyricsContainer.getChildren().add(placeholder);

        if (lyricsScrollPane != null) {
            lyricsScrollPane.setVvalue(0);
        }
    }

    private void clearLyrics(String headline, String sourceText) {
        lyricsRequestSequence++;
        renderLyricsPlaceholder(headline, sourceText);
    }

    private void resetLyricsProgress() {
        currentLyricIndex = -1;
        if (currentLyricsSynced) {
            styleLyrics(-1);
        }
        if (lyricsScrollPane != null) {
            lyricsScrollPane.setVvalue(0);
        }
    }

    private void syncLyricsToPosition(long currentMs) {
        if (!currentLyricsSynced || currentLyrics.isEmpty() || lyricLineLabels.isEmpty()) {
            return;
        }

        int nextIndex = findLyricIndex(currentMs);
        if (nextIndex != currentLyricIndex) {
            currentLyricIndex = nextIndex;
            styleLyrics(nextIndex);
            scrollLyricsTo(nextIndex);
        }
    }

    private int findLyricIndex(long currentMs) {
        int index = -1;
        for (int i = 0; i < currentLyrics.size(); i++) {
            if (currentLyrics.get(i).getTimestampMs() <= currentMs) {
                index = i;
            } else {
                break;
            }
        }
        return index;
    }

    private void styleLyrics(int activeIndex) {
        for (int i = 0; i < lyricLineLabels.size(); i++) {
            Label lyricLine = lyricLineLabels.get(i);
            lyricLine.getStyleClass().removeAll("lyrics-line-past", "lyrics-line-active", "lyrics-line-upcoming");

            if (activeIndex >= 0 && i < activeIndex) {
                lyricLine.getStyleClass().add("lyrics-line-past");
            } else if (i == activeIndex) {
                lyricLine.getStyleClass().add("lyrics-line-active");
            } else {
                lyricLine.getStyleClass().add("lyrics-line-upcoming");
            }
        }
    }

    private void scrollLyricsTo(int activeIndex) {
        if (lyricsScrollPane == null || activeIndex < 0 || currentLyrics.size() < 2) {
            return;
        }
        int focusIndex = Math.max(0, activeIndex - 1);
        double position = (double) focusIndex / Math.max(1, currentLyrics.size() - 1);
        lyricsScrollPane.setVvalue(Math.max(0, Math.min(1, position)));
    }

    private String formatTime(long millis) {
        long s = (millis / 1000) % 60;
        long m = (millis / 60000) % 60;
        long h = millis / 3600000;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
    }

    public void shutdown() {
        if (progressTimer != null) progressTimer.stop();
        stopMusic();
        lanSync.stopSession();
        updateSessionButtonLabels();
        cache.saveCache();
        YtDlpStreamResolver.cleanup();
        System.out.println("FxMusicPlayer shut down.");
    }
}
