package com.localstream.player.ui;

import com.localstream.player.model.RemoteFile;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.awt.Desktop;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

/**
 * Dedicated Media Player Window with built-in controls (play, pause, volume,
 * range-based seeking) and graceful fallback for unsupported formats (like WebM or unrecognized codecs).
 */
public class PlayerWindow {

    private final Stage stage;
    private final RemoteFile file;
    private final String streamUrl;

    private MediaPlayer mediaPlayer;
    private MediaView mediaView;

    // Controls
    private Button playPauseBtn;
    private Slider timeSlider;
    private Label timeLabel;
    private Slider volumeSlider;
    private Button muteBtn;
    private Label statusLabel;
    private ComboBox<String> rateCombo;
    private StackPane videoContainer;
    private VBox fallbackPane;

    private boolean isUserSeeking = false;
    private Duration totalDuration = Duration.UNKNOWN;

    public PlayerWindow(RemoteFile file, String streamUrl) {
        this.file = file;
        this.streamUrl = streamUrl;
        this.stage = new Stage();
    }

    public void show() {
        stage.setTitle("Playing: " + file.getName() + " [" + file.getFormattedSize() + "]");
        stage.setWidth(900);
        stage.setHeight(620);
        stage.setMinWidth(640);
        stage.setMinHeight(480);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #121212;");

        // Top info header
        HBox topBar = createTopBar();
        root.setTop(topBar);

        // Center: Video Viewport Stack
        videoContainer = new StackPane();
        videoContainer.setStyle("-fx-background-color: #000000;");

        mediaView = new MediaView();
        mediaView.setPreserveRatio(true);
        mediaView.fitWidthProperty().bind(videoContainer.widthProperty());
        mediaView.fitHeightProperty().bind(videoContainer.heightProperty());

        // Status overlay (e.g. "Loading media...", "Buffering...")
        statusLabel = new Label("Initializing media player...");
        statusLabel.setStyle("-fx-text-fill: #E0E0E0; -fx-font-size: 15px; -fx-background-color: rgba(0,0,0,0.7); -fx-padding: 8 16; -fx-background-radius: 6;");

        // Fallback Panel (hidden by default)
        fallbackPane = createFallbackPane();
        fallbackPane.setVisible(false);

        videoContainer.getChildren().addAll(mediaView, statusLabel, fallbackPane);
        root.setCenter(videoContainer);

        // Bottom Controls Bar
        VBox controlsBar = createControlsBar();
        root.setBottom(controlsBar);

        Scene scene = new Scene(root);
        applyKeyboardShortcuts(scene);

        stage.setScene(scene);
        stage.setOnCloseRequest(e -> cleanup());
        stage.show();

        // Start loading the stream
        initializePlayer();
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
        openBrowserBtn.setTooltip(new Tooltip("Open in default system browser (plays WebM/MP4 directly)"));
        openBrowserBtn.setOnAction(e -> launchInBrowser());

        bar.getChildren().addAll(nameLbl, badge, sizeLbl, spacer, copyUrlBtn, openBrowserBtn);
        return bar;
    }

