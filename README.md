# End-to-End Local Streaming System

An end-to-end, high-security local media streaming solution consisting of:
1. **Android Streaming Server**: Picks a folder from device storage via SAF, encrypts traffic over **HTTPS / TLS 1.3** with RSA-2048 keys, manages multi-device sessions via camera QR code scanning, and enforces custom configurable sliding-window rate limiting.
2. **Python Desktop Player (PyQt6)**: Laptop player with native MKV, AVI, MP4, and WebM decoding via Qt 6.5+ FFmpeg engine, audio track selection, embedded subtitle selection, 10s arrow key skipping, anti-sleep screen wake protection, and true edge-to-edge fullscreen with auto-hiding controls.

---

## 🔒 Security Architecture (Option B)

```
+------------------------------------------+          TLS 1.3 / HTTPS (Port 8443)        +------------------------------------------+
|          Android Mobile Device           | <=========================================> |           Laptop Desktop Client          |
|                                          |                                             |                                          |
|  [ Camera QR Scanner (ZXing) ]           |  1. Laptop displays dynamic 256-bit QR      |  [ PythonStreamPlayer (PyQt6) ]          |
|          |                               |     `localstream://auth?token=...`          |     - Click "📱 Pair (QR)"               |
|          v                               | <------------------------------------------ |     - Generates 256-bit crypto token     |
|  [ Multi-Device Session Store ]          |  2. Android camera scans & authorizes token |     - Shows High-Contrast QR Code        |
|     - Multiple live clients supported    |                                             |                                          |
|     - Individual device revocation       |  3. Encrypted HTTPS GET /files (Bearer Auth)|                                          |
|          |                               | <------------------------------------------ |     - Filterable Media Table             |
|          v                               |  4. Encrypted JSON File Listing             |          |                               |
|  [ Configurable Rate Limiter ]           | ------------------------------------------> |          v                               |
|     - Sliding window (5-200 req/s)       |                                             |  [ PlayerWindow ]                        |
|     - Max failed attempts (3-20)         |  5. Range Seek GET /stream/{id} (TLS 1.3)   |     - Native MKV, AVI, MP4, WebM (FFmpeg)|
|     - Auto temporary IP ban (403/429)    | <------------------------------------------ |     - Multi-Audio Track Selection        |
|          |                               |  6. HTTP 206 Partial Content (AES-256)      |     - Embedded Subtitle Track Selection  |
|          v                               | ------------------------------------------> |     - 10s Skip via Arrow Keys (← / →)    |
|  [ HTTPS / TLS 1.3 Server ]              |                                             |     - Edge-to-Edge Fullscreen (F / F11)  |
|     - In-memory RSA-2048 Keypair         |                                             |     - Auto-Hiding Controls on Idle       |
|     - Self-signed X.509 Certificate      |                                             |     - Screen Wake Manager (No Sleep)     |
+------------------------------------------+                                             +------------------------------------------+
```

### Security & Privacy Highlights:
- **End-to-End Transport Encryption**: Full **TLS 1.3 / HTTPS** encryption using in-memory RSA-2048 certificate generation on Android. Prevents eavesdropping and packet sniffing on open Wi-Fi / Hotspot networks.
- **Dynamic 256-bit Session Tokens**: Laptop generates cryptographically random 256-bit tokens (`secrets.token_hex(32)`).
- **Camera QR Code Pairing**: Zero-friction pairing: user taps **"📷 Scan Laptop QR"** on phone and points at laptop screen to authorize.
- **Multi-Device Live Sessions**: Multiple devices (laptop, tablet, TV) can be paired and stream concurrently without disconnecting existing sessions. Devices can be revoked individually from the Android UI.
- **Configurable Rate Limiting & Brute-Force Defense**: Built-in sliding-window limiter with customizable requests-per-second and maximum failed attempt thresholds before automatic IP temporary bans (HTTP 429 / 403).
- **Scoped Storage Isolation**: The Android app uses Android Storage Access Framework (SAF) in read-only mode for the chosen folder. No client or remote entity can access other storage or write to device memory.

---

## 📁 Repository Structure

```
LocalStream/
├── LocalStreamPlayer.exe            # Standalone Portable Windows Executable (Zero Installation)
├── LocalStreamServer.apk            # Pre-compiled Android APK with TLS 1.3 & QR Scanner
├── run-desktop-player.bat           # 1-Click launcher for Python Desktop Player (Source)
├── run-desktop-player.ps1           # PowerShell launcher for Python Desktop Player (Source)
├── README.md                        # Documentation
│
├── PythonStreamPlayer/              # High-Performance Python Client (PyQt6)
│   ├── main.py                      # Connection manager, pairing & searchable file table UI
│   ├── pairing_dialog.py            # Dynamic 256-bit QR Code display dialog
│   ├── player_window.py             # Video player with MKV/AVI/MP4, audio/sub tracks, fullscreen
│   ├── api_client.py                # HTTPS client with TLS verify bypass & token headers
│   ├── wake_manager.py              # Anti-sleep background activity & Windows power management
│   ├── requirements.txt             # PyQt6>=6.5.0, requests, qrcode, pillow
│   ├── test_client.py               # Automated test suite (11 unit tests)
│   └── README.md
│
├── AndroidStreamServer/             # Android Application
│   ├── app/
│   │   ├── src/main/java/com/localstream/server/
│   │   │   ├── model/MediaFile.kt                 # Data model with formatting & type detection
│   │   │   ├── model/DeviceSession.kt             # Cryptographic device session model
│   │   │   ├── server/StreamingHttpServer.kt      # Multi-threaded HTTPS/HTTP server with Range & Auth
│   │   │   ├── server/CertificateHelper.kt        # In-memory RSA-2048 & X.509 TLS certificate builder
│   │   │   ├── server/RateLimiter.kt              # Sliding window & brute-force IP rate limiter
│   │   │   ├── service/StreamingService.kt        # Foreground service with multi-device state
│   │   │   ├── util/StorageHelper.kt              # Storage Access Framework folder scanning
│   │   │   ├── util/NetworkUtils.kt               # Hotspot & Wi-Fi IP address resolution
│   │   │   ├── ui/StreamingViewModel.kt           # Reactive state, QR scan handler & session management
│   │   │   └── MainActivity.kt                    # Jetpack Compose UI with ZXing QR Camera Scanner
│   │   ├── src/test/java/                         # Unit tests
│   │   └── AndroidManifest.xml                    # Camera and foreground service permissions
│   └── build.gradle.kts                           # BouncyCastle and ZXing dependencies
│
└── DesktopStreamPlayer/             # Optional Java Desktop Client
```

---

## 🚀 Running Guide

### Step 1: Set up the Android Device
1. **Install the App**:
   Install `LocalStreamServer.apk` on your Android phone, or run:
   ```cmd
   adb install -r LocalStreamServer.apk
   ```
2. **Turn on Mobile Hotspot**:
   - Turn on Hotspot or connect both phone and laptop to the same Wi-Fi network.
3. **Select Folder & Start Server**:
   - Open **Local Stream Server**.
   - Tap **"Pick Folder"** and choose any directory containing movies or audio.
   - Adjust **Security & Rate Limiting** settings if desired (HTTPS toggle, PIN auth toggle, req/s limit).
   - Tap **"Start Server"**. The app displays the active server addresses (e.g. `https://192.168.43.1:8443` or `http://192.168.43.1:8080`).

