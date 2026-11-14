# Desktop Stream Player (Java Client)

A modern JavaFX desktop client for browsing and streaming media files served by the Android Local Stream Server over a device hotspot or local Wi-Fi.

## Features

- **Multi-Window Architecture**:
  - **Main Window**: Connection configuration (Server URL & PIN token), live connection status, file filter/search bar, and interactive file table.
  - **Player Window**: Dedicated playback window with custom media controls.
- **Media Controls**:
  - **Play / Pause** toggle (Spacebar shortcut).
  - **Seek Slider**: Enables scrubbing to any point in the media file using HTTP Range requests (`Range: bytes=start-end`) supported by the Android server.
  - **Skip Buttons**: `-10s` and `+10s` quick seek.
  - **Volume Slider & Mute Toggle**: Volume control with visual icon state.
  - **Playback Speed Selector**: `0.5x`, `0.75x`, `1.0x`, `1.25x`, `1.5x`, `2.0x`.
  - **Fullscreen**: Toggleable via button or `F11` / `F` key.
  - **Time Display**: Formatted elapsed and total duration (`mm:ss / mm:ss`).
- **Format Support & Graceful Fallback**:
  - **MP4 (H.264/AAC)**: Hardware-accelerated native playback via JavaFX `MediaPlayer` & `MediaView`.
  - **WebM & Unsupported Codecs**:
    - Automatic detection and graceful fallback handling if the native JavaFX pipeline cannot decode the container/codec.
    - **One-Click Browser Playback**: Launches Chrome / Edge / Firefox directly to the stream URL (with PIN), taking advantage of modern browser WebM (VP8/VP9/AV1) engines and range seeking.
    - **Open in VLC Player**: One-click launch in VLC Media Player.
    - **Embedded HTML5 View**: Loads the stream inside an embedded WebKit/HTML5 player.
    - **Copy Stream URL**: Ready to paste into external players.
    - **Download File**: Save a local copy.

---

## Running the Application

### Option A: Using the 1-Click Launch Script
Double-click `run-desktop-player.bat` from the root folder, or in PowerShell:
```powershell
.\run-desktop-player.ps1
```

### Option B: Using Gradle
From the `DesktopStreamPlayer` directory:
```bash
.\gradlew.bat run
```

### Option C: Using Standalone Distribution
From the root folder:
```cmd
.\DesktopStreamPlayer\dist\DesktopStreamPlayer\bin\DesktopStreamPlayer.bat
```