    private VBox createControlsBar() {
        VBox container = new VBox(6);
        container.setPadding(new Insets(10, 16, 12, 16));
        container.setStyle("-fx-background-color: #1A1A1A; -fx-border-color: #2D2D2D; -fx-border-width: 1 0 0 0;");

        // 1. Time Slider Row
        HBox sliderRow = new HBox(10);
        sliderRow.setAlignment(Pos.CENTER);

        timeSlider = new Slider(0, 100, 0);
        HBox.setHgrow(timeSlider, Priority.ALWAYS);
        timeSlider.setStyle("-fx-control-inner-background: #333333; -fx-accent: #2979FF;");

        timeLabel = new Label("00:00 / 00:00");
        timeLabel.setStyle("-fx-text-fill: #CCCCCC; -fx-font-family: monospace; -fx-font-size: 12px;");
        timeLabel.setMinWidth(95);
        timeLabel.setAlignment(Pos.CENTER_RIGHT);

        sliderRow.getChildren().addAll(timeSlider, timeLabel);

        // Setup seek slider listeners for range seeking
        timeSlider.setOnMousePressed(e -> isUserSeeking = true);
        timeSlider.setOnMouseReleased(e -> {
            isUserSeeking = false;
            if (mediaPlayer != null && totalDuration.greaterThan(Duration.ZERO)) {
                double seekFraction = timeSlider.getValue() / 100.0;
                Duration seekTo = totalDuration.multiply(seekFraction);
                // Seeking here causes MediaPlayer to issue HTTP Range requests to the server!
                mediaPlayer.seek(seekTo);
            }
        });

        // 2. Playback buttons & volume row
        HBox buttonsRow = new HBox(12);
        buttonsRow.setAlignment(Pos.CENTER_LEFT);

        playPauseBtn = new Button("▶ Play");
        playPauseBtn.setStyle("-fx-background-color: #2979FF; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 6 16; -fx-background-radius: 6; -fx-cursor: hand;");
        playPauseBtn.setOnAction(e -> togglePlayPause());

        Button back10Btn = new Button("⏪ -10s");
        back10Btn.setStyle("-fx-background-color: #333333; -fx-text-fill: #DDDDDD; -fx-background-radius: 4; -fx-cursor: hand;");
        back10Btn.setOnAction(e -> seekRelative(-10));

        Button forward10Btn = new Button("+10s ⏩");
        forward10Btn.setStyle("-fx-background-color: #333333; -fx-text-fill: #DDDDDD; -fx-background-radius: 4; -fx-cursor: hand;");
        forward10Btn.setOnAction(e -> seekRelative(10));

        // Volume controls
        muteBtn = new Button("🔊");
        muteBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #DDDDDD; -fx-font-size: 14px; -fx-cursor: hand;");
        muteBtn.setOnAction(e -> toggleMute());

        volumeSlider = new Slider(0, 1, 0.8);
        volumeSlider.setPrefWidth(90);
        volumeSlider.setStyle("-fx-control-inner-background: #333333; -fx-accent: #2979FF;");
        volumeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (mediaPlayer != null) {
                mediaPlayer.setVolume(newVal.doubleValue());
                updateMuteIcon(newVal.doubleValue() == 0 || mediaPlayer.isMute());
            }
        });

        // Rate Selector
        Label rateLbl = new Label("Speed:");
        rateLbl.setStyle("-fx-text-fill: #888888; -fx-font-size: 11px;");
        rateCombo = new ComboBox<>();
        rateCombo.getItems().addAll("0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "2.0x");
        rateCombo.setValue("1.0x");
        rateCombo.setStyle("-fx-background-color: #2D2D2D; -fx-text-fill: white; -fx-font-size: 11px;");
        rateCombo.setOnAction(e -> {
            if (mediaPlayer != null) {
                String val = rateCombo.getValue().replace("x", "");
                try {
                    double rate = Double.parseDouble(val);
                    mediaPlayer.setRate(rate);
                } catch (NumberFormatException ignored) {}
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button fullscreenBtn = new Button("⛶ Fullscreen");
        fullscreenBtn.setStyle("-fx-background-color: #333333; -fx-text-fill: #DDDDDD; -fx-background-radius: 4; -fx-cursor: hand;");
        fullscreenBtn.setOnAction(e -> stage.setFullScreen(!stage.isFullScreen()));

        buttonsRow.getChildren().addAll(playPauseBtn, back10Btn, forward10Btn, muteBtn, volumeSlider, rateLbl, rateCombo, spacer, fullscreenBtn);

        container.getChildren().addAll(sliderRow, buttonsRow);
        return container;
    }

    private VBox createFallbackPane() {
        VBox box = new VBox(14);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(30));
        box.setStyle("-fx-background-color: rgba(18, 18, 18, 0.95); -fx-background-radius: 12; -fx-border-color: #FF9800; -fx-border-radius: 12; -fx-border-width: 1; -fx-max-width: 600; -fx-max-height: 380;");

        Label icon = new Label("⚠️");
        icon.setStyle("-fx-font-size: 36px;");

        Label title = new Label("Native JavaFX Decoder Notice");
        title.setStyle("-fx-text-fill: #FFA726; -fx-font-weight: bold; -fx-font-size: 18px;");

        Label desc = new Label(
                "The JavaFX native media component cannot decode this container or codec (" + file.getMimeType() + ") directly.\n" +
                "WebM VP8/VP9 or specialized video codecs require an external player or browser engine with native codecs.\n\n" +
                "You can play this stream with full seeking support using the options below:"
        );
        desc.setWrapText(true);
        desc.setStyle("-fx-text-fill: #DDDDDD; -fx-font-size: 13px; -fx-text-alignment: center;");

        HBox actions = new HBox(12);
        actions.setAlignment(Pos.CENTER);

        Button btnBrowser = new Button("🌐 Play in Browser (Chrome/Edge)");
        btnBrowser.setStyle("-fx-background-color: #2979FF; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 16; -fx-background-radius: 6; -fx-cursor: hand;");
        btnBrowser.setOnAction(e -> launchInBrowser());

        Button btnVlc = new Button("🎬 Open in VLC Player");
        btnVlc.setStyle("-fx-background-color: #E65100; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 16; -fx-background-radius: 6; -fx-cursor: hand;");
        btnVlc.setOnAction(e -> launchInVlc());

        Button btnEmbedHtml5 = new Button("🌐 Try Embedded HTML5 View");
        btnEmbedHtml5.setStyle("-fx-background-color: #43A047; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 16; -fx-background-radius: 6; -fx-cursor: hand;");
        btnEmbedHtml5.setOnAction(e -> tryEmbeddedHtml5());

        actions.getChildren().addAll(btnBrowser, btnVlc, btnEmbedHtml5);

        HBox secondaryActions = new HBox(12);
        secondaryActions.setAlignment(Pos.CENTER);

        Button btnDownload = new Button("⬇ Download File");
        btnDownload.setStyle("-fx-background-color: #333333; -fx-text-fill: #EEEEEE; -fx-padding: 6 14; -fx-background-radius: 6; -fx-cursor: hand;");
        btnDownload.setOnAction(e -> downloadFile());

        secondaryActions.getChildren().add(btnDownload);

        box.getChildren().addAll(icon, title, desc, actions, secondaryActions);
        return box;
    }

    private void initializePlayer() {
        try {
            statusLabel.setText("Connecting to stream:\n" + streamUrl);
            statusLabel.setVisible(true);

            // Attempt to create JavaFX Media
            Media media = new Media(streamUrl);

            mediaPlayer = new MediaPlayer(media);
            mediaView.setMediaPlayer(mediaPlayer);

            mediaPlayer.setOnReady(() -> {
                statusLabel.setVisible(false);
                totalDuration = media.getDuration();
                updateTimeLabel(Duration.ZERO, totalDuration);
                playPauseBtn.setText("❚❚ Pause");
                mediaPlayer.play();
            });

            mediaPlayer.setOnPlaying(() -> {
                statusLabel.setVisible(false);
                playPauseBtn.setText("❚❚ Pause");
            });

            mediaPlayer.setOnPaused(() -> playPauseBtn.setText("▶ Play"));

            mediaPlayer.setOnEndOfMedia(() -> {
                playPauseBtn.setText("▶ Replay");
                if (mediaPlayer != null) {
                    mediaPlayer.seek(Duration.ZERO);
                    mediaPlayer.pause();
                }
            });

            // Update time slider as video plays
            mediaPlayer.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
                if (!isUserSeeking && totalDuration.greaterThan(Duration.ZERO)) {
                    double progress = (newTime.toMillis() / totalDuration.toMillis()) * 100.0;
                    timeSlider.setValue(progress);
                    updateTimeLabel(newTime, totalDuration);
                }
            });

            // Handle errors gracefully (e.g. unsupported WebM codecs or corrupt streams)
            mediaPlayer.setOnError(() -> {
                MediaException err = mediaPlayer.getError();
                handlePlaybackFallback(err != null ? err.getMessage() : "Unsupported format");
            });

            media.setOnError(() -> {
                MediaException err = media.getError();
                handlePlaybackFallback(err != null ? err.getMessage() : "Unsupported media source");
            });

            // Pre-check for WebM: on some platforms, JavaFX Media throws immediately
            if (file.isWebM()) {
                // If WebM, also show fallback option proactively after brief delay if not ready
                new Thread(() -> {
                    try {
                        Thread.sleep(2500);
                        if (mediaPlayer != null && mediaPlayer.getStatus() != MediaPlayer.Status.PLAYING && mediaPlayer.getStatus() != MediaPlayer.Status.READY) {
                            Platform.runLater(() -> handlePlaybackFallback("WebM VP8/VP9 format"));
                        }
                    } catch (InterruptedException ignored) {}
                }).start();
            }

        } catch (Exception ex) {
            handlePlaybackFallback(ex.getMessage());
        }
    }

    private void handlePlaybackFallback(String reason) {
        Platform.runLater(() -> {
            statusLabel.setVisible(false);
            fallbackPane.setVisible(true);
            playPauseBtn.setDisable(true);
            timeSlider.setDisable(true);
        });
    }

    private void togglePlayPause() {
        if (mediaPlayer == null) return;
        if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
            mediaPlayer.pause();
            playPauseBtn.setText("▶ Play");
        } else {
            mediaPlayer.play();
            playPauseBtn.setText("❚❚ Pause");
        }
    }

    private void seekRelative(int seconds) {
        if (mediaPlayer != null && totalDuration.greaterThan(Duration.ZERO)) {
            Duration current = mediaPlayer.getCurrentTime();
            Duration target = current.add(Duration.seconds(seconds));
            if (target.lessThan(Duration.ZERO)) target = Duration.ZERO;
            if (target.greaterThan(totalDuration)) target = totalDuration;
            mediaPlayer.seek(target);
        }
    }

    private void toggleMute() {
        if (mediaPlayer == null) return;
        boolean isMuted = !mediaPlayer.isMute();
        mediaPlayer.setMute(isMuted);
        updateMuteIcon(isMuted);
    }

    private void updateMuteIcon(boolean isMuted) {
        muteBtn.setText(isMuted ? "🔇" : "🔊");
    }

    private void updateTimeLabel(Duration current, Duration total) {
        String currentStr = formatDuration(current);
        String totalStr = (total.isUnknown() || total.lessThan(Duration.ZERO)) ? "--:--" : formatDuration(total);
        timeLabel.setText(currentStr + " / " + totalStr);
    }

    private String formatDuration(Duration duration) {
        int totalSeconds = (int) Math.floor(duration.toSeconds());
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }

    private void launchInBrowser() {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(streamUrl));
            } else {
                new ProcessBuilder("cmd", "/c", "start", streamUrl).start();
            }
        } catch (Exception ex) {
            showAlert("Could not open browser", ex.getMessage());
        }
    }

    private void launchInVlc() {
        try {
            // Check common VLC paths or command
            String[] vlcCandidates = {
                    "vlc",
                    "C:\\Program Files\\VideoLAN\\VLC\\vlc.exe",
                    "C:\\Program Files (x86)\\VideoLAN\\VLC\\vlc.exe"
            };
            boolean launched = false;
            for (String vlcCmd : vlcCandidates) {
                try {
                    new ProcessBuilder(vlcCmd, streamUrl).start();
                    launched = true;
                    break;
                } catch (Exception ignored) {}
            }
            if (!launched) {
                // Fallback to opening system player
                launchInBrowser();
            }
        } catch (Exception ex) {
            showAlert("Error launching player", ex.getMessage());
        }
    }

    private void tryEmbeddedHtml5() {
        try {
            fallbackPane.setVisible(false);
            WebView webView = new WebView();
            webView.getEngine().loadContent(
                    "<!DOCTYPE html><html><head><style>" +
                    "body { margin:0; background:#000; display:flex; justify-content:center; align-items:center; height:100vh; overflow:hidden; }" +
                    "video { max-width:100%; max-height:100%; outline:none; }" +
                    "</style></head><body>" +
                    "<video controls autoplay src=\"" + streamUrl + "\"></video>" +
                    "</body></html>"
            );
            videoContainer.getChildren().clear();
            videoContainer.getChildren().add(webView);
        } catch (Exception e) {
            handlePlaybackFallback("Embedded HTML5 failed: " + e.getMessage());
        }
    }

    private void downloadFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Video File");
        chooser.setInitialFileName(file.getName());
        File destination = chooser.showSaveDialog(stage);
        if (destination != null) {
            new Thread(() -> {
                try (InputStream in = new URL(streamUrl).openStream();
                     FileOutputStream out = new FileOutputStream(destination);
                     ReadableByteChannel rbc = Channels.newChannel(in)) {
                    out.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
                    Platform.runLater(() -> showAlert("Download Complete", "Saved to " + destination.getAbsolutePath()));
                } catch (Exception ex) {
                    Platform.runLater(() -> showAlert("Download Failed", ex.getMessage()));
                }
            }).start();
        }
    }

    private void applyKeyboardShortcuts(Scene scene) {
        scene.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.SPACE) {
                togglePlayPause();
                event.consume();
            } else if (event.getCode() == KeyCode.LEFT) {
                seekRelative(-5);
                event.consume();
            } else if (event.getCode() == KeyCode.RIGHT) {
                seekRelative(5);
                event.consume();
            } else if (event.getCode() == KeyCode.UP) {
                volumeSlider.setValue(Math.min(1.0, volumeSlider.getValue() + 0.05));
                event.consume();
            } else if (event.getCode() == KeyCode.DOWN) {
                volumeSlider.setValue(Math.max(0.0, volumeSlider.getValue() - 0.05));
                event.consume();
            } else if (event.getCode() == KeyCode.F || event.getCode() == KeyCode.F11) {
                stage.setFullScreen(!stage.isFullScreen());
                event.consume();
            } else if (event.getCode() == KeyCode.M) {
                toggleMute();
                event.consume();
            }
        });
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void cleanup() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
                mediaPlayer.dispose();
            } catch (Exception ignored) {}
            mediaPlayer = null;
        }
    }
}
