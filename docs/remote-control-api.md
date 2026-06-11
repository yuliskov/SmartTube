# SmartTube Remote Control API — Complete Specification

> **Status:** Ready for Implementation  
> **Purpose:** Use this doc to build both the Android TV server and Chrome extension client

---

## Table of Contents

1. [Architecture](#1-architecture)
2. [Service Discovery (UDP Broadcast)](#2-service-discovery-udp-broadcast)
3. [Authentication & Pairing](#3-authentication--pairing)
4. [REST API Endpoints](#4-rest-api-endpoints)
5. [WebSocket — Real-time Updates](#411-websocket--real-time-state-updates)
6. [Data Models](#5-data-models)
7. [Chrome Extension Spec](#6-chrome-extension-spec)
8. [Android Implementation Plan](#7-android-implementation-plan)
9. [Security](#8-security)

---

## 1. Architecture

```
Chrome Extension ──HTTP/UDP/WebSocket──► SmartTube Android TV
                                           │
                                     RemoteApiServer (NanoWSD, port 8497)
                                           │
                                     ├── HTTP REST (pairing, commands)
                                     └── WebSocket (real-time state updates)
                                           │
                                     RemoteApiBridge (static adapter)
                                           │
                                     PlayerEngine (existing interface)
                                           │
                                     ExoPlayerController (existing impl)
```

**Key principle:** Zero new playback logic. The `RemoteApiBridge` is a thin adapter that maps HTTP requests to existing `PlayerEngine` methods. All playback, track selection, quality switching, etc. already exists in the codebase.

---

## 2. Service Discovery (UDP Broadcast)

### How It Works

When Remote API is enabled, the server listens for UDP broadcast probes on port 8497.

**Discovery Request (Extension → TV, UDP broadcast):**
```json
{"action":"discover"}
```

**Discovery Response (TV → Extension, unicast UDP):**
```json
{
  "device_name": "Living Room TV",
  "device_id": "uuid-abc-123",
  "api_port": 8497,
  "app_version": "24.93",
  "api_version": "1"
}
```

The extension sends a broadcast to `255.255.255.255:8497` and each SmartTube instance responds with its info. If no TV is discovered, user enters IP manually.

---

## 3. Authentication & Pairing

### Pairing Flow

```
1. User enables "Remote API" in SmartTube settings
   → TV generates 6-digit code, starts HTTP server on port 8497

2. Extension sends GET /api/pair
   ← { "code": "482 917", "expires_in": 300 }

3. User enters code in extension popup

4. Extension sends POST /api/pair/verify
   → { "code": "482 917" }
   ← { "token": "a1b2c3d4...", "device_name": "Living Room TV" }

5. Extension stores token in chrome.storage.local

6. All subsequent requests: Authorization: Bearer <token>
```

### Token Rules

- 32-byte hex string, generated via `SecureRandom`
- Persisted until explicitly revoked (no expiry)
- Max 10 paired devices (oldest auto-revoked)
- Pairing code: 6 digits, valid 5 minutes, 5 verification attempts/min per IP

---

## 4. REST API Endpoints

**Base URL:** `http://<TV_IP>:8497`  
**Content-Type:** `application/json`  
**Auth:** `Authorization: Bearer <token>` header (except ping & pair)

### Error Response Format

```json
{ "error": { "code": 401, "message": "Unauthorized" } }
```

| HTTP Status | When |
|-------------|------|
| `400 Bad Request` | Malformed JSON body or invalid/missing parameter |
| `401 Unauthorized` | Missing or invalid bearer token |
| `404 Not Found` | Unknown endpoint |
| `429 Too Many Requests` | Pairing rate limit exceeded (5 failed attempts/min per IP) |
| `500 Internal Server Error` | Unexpected server-side error |

### 4.1 System

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `GET` | `/api/system/ping` | No | Health check + device info |

**Response:**
```json
{
  "status": "ok",
  "device_name": "Living Room TV",
  "app_version": "24.93",
  "api_version": "1"
}
```

### 4.2 Pairing

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `GET` | `/api/pair` | No | Get pairing code |
| `POST` | `/api/pair/verify` | No | Verify code, get token |

**`GET /api/pair` Response:**
```json
{ "code": "482 917", "expires_in": 300 }
```

**`POST /api/pair/verify` Request:**
```json
{ "code": "482 917" }
```

**`POST /api/pair/verify` Response:**
```json
{ "token": "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4", "device_name": "Living Room TV" }
```

### 4.3 Player State

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `GET` | `/api/player` | Yes | Full playback state |

**Response — see Section 5.1**

### 4.4 Transport Controls

| Method | Endpoint | Auth | Body | Description |
|--------|----------|------|------|-------------|
| `POST` | `/api/player/play` | Yes | — | Resume |
| `POST` | `/api/player/pause` | Yes | — | Pause |
| `POST` | `/api/player/toggle` | Yes | — | Toggle play/pause |
| `POST` | `/api/player/seek` | Yes | `{"position_ms":60000}` | Seek |
| `POST` | `/api/player/next` | Yes | — | Next track |
| `POST` | `/api/player/previous` | Yes | — | Previous track |
| `POST` | `/api/player/stop` | Yes | — | Close player |
| `POST` | `/api/player/reload` | Yes | — | Reload source |

**Standard response:** `{ "ok": true }`  
**Seek response:** `{ "ok": true, "position_ms": 60000 }`

### 4.5 Playback Settings

| Method | Endpoint | Auth | Body | Description |
|--------|----------|------|------|-------------|
| `GET` | `/api/player/speed` | Yes | — | Get speed |
| `PUT` | `/api/player/speed` | Yes | `{"speed":1.5}` | Set speed |
| `GET` | `/api/player/volume` | Yes | — | Get volume (0.0–1.0) |
| `PUT` | `/api/player/volume` | Yes | `{"volume":0.85}` | Set volume |
| `GET` | `/api/player/pitch` | Yes | — | Get pitch |
| `PUT` | `/api/player/pitch` | Yes | `{"pitch":1.0}` | Set pitch |
| `GET` | `/api/player/mute` | Yes | — | Get mute state |
| `POST` | `/api/player/mute/toggle` | Yes | — | Toggle mute/unmute |
| `GET` | `/api/player/subtitle` | Yes | — | Get subtitle state |
| `POST` | `/api/player/subtitle/toggle` | Yes | — | Toggle subtitles on/off |

### 4.6 Track Selection

| Method | Endpoint | Auth | Body | Description |
|--------|----------|------|------|-------------|
| `GET` | `/api/player/formats/video` | Yes | — | List video tracks |
| `GET` | `/api/player/formats/audio` | Yes | — | List audio tracks |
| `GET` | `/api/player/formats/subtitle` | Yes | — | List subtitle tracks |
| `GET` | `/api/player/formats/selected` | Yes | — | Get selected tracks |
| `PUT` | `/api/player/formats/video` | Yes | `{"format_id":"30232"}` | Select video |
| `PUT` | `/api/player/formats/audio` | Yes | `{"format_id":"251"}` | Select audio |
| `PUT` | `/api/player/formats/subtitle` | Yes | `{"format_id":"en"}` | Select subtitle (null to disable) |

### 4.7 Video Manipulation

| Method | Endpoint | Auth | Body | Description |
|--------|----------|------|------|-------------|
| `GET` | `/api/player/video/resize` | Yes | — | Get resize mode |
| `PUT` | `/api/player/video/resize` | Yes | `{"mode":0}` | Set (0=fit, 1=fill, 2=zoom) |
| `GET` | `/api/player/video/zoom` | Yes | — | Get zoom % |
| `PUT` | `/api/player/video/zoom` | Yes | `{"zoom":120}` | Set zoom |
| `GET` | `/api/player/video/rotation` | Yes | — | Get rotation |
| `PUT` | `/api/player/video/rotation` | Yes | `{"angle":90}` | Set (0/90/180/270) |
| `GET` | `/api/player/video/flip` | Yes | — | Get flip |
| `PUT` | `/api/player/video/flip` | Yes | `{"enabled":true}` | Set flip |

### 4.8 Content

| Method | Endpoint | Auth | Body | Description |
|--------|----------|------|------|-------------|
| `POST` | `/api/content/open` | Yes | See below | Open video |
| `POST` | `/api/content/search` | Yes | `{"query":"..."}` | Search YouTube and play first result |
| `GET` | `/api/content/suggestions` | Yes | — | Get queue |
| `POST` | `/api/content/suggestions/:index` | Yes | — | Play queue item |

### 4.9 Queue Management

| Method | Endpoint | Auth | Body | Description |
|--------|----------|------|------|-------------|
| `GET` | `/api/player/queue` | Yes | — | Get all videos in queue |
| `POST` | `/api/player/queue` | Yes | `{"video_id":"..."}` | Add video to end of queue |
| `POST` | `/api/player/queue/next` | Yes | `{"video_id":"..."}` | Play video next |
| `DELETE` | `/api/player/queue` | Yes | `{"video_id":"..."}` | Remove video from queue |
| `POST` | `/api/player/queue/clear` | Yes | — | Clear entire queue |

**`GET /api/player/queue` Response:**
```json
[
  { "index": 0, "video_id": "abc123", "title": "Video 1", "author": "Channel A", "is_current": true },
  { "index": 1, "video_id": "def456", "title": "Video 2", "author": "Channel B", "is_current": false }
]
```

**`POST /api/content/open` body variants:**
```json
{ "url": "https://www.youtube.com/watch?v=dQw4w9WgXcQ" }
{ "video_id": "dQw4w9WgXcQ" }
{ "video_id": "dQw4w9WgXcQ", "position_ms": 30000 }
{ "video_id": "dQw4w9WgXcQ", "playlist_id": "PLxxx", "playlist_index": 5 }
```

### 4.9 Home Theater Control

Control the TV's volume, mute, and power. HDMI CEC features (output switching, subwoofer/rear levels, sound mode, immersive AE) should be implemented by the Chrome extension or an ADB bridge — see [ADB Commands for CEC](#adb-commands-for-cec-operations).

| Method | Endpoint | Auth | Body | Description |
|--------|----------|------|------|-------------|
| `GET` | `/api/theater` | Yes | — | Volume + mute + audio output |
| `GET` | `/api/theater/volume` | Yes | — | Get TV volume + muted state |
| `PUT` | `/api/theater/volume` | Yes | `{"volume":50}` | Set TV volume (0–100) |
| `POST` | `/api/theater/volume/up` | Yes | — | Volume up one step |
| `POST` | `/api/theater/volume/down` | Yes | — | Volume down one step |
| `POST` | `/api/theater/mute/toggle` | Yes | — | Toggle TV mute |
| `POST` | `/api/theater/power/toggle` | Yes | — | Toggle TV power (sends `KEYCODE_POWER`) |

**`GET /api/theater` Response:**
```json
{
  "volume": 42,
  "muted": false,
  "audio_output": "tv"
}
```

**`GET /api/theater/volume` Response:**
```json
{ "volume": 42, "muted": false }
```

#### Why HDMI CEC Needs an ADB Bridge

`android.hardware.hdmi.HdmiControlManager` is a `@SystemApi` — it requires the `android.permission.MODIFY_TV_USER_SELECTION` signature-level permission, which is only granted to system apps. User-installed apps cannot access it.

Similarly, the `cmd hdmi_control` shell commands require shell UID, which `Runtime.exec()` from an app does not have.

**Volume and mute** use `AudioManager` — these work from any app.

**CEC vendor commands** (output switching, subwoofer/rear levels, sound mode, immersive AE) must be run by an external tool that has shell access. An ADB-based bridge, a companion app installed as a system app, or a Shizuku service can execute these.

#### ADB Commands for CEC Operations (for Chrome Extension / ADB Bridge)

The SmartTube app cannot run these commands directly — `cmd hdmi_control` requires shell UID and `HdmiControlManager` is a `@SystemApi` unavailable to user-installed apps.

The Chrome extension or an ADB bridge running on the user's machine should implement these features by executing the commands below via ADB.

An extension or bridge tool running ADB commands on the user's machine can implement the full theater control. These are the exact commands:

**Switch to home theater speakers:**
```bash
adb shell cmd hdmi_control setsystemaudiomode on
adb shell cmd hdmi_control setarc on
adb shell cmd hdmi_control cec_setting set volume_control_enabled 1
```

**Switch to TV speakers:**
```bash
adb shell cmd hdmi_control setsystemaudiomode off
adb shell cmd hdmi_control setarc off
```

**Set subwoofer level (0–12):**
```bash
adb shell cmd hdmi_control vendorcommand \
  --device_type 0 --destination 5 \
  --args "F2:44:00:FF:06:FF:FF" --id true
```
Replace `06` with hex level (e.g., `08` for level 8, `00` for off).

**Set rear speaker level (0–12):**
```bash
adb shell cmd hdmi_control vendorcommand \
  --device_type 0 --destination 5 \
  --args "F2:44:00:FF:FF:FF:FF:06" --id true
```
Replace `06` with hex level.

**Set immersive audio enhancement:**
```bash
# Enable
adb shell cmd hdmi_control vendorcommand \
  --device_type 0 --destination 5 \
  --args "F2:44:00:FF:FF:FF:01" --id true

# Disable
adb shell cmd hdmi_control vendorcommand \
  --device_type 0 --destination 5 \
  --args "F2:44:00:FF:FF:FF:00" --id true
```

**Set sound mode:**
```bash
# Auto
adb shell cmd hdmi_control vendorcommand \
  --device_type 0 --destination 5 \
  --args "F2:0D:00:55:FF:FF:FF:FF" --id true

# Cinema
adb shell cmd hdmi_control vendorcommand \
  --device_type 0 --destination 5 \
  --args "F2:0D:00:34:FF:FF:FF:FF" --id true

# Music
adb shell cmd hdmi_control vendorcommand \
  --device_type 0 --destination 5 \
  --args "F2:0D:00:06:FF:FF:FF:FF" --id true

# Standard
adb shell cmd hdmi_control vendorcommand \
  --device_type 0 --destination 5 \
  --args "F2:0D:00:00:FF:FF:FF:FF" --id true
```

**Read current CEC state:**
```bash
adb shell dumpsys hdmi_control | tail -n 260
```

**Read current theater level state:**
```bash
adb shell cmd hdmi_control vendorcommand \
  --device_type 0 --destination 5 \
  --args "F2:43:00:FF:FF:FF:FF:FF" --id true
```

#### CEC State Parsing (from dumpsys output)

Parse the last 260 lines of `dumpsys hdmi_control` to extract:

| Pattern | Regex | Meaning |
|---------|-------|---------|
| Audio output | `(?:SET SYSTEM AUDIO MODE\|SYSTEM AUDIO MODE REQUEST).*(?:5F:72:\|05:70:[0-9A-F]{2}:[0-9A-F]{2}:)(00\|01)` | `01` = theater, `00` = TV |
| Combined levels | `F2:43:00:FF:([0-9A-F]{2}):([0-9A-F]{2}):([0-9A-F]{2}):([0-9A-F]{2})` | Subwoofer, ?, Immersive, Rear |
| Subwoofer set | `F2:44:00:FF:([0-9A-F]{2}):FF:FF` | Subwoofer level |
| Rear set | `F2:44:00:FF:FF:FF:FF:([0-9A-F]{2})` | Rear level |
| Immersive AE | `F2:44:00:FF:FF:FF:(00\|01)` | Immersive state |
| Sound mode | `F2:0[CD]:00:([0-9A-F]{2}):FF:(?:00\|FF):FF:(?:00\|FF)` | Mode byte |

#### Extension Implementation Pattern

The Chrome extension or an ADB bridge implements full theater control:

1. **Volume/mute** — use `GET/PUT /api/theater/volume` (runs on TV via AudioManager).
2. **CEC features** — execute ADB commands directly from the user's machine:
   - Switch audio output, set subwoofer/rear levels, sound mode, immersive AE.
   - Parse `dumpsys hdmi_control` for current CEC state.
3. **Power** — use `POST /api/theater/power/toggle` or ADB `input keyevent KEYCODE_POWER`.

The app stores no CEC state — the bridge reads it fresh from `dumpsys hdmi_control` each time.

Recommended architecture:

```
Chrome Extension ──ADB──► cmd hdmi_control (CEC features)
                    ──REST──► SmartTube App (volume/mute/power via AudioManager)
```

The extension connects to the same REST API for player controls and volume, and runs ADB commands for HDMI CEC features.

### 4.10 System Control

| Method | Endpoint | Auth | Body/Params | Description |
|--------|----------|------|-------------|-------------|
| `GET` | `/api/system/dpad` | Yes | `?key=up` | D-pad (up/down/left/right/enter/back) |
| `POST` | `/api/system/voice` | Yes | `{"action":"start"}` | Voice search |

### 4.10 WebSocket — Real-time State Updates

**URL:** `ws://<TV_IP>:8497/ws?token=<auth_token>`

Instead of polling `GET /api/player` every second, the Chrome extension can open a WebSocket connection to receive live player state updates at ~2Hz (every 500ms).

#### Connection

1. After pairing, open WebSocket to `ws://<TV_IP>:8497/ws?token=<your_token>`
2. On connect, server sends a `hello` message
3. Server then streams `state_update` messages every 500ms while player is active
4. Client can send commands over the WebSocket (no HTTP needed)

#### Server → Client Messages

**Hello (on connect):**
```json
{
  "type": "hello",
  "api_version": "1",
  "device_name": "Living Room TV"
}
```

**State Update (every 500ms):**
```json
{
  "type": "state_update",
  "data": {
    "state": "playing",
    "video": {
      "video_id": "dQw4w9WgXcQ",
      "title": "Never Gonna Give You Up",
      "author": "Rick Astley",
      "channel_id": "UCuAXFkgsw1L7xaCfnd5JJOw",
      "thumbnail_url": "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
      "duration_ms": 212000,
      "is_live": false,
      "is_shorts": false,
      "playlist_id": "PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf",
      "playlist_index": 5
    },
    "position_ms": 45000,
    "duration_ms": 212000,
    "speed": 1.0,
    "pitch": 1.0,
    "volume": 0.85,
    "selected_tracks": {
      "video": { "format_id": "30232", "width": 1920, "height": 1080, "frame_rate": 60.0, "codec": "vp9", "bitrate": 4500000 },
      "audio": { "format_id": "251", "codec": "opus", "language": "en", "bitrate": 128000 },
      "subtitle": null
    },
    "video_transform": {
      "resize_mode": 0,
      "zoom_percents": 100,
      "rotation_angle": 0,
      "flip_enabled": false
    },
    "suggestions_count": 12,
    "queue_size": 5,
    "queue_index": 0
  }
}
```

#### Client → Server Commands

Send JSON commands over the WebSocket:

| Action | Body | Description |
|--------|------|-------------|
| `play` | `{"action":"play"}` | Resume |
| `pause` | `{"action":"pause"}` | Pause |
| `toggle` | `{"action":"toggle"}` | Toggle play/pause |
| `seek` | `{"action":"seek","position_ms":60000}` | Seek |
| `next` | `{"action":"next"}` | Next track |
| `previous` | `{"action":"previous"}` | Previous track |
| `stop` | `{"action":"stop"}` | Close player |
| `reload` | `{"action":"reload"}` | Reload source |
| `set_speed` | `{"action":"set_speed","speed":1.5}` | Set speed |
| `set_volume` | `{"action":"set_volume","volume":0.85}` | Set volume |
| `set_video_format` | `{"action":"set_video_format","format_id":"30232"}` | Switch video track |
| `set_audio_format` | `{"action":"set_audio_format","format_id":"251"}` | Switch audio track |
| `set_subtitle_format` | `{"action":"set_subtitle_format","format_id":"en"}` | Switch subtitle (null to disable) |
| `toggle_subtitles` | `{"action":"toggle_subtitles"}` | Toggle subtitles on/off |
| `toggle_mute` | `{"action":"toggle_mute"}` | Toggle mute/unmute |
| `search` | `{"action":"search","query":"never gonna give you up"}` | Search and play first result |
| `add_to_queue` | `{"action":"add_to_queue","video_id":"dQw4w9WgXcQ"}` | Add video to queue |
| `play_next` | `{"action":"play_next","video_id":"dQw4w9WgXcQ"}` | Play video next |
| `remove_from_queue` | `{"action":"remove_from_queue","video_id":"dQw4w9WgXcQ"}` | Remove video from queue |
| `clear_queue` | `{"action":"clear_queue"}` | Clear entire queue |
| `get_queue` | `{"action":"get_queue"}` | Request queue list |
| `theater_power_toggle` | `{"action":"theater_power_toggle"}` | Toggle TV power |
| `theater_get_state` | `{"action":"theater_get_state"}` | Request theater state |
| `get_state` | `{"action":"get_state"}` | Request immediate state update |

#### Chrome Extension Usage

```javascript
class SmartTubeWebSocket {
  constructor(host, port, token, onStateUpdate) {
    this.url = `ws://${host}:${port}/ws?token=${token}`;
    this.onStateUpdate = onStateUpdate;
    this.ws = null;
    this.reconnectDelay = 1000;
  }

  connect() {
    this.ws = new WebSocket(this.url);
    
    this.ws.onopen = () => {
      console.log('SmartTube WebSocket connected');
      this.reconnectDelay = 1000;
    };

    this.ws.onmessage = (event) => {
      const msg = JSON.parse(event.data);
      
      if (msg.type === 'hello') {
        console.log(`Connected to ${msg.device_name}`);
      } else if (msg.type === 'state_update') {
        this.onStateUpdate(msg.data);
      }
    };

    this.ws.onclose = () => {
      console.log('WebSocket closed, reconnecting...');
      setTimeout(() => this.connect(), this.reconnectDelay);
      this.reconnectDelay = Math.min(this.reconnectDelay * 2, 30000);
    };

    this.ws.onerror = (err) => {
      console.error('WebSocket error:', err);
      this.ws.close();
    };
  }

  send(action, params = {}) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify({ action, ...params }));
    }
  }

  play()                          { this.send('play'); }
  pause()                         { this.send('pause'); }
  toggle()                        { this.send('toggle'); }
  seek(positionMs)                { this.send('seek', { position_ms: positionMs }); }
  next()                          { this.send('next'); }
  previous()                      { this.send('previous'); }
  setSpeed(speed)                 { this.send('set_speed', { speed }); }
  setVolume(volume)               { this.send('set_volume', { volume }); }
  setVideoFormat(formatId)        { this.send('set_video_format', { format_id: formatId }); }
  setAudioFormat(formatId)        { this.send('set_audio_format', { format_id: formatId }); }
  setSubtitleFormat(formatId)     { this.send('set_subtitle_format', { format_id: formatId }); }
  getState()                      { this.send('get_state'); }

  disconnect() {
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
  }
}
```

#### Popup Integration Example

```javascript
// In popup.js — replace polling with WebSocket
const ws = new SmartTubeWebSocket(host, port, token, (state) => {
  // Update UI instantly
  updateSeekBar(state.position_ms, state.duration_ms);
  updatePlayPauseButton(state.state);
  updateVolumeSlider(state.volume);
  updateVideoTitle(state.video?.title);
  updateThumbnail(state.video?.thumbnail_url);
});

ws.connect();

// Still use REST for one-off actions like track listing
const formats = await api.getVideoFormats();
```

#### Performance Notes

- State updates are ~500 bytes each, sent at 2Hz = ~1 KB/s bandwidth
- Server only sends updates when WebSocket clients are connected and player is active
- No updates sent when player is idle (saves battery)
- Auto-reconnect with exponential backoff (1s → 30s max)
- WebSocket auth uses the same bearer token from pairing
- Multiple clients can connect simultaneously

## 5. Data Models

### 5.1 Full Player State (`GET /api/player`)

```json
{
  "state": "playing",
  "video": {
    "video_id": "dQw4w9WgXcQ",
    "title": "Never Gonna Give You Up",
    "author": "Rick Astley",
    "channel_id": "UCuAXFkgsw1L7xaCfnd5JJOw",
    "thumbnail_url": "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
    "duration_ms": 212000,
    "is_live": false,
    "is_shorts": false,
    "playlist_id": "PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf",
    "playlist_index": 5
  },
  "position_ms": 45000,
  "duration_ms": 212000,
  "speed": 1.0,
  "pitch": 1.0,
  "volume": 0.85,
  "selected_tracks": {
    "video": { "format_id": "30232", "width": 1920, "height": 1080, "frame_rate": 60.0, "codec": "vp9", "bitrate": 4500000 },
    "audio": { "format_id": "251", "codec": "opus", "language": "en", "bitrate": 128000 },
    "subtitle": null
  },
  "video_transform": {
    "resize_mode": 0,
    "zoom_percents": 100,
    "rotation_angle": 0,
    "flip_enabled": false
  },
  "suggestions_count": 12
}
```

### 5.2 Video Format

```json
{
  "format_id": "30232",
  "width": 1920,
  "height": 1080,
  "frame_rate": 60.0,
  "codec": "vp9",
  "bitrate": 4500000,
  "label": "1080p60 VP9",
  "is_selected": true
}
```

### 5.3 Audio Format

```json
{
  "format_id": "251",
  "codec": "opus",
  "language": "en",
  "language_label": "English",
  "bitrate": 128000,
  "is_selected": true
}
```

### 5.4 Subtitle Format

```json
{
  "format_id": "en",
  "language": "en",
  "language_label": "English",
  "is_selected": false
}
```

### 5.5 State Values

| Value | Meaning |
|-------|---------|
| `"playing"` | Actually playing video |
| `"paused"` | Playback paused |
| `"buffering"` | Loading/buffering |
| `"idle"` | No video loaded |
| `"ended"` | Video finished |

---

## 6. Chrome Extension Spec

### 6.1 Manifest V3

```json
{
  "manifest_version": 3,
  "name": "SmartTube Remote",
  "version": "1.0.0",
  "permissions": ["storage"],
  "host_permissions": ["http://*:*/*"],
  "action": { "default_popup": "popup.html", "default_icon": { "16": "icons/icon16.png", "48": "icons/icon48.png", "128": "icons/icon128.png" },
  "background": { "service_worker": "background.js" },
  "options_page": "options.html"
}
```

### 6.2 File Structure

```
chrome-extension/
├── manifest.json
├── popup.html            # Main remote UI
├── popup.css             # Dark theme styles
├── popup.js              # Popup logic (fetch, polling)
├── background.js         # Service worker (badge, background polling)
├── options.html          # Settings: manage TVs
├── options.js
├── options.css
├── lib/
│   ├── api.js            # REST API client class
│   ├── websocket.js      # WebSocket client for real-time updates
│   ├── discovery.js      # UDP broadcast discovery
│   └── storage.js        # chrome.storage wrapper
└── icons/
    ├── icon16.png
    ├── icon48.png
    └── icon128.png
```

### 6.3 `lib/api.js` — API Client

```javascript
class SmartTubeAPI {
  constructor(baseUrl, token) {
    this.baseUrl = baseUrl;
    this.token = token;
  }

  async request(method, path, body = null) {
    const opts = {
      method,
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${this.token}`
      }
    };
    if (body) opts.body = JSON.stringify(body);
    const res = await fetch(`${this.baseUrl}${path}`, opts);
    return res.json();
  }

  // Player
  getPlayer()                     { return this.request('GET', '/api/player'); }
  play()                          { return this.request('POST', '/api/player/play'); }
  pause()                         { return this.request('POST', '/api/player/pause'); }
  toggle()                        { return this.request('POST', '/api/player/toggle'); }
  seek(positionMs)                { return this.request('POST', '/api/player/seek', { position_ms: positionMs }); }
  next()                          { return this.request('POST', '/api/player/next'); }
  previous()                      { return this.request('POST', '/api/player/previous'); }
  stop()                          { return this.request('POST', '/api/player/stop'); }
  reload()                        { return this.request('POST', '/api/player/reload'); }

  // Speed/Volume
  getSpeed()                      { return this.request('GET', '/api/player/speed'); }
  setSpeed(speed)                 { return this.request('PUT', '/api/player/speed', { speed }); }
  getVolume()                     { return this.request('GET', '/api/player/volume'); }
  setVolume(volume)               { return this.request('PUT', '/api/player/volume', { volume }); }

  // Tracks
  getVideoFormats()               { return this.request('GET', '/api/player/formats/video'); }
  getAudioFormats()               { return this.request('GET', '/api/player/formats/audio'); }
  getSubtitleFormats()            { return this.request('GET', '/api/player/formats/subtitle'); }
  getSelectedTracks()             { return this.request('GET', '/api/player/formats/selected'); }
  setVideoFormat(formatId)        { return this.request('PUT', '/api/player/formats/video', { format_id: formatId }); }
  setAudioFormat(formatId)        { return this.request('PUT', '/api/player/formats/audio', { format_id: formatId }); }
  setSubtitleFormat(formatId)     { return this.request('PUT', '/api/player/formats/subtitle', { format_id: formatId }); }

  // Content
  openVideo(urlOrId, extra = {})  { return this.request('POST', '/api/content/open', { ...extra, ...(urlOrId.includes('/') ? { url: urlOrId } : { video_id: urlOrId }) }); }
  getSuggestions()                { return this.request('GET', '/api/content/suggestions'); }
  playSuggestion(index)           { return this.request('POST', `/api/content/suggestions/${index}`); }

  // System
  dpad(key)                       { return this.request('GET', `/api/system/dpad?key=${key}`); }
  voice(action)                   { return this.request('POST', '/api/system/voice', { action }); }

  // Pairing (static)
  static async pair(host, port, code) {
    const res = await fetch(`http://${host}:${port}/api/pair/verify`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ code })
    });
    return res.json();
  }

  static async getCode(host, port) {
    const res = await fetch(`http://${host}:${port}/api/pair`);
    return res.json();
  }
}
```

### 6.4 `lib/discovery.js` — UDP Broadcast Discovery

```javascript
async function discoverTVs(timeoutMs = 2000) {
  // Chrome extensions can't do raw UDP, so we use a fallback:
  // 1. Try known IPs from storage
  // 2. Try common gateway IPs
  // 3. Fall back to manual entry
  // 
  // For full UDP discovery, a native messaging host is needed.
  // For MVP, use manual IP entry or stored IPs.

  const stored = await chrome.storage.local.get('known_tvs');
  return stored.known_tvs || [];
}

async function pingTV(host, port) {
  try {
    const res = await fetch(`http://${host}:${port}/api/system/ping`, { signal: AbortSignal.timeout(2000) });
    return res.ok ? await res.json() : null;
  } catch { return null; }
}
```

### 6.5 UI Screens

**Popup (when playing):**
- Video thumbnail (from `video.thumbnail_url`)
- Title + seek bar (draggable, seek on mouseup)
- Transport: prev, rewind 10s, play/pause, forward 10s, next
- Volume slider
- Speed [−]/[+] buttons
- Quality button → dropdown with video formats
- Audio button → dropdown with audio formats
- Subs button → dropdown with subtitle formats
- Bottom toolbar: Search, Queue, D-pad

**Pairing screen:**
- Discovered TVs list (from ping)
- Manual IP:port input
- 6-digit code input
- Connect button

**Badge:**
- Green = playing, Yellow = paused, Gray = idle, None = disconnected

### 6.6 Keyboard Shortcuts (popup focused)

| Key | Action |
|-----|--------|
| Space | Toggle play/pause |
| ← / → | Seek ±10s |
| ↑ / ↓ | Volume ±5% |
| f | D-pad enter (fullscreen) |
| Escape | Back |

---

## 7. Android Implementation Plan

### 7.1 New Files

| File | Package | Purpose |
|------|---------|---------|
| `RemoteApiServer.java` | `common/.../misc/remoteapi/` | NanoHTTPD server + UDP discovery listener |
| `RemoteApiBridge.java` | `common/.../misc/remoteapi/` | HTTP→PlayerEngine adapter (static singleton) |
| `RemoteApiAuthProvider.java` | `common/.../misc/remoteapi/` | Token generation, pairing code management |
| `RemoteApiData.java` | `common/.../prefs/` | Settings persistence |
| `RemoteApiSettingsPresenter.java` | `common/.../app/presenters/settings/` | Settings UI |

### 7.2 Files to Modify

| File | Change |
|------|--------|
| `common/build.gradle` | Add `nanohttpd:2.3.1` dependency |
| `common/src/main/AndroidManifest.xml` | Add `INTERNET` permission |
| `PlaybackPresenter.java` | Register `RemoteApiBridge` in constructor |
| `RemoteControlService.java` | Start/stop `RemoteApiServer` |
| `AppDataSourceManager.java` | Add "Remote API" settings entry |
| `strings.xml` | Add new string resources |

### 7.3 Key Implementation Details

**RemoteApiServer (extends NanoHTTPD):**
```java
public class RemoteApiServer extends NanoHTTPD {
    private RemoteApiAuthProvider mAuth;
    
    public RemoteApiServer(int port) {
        super(port);
        mAuth = new RemoteApiAuthProvider();
    }
    
    @Override
    public Response serve(IHTTPSession session) {
        // CORS headers on all responses
        // Auth check (skip for /api/system/ping and /api/pair)
        // Route to handler methods
        // Return JSON responses
    }
}
```

**RemoteApiBridge (static singleton, accessed by server):**
```java
public class RemoteApiBridge {
    private static PlaybackPresenter sPresenter;
    
    public static void setPresenter(PlaybackPresenter p) { sPresenter = p; }
    
    public static PlayerEngine getPlayer() {
        PlaybackView view = sPresenter.getPlayer();
        return view != null ? view.getPlayerEngine() : null;
    }
    
    public static Video getVideo() { return sPresenter.getVideo(); }
    
    // All endpoint handlers are static methods here
    // e.g., handlePlay(), handleSeek(), handleGetPlayer(), etc.
}
```

**Registration in PlaybackPresenter constructor (after RemoteController):**
```java
mEventListeners.add(new RemoteController(context));
mEventListeners.add(new RemoteApiBridge()); // NEW — registers itself as static singleton
```

### 7.4 Dependencies to Add

```gradle
// common/build.gradle
dependencies {
    implementation 'org.nanohttpd:nanohttpd:2.3.1'
}
```

### 7.5 Manifest Changes

```xml
<!-- common/src/main/AndroidManifest.xml -->
<uses-permission android:name="android.permission.INTERNET" />
```

---

## 8. Security

| Threat | Mitigation |
|--------|-----------|
| Unauthorized LAN access | Bearer token via pairing |
| Brute-force pairing | 5 attempts/min per IP |
| Token storage | SharedPreferences (encrypted on API 23+) |
| MITM | HTTP only (local network, no sensitive data) |
| DoS | 10 req/s per IP rate limit |

**CORS:**
```
Access-Control-Allow-Origin: *
Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS
Access-Control-Allow-Headers: Authorization, Content-Type
```

---

## Appendix: Comparison with Existing Remote Control

| Feature | YouTube Device Link | REST API (this) |
|---------|-------------------|-----------------|
| Protocol | YouTube servers | Local HTTP |
| Client | YouTube app only | Any HTTP client |
| Pairing | TV code in YT app | 6-digit code in extension |
| Commands | Play, pause, seek, volume, D-pad | All + quality, speed, zoom, search, subtitles |
| State | Position, duration | Full: tracks, transforms, queue, metadata |
| Latency | ~200ms (cloud) | <1ms (LAN) |
| Internet | Required | Not required |
