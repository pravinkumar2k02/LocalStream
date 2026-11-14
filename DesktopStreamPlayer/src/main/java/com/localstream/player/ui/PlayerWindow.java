package com.localstream.player.ui;

import com.localstream.player.model.RemoteFile;
import com.sun.jna.NativeLibrary;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.Stage;
import javafx.util.Duration;
import uk.co.caprica.vlcj.factory.MediaPlayerFactory;
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery;
import uk.co.caprica.vlcj.javafx.videosurface.ImageViewVideoSurface;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;
import uk.co.caprica.vlcj.player.base.TrackDescription;
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Dedicated Media Player Window with native MKV, AVI, MP4, and WebM playback,
 * audio track selection, subtitle track selection, 10-second arrow-key skipping,
 * and built-in timeline scrubbing with HTTP Range support.
 */
public class PlayerWindow {

    private final Stage stage;
    private final RemoteFile file;
    private final String streamUrl;

    // VLCJ Components (Primary Native Engine for MKV, AVI, MP4, WebM)
    private MediaPlayerFactory vlcFactory;
    private EmbeddedMediaPlayer vlcPlayer;
    private ImageView vlcImageView;
    private boolean isVlcActive = false;

    // JavaFX Fallback Components (Used if LibVLC is unavailable)
    private MediaPlayer fxPlayer;
    private MediaView fxMediaView;

    // UI Controls
    private Button playPauseBtn;
    private Slider timeSlider;
    private Label timeLabel;
    private Slider volumeSlider;
    private Button muteBtn;
    private Label statusLabel;
    private ComboBox<String> rateCombo;
    private ComboBox<TrackItem> audioTrackCombo;
    private ComboBox<TrackItem> subtitleTrackCombo;
    private Label osdBadge;
    private StackPane videoContainer;
    private VBox controlsContainer;

    private boolean isUserSeeking = false;
    private long totalDurationMs = 0;
    private Timer progressTimer;

    public static class TrackItem {
        public final int id;
        public final String description;

        public TrackItem(int id, String description) {
            this.id = id;
            this.description = description;
        }

        @Override
        public String toString() {
            return description;
        }
    }

    public PlayerWindow(RemoteFile file, String streamUrl) {
        this.file = file;
        this.streamUrl = streamUrl;
        this.stage = new Stage();
    }

    public void show() {
        stage.setTitle("Playing: " + file.getName() + " [" + file.getFormattedSize() + "]");
        stage.setWidth(1000);
        stage.setHeight(680);
        stage.setMinWidth(720);
        stage.setMinHeight(500);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #121212;");

        // Top info header
        HBox topBar = createTopBar();
        root.setTop(topBar);

        // Center: Video Viewport Stack
        videoContainer = new StackPane();
        videoContainer.setStyle("-fx-background-color: #000000;");

        // Video Surface
        vlcImageView = new ImageView();
        vlcImageView.setPreserveRatio(true);
        vlcImageView.fitWidthProperty().bind(videoContainer.widthProperty());
        vlcImageView.fitHeightProperty().bind(videoContainer.heightProperty());

        // Status message
        statusLabel = new Label("Initializing media engine...");
        statusLabel.setStyle("-fx-text-fill: #E0E0E0; -fx-font-size: 15px; -fx-background-color: rgba(0,0,0,0.7); -fx-padding: 8 16; -fx-background-radius: 6;");

        // On-Screen Display (OSD) badge for arrow-key 10s skip confirmation
        osdBadge = new Label("");
        osdBadge.setStyle("-fx-text-fill: #FFFFFF; -fx-font-size: 22px; -fx-font-weight: bold; -fx-background-color: rgba(0,0,0,0.75); -fx-padding: 10 24; -fx-background-radius: 10; -fx-border-color: #2979FF; -fx-border-radius: 10;");
        osdBadge.setVisible(false);

        videoContainer.getChildren().addAll(vlcImageView, statusLabel, osdBadge);
        root.setCenter(videoContainer);

        // Bottom Controls Bar
        controlsContainer = createControlsBar();
        root.setBottom(controlsContainer);

        Scene scene = new Scene(root);
        // Intercept arrow keys globally via EventFilter so 10s skip always works regardless of focus
        applyGlobalKeyboardShortcuts(scene);

        stage.setScene(scene);
        stage.setOnCloseRequest(e -> cleanup());
        stage.show();

        // Start media engine
        initializePlayback();
    }

