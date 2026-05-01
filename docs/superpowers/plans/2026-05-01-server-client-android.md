# Server / Client split + Android client — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Steps use checkbox (`- [ ]`) syntax for tracking. **Each task in this plan must produce exactly one commit.** When dispatching, hand the agent (a) this plan file, (b) the spec at `docs/superpowers/specs/2026-05-01-server-client-android-design.md`, and the task number. The agent reads both files and implements that single task — no pasted code in the dispatch prompt.

**Goal:** Split desktop dictation into Python server + Python client (Phase 1), then add an Android client driving the same server through a floating-button overlay with auto-paste (Phase 2).

**Architecture:** HTTP-only inter-process communication (`POST /transcribe` carrying WAV bytes; `GET /health`). Python server owns the ASR model and hallucination filter; Python client owns hotkey/clipboard/auto-paste UX. Android client is a single-module Gradle project (Kotlin + Compose + Coroutines + Koin + Room) targeting min SDK 26; floating button via `WindowManager.TYPE_APPLICATION_OVERLAY`, paste via custom `AccessibilityService`, on/off via Quick Settings tile.

**Tech Stack:** Python 3.10+, Flask, requests, NumPy, copykitten; Kotlin (latest stable), Jetpack Compose (Material 3), Koin, Room, Retrofit/OkHttp, AGP/Gradle latest stable.

**Reference docs (every task must read these):**
- Spec: `docs/superpowers/specs/2026-05-01-server-client-android-design.md`
- Existing `dictate.py` on `main` (pre-split) for behavior parity reference

---

## Workflow rules (apply to every task)

1. Work happens on branch `feature/server-client-android` (already created).
2. Each task = one git commit, including any tests added in that task.
3. Use `git add <specific files>`; never `git add -A` or `git add .`.
4. Commit message format: `<type>: <short subject>` (no closing period). Types: `feat`, `refactor`, `test`, `docs`, `chore`.
5. Before claiming "done": run any tests defined in the task; confirm Python parses (`python -c "import ast; ast.parse(open('FILE.py').read())"`); for Android, confirm Gradle sync succeeds (`./gradlew help` from `android/`).
6. After Phase 1 completes (Task 1.11), the branch is reviewed and merged to `main` BEFORE Phase 2 starts.

---

# PHASE 1 — Python split

Tasks 1.1 through 1.11. After Task 1.11 the desktop app must work end-to-end exactly as the pre-split version did, but with separate `server.py` and `client.py` processes.

---

### Task 1.1: WAV utilities module + tests

**What:** Create the shared `wav_utils.py` helper used by both `server.py` and `client.py` for round-tripping audio between `numpy.float32` arrays and WAV byte strings.

**Files:**
- Create: `wav_utils.py`
- Create: `tests/test_wav_utils.py`
- Create: `tests/__init__.py` (empty)

**Spec sections to read:** "Audio wire format" (under Phase 1).

**Definition of done:**
- Module exposes `encode_wav(samples: np.ndarray, sample_rate: int = 16000) -> bytes` and `decode_wav(wav_bytes: bytes) -> tuple[np.ndarray, int]` exactly as in the spec.
- `encode_wav` produces a complete RIFF/WAVE/PCM-s16le file with a 44-byte header.
- `decode_wav` parses what `encode_wav` produces and returns float32 samples in `[-1.0, 1.0]` plus the sample rate.
- Round-trip test: `np.allclose(decode_wav(encode_wav(x))[0], x, atol=1/32768)` for several inputs (zeros, sine wave, random noise — all `np.float32`).
- Tests use `pytest`; can be run with `python -m pytest tests/test_wav_utils.py -v`.
- Decoder accepts both the precise 44-byte canonical header and slightly-larger headers (e.g. with `LIST INFO` chunks); rejects non-PCM, multi-channel, or non-16kHz WAVs with a clear `ValueError`.

**Commit:** `feat: add wav_utils module for client/server audio round-trip`

---

### Task 1.2: Server skeleton — config validation + `/health`

**What:** Create `server.py` that validates `voice_key.json` BEFORE doing anything that needs Flask, installs server core deps, then starts a Flask app exposing only `/health` (no model yet). The model loading lives in the next task so this one stays small.

**Files:**
- Create: `server.py`

**Spec sections to read:** "`server.py` responsibilities" steps 1-2 of startup; "GET /health" endpoint contract.

**Definition of done:**
- Running `python server.py` without `voice_key.json` exits with code 1 and prints a clear instruction to run `client.py` first. The error happens BEFORE `import flask`.
- Running with a valid `voice_key.json` (any backend) installs flask + numpy if missing (use the same `_ensure_deps` pattern from `dictate.py`), then binds to `0.0.0.0:7531` and serves `GET /health` returning `{"ready": false, "backend": <from config>, "model": <from config>}` (ready stays false until Task 1.3 adds the model).
- The model loading is intentionally absent in this task; `_MODEL = None` is a module-level sentinel that future tasks fill in.
- Manual smoke: `curl -sf http://127.0.0.1:7531/health` returns the expected JSON.

**Commit:** `feat: add server.py skeleton with config validation and health endpoint`

---

### Task 1.3: Server — model loading + `recognize` move

**What:** Move the model-loading code (`load_model`, the per-backend deps lists, the `recognize` function, and the `HallucinationFilter` class) from `dictate.py` into `server.py`. After model load, `_MODEL` is populated and `/health` returns `ready: true`.

**Files:**
- Modify: `server.py`
- Read for reference: `dictate.py` (do not modify yet)

**Spec sections to read:** "`server.py` responsibilities" — full list, especially the startup ordering.

**Definition of done:**
- `server.py` contains `WHISPER_DEPS`, `GIGAAM_DEPS`, `load_model`, `recognize`, and `HallucinationFilter` as their counterparts existed in `dictate.py`.
- After config validation: install the chosen backend's deps, load the model into `_MODEL`, load `HallucinationFilter` into `_HALLUC`.
- `/health` returns `{"ready": true, "backend": "...", "model": "..."}` once load completes.
- Recognition is NOT yet exposed (no `/transcribe` endpoint).
- Manual smoke: start server, hit `/health`, see `ready: true` with correct backend/model.

**Commit:** `feat: load ASR model into server.py with hallucination filter`

---

### Task 1.4: Server — `/transcribe` endpoint

**What:** Add `POST /transcribe` to `server.py`. It decodes WAV bytes via `wav_utils`, runs `recognize`, applies hallucination filter, returns `{text, words, duration_sec}` JSON.

**Files:**
- Modify: `server.py`

**Spec sections to read:** "`server.py` responsibilities" → `POST /transcribe` block.

**Definition of done:**
- Handler reads `request.data` (raw bytes), decodes via `wav_utils.decode_wav`, validates sample rate is 16000, runs `recognize(audio_flat)`, runs `HallucinationFilter.check`, returns JSON with `text` (cleaned), `words = len(text.split())`, `duration_sec = len(audio_flat) / 16000`.
- Bad WAV returns HTTP 400 with `{"error": "..."}`.
- Server runs with `app.run(host=HOST, port=PORT, threaded=False)` to enforce serial inference.
- Smoke test (manual): Python REPL — `requests.post('http://127.0.0.1:7531/transcribe', data=wav_utils.encode_wav(np.zeros(16000, dtype=np.float32))).json()` returns 200 with `text` likely empty (silence) and `duration_sec` ≈ 1.0.

