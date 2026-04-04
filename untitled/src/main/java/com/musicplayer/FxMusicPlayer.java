package com.musicplayer;

import org.json.JSONArray;
import org.json.JSONObject;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldListCell;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
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
    private final PlaylistShelfStore shelfStore = new PlaylistShelfStore();
    private Consumer<String> externalUrlOpener;

    // --- FXML UI Components ---
    @FXML private Label userInfoLabel;
    @FXML private TextField searchField;
    @FXML private StackPane desktopHomePage;
    @FXML private StackPane desktopSearchPage;
    @FXML private StackPane desktopSongDetailsPage;
    @FXML private VBox desktopMiniPlayerBar;
    @FXML private Label desktopMiniPlayerTitleLabel;
    @FXML private Label desktopMiniPlayerArtistLabel;
    @FXML private VBox searchHistorySection;
    @FXML private FlowPane searchHistoryContainer;
    @FXML private ListView<SongData> searchResultsList;
    @FXML private ListView<String> searchSuggestionsList;
    @FXML private ListView<SongData> desktopRecentPlayedList;
    @FXML private ListView<SongData> desktopHomeRecommendationsList;
    @FXML private ListView<String> desktopCustomPlaylistsView;
    @FXML private ListView<SongData> desktopCustomPlaylistSongsView;
    @FXML private ComboBox<String> desktopPlaylistPicker;
    @FXML private ComboBox<String> desktopDetailsPlaylistPicker;
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
    @FXML private HBox mobilePlayerDock;
    @FXML private Button shuffleButton;
    @FXML private Button repeatButton;
    @FXML private Button autoRadioButton;
    @FXML private ProgressIndicator loadingIndicator;
    @FXML private FlowPane searchCategoriesBox;
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
    @FXML private ProgressBar miniProgressLine;
    @FXML private Slider expandedProgressSlider;
    @FXML private Label mTimeC;
    @FXML private Label mTimeT;
    @FXML private Button expandedPlayPauseButton;
    @FXML private Label expandedLyricsBadgeLabel;
    @FXML private ListView<SongData> mobileHomeQueueList;
    @FXML private ListView<SongData> mobileRecommendationsList;
    @FXML private Label mobileQueueSummaryLabel;
    @FXML private Label mobileRecommendationSummaryLabel;
    @FXML private ListView<SongData> likedSongsView;
    @FXML private ListView<SongData> desktopRecommendationsList;
    @FXML private Label desktopQueueSummaryLabel;
    @FXML private Label desktopRecommendationSummaryLabel;
    @FXML private Label likedSongsSummaryLabel;
    @FXML private Label desktopRecentSummaryLabel;
    @FXML private Label desktopPlaylistSummaryLabel;
    private Button mobileSettingsHostButton;
    private Button mobileSettingsJoinButton;
    private TextField mobileSettingsLinkField;
    private Label mobileSettingsPeerLabel;
    private Label mobileSettingsStatusLabel;

    // --- State ---
    private final ObservableList<SongData> playlist = FXCollections.observableArrayList();
    private final ObservableList<SongData> likedSongs = FXCollections.observableArrayList();
    private final ObservableList<SongData> recommendedSongs = FXCollections.observableArrayList();
    private final ObservableList<String> searchSuggestions = FXCollections.observableArrayList();
    private final ObservableList<SongData> recentPlayedSongs = FXCollections.observableArrayList();
    private final ObservableList<String> customPlaylistNames = FXCollections.observableArrayList();
    private final ObservableList<SongData> selectedCustomPlaylistSongs = FXCollections.observableArrayList();
    private final Map<String, ObservableList<SongData>> customPlaylists = new LinkedHashMap<>();
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
    private final Map<String, SongData> webSearchIndex = new ConcurrentHashMap<>();
    private volatile String lastStatusMessage = "App Ready - Select a track";
    private long recommendationRequestSequence = 0;
    private long playbackRequestSequence = 0;
    private long activePlaybackRequestId = 0;
    private long searchSuggestionRequestSequence = 0;
    private boolean mobileLibraryMode = false;
    private final Timeline searchSuggestionDebounce = new Timeline();

    public FxMusicPlayer(AuthSystem auth, SettingsManager settings) {
        this.auth = auth;
        this.settings = settings;
        this.cache = new CacheManager();
        this.audioPlayer = new AudioPlayer();
        this.audioPlayer.setOnError(err -> Platform.runLater(() -> {
            updateStatus("Playback Error: " + err);
            // Also show a temporary alert on mobile to be sure user sees it
            if (AppPlatform.isMobile()) {
                statusLabel.setText("ERROR: " + err);
            }
        }));

        // Auto-advance only when the active track really reaches the end.
        this.audioPlayer.setOnEndOfMedia(() -> Platform.runLater(this::handleNaturalTrackEnd));

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

        lanSync.setWebAppBridge(new WebAppBridge() {
            @Override
            public JSONObject getState() {
                return buildWebStateSnapshot();
            }

            @Override
            public JSONObject search(String query) throws Exception {
                return searchSongsForWeb(query);
            }

            @Override
            public JSONObject addToQueue(String videoId, boolean playNow) {
                return addSongToQueueFromWeb(videoId, playNow);
            }

            @Override
            public JSONObject playQueueIndex(int index) {
                return playQueueIndexFromWeb(index);
            }

            @Override
            public JSONObject removeQueueIndex(int index) {
                return removeQueueIndexFromWeb(index);
            }

            @Override
            public JSONObject clearQueue() {
                return clearQueueFromWeb();
            }

            @Override
            public JSONObject setToggle(String toggle, boolean enabled) {
                return setToggleFromWeb(toggle, enabled);
            }

            @Override
            public JSONObject addRelated() {
                return addRelatedSongsFromWeb();
            }

            @Override
            public JSONObject toggleLiked(String videoId) {
                return toggleLikedFromWeb(videoId);
            }

            @Override
            public JSONObject playLiked(String videoId) {
                return playLikedFromWeb(videoId);
            }

            @Override
            public JSONObject removeLiked(String videoId) {
                return removeLikedFromWeb(videoId);
            }
        });
    }

    @FXML
    public void initialize() {
        // Bind playlist
        playlistView.setItems(playlist);
        configureListView(playlistView);
        configureListView(searchResultsList);
        if (searchSuggestionsList != null) {
            searchSuggestionsList.setItems(searchSuggestions);
            searchSuggestionsList.setOnMouseClicked(event -> {
                if (event.getClickCount() == 1) {
                    String suggestion = searchSuggestionsList.getSelectionModel().getSelectedItem();
                    if (suggestion != null && !suggestion.isBlank()) {
                        searchField.setText(suggestion);
                        performSearch();
                    }
                }
            });
        }
        if (desktopRecentPlayedList != null) {
            desktopRecentPlayedList.setItems(recentPlayedSongs);
            configureListView(desktopRecentPlayedList);
        }
        if (desktopHomeRecommendationsList != null) {
            desktopHomeRecommendationsList.setItems(recommendedSongs);
            configureListView(desktopHomeRecommendationsList);
        }
        if (desktopCustomPlaylistsView != null) {
            desktopCustomPlaylistsView.setItems(customPlaylistNames);
            desktopCustomPlaylistsView.setCellFactory(lv -> new TextFieldListCell<>());
            desktopCustomPlaylistsView.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
                if (desktopPlaylistPicker != null && newValue != null && !newValue.equals(desktopPlaylistPicker.getValue())) {
                    desktopPlaylistPicker.setValue(newValue);
                }
                refreshDesktopCustomPlaylistSongs(newValue);
            });
        }
        if (desktopCustomPlaylistSongsView != null) {
            desktopCustomPlaylistSongsView.setItems(selectedCustomPlaylistSongs);
            configureListView(desktopCustomPlaylistSongsView);
        }
        if (desktopPlaylistPicker != null) {
            desktopPlaylistPicker.setItems(customPlaylistNames);
            desktopPlaylistPicker.valueProperty().addListener((obs, oldValue, newValue) -> {
                if (desktopCustomPlaylistsView != null && newValue != null && !newValue.equals(desktopCustomPlaylistsView.getSelectionModel().getSelectedItem())) {
                    desktopCustomPlaylistsView.getSelectionModel().select(newValue);
                }
            });
        }
        if (desktopDetailsPlaylistPicker != null) {
            desktopDetailsPlaylistPicker.setItems(customPlaylistNames);
        }
        if (likedSongsView != null) {
            likedSongsView.setItems(likedSongs);
            configureListView(likedSongsView);
        }
        if (desktopRecommendationsList != null) {
            desktopRecommendationsList.setItems(recommendedSongs);
            configureListView(desktopRecommendationsList);
        }
        if (mobileHomeQueueList != null) {
            mobileHomeQueueList.setItems(playlist);
            configureListView(mobileHomeQueueList);
        }
        if (mobileRecommendationsList != null) {
            mobileRecommendationsList.setItems(recommendedSongs);
            configureListView(mobileRecommendationsList);
        }
        likedSongs.setAll(shelfStore.loadLikedSongs());
        recentPlayedSongs.setAll(shelfStore.loadRecentPlayedSongs());
        loadCustomPlaylists();
        playlist.addListener((ListChangeListener<SongData>) change -> {
            refreshMobileQueueSummary();
            refreshDesktopQueueSummary();
            if (playlistView != null) {
                playlistView.refresh();
            }
            if (mobileHomeQueueList != null) {
                mobileHomeQueueList.refresh();
            }
        });
        likedSongs.addListener((ListChangeListener<SongData>) change -> {
            shelfStore.saveLikedSongs(likedSongs);
            refreshLikedSongsSummary();
            if (likedSongsView != null) {
                likedSongsView.refresh();
            }
        });
        recentPlayedSongs.addListener((ListChangeListener<SongData>) change -> {
            shelfStore.saveRecentPlayedSongs(recentPlayedSongs);
            refreshRecentPlayedSummary();
            if (desktopRecentPlayedList != null) {
                desktopRecentPlayedList.refresh();
            }
        });
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

        audioPlayer.setOnReady(() -> {
            Platform.runLater(() -> {
                SongData current = playlist.get(currentSongIndex);
                if (current != null) {
                    System.out.println("Metadata ready for: " + current.getTitle() + ", Duration: " + audioPlayer.getDuration());
                    loadLyricsForSong(current);
                }
            });
        });

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
        refreshMobileQueueSummary();
        refreshDesktopQueueSummary();
        refreshLikedSongsSummary();
        refreshRecentPlayedSummary();
        refreshDesktopCustomPlaylistSongs(getSelectedCustomPlaylistName());
        refreshMobileRecommendations(null);

        // Shuffle / Repeat / Auto Radio button initial style
        updateToggleButtonStyle(shuffleButton, shuffleEnabled);
        updateToggleButtonStyle(repeatButton, repeatEnabled);
        updateToggleButtonStyle(autoRadioButton, autoRadioEnabled);

        if (mobilePlayerDock != null) {
            mobilePlayerDock.setOnMouseClicked(this::handleMobileDockClick);
        }

        if (searchField != null) {
            searchField.setOnAction(event -> performSearch());
            searchSuggestionDebounce.getKeyFrames().setAll(
                new KeyFrame(Duration.millis(260), event -> triggerSearchSuggestions())
            );
            searchField.textProperty().addListener((obs, oldValue, newValue) -> {
                if (AppPlatform.isMobile() && !mobileSearchOverlay.isVisible()) {
                    return;
                }
                searchSuggestionDebounce.playFromStart();
            });
        }

        // Setup responsive layout listener (runs after scene is created)
        Platform.runLater(() -> {
            setupResponsiveListener();
            if (statusLabel != null) {
                statusLabel.getStyleClass().add("status-label-diagnostic");
                updateStatus("App Ready - Select a track");
            }
            if (!AppPlatform.isMobile()) {
                showDesktopPage("home");
            }
        });
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

    //  UI Helpers 

    private void configureListView(ListView<SongData> listView) {
        listView.setCellFactory(lv -> {
            ListCell<SongData> cell = new ListCell<>() {
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
            };
            
            // Step 6: Robust touch/click listener on the cell itself
            cell.setOnMouseClicked(e -> {
                if (!cell.isEmpty() && cell.getItem() != null) {
                    System.out.println("UI: Cell Clicked for " + cell.getItem().getTitle());
                    if (listView == searchResultsList) {
                        if (AppPlatform.isMobile() || e.getClickCount() >= 2) {
                            addToQueueAndPlay(cell.getItem());
                        }
                    } else if (listView == mobileHomeQueueList) {
                        currentSongIndex = indexOfSongInPlaylist(cell.getItem());
                        if (currentSongIndex >= 0) {
                            isPaused = false;
                            playMusic();
                        }
                    } else if (listView == desktopRecentPlayedList) {
                        addToQueueAndPlay(cloneSong(cell.getItem()));
                    } else if (listView == desktopHomeRecommendationsList) {
                        addToQueueAndPlay(cloneSong(cell.getItem()));
                    } else if (listView == desktopRecommendationsList) {
                        addToQueueAndPlay(cell.getItem());
                    } else if (listView == mobileRecommendationsList) {
                        addToQueueAndPlay(cell.getItem());
                    } else if (listView == likedSongsView) {
                        addToQueueAndPlay(cloneSong(cell.getItem()));
                    } else if (listView == desktopCustomPlaylistSongsView) {
                        addToQueueAndPlay(cloneSong(cell.getItem()));
                    } else if (listView == playlistView && e.getClickCount() == 2) {
                        handlePlaylistDoubleClick();
                    }
                }
            });
            
            return cell;
        });
    }

    private void setAlbumArt(String thumbnailUrl) {
        Image image = (thumbnailUrl != null && !thumbnailUrl.isEmpty()) ? new Image(thumbnailUrl, true) : null;
        try {
            if (albumArtView != null) {
                if (image != null) {
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
            } else if (expandedAlbumArtView != null) {
                expandedAlbumArtView.setImage(image != null ? image : defaultAlbumArt);
            }
        } catch (Exception e) {
            if (albumArtView != null) albumArtView.setImage(defaultAlbumArt);
            if (expandedAlbumArtView != null) expandedAlbumArtView.setImage(defaultAlbumArt);
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
            playPauseButton.setText("");
            playPauseButton.getStyleClass().remove("playing");
            if (isPlaying) playPauseButton.getStyleClass().add("playing");
        }
        if (expandedPlayPauseButton != null) {
            expandedPlayPauseButton.setText("");
            expandedPlayPauseButton.getStyleClass().remove("playing");
            if (isPlaying) expandedPlayPauseButton.getStyleClass().add("playing");
        }
    }

    private void updateSessionButtonLabels() {
        if (lanSync != null) {
            if (hostButton != null) hostButton.setText(lanSync.getMode() == LanSyncManager.Mode.HOST ? "Stop Sync" : "Start Sync");
            if (joinButton != null) joinButton.setText(lanSync.getMode() == LanSyncManager.Mode.CLIENT ? "Leave" : "Connect");
            if (mobileSettingsHostButton != null) mobileSettingsHostButton.setText(lanSync.getMode() == LanSyncManager.Mode.HOST ? "Stop Sync" : "Host Session");
            if (mobileSettingsJoinButton != null) mobileSettingsJoinButton.setText(lanSync.getMode() == LanSyncManager.Mode.CLIENT ? "Leave Session" : "Join Session");
        }
    }

    private void setLoading(boolean loading) {
        Runnable apply = () -> {
            if (loadingIndicator != null) {
                loadingIndicator.setVisible(loading);
                loadingIndicator.setManaged(loading);
            }
        };
        if (Platform.isFxApplicationThread()) {
            apply.run();
        } else {
            Platform.runLater(apply);
        }
    }

    private void showDesktopPage(String page) {
        if (AppPlatform.isMobile()) {
            return;
        }
        setDesktopPageVisible(desktopHomePage, "home".equals(page));
        setDesktopPageVisible(desktopSearchPage, "search".equals(page));
        setDesktopPageVisible(desktopSongDetailsPage, "details".equals(page));
    }

    private void setDesktopPageVisible(StackPane page, boolean visible) {
        if (page == null) {
            return;
        }
        page.setVisible(visible);
        page.setManaged(visible);
    }

    //  Player Controls 

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
        long playbackRequestId = beginPlaybackRequest();
        updateStatus("PLAY: " + song.getTitle() + " [" + (song.getType() != null ? song.getType() : "unknown") + "]");
        updateNowPlaying(song);

        if ("local".equals(song.getType())) {
            playLocal(song, playbackRequestId);
        } else {
            playYouTube(song, playbackRequestId);
        }
    }

    private void playLocal(SongData song, long playbackRequestId) {
        try {
            if (!isPlaybackRequestCurrent(playbackRequestId)) {
                return;
            }
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
    private void playYouTube(SongData song, long playbackRequestId) {
        if (song.isGuestPlaybackBlocked()) {
            updateStatus(song.getPlaybackIssue() != null
                ? song.getPlaybackIssue()
                : "This track can't be played in Guest Mode. Try another result or sign in.");
            tryAdvanceAfterBlockedTrack(song);
            return;
        }

        updateStatus("RESOLVE: " + song.getTitle());
        setLoading(true);

        new Thread(() -> {
            boolean[] completed = {false};
            // 20-second Watchdog
            new Thread(() -> {
                try { Thread.sleep(20000); } catch (Exception ignored) {}
                if (!completed[0] && isPlaybackRequestCurrent(playbackRequestId)) {
                    Platform.runLater(() -> {
                        if (!isPlaybackRequestCurrent(playbackRequestId)) {
                            return;
                        }
                        setLoading(false);
                        updateStatus("Error: Resolution timed out for " + song.getTitle());
                    });
                }
            }, "resolver-watchdog").start();

            try {
                Platform.runLater(() -> {
                    if (isPlaybackRequestCurrent(playbackRequestId)) {
                        updateStatus("Resolving stream...");
                    }
                });
                String streamUrl = resolveOnlineStream(song);
                if (!isPlaybackRequestCurrent(playbackRequestId)) {
                    completed[0] = true;
                    return;
                }
                
                System.out.println("DEBUG: Resolved stream URL: " + (streamUrl != null ? streamUrl.substring(0, Math.min(streamUrl.length(), 50)) : "null"));

                Platform.runLater(() -> {
                    if (!isPlaybackRequestCurrent(playbackRequestId)) {
                        completed[0] = true;
                        return;
                    }
                    completed[0] = true;
                    setLoading(false);
                    try {
                        String displayUrl = (streamUrl != null && streamUrl.length() > 30) ? streamUrl.substring(0, 30) : streamUrl;
                        updateStatus("URL: " + displayUrl + "...");
                        audioPlayer.play(streamUrl);
                        onPlaybackStarted();
                        updateStatus("Buffering...");
                        
                        // Notify LAN clients
                        lanSync.notifyPlay(song.getVideoId(), streamUrl, 0);
                    } catch (Exception e) {
                        updateStatus("Playback error: " + e.getMessage());
                        handleNext();
                    }
                });

            } catch (Throwable e) {
                completed[0] = true;
                Platform.runLater(() -> {
                    if (!isPlaybackRequestCurrent(playbackRequestId)) {
                        return;
                    }
                    setLoading(false);
                    if (shouldUseOfficialYouTubePlayback()) {
                        // On Android guest mode, open YouTube immediately without
                        // showing the raw technical error message.
                        updateStatus("Opening in YouTube...");
                        openSongInYouTube(song);
                        return;
                    }
                    // On desktop or logged-in users: show the error and try to advance
                    String rawMsg = e.getMessage();
                    String displayMsg = (rawMsg != null && rawMsg.length() > 120)
                        ? rawMsg.substring(0, 120) + "..." : rawMsg;
                    updateStatus("Stream error: " + displayMsg);
                    handlePlaybackFailure(song, new Exception(e), playbackRequestId);
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
            recordRecentPlay(currentSong);
            updateStatus("Now Playing: " + currentSong.getTitle());
            loadLyricsForSong(currentSong);
        }
    }

    private void recordRecentPlay(SongData song) {
        if (song == null) {
            return;
        }
        recentPlayedSongs.removeIf(existing -> isSameSong(existing, song));
        recentPlayedSongs.add(0, cloneSong(song));
        while (recentPlayedSongs.size() > 12) {
            recentPlayedSongs.remove(recentPlayedSongs.size() - 1);
        }
    }

    private void handleNaturalTrackEnd() {
        long duration = audioPlayer.getDuration();
        long position = audioPlayer.getCurrentPosition();
        if (duration > 0 && position + 2500 < duration) {
            System.out.println("Ignoring premature end-of-media callback at " + position + " / " + duration);
            return;
        }
        handleNext();
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
        invalidatePlaybackRequests();
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
        if (currentTimeLabel != null) currentTimeLabel.setText("0:00");
        if (totalTimeLabel != null) totalTimeLabel.setText("0:00");
        if (expandedProgressBar != null) {
            expandedProgressBar.setProgress(0);
        }
        if (expandedProgressSlider != null) {
            expandedProgressSlider.setValue(0);
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

    //  Auto Radio 

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
        int existingIndex = indexOfSongInPlaylist(song);
        if (existingIndex == -1) {
            playlist.add(song);
            existingIndex = playlist.size() - 1;
        }
        currentSongIndex = existingIndex;
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
        lanSync.notifyWebStateChanged();
    }

    private void handlePlaybackFailure(SongData song, Exception error) {
        handlePlaybackFailure(song, error, activePlaybackRequestId);
    }

    private void handlePlaybackFailure(SongData song, Exception error, long playbackRequestId) {
        if (!isPlaybackRequestCurrent(playbackRequestId)) {
            return;
        }
        if (song != null && error instanceof GuestPlaybackUnavailableException) {
            song.setGuestPlaybackBlocked(true);
            song.setPlaybackIssue(error.getMessage());
            playlistView.refresh();
            searchResultsList.refresh();
            updateStatus(error.getMessage());
            tryAdvanceAfterBlockedTrack(song);
            return;
        }

        String msg = "Stream error: " + error.getMessage();
        if (error.getMessage() != null && error.getMessage().contains("decoding")) {
            msg = "Playback error: Media codec issue. Try another track.";
        }
        updateStatus(msg);
        new Timeline(new KeyFrame(Duration.seconds(4), ev -> handleNext())).play();
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

        List<String> queries = buildAutoRadioQueries(baseSong);
        updateStatus("Radio: finding songs like \"" + baseSong.getTitle() + "\"...");

        new Thread(() -> {
            try {
                List<SongData> songsToAdd = new ArrayList<>();
                for (String query : queries) {
                    if (songsToAdd.size() >= 5) {
                        break;
                    }

                    List<YouTubeVideo> videos;
                    try {
                        videos = youtubeService.searchVideos(query, 8);
                    } catch (Exception searchError) {
                        System.err.println("Auto-radio search failed for query '" + query + "': " + searchError.getMessage());
                        continue;
                    }

                    for (YouTubeVideo v : videos) {
                        if (!isRelatedAutoRadioCandidate(baseSong, songsToAdd, v)) {
                            continue;
                        }

                        SongData s = new SongData();
                        s.setVideoId(v.getId());
                        s.setTitle(v.getTitle());
                        s.setChannel(v.getChannel());
                        s.setThumbnailUrl(v.getThumbnailUrl());
                        s.setType("youtube");
                        s.setLyricsSearchHint(query);
                        songsToAdd.add(s);

                        if (songsToAdd.size() >= 5) {
                            break;
                        }
                    }
                }

                Platform.runLater(() -> {
                    int added = songsToAdd.size();
                    playlist.addAll(songsToAdd);
                    if (added > 0) {
                                                updateStatus("Radio added " + added + " songs - keep listening!");
                        lanSync.notifyWebStateChanged();
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

    private List<String> buildAutoRadioQueries(SongData baseSong) {
        return RelatedTrackHelper.buildQueries(baseSong);
    }

    private boolean isRelatedAutoRadioCandidate(SongData baseSong, List<SongData> pendingSongs, YouTubeVideo candidate) {
        if (candidate == null || candidate.getId() == null || candidate.getId().isBlank()) {
            return false;
        }

        String candidateId = candidate.getId().trim();
        String baseId = safeText(baseSong.getVideoId());
        if (!baseId.isBlank() && baseId.equals(candidateId)) {
            return false;
        }

        boolean duplicate = playlist.stream()
                .anyMatch(existing -> candidateId.equals(safeText(existing.getVideoId())));
        if (duplicate) {
            return false;
        }

        boolean pendingDuplicate = pendingSongs.stream()
                .anyMatch(existing -> candidateId.equals(safeText(existing.getVideoId())));
        if (pendingDuplicate) {
            return false;
        }

        if (RelatedTrackHelper.isTooSimilar(baseSong, candidate.getTitle(), candidate.getChannel())) {
            return false;
        }

        return true;
    }

    private String safeText(String value) {
        return value == null ? "" : value;
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

    //  Volume & Progress 

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
                    if (currentTimeLabel != null) currentTimeLabel.setText(formatTime(current));
                    if (totalTimeLabel != null) totalTimeLabel.setText(formatTime(total));
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
                    if (expandedProgressSlider != null) {
                        expandedProgressSlider.setValue(progress * 100);
                    }
                    if (expandedCurrentTimeLabel != null) {
                        expandedCurrentTimeLabel.setText(formatTime(current));
                    }
                    if (expandedTotalTimeLabel != null) {
                        expandedTotalTimeLabel.setText(formatTime(total));
                    }
                    if (miniProgressLine != null) {
                        miniProgressLine.setProgress(progress);
                    }
                    if (mTimeC != null) {
                        mTimeC.setText(formatTime(current));
                    }
                    if (mTimeT != null) {
                        mTimeT.setText(formatTime(total));
                    }
                }
                syncLyricsToPosition(current);
            }
        }));
        progressTimer.setCycleCount(Timeline.INDEFINITE);
    }

    private void setupProgressSlider() {
        if (progressSlider != null) {
            setupSliderListeners(progressSlider);
        }
        if (expandedProgressSlider != null) {
            setupSliderListeners(expandedProgressSlider);
        }
    }

    private void setupSliderListeners(Slider slider) {
        slider.setOnMousePressed(e  -> isDraggingSlider = true);
        slider.setOnMouseReleased(e -> {
            long total = audioPlayer.getDuration();
            if (total > 0 && (isPlaying || isPaused)) {
                long newPosMs = (long) (slider.getValue() / 100.0 * total);
                audioPlayer.seek(newPosMs);
                if (currentTimeLabel != null) currentTimeLabel.setText(formatTime(newPosMs));
                if (expandedCurrentTimeLabel != null) expandedCurrentTimeLabel.setText(formatTime(newPosMs));
                if (mTimeC != null) mTimeC.setText(formatTime(newPosMs));
                syncLyricsToPosition(newPosMs);
                lanSync.notifySeek(newPosMs);
            }
            isDraggingSlider = false;
        });
    }

    //  LAN Sync UI Handlers 

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
                    String localWebUrl = lanSync.getLocalWebUrl();
                    Platform.runLater(() -> {
                        updateToggleButtonStyle(hostButton, true);
                        updateToggleButtonStyle(joinButton, false);
                        updateToggleButtonStyle(mobileSettingsHostButton, true);
                        updateToggleButtonStyle(mobileSettingsJoinButton, false);
                        updateSessionButtonLabels();
                                                updateStatus("Website live on " + ip + " | Starting public tunnel...");
                        updatePeerLabel(0);
                        updatePublicLinkFields(localWebUrl, "Local website ready", true);
                    });
                    
                    // Automatically launch the public tunnel so users don't have to use CMD
                    lanSync.startPublicTunnel(url -> {
                        Platform.runLater(() -> {
                            if (url.startsWith("http")) {
                                updateStatus("Public website link ready. Share it with friends.");
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

    //  Playlist & Search 

    @FXML
    public void handleSearch() {
        if (!AppPlatform.isMobile()) {
            showDesktopPage("search");
        }
        performSearch();
    }

    @FXML
    public void handleDesktopGoHome() {
        showDesktopPage("home");
    }

    @FXML
    public void handleOpenDesktopSongDetails() {
        if (AppPlatform.isMobile()) {
            return;
        }
        if (currentSongIndex < 0 || currentSongIndex >= playlist.size()) {
            updateStatus("Play a song first to open song details.");
            return;
        }
        showDesktopPage("details");
    }


    private void performSearch() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) { updateStatus("Please enter a search query."); return; }
        if (youtubeService == null) { updateStatus("YouTube Service not connected."); return; }
        setMobileLibraryMode(false);
        clearSearchSuggestions();

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
                    if (results.isEmpty()) {
                        updateStatus("No results found.");
                        if (searchCategoriesBox != null) {
                            searchCategoriesBox.setVisible(true);
                            searchCategoriesBox.setManaged(true);
                        }
                        searchResultsList.setVisible(false);
                        searchResultsList.setManaged(false);
                    } else {
                        searchResultsList.setItems(FXCollections.observableArrayList(results));
                        searchResultsList.setVisible(true);
                        searchResultsList.setManaged(true);
                        if (searchCategoriesBox != null) {
                            searchCategoriesBox.setVisible(false);
                            searchCategoriesBox.setManaged(false);
                        }
                        updateStatus("Found " + results.size() + " results.");
                    }
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
    public void handlePlaySelectedSearchResult() {
        SongData song = searchResultsList.getSelectionModel().getSelectedItem();
        if (song == null) {
            updateStatus("Select a search result first.");
            return;
        }
        addToQueueAndPlay(song);
    }

    @FXML
    public void handleCreateDesktopPlaylist() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Create Playlist");
        dialog.setHeaderText("Create a playlist");
        dialog.setContentText("Playlist name:");
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(this::createCustomPlaylist);
    }

    @FXML
    public void handleAddSearchResultToPlaylist() {
        SongData song = searchResultsList.getSelectionModel().getSelectedItem();
        if (song == null) {
            updateStatus("Select a song from search results first.");
            return;
        }

        String playlistName = getSelectedCustomPlaylistName();
        if (playlistName == null || playlistName.isBlank()) {
            updateStatus("Create or select a playlist first.");
            return;
        }

        ObservableList<SongData> songs = customPlaylists.get(playlistName);
        if (songs == null) {
            updateStatus("Playlist not found.");
            return;
        }

        SongData targetSong = song;
        boolean exists = songs.stream().anyMatch(existing -> isSameSong(existing, targetSong));
        if (!exists) {
            songs.add(cloneSong(targetSong));
            saveCustomPlaylists();
            refreshDesktopCustomPlaylistSongs(playlistName);
            updateStatus("Added to playlist \"" + playlistName + "\": " + targetSong.getTitle());
        } else {
            updateStatus("That song is already in \"" + playlistName + "\".");
        }
    }

    @FXML
    public void handleAddCurrentQueueSongToPlaylist() {
        SongData song = playlistView != null ? playlistView.getSelectionModel().getSelectedItem() : null;
        if (song == null && currentSongIndex >= 0 && currentSongIndex < playlist.size()) {
            song = playlist.get(currentSongIndex);
        }
        if (song == null) {
            updateStatus("Select a queue song first.");
            return;
        }

        String playlistName = desktopDetailsPlaylistPicker != null ? desktopDetailsPlaylistPicker.getValue() : getSelectedCustomPlaylistName();
        if (playlistName == null || playlistName.isBlank()) {
            updateStatus("Create or select a playlist first.");
            return;
        }

        ObservableList<SongData> songs = customPlaylists.get(playlistName);
        if (songs == null) {
            updateStatus("Playlist not found.");
            return;
        }

        SongData targetSong = song;
        boolean exists = songs.stream().anyMatch(existing -> isSameSong(existing, targetSong));
        if (!exists) {
            songs.add(cloneSong(targetSong));
            saveCustomPlaylists();
            refreshDesktopCustomPlaylistSongs(playlistName);
            updateStatus("Added to playlist \"" + playlistName + "\": " + targetSong.getTitle());
        } else {
            updateStatus("That song is already in \"" + playlistName + "\".");
        }
    }

    @FXML
    public void handleAddRelatedSongs() {
        SongData baseSong = null;
        if (currentSongIndex >= 0 && currentSongIndex < playlist.size()) {
            baseSong = playlist.get(currentSongIndex);
        } else if (!playlist.isEmpty()) {
            baseSong = playlist.get(playlist.size() - 1);
        }

        if (baseSong == null) {
            updateStatus("Play something first so Harmony can find related songs.");
            return;
        }

        autoFetchMoreSongs(baseSong, false);
        refreshMobileRecommendations(baseSong);
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

    //  Utility 

    public void updateStatus(String msg) {
        String safeMessage = msg == null ? "" : msg;
        lastStatusMessage = safeMessage;
        Runnable apply = () -> {
            if (statusLabel != null) statusLabel.setText(safeMessage);
            if (mobileSettingsStatusLabel != null) mobileSettingsStatusLabel.setText(safeMessage);
        };
        if (Platform.isFxApplicationThread()) {
            apply.run();
        } else {
            Platform.runLater(apply);
        }
    }

    private long beginPlaybackRequest() {
        activePlaybackRequestId = ++playbackRequestSequence;
        return activePlaybackRequestId;
    }

    private void invalidatePlaybackRequests() {
        activePlaybackRequestId = ++playbackRequestSequence;
    }

    private boolean isPlaybackRequestCurrent(long playbackRequestId) {
        return playbackRequestId == activePlaybackRequestId;
    }

    private int indexOfSongInPlaylist(SongData target) {
        if (target == null) {
            return -1;
        }
        for (int i = 0; i < playlist.size(); i++) {
            SongData existing = playlist.get(i);
            if (isSameSong(existing, target)) {
                return i;
            }
        }
        return -1;
    }

    private int indexOfSongInLikedSongs(SongData target) {
        if (target == null) {
            return -1;
        }
        for (int i = 0; i < likedSongs.size(); i++) {
            SongData existing = likedSongs.get(i);
            if (isSameSong(existing, target)) {
                return i;
            }
        }
        return -1;
    }

    private SongData findLikedSongByVideoId(String videoId) {
        String safeVideoId = safeText(videoId).trim();
        if (safeVideoId.isBlank()) {
            return null;
        }
        for (SongData song : likedSongs) {
            if (safeVideoId.equals(safeText(song.getVideoId()).trim())) {
                return song;
            }
        }
        return null;
    }

    private boolean isSongLiked(SongData song) {
        return indexOfSongInLikedSongs(song) >= 0;
    }

    @FXML
    public void handleToggleCurrentSongLiked() {
        SongData currentSong = currentSongIndex >= 0 && currentSongIndex < playlist.size() ? playlist.get(currentSongIndex) : null;
        if (currentSong == null) {
            updateStatus("Play a song first to save it to your wishlist.");
            return;
        }

        int existingIndex = indexOfSongInLikedSongs(currentSong);
        if (existingIndex >= 0) {
            likedSongs.remove(existingIndex);
            updateStatus("Removed from wishlist: " + currentSong.getTitle());
        } else {
            likedSongs.add(0, cloneSong(currentSong));
            updateStatus("Saved to wishlist: " + currentSong.getTitle());
        }
        lanSync.notifyWebStateChanged();
    }

    @FXML
    public void handleRemoveLikedSong() {
        SongData selected = likedSongsView != null ? likedSongsView.getSelectionModel().getSelectedItem() : null;
        if (selected == null) {
            updateStatus("Select a saved song to remove it.");
            return;
        }
        likedSongs.removeIf(song -> isSameSong(song, selected));
        updateStatus("Removed from wishlist: " + selected.getTitle());
        lanSync.notifyWebStateChanged();
    }

    private boolean isSameSong(SongData a, SongData b) {
        if (a == null || b == null) {
            return false;
        }
        String aVideo = safeText(a.getVideoId()).trim();
        String bVideo = safeText(b.getVideoId()).trim();
        if (!aVideo.isBlank() && !bVideo.isBlank()) {
            return aVideo.equals(bVideo);
        }
        String aPath = safeText(a.getPath()).trim();
        String bPath = safeText(b.getPath()).trim();
        return !aPath.isBlank() && !bPath.isBlank() && aPath.equalsIgnoreCase(bPath);
    }

    private SongData cloneSong(SongData original) {
        SongData copy = new SongData();
        copy.setVideoId(original.getVideoId());
        copy.setTitle(original.getTitle());
        copy.setChannel(original.getChannel());
        copy.setThumbnailUrl(original.getThumbnailUrl());
        copy.setPath(original.getPath());
        copy.setType(original.getType());
        copy.setLyricsSearchHint(original.getLyricsSearchHint());
        copy.setDuration(original.getDuration());
        copy.setGuestPlaybackBlocked(original.isGuestPlaybackBlocked());
        copy.setPlaybackIssue(original.getPlaybackIssue());
        return copy;
    }

    private JSONObject toWebSongJson(SongData song, boolean current, int index) {
        JSONObject item = new JSONObject();
        item.put("index", index);
        item.put("videoId", safeText(song.getVideoId()));
        item.put("title", safeText(song.getTitle()));
        item.put("channel", safeText(song.getChannel()));
        item.put("thumbnailUrl", safeText(song.getThumbnailUrl()));
        item.put("type", safeText(song.getType()));
        item.put("current", current);
        item.put("liked", isSongLiked(song));
        return item;
    }

    private JSONObject okJson(String message) {
        JSONObject json = new JSONObject();
        json.put("ok", true);
        json.put("message", message == null ? "" : message);
        return json;
    }

    private JSONObject errorJson(String message) {
        JSONObject json = new JSONObject();
        json.put("ok", false);
        json.put("message", message == null ? "Unknown error" : message);
        return json;
    }

    private JSONObject buildWebStateSnapshot() {
        AtomicReference<JSONObject> ref = new AtomicReference<>(new JSONObject());
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                JSONObject state = new JSONObject();
                state.put("ok", true);
                state.put("status", lastStatusMessage);
                state.put("isPlaying", isPlaying);
                state.put("isPaused", isPaused);
                state.put("shuffleEnabled", shuffleEnabled);
                state.put("repeatEnabled", repeatEnabled);
                state.put("autoRadioEnabled", autoRadioEnabled);
                state.put("currentSongIndex", currentSongIndex);
                state.put("currentTimeMs", audioPlayer.getCurrentPosition());
                state.put("durationMs", audioPlayer.getDuration());

                JSONArray queue = new JSONArray();
                for (int i = 0; i < playlist.size(); i++) {
                    SongData song = playlist.get(i);
                    queue.put(toWebSongJson(song, i == currentSongIndex, i));
                }
                state.put("queue", queue);

                JSONArray liked = new JSONArray();
                for (int i = 0; i < likedSongs.size(); i++) {
                    liked.put(toWebSongJson(likedSongs.get(i), false, i));
                }
                state.put("likedSongs", liked);

                JSONArray recommendations = new JSONArray();
                for (int i = 0; i < recommendedSongs.size(); i++) {
                    recommendations.put(toWebSongJson(recommendedSongs.get(i), false, i));
                }
                state.put("recommendations", recommendations);

                SongData current = (currentSongIndex >= 0 && currentSongIndex < playlist.size()) ? playlist.get(currentSongIndex) : null;
                if (current != null) {
                    JSONObject currentJson = toWebSongJson(current, true, currentSongIndex);
                    currentJson.put("liked", isSongLiked(current));
                    state.put("currentSong", currentJson);
                } else {
                    state.put("currentSong", JSONObject.NULL);
                }
                ref.set(state);
            } finally {
                latch.countDown();
            }
        });
        awaitLatch(latch);
        return ref.get();
    }

    private JSONObject searchSongsForWeb(String query) throws Exception {
        if (youtubeService == null) {
            return errorJson("YouTube service not connected.");
        }
        String normalizedQuery = RelatedTrackHelper.normalizeWhitespace(query);
        if (normalizedQuery.isBlank()) {
            return errorJson("Search query is empty.");
        }

        List<YouTubeVideo> videos = youtubeService.searchVideos(normalizedQuery, 15);
        JSONArray results = new JSONArray();
        for (YouTubeVideo v : videos) {
            SongData song = new SongData();
            song.setVideoId(v.getId());
            song.setTitle(v.getTitle());
            song.setChannel(v.getChannel());
            song.setThumbnailUrl(v.getThumbnailUrl());
            song.setType("youtube");
            song.setLyricsSearchHint(normalizedQuery);
            webSearchIndex.put(v.getId(), song);

            JSONObject item = new JSONObject();
            item.put("videoId", safeText(song.getVideoId()));
            item.put("title", safeText(song.getTitle()));
            item.put("channel", safeText(song.getChannel()));
            item.put("thumbnailUrl", safeText(song.getThumbnailUrl()));
            results.put(item);
        }

        Platform.runLater(() -> rememberSearchQuery(normalizedQuery));

        JSONObject payload = okJson("Found " + results.length() + " results.");
        payload.put("results", results);
        return payload;
    }

    private JSONObject addSongToQueueFromWeb(String videoId, boolean playNow) {
        SongData cachedSong = webSearchIndex.get(videoId);
        if (cachedSong == null) {
            return errorJson("Song was not found in the latest search results.");
        }

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<JSONObject> ref = new AtomicReference<>(errorJson("Queue update failed."));
        Platform.runLater(() -> {
            try {
                SongData song = cloneSong(cachedSong);
                if (playNow) {
                    addToQueueAndPlay(song);
                    ref.set(okJson("Playing " + safeText(song.getTitle())));
                } else {
                    int existingIndex = indexOfSongInPlaylist(song);
                    if (existingIndex == -1) {
                        playlist.add(song);
                        updateStatus("Added to queue: " + safeText(song.getTitle()));
                    } else {
                        updateStatus("Already in queue: " + safeText(song.getTitle()));
                    }
                    lanSync.notifyWebStateChanged();
                    ref.set(okJson("Queue updated."));
                }
            } finally {
                latch.countDown();
            }
        });
        awaitLatch(latch);
        return ref.get().put("state", buildWebStateSnapshot());
    }

    private JSONObject playQueueIndexFromWeb(int index) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<JSONObject> ref = new AtomicReference<>(errorJson("Could not play queue item."));
        Platform.runLater(() -> {
            try {
                if (index < 0 || index >= playlist.size()) {
                    ref.set(errorJson("Queue index out of range."));
                    return;
                }
                currentSongIndex = index;
                isPaused = false;
                isPlaying = false;
                audioPlayer.stop();
                playMusic();
                lanSync.notifyWebStateChanged();
                ref.set(okJson("Playing queue item."));
            } finally {
                latch.countDown();
            }
        });
        awaitLatch(latch);
        return ref.get().put("state", buildWebStateSnapshot());
    }

    private JSONObject removeQueueIndexFromWeb(int index) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<JSONObject> ref = new AtomicReference<>(errorJson("Could not remove queue item."));
        Platform.runLater(() -> {
            try {
                if (index < 0 || index >= playlist.size()) {
                    ref.set(errorJson("Queue index out of range."));
                    return;
                }
                if (index == currentSongIndex && (isPlaying || isPaused)) {
                    stopMusic();
                }
                playlist.remove(index);
                if (playlist.isEmpty()) {
                    currentSongIndex = -1;
                    updateNowPlaying(null);
                } else if (currentSongIndex >= playlist.size()) {
                    currentSongIndex = playlist.size() - 1;
                } else if (index < currentSongIndex) {
                    currentSongIndex--;
                }
                updateStatus("Queue item removed.");
                lanSync.notifyWebStateChanged();
                ref.set(okJson("Queue item removed."));
            } finally {
                latch.countDown();
            }
        });
        awaitLatch(latch);
        return ref.get().put("state", buildWebStateSnapshot());
    }

    private JSONObject clearQueueFromWeb() {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<JSONObject> ref = new AtomicReference<>(errorJson("Could not clear queue."));
        Platform.runLater(() -> {
            try {
                stopMusic();
                playlist.clear();
                currentSongIndex = -1;
                updateNowPlaying(null);
                updateStatus("Playlist cleared.");
                lanSync.notifyWebStateChanged();
                ref.set(okJson("Queue cleared."));
            } finally {
                latch.countDown();
            }
        });
        awaitLatch(latch);
        return ref.get().put("state", buildWebStateSnapshot());
    }

    private JSONObject setToggleFromWeb(String toggle, boolean enabled) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<JSONObject> ref = new AtomicReference<>(errorJson("Could not update toggle."));
        Platform.runLater(() -> {
            try {
                switch (safeText(toggle).toLowerCase(Locale.ROOT)) {
                    case "shuffle" -> {
                        shuffleEnabled = enabled;
                        updateToggleButtonStyle(shuffleButton, shuffleEnabled);
                        updateStatus(shuffleEnabled ? "Shuffle ON" : "Shuffle OFF");
                    }
                    case "repeat" -> {
                        repeatEnabled = enabled;
                        updateToggleButtonStyle(repeatButton, repeatEnabled);
                        updateStatus(repeatEnabled ? "Repeat ON" : "Repeat OFF");
                    }
                    case "autoradio" -> {
                        autoRadioEnabled = enabled;
                        updateToggleButtonStyle(autoRadioButton, autoRadioEnabled);
                        updateStatus(autoRadioEnabled ? "Auto Radio ON - related songs will be added automatically" : "Auto Radio OFF");
                        if (autoRadioEnabled && !playlist.isEmpty()) {
                            autoFetchMoreSongs(playlist.get(playlist.size() - 1), false);
                        }
                    }
                    default -> {
                        ref.set(errorJson("Unknown toggle: " + toggle));
                        return;
                    }
                }
                lanSync.notifyWebStateChanged();
                ref.set(okJson("Toggle updated."));
            } finally {
                latch.countDown();
            }
        });
        awaitLatch(latch);
        return ref.get().put("state", buildWebStateSnapshot());
    }

    private JSONObject addRelatedSongsFromWeb() {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<JSONObject> ref = new AtomicReference<>(errorJson("No base song available for related songs."));
        Platform.runLater(() -> {
            try {
                SongData baseSong = null;
                if (currentSongIndex >= 0 && currentSongIndex < playlist.size()) {
                    baseSong = playlist.get(currentSongIndex);
                } else if (!playlist.isEmpty()) {
                    baseSong = playlist.get(playlist.size() - 1);
                }

                if (baseSong == null) {
                    ref.set(errorJson("Queue is empty."));
                    return;
                }

                autoFetchMoreSongs(baseSong, false);
                ref.set(okJson("Finding related songs..."));
            } finally {
                latch.countDown();
            }
        });
        awaitLatch(latch);
        return ref.get().put("state", buildWebStateSnapshot());
    }

    private JSONObject toggleLikedFromWeb(String videoId) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<JSONObject> ref = new AtomicReference<>(errorJson("Could not update wishlist."));
        Platform.runLater(() -> {
            try {
                SongData target = null;
                if (!safeText(videoId).isBlank()) {
                    target = webSearchIndex.get(videoId);
                    if (target == null) {
                        target = findLikedSongByVideoId(videoId);
                    }
                    if (target == null) {
                        for (SongData song : playlist) {
                            if (safeText(song.getVideoId()).equals(safeText(videoId))) {
                                target = song;
                                break;
                            }
                        }
                    }
                }
                if (target == null && currentSongIndex >= 0 && currentSongIndex < playlist.size()) {
                    target = playlist.get(currentSongIndex);
                }
                if (target == null) {
                    ref.set(errorJson("No song available to save."));
                    return;
                }
                int existingIndex = indexOfSongInLikedSongs(target);
                if (existingIndex >= 0) {
                    likedSongs.remove(existingIndex);
                    updateStatus("Removed from wishlist: " + target.getTitle());
                } else {
                    likedSongs.add(0, cloneSong(target));
                    updateStatus("Saved to wishlist: " + target.getTitle());
                }
                lanSync.notifyWebStateChanged();
                ref.set(okJson("Wishlist updated."));
            } finally {
                latch.countDown();
            }
        });
        awaitLatch(latch);
        return ref.get().put("state", buildWebStateSnapshot());
    }

    private JSONObject playLikedFromWeb(String videoId) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<JSONObject> ref = new AtomicReference<>(errorJson("Could not play saved song."));
        Platform.runLater(() -> {
            try {
                SongData likedSong = findLikedSongByVideoId(videoId);
                if (likedSong == null) {
                    ref.set(errorJson("Saved song not found."));
                    return;
                }
                addToQueueAndPlay(cloneSong(likedSong));
                ref.set(okJson("Playing saved song."));
            } finally {
                latch.countDown();
            }
        });
        awaitLatch(latch);
        return ref.get().put("state", buildWebStateSnapshot());
    }

    private JSONObject removeLikedFromWeb(String videoId) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<JSONObject> ref = new AtomicReference<>(errorJson("Could not remove saved song."));
        Platform.runLater(() -> {
            try {
                SongData likedSong = findLikedSongByVideoId(videoId);
                if (likedSong == null) {
                    ref.set(errorJson("Saved song not found."));
                    return;
                }
                likedSongs.removeIf(song -> isSameSong(song, likedSong));
                updateStatus("Removed from wishlist: " + likedSong.getTitle());
                lanSync.notifyWebStateChanged();
                ref.set(okJson("Removed saved song."));
            } finally {
                latch.countDown();
            }
        });
        awaitLatch(latch);
        return ref.get().put("state", buildWebStateSnapshot());
    }

    private void awaitLatch(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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

    private void setMobileLibraryMode(boolean libraryMode) {
        mobileLibraryMode = libraryMode;
        if (playlistView != null) {
            playlistView.setVisible(libraryMode);
            playlistView.setManaged(libraryMode);
        }
        if (libraryMode) {
            if (searchCategoriesBox != null) {
                searchCategoriesBox.setVisible(false);
                searchCategoriesBox.setManaged(false);
            }
            if (searchResultsList != null) {
                searchResultsList.setVisible(false);
                searchResultsList.setManaged(false);
            }
            if (searchHistorySection != null) {
                searchHistorySection.setVisible(false);
                searchHistorySection.setManaged(false);
            }
            if (searchHistoryContainer != null) {
                searchHistoryContainer.setVisible(false);
                searchHistoryContainer.setManaged(false);
            }
            refreshMobileQueueSummary();
        } else {
            refreshSearchHistoryUi();
            if (searchResultsList != null) {
                boolean hasResults = !searchResultsList.getItems().isEmpty();
                searchResultsList.setVisible(hasResults);
                searchResultsList.setManaged(hasResults);
                if (searchCategoriesBox != null) {
                    searchCategoriesBox.setVisible(!hasResults);
                    searchCategoriesBox.setManaged(!hasResults);
                }
            }
        }
    }

    private void triggerSearchSuggestions() {
        if (searchField == null) {
            return;
        }

        String query = searchField.getText() == null ? "" : searchField.getText().trim();
        if (query.length() < 2 || youtubeService == null) {
            clearSearchSuggestions();
            return;
        }

        long requestId = ++searchSuggestionRequestSequence;
        new Thread(() -> {
            try {
                List<YouTubeVideo> videos = youtubeService.searchVideos(query, 6);
                List<String> suggestions = new ArrayList<>();
                for (YouTubeVideo video : videos) {
                    String title = video.getTitle() == null ? "" : video.getTitle().trim();
                    if (title.isBlank()) {
                        continue;
                    }
                    boolean alreadyPresent = suggestions.stream().anyMatch(existing -> existing.equalsIgnoreCase(title));
                    if (!alreadyPresent) {
                        suggestions.add(title);
                    }
                }

                Platform.runLater(() -> {
                    if (requestId != searchSuggestionRequestSequence) {
                        return;
                    }
                    searchSuggestions.setAll(suggestions);
                    updateSearchSuggestionVisibility();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (requestId == searchSuggestionRequestSequence) {
                        clearSearchSuggestions();
                    }
                });
            }
        }, "search-suggestions").start();
    }

    private void clearSearchSuggestions() {
        searchSuggestions.clear();
        updateSearchSuggestionVisibility();
    }

    private void updateSearchSuggestionVisibility() {
        if (searchSuggestionsList == null) {
            return;
        }
        boolean visible = !searchSuggestions.isEmpty();
        searchSuggestionsList.setVisible(visible);
        searchSuggestionsList.setManaged(visible);
    }

    @FXML
    public void handleOpenNowPlaying() {
        if (!AppPlatform.isMobile()) {
            return;
        }
        setMobileSearchVisible(false);
        setMobileNowPlayingVisible(true);
    }

    public void handleMobileDockClick(MouseEvent event) {
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

    private void loadCustomPlaylists() {
        customPlaylists.clear();
        Map<String, List<SongData>> loaded = shelfStore.loadCustomPlaylists();
        for (Map.Entry<String, List<SongData>> entry : loaded.entrySet()) {
            customPlaylists.put(entry.getKey(), FXCollections.observableArrayList(entry.getValue()));
        }
        customPlaylistNames.setAll(customPlaylists.keySet());
        if (desktopCustomPlaylistsView != null && !customPlaylistNames.isEmpty()) {
            desktopCustomPlaylistsView.getSelectionModel().select(0);
        }
        if (desktopPlaylistPicker != null && !customPlaylistNames.isEmpty() && desktopPlaylistPicker.getValue() == null) {
            desktopPlaylistPicker.setValue(customPlaylistNames.get(0));
        }
        if (desktopDetailsPlaylistPicker != null && !customPlaylistNames.isEmpty() && desktopDetailsPlaylistPicker.getValue() == null) {
            desktopDetailsPlaylistPicker.setValue(customPlaylistNames.get(0));
        }
    }

    private void saveCustomPlaylists() {
        Map<String, List<SongData>> snapshot = new LinkedHashMap<>();
        for (Map.Entry<String, ObservableList<SongData>> entry : customPlaylists.entrySet()) {
            snapshot.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        shelfStore.saveCustomPlaylists(snapshot);
        customPlaylistNames.setAll(customPlaylists.keySet());
        refreshDesktopPlaylistSummary();
    }

    private void createCustomPlaylist(String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isBlank()) {
            updateStatus("Playlist name can't be empty.");
            return;
        }
        if (customPlaylists.containsKey(name)) {
            updateStatus("Playlist already exists: " + name);
            return;
        }
        customPlaylists.put(name, FXCollections.observableArrayList());
        saveCustomPlaylists();
        if (desktopCustomPlaylistsView != null) {
            desktopCustomPlaylistsView.getSelectionModel().select(name);
        }
        if (desktopPlaylistPicker != null) {
            desktopPlaylistPicker.setValue(name);
        }
        if (desktopDetailsPlaylistPicker != null) {
            desktopDetailsPlaylistPicker.setValue(name);
        }
        refreshDesktopCustomPlaylistSongs(name);
        updateStatus("Created playlist: " + name);
    }

    private String getSelectedCustomPlaylistName() {
        if (desktopPlaylistPicker != null && desktopPlaylistPicker.getValue() != null && !desktopPlaylistPicker.getValue().isBlank()) {
            return desktopPlaylistPicker.getValue();
        }
        if (desktopCustomPlaylistsView != null) {
            return desktopCustomPlaylistsView.getSelectionModel().getSelectedItem();
        }
        return customPlaylistNames.isEmpty() ? null : customPlaylistNames.get(0);
    }

    private void refreshDesktopCustomPlaylistSongs(String playlistName) {
        selectedCustomPlaylistSongs.clear();
        if (playlistName == null || playlistName.isBlank()) {
            refreshDesktopPlaylistSummary();
            return;
        }
        ObservableList<SongData> songs = customPlaylists.get(playlistName);
        if (songs != null) {
            selectedCustomPlaylistSongs.setAll(songs);
        }
        refreshDesktopPlaylistSummary();
    }

    private void refreshMobileQueueSummary() {
        if (mobileQueueSummaryLabel == null) {
            return;
        }

        if (playlist.isEmpty()) {
            mobileQueueSummaryLabel.setText("Your queue is empty. Search for a song to get started.");
            return;
        }

        String summary = playlist.size() + " track" + (playlist.size() == 1 ? "" : "s") + " queued";
        if (currentSongIndex >= 0 && currentSongIndex < playlist.size()) {
            SongData current = playlist.get(currentSongIndex);
            summary += " • Now playing " + current.getTitle();
        }
        mobileQueueSummaryLabel.setText(summary);
    }

    private void refreshDesktopQueueSummary() {
        if (desktopQueueSummaryLabel == null) {
            return;
        }
        if (playlist.isEmpty()) {
            desktopQueueSummaryLabel.setText("Your queue is empty. Search for tracks or add local audio.");
            return;
        }
        String summary = playlist.size() + " track" + (playlist.size() == 1 ? "" : "s") + " ready";
        if (currentSongIndex >= 0 && currentSongIndex < playlist.size()) {
            summary += " • Playing " + playlist.get(currentSongIndex).getTitle();
        }
        desktopQueueSummaryLabel.setText(summary);
    }

    private void refreshRecentPlayedSummary() {
        if (desktopRecentSummaryLabel == null) {
            return;
        }
        if (recentPlayedSongs.isEmpty()) {
            desktopRecentSummaryLabel.setText("Recently played songs will show up here once you start listening.");
        } else {
            desktopRecentSummaryLabel.setText(recentPlayedSongs.size() + " recent song" + (recentPlayedSongs.size() == 1 ? "" : "s") + " ready to replay.");
        }
    }

    private void refreshDesktopPlaylistSummary() {
        if (desktopPlaylistSummaryLabel == null) {
            return;
        }
        String name = getSelectedCustomPlaylistName();
        if (name == null || name.isBlank()) {
            desktopPlaylistSummaryLabel.setText("Create a playlist, then add songs to it from search.");
            return;
        }
        ObservableList<SongData> songs = customPlaylists.get(name);
        int count = songs == null ? 0 : songs.size();
        desktopPlaylistSummaryLabel.setText(name + " • " + count + " track" + (count == 1 ? "" : "s"));
    }

    private void refreshLikedSongsSummary() {
        if (likedSongsSummaryLabel == null) {
            return;
        }
        if (likedSongs.isEmpty()) {
            likedSongsSummaryLabel.setText("Save songs here to build a quick-access shelf across the app.");
        } else {
            likedSongsSummaryLabel.setText(likedSongs.size() + " saved song" + (likedSongs.size() == 1 ? "" : "s") + " in your wishlist.");
        }
    }

    private void refreshMobileRecommendations(SongData baseSong) {
        if (mobileRecommendationsList == null && desktopRecommendationSummaryLabel == null) {
            return;
        }

        long requestId = ++recommendationRequestSequence;
        SongData pivot = baseSong;
        if (pivot == null && currentSongIndex >= 0 && currentSongIndex < playlist.size()) {
            pivot = playlist.get(currentSongIndex);
        }

        if (pivot == null) {
            recommendedSongs.setAll(List.of());
            if (mobileRecommendationSummaryLabel != null) {
                mobileRecommendationSummaryLabel.setText("Play a song to unlock related recommendations.");
            }
            if (desktopRecommendationSummaryLabel != null) {
                desktopRecommendationSummaryLabel.setText("Recommendations appear here when a track is playing.");
            }
            return;
        }

        SongData finalPivot = pivot;
        if (mobileRecommendationSummaryLabel != null) {
            mobileRecommendationSummaryLabel.setText("Finding songs related to " + finalPivot.getTitle() + "...");
        }
        if (desktopRecommendationSummaryLabel != null) {
            desktopRecommendationSummaryLabel.setText("Finding songs related to " + finalPivot.getTitle() + "...");
        }
        new Thread(() -> {
            List<SongData> recommendations = new ArrayList<>();

            if (youtubeService != null) {
                try {
                    for (String query : buildAutoRadioQueries(finalPivot)) {
                        if (recommendations.size() >= 8) {
                            break;
                        }
                        List<YouTubeVideo> videos = youtubeService.searchVideos(query, 8);
                        for (YouTubeVideo candidate : videos) {
                            if (!isRelatedAutoRadioCandidate(finalPivot, recommendations, candidate)) {
                                continue;
                            }
                            SongData song = new SongData();
                            song.setVideoId(candidate.getId());
                            song.setTitle(candidate.getTitle());
                            song.setChannel(candidate.getChannel());
                            song.setThumbnailUrl(candidate.getThumbnailUrl());
                            song.setType("youtube");
                            song.setLyricsSearchHint(finalPivot.getTitle());
                            recommendations.add(song);
                            if (recommendations.size() >= 8) {
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Mobile recommendation fetch failed: " + e.getMessage());
                }
            }

            Platform.runLater(() -> {
                if (requestId != recommendationRequestSequence) {
                    return;
                }
                recommendedSongs.setAll(recommendations);
                if (recommendations.isEmpty()) {
                    if (mobileRecommendationSummaryLabel != null) {
                        mobileRecommendationSummaryLabel.setText("No related songs found yet. Try a different search or play another track.");
                    }
                    if (desktopRecommendationSummaryLabel != null) {
                        desktopRecommendationSummaryLabel.setText("No fresh recommendations yet. Try another song or another search.");
                    }
                } else {
                    if (mobileRecommendationSummaryLabel != null) {
                        mobileRecommendationSummaryLabel.setText("Tap any result to play it instantly or fold it into the queue.");
                    }
                    if (desktopRecommendationSummaryLabel != null) {
                        desktopRecommendationSummaryLabel.setText("Tap a recommendation to play it or let Auto Radio continue from here.");
                    }
                }
            });
        }, "mobile-recommendations").start();
    }

    public void setExternalUrlOpener(Consumer<String> externalUrlOpener) {
        this.externalUrlOpener = externalUrlOpener;
    }

    private boolean shouldUseOfficialYouTubePlayback() {
        // Only force official YouTube (external browser/app) if we are ACTUALLY on Android
        // and using Guest Mode. On Desktop (even in mobile UI mode), we prefer yt-dlp.
        return AppPlatform.isAndroid()
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
        updateStatus("BROWSER TRIGGERED...");
        externalUrlOpener.accept(youtubeUrl);
        isPlaying = false;
        isPaused = false;
        updatePlayPauseLabel();
        
        new Thread(() -> {
            try { Thread.sleep(2000); } catch (Exception ignored) {}
            Platform.runLater(() -> updateStatus("Opening YouTube: " + song.getTitle()));
        }).start();
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
            if (songTitleLabel != null) songTitleLabel.setText("No song selected");
            if (artistLabel != null) artistLabel.setText("Select a song from the playlist");
            if (expandedSongTitleLabel != null) expandedSongTitleLabel.setText("No song selected");
            if (expandedArtistLabel != null) expandedArtistLabel.setText("Select a song from the playlist");
            setAlbumArt(null);
            playlistView.getSelectionModel().clearSelection();
            if (desktopMiniPlayerBar != null) {
                desktopMiniPlayerBar.setVisible(false);
                desktopMiniPlayerBar.setManaged(false);
            }
            lanSync.clearWebTrackInfo();
            clearLyrics("Pick a song to light up the lyrics panel.", "Waiting for track");
            refreshMobileRecommendations(null);
        } else {
            if (songTitleLabel != null) songTitleLabel.setText(song.getTitle());
            if (artistLabel != null) artistLabel.setText(song.getChannel() != null ? song.getChannel() : "Unknown Artist");
            if (expandedSongTitleLabel != null) expandedSongTitleLabel.setText(song.getTitle());
            if (expandedArtistLabel != null) expandedArtistLabel.setText(song.getChannel() != null ? song.getChannel() : "Unknown Artist");
            playlistView.getSelectionModel().select(song);
            if (desktopMiniPlayerTitleLabel != null) {
                desktopMiniPlayerTitleLabel.setText(song.getTitle());
            }
            if (desktopMiniPlayerArtistLabel != null) {
                desktopMiniPlayerArtistLabel.setText(song.getChannel() != null ? song.getChannel() : "Unknown Artist");
            }
            if (desktopMiniPlayerBar != null) {
                desktopMiniPlayerBar.setVisible(true);
                desktopMiniPlayerBar.setManaged(true);
            }
            setAlbumArt(song.getThumbnailUrl());
            lanSync.updateWebTrackInfo(song.getTitle(), artistLabel.getText());
            renderLyricsPlaceholder("Fetching lyrics for " + song.getTitle(), "Searching lyrics");
            loadLyricsForSong(song);
            refreshMobileRecommendations(song);
        }
        refreshMobileQueueSummary();
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
        if (lyricsContainer != null) lyricsContainer.getChildren().clear();

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
            if (lyricsContainer != null) {
                lyricsContainer.getChildren().add(lyricLine);
            }
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

        if (lyricsHeadlineLabel != null) lyricsHeadlineLabel.setText(headline);
        if (lyricsSourceLabel != null) lyricsSourceLabel.setText(sourceText);
        
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

    //  Mobile Bottom Nav 

    @FXML
    public void handleNavHome() {
        setMobileSearchVisible(false);
        setMobileLibraryMode(false);
        hideSettingsOverlay();
    }

    @FXML
    public void handleNavSearch() {
        setMobileSearchVisible(true);
        setMobileLibraryMode(false);
        hideSettingsOverlay();
    }

    @FXML
    public void handleNavLibrary() {
        setMobileSearchVisible(true);
        setMobileLibraryMode(true);
        hideSettingsOverlay();
        updateStatus(playlist.isEmpty() ? "Library is empty. Search and add songs to build your queue." : "Queue ready. Tap a track to play it.");
    }

    @FXML
    public void handleNavPremium() {
        // Future: premium upsell overlay
    }

    //  Theme Picker 

    private void applyTheme(String cssClass) {
        if (appRoot == null) return;
        appRoot.getStyleClass().removeIf(c -> c.startsWith("theme-"));
        appRoot.getStyleClass().add(cssClass);
    }

    @FXML public void handleThemeRed()    { applyTheme("theme-red"); }
    @FXML public void handleThemeYellow() { applyTheme("theme-yellow"); }
    @FXML public void handleThemeGreen()  { applyTheme("theme-green"); }
    @FXML public void handleThemeBlue()   { applyTheme("theme-blue"); }
    @FXML public void handleThemeWhite()  { applyTheme("theme-white"); }
}

