import sys
from PyQt6.QtCore import Qt, QUrl, QTimer, QEvent
from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QPushButton, QSlider, QLabel,
    QComboBox, QSizePolicy, QApplication
)
from PyQt6.QtGui import QCursor
from PyQt6.QtMultimedia import QMediaPlayer, QAudioOutput, QMediaMetaData
from PyQt6.QtMultimediaWidgets import QVideoWidget
from PyQt6.QtNetwork import QSslConfiguration, QSslSocket

# Allow self-signed TLS certificates for end-to-end encrypted local streaming
try:
    _ssl_conf = QSslConfiguration.defaultConfiguration()
    _ssl_conf.setPeerVerifyMode(QSslSocket.PeerVerifyMode.VerifyNone)
    QSslConfiguration.setDefaultConfiguration(_ssl_conf)
except Exception:
    pass



class PlayerWindow(QWidget):
    """
    High-Performance Media Player Window:
    - Native MKV, AVI, MP4, and WebM hardware playback via Qt 6.5+ FFmpeg backend.
    - Multi-audio track selection.
    - Embedded subtitle track selection.
    - 10-second skip via Left / Right arrow keys with centered OSD badge.
    - Docked controls ALWAYS visible in small screen (windowed mode).
    - True edge-to-edge fullscreen where video covers 100% of the display.
    - In fullscreen mode, controls and cursor auto-hide after 2.5s of idle,
      and wake up instantly on mouse movement.
    """

    def __init__(self, file_data: dict, stream_url: str):
        super().__init__()
        self.file_data = file_data
        self.stream_url = stream_url

        self.setWindowTitle(f"Playing: {file_data.get('name', 'Media')} [{file_data.get('size_formatted', '')}]")
        self.resize(980, 620)
        self.setMinimumSize(680, 420)
        self.setStyleSheet("background-color: #0E0E0E; color: #FFFFFF;")

        # Enable mouse tracking on window and widgets
        self.setMouseTracking(True)

        # Player Engine
        self.player = QMediaPlayer(self)
        self.audio_output = QAudioOutput(self)
        self.player.setAudioOutput(self.audio_output)
        self.audio_output.setVolume(0.85)

        # Video Widget
        self.video_widget = QVideoWidget(self)
        self.video_widget.setMouseTracking(True)
        self.video_widget.setStyleSheet("background-color: #000000;")
        self.player.setVideoOutput(self.video_widget)

        # Auto-hide timer for Fullscreen only (2.5 seconds idle timeout)
        self.idle_timer = QTimer(self)
        self.idle_timer.setInterval(2500)
        self.idle_timer.timeout.connect(self._on_idle_timeout)

        # OSD Badge Timer
        self.osd_timer = QTimer(self)
        self.osd_timer.setSingleShot(True)
        self.osd_timer.setInterval(900)
        self.osd_timer.timeout.connect(self._hide_osd)

        self.is_user_scrubbing = False

        self._build_ui()
        self._bind_events()
        self._load_stream()

        # Install application-level event filter so arrow keys and mouse moves are reliably caught
        QApplication.instance().installEventFilter(self)

    def _build_ui(self):
        # Master Vertical Layout: Top Bar -> Video Widget -> Bottom Controls Bar
        self.root_layout = QVBoxLayout(self)
        self.root_layout.setContentsMargins(0, 0, 0, 0)
        self.root_layout.setSpacing(0)

        # 1. Top Header Bar
        self.top_bar = self._create_top_bar()
        self.top_bar.setMouseTracking(True)

        # 2. Video Center Area with Centered Floating OSD Badge
        self.osd_badge = QLabel(self.video_widget)
        self.osd_badge.setStyleSheet(
            "background-color: rgba(0, 0, 0, 0.85); color: #FFFFFF; font-size: 20px; "
            "font-weight: bold; padding: 10px 24px; border-radius: 8px; border: 2px solid #2979FF;"
        )
        self.osd_badge.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.osd_badge.hide()

        # 3. Bottom Controls Bar (Permanently docked & visible in windowed mode)
        self.controls_bar = self._create_controls_bar()
        self.controls_bar.setMouseTracking(True)

        # Assemble layout
        self.root_layout.addWidget(self.top_bar)
        self.root_layout.addWidget(self.video_widget, stretch=1)
        self.root_layout.addWidget(self.controls_bar)

    def _create_top_bar(self) -> QWidget:
        bar = QWidget()
        bar.setStyleSheet("""
            QWidget {
                background-color: #1A1A1A;
                border-bottom: 1px solid #2A2A2A;
            }
        """)
        layout = QHBoxLayout(bar)
        layout.setContentsMargins(14, 6, 14, 6)
        layout.setSpacing(10)

        title = QLabel(self.file_data.get("name", "Unknown File"))
        title.setStyleSheet("font-weight: bold; font-size: 13px; color: #FFFFFF;")

        mime = QLabel(self.file_data.get("mimeType", "video/*"))
        mime.setStyleSheet(
            "background-color: #2979FF; color: white; padding: 2px 8px; border-radius: 4px; "
            "font-size: 11px; font-weight: bold;"
        )

        size = QLabel(self.file_data.get("size_formatted", ""))
        size.setStyleSheet("color: #AAAAAA; font-size: 11px;")

        copy_btn = QPushButton("📋 Copy Stream URL")
        copy_btn.setFocusPolicy(Qt.FocusPolicy.NoFocus)
        copy_btn.setStyleSheet(
            "background-color: #262626; color: #DDDDDD; padding: 4px 10px; border-radius: 4px; "
            "font-size: 11px; border: 1px solid #3A3A3A;"
        )
        copy_btn.clicked.connect(self._copy_url)

        layout.addWidget(title)
        layout.addWidget(mime)
        layout.addWidget(size)
        layout.addStretch(1)
        layout.addWidget(copy_btn)
        return bar

    def _create_controls_bar(self) -> QWidget:
        bar = QWidget()
        bar.setStyleSheet("""
            QWidget {
                background-color: #141414;
                border-top: 1px solid #282828;
            }
            QLabel {
                background: transparent;
            }
        """)
        vbox = QVBoxLayout(bar)
        vbox.setContentsMargins(10, 6, 10, 8)
        vbox.setSpacing(6)

        # Row 1: Timeline Seek Slider + Time Label (takes full width)
        slider_row = QHBoxLayout()
        slider_row.setSpacing(10)

        self.time_slider = QSlider(Qt.Orientation.Horizontal)
        self.time_slider.setRange(0, 1000)
        self.time_slider.setFocusPolicy(Qt.FocusPolicy.NoFocus)
        self.time_slider.setStyleSheet("""
            QSlider { background: transparent; height: 16px; }
            QSlider::groove:horizontal { height: 5px; background: #333333; border-radius: 2px; }
            QSlider::sub-page:horizontal { background: #2979FF; border-radius: 2px; }
            QSlider::handle:horizontal {
                background: #FFFFFF;
                border: 2px solid #2979FF;
                width: 12px;
                height: 12px;
                margin-top: -4px;
                margin-bottom: -4px;
                border-radius: 6px;
            }
            QSlider::handle:horizontal:hover {
                background: #2979FF;
                border: 2px solid #FFFFFF;
                width: 14px;
                height: 14px;
                margin-top: -5px;
                margin-bottom: -5px;
                border-radius: 7px;
            }
        """)
        self.time_slider.sliderPressed.connect(self._on_slider_pressed)
        self.time_slider.sliderReleased.connect(self._on_slider_released)

        self.time_label = QLabel("00:00:00 / 00:00:00")
        self.time_label.setStyleSheet("color: #CCCCCC; font-family: monospace; font-size: 11px; min-width: 125px;")
        self.time_label.setAlignment(Qt.AlignmentFlag.AlignRight | Qt.AlignmentFlag.AlignVCenter)

        slider_row.addWidget(self.time_slider, stretch=1)
        slider_row.addWidget(self.time_label)

        # Row 2: Play/Pause, 10s Skips, Audio track, Subtitles, Volume, Speed, Fullscreen
        controls_row = QHBoxLayout()
        controls_row.setSpacing(6)

        self.play_btn = QPushButton("▶ Play")
        self.play_btn.setFocusPolicy(Qt.FocusPolicy.NoFocus)
        self.play_btn.setStyleSheet(
            "background-color: #2979FF; color: white; font-weight: bold; padding: 4px 12px; "
            "border-radius: 4px; font-size: 11px;"
        )
        self.play_btn.clicked.connect(self.toggle_play_pause)

        self.back10_btn = QPushButton("⏪ -10s")
        self.back10_btn.setToolTip("Skip 10 seconds backward (Left Arrow)")
        self.back10_btn.setFocusPolicy(Qt.FocusPolicy.NoFocus)
        self.back10_btn.setStyleSheet(
            "background-color: #262626; color: #EEEEEE; padding: 4px 8px; border-radius: 4px; "
            "font-size: 11px; border: 1px solid #3A3A3A;"
        )
        self.back10_btn.clicked.connect(lambda: self.seek_relative(-10))

        self.fwd10_btn = QPushButton("+10s ⏩")
        self.fwd10_btn.setToolTip("Skip 10 seconds forward (Right Arrow)")
        self.fwd10_btn.setFocusPolicy(Qt.FocusPolicy.NoFocus)
        self.fwd10_btn.setStyleSheet(
            "background-color: #262626; color: #EEEEEE; padding: 4px 8px; border-radius: 4px; "
            "font-size: 11px; border: 1px solid #3A3A3A;"
        )
        self.fwd10_btn.clicked.connect(lambda: self.seek_relative(10))

        # Audio Track Dropdown
        audio_label = QLabel("🔊")
        audio_label.setStyleSheet("color: #AAAAAA; font-size: 12px;")
        self.audio_combo = QComboBox()
        self.audio_combo.setFocusPolicy(Qt.FocusPolicy.NoFocus)
        self.audio_combo.setSizePolicy(QSizePolicy.Policy.Preferred, QSizePolicy.Policy.Fixed)
        self.audio_combo.setMinimumWidth(80)
        self.audio_combo.setMaximumWidth(125)
        self.audio_combo.setStyleSheet("""
            QComboBox { background-color: #262626; color: white; padding: 3px 6px; border-radius: 4px; font-size: 11px; border: 1px solid #3A3A3A; }
            QComboBox QAbstractItemView { background-color: #1E1E1E; color: white; selection-background-color: #2979FF; }
        """)
        self.audio_combo.currentIndexChanged.connect(self._on_audio_track_changed)

        # Subtitle Track Dropdown
        sub_label = QLabel("💬")
        sub_label.setStyleSheet("color: #AAAAAA; font-size: 12px;")
        self.subtitle_combo = QComboBox()
        self.subtitle_combo.setFocusPolicy(Qt.FocusPolicy.NoFocus)
        self.subtitle_combo.setSizePolicy(QSizePolicy.Policy.Preferred, QSizePolicy.Policy.Fixed)
        self.subtitle_combo.setMinimumWidth(80)
        self.subtitle_combo.setMaximumWidth(125)
        self.subtitle_combo.setStyleSheet("""
            QComboBox { background-color: #262626; color: white; padding: 3px 6px; border-radius: 4px; font-size: 11px; border: 1px solid #3A3A3A; }
            QComboBox QAbstractItemView { background-color: #1E1E1E; color: white; selection-background-color: #2979FF; }
        """)
        self.subtitle_combo.currentIndexChanged.connect(self._on_subtitle_track_changed)

        # Volume Controls
        self.mute_btn = QPushButton("🔊")
        self.mute_btn.setFocusPolicy(Qt.FocusPolicy.NoFocus)
        self.mute_btn.setFixedWidth(24)
        self.mute_btn.setStyleSheet("background: transparent; font-size: 13px; border: none;")
        self.mute_btn.clicked.connect(self.toggle_mute)

        self.volume_slider = QSlider(Qt.Orientation.Horizontal)
        self.volume_slider.setRange(0, 100)
        self.volume_slider.setValue(85)
        self.volume_slider.setFixedWidth(55)
        self.volume_slider.setFocusPolicy(Qt.FocusPolicy.NoFocus)
        self.volume_slider.setStyleSheet("""
            QSlider { background: transparent; height: 14px; }
            QSlider::groove:horizontal { height: 4px; background: #333333; border-radius: 2px; }
            QSlider::sub-page:horizontal { background: #2979FF; border-radius: 2px; }
            QSlider::handle:horizontal { background: white; width: 8px; margin-top: -3px; margin-bottom: -3px; border-radius: 4px; }
        """)
        self.volume_slider.valueChanged.connect(self._on_volume_changed)

        # Speed Dropdown
        self.speed_combo = QComboBox()
        self.speed_combo.setFocusPolicy(Qt.FocusPolicy.NoFocus)
        self.speed_combo.setFixedWidth(52)
        self.speed_combo.addItems(["0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "2.0x"])
        self.speed_combo.setCurrentText("1.0x")
        self.speed_combo.setStyleSheet("""
            QComboBox { background-color: #262626; color: white; font-size: 11px; padding: 3px 4px; border-radius: 4px; border: 1px solid #3A3A3A; }
            QComboBox QAbstractItemView { background-color: #1E1E1E; color: white; selection-background-color: #2979FF; }
        """)
        self.speed_combo.currentTextChanged.connect(self._on_speed_changed)

        # Fullscreen Toggle Button
        self.fs_btn = QPushButton("⛶ Fullscreen")
        self.fs_btn.setToolTip("Toggle Fullscreen (F / F11 / Double-Click)")
        self.fs_btn.setFocusPolicy(Qt.FocusPolicy.NoFocus)
        self.fs_btn.setStyleSheet(
            "background-color: #262626; color: #EEEEEE; padding: 4px 10px; border-radius: 4px; "
            "font-size: 11px; font-weight: bold; border: 1px solid #3A3A3A;"
        )
        self.fs_btn.clicked.connect(self.toggle_fullscreen)

        controls_row.addWidget(self.play_btn)
        controls_row.addWidget(self.back10_btn)
        controls_row.addWidget(self.fwd10_btn)
        controls_row.addWidget(audio_label)
        controls_row.addWidget(self.audio_combo)
        controls_row.addWidget(sub_label)
        controls_row.addWidget(self.subtitle_combo)
        controls_row.addWidget(self.mute_btn)
        controls_row.addWidget(self.volume_slider)
        controls_row.addWidget(self.speed_combo)
        controls_row.addStretch(1)
        controls_row.addWidget(self.fs_btn)

        vbox.addLayout(slider_row)
        vbox.addLayout(controls_row)
        return bar

    def resizeEvent(self, event):
        super().resizeEvent(event)
        # Keep OSD badge centered on the video widget
        if hasattr(self, 'osd_badge') and hasattr(self, 'video_widget'):
            w = self.video_widget.width()
            h = self.video_widget.height()
            bw = self.osd_badge.width()
            bh = self.osd_badge.height()
            self.osd_badge.move((w - bw) // 2, (h - bh) // 2)

    def _bind_events(self):
        self.player.positionChanged.connect(self._on_position_changed)
        self.player.durationChanged.connect(self._on_duration_changed)
        self.player.playbackStateChanged.connect(self._on_playback_state_changed)
        self.player.tracksChanged.connect(self._populate_tracks)
        self.player.metaDataChanged.connect(self._populate_tracks)

    def _load_stream(self):
        if not self.stream_url:
            return
        url = QUrl(self.stream_url)
        self.player.setSource(url)
        self.player.play()

    def _populate_tracks(self):
        # 1. Multi-Audio Tracks
        audio_tracks = self.player.audioTracks()
        current_audio = self.player.activeAudioTrack()
        self.audio_combo.blockSignals(True)
        self.audio_combo.clear()
        if not audio_tracks:
            self.audio_combo.addItem("Default Audio", 0)
        else:
            for idx, track in enumerate(audio_tracks):
                lang = track.value(QMediaMetaData.Key.Language) or ""
                title = track.value(QMediaMetaData.Key.Title) or track.value(QMediaMetaData.Key.Description) or f"Track {idx+1}"
                name = f"{lang} - {title}".strip(" -")
                self.audio_combo.addItem(name if name else f"Audio {idx+1}", idx)
            if 0 <= current_audio < len(audio_tracks):
                self.audio_combo.setCurrentIndex(current_audio)
        self.audio_combo.blockSignals(False)

        # 2. Embedded Subtitle Tracks
        sub_tracks = self.player.subtitleTracks()
        current_sub = self.player.activeSubtitleTrack()
        self.subtitle_combo.blockSignals(True)
        self.subtitle_combo.clear()
        self.subtitle_combo.addItem("Subtitles: Off", -1)
        for idx, track in enumerate(sub_tracks):
            lang = track.value(QMediaMetaData.Key.Language) or ""
            title = track.value(QMediaMetaData.Key.Title) or track.value(QMediaMetaData.Key.Description) or f"Subtitle {idx+1}"
            name = f"{lang} - {title}".strip(" -")
            self.subtitle_combo.addItem(name if name else f"Sub {idx+1}", idx)
        if current_sub >= 0:
            combo_idx = self.subtitle_combo.findData(current_sub)
            if combo_idx >= 0:
                self.subtitle_combo.setCurrentIndex(combo_idx)
        else:
            self.subtitle_combo.setCurrentIndex(0)
        self.subtitle_combo.blockSignals(False)

    def _on_audio_track_changed(self, combo_idx: int):
        track_idx = self.audio_combo.itemData(combo_idx)
        if track_idx is not None and track_idx >= 0:
            self.player.setActiveAudioTrack(track_idx)
            self._show_osd(f"🔊 Audio: {self.audio_combo.currentText()}")

    def _on_subtitle_track_changed(self, combo_idx: int):
        track_idx = self.subtitle_combo.itemData(combo_idx)
        if track_idx is not None:
            self.player.setActiveSubtitleTrack(track_idx)
            self._show_osd(f"💬 {self.subtitle_combo.currentText()}")

    def _on_position_changed(self, position_ms: int):
        duration_ms = self.player.duration()
        if not self.is_user_scrubbing and duration_ms > 0:
            val = int((position_ms / duration_ms) * 1000)
            self.time_slider.setValue(val)
            self._update_time_label(position_ms, duration_ms)

    def _on_duration_changed(self, duration_ms: int):
        self._populate_tracks()
        self._update_time_label(self.player.position(), duration_ms)

    def _on_playback_state_changed(self, state):
        if state == QMediaPlayer.PlaybackState.PlayingState:
            self.play_btn.setText("❚❚ Pause")
        else:
            self.play_btn.setText("▶ Play")

    def _on_slider_pressed(self):
        self.is_user_scrubbing = True

    def _on_slider_released(self):
        self.is_user_scrubbing = False
        duration_ms = self.player.duration()
        if duration_ms > 0:
            fraction = self.time_slider.value() / 1000.0
            target_ms = int(duration_ms * fraction)
            self.player.setPosition(target_ms)

    def _on_volume_changed(self, val: int):
        self.audio_output.setVolume(val / 100.0)
        self.mute_btn.setText("🔇" if val == 0 else "🔊")
        self._show_osd(f"Volume: {val}%")

    def _on_speed_changed(self, text: str):
        try:
            rate = float(text.replace("x", ""))
            self.player.setPlaybackRate(rate)
            self._show_osd(f"Speed: {text}")
        except ValueError:
            pass

    def toggle_play_pause(self):
        if self.player.playbackState() == QMediaPlayer.PlaybackState.PlayingState:
            self.player.pause()
            self._show_osd("❚❚ Paused")
        else:
            self.player.play()
            self._show_osd("▶ Playing")

    def seek_relative(self, seconds: int):
        dur = self.player.duration()
        cur = self.player.position()
        target = max(0, min(dur, cur + seconds * 1000))
        self.player.setPosition(target)

        # Show Floating OSD Badge centered on video
        msg = f"+{seconds}s ⏩" if seconds > 0 else f"⏪ {abs(seconds)}s"
        self._show_osd(msg)

    def toggle_mute(self):
        is_muted = not self.audio_output.isMuted()
        self.audio_output.setMuted(is_muted)
        self.mute_btn.setText("🔇" if is_muted else "🔊")
        self._show_osd("Muted" if is_muted else "Unmuted")

    def toggle_fullscreen(self):
        if self.isFullScreen():
            # Exit Fullscreen -> Return to Docked Windowed Mode
            self.showNormal()
            self.fs_btn.setText("⛶ Fullscreen")
            self.top_bar.show()
            self.controls_bar.show()
            self.setCursor(Qt.CursorShape.ArrowCursor)
            self.video_widget.setCursor(Qt.CursorShape.ArrowCursor)
            self.idle_timer.stop()
        else:
            # Enter Fullscreen -> Video covers 100% of the entire display
            self.showFullScreen()
            self.fs_btn.setText("🗗 Exit Fullscreen")
            self._wake_controls()
            self.idle_timer.start(2500)

    def _on_idle_timeout(self):
        if not self.isFullScreen():
            return

        # Do not auto-hide while scrubbing timeline
        if self.is_user_scrubbing:
            self.idle_timer.start(1000)
            return

        # Do not auto-hide if mouse cursor is currently over top bar or controls bar
        cursor_pos = self.mapFromGlobal(QCursor.pos())
        if self.controls_bar.geometry().contains(cursor_pos) or self.top_bar.geometry().contains(cursor_pos):
            self.idle_timer.start(1500)
            return

        # Hide bars in fullscreen: video expands to 100% of the entire monitor
        self.top_bar.hide()
        self.controls_bar.hide()

        # Hide mouse cursor completely
        self.setCursor(Qt.CursorShape.BlankCursor)
        self.video_widget.setCursor(Qt.CursorShape.BlankCursor)

    def _wake_controls(self):
        if not self.controls_bar.isVisible():
            self.controls_bar.show()
        if not self.top_bar.isVisible():
            self.top_bar.show()

        self.setCursor(Qt.CursorShape.ArrowCursor)
        self.video_widget.setCursor(Qt.CursorShape.ArrowCursor)

        if self.isFullScreen():
            self.idle_timer.start(2500)

    def _show_osd(self, text: str):
        self.osd_badge.setText(text)
        self.osd_badge.adjustSize()
        w = self.video_widget.width()
        h = self.video_widget.height()
        bw = self.osd_badge.width()
        bh = self.osd_badge.height()
        self.osd_badge.move((w - bw) // 2, (h - bh) // 2)
        self.osd_badge.show()
        self.osd_badge.raise_()
        self.osd_timer.start(900)

    def _hide_osd(self):
        self.osd_badge.hide()

    def _update_time_label(self, cur_ms: int, tot_ms: int):
        cur_str = self._format_ms(cur_ms)
        tot_str = self._format_ms(tot_ms) if tot_ms > 0 else "--:--:--"
        self.time_label.setText(f"{cur_str} / {tot_str}")

    @staticmethod
    def _format_ms(ms: int) -> str:
        s = max(0, ms // 1000)
        h = s // 3600
        m = (s % 3600) // 60
        sec = s % 60
        return f"{h:02d}:{m:02d}:{sec:02d}"

    def _copy_url(self):
        QApplication.clipboard().setText(self.stream_url)
        self._show_osd("Stream URL Copied!")

    # Global Event Filter to guarantee Arrow keys and Mouse Move handling
    def eventFilter(self, watched, event):
        is_widget = isinstance(watched, QWidget)
        is_mine = (watched == self or (is_widget and self.isAncestorOf(watched)) or self.isActiveWindow())

        if is_mine:
            # 1. Wake controls on genuine mouse move in fullscreen
            if event.type() == QEvent.Type.MouseMove:
                if self.isFullScreen():
                    curr_pos = QCursor.pos()
                    if hasattr(self, '_last_mouse_pos') and self._last_mouse_pos == curr_pos:
                        pass
                    else:
                        self._last_mouse_pos = curr_pos
                        self._wake_controls()

            # 2. Double click on video toggles fullscreen
            elif event.type() == QEvent.Type.MouseButtonDblClick:
                if watched == self.video_widget:
                    self.toggle_fullscreen()
                    return True

            # 3. Intercept media keys
            elif event.type() == QEvent.Type.KeyPress:
                key = event.key()
                if key == Qt.Key.Key_F15:
                    return True  # Absorb simulated wake heartbeat key
                elif key == Qt.Key.Key_Left:
                    self.seek_relative(-10)
                    return True
                elif key == Qt.Key.Key_Right:
                    self.seek_relative(10)
                    return True
                elif key == Qt.Key.Key_Space:
                    self.toggle_play_pause()
                    return True
                elif key == Qt.Key.Key_Up:
                    new_vol = min(100, self.volume_slider.value() + 5)
                    self.volume_slider.setValue(new_vol)
                    return True
                elif key == Qt.Key.Key_Down:
                    new_vol = max(0, self.volume_slider.value() - 5)
                    self.volume_slider.setValue(new_vol)
                    return True
                elif key == Qt.Key.Key_M:
                    self.toggle_mute()
                    return True
                elif key in (Qt.Key.Key_F, Qt.Key.Key_F11):
                    self.toggle_fullscreen()
                    return True
                elif key == Qt.Key.Key_Escape:
                    if self.isFullScreen():
                        self.toggle_fullscreen()
                        return True

        return super().eventFilter(watched, event)

    def closeEvent(self, event):
        self.idle_timer.stop()
        self.osd_timer.stop()
        self.player.stop()
        try:
            QApplication.instance().removeEventFilter(self)
        except Exception:
            pass
        super().closeEvent(event)
