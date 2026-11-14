# End-to-End Local Streaming System

An end-to-end local media streaming solution consisting of:
1. **Android Streaming Server**: Picks a folder from device storage via SAF and exposes an HTTP API over mobile hotspot / Wi-Fi with PIN security and HTTP range requests (`206 Partial Content`).
2. **Python Desktop Player (PyQt6)**: Laptop player with native MKV, AVI, MP4, and WebM decoding via Qt 6.5+ FFmpeg engine, audio track selection, embedded subtitle selection, 10s arrow key skipping, and true edge-to-edge fullscreen with auto-hiding controls.

---

## 🌟 Architecture Overview

```
+------------------------------------------+          Wi-Fi / Hotspot           +------------------------------------------+
|          Android Mobile Device           | <================================> |           Laptop Desktop Client          |
|                                          |                                    |                                          |
|  [ SAF Folder Picker ]                   |                                    |  [ PythonStreamPlayer (PyQt6) ]          |
|          |                               |                                    |     - Server URL & PIN Connection Bar    |
|          v                               |                                    |     - Filterable Media Table (Video/Audio|
|  [ Media Files Map ]                     |                                    |          |                               |
|          |                               |                                    |          v                               |
|          v                               |                                    |  [ PlayerWindow ]                        |
|  [ StreamingHttpServer ]                 |                                    |     - Native MKV, AVI, MP4, WebM (FFmpeg)|
|     - Port 8080 (0.0.0.0)                |      GET /files?pin=XXXX           |     - Multi-Audio Track Selection        |
|     - PIN Security (Header / Query)      | ---------------------------------> |     - Embedded Subtitle Track Selection  |
|     - GET /files -> JSON metadata        | <--------------------------------- |     - 10s Skip via Arrow Keys (← / →)    |
|     - GET /stream/{id}                   |         JSON File Listing          |     - Edge-to-Edge Fullscreen (F / F11)  |
|        * HTTP 200 (Full content)         |                                    |     - Auto-Hiding Controls on Idle       |
|        * HTTP 206 (Range Seeking)        |      GET /stream/1?pin=XXXX        |     - Wake Controls on Mouse Movement    |
|                                          |      (Range: bytes=1048576-...)    |                                          |
|  [ Foreground Service & Notification ]   | <--------------------------------- |                                          |
|                                          |      HTTP 206 Partial Content      |                                          |
+------------------------------------------+ ---------------------------------> +------------------------------------------+
```

---

## 📁 Repository Structure

```
LocalStream/
├── LocalStreamServer.apk            # Pre-compiled Android APK (ready to install)
├── run-desktop-player.bat           # 1-Click launcher for Python Desktop Player
├── run-desktop-player.ps1           # PowerShell launcher for Python Desktop Player
├── README.md                        # Documentation
│
├── PythonStreamPlayer/              # High-Performance Python Client (PyQt6)
│   ├── main.py                      # Connection manager & searchable file table UI
│   ├── player_window.py             # Video player with MKV/AVI/MP4, audio/sub tracks, fullscreen
│   ├── api_client.py                # HTTP REST client with PIN auth & URL normalization
│   ├── requirements.txt             # PyQt6>=6.5.0, requests>=2.28.0
│   ├── test_client.py               # Automated test suite (8 tests)
│   └── README.md
│
├── AndroidStreamServer/             # Android Application
│   ├── app/
│   │   ├── src/main/java/com/localstream/server/
│   │   │   ├── model/MediaFile.kt                 # Data model with formatting & type detection
│   │   │   ├── server/StreamingHttpServer.kt      # Multi-threaded HTTP server with Range & PIN support
│   │   │   ├── service/StreamingService.kt        # Foreground service with persistent notification
│   │   │   ├── util/StorageHelper.kt              # Storage Access Framework folder scanning
│   │   │   ├── util/NetworkUtils.kt               # Hotspot & Wi-Fi IP address resolution
│   │   │   ├── ui/StreamingViewModel.kt           # Reactive state management
│   │   │   └── MainActivity.kt                    # Jetpack Compose Material 3 UI
│   │   ├── src/test/java/                         # Unit tests for HTTP Range Request parsing
│   │   └── AndroidManifest.xml
│   ├── build.gradle.kts
│   └── LocalStreamServer.apk
│
└── DesktopStreamPlayer/             # Optional Java Desktop Client
    └── ...
```

---

## 🚀 Running Guide

### Step 1: Set up the Android Device
1. **Install the App**:
   Transfer and install `LocalStreamServer.apk` on your Android phone, or run:
   ```cmd
   adb install -r LocalStreamServer.apk
   ```
2. **Turn on Mobile Hotspot**:
   - Go to **Settings > Network & internet > Hotspot & tethering > Wi-Fi hotspot** and turn it ON.
   - Alternatively, connect both your Android phone and laptop to the same Wi-Fi router.
3. **Select Folder & Start Server**:
   - Open **Local Stream Server**.
   - Tap **"Pick Folder"** and choose any folder containing movies or music.
   - Tap **"Start Server"**. The app displays:
     - **Server IP**: e.g., `http://192.168.43.1:8080`
     - **PIN Token**: e.g., `8492`

---

### Step 2: Run the Laptop Player (Python)
1. Connect your laptop Wi-Fi to the phone's Hotspot (or same Wi-Fi network).
2. Double-click `run-desktop-player.bat` (or run `run-desktop-player.ps1`).
3. Enter the Server URL and 4-digit PIN, then click **Connect**.
4. Browse or filter the file list, and click **▶ Play** (or double-click any row).

---

## 🎮 Player Controls & Keyboard Shortcuts

| Shortcut / Control | Action |
|---|---|
| **Left Arrow (`←`)** | Skip backward 10 seconds (with visual OSD feedback) |
| **Right Arrow (`→`)** | Skip forward 10 seconds (with visual OSD feedback) |
| **Spacebar** | Toggle Play / Pause |
| **F** or **F11** | Toggle True Edge-to-Edge Fullscreen |
| **Escape** | Exit Fullscreen |
| **Double Click (Video)** | Toggle Fullscreen |
| **Mouse Idle in Fullscreen** | Controls & cursor auto-hide after 2.5s (pure 100% video coverage) |
| **Mouse Move** | Controls and cursor instantly reappear |
| **🔊 Audio Track Dropdown** | Switch between audio tracks (e.g. Tamil, English, Hindi) |
| **💬 Subtitles Dropdown** | Switch subtitle tracks (SRT, ASS, SubStation) or turn Off |
| **Up Arrow (`↑`)** | Increase volume (+5%) |
| **Down Arrow (`↓`)** | Decrease volume (-5%) |
| **M** | Toggle Mute |
| **Speed Dropdown** | Adjust playback speed (0.5x, 0.75x, 1.0x, 1.25x, 1.5x, 2.0x) |