**Commit:** `feat: add /transcribe endpoint with WAV decoding and hallucination filter`

---

### Task 1.5: Server integration test

**What:** Add an integration test that boots the server in a subprocess, posts a known-silent WAV, asserts a 200 with the expected response shape.

**Files:**
- Create: `tests/test_server_integration.py`

**Spec sections to read:** "Testing strategy → Phase 1 (Python)".

**Definition of done:**
- Pytest fixture spawns `server.py` via `subprocess.Popen` with a `voice_key.json` whose contents are exactly `{"keys": [{"type":"keyboard","key":"ctrl"},{"type":"keyboard","key":"space"}], "backend": "whisper", "model_name": "large-v3-turbo"}` (cheapest first-load) — but skip the test if neither model is already cached locally (use `pytest.skip` based on `~/.cache/huggingface` or faster-whisper cache existence).
- Fixture polls `/health` until `ready: true` (timeout 120 s).
- Test posts 1 second of silence, asserts 200, asserts `set(response.json().keys()) == {"text", "words", "duration_sec"}`, asserts `0.99 < duration_sec < 1.01`.
- Tear-down kills the subprocess.
- Test runs with `python -m pytest tests/test_server_integration.py -v -s`.

**Commit:** `test: add server integration test for /transcribe round-trip`

---

### Task 1.6: Client extracted from `dictate.py`

**What:** Create `client.py` containing everything `dictate.py` does except model loading, recognition body, and hallucination filtering. The client's `recognize()` becomes an HTTP call.

**Files:**
- Create: `client.py`
- Read for reference: `dictate.py`

**Spec sections to read:** "`client.py` responsibilities".

**Definition of done:**
- `client.py` contains: hotkey wizard, model selection wizard (writes to `voice_key.json`), audio capture (`record_full`, `record_streaming`), clipboard helpers (`_clipboard_snapshot`, `_clipboard_restore`, `safe_paste`), `filter_and_paste` simplified (no filter logic — the server filtered).
- New core deps list adds `requests`.
- New `recognize(audio_flat, prev_text="")` function: posts `wav_utils.encode_wav(audio_flat)` to `http://127.0.0.1:7531/transcribe`, returns `response.json()["text"]`. On HTTP error, prints the error and returns `""`.
- `HallucinationFilter` class is REMOVED from the file.
- `load_model` and per-backend deps lists are REMOVED.
- The startup section creates no `model` global; instead it ensures the server is reachable (next task adds auto-spawn).
- Module imports: `wav_utils`, `requests`, drop `faster_whisper` / transformers.
- Running `python client.py` against an already-running server end-to-end works: hotkey → record → paste.

**Commit:** `refactor: split client.py from dictate.py, swap recognize to HTTP call`

---

### Task 1.7: Client — auto-spawn server

**What:** Make `client.py` probe `http://127.0.0.1:7531/health` at startup, and if unreachable, launch `server.py` as a background subprocess and wait for it to become ready.

**Files:**
- Modify: `client.py`

**Spec sections to read:** "`client.py` responsibilities" — server lifecycle bullet.

**Definition of done:**
- New helper `_ensure_server_running()` runs after the model wizard:
  - GET `/health` with 0.5 s connect timeout.
  - If reachable and `ready: true` → return.
  - If unreachable → `subprocess.Popen([sys.executable, "server.py"])` with stdout/stderr inherited.
  - Poll `/health` every 1 s up to 60 s; if `ready: true` → return; if timeout → print error + exit 1.
- The subprocess is intentionally orphaned — no cleanup on client exit.
- If `/health` is reachable but `backend`/`model` differs from the user's `voice_key.json`, print a warning instructing the user to kill the running server.
- Manual smoke: with no server running, `python client.py` brings one up; second invocation reuses it.

**Commit:** `feat: client auto-spawns server.py via subprocess if not reachable`

---

### Task 1.8: Update startup scripts

**What:** Point `dictate.ps1` and `dictate.sh` at `client.py` instead of `dictate.py`. Make sure `dictate.sh` uses `python3` explicitly per the spec's macOS / Linux notes.

**Files:**
- Modify: `dictate.ps1`
- Modify: `dictate.sh`

**Spec sections to read:** "Per-OS notes".

**Definition of done:**
- `dictate.ps1` invokes `python "$ScriptPath\client.py"` (was `python "$ScriptPath\dictate.py"`).
- `dictate.sh` invokes `python3 "$(...)/client.py"` (was `dictate.py`); shebang/`#!/usr/bin/env bash` unchanged.
- Existing GPU-detection logic stays.

**Commit:** `chore: update startup scripts to invoke client.py`

---

### Task 1.9: Delete `dictate.py`

**What:** Remove `dictate.py` from the repo. All behavior now lives in `server.py` + `client.py`.

**Files:**
- Delete: `dictate.py`

**Definition of done:**
- `git rm dictate.py`.
- `git grep "dictate.py"` returns only docs / git history references (no code paths).

**Commit:** `chore: remove dictate.py after split into server.py and client.py`

---

### Task 1.10: requirements.txt update

**What:** Update `requirements.txt` to include `flask` (server) and `requests` (client). Document the per-process split with comments.

**Files:**
- Modify: `requirements.txt`

**Definition of done:**
- File contains all packages from before plus `flask` and `requests`.
- A short comment block at the top explains: "this file lists the union of client + server deps for users who prefer a one-shot pip install; the scripts auto-install only what they need."
- Order: core (numpy/sounddevice/copykitten/keyboard/mouse) → client-only (requests) → server (flask) → backend (transformers/torch/torchaudio/pyannote-audio/sentencepiece/omegaconf/hydra-core/faster-whisper).

**Commit:** `chore: requirements.txt split into client/server/backend sections`

---

### Task 1.11: README updates for the split

**What:** Update `README.md` and `README.ru.md` to mention the server/client split, the port, and the per-OS firewall notes.

**Files:**
- Modify: `README.md`
- Modify: `README.ru.md`

**Spec sections to read:** "Ports / firewall"; per-OS lifecycle notes.

**Definition of done:**
- Both READMEs have a one-paragraph "How it runs" section after the "Quick start" table explaining: "the launch script starts `client.py`, which spawns `server.py` on `127.0.0.1:7531` if it isn't already running. The server keeps running after the client exits — kill it manually with `taskkill /F /IM python.exe` (Windows) / `pkill -f server.py` (macOS / Linux)."
- A "Network access" note: by default the server binds to `0.0.0.0:7531`. To allow LAN access, open the port:
  - Windows (admin PowerShell): `netsh advfirewall firewall add rule name="Dictate" dir=in action=allow protocol=TCP localport=7531`
  - Linux: `sudo ufw allow 7531`
  - macOS: confirm the firewall prompt that appears on first server bind to `0.0.0.0`.
