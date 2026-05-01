# Server / Client split + Android client — Design Spec

## Goal

Split the current `dictate.py` into two Python processes (server + client) communicating over HTTP, then add an Android client that drives the same server through a draggable floating button overlay with auto-paste into any focused input.

## Non-goals

- Authentication / token-based access (LAN-only, open server)
- Service discovery (mDNS / QR pairing) — Android uses a hardcoded server URL constant
- Streaming-mode transcription over the wire (full-phrase only)
- Runtime UI for changing the server URL on Android
- Google Play publication

## High-level architecture

```
┌────────────────────────┐
│  Python client.py      │  hotkey + clipboard + auto-paste (current UX)
└────────────┬───────────┘
             │  HTTP POST /transcribe (loopback)
             ▼
┌────────────────────────┐
│  Python server.py      │  ASR model (Whisper or GigaAM), hallucination filter
│  0.0.0.0:7531          │
└────────────▲───────────┘
             │  HTTP POST /transcribe (LAN)
             │
┌────────────┴───────────┐
│  Android client (APK)  │  floating button + AccessibilityService paste + stats
└────────────────────────┘
```

Server listens on `0.0.0.0:7531` so both loopback and LAN clients can reach it. No authentication: anything that reaches the port gets a response.

## Phase 1: Python split

### Files

- `server.py` — new. Owns model, hallucination filter, HTTP API.
- `client.py` — new. Owns hotkey loop, audio capture, clipboard, `safe_paste`, model selection wizard.
- `dictate.py` — deleted.
- `dictate.ps1` / `dictate.sh` — updated to invoke `client.py`.

### `server.py` responsibilities

- On startup, in this order:
  1. Read `voice_key.json` BEFORE importing or installing Flask. If the file is absent or has no `backend`/`model_name`, print a clear instruction to run `client.py` first and `sys.exit(1)`. This guarantees the error is visible even on a fresh Python install where Flask is not yet present.
  2. `_ensure_deps` for server core (`flask`, `numpy`).
  3. `_ensure_deps` for the chosen backend.
  4. Load `HallucinationFilter` from `hallucinations.txt`. If `hallucinations.txt` is missing, log a warning and proceed with an empty filter (matches current behavior).
  5. Load model via `load_model(backend, model_name)` (logic moves here).
- The loaded model is held as a module-level global `_MODEL`. Flask handlers reference it directly. Single-threaded GPU inference makes concurrent access non-issue; `app.run(threaded=False)` enforces serial request handling.
- HTTP server (Flask):
  - `POST /transcribe`
    - Request body: WAV bytes (16 kHz mono PCM signed 16-bit little-endian).
    - Decoded into a `numpy.float32` array `[-1.0, 1.0]` via `wav_utils.decode_wav()`.
    - `duration_sec` = `samples / 16000` computed from the decoded array (audio length in seconds, NOT inference wall-clock time).
    - Audio fed to `recognize()` (logic moves here).
    - Hallucination filter applied to the raw text.
    - Response body (JSON): `{"text": str, "words": int, "duration_sec": float}` where `words` is `len(text.split())` after filtering.
  - `GET /health`
    - Response: `{"ready": bool, "backend": str, "model": str}`. `ready` is `true` once the model is loaded.
- Bind: `0.0.0.0:7531` by default; constants `HOST = "0.0.0.0"`, `PORT = 7531` at top of file (edit the file to change).
- Run as a foreground process. No daemonization.

### `client.py` responsibilities

- Owns everything the current `dictate.py` does except model loading and recognition: hotkey wizard, model-selection wizard (writes choice into `voice_key.json` for the server to read), audio capture, hallucination filter (NO — moved to server; the client receives already-filtered text), clipboard save/restore, paste, streaming/full mode.
- On startup:
  - `_ensure_deps` for *core* deps + a new `requests` dep.
  - Run hotkey + model wizard as today (writes `voice_key.json`).
  - Probe `http://127.0.0.1:7531/health`. If unreachable, spawn `python server.py` as a background subprocess (`subprocess.Popen`, stdout/stderr inherited or piped through to the user). Poll `/health` until `ready=true` (timeout 60 s; fail fast otherwise).
  - Once server is ready, enter the main hotkey loop.
