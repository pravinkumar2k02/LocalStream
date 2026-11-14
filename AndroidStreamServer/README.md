# Android Local Stream Server

An Android application that allows users to pick any folder on their device and exposes an embedded HTTP media streaming server over the device Wi-Fi hotspot or local network.

## Features

- **Folder Picker**: Uses the Android Storage Access Framework (`ActivityResultContracts.OpenDocumentTree`) to scan and serve any local folder.
- **Embedded HTTP Server**: Multi-threaded HTTP/1.1 server running directly on the device.
- **HTTP Range Support (`206 Partial Content`)**:
  - Full support for `Range: bytes=start-end`, `bytes=start-`, and `bytes=-suffix`.
  - Enables instant scrubbing/seeking in remote players (such as JavaFX Media, VLC, or Web browsers) without needing to download the entire file first.
- **PIN Token Security**:
  - Automatically generates a 4-digit PIN token (with one-click regeneration and clipboard copying).
  - Protects both `GET /files` and `GET /stream/{id}`.
  - Accepts the PIN via query param `?pin=...`, header `X-PIN: ...`, or header `Authorization: Bearer ...`.
- **Foreground Service**: Keeps the streaming server running even when the device screen is off or the app is minimized.
- **Hotspot & Network Detection**: Automatically detects device IP addresses on Hotspot (`192.168.43.1`, `ap0`, `softap0`, `rndis0`) and Wi-Fi (`wlan0`).

---

## API Specification

### 1. `GET /files`
Returns a JSON array of all files discovered in the selected folder.

- **Authentication**: Requires PIN (via `?pin=XXXX` query parameter or `X-PIN: XXXX` header).
- **Response `200 OK`**:
```json
[
  {
    "id": "1",
    "name": "sample_video.mp4",
    "mimeType": "video/mp4",
    "size": 52428800
  },
  {
    "id": "2",
    "name": "nature_clip.webm",
    "mimeType": "video/webm",
    "size": 24117248
  }
]
```
- **Error `401 Unauthorized`**:
```json
{
  "error": "Unauthorized: Invalid or missing PIN token"
}
```

---

### 2. `GET /stream/{id}` & `HEAD /stream/{id}`
Streams the media file with accurate MIME type and range seek support.

- **Authentication**: Query parameter `?pin=XXXX` or header `X-PIN: XXXX`.
- **Normal Request (No Range)**:
  - Status: `200 OK`
  - Headers:
    - `Content-Type`: `video/mp4` (or `video/webm`, etc.)
    - `Content-Length`: `<total-bytes>`
    - `Accept-Ranges`: `bytes`
- **Range Request (Seeking)**:
  - Request: `Range: bytes=1048576-2097151`
  - Status: `206 Partial Content`
  - Headers:
    - `Content-Type`: `video/mp4`
    - `Content-Range`: `bytes 1048576-2097151/<total-bytes>`
    - `Content-Length`: `1048576`
    - `Accept-Ranges`: `bytes`

---

## Installation & Running

### Option A: Install Pre-Built APK
The compiled debug APK is located at:
- `LocalStreamServer.apk` (root folder)
- `AndroidStreamServer/LocalStreamServer.apk`

Install via `adb`:
```bash
adb install -r LocalStreamServer.apk
```

### Option B: Build from Source
From the `AndroidStreamServer` directory:
```bash
.\gradlew.bat assembleDebug
```
The APK will be generated under `app/outputs/apk/debug/app-debug.apk`.