    private HBox createTopBar() {
        HBox bar = new HBox(12);
        bar.setPadding(new Insets(10, 16, 10, 16));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setStyle("-fx-background-color: #1E1E1E; -fx-border-color: #2D2D2D; -fx-border-width: 0 0 1 0;");

        Label nameLbl = new Label(file.getName());
        nameLbl.setStyle("-fx-text-fill: #FFFFFF; -fx-font-weight: bold; -fx-font-size: 14px;");

        Label badge = new Label(file.getMimeType());
        badge.setStyle("-fx-background-color: #2979FF; -fx-text-fill: white; -fx-padding: 3 8; -fx-background-radius: 4; -fx-font-size: 11px;");

        Label sizeLbl = new Label(file.getFormattedSize());
        sizeLbl.setStyle("-fx-text-fill: #AAAAAA; -fx-font-size: 12px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button copyUrlBtn = new Button("Copy Stream URL");
        copyUrlBtn.setStyle("-fx-background-color: #333333; -fx-text-fill: #DDDDDD; -fx-background-radius: 4; -fx-cursor: hand;");
        copyUrlBtn.setOnAction(e -> {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(streamUrl);
            clipboard.setContent(content);
            copyUrlBtn.setText("Copied!");
            new Thread(() -> {
                try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
                Platform.runLater(() -> copyUrlBtn.setText("Copy Stream URL"));
            }).start();
        });

        Button openBrowserBtn = new Button("Open in Browser");
        openBrowserBtn.setStyle("-fx-background-color: #00897B; -fx-text-fill: white; -fx-background-radius: 4; -fx-cursor: hand;");
        openBrowserBtn.setOnAction(e -> launchInBrowser());

        bar.getChildren().addAll(nameLbl, badge, sizeLbl, spacer, copyUrlBtn, openBrowserBtn);
        return bar;
    }

    private VBox createControlsBar() {
        VBox container = new VBox(8);
        container.setPadding(new Insets(10, 16, 12, 16));
        container.setStyle("-fx-background-color: #1A1A1A; -fx-border-color: #2D2D2D; -fx-border-width: 1 0 0 0;");

        // Row 1: Time Slider
        HBox sliderRow = new HBox(10);
        sliderRow.setAlignment(Pos.CENTER);

        timeSlider = new Slider(0, 100, 0);
        HBox.setHgrow(timeSlider, Priority.ALWAYS);
        timeSlider.setStyle("-fx-control-inner-background: #333333; -fx-accent: #2979FF;");

        timeLabel = new Label("00:00:00 / 00:00:00");
        timeLabel.setStyle("-fx-text-fill: #CCCCCC; -fx-font-family: monospace; -fx-font-size: 12px;");
        timeLabel.setMinWidth(140);
        timeLabel.setAlignment(Pos.CENTER_RIGHT);

        sliderRow.getChildren().addAll(timeSlider, timeLabel);

        // Seek Slider Listeners
        timeSlider.setOnMousePressed(e -> isUserSeeking = true);
        timeSlider.setOnMouseReleased(e -> {
            isUserSeeking = false;
            double fraction = timeSlider.getValue() / 100.0;
            seekToFraction(fraction);
        });

        // Row 2: Playback buttons, Audio Track, Subtitles, Volume, Speed
        HBox controlsRow = new HBox(12);
        controlsRow.setAlignment(Pos.CENTER_LEFT);

        playPauseBtn = new Button("▶ Play");
        playPauseBtn.setStyle("-fx-background-color: #2979FF; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 6 16; -fx-background-radius: 6; -fx-cursor: hand;");
        playPauseBtn.setOnAction(e -> togglePlayPause());

        Button back10Btn = new Button("⏪ -10s");
        back10Btn.setTooltip(new Tooltip("Skip 10 seconds back (Left Arrow)"));
        back10Btn.setStyle("-fx-background-color: #333333; -fx-text-fill: #DDDDDD; -fx-background-radius: 4; -fx-cursor: hand;");
        back10Btn.setOnAction(e -> seekRelative(-10));

        Button forward10Btn = new Button("+10s ⏩");
        forward10Btn.setTooltip(new Tooltip("Skip 10 seconds forward (Right Arrow)"));
        forward10Btn.setStyle("-fx-background-color: #333333; -fx-text-fill: #DDDDDD; -fx-background-radius: 4; -fx-cursor: hand;");
        forward10Btn.setOnAction(e -> seekRelative(10));

        // Audio Track Selector
        Label audioLbl = new Label("🔊 Audio:");
        audioLbl.setStyle("-fx-text-fill: #AAAAAA; -fx-font-size: 11px;");
        audioTrackCombo = new ComboBox<>();
        audioTrackCombo.setPromptText("Default Audio");
        audioTrackCombo.setPrefWidth(140);
        audioTrackCombo.setStyle("-fx-background-color: #2D2D2D; -fx-text-fill: white; -fx-font-size: 11px;");
        audioTrackCombo.setOnAction(e -> {
            TrackItem selected = audioTrackCombo.getValue();
            if (selected != null && isVlcActive && vlcPlayer != null) {
                vlcPlayer.audio().setTrack(selected.id);
                showOsd("🔊 Audio: " + selected.description);
            }
        });

        // Subtitle Track Selector
        Label subLbl = new Label("💬 Subtitles:");
        subLbl.setStyle("-fx-text-fill: #AAAAAA; -fx-font-size: 11px;");
        subtitleTrackCombo = new ComboBox<>();
        subtitleTrackCombo.setPromptText("Subtitles: Off");
        subtitleTrackCombo.setPrefWidth(140);
        subtitleTrackCombo.setStyle("-fx-background-color: #2D2D2D; -fx-text-fill: white; -fx-font-size: 11px;");
        subtitleTrackCombo.setOnAction(e -> {
            TrackItem selected = subtitleTrackCombo.getValue();
            if (selected != null && isVlcActive && vlcPlayer != null) {
                vlcPlayer.subpictures().setTrack(selected.id);
                showOsd("💬 Subtitles: " + selected.description);
            }
        });

        // Volume controls
        muteBtn = new Button("🔊");
        muteBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #DDDDDD; -fx-font-size: 14px; -fx-cursor: hand;");
        muteBtn.setOnAction(e -> toggleMute());

        volumeSlider = new Slider(0, 100, 80);
        volumeSlider.setPrefWidth(90);
        volumeSlider.setStyle("-fx-control-inner-background: #333333; -fx-accent: #2979FF;");
        volumeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            setVolume(newVal.intValue());
        });