- Recognition: `recognize(audio_flat, prev_text="")` becomes a small wrapper that
  1. Encodes `audio_flat` to WAV bytes via `wav_utils.encode_wav()`.
  2. POSTs to `http://127.0.0.1:7531/transcribe`.
  3. Returns the `text` field from the JSON response.
- Filtering: server already filters, so `filter_and_paste` in the client treats `clean_text == raw_text`. The hallucination filter is removed from the client. `hallucinations.txt` continues to live in the project root so the server reads it from the same path.
- Server lifecycle: client launches the server but does not own it after spawning — if the user kills the client (Ctrl+C), the server keeps running. Subsequent client launches reuse the existing server. No PID file. To stop the server: `taskkill /F /IM python.exe` (Windows), `pkill -f server.py` (macOS / Linux), or restart the machine.
  - Per-OS notes:
    - **Windows**: `dictate.ps1` invokes `python client.py`. The server subprocess inherits the user's session and exits with the user's logout.
    - **macOS**: `dictate.sh` invokes `python3 client.py` (system `python` may resolve to Python 2 — the script uses `python3` explicitly and the auto-installer uses `sys.executable`). When `client.py` spawns the server it uses `subprocess.Popen([sys.executable, "server.py"])` so the same interpreter is reused. The orphaned server is killed when the Terminal session that started it ends; for a long-running setup the user can run `nohup python3 server.py &` manually before launching the client.
    - **Linux**: same as macOS for `python3` / `sys.executable`. If the user wants LAN access, the project README documents `sudo ufw allow 7531` (or equivalent for non-ufw firewalls).

### `voice_key.json` schema (unchanged from current)

```json
{
  "keys": [...],
  "backend": "whisper" | "gigaam",
  "model_name": "large-v3" | "e2e_rnnt"
}
```

### Dependency split

- Client core deps: `numpy`, `sounddevice`, `copykitten`, `keyboard`, `mouse`, `requests`.
- Server core deps: `numpy`, `flask`.
- Backend deps (Whisper / GigaAM): unchanged, lazily installed inside `load_model()` on the server.

`requirements.txt` stays as a flat list of all packages for documentation.

### Audio wire format

WAV with standard 44-byte header:
- ChunkID `RIFF`, Format `WAVE`, Subchunk1 `fmt `, AudioFormat 1 (PCM), NumChannels 1, SampleRate 16000, BitsPerSample 16.
- Subchunk2 `data` followed by raw PCM samples.

A shared helper module `wav_utils.py` lives at the project root and is imported by both `server.py` and `client.py`. It exposes:

```python
def encode_wav(audio_flat: np.ndarray, sample_rate: int = 16000) -> bytes:
    """Take float32 samples in [-1, 1], return a complete WAV byte string (44-byte header + PCM s16le)."""

def decode_wav(wav_bytes: bytes) -> tuple[np.ndarray, int]:
    """Parse a WAV byte string. Return (float32 samples in [-1, 1], sample_rate)."""
```

Pure functions, no state. The wire format is the contract between three implementations (Python server, Python client, Android `WavRecorder`); duplicating it inside Python would only invite drift between client and server with no benefit.

`requirements.txt` is updated to include `requests` (client) and `flask` (server) so a manual `pip install -r requirements.txt` still works for users who prefer that path.

### Ports / firewall

Default port `7531`. On Windows, `dictate.ps1` does NOT add a firewall rule automatically — the README documents the manual `netsh advfirewall` command (or Windows Defender prompt on first run). Loopback works without rules. On Linux, LAN access requires `sudo ufw allow 7531` (or distro equivalent). On macOS, the firewall prompts the user on first bind to `0.0.0.0`.

## Phase 2: Android client

### Stack

- Kotlin, Coroutines, Jetpack Compose, Material 3
- Koin for DI
- Room for persistence
- Retrofit + OkHttp for network
- Min SDK 26 (Android 8), target SDK current (35+)
- Single-module Gradle project, Kotlin DSL build files

### Repository layout

