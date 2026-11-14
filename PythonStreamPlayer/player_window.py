import sys
import webbrowser
from PyQt6.QtCore import Qt, QUrl, QTimer, QTime, QPoint, QEvent
from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QPushButton, QSlider, QLabel,
    QComboBox, QApplication
)
from PyQt6.QtGui import QIcon, QKeySequence, QClipboard, QCursor
from PyQt6.QtMultimedia import QMediaPlayer, QAudioOutput, QMediaMetaData
from PyQt6.QtMultimediaWidgets import QVideoWidget


class PlayerWindow(QWidget):
    """
    Dedicated High-Performance Video Player Window:
    - Native MKV, AVI, MP4, and WebM hardware-accelerated playback via Qt 6.5+ FFmpeg backend.
    - Multi-audio track selection (Tamil, Hindi, English, etc.).
    - Embedded subtitle track selection (SRT, ASS, SubStation, etc.).
    - 10-second skip via Left / Right arrow keys with centered OSD badge feedback.
    - True edge-to-edge fullscreen where video covers 100% of the display.
    - Automatic auto-hiding controls and mouse cursor on idle in fullscreen.
    - Controls seamlessly reappear when the mouse moves.
    """

    def __init__(self, file_data: dict, stream_url: str):
        super().__init__()
        self.file_data = file_data
        self.stream_url = stream_url

        self.setWindowTitle(f"Playing: {file_data.get('name', 'Media')} [{file_data.get('size_formatted', '')}]")
        self.resize(1080, 680)
        self.setMinimumSize(720, 480)
        self.setStyleSheet("background-color: #000000; color: #FFFFFF;")

        # Enable mouse tracking on the main window
        self.setMouseTracking(True)

        # Player Engine
        self.player = QMediaPlayer(self)
        self.audio_output = QAudioOutput(self)
        self.player.setAudioOutput(self.audio_output)
        self.audio_output.setVolume(0.85)

        # Video Widget (Layer 0: fills 100% of the window)
        self.video_widget = QVideoWidget(self)
        self.video_widget.setMouseTracking(True)
        self.video_widget.setStyleSheet("background-color: #000000;")
        self.player.setVideoOutput(self.video_widget)

        # Auto-hide timer for Fullscreen (2.5 seconds idle timeout)
        self.idle_timer = QTimer(self)
        self.idle_timer.setInterval(2500)
        self.idle_timer.timeout.connect(self._on_idle_timeout)

        # OSD Badge Timer
        self.osd_timer = QTimer(self)
        self.osd_timer.setSingleShot(True)
        self.osd_timer.setInterval(900)
        self.osd_timer.timeout.connect(self._hide_osd)

        self.is_user_scrubbing = False

        self._build_overlays()
        self._bind_events()
        self._load_stream()

        # Install application-level event filter so arrow keys and mouse moves are reliably caught
        QApplication.instance().installEventFilter(self)

    def _build_overlays(self):
        # 1. Top Bar Overlay
        self.top_bar = QWidget(self)
        self.top_bar.setStyleSheet("""
            QWidget {
                background-color: rgba(14, 14, 14, 0.90);
                border-bottom: 1px solid rgba(255, 255, 255, 0.12);
            }
        """)
        top_layout = QHBoxLayout(self.top_bar)
        top_layout.setContentsMargins(16, 8, 16, 8)
        top_layout.setSpacing(12)

        self.title_label = QLabel(self.file_data.get("name", "Unknown File"), self.top_bar)
        self.title_label.setStyleSheet("font-weight: bold; font-size: 14px; color: #FFFFFF; background: transparent;")

        mime_badge = QLabel(self.file_data.get("mimeType", "video/*"), self.top_bar)
        mime_badge.setStyleSheet(
            "background-color: #2979FF; color: white; padding: 3px 8px; border-radius: 4px; "
            "font-size: 11px; font-weight: bold;"
        )

        size_label = QLabel(self.file_data.get("size_formatted", ""), self.top_bar)
        size_label.setStyleSheet("color: #AAAAAA; font-size: 12px; background: transparent;")

        copy_btn = QPushButton("📋 Copy Stream URL", self.top_bar)
        copy_btn.setFocusPolicy(Qt.FocusPolicy.NoFocus)
        copy_btn.setStyleSheet(
            "background-color: #262626; color: #DDDDDD; padding: 5px 12px; border-radius: 4px; "
            "font-size: 11px; border: 1px solid #3A3A3A;"
        )
        copy_btn.clicked.connect(self._copy_url)

        top_layout.addWidget(self.title_label)
        top_layout.addWidget(mime_badge)
        top_layout.addWidget(size_label)
        top_layout.addStretch(1)
        top_layout.addWidget(copy_btn)

        # 2. Bottom Controls Bar Overlay
        self.controls_bar = QWidget(self)
        self.controls_bar.setStyleSheet("""
            QWidget {
                background-color: rgba(14, 14, 14, 0.92);
                border-top: 1px solid rgba(255, 255, 255, 0.12);
            }
            QLabel {
                background: transparent;
            }
        """)
        controls_layout = QVBoxLayout(self.controls_bar)
        controls_layout.setContentsMargins(16, 8, 16, 12)
        controls_layout.setSpacing(8)

        # Row 1: Timeline Seek Slider + Time Label
        slider_row = QHBoxLayout()
        slider_row.setSpacing(12)

        self.time_slider = QSlider(Qt.Orientation.Horizontal, self.controls_bar)
        self.time_slider.setRange(0, 1000)
        self.time_slider.setFocusPolicy(Qt.FocusPolicy.NoFocus)
        self.time_slider.setStyleSheet("""
            QSlider { background: transparent; height: 18px; }
            QSlider::groove:horizontal { height: 6px; background: #333333; border-radius: 3px; }
            QSlider::sub-page:horizontal { background: #2979FF; border-radius: 3px; }
            QSlider::handle:horizontal {
                background: #FFFFFF;
                border: 2px solid #2979FF;
                width: 14px;
                height: 14px;
                margin-top: -5px;
                margin-bottom: -5px;
                border-radius: 7px;
            }
            QSlider::handle:horizontal:hover {
                background: #2979FF;
                border: 2px solid #FFFFFF;
                width: 16px;
                height: 16px;
                margin-top: -6px;
                margin-bottom: -6px;
                border-radius: 8px;
            }
        """)
        self.time_slider.sliderPressed.connect(self._on_slider_pressed)
        self.time_slider.sliderReleased.connect(self._on_slider_released)

        self.time_label = QLabel("00:00:00 / 00:00:00", self.controls_bar)
        self.time_label.setStyleSheet("color: #CCCCCC; font-family: monospace; font-size: 12px; min-width: 145px;")
        self.time_label.setAlignment(Qt.AlignmentFlag.AlignRight | Qt.AlignmentFlag.AlignVCenter)

        slider_row.addWidget(self.time_slider, stretch=1)
        slider_row.addWidget(self.time_label)

        # Row 2: Play/Pause, 10s Skips, Audio track, Subtitles, Volume, Speed, Fullscreen
        buttons_row = QHBoxLayout()
        buttons_row.setSpacing(10)

        self.play_btn = QPushButton("▶ Play", self.controls_bar)
        self.play_btn.setFocusPolicy(Qt.FocusPolicy.NoFocus)
        self.play_btn.setStyleSheet(
            "background-color: #2979FF; color: white; font-weight: bold; padding: 6px 16px; "
            "border-radius: 4px; font-size: 12px;"
        )
        self.play_btn.clicked.connect(self.toggle_play_pause)

        self.back10_btn = QPushButton("⏪ -10s", self.controls_bar)
        self.back10_btn.setToolTip("Skip 10 seconds backward (Left Arrow)")
        self.back10_btn.setFocusPolicy(Qt.FocusPolicy.NoFocus)
        self.back10_btn.setStyleSheet(
            "background-color: #262626; color: #EEEEEE; padding: 6px 12px; border-radius: 4px; "
            "font-size: 11px; border: 1px solid #3A3A3A;"
        )
        self.back10_btn.clicked.connect(lambda: self.seek_relative(-10))

        self.fwd10_btn = QPushButton("+10s ⏩", self.controls_bar)
        self.fwd10_btn.setToolTip("Skip 10 seconds forward (Right Arrow)")
        self.fwd10_btn.setFocusPolicy(Qt.FocusPolicy.NoFocus)
        self.fwd10_btn.setStyleSheet(
            "background-color: #262626; color: #EEEEEE; padding: 6px 12px; border-radius: 4px; "
            "font-size: 11px; border: 1px solid #3A3A3A;"
        )
        self.fwd10_btn.clicked.connect(lambda: self.seek_relative(10))

        # Audio Track Dropdown
        audio_label = QLabel("🔊 Audio:", self.controls_bar)
        audio_label.setStyleSheet("color: #AAAAAA; font-size: 11px;")
        self.audio_combo = QComboBox(self.controls_bar)
        self.audio_combo.setFocusPolicy(Qt.FocusPolicy.NoFocus)
        self.audio_combo.setMinimumWidth(130)
        self.audio_combo.setStyleSheet("""
            QComboBox { background-color: #262626; color: white; padding: 4px 8px; border-radius: 4px; font-size: 11px; border: 1px solid #3A3A3A; }
            QComboBox QAbstractItemView { background-color: #1E1E1E; color: white; selection-background-color: #2979FF; }
        """)
        self.audio_combo.currentIndexChanged.connect(self._on_audio_track_changed)

        # Subtitle Track Dropdown
        sub_label = QLabel("💬 Subtitles:", self.controls_bar)
        sub_label.setStyleSheet("color: #AAAAAA; font-size: 11px;")
        self.subtitle_combo = QComboBox(self.controls_bar)
        self.subtitle_combo.setFocusPolicy(Qt.FocusPolicy.NoFocus)
        self.subtitle_combo.setMinimumWidth(130)
        self.subtitle_combo.setStyleSheet("""
            QComboBox { background-color: #262626; color: white; padding: 4px 8px; border-radius: 4px; font-size: 11px; border: 1px solid #3A3A3A; }
            QComboBox QAbstractItemView { background-color: #1E1E1E; color: white; selection-background-color: #2979FF; }
        """)
        self.subtitle_combo.currentIndexChanged.connect(self._on_subtitle_track_changed)

        # Volume Controls
        self.mute_btn = QPushButton("🔊", self.controls_bar)
        self.mute_btn.setFocusPolicy(Qt.FocusPolicy.NoFocus)
        self.mute_btn.setStyleSheet("background: transparent; font-size: 14px; border: none;")
        self.mute_btn.clicked.connect(self.toggle_mute)

        self.volume_slider = QSlider(Qt.Orientation.Horizontal, self.controls_bar)
        self.volume_slider.setRange(0, 100)
        self.volume_slider.setValue(85)
        self.volume_slider.setFixedWidth(75)
        self.volume_slider.setFocusPolicy(Qt.FocusPolicy.NoFocus)
        self.volume_slider.setStyleSheet("""
            QSlider { background: transparent; height: 16px; }
            QSlider::groove:horizontal { height: 4px; background: #333333; border-radius: 2px; }
            QSlider::sub-page:horizontal { background: #2979FF; border-radius: 2px; }
            QSlider::handle:horizontal { background: white; width: 10px; margin-top: -3px; margin-bottom: -3px; border-radius: 5px; }
        """)
        self.volume_slider.valueChanged.connect(self._on_volume_changed)

        # Speed Dropdown
        speed_label = QLabel("Speed:", self.controls_bar)
        speed_label.setStyleSheet("color: #888888; font-size: 11px;")
        self.speed_combo = QComboBox(self.controls_bar)
        self.speed_combo.setFocusPolicy(Qt.FocusPolicy.NoFocus)
        self.speed_combo.addItems(["0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "2.0x"])
        self.speed_combo.setCurrentText("1.0x")
        self.speed_combo.setStyleSheet("""
            QComboBox { background-color: #262626; color: white; font-size: 11px; padding: 3px 6px; border-radius: 4px; border: 1px solid #3A3A3A; }
            QComboBox QAbstractItemView { background-color: #1E1E1E; color: white; selection-background-color: #2979FF; }
        """)
        self.speed_combo.currentTextChanged.connect(self._on_speed_changed)

        # Fullscreen Toggle Button
        self.fs_btn = QPushButton("⛶ Fullscreen", self.controls_bar)
        self.fs_btn.setToolTip("Toggle Fullscreen (F / F11 / Double-Click)")
        self.fs_btn.setFocusPolicy(Qt.FocusPolicy.NoFocus)
        self.fs_btn.setStyleSheet(
            "background-color: #262626; color: #EEEEEE; padding: 6px 14px; border-radius: 4px; "
            "font-size: 11px; font-weight: bold; border: 1px solid #3A3A3A;"
        )
        self.fs_btn.clicked.connect(self.toggle_fullscreen)

        buttons_row.addWidget(self.play_btn)
        buttons_row.addWidget(self.back10_btn)
        buttons_row.addWidget(self.fwd10_btn)
        buttons_row.addWidget(audio_label)
        buttons_row.addWidget(self.audio_combo)
        buttons_row.addWidget(sub_label)
        buttons_row.addWidget(self.subtitle_combo)
        buttons_row.addWidget(self.mute_btn)
        buttons_row.addWidget(self.volume_slider)
        buttons_row.addWidget(speed_label)
        buttons_row.addWidget(self.speed_combo)
        buttons_row.addStretch(1)
        buttons_row.addWidget(self.fs_btn)

        controls_layout.addLayout(slider_row)
        controls_layout.addLayout(buttons_row)

        # 3. Center OSD Badge (Floating Notification)
        self.osd_badge = QLabel(self)
        self.osd_badge.setStyleSheet(
            "background-color: rgba(0, 0, 0, 0.85); color: #FFFFFF; font-size: 22px; "
            "font-weight: bold; padding: 12px 28px; border-radius: 10px; border: 2px solid #2979FF;"
        )
        self.osd_badge.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.osd_badge.hide()

    def resizeEvent(self, event):
        super().resizeEvent(event)
        w = self.width()
        h = self.height()

        # Video fills 100% of the entire window/screen
        self.video_widget.setGeometry(0, 0, w, h)

        # Top Bar
        top_h = 50
        self.top_bar.setGeometry(0, 0, w, top_h)
        self.top_bar.raise_()

        # Bottom Controls Bar
        ctl_h = 96
        self.controls_bar.setGeometry(0, h - ctl_h, w, ctl_h)
        self.controls_bar.raise_()

        # Center OSD Badge
        if hasattr(self, 'osd_badge'):
            bw = self.osd_badge.width()
            bh = self.osd_badge.height()
            self.osd_badge.move((w - bw) // 2, (h - bh) // 2)
            self.osd_badge.raise_()

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

        # Show Floating OSD Badge
        msg = f"+{seconds}s ⏩" if seconds > 0 else f"⏪ {abs(seconds)}s"
        self._show_osd(msg)

    def toggle_mute(self):
        is_muted = not self.audio_output.isMuted()
        self.audio_output.setMuted(is_muted)
        self.mute_btn.setText("🔇" if is_muted else "🔊")
        self._show_osd("Muted" if is_muted else "Unmuted")

    def toggle_fullscreen(self):
        if self.isFullScreen():
            # Exit Fullscreen
            self.showNormal()
            self.fs_btn.setText("⛶ Fullscreen")
            self._wake_controls()
            self.idle_timer.stop()
        else:
            # Enter Fullscreen: video covers 100% of the entire display
            self.showFullScreen()
            self.fs_btn.setText("🗗 Exit Fullscreen")
            self._wake_controls()
            self.idle_timer.start(2500)

    def _on_idle_timeout(self):
        if not self.isFullScreen():
            return

        # Do not auto-hide while scrubbing the seek bar
        if self.is_user_scrubbing:
            self.idle_timer.start(1000)
            return

        # Do not auto-hide if mouse cursor is currently over top bar or controls bar
        cursor_pos = self.mapFromGlobal(QCursor.pos())
        if self.controls_bar.geometry().contains(cursor_pos) or self.top_bar.geometry().contains(cursor_pos):
            self.idle_timer.start(1500)
            return

        # Hide overlay controls completely: pure edge-to-edge video
        self.top_bar.hide()
        self.controls_bar.hide()

        # Hide mouse cursor completely
        self.setCursor(Qt.CursorShape.BlankCursor)
        self.video_widget.setCursor(Qt.CursorShape.BlankCursor)

    def _wake_controls(self):
        if not self.controls_bar.isVisible():
            self.controls_bar.show()
            self.controls_bar.raise_()
        if not self.top_bar.isVisible():
            self.top_bar.show()
            self.top_bar.raise_()

        self.setCursor(Qt.CursorShape.ArrowCursor)
        self.video_widget.setCursor(Qt.CursorShape.ArrowCursor)

        if self.isFullScreen():
            self.idle_timer.start(2500)

    def _show_osd(self, text: str):
        self.osd_badge.setText(text)
        self.osd_badge.adjustSize()
        w = self.width()
        h = self.height()
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
        # Safely check if the event belongs to this player window or its child widgets
        is_widget = isinstance(watched, QWidget)
        is_mine = (watched == self or (is_widget and self.isAncestorOf(watched)) or self.isActiveWindow())

        if is_mine:
            # 1. Wake controls on ANY mouse move
            if event.type() == QEvent.Type.MouseMove:
                self._wake_controls()

            # 2. Double click on video toggles fullscreen
            elif event.type() == QEvent.Type.MouseButtonDblClick:
                if watched == self.video_widget:
                    self.toggle_fullscreen()
                    return True

            # 3. Handle Arrow Keys, Space, and Shortcuts globally so focus doesn't swallow them
            elif event.type() == QEvent.Type.KeyPress:
                key = event.key()
                if key == Qt.Key.Key_Left:
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
