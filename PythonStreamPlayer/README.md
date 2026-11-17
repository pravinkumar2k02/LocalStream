# Python Stream Player (PyQt6 Desktop Client)

A modern, high-performance local video streaming player built with Python and **PyQt6 (Qt 6.5+ Multimedia with embedded FFmpeg backend)**.

---

## 🌟 Key Features

1. **Keep Screen Awake (Anti-Sleep & Timeout Prevention)**:
   - Prevents your laptop screen from turning off, dimming, locking, or sleeping due to Windows power settings or timeout policies.
   - **Dual-Protection Engine**:
     - **Windows Power API**: Engages `SetThreadExecutionState(ES_CONTINUOUS | ES_DISPLAY_REQUIRED | ES_SYSTEM_REQUIRED)`.
     - **Simulated Background Heartbeat**: Periodically emits harmless virtual input (`VK_F15` key and sub-pixel mouse nudge) in a background thread to reset corporate screensavers and OS idle timers.
   - Stays active automatically from the moment you open the app until you close it.
   - Includes an on-screen toggle (`Keep Screen Awake`) with real-time status.

2. **Native MKV, AVI, MP4, and WebM Container & Codec Support**:
   - Uses Qt 6's native FFmpeg decoder pipeline.
   - Plays Matroska (`.mkv`), AVI (`.avi`), MP4 (`.mp4`), WebM (`.webm`), and QuickTime (`.mov`) files without requiring VLC or external software to be installed on the laptop.

3. **Audio Track Selection**:
   - Dynamic audio stream discovery.
   - Switch between multiple audio languages/dub tracks (e.g. Tamil, English, Hindi, Commentary) on the fly via the `🔊 Audio:` dropdown.

4. **Embedded Subtitle Track Selection**:
   - Embedded subtitle stream discovery (SRT, ASS, SubStation, VobSub).
   - Switch between subtitle languages or turn subtitles off via the `💬 Subtitles:` dropdown.

5. **10-Second Skip with Arrow Keys & OSD**:
   - `Left Arrow` (←): Skip 10 seconds backward.
   - `Right Arrow` (→): Skip 10 seconds forward.
   - Visual OSD badge (`⏪ 10s` / `+10s ⏩`) appears centered over the video for immediate feedback.
   - Global event filter ensures arrow keys work reliably even if sliders or buttons have focus.

6. **Dedicated Docked Controls in Small Screen & Edge-to-Edge Fullscreen**:
   - **Small Screen (Windowed)**: Controls are cleanly docked beneath the video with their own dedicated layout space. Hardware video frames can never overlap or hide controls.
   - **Fullscreen**: Video expands to **100% of the entire display**. Controls and cursor auto-hide after 2.5s of mouse idle, and wake up instantly when the mouse moves.

7. **Built-in Controls**:
   - Play / Pause (Button or `Spacebar`).
   - Mute / Unmute (`M` key or `🔊` button).
   - Volume slider with percentage OSD badge (`Up` / `Down` arrow keys).
   - Playback speed selector (`0.5x`, `0.75x`, `1.0x`, `1.25x`, `1.5x`, `2.0x`).
   - Copy Stream URL button.

8. **Zero-Friction QR Code Pairing & Cryptographic Token Auth**:
   - Generates dynamic 256-bit session tokens (`secrets.token_hex(32)`).
   - Click **"📱 Pair (QR)"** to display a scannable QR code on screen.
   - The Android app scans the QR code with its built-in camera scanner, instantly authorizing this laptop session without disturbing other streaming clients.
   - Automatically supports **TLS 1.3 / HTTPS** streams with self-signed certificate acceptance.

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

### Option 1: Standalone Portable Executable (Recommended - Zero Installation)
1. Download `LocalStreamPlayer.exe` from [GitHub Releases](https://github.com/pravinkumar2k02/LocalStream/releases).
2. Double-click `LocalStreamPlayer.exe` to run.
   - No Python, FFmpeg, or dependencies needed. Works on any Windows 10/11 machine.

### Option 2: Run from Python Source
#### Prerequisites
- Python 3.10+ (Tested with Python 3.14)
- Pip

#### Install Dependencies
```bash
pip install -r requirements.txt
```

#### Launch
```bash
python main.py
```
Or use the provided launcher script:
```bat
..\run-desktop-player.bat
```