```
android/
├── app/
│   ├── build.gradle.kts
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── kotlin/ru/er_log/dictate/
│   │   │   ├── core/
│   │   │   │   ├── network/      Retrofit, ApiService, DTOs, audio encoder
│   │   │   │   ├── audio/        AudioRecord wrapper → WAV bytes
│   │   │   │   ├── overlay/      FloatingButtonService (Foreground Service + WindowManager)
│   │   │   │   ├── accessibility/  PasteAccessibilityService
│   │   │   │   ├── tile/         OverlayTileService (Quick Settings)
│   │   │   │   ├── db/           Room database, DAOs, entities
│   │   │   │   ├── di/           Koin modules (top-level)
│   │   │   │   └── theme/        Compose Material 3 theme
│   │   │   ├── feature/
│   │   │   │   ├── home/         {ui, domain, data}
│   │   │   │   ├── permissions/  {ui, domain, data}
│   │   │   │   ├── settings/     {ui, domain, data}
│   │   │   │   └── stats/        {ui, domain, data}
│   │   │   ├── DictateApp.kt     Application class, Koin startApplication
│   │   │   └── MainActivity.kt   Single activity, Compose nav graph
│   │   └── res/
│   │       ├── drawable/         icons (mic, settings, etc.)
│   │       ├── values/           strings.xml, colors, theme attrs
│   │       └── xml/              accessibility_service_config.xml
├── build.gradle.kts              root
├── settings.gradle.kts
└── gradle/                       wrapper, version catalog (libs.versions.toml)
```

### Clean Architecture layers (per feature)

- `ui/` — Compose screens, ViewModels (Coroutines + StateFlow).
- `domain/` — UseCases, repository interfaces, domain models. Pure Kotlin, no Android imports.
- `data/` — Repository implementations, Room DAOs/entities mapping, network mappers.

### Features

#### `feature/home`
Single screen, two zones in a vertical scroll:

1. **Stats card** — period tabs (week / month / year), large number of recognized words, optional sparkline. Reactive from Room via `StateFlow<StatsUiState>`.
2. **Permissions / status card** — collapsible. Shows three items: Microphone, Display over other apps, Accessibility. Each has a status (✓ / ⚠) and a `[Grant]` button. Once all three are granted the entire card collapses to a single "✓ Ready to dictate" line.
3. **Overlay toggle button** — large primary button at the bottom: `[Enable floating button]` / `[Disable]`. Mirrors the Quick Settings tile state.

#### `feature/permissions`
Backs the permissions card on home. Domain logic: query each permission's status; provide intent factory for the system settings screens.

- Microphone → `ActivityResultContracts.RequestPermission` (`Manifest.permission.RECORD_AUDIO`).
- Display over other apps → `Settings.canDrawOverlays(context)`; intent `Settings.ACTION_MANAGE_OVERLAY_PERMISSION`.
- Accessibility → enumerate enabled services via `AccessibilityManager.getEnabledAccessibilityServiceList`; intent `Settings.ACTION_ACCESSIBILITY_SETTINGS`.

For accessibility, the `[Grant]` button shows a bottom sheet with step-by-step instructions before opening settings (since the system flow is non-obvious): "1. Find 'Dictate' in the list. 2. Tap it. 3. Toggle 'Use service' on. 4. Confirm the dialog."

#### `feature/settings`
Two toggles for Phase 1:
- "Also copy result to clipboard" (default: off).
- "Vibrate on record start/stop" (default: on).

Persisted to DataStore (preferences).

#### `feature/stats`
Domain queries on Room:
- `wordsForDay(date)` / `wordsForWeek(weekStart)` / `wordsForMonth(yearMonth)` / `wordsForYear(year)`.
- DAO returns `Flow<Long>` for live updates.

Entity: `RecognitionEvent(id, timestampUtcMs, words, durationSec)`.

### Core: overlay (the floating button)

`FloatingButtonService` — Foreground Service with a minimal `NotificationCompat` notification ("Dictate is active — tap tile to hide"). Inflates a Compose-free root view via `WindowManager.addView` with type `TYPE_APPLICATION_OVERLAY`. Foreground service type is `microphone`; manifest declares `android.permission.FOREGROUND_SERVICE_MICROPHONE` (required on Android 14+, ignored on older API levels via manifest merger).

#### Gesture model