- No screenshots needed.

**Commit:** `docs: update READMEs for server/client split`

---

## ━━━ PHASE 1 MERGE CHECKPOINT ━━━

Before any Phase 2 task starts:

1. Two-round final review of Phase 1 (see "Final review" section at the bottom of this plan).
2. Manual smoke test: hotkey → speak → text appears, with the orchestrator running both files.
3. Merge `feature/server-client-android` to `main`.
4. (Optionally) re-create the same branch for Phase 2.

---

# PHASE 2 — Android client

Tasks 2.1 through 2.20. Each task lands one cohesive unit on the branch.

---

### Task 2.1: Bootstrap the Android Gradle project

**What:** Create the empty Android project under `android/`. Single module `app`. Kotlin DSL build files. Version catalog (`libs.versions.toml`). No code yet — just a buildable empty `MainActivity` showing "Hello".

**Files:**
- Create: `android/settings.gradle.kts`
- Create: `android/build.gradle.kts`
- Create: `android/gradle.properties`
- Create: `android/gradle/wrapper/gradle-wrapper.properties`
- Create: `android/gradle/wrapper/gradle-wrapper.jar` (via `gradle wrapper` command — the agent must run it)
- Create: `android/gradlew` and `android/gradlew.bat` (via wrapper command)
- Create: `android/gradle/libs.versions.toml`
- Create: `android/app/build.gradle.kts`
- Create: `android/app/src/main/AndroidManifest.xml` (minimal)
- Create: `android/app/src/main/kotlin/ru/er_log/dictate/MainActivity.kt` (placeholder, no Compose yet)
- Create: `android/app/src/main/res/values/strings.xml` (just `app_name` = "Dictate")
- Create: `android/.gitignore` (build/, .idea/, *.iml, local.properties)

**Spec sections to read:** "Repository layout"; "Stack".

**Definition of done:**
- `cd android && ./gradlew help` succeeds (Gradle resolves the project).
- `./gradlew assembleDebug` produces an APK (no Compose yet, just `setContentView(TextView("Hello"))` or similar).
- Min SDK 26, target SDK latest stable (35 as of writing), Kotlin/AGP versions pinned in version catalog.
- `applicationId` = `ru.er_log.dictate`.
- `BuildConfig` enabled in `app/build.gradle.kts`.

**Commit:** `feat: bootstrap android module with gradle scaffolding`

---

### Task 2.2: Add Compose, Material 3, Koin, Coroutines

**What:** Wire up the runtime dependencies. Replace the placeholder `MainActivity` content with a Compose `Surface` showing "Dictate" centered. Add `DictateApp : Application` calling `startKoin {}` with an empty modules list (placeholders for later tasks).

**Files:**
- Modify: `android/gradle/libs.versions.toml` (add deps versions)
- Modify: `android/app/build.gradle.kts` (declare deps)
- Modify: `android/app/src/main/AndroidManifest.xml` (add `android:name=".DictateApp"` to `<application>`)
- Create: `android/app/src/main/kotlin/ru/er_log/dictate/DictateApp.kt`
- Modify: `android/app/src/main/kotlin/ru/er_log/dictate/MainActivity.kt`
- Create: `android/app/src/main/kotlin/ru/er_log/dictate/core/di/AppModule.kt` (empty module placeholder)

**Spec sections to read:** "Stack"; "Core: DI".

**Definition of done:**
- `compose-bom`, `material3`, `compose-ui-tooling-preview`, `lifecycle-viewmodel-compose`, `kotlinx-coroutines-android`, `koin-android`, `koin-androidx-compose` declared via version catalog.
- Test deps in version catalog: `junit:junit:4.13.2`, `app.cash.turbine:turbine` (latest), `io.mockk:mockk` (latest), `kotlinx-coroutines-test`. Wire them into `app/build.gradle.kts` `testImplementation` and `androidTestImplementation` blocks.
- `MainActivity` uses `setContent { MaterialTheme { ... } }` showing centered "Dictate".
- `DictateApp.onCreate` calls `startKoin { androidContext(this@DictateApp); modules(appModule) }`.
- App still builds and installs; UI shows the placeholder.

**Commit:** `feat: add compose, koin, coroutines, material3 to android app`

---

### Task 2.3: Compose theme module

**What:** Implement the Compose Material 3 theme: colors, typography, light/dark schemes.

**Files:**
- Create: `android/app/src/main/kotlin/ru/er_log/dictate/core/theme/Color.kt`
- Create: `android/app/src/main/kotlin/ru/er_log/dictate/core/theme/Type.kt`
- Create: `android/app/src/main/kotlin/ru/er_log/dictate/core/theme/Theme.kt`
- Modify: `android/app/src/main/res/values/themes.xml` (set parent to `Theme.Material3.DayNight.NoActionBar`)
- Modify: `android/app/src/main/AndroidManifest.xml` (set `android:theme="@style/Theme.Dictate"`)

**Spec sections to read:** none specific — the spec calls for "приятный дизайн", so use Material 3 with a sensible default palette.

**Definition of done:**
- `DictateTheme(content: @Composable () -> Unit)` callable composable that picks light/dark scheme based on `isSystemInDarkTheme()`.
- `MainActivity` wraps content in `DictateTheme`.
- Default seed color: `Color(0xFF6650A4)` (Material default purple). Use Material 3 dynamic color on Android 12+ when available; fall back to the seed-derived palette otherwise.
- Typography: keep system defaults; `Type.kt` exposes a `Typography` instance.

**Commit:** `feat: add material3 theme module`

---

### Task 2.4: Network layer

**What:** Retrofit + OkHttp, `DictateApi`, DTOs, `BuildConfig.SERVER_URL` from gradle, sanity test that fails the build if the server URL still contains the placeholder.

**Files:**
- Create: `android/app/src/main/kotlin/ru/er_log/dictate/core/network/Dto.kt` (`TranscribeResponse`, `HealthResponse`)
- Create: `android/app/src/main/kotlin/ru/er_log/dictate/core/network/DictateApi.kt`
- Create: `android/app/src/main/kotlin/ru/er_log/dictate/core/network/NetworkModule.kt` (Koin module)
- Modify: `android/app/build.gradle.kts` (read `server.url` from `local.properties` with placeholder fallback; declare `buildConfigField`; add Retrofit + OkHttp + kotlinx.serialization deps)
- Modify: `android/gradle/libs.versions.toml`
- Modify: `android/app/src/main/kotlin/ru/er_log/dictate/core/di/AppModule.kt` (include networkModule)
- Modify: `android/.gitignore` (ensure `local.properties` is ignored — it usually already is)
- Create: `android/app/src/test/kotlin/ru/er_log/dictate/core/network/ConfigSanityTest.kt`

**Spec sections to read:** "Core: network"; "Network Config".

