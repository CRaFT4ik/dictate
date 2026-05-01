# 🎙️ Local Voice Dictation

Offline speech-to-text by hotkey: hold a key (or mouse button), speak, release — text is recognized locally and pasted into the active window.

🇷🇺 Russian: [README.ru.md](./README.ru.md)

---

## ✨ Features

- 🔒 **100% offline** — nothing leaves your machine
- 🧠 **Two ASR backends** — pick one at first launch
  - **Whisper large-v3** — multilingual (99 languages)
  - **GigaAM v3 e2e_rnnt** — Russian-only, ~2× more accurate, better punctuation
- ⚡ **CUDA acceleration** (CPU also works)
- ⌨️🖱️ **Custom hotkey** — keyboard, mouse, or any combo
- 📋 **Smart clipboard** — text and images survive the dictation paste
- 📦 **Auto-installs its own dependencies** on first run — no `pip install` needed

---

## 🚀 Quick start

```bash
git clone <repo-url>
cd dictate
```

Then run:

| OS | Command |
|---|---|
| 🪟 Windows | `.\dictate.ps1` (run as Administrator) |
| 🐧 Linux   | `sudo bash dictate.sh` |
| 🍎 macOS   | `bash dictate.sh` |

**On first launch you'll be asked to:**
1. Press your hotkey combo (e.g. `Ctrl + Space`) — saved to `voice_key.json`
2. Pick a model: `[1]` Whisper or `[2]` GigaAM

Both choices are remembered. Only the dependencies for the selected backend are installed.

> Requires Python 3.10+ pre-installed.

**How it runs:** the launch script starts `client.py`, which spawns `server.py` on `127.0.0.1:7531` if it isn't already running. The server keeps running after the client exits — kill it manually with `taskkill /F /IM python.exe` (Windows) / `pkill -f server.py` (macOS / Linux).

**Network access:** by default the server binds to `0.0.0.0:7531`. Loopback works without any firewall changes. To allow LAN access, open the port:
- **Windows** (admin PowerShell): `netsh advfirewall firewall add rule name="Dictate" dir=in action=allow protocol=TCP localport=7531`
- **Linux**: `sudo ufw allow 7531`
- **macOS**: confirm the firewall prompt that appears on first server bind to `0.0.0.0`.

---

## 📱 Android client (optional)

Hold a draggable floating button anywhere on Android, speak, release — text is inserted into the focused input via Accessibility paste.

**Setup:**
1. Edit `android/app/src/main/local.properties` (create if missing): `server.url=http://<your-PC-LAN-IP>:7531`.
2. Build & install on a device on the same Wi-Fi:
   ```bash
   cd android
   ./gradlew installDebug
   ```
3. Launch the app, grant the three requested permissions (mic, draw over, accessibility).
4. Tap "Enable floating button" or pull down the Quick Settings tile labeled "Dictate".
5. Long-press the floating button (~200 ms), speak, release.

The Python server on the PC must be running and reachable on the LAN. Open the firewall port `7531` per the "Network access" instructions above.

**Smoke test (emulator):** see `android/scripts/adb_smoke.sh` (or `.ps1`).

---

## 🎯 Choosing a model

| | Whisper large-v3 | GigaAM v3 e2e_rnnt |
|---|---|---|
| Languages | 99 | Russian only |
| Russian WER | ~21% | **~11%** |
| Punctuation | ✅ | ✅ (notably better, esp. commas) |
| Model size | ~3 GB | ~0.8 GB |
| Extra deps | small | heavy (torch + transformers, ~2 GB) |

Pick **GigaAM** for Russian, **Whisper** for everything else.

---

## ▶️ Usage

Hold the hotkey, speak a phrase, release — recognized text is pasted into the active app.

**Reconfigure:**
- `python client.py --rebind`  — change the hotkey
- `python client.py --remodel` — switch the model

---

## ⚙️ GPU / CPU

Both backends run on NVIDIA GPU (default) or CPU. For GPU you need a recent NVIDIA driver — verify with `nvidia-smi`. To force CPU, edit `DEVICE = "cpu"` in `server.py`.

If `faster-whisper` errors out on CUDA/cuDNN, pin a compatible version: `pip install --force-reinstall ctranslate2==4.4.0`.

---

## 🧽 Hallucination filter

Whisper sometimes hallucinates phrases like "Thanks for watching" on silence. `hallucinations.txt` strips them from the output:

- `some phrase`   — exact match
- `~subscribe`    — substring
- `^thanks.*`     — regex
- `# comment`     — comment

---

## 📝 Notes

- 🛡️ Global keyboard/mouse hooks need admin/root rights on Windows and Linux.
- 🐧 On Linux, Wayland often blocks global hooks — X11 sessions work better.
- 📋 The clipboard is preserved across the paste (text and images both restored, cross-platform).

---

## 📄 License

GPL-3.0 — see [LICENSE](./LICENSE).