The button is a single circular `View` with a custom `OnTouchListener`. Three states (`IDLE`, `DRAG`, `RECORD`) and the following transitions:

- `ACTION_DOWN` (in `IDLE`): capture initial pointer + view position, post a 200 ms `Handler` callback. State stays `IDLE` for the moment.
- `ACTION_MOVE` (in `IDLE`): if displacement from initial position exceeds `ViewConfiguration.scaledTouchSlop`, cancel the 200 ms callback and transition to `DRAG`. `WindowManager.updateViewLayout` follows the pointer.
- 200 ms callback fires (still in `IDLE`, no `MOVE` past slop): transition to `RECORD`. Vibrate (short tick). Start `WavRecorder`. Visually pulse / change tint.
- `ACTION_UP` in `DRAG`: save position to DataStore (in dp, derived from `pixels / displayMetrics.density`, so it survives device / density changes). Transition to `IDLE`.
- `ACTION_UP` or `ACTION_CANCEL` in `RECORD`: stop `WavRecorder`, launch a coroutine that sends WAV to the server, shows a small spinner ring on the button. Transition to `IDLE`.
- `ACTION_CANCEL` in `IDLE` or `DRAG`: cancel the 200 ms callback, transition to `IDLE`. (`ACTION_CANCEL` fires when the OS removes the view, e.g., on rotation; treating it like `ACTION_UP` for `RECORD` ensures the recorded audio still gets sent.)
- `ACTION_POINTER_DOWN` (second finger in any state): ignored. Multi-touch is not supported on the floating button.
- `ACTION_DOWN` while not in `IDLE`: should not happen, but defensively ignored.

After server response: ask `PasteController.paste(text)`. On clipboard toggle, additionally `ClipboardManager.setPrimaryClip` AFTER paste (so the clipboard ends with the dictated text). On failure: vibrate twice + toast with reason.

Position persistence: `(x_dp, y_dp)` in DataStore. Restored on service start, clamped to current screen bounds (computed at restore time).

### Core: AccessibilityService

`PasteAccessibilityService` registered with metadata `accessibility_service_config.xml`:
```xml
<accessibility-service
    android:accessibilityEventTypes="typeViewFocused|typeWindowStateChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagRetrieveInteractiveWindows"
    android:canRetrieveWindowContent="true"
    android:canPerformGestures="false"
    android:description="@string/accessibility_service_description" />
```

`flagRetrieveInteractiveWindows` is required so `findFocus(FOCUS_INPUT)` can search across all interactive windows on Android 9+. Without it, the focused input may be invisible to the service when the floating overlay is the topmost window.

#### Service-to-app binding (lifecycle-safe)

The service is created and destroyed by the OS, not by the app. Koin runs at `Application.onCreate`, well before the user has enabled the accessibility service. Standard DI cannot own this binding; instead:

- `PasteAccessibilityService` exposes a `companion object holder: AtomicReference<PasteAccessibilityService?>`.
- `onServiceConnected` writes `holder.set(this)`. `onUnbind` and `onDestroy` write `holder.set(null)`.
- A separate `PasteController` implementation (Koin `single`) reads `PasteAccessibilityService.holder.get()` on each call. If null, returns `false` (paste not available — user has not granted permission).
- The Koin module exposes `PasteController` (the indirection class), NOT the service directly. This keeps the rest of the app testable with a fake controller.

`AtomicReference` provides the necessary memory visibility across threads (the service runs on the main thread, but `PasteController.paste` is invoked from a coroutine on `Dispatchers.IO`).

API exposed to the rest of the app:
```kotlin
interface PasteController {
    suspend fun paste(text: String): PasteResult  // Success / NoFocus / NotAvailable
}
```

Implementation: looks up the active service via the holder; if null returns `PasteResult.NotAvailable`. Otherwise:
1. Put `text` on the clipboard via `ClipboardManager` (system clipboard is the only handoff path that `ACTION_PASTE` understands).
2. Find the focused editable node: `rootInActiveWindow.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)` first; fall back to iterating `windows` (now visible due to `flagRetrieveInteractiveWindows`) and calling `findFocus` on each root.
3. If no editable focused node found → return `NoFocus`.
4. Try `node.performAction(AccessibilityNodeInfo.ACTION_PASTE)`. If false, fall back to `node.performAction(ACTION_SET_TEXT, Bundle().apply { putCharSequence(ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) })`.
5. Return `Success`.