---

### Step 2: Run and Pair the Windows Player
You can run the player on any Windows laptop/PC using either method:

#### Option A: Portable Executable (No Python or installation required!)
1. Download **`LocalStreamPlayer.exe`** from the [GitHub Releases](https://github.com/pravinkumar2k02/LocalStream/releases).
2. Simply double-click **`LocalStreamPlayer.exe`**.
   - Completely standalone and portable — run it from your Desktop, USB drive, or Downloads folder on any Windows machine!

#### Option B: Run from Source
1. Double-click `run-desktop-player.bat` (or run `run-desktop-player.ps1`).

---

### Step 3: Pairing & Streaming
1. Click **"📱 Pair (QR)"** in the top bar of the laptop player.
2. On your phone, tap **"📷 Scan Laptop QR"** and point the camera at the QR code on the laptop screen.
   - The phone immediately displays: `Authorized: <Your-Laptop-Name>`.
   - The existing connection remains active even if other devices are already connected.
3. On the laptop, click **"✅ Use This Token & Connect"** (or click **"⚡ Connect"**).
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
| **Keep Screen Awake** | Prevents laptop screen timeout/dimming while the app runs |

---

## 🧪 Testing

Run the automated Python test suite:
```cmd
cd PythonStreamPlayer
python test_client.py
```
Outputs:
```
Ran 11 tests in 3.1s - OK
```
