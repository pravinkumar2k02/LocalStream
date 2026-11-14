# Python Stream Player (PyQt6 Desktop Client)

A modern, high-performance local video streaming player built with Python and **PyQt6 (Qt 6.5+ Multimedia with embedded FFmpeg backend)**.

---

## 🌟 Key Features

1. **Native MKV, AVI, MP4, and WebM Container & Codec Support**:
   - Uses Qt 6's native FFmpeg decoder pipeline.
   - Plays Matroska (`.mkv`), AVI (`.avi`), MP4 (`.mp4`), WebM (`.webm`), and QuickTime (`.mov`) files without requiring VLC or external software to be installed on the laptop.
2. **Audio Track Selection**:
   - Dynamic audio stream discovery.
   - Switch between multiple audio languages/dub tracks (e.g. Tamil, English, Hindi, Commentary) on the fly via the `🔊 Audio:` dropdown.
3. **Embedded Subtitle Track Selection**:
   - Embedded subtitle stream discovery (SRT, ASS, SubStation, VobSub).
   - Switch between subtitle languages or turn subtitles off via the `💬 Subtitles:` dropdown.
4. **10-Second Skip with Arrow Keys & OSD**:
   - `Left Arrow` (←): Skip 10 seconds backward.
   - `Right Arrow` (→): Skip 10 seconds forward.
   - Visual OSD badge (`⏪ 10s` / `+10s ⏩`) appears centered over the video for immediate feedback.
   - Global event filter ensures arrow keys work reliably even if sliders or buttons have focus.
5. **True Edge-to-Edge Fullscreen with Auto-Hiding Controls**:
   - In Fullscreen mode (`F`, `F11`, double-click, or `⛶ Fullscreen` button), the video covers **100% of the display** (no window borders, no title bar, no Windows taskbar).
   - Controls and top title bar float as a modern semi-transparent overlay over the video.
   - **Auto-Hide on Idle**: After 2.5 seconds of mouse inactivity, all controls and the mouse cursor automatically hide completely, providing an uninterrupted cinematic experience.
   - **Wake on Movement**: Moving the mouse even 1 pixel instantly reveals the controls and restores the cursor.
   - Controls remain visible while scrubbing the timeline slider or hovering over buttons.
6. **Built-in Controls**:
   - Play / Pause (Button or `Spacebar`).
   - Mute / Unmute (`M` key or `🔊` button).
   - Volume slider with percentage OSD badge (`Up` / `Down` arrow keys).
   - Playback speed selector (`0.5x`, `0.75x`, `1.0x`, `1.25x`, `1.5x`, `2.0x`).
   - Copy Stream URL button.

---

## ⌨️ Keyboard Shortcuts

| Shortcut | Action |
|---|---|
| **Left Arrow (`←`)** | Skip backward 10 seconds (with OSD) |
| **Right Arrow (`→`)** | Skip forward 10 seconds (with OSD) |
| **Spacebar** | Toggle Play / Pause |
| **F** or **F11** | Toggle Edge-to-Edge Fullscreen |
| **Escape** | Exit Fullscreen |
| **Double Click (Video)** | Toggle Edge-to-Edge Fullscreen |
| **Up Arrow (`↑`)** | Volume +5% |
| **Down Arrow (`↓`)** | Volume -5% |
| **M** | Toggle Mute |

---

## 🚀 Running the Player

### Prerequisites
- Python 3.10+ (Tested with Python 3.14)
- Pip

### Install Dependencies
```bash
pip install -r requirements.txt
```

### Launch
```bash
python main.py
```
Or use the provided launcher script:
```bat
..\run-desktop-player.bat
```