Caller behavior: on `NotAvailable` toast "Enable Accessibility in Settings" and offer the deep-link button. On `NoFocus` toast "No focused text field — text is on clipboard". On `Success` no UI feedback (the inserted text is the feedback).

The service holds no per-instance state besides the focus tracking via `findFocus` (re-run each call), so there is no stale-reference problem.

### Core: Quick Settings tile

`OverlayTileService` extends `TileService`. Click toggles `FloatingButtonService` start/stop and updates tile state (`Tile.STATE_ACTIVE` / `STATE_INACTIVE`). Tile labels: "Dictate".

### Core: network

```kotlin
interface DictateApi {
    @POST("/transcribe")
    suspend fun transcribe(@Body wav: RequestBody): TranscribeResponse

    @GET("/health")
    suspend fun health(): HealthResponse
}

data class TranscribeResponse(val text: String, val words: Int, val durationSec: Double)
data class HealthResponse(val ready: Boolean, val backend: String, val model: String)
```

Server URL is a `BuildConfig` field driven from `app/build.gradle.kts`:

```kotlin
android {
    defaultConfig {
        buildConfigField("String", "SERVER_URL", "\"http://192.168.X.X:7531\"")
    }
}
```

A unit test in `app/src/test/.../ConfigSanityTest.kt` asserts that `BuildConfig.SERVER_URL` does not contain the placeholder `"X.X"` — this fails the build if the developer forgets to set the real server IP. Changing servers requires editing the gradle file and rebuilding (no runtime UI, as required).

`OkHttpClient` configured with `connectTimeout=2s`, `readTimeout=30s`, `writeTimeout=10s`. Failure surfaces as a Kotlin `Result<TranscribeResponse>` from the repository layer.

### Core: audio

`WavRecorder` wraps `AudioRecord(MIC, 16000, MONO, ENCODING_PCM_16BIT)`. Records into a `ByteArrayOutputStream`. On stop, builds a WAV by writing the 44-byte header (using the recorded byte count) + the captured samples. Returns a `ByteArray` ready for the network.

### Core: DI

Koin modules at `core/di/`:
- `networkModule` — Retrofit, OkHttpClient, DictateApi.
- `dbModule` — Room database, DAOs.
- `audioModule` — `WavRecorder`.
- `pasteModule` — exposes `PasteController` (the indirection class that reads from `PasteAccessibilityService.holder`). The service is NOT itself a Koin component; see "Service-to-app binding (lifecycle-safe)" above.
- `featureModules` — one `module {}` per feature exposing repos / use cases / view models.

`Application` class calls `startKoin { modules(...) }`.

### Manifest highlights

- `<uses-permission android:name="android.permission.RECORD_AUDIO"/>`
- `<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW"/>`
- `<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>`
- `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE"/>` — required on Android 14+ to pair with `foregroundServiceType="microphone"`. Manifest merger ignores it on lower API levels.
- `<uses-permission android:name="android.permission.INTERNET"/>`
- `<uses-permission android:name="android.permission.VIBRATE"/>` — for the haptic on record start/stop.
- `<service android:name=".core.overlay.FloatingButtonService" android:foregroundServiceType="microphone" android:exported="false"/>`
- `<service android:name=".core.accessibility.PasteAccessibilityService" android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE" android:exported="true">` with metadata pointing to `accessibility_service_config.xml`.
- `<service android:name=".core.tile.OverlayTileService" android:permission="android.permission.BIND_QUICK_SETTINGS_TILE" android:exported="true">` with action `android.service.quicksettings.action.QS_TILE`.

`android:usesCleartextTraffic="true"` on the application tag because the server uses plain HTTP. Network security config explicitly limits cleartext to the local subnets only.

### Data flow: end-to-end dictation

1. User long-presses the floating button (200 ms). `FloatingButtonService` vibrates and starts `WavRecorder`.
2. User releases. `FloatingButtonService` stops `WavRecorder`, gets `wavBytes`.
3. Service launches a coroutine: `dictateRepository.transcribe(wavBytes)` →
   - Retrofit `POST /transcribe` with body.
   - Returns `TranscribeResponse(text, words, durationSec)`.
