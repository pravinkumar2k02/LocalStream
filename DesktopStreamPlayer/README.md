# Desktop Stream Player (Java Client)

A modern Java desktop media player for browsing and streaming media files served by the Android Local Stream Server over a device hotspot or local Wi-Fi.

## Key Features

- **Native MKV, AVI, MP4, and WebM Playback**:
  - High-performance direct video rendering backed by LibVLC and JavaFX `PixelBuffer`.
  - Plays complex MKV videos (like multi-gigabyte 1080p/4K HDR, x264, x265/HEVC), AVI, MP4, WebM, and other formats natively without external conversion.
- **Audio Track Selection**:
  - Automatically identifies all audio tracks embedded in the stream (e.g. Tamil, Korean, English, Hindi).
  - One-click track switching on the fly via the `🔊 Audio` dropdown menu.
- **Subtitle Track Selection**:
  - Automatically discovers embedded subtitle tracks (ESub, SRT, ASS/SSA, VobSub).
  - Toggle or switch subtitle tracks instantly using the `💬 Subtitles` dropdown menu.
- **10-Second Skip with Arrow Keys & Global Shortcuts**:
  - **Right Arrow (`→`)**: Skips forward 10 seconds (`+10s ⏩`).
  - **Left Arrow (`←`)**: Skips backward 10 seconds (`⏪ -10s`).
  - **Spacebar**: Toggle Play / Pause.
  - **Up Arrow (`↑`) / Down Arrow (`↓`)**: Adjust volume by ±5%.
  - **M**: Toggle Mute (`🔊` / `🔇`).
  - **F / F11**: Toggle Fullscreen.
  - Features an On-Screen Display (OSD) notification badge for instant visual feedback.
- **Timeline Scrubbing & Range Seeking**:
  - Drag or click anywhere on the timeline slider.
  - Sends HTTP Range requests (`Range: bytes=start-end`) to the Android server to seek immediately without downloading the whole file.

---

## Running the Application

### 1-Click Launch (Windows)
Double-click `run-desktop-player.bat` in the repository root.

### PowerShell
```powershell
.\run-desktop-player.ps1
```

### Gradle
```bash
cd DesktopStreamPlayer
.\gradlew.bat run
```