**Definition of done:**
- `DictateApi` interface with `@POST("/transcribe") suspend fun transcribe(@Body wav: RequestBody): TranscribeResponse` and `@GET("/health") suspend fun health(): HealthResponse`.
- `OkHttpClient` configured with timeouts as in spec (connect 2 s, read 30 s, write 10 s).
- `BuildConfig.SERVER_URL` is generated from `local.properties` (per-developer config, gitignored). Pattern in `app/build.gradle.kts`:
  ```kotlin
  val localProps = Properties().apply {
      val f = rootProject.file("local.properties")
      if (f.exists()) f.inputStream().use { load(it) }
  }
  val serverUrl = localProps.getProperty("server.url") ?: "http://192.168.X.X:7531"
  android { defaultConfig { buildConfigField("String", "SERVER_URL", "\"$serverUrl\"") } }
  ```
  Each developer adds `server.url=http://<their-ip>:7531` to their own `local.properties`. The placeholder remains the build-time fallback ONLY for developers who haven't configured.
- `ConfigSanityTest`:
  ```kotlin
  @Test fun configHasNoPlaceholder() {
      assumeFalse(BuildConfig.SERVER_URL.contains("X.X"))  // skip when developer hasn't configured local.properties
      assertTrue(BuildConfig.SERVER_URL.startsWith("http://"))
  }
  ```
  Uses `org.junit.Assume.assumeFalse` so missing `local.properties` skips (does not fail) the test. When the developer DOES configure, the assertion runs and validates the URL shape.
- Unit test runs with `./gradlew :app:testDebugUnitTest`.

**Commit:** `feat: add retrofit network layer with config sanity test`

---

### Task 2.5: WAV recorder

**What:** `WavRecorder` wraps `AudioRecord` to capture 16 kHz mono PCM s16le and produce WAV bytes on stop. Coroutine-friendly API.

**Files:**
- Create: `android/app/src/main/kotlin/ru/er_log/dictate/core/audio/WavRecorder.kt`
- Create: `android/app/src/main/kotlin/ru/er_log/dictate/core/audio/AudioModule.kt`
- Modify: `android/app/src/main/kotlin/ru/er_log/dictate/core/di/AppModule.kt`
- Create: `android/app/src/androidTest/kotlin/ru/er_log/dictate/core/audio/WavRecorderTest.kt`

**Spec sections to read:** "Core: audio"; "Audio wire format".

**Definition of done:**
- API:
  ```kotlin
  class WavRecorder(...) {
      fun start()                  // throws IllegalStateException if already recording or no permission
      suspend fun stop(): ByteArray  // returns full WAV bytes (44-byte header + samples), suspends until the recording thread joins
  }
  ```
- Captures via `AudioRecord(MIC, 16000, MONO, ENCODING_PCM_16BIT)` on a dedicated thread (or `Dispatchers.IO` worker), writes to `ByteArrayOutputStream`.
- `stop()` finalizes the WAV header with the actual byte count.
- Instrumented test: starts, sleeps 1 second, stops, asserts WAV header (`RIFF`, `WAVE`, `fmt `, `data`), sample rate 16000, bits 16, channels 1, total samples ≈ 16000 ± 5%.
- Test runs via `./gradlew :app:connectedDebugAndroidTest` (requires a connected emulator).

**Commit:** `feat: add WavRecorder with instrumented test`

---

### Task 2.6: Room database

**What:** Room database with `RecognitionEvent` entity and DAO returning `Flow` aggregates per period.

**Files:**
- Create: `android/app/src/main/kotlin/ru/er_log/dictate/core/db/RecognitionEvent.kt`
- Create: `android/app/src/main/kotlin/ru/er_log/dictate/core/db/RecognitionEventDao.kt`
- Create: `android/app/src/main/kotlin/ru/er_log/dictate/core/db/DictateDatabase.kt`
- Create: `android/app/src/main/kotlin/ru/er_log/dictate/core/db/DbModule.kt`
- Modify: `android/app/build.gradle.kts` (add room deps + ksp plugin + `org.jetbrains.kotlinx:kotlinx-datetime`)
- Modify: `android/gradle/libs.versions.toml` (room, ksp, kotlinx-datetime versions)
- Modify: `android/app/src/main/kotlin/ru/er_log/dictate/core/di/AppModule.kt`

**Spec sections to read:** "feature/stats" entity definition; "Data flow: end-to-end dictation" step 5.

**Definition of done:**
- `RecognitionEvent(@PrimaryKey(autoGenerate=true) val id: Long, val timestampUtcMs: Long, val words: Int, val durationSec: Double)`.
- DAO methods (return `Flow<Long>`):
  - `wordsBetween(fromUtcMs: Long, toUtcMs: Long): Flow<Long?>` (sums `words` in range; null when no events → coalesce to 0 in domain layer).
  - `insert(event: RecognitionEvent)` (suspend).
- `DictateDatabase : RoomDatabase` with `version = 1` and the DAO.
- DI provides `DictateDatabase` as `single` and the DAO via `single { get<DictateDatabase>().recognitionEventDao() }`.

**Commit:** `feat: add room database for recognition events`

---

### Task 2.7: DataStore (preferences for settings + overlay position)