4. Service calls `pasteController.paste(text)`.
5. Service inserts a row into `RecognitionEventDao` with `(now, words, durationSec)`.
6. UI on home screen reactively updates stats via the DAO `Flow`.

Steps 4 and 5 happen in parallel; failure of paste does not block stats logging.

### Error handling

- Server unreachable → toast "Server unreachable" + log to a small in-memory ring buffer (10 entries) the user can later view (post-MVP).
- Server 5xx → toast "Server error".
- Microphone permission revoked at runtime → toast + open permission settings.
- Accessibility disabled → fall back to clipboard-only mode (just copy, no auto-paste) regardless of the user's clipboard toggle, plus toast "Accessibility disabled — text copied to clipboard".

### Testing strategy

- **Phase 1 (Python)**:
  - Unit tests for the WAV encoder/decoder helper.
  - Integration test with the server: spawn server in a thread, POST a known-silent WAV, expect empty / short text without crashing.
  - Manual smoke: run client.py end-to-end as today.
- **Phase 2 (Android)**:
  - Unit tests for ViewModels and use cases (JUnit 4 + Turbine for Flow + MockK for fakes).
  - Instrumented test for `WavRecorder` that captures 1 second of silence and verifies the WAV header (44 bytes, sample rate 16000, mono, PCM s16le).
  - End-to-end test on emulator driven via `adb shell` — automation scope is intentionally narrow: install + permission grants + UI launch + button-press simulation. The test script:
    1. Boots the emulator (assumed running).
    2. Builds and installs the debug APK (`./gradlew installDebug`).
    3. Programmatically grants permissions:
       - `pm grant ru.er_log.dictate android.permission.RECORD_AUDIO`
       - `appops set ru.er_log.dictate SYSTEM_ALERT_WINDOW allow`
       - `settings put secure enabled_accessibility_services ru.er_log.dictate/ru.er_log.dictate.core.accessibility.PasteAccessibilityService` followed by `settings put secure accessibility_enabled 1`
    4. `am start -n ru.er_log.dictate/.MainActivity` and `adb logcat` for `Dictate:*` to confirm app launches without crash.
    5. `am startservice` (or `am start-foreground-service`) to launch the floating button overlay.
    6. `input tap x y` to simulate tapping the floating button (coordinates discoverable via `dumpsys window`).
  - **Audio injection into the emulator microphone is intentionally out of scope.** The Android emulator does not support piping a WAV file into the simulated mic via any documented command-line flag; the only paths involve modifying the emulator config to use a virtual audio source (host-side, brittle) or mocking `AudioRecord` at the test layer. Neither is worth the engineering for an MVP. The end-to-end audio path is verified manually by speaking into the host mic on a real device or emulator with mic forwarding enabled.

### Out of scope (for both phases)

- Auth, mDNS, QR, runtime server URL editing, Play Store readiness, multi-user stats, dark mode polish beyond Material defaults, accessibility-of-the-app (TalkBack support), localization beyond Russian + English strings.

## Trade-offs and open decisions resolved

- **Server framework**: Flask. Smallest mature framework; the model is single-threaded on GPU anyway, so async is unnecessary.
- **Server lifecycle**: client spawns server lazily and orphans it on exit. Avoids a PID file at the cost of leaving a stale server when the user logs out (acceptable — terminal close kills it).
- **Drag vs record gesture**: 200 ms hold-still threshold (Messenger Chat Heads pattern). Pointer movement past `touchSlop` before the timer fires = drag; 200 ms of stillness without exceeding `touchSlop` = record.
- **Filter location**: hallucination filter on the server only. Avoids duplicating the filter file in the Android APK and keeps the wire response final.

## Phase ordering

1. Phase 1 lands on `feature/server-client-android` and merges to `main` BEFORE Phase 2 begins. This avoids a long-lived branch and gives the user a working refactor to use day-to-day.
2. Phase 2 reopens (or rebranches off `main`) and adds the `android/` directory with no Python-side changes.

Both phases share this single spec; each gets its own implementation plan.
