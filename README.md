# End-to-End Local Streaming System

An end-to-end local media streaming solution consisting of:
1. **Android Streaming Server**: Picks a folder from device storage and exposes an HTTP API over the mobile hotspot / Wi-Fi with PIN security and HTTP range requests.
2. **Java Desktop Player**: Connects to the Android device, lists available files, and plays media with built-in playback controls (play, pause, volume, range-based seeking) and graceful fallback for WebM and unsupported codecs.

---

## Architecture Overview

```
+------------------------------------------+          Wi-Fi / Hotspot           +------------------------------------------+
|          Android Mobile Device           | <================================> |           Laptop Desktop Client          |
|                                          |                                    |                                          |
|  [ SAF Folder Picker ]                   |                                    |  [ LocalStreamApp (JavaFX) ]             |
|          |                               |                                    |     - IP & PIN Input Connection Bar      |
|          v                               |                                    |     - Search & Filterable File Table     |
|  [ Media Files Map ]                     |                                    |          |                               |
|          |                               |                                    |          v                               |
|          v                               |                                    |  [ PlayerWindow ]                        |
|  [ StreamingHttpServer ]                 |                                    |     - Built-in Controls (Play/Pause,     |
|     - Port 8080 (0.0.0.0)                |      GET /files?pin=XXXX           |       Volume, Rate, Fullscreen)          |
|     - PIN Security (Query / Header)      | ---------------------------------> |     - Seek Slider -> HTTP Range Requests |
|     - GET /files -> JSON metadata        | <--------------------------------- |     - Format Detection & Fallback:       |
|     - GET /stream/{id}                   |         JSON File Listing          |        * MP4 -> JavaFX MediaPlayer       |
|        * HTTP 200 (Full content)         |                                    |        * WebM / Unsupported Codecs ->    |
|        * HTTP 206 (Range Seeking)        |      GET /stream/1?pin=XXXX        |          Browser Launch / VLC / HTML5    |
|                                          |      (Range: bytes=1048576-...)    |                                          |
|  [ Foreground Service & Notification ]   | <--------------------------------- |                                          |
|                                          |      HTTP 206 Partial Content      |                                          |
+------------------------------------------+ ---------------------------------> +------------------------------------------+
```

---

## Project Structure

```
VibeCoding-PrototypeApp/
├── LocalStreamServer.apk            # Pre-compiled Android APK (ready to install)
├── run-desktop-player.bat           # 1-Click launcher for Java Desktop Player
├── run-desktop-player.ps1           # PowerShell launcher for Java Desktop Player
├── README.md                        # Complete project guide (this document)
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
└── DesktopStreamPlayer/             # Java Desktop Application
    ├── src/main/java/com/localstream/player/
    │   ├── model/RemoteFile.java                  # Media model with size & format helpers
    │   ├── api/StreamApiClient.java               # HTTP client with PIN token validation
    │   ├── ui/PlayerWindow.java                   # Player window with range seeking & fallback
    │   └── LocalStreamApp.java                    # File listing UI with filter & connection bar
    ├── src/test/java/                             # Unit tests for API client & model parsing
    ├── build.gradle.kts                           # JavaFX 21 Gradle build configuration
    └── dist/                                      # Standalone distribution package
```

---

## Requirements & Prerequisites

- **Laptop**:
  - JDK 21+ (Installed at `C:\Users\p.k.palanisamy\.jdks\jbr-21.0.11` or system `JAVA_HOME`).
  - Windows, macOS, or Linux.
- **Android Device**:
  - Android 8.0 (API level 26) or newer.
  - Wi-Fi Hotspot or common local Wi-Fi network.

---

## Step-by-Step Setup & Running Guide

### Step 1: Set up the Android Device
1. **Install the App**:
   Transfer and install `LocalStreamServer.apk` on your Android phone, or install via ADB:
   ```cmd
   adb install -r LocalStreamServer.apk
   ```
2. **Turn on Mobile Hotspot**:
   - On Android: Go to **Settings > Network & internet > Hotspot & tethering > Wi-Fi hotspot** and turn it ON.
   - Alternatively, connect both your Android phone and laptop to the same Wi-Fi router.
3. **Launch the App & Select Media**:
   - Open **Local Stream Server**.
   - Tap **"Pick Folder"** and select any directory on your device that contains videos or music (e.g. `Movies`, `Downloads`, or `DCIM`).
   - The app will automatically scan and list all discovered files.
4. **Start the Server**:
   - Tap **"Start Server"**.
   - Note the **Server Address** (e.g., `http://192.168.43.1:8080`) and the 4-digit **Security PIN** displayed in the app.

---

### Step 2: Connect from the Laptop
1. **Connect Wi-Fi**:
   Connect your laptop's Wi-Fi to your Android phone's Hotspot (or ensure both are on the same Wi-Fi).
2. **Launch the Player**:
   - **Option A**: Double-click `run-desktop-player.bat`.
   - **Option B (PowerShell)**: Run `.\run-desktop-player.ps1`.
   - **Option C (Gradle)**:
     ```cmd
     cd DesktopStreamPlayer
     .\gradlew.bat run
     ```
3. **Connect & Browse**:
   - In the top bar, enter the **Server URL** (e.g., `http://192.168.43.1:8080`).
   - Enter the 4-digit **PIN Token** from your Android screen.
   - Click **"⚡ Connect"**.
   - The file list will populate with all video and audio files from the phone!

---

### Step 3: Stream & Seek Media
1. Double-click any file in the list (or click **"▶ Play"**).
2. A separate **Player Window** will open with built-in controls:
   - **Native Multi-Format Playback**: Plays **MKV, AVI, MP4, WebM**, TS, and other common video/audio containers natively right inside the app window with hardware acceleration.
   - **Audio Track Selection**: Dropdown menu (`🔊 Audio`) dynamically populates all audio streams (e.g. Tamil, Korean, English, Director's Commentary) and allows switching tracks on the fly.
   - **Subtitle Track Selection**: Dropdown menu (`💬 Subtitles`) dynamically lists embedded subtitles (ESub, SRT, ASS/SSA) and allows toggling or switching subtitle languages instantly.
   - **10-Second Skip with Arrow Keys**:
     - **Right Arrow (`→`)** / `+10s` button: Skips forward 10 seconds with on-screen visual confirmation (`+10s ⏩`).
     - **Left Arrow (`←`)** / `-10s` button: Skips backward 10 seconds with on-screen visual confirmation (`⏪ -10s`).
   - **Play / Pause**: Button or `Spacebar`.
   - **Volume Control**: Slider (0–100%), mute button (`M`), or `Up`/`Down` arrow keys.
   - **Timeline Seeking**: Scrubbing or clicking anywhere on the timeline slider leverages HTTP Range requests (`HTTP 206 Partial Content`) on the Android server for instant seeking.
   - **Speed Control**: `0.5x` to `2.0x`.
   - **Fullscreen**: Toggle with `F11` or `F` key.
