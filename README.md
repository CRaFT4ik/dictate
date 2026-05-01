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
- `python dictate.py --rebind`  — change the hotkey
- `python dictate.py --remodel` — switch the model

---

## ⚙️ GPU / CPU

Both backends run on NVIDIA GPU (default) or CPU. For GPU you need a recent NVIDIA driver — verify with `nvidia-smi`. To force CPU, edit `DEVICE = "cpu"` in `dictate.py`.

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
