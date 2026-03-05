# 🎙️ Local Voice Dictation Hotkey (Embedded Faster-Whisper)

Offline voice dictation that runs an **embedded speech-to-text model locally on your machine** (no cloud): hold a hotkey/mouse button → speak → text is transcribed with Faster-Whisper and **auto-pasted** into the active window.

🇷🇺 Russian version: [README.ru.md](./README.ru.md)

---

## ✨ What you get
- 🔒 **Local / offline** transcription (nothing is sent to the cloud)
- ⚡ **CUDA GPU acceleration** (optional) via `faster-whisper` (CPU also works)
- ⌨️🖱️ **Custom hotkey combo** (keyboard + mouse)
- 📋 **Auto-paste** into the current app (clipboard + paste)
- 🎚️ **Two modes**
  - 🧠 **Full mode (default):** record while pressed → transcribe on release (best quality)
  - 🚀 **Streaming mode:** split by pauses/timer and paste as you speak (faster, less context)
- 🧹 **Hallucination filter** via `hallucinations.txt` (exact / substring / regex rules)

---

## 🚀 Quick start

### 🧩 Install project
1) Clone and create venv
- `git clone <repo-url>`
- `cd <repo-folder>`
- `python -m venv .venv`

2) Activate venv
- Windows: `.venv\Scripts\activate`
- Linux/macOS: `source .venv/bin/activate`

3) Install deps
- `pip install -U pip`
- `pip install -r requirements.txt`

Example `requirements.txt`:
- `faster-whisper`
- `sounddevice`
- `numpy`
- `pyperclip`
- `keyboard`
- `mouse`

---

## 🧠⚡ Install CUDA (GPU)

This project can run on NVIDIA GPUs via CUDA (set `DEVICE="cuda"`). You need:

1) NVIDIA driver
- Install a recent NVIDIA driver for your GPU.
- Verify it works:
  - `nvidia-smi`

2) CUDA Toolkit (optional)
- Often the driver is enough, because many Python packages ship CUDA runtime libraries.
- If you want the full toolkit, install CUDA Toolkit from NVIDIA:
  - CUDA downloads: https://developer.nvidia.com/cuda-downloads
  - Windows install guide: https://docs.nvidia.com/cuda/cuda-installation-guide-microsoft-windows/
  - Linux install guide: https://docs.nvidia.com/cuda/cuda-installation-guide-linux/

3) Install Python dependencies
- `pip install -r requirements.txt`

Important note: faster-whisper / CTranslate2 CUDA compatibility
- `faster-whisper` uses `ctranslate2` under the hood, and CUDA/cuDNN compatibility matters.
- If you get errors like “Could not load cudnn” / “CUDA version mismatch” / “No CUDA device”, try:
  - Make sure `nvidia-smi` works (driver installed correctly)
  - Update your NVIDIA driver
  - Reinstall packages in a clean venv
  - Pin `ctranslate2` to a compatible version (example):
    - `pip install --force-reinstall ctranslate2==4.4.0`

If GPU setup is too painful, switch to CPU:
- Set `DEVICE="cpu"` in `dictate.py`

---

## ▶️ Run
- 🪟 Windows: run `dictate.bat` (as Administrator)
- 🐧 Linux: run `bash dictate.sh`

On first run, hold your desired hotkey combo (~1 second) — it will be saved to `voice_key.json`.

Rebind hotkey:
- `python dictate.py --rebind`

---

## ⚙️ Settings (edit at the top of `dictate.py`)
- `MODEL_NAME` — Whisper model name (e.g. `large-v3-turbo`)
- `DEVICE` — `cuda` or `cpu`
- `LANGUAGE` — language code (e.g. `en`, `ru`, `de`), or `None` for auto-detect
- `STREAMING_MODE` — `False` (best quality) / `True` (paste during speech)

---

## 🧽 Hallucination filter
Edit `hallucinations.txt`:
- exact line match: `some phrase`
- substring match: `~subscribe`
- regex: `^(thanks|thank you).*`
- comments: lines starting with `#`

---

## 📝 Notes
- 🛡️ Uses global keyboard/mouse hooks; admin/root may be required.
- 🐧 On Linux: hotkeys/paste depend on X11 vs Wayland (Wayland may block global hooks/input injection).
- 📋 Auto-paste uses the clipboard temporarily and then restores it.

---

## 📄 License
This project is licensed under the **GNU General Public License v3.0 (GPL-3.0)**.

- You are free to use, modify, and redistribute this software, including commercially, **as long as** any distributed derivative work remains **GPL-3.0 licensed** and you provide the corresponding source code.
- You must keep the copyright and license notices.

See the [LICENSE](./LICENSE) file for details.