        // Speed Selector
        Label speedLbl = new Label("Speed:");
        speedLbl.setStyle("-fx-text-fill: #AAAAAA; -fx-font-size: 11px;");
        rateCombo = new ComboBox<>();
        rateCombo.getItems().addAll("0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "2.0x");
        rateCombo.setValue("1.0x");
        rateCombo.setStyle("-fx-background-color: #2D2D2D; -fx-text-fill: white; -fx-font-size: 11px;");
        rateCombo.setOnAction(e -> {
            String val = rateCombo.getValue().replace("x", "");
            try {
                float rate = Float.parseFloat(val);
                setPlaybackRate(rate);
                showOsd("Speed: " + rateCombo.getValue());
            } catch (NumberFormatException ignored) {}
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button fullscreenBtn = new Button("⛶ Fullscreen");
        fullscreenBtn.setStyle("-fx-background-color: #333333; -fx-text-fill: #DDDDDD; -fx-background-radius: 4; -fx-cursor: hand;");
        fullscreenBtn.setOnAction(e -> stage.setFullScreen(!stage.isFullScreen()));

        controlsRow.getChildren().addAll(
                playPauseBtn, back10Btn, forward10Btn,
                audioLbl, audioTrackCombo,
                subLbl, subtitleTrackCombo,
                muteBtn, volumeSlider,
                speedLbl, rateCombo,
                spacer, fullscreenBtn
        );

        container.getChildren().addAll(sliderRow, controlsRow);
        return container;
    }

    private void initializePlayback() {
        statusLabel.setText("Connecting to: " + file.getName());
        statusLabel.setVisible(true);

        // Attempt LibVLC initialization for native MKV, AVI, MP4, WebM decoding
        boolean vlcReady = setupVlcPlayer();
        if (vlcReady) {
            isVlcActive = true;
            startVlcPlayback();
        } else {
            // Fallback to JavaFX MediaPlayer if LibVLC is not installed
            isVlcActive = false;
            startFxPlayback();
        }
    }

    private boolean setupVlcPlayer() {
        try {
            // Ensure search paths for VLC
            String[] vlcDirs = {
                    "C:\\Program Files\\VideoLAN\\VLC",
                    "C:\\Program Files (x86)\\VideoLAN\\VLC"
            };
            for (String dir : vlcDirs) {
                if (new File(dir).exists()) {
                    NativeLibrary.addSearchPath("libvlc", dir);
                    System.setProperty("VLC_PLUGIN_PATH", dir + "\\plugins");
                }
            }

            NativeDiscovery discovery = new NativeDiscovery();
            boolean discovered = discovery.discover();

            vlcFactory = new MediaPlayerFactory(
                    "--no-video-title-show",
                    "--network-caching=3000",
                    "--clock-jitter=0",
                    "--clock-synchro=0"
            );
            vlcPlayer = vlcFactory.mediaPlayers().newEmbeddedMediaPlayer();
            vlcPlayer.videoSurface().set(new ImageViewVideoSurface(vlcImageView));

            setupVlcEvents();
            return true;
        } catch (Throwable t) {
            System.err.println("LibVLC not available, falling back to JavaFX: " + t.getMessage());
            return false;
        }
    }

    private void setupVlcEvents() {
        vlcPlayer.events().addMediaPlayerEventListener(new MediaPlayerEventAdapter() {
            @Override
            public void playing(uk.co.caprica.vlcj.player.base.MediaPlayer mediaPlayer) {
                Platform.runLater(() -> {
                    statusLabel.setVisible(false);
                    playPauseBtn.setText("❚❚ Pause");
                    refreshTracks();
                });
            }

            @Override
            public void paused(uk.co.caprica.vlcj.player.base.MediaPlayer mediaPlayer) {
                Platform.runLater(() -> playPauseBtn.setText("▶ Play"));
            }

            @Override
            public void stopped(uk.co.caprica.vlcj.player.base.MediaPlayer mediaPlayer) {
                Platform.runLater(() -> playPauseBtn.setText("▶ Play"));
            }

            @Override
            public void finished(uk.co.caprica.vlcj.player.base.MediaPlayer mediaPlayer) {
                Platform.runLater(() -> {
                    playPauseBtn.setText("▶ Replay");
                    timeSlider.setValue(100);
                });
            }

            @Override
            public void lengthChanged(uk.co.caprica.vlcj.player.base.MediaPlayer mediaPlayer, long newLength) {
                totalDurationMs = newLength;
                Platform.runLater(() -> refreshTracks());
            }

            @Override
            public void error(uk.co.caprica.vlcj.player.base.MediaPlayer mediaPlayer) {
                Platform.runLater(() -> {
                    statusLabel.setText("Playback error encountered. Trying alternate playback...");
                    statusLabel.setVisible(true);
                });
            }
        });
    }

    private void startVlcPlayback() {
        vlcPlayer.media().play(streamUrl);
        vlcPlayer.audio().setVolume(80);

        // Start progress polling timer for smooth timeline & track detection
        progressTimer = new Timer(true);
        progressTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (vlcPlayer != null && vlcPlayer.status().isPlaying()) {
                    long current = vlcPlayer.status().time();
                    long length = vlcPlayer.status().length();
                    if (length > 0) {
                        totalDurationMs = length;
                        double fraction = (double) current / (double) length;
                        Platform.runLater(() -> {
                            if (!isUserSeeking) {
                                timeSlider.setValue(fraction * 100.0);
                                updateTimeLabel(current, length);
                            }
                            // Check if tracks need updating
                            if (audioTrackCombo.getItems().isEmpty()) {
                                refreshTracks();
                            }
                        });
                    }
                }
            }
        }, 500, 500);
    }

    private void refreshTracks() {
        if (!isVlcActive || vlcPlayer == null) return;

        // Audio Tracks
        List<TrackDescription> audioTracks = vlcPlayer.audio().trackDescriptions();
        if (audioTracks != null && !audioTracks.isEmpty()) {
            int currentAudio = vlcPlayer.audio().track();
            audioTrackCombo.getItems().clear();
            TrackItem toSelect = null;
            for (TrackDescription td : audioTracks) {
                TrackItem item = new TrackItem(td.id(), td.description());
                audioTrackCombo.getItems().add(item);
                if (td.id() == currentAudio) toSelect = item;
            }
            if (toSelect != null) {
                audioTrackCombo.setValue(toSelect);
            } else if (!audioTrackCombo.getItems().isEmpty()) {
                audioTrackCombo.getSelectionModel().select(0);
            }
        }

        // Subtitle Tracks
        List<TrackDescription> subTracks = vlcPlayer.subpictures().trackDescriptions();
        if (subTracks != null && !subTracks.isEmpty()) {
            int currentSub = vlcPlayer.subpictures().track();
            subtitleTrackCombo.getItems().clear();
            TrackItem toSelect = null;
            for (TrackDescription td : subTracks) {
                TrackItem item = new TrackItem(td.id(), td.description());
                subtitleTrackCombo.getItems().add(item);
                if (td.id() == currentSub) toSelect = item;
            }
            if (toSelect != null) {
                subtitleTrackCombo.setValue(toSelect);
            } else if (!subtitleTrackCombo.getItems().isEmpty()) {
                subtitleTrackCombo.getSelectionModel().select(0);
            }
        }
    }

    private void startFxPlayback() {
        try {
            Media media = new Media(streamUrl);
            fxPlayer = new MediaPlayer(media);
            fxMediaView = new MediaView(fxPlayer);
            fxMediaView.setPreserveRatio(true);
            fxMediaView.fitWidthProperty().bind(videoContainer.widthProperty());
            fxMediaView.fitHeightProperty().bind(videoContainer.heightProperty());

            videoContainer.getChildren().clear();
            videoContainer.getChildren().addAll(fxMediaView, statusLabel, osdBadge);

            fxPlayer.setOnReady(() -> {
                statusLabel.setVisible(false);
                Duration dur = media.getDuration();
                totalDurationMs = (long) dur.toMillis();
                updateTimeLabel(0, totalDurationMs);
                playPauseBtn.setText("❚❚ Pause");
                fxPlayer.play();
            });

            fxPlayer.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
                if (!isUserSeeking && totalDurationMs > 0) {
                    double progress = (newTime.toMillis() / (double) totalDurationMs) * 100.0;
                    timeSlider.setValue(progress);
                    updateTimeLabel((long) newTime.toMillis(), totalDurationMs);
                }
            });

            fxPlayer.setOnError(() -> {
                statusLabel.setText("JavaFX media error: " + fxPlayer.getError().getMessage());
                statusLabel.setVisible(true);
            });

        } catch (Exception ex) {
            statusLabel.setText("Could not load media: " + ex.getMessage());
            statusLabel.setVisible(true);
        }
    }

    /**
     * Intercepts key presses globally using EventFilter so that arrow keys and spacebar
     * ALWAYS work regardless of which button, combobox, or slider currently has focus.
     */
    private void applyGlobalKeyboardShortcuts(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            KeyCode code = event.getCode();

            if (code == KeyCode.LEFT) {
                // Skip backward 10 seconds
                seekRelative(-10);
                event.consume();
            } else if (code == KeyCode.RIGHT) {
                // Skip forward 10 seconds
                seekRelative(10);
                event.consume();
            } else if (code == KeyCode.SPACE) {
                togglePlayPause();
                event.consume();
            } else if (code == KeyCode.UP) {
                adjustVolume(5);
                event.consume();
            } else if (code == KeyCode.DOWN) {
                adjustVolume(-5);
                event.consume();
            } else if (code == KeyCode.M) {
                toggleMute();
                event.consume();
            } else if (code == KeyCode.F || code == KeyCode.F11) {
                stage.setFullScreen(!stage.isFullScreen());
                event.consume();
            }
        });
    }

    public void seekRelative(int seconds) {
        if (isVlcActive && vlcPlayer != null) {
            long skipMs = seconds * 1000L;
            vlcPlayer.controls().skipTime(skipMs);
            long current = vlcPlayer.status().time();
            updateTimeLabel(current, totalDurationMs);
            if (totalDurationMs > 0) {
                timeSlider.setValue(((double) current / (double) totalDurationMs) * 100.0);
            }
        } else if (fxPlayer != null && totalDurationMs > 0) {
            Duration cur = fxPlayer.getCurrentTime();
            Duration target = cur.add(Duration.seconds(seconds));
            if (target.lessThan(Duration.ZERO)) target = Duration.ZERO;
            fxPlayer.seek(target);
        }

        // Show on-screen visual confirmation of 10s skip
        String osdText = (seconds > 0 ? ("+ " + seconds + "s ⏩") : ("⏪ " + seconds + "s"));
        showOsd(osdText);
    }

    private void seekToFraction(double fraction) {
        if (isVlcActive && vlcPlayer != null) {
            vlcPlayer.controls().setPosition((float) fraction);
        } else if (fxPlayer != null && totalDurationMs > 0) {
            fxPlayer.seek(Duration.millis(totalDurationMs * fraction));
        }
    }

    private void togglePlayPause() {
        if (isVlcActive && vlcPlayer != null) {
            if (vlcPlayer.status().isPlaying()) {
                vlcPlayer.controls().pause();
                playPauseBtn.setText("▶ Play");
                showOsd("❚❚ Paused");
            } else {
                vlcPlayer.controls().play();
                playPauseBtn.setText("❚❚ Pause");
                showOsd("▶ Playing");
            }
        } else if (fxPlayer != null) {
            if (fxPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
                fxPlayer.pause();
                playPauseBtn.setText("▶ Play");
                showOsd("❚❚ Paused");
            } else {
                fxPlayer.play();
                playPauseBtn.setText("❚❚ Pause");
                showOsd("▶ Playing");
            }
        }
    }

    private void setVolume(int volumePercent) {
        if (isVlcActive && vlcPlayer != null) {
            vlcPlayer.audio().setVolume(volumePercent);
        } else if (fxPlayer != null) {
            fxPlayer.setVolume(volumePercent / 100.0);
        }
        muteBtn.setText(volumePercent == 0 ? "🔇" : "🔊");
    }

    private void adjustVolume(int delta) {
        int newVol = (int) Math.max(0, Math.min(100, volumeSlider.getValue() + delta));
        volumeSlider.setValue(newVol);
        showOsd("Volume: " + newVol + "%");
    }

    private void toggleMute() {
        if (isVlcActive && vlcPlayer != null) {
            boolean isMuted = !vlcPlayer.audio().isMute();
            vlcPlayer.audio().setMute(isMuted);
            muteBtn.setText(isMuted ? "🔇" : "🔊");
            showOsd(isMuted ? "Muted" : "Unmuted");
        } else if (fxPlayer != null) {
            boolean isMuted = !fxPlayer.isMute();
            fxPlayer.setMute(isMuted);
            muteBtn.setText(isMuted ? "🔇" : "🔊");
            showOsd(isMuted ? "Muted" : "Unmuted");
        }
    }

    private void setPlaybackRate(float rate) {
        if (isVlcActive && vlcPlayer != null) {
            vlcPlayer.controls().setRate(rate);
        } else if (fxPlayer != null) {
            fxPlayer.setRate(rate);
        }
    }

    private void showOsd(String text) {
        Platform.runLater(() -> {
            osdBadge.setText(text);
            osdBadge.setVisible(true);
            osdBadge.setOpacity(1.0);

            FadeTransition fade = new FadeTransition(Duration.millis(800), osdBadge);
            fade.setFromValue(1.0);
            fade.setToValue(0.0);
            fade.setDelay(Duration.millis(500));
            fade.setOnFinished(e -> osdBadge.setVisible(false));
            fade.play();
        });
    }

    private void updateTimeLabel(long currentMs, long totalMs) {
        String curStr = formatMs(currentMs);
        String totStr = totalMs > 0 ? formatMs(totalMs) : "--:--:--";
        timeLabel.setText(curStr + " / " + totStr);
    }

    private String formatMs(long ms) {
        long totalSec = Math.max(0, ms / 1000);
        long hours = totalSec / 3600;
        long minutes = (totalSec % 3600) / 60;
        long seconds = totalSec % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    private void launchInBrowser() {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(streamUrl));
            } else {
                new ProcessBuilder("cmd", "/c", "start", streamUrl).start();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void cleanup() {
        if (progressTimer != null) {
            progressTimer.cancel();
            progressTimer = null;
        }
        if (vlcPlayer != null) {
            try {
                vlcPlayer.controls().stop();
                vlcPlayer.release();
            } catch (Exception ignored) {}
            vlcPlayer = null;
        }
        if (vlcFactory != null) {
            try {
                vlcFactory.release();
            } catch (Exception ignored) {}
            vlcFactory = null;
        }
        if (fxPlayer != null) {
            try {
                fxPlayer.stop();
                fxPlayer.dispose();
            } catch (Exception ignored) {}
            fxPlayer = null;
        }
    }
}