**What:** Set up `androidx.datastore.preferences` with two stores (or one — implementer's call): `settings` (clipboard-on-paste, vibrate) and `overlay_position` (`x_dp`, `y_dp` Floats).

**Files:**
- Create: `android/app/src/main/kotlin/ru/er_log/dictate/core/prefs/SettingsStore.kt`
- Create: `android/app/src/main/kotlin/ru/er_log/dictate/core/prefs/OverlayPositionStore.kt`
- Create: `android/app/src/main/kotlin/ru/er_log/dictate/core/prefs/PrefsModule.kt`
- Modify: `android/app/build.gradle.kts` (datastore-preferences dep)
- Modify: `android/gradle/libs.versions.toml`
- Modify: `android/app/src/main/kotlin/ru/er_log/dictate/core/di/AppModule.kt`

**Spec sections to read:** "feature/settings"; gesture "Position persistence" note in "Core: overlay".

**Definition of done:**
- `SettingsStore` exposes `clipboardOnPaste: Flow<Boolean>` (default false) and `vibrateOnRecord: Flow<Boolean>` (default true), with `setClipboardOnPaste(v: Boolean)` and `setVibrateOnRecord(v: Boolean)` (suspend).
- `OverlayPositionStore` exposes `position: Flow<Pair<Float, Float>?>` (null until first save, returning `(x_dp, y_dp)`) and `setPosition(xDp: Float, yDp: Float)`.
- Both DataStores use `Context.dataStore` extension via `preferencesDataStore` delegate, file names `settings.preferences_pb` and `overlay_position.preferences_pb`.
- DI provides both as `single`.

**Commit:** `feat: add DataStore for settings and overlay position`

---

### Task 2.8: PasteAccessibilityService + holder + PasteController

**What:** Create the AccessibilityService that registers itself in a static `AtomicReference` on first connect; create `PasteController` that reads the holder and performs paste.

**Files:**
- Create: `android/app/src/main/kotlin/ru/er_log/dictate/core/accessibility/PasteAccessibilityService.kt`
- Create: `android/app/src/main/kotlin/ru/er_log/dictate/core/accessibility/PasteController.kt` (interface)
- Create: `android/app/src/main/kotlin/ru/er_log/dictate/core/accessibility/PasteControllerImpl.kt` (implementation)
- Create: `android/app/src/main/kotlin/ru/er_log/dictate/core/accessibility/PasteResult.kt` (sealed class: `Success`, `NoFocus`, `NotAvailable`)
- Create: `android/app/src/main/kotlin/ru/er_log/dictate/core/accessibility/AccessibilityModule.kt`
- Create: `android/app/src/main/res/xml/accessibility_service_config.xml`
- Modify: `android/app/src/main/AndroidManifest.xml` (register service + meta-data)
- Modify: `android/app/src/main/res/values/strings.xml` (add `accessibility_service_description`)
- Modify: `android/app/src/main/kotlin/ru/er_log/dictate/core/di/AppModule.kt`

**Spec sections to read:** "Core: AccessibilityService" — entire section.

**Definition of done:**
- Service config XML matches spec exactly, including `flagRetrieveInteractiveWindows`.
- `PasteAccessibilityService` has `companion object { val holder = AtomicReference<PasteAccessibilityService?>() }`. Sets in `onServiceConnected`, clears in `onUnbind` and `onDestroy`.
- `onAccessibilityEvent` is empty / no-op (focus is searched per-call via `findFocus`).
- `interface PasteController { suspend fun paste(text: String): PasteResult }` — pure Kotlin interface, no Android dependencies (so it can be faked in unit tests).
- `class PasteControllerImpl(private val context: Context) : PasteController` does the work:
  - holder null → `PasteResult.NotAvailable`.
  - Copy text to `ClipboardManager`.
  - Find focused editable node: `rootInActiveWindow.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)` first; fallback iterate `windows` and `findFocus` on each.
  - None → `PasteResult.NoFocus`.
  - Try `node.performAction(ACTION_PASTE)`; if false, `node.performAction(ACTION_SET_TEXT, ...)`.
  - → `PasteResult.Success`.
- DI binds: `single<PasteController> { PasteControllerImpl(androidContext()) }`. Consumers depend on the interface only.
- The service does NOT need to live in Koin; only `PasteController` does.
- (Note: an earlier paragraph in the spec says "returns `false`" in plain English — that wording predates the `PasteResult` sealed class; the correct return is `PasteResult.NotAvailable`.)

**Commit:** `feat: add accessibility service and PasteController for cross-app text insertion`

---

### Task 2.9: FloatingButtonService skeleton

**What:** Foreground service with a notification, drawing a circular `View` via `WindowManager.TYPE_APPLICATION_OVERLAY`. No gestures yet (next task) — just a static button positioned at the saved/default location.

**Files:**
- Create: `android/app/src/main/kotlin/ru/er_log/dictate/core/overlay/FloatingButtonService.kt`
- Create: `android/app/src/main/kotlin/ru/er_log/dictate/core/overlay/OverlayView.kt` (the View subclass)
- Create: `android/app/src/main/kotlin/ru/er_log/dictate/core/overlay/OverlayModule.kt`
- Create: `android/app/src/main/res/drawable/ic_mic.xml` (vector — simple mic icon)
- Modify: `android/app/src/main/AndroidManifest.xml` (register service with `foregroundServiceType="microphone"` + permissions `RECORD_AUDIO`, `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`, `INTERNET`, `VIBRATE`)
- Modify: `android/app/src/main/res/values/strings.xml` (`floating_notification_text`, etc.)
- Modify: `android/app/src/main/kotlin/ru/er_log/dictate/core/di/AppModule.kt`

**Spec sections to read:** "Core: overlay (the floating button)" — first paragraph and "Position persistence"; "Manifest highlights".

**Definition of done:**
- Service `FloatingButtonService : Service` with `onStartCommand` calling `startForeground(NOTIFICATION_ID, notification)`. Notification channel created on API 26+. Notification is non-interactive; tap dismisses (or no-op).
- Inside service: lazy `OverlayView`, added via `WindowManager.addView` with `LayoutParams(width, height, TYPE_APPLICATION_OVERLAY, FLAG_NOT_FOCUSABLE or FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT)`.
- Position read from `OverlayPositionStore` on start; default center-right of screen.
- Removed in `onDestroy`.
- App can request the service via `ContextCompat.startForegroundService(context, intent)`. Not yet wired to UI — the test is via `adb shell am start-foreground-service -n ru.er_log.dictate/.core.overlay.FloatingButtonService`.

**Commit:** `feat: floating button service with overlay view scaffolding`

---

### Task 2.10: Floating button gesture state machine

**What:** Implement the IDLE / DRAG / RECORD state machine on `OverlayView` per spec, including `ACTION_CANCEL` and multi-touch ignore. No recording or network yet — gestures only emit callbacks via an injected `OverlayGestureListener`.

**Files:**
- Modify: `android/app/src/main/kotlin/ru/er_log/dictate/core/overlay/OverlayView.kt`
- Create: `android/app/src/main/kotlin/ru/er_log/dictate/core/overlay/OverlayGestureListener.kt` (interface)

**Spec sections to read:** "Core: overlay → Gesture model".

**Definition of done:**
- `OverlayView` accepts an `OverlayGestureListener` via constructor or setter:
  ```kotlin
  interface OverlayGestureListener {
      fun onDragStart()
      fun onDrag(deltaXPx: Float, deltaYPx: Float)
      fun onDragEnd(finalXPx: Int, finalYPx: Int)
      fun onRecordStart()
      fun onRecordEnd()
      fun onRecordCancel()  // for ACTION_CANCEL during RECORD
  }
  ```
- `setOnTouchListener` implements the state machine exactly per spec: 200 ms timer, `touchSlop` from `ViewConfiguration.get(context).scaledTouchSlop`, `ACTION_POINTER_DOWN` ignored.
- Visual feedback in RECORD mode: change tint (e.g., red) via `setBackgroundTintList`. Vibration is NOT in this task — it lives in the service-side listener implementation in Task 2.11 (the view does not own settings or system services).
- Save position to DataStore: handled by `FloatingButtonService` in its listener implementation, NOT by the view directly.
- The view's `WindowManager.LayoutParams.x` and `.y` are updated during DRAG via `windowManager.updateViewLayout(this, layoutParams)` — done from the service's listener (or expose a callback for the service).

**Commit:** `feat: gesture state machine on floating button (drag, record, cancel)`

---

### Task 2.11: End-to-end dictation flow in service

**What:** Wire `FloatingButtonService` to actually record audio, POST to server through a domain repository, paste, log to DB, and handle errors per spec. Add the `DictateRepository` abstraction so the service does not import Retrofit directly (Clean Architecture: `core/overlay` does not depend on `core/network`).

**Files:**
- Create: `android/app/src/main/kotlin/ru/er_log/dictate/core/transcribe/DictateRepository.kt` (interface, pure Kotlin)
- Create: `android/app/src/main/kotlin/ru/er_log/dictate/core/transcribe/DictateRepositoryImpl.kt` (data layer; calls `DictateApi`)
- Create: `android/app/src/main/kotlin/ru/er_log/dictate/core/transcribe/TranscribeResult.kt` (sealed: `Success(text, words, durationSec)`, `NetworkError`, `ServerError(code)`)
- Create: `android/app/src/main/kotlin/ru/er_log/dictate/core/transcribe/TranscribeModule.kt`
- Modify: `android/app/src/main/kotlin/ru/er_log/dictate/core/overlay/FloatingButtonService.kt`
- Create: `android/app/src/main/kotlin/ru/er_log/dictate/feature/stats/data/RecognitionLogger.kt` (small wrapper around DAO; coroutine-friendly insert)
- Create: `android/app/src/main/kotlin/ru/er_log/dictate/feature/stats/data/StatsModule.kt`
- Modify: `android/app/src/main/kotlin/ru/er_log/dictate/core/di/AppModule.kt`

**Spec sections to read:** "Data flow: end-to-end dictation"; "Error handling"; "Service-to-app binding (lifecycle-safe)" (where `PasteController` is consumed).

**Definition of done:**
- `interface DictateRepository { suspend fun transcribe(wav: ByteArray): TranscribeResult }`. Pure Kotlin, no Android imports.
- `DictateRepositoryImpl(private val api: DictateApi) : DictateRepository` wraps Retrofit calls in `runCatching` and maps to `TranscribeResult`.
- DI: `single<DictateRepository> { DictateRepositoryImpl(get()) }`.
- Service implements `OverlayGestureListener`:
  - `onRecordStart()` → reads `vibrateOnRecord` from `SettingsStore` (one-shot via `runBlocking { firstOrNull() }` — acceptable here because IDLE→RECORD is on the main thread and the read is sub-ms); if true, vibrate (`VibrationEffect.createOneShot(50, ...)`); start `WavRecorder`.
  - `onRecordEnd()` → stops recorder, launches a coroutine on `Dispatchers.IO`:
    1. `dictateRepository.transcribe(wavBytes)`.
    2. On `TranscribeResult.Success`:
       - `logger.log(RecognitionEvent(now, words, durationSec))` (always — successful recognition counts).
       - Try `pasteController.paste(text)`:
         - `Success` → no UI feedback.
         - `NoFocus` → text already on clipboard via `PasteControllerImpl`; toast "Нет активного поля — текст в буфере".
         - `NotAvailable` → fall back to clipboard-only mode: copy `text` via `ClipboardManager`; toast "Включите спец. возможности в настройках" (the clipboard toggle setting is irrelevant here — the fallback is unconditional when accessibility is off).
       - If user's `clipboardOnPaste` setting is true AND paste succeeded, also write `text` to clipboard via `ClipboardManager` (so the clipboard ends with the dictated text).
    3. On `TranscribeResult.NetworkError`: vibrate twice (`VibrationEffect.createWaveform(longArrayOf(0, 80, 80, 80), -1)`); toast "Сервер недоступен"; do NOT log stats.
    4. On `TranscribeResult.ServerError`: vibrate twice; toast "Ошибка сервера"; do NOT log stats.
  - `onRecordCancel()` → if a recognition coroutine is in flight, cancel it; ignore the audio.
  - `onDragEnd(...)` → save position via `OverlayPositionStore.setPosition(xDp, yDp)` (convert from px using `displayMetrics.density`).
- DI dependencies are obtained in `onCreate` via `getKoin().get<...>()`. Service holds them as `private val` properties.

**Commit:** `feat: end-to-end dictation flow with DictateRepository abstraction`

---

### Task 2.12: Quick Settings tile

**What:** `OverlayTileService` toggles the floating button service on/off when the user taps the Quick Settings tile.

**Files:**
- Create: `android/app/src/main/kotlin/ru/er_log/dictate/core/tile/OverlayTileService.kt`
- Modify: `android/app/src/main/AndroidManifest.xml` (register tile service)
- Modify: `android/app/src/main/res/values/strings.xml` (`tile_label`)
- Create: `android/app/src/main/res/drawable/ic_tile.xml`

**Spec sections to read:** "Core: Quick Settings tile".

**Definition of done:**
- `OverlayTileService : TileService` with `onClick` that:
  - If `qsTile.state == STATE_INACTIVE`: `ContextCompat.startForegroundService(this, Intent(this, FloatingButtonService::class.java))`, set tile state ACTIVE.
  - Else: `stopService(Intent(this, FloatingButtonService::class.java))`, set tile state INACTIVE.
- `onStartListening` queries the service running state (e.g., via a static AtomicBoolean on `FloatingButtonService` set in `onCreate`/`onDestroy`) and updates the tile state accordingly.
- Manifest entry per spec.

**Commit:** `feat: quick settings tile to toggle floating button overlay`

---

### Task 2.13: feature/permissions

**What:** Domain layer (status checks, intent factory) + UI Composable card with bottom sheet for accessibility instructions.

**Files:**
- Create: `android/app/src/main/kotlin/ru/er_log/dictate/feature/permissions/domain/Permission.kt` (sealed: `Microphone`, `Overlay`, `Accessibility`)
- Create: `android/app/src/main/kotlin/ru/er_log/dictate/feature/permissions/domain/PermissionStatus.kt`
- Create: `android/app/src/main/kotlin/ru/er_log/dictate/feature/permissions/domain/PermissionsRepository.kt` (interface)
- Create: `android/app/src/main/kotlin/ru/er_log/dictate/feature/permissions/data/PermissionsRepositoryImpl.kt`
- Create: `android/app/src/main/kotlin/ru/er_log/dictate/feature/permissions/data/PermissionsModule.kt`
- Create: `android/app/src/main/kotlin/ru/er_log/dictate/feature/permissions/ui/PermissionsCard.kt` (Composable)
- Create: `android/app/src/main/kotlin/ru/er_log/dictate/feature/permissions/ui/AccessibilityHelpSheet.kt` (Composable bottom sheet)
- Create: `android/app/src/main/kotlin/ru/er_log/dictate/feature/permissions/ui/PermissionsViewModel.kt`
- Modify: `android/app/src/main/kotlin/ru/er_log/dictate/core/di/AppModule.kt`

**Spec sections to read:** "feature/permissions"; "feature/home → Permissions / status card".

**Definition of done:**
- `PermissionsRepository.observe(): Flow<Map<Permission, PermissionStatus>>` reflecting current grant state. The flow emits initially and on lifecycle resume (use `lifecycleEventFlow` or recompute on screen resume).
- Each permission has a `pending intent factory` for opening the corresponding system screen (`ACTION_MANAGE_OVERLAY_PERMISSION` for overlay, `ACTION_ACCESSIBILITY_SETTINGS` for accessibility, request-permission API for mic).
- `PermissionsCard` Composable: shows three rows with status icon + label + "Grant" button. When all three are granted, the entire card is replaced by a single "✓ Ready to dictate" line.
- "Grant" for accessibility opens `AccessibilityHelpSheet` first; the sheet has a `[Open Settings]` action button that fires the intent.
- Tests: unit test for `PermissionsViewModel` using a fake `PermissionsRepository` (verify state aggregation and "all granted" collapse).

**Commit:** `feat: feature/permissions card with grant flows and accessibility help sheet`

---

### Task 2.14: feature/stats domain + data

**What:** Domain use cases (`GetWordsForPeriodUseCase`) + data layer queries on Room. UI lives in feature/home (next task).

**Files:**
- Create: `android/app/src/main/kotlin/ru/er_log/dictate/feature/stats/domain/Period.kt` (sealed: `Week`, `Month`, `Year` — matching the home-screen tabs)
- Create: `android/app/src/main/kotlin/ru/er_log/dictate/feature/stats/domain/StatsRepository.kt` (interface)
- Create: `android/app/src/main/kotlin/ru/er_log/dictate/feature/stats/domain/GetWordsForPeriodUseCase.kt`
- Create: `android/app/src/main/kotlin/ru/er_log/dictate/feature/stats/data/StatsRepositoryImpl.kt`
- Modify: `android/app/src/main/kotlin/ru/er_log/dictate/feature/stats/data/StatsModule.kt` (extend from Task 2.11 to bind repo + use case)
- Create: `android/app/src/test/kotlin/ru/er_log/dictate/feature/stats/domain/GetWordsForPeriodUseCaseTest.kt`

**Spec sections to read:** "feature/stats".

**Definition of done:**
- `Period` exposes `range(): Pair<Long, Long>` (UTC-ms inclusive start, exclusive end) computed from `Clock.System.now()` (kotlinx-datetime, dependency added in Task 2.6).
- `GetWordsForPeriodUseCase(period: Period): Flow<Long>` emits totals reactively as new events arrive. Maps DAO `Flow<Long?>` → `Flow<Long>` via `?: 0L`.
- Unit test injects a fake repository returning a fixed `Flow<Long?>` (using `flowOf` or `MutableStateFlow`); asserts the use case maps null → 0 and forwards numbers unchanged. Use Turbine for collection assertions.
- `Period.Week` returns Mon (start of week, local timezone) → next Mon (exclusive); `Period.Month` returns the first day of the current month → first day of next; `Period.Year` returns Jan 1 → Jan 1 next year. All boundaries computed in the device's local timezone, then converted to UTC ms.

**Commit:** `feat: stats domain layer with Period, GetWordsForPeriodUseCase, and Room repository`

---

### Task 2.15: feature/settings

**What:** Compose settings screen wired to `SettingsStore`. Two toggles per spec.

**Files:**
- Create: `android/app/src/main/kotlin/ru/er_log/dictate/feature/settings/domain/SettingsRepository.kt` (interface)
- Create: `android/app/src/main/kotlin/ru/er_log/dictate/feature/settings/data/SettingsRepositoryImpl.kt` (wraps `SettingsStore`)
- Create: `android/app/src/main/kotlin/ru/er_log/dictate/feature/settings/ui/SettingsScreen.kt` (Composable)
- Create: `android/app/src/main/kotlin/ru/er_log/dictate/feature/settings/ui/SettingsViewModel.kt`
- Create: `android/app/src/main/kotlin/ru/er_log/dictate/feature/settings/SettingsModule.kt`
- Modify: `android/app/src/main/kotlin/ru/er_log/dictate/core/di/AppModule.kt`

**Spec sections to read:** "feature/settings".

**Definition of done:**
- Repository interface in domain has `clipboardOnPaste: Flow<Boolean>`, `vibrateOnRecord: Flow<Boolean>`, suspend setters.
- `SettingsScreen` shows two `SwitchPreference`-style rows (use Material 3 `ListItem` + `Switch`). Each row binds to a state in `SettingsViewModel`.
- ViewModel uses `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initial)` for both flows.
- Toggles persist immediately (no Save button).

**Commit:** `feat: settings screen with clipboard and vibration toggles`

---

### Task 2.16: feature/home (UI)

**What:** Compose home screen composing the stats card, permissions card, overlay toggle button.

**Files:**
- Create: `android/app/src/main/kotlin/ru/er_log/dictate/feature/home/ui/HomeScreen.kt`
- Create: `android/app/src/main/kotlin/ru/er_log/dictate/feature/home/ui/HomeViewModel.kt`
- Create: `android/app/src/main/kotlin/ru/er_log/dictate/feature/home/ui/StatsCard.kt`
- Create: `android/app/src/main/kotlin/ru/er_log/dictate/feature/home/HomeModule.kt`
- Modify: `android/app/src/main/kotlin/ru/er_log/dictate/core/di/AppModule.kt`
- Create: `android/app/src/test/kotlin/ru/er_log/dictate/feature/home/ui/HomeViewModelTest.kt`

**Spec sections to read:** "feature/home"; "Statistics".

**Definition of done:**
- `HomeViewModel` exposes `StateFlow<HomeUiState>` containing: selected `Period`, `wordsForPeriod: Long`, `permissionsState`, `overlayEnabled: Boolean`.
- `StatsCard`: tabs row (week / month / year), big number, period label.
- `HomeScreen` lays out: top — `StatsCard`; middle — `PermissionsCard` (from feature/permissions); bottom — `Button` "Enable floating button" / "Disable" that calls `startForegroundService` / `stopService`.
- ViewModel test: fake use case returning `flowOf(123L)`, fake permissions repo, assert `wordsForPeriod == 123` after init.
- Material 3 styling, padding, scrollable column.

**Commit:** `feat: home screen with stats card, permissions, overlay toggle`

---

### Task 2.17: MainActivity navigation

**What:** Connect Home and Settings via Compose navigation.

**Files:**
- Modify: `android/app/src/main/kotlin/ru/er_log/dictate/MainActivity.kt`
- Modify: `android/app/build.gradle.kts` (add `androidx.navigation:navigation-compose`)
- Modify: `android/gradle/libs.versions.toml`

**Spec sections to read:** "Single activity, Compose nav graph" (under Repository layout).

**Definition of done:**
- One `NavHost` with two destinations: `home`, `settings`.
- App bar with title "Dictate" and a settings cog icon top-right that navigates to `settings`.
- Settings screen has back arrow.

**Commit:** `feat: nav host wiring home and settings screens`

---

### Task 2.18: Network security config + final manifest review

**What:** Allow plain HTTP only for the LAN ranges. Audit manifest for completeness against the spec.

**Files:**
- Create: `android/app/src/main/res/xml/network_security_config.xml`
- Modify: `android/app/src/main/AndroidManifest.xml` (set `android:networkSecurityConfig="@xml/network_security_config"`, `android:usesCleartextTraffic="true"`)

**Spec sections to read:** "Manifest highlights" full block.

**Definition of done:**
- Network security config permits cleartext to `192.168.0.0/16`, `10.0.0.0/8`, `172.16.0.0/12`, and `10.0.2.2` (emulator host) only.
- All permissions / services / metadata from the spec are present in the manifest. Run a quick grep checklist:
  - `RECORD_AUDIO`, `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`, `INTERNET`, `VIBRATE`, `BIND_ACCESSIBILITY_SERVICE` (declared on the service), `BIND_QUICK_SETTINGS_TILE`.
  - Three services present: `FloatingButtonService` (`foregroundServiceType="microphone"`), `PasteAccessibilityService`, `OverlayTileService`.

**Commit:** `chore: network security config and manifest finalization`

---

### Task 2.19: README — Android section

**What:** Add a top-level "Android client" section to both READMEs covering install, server URL config, permissions, usage.

**Files:**
- Modify: `README.md`
- Modify: `README.ru.md`

**Definition of done:**
- New section after "How it runs" titled "Android client (optional)".
- Steps: clone, edit `android/app/build.gradle.kts` `SERVER_URL`, `./gradlew installDebug`, grant the three permissions in-app, enable the QS tile or tap "Enable" on home, hold the floating button to dictate.
- One screenshot OPTIONAL — the implementer may skip if no screenshot is available.

**Commit:** `docs: README sections for android client`

---

### Task 2.20: ADB-driven smoke test script

**What:** Bash + PowerShell scripts at `android/scripts/` that automate install + permission grants + service launch on a connected emulator. The script does not test the audio path (out of scope per spec) but verifies the app launches without crashing and the overlay shows up.

**Files:**
- Create: `android/scripts/adb_smoke.sh`
- Create: `android/scripts/adb_smoke.ps1`

**Spec sections to read:** "Testing strategy → Phase 2 (Android) → End-to-end test".

**Definition of done:**
- Both scripts perform these steps:
  1. `adb wait-for-device`.
  2. `cd android && ./gradlew installDebug` (Linux/macOS) or `gradlew.bat installDebug` (Windows).
  3. Grant `RECORD_AUDIO` via `adb shell pm grant ru.er_log.dictate android.permission.RECORD_AUDIO`.
  4. Grant overlay via `adb shell appops set ru.er_log.dictate SYSTEM_ALERT_WINDOW allow`.
  5. Enable accessibility via `adb shell settings put secure enabled_accessibility_services ru.er_log.dictate/ru.er_log.dictate.core.accessibility.PasteAccessibilityService` and `adb shell settings put secure accessibility_enabled 1`.
  6. Clear logcat: `adb logcat -c`.
  7. Launch app: `adb shell am start -n ru.er_log.dictate/.MainActivity`; `sleep 3`.
  8. Crash check: `adb logcat -d -b crash | grep -i ru.er_log.dictate` — if any output, fail with the captured crash.
  9. Start overlay: `adb shell am start-foreground-service -n ru.er_log.dictate/.core.overlay.FloatingButtonService`; `sleep 2`.
  10. Confirm overlay present: `adb shell dumpsys window windows | grep -i FloatingButtonService` (or just `OverlayView`) — fail if absent.
  11. Discover overlay coordinates: `adb shell dumpsys window windows | grep -E "Frame:|FloatingButton"` and parse the rect; default to screen center-right (e.g., `$(width-100) $(height/2)`).
  12. Simulate a tap on the button (idempotency check — should not crash even though the tap won't trigger record-mode without a hold): `adb shell input tap <x> <y>`; `sleep 1`; re-run step 8 (crash check).
  13. Print a clear PASS / FAIL summary at the end.
- Script exits non-zero on any failed step.
- README link to the script at the end of the Android section.

**Commit:** `test: adb-driven smoke test scripts for android`

---

## Final review

After all Phase 2 tasks complete:

- [ ] Round 1 review: dispatch `superpowers:code-reviewer` agent with model=sonnet on the entire branch (Phase 1 + Phase 2). Focus areas:
  - Clean Architecture compliance (no Android imports in `domain/`; no Compose in `data/`).
  - Coroutine usage (no blocking calls on main; cancellation safety).
  - Koin module boundaries.
  - Compose recomposition correctness.
  - Accessibility / paste edge cases.
  - Security (no leaked data, no unintentional `exported=true`).
  - Match against spec — anything missing?
- [ ] Apply review fixes (each fix as a separate commit).
- [ ] Round 2 review: dispatch a fresh `superpowers:code-reviewer` agent on the post-fix branch. Focus on the same areas but specifically asks "did the fixes from round 1 introduce any new issues?"
- [ ] Apply round 2 fixes (separate commits).
- [ ] Run the ADB smoke script if a connected emulator is available; record PASS/FAIL.
- [ ] Self-suspend the computer.

---

## Self-review of this plan

**Spec coverage:**
- Server `/health`, `/transcribe`, hallucination filter, `_MODEL` → Tasks 1.2, 1.3, 1.4 ✓
- WAV utils + tests → Task 1.1 ✓
- Client startup wizard, auto-spawn, recognize-as-HTTP → Tasks 1.6, 1.7 ✓
- Per-OS scripts updated → Task 1.8 ✓
- requirements.txt update → Task 1.10 ✓
- READMEs → Tasks 1.11, 2.19 ✓
- Android scaffolding + Compose + Koin + Coroutines + test deps (Turbine, MockK) → Tasks 2.1, 2.2 ✓
- Theme with default seed color → Task 2.3 ✓
- Network with `BuildConfig.SERVER_URL` (from `local.properties`) + sanity test → Task 2.4 ✓
- WavRecorder + instrumented test → Task 2.5 ✓
- Room + kotlinx-datetime → Task 2.6 ✓
- DataStore (settings + position) → Task 2.7 ✓
- Accessibility service + holder + `interface PasteController` + `PasteControllerImpl` + `flagRetrieveInteractiveWindows` → Task 2.8 ✓
- FloatingButtonService + WindowManager + foreground notification → Task 2.9 ✓
- Gesture state machine (ACTION_CANCEL + multi-touch ignore) → Task 2.10 ✓
- End-to-end dictation flow with `DictateRepository` abstraction, vibration on record start, vibrate-twice on failure, clipboard-only fallback when accessibility off → Task 2.11 ✓
- Quick Settings tile → Task 2.12 ✓
- feature/permissions with help sheet → Task 2.13 ✓
- feature/stats domain (Period: Week/Month/Year) → Task 2.14 ✓
- feature/settings → Task 2.15 ✓
- feature/home → Task 2.16 ✓
- MainActivity nav → Task 2.17 ✓
- Network security + manifest → Task 2.18 ✓
- ADB smoke scripts (install, perms, launch, crash check, overlay check, tap test) → Task 2.20 ✓

**Type consistency:**
- `RecognitionEvent` declared 2.6, used in 2.11 (logger), 2.14 (queries) ✓
- `PasteController` (interface, Task 2.8) + `PasteControllerImpl` (Task 2.8) + `PasteResult` (Task 2.8) — consumed in Task 2.11 ✓
- `DictateRepository` (interface, Task 2.11) + `DictateRepositoryImpl` (Task 2.11) + `TranscribeResult` (Task 2.11) — self-contained ✓
- `OverlayGestureListener` (Task 2.10) → implemented in Task 2.11 ✓
- `Permission` sealed class (Task 2.13) — consumed in `HomeViewModel` (Task 2.16) via `PermissionsRepository` ✓
- `BuildConfig.SERVER_URL` declared 2.4, consumed in 2.4 (Retrofit), validated 2.4 (sanity test) ✓
- `Period.Week`, `Period.Month`, `Period.Year` — three variants matching the three home tabs ✓

**Placeholder scan:** no "TBD" / "TODO" / "implement later".
