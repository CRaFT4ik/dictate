# Copyright (c) 2026 Eldar Timraleev

import os
import sys
import subprocess
import importlib.util

sys.stdout.reconfigure(encoding="utf-8", errors="replace")


# ============== DEPENDENCY AUTO-INSTALL ==============================
# Core packages are needed for the script to even start.
# Per-backend packages (Whisper / GigaAM) are installed lazily inside
# load_model() — no point pulling ~2 GB of GigaAM deps if the user picked
# Whisper, and vice versa.

CORE_DEPS = [
    # (import name, pip name)
    ("numpy",       "numpy"),
    ("copykitten",  "copykitten"),
    ("sounddevice", "sounddevice"),
    ("keyboard",    "keyboard"),
    ("mouse",       "mouse"),
]

WHISPER_DEPS = [
    ("faster_whisper", "faster-whisper"),
]

GIGAAM_DEPS = [
    ("transformers",   "transformers>=4.57.1"),
    ("torch",          "torch"),
    ("torchaudio",     "torchaudio"),
    ("pyannote.audio", "pyannote-audio"),
    ("sentencepiece",  "sentencepiece"),
    ("omegaconf",      "omegaconf"),
    ("hydra",          "hydra-core"),
]


def _ensure_deps(deps, label):
    """Install missing packages from `deps` (list of (import_name, pip_name))."""
    missing = []
    for imp_name, pip_name in deps:
        try:
            if importlib.util.find_spec(imp_name) is None:
                missing.append(pip_name)
        except (ImportError, ValueError):
            missing.append(pip_name)
    if not missing:
        return
    print(f"\nInstalling {label} dependencies: {' '.join(missing)}")
    subprocess.check_call([sys.executable, "-m", "pip", "install", *missing])


_ensure_deps(CORE_DEPS, "core")


import re
import json
import time
import numpy as np
import sounddevice as sd
import copykitten
import keyboard
import mouse

# ========================= SETTINGS =========================
DEVICE = "cuda"
FS = 16000
LANGUAGE = "ru"  # e.g. "en", "de", "uk", "es". Use None for auto-detect (slower/less stable).

# Populated from config at startup by load_or_setup_config():
BACKEND = "whisper"
MODEL_NAME = "large-v3-turbo"

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
CONFIG_FILE = os.path.join(SCRIPT_DIR, "voice_key.json")
HALLUC_FILE = os.path.join(SCRIPT_DIR, "hallucinations.txt")

# ---- OPERATION MODE ----
# False (default) = records while the key is held, recognizes after release.
#                   Best quality: Whisper sees the whole phrase at once.
# True            = streaming: splits speech into chunks by pauses/timer
#                   and inserts while recording. Faster, but less coherent.
STREAMING_MODE = False

# Streaming timings (only for STREAMING_MODE = True)
SILENCE_RMS = 0.012
SILENCE_FOR_PHRASE = 0.7
MAX_PHRASE_SEC = 4.0
MIN_PHRASE_SEC = 0.3
RMS_WINDOW = 0.2
SPEECH_START_RMS = 0.015
# =============================================================


# ============== HALLUCINATION FILTER =========================

class HallucinationFilter:
    def __init__(self, filepath):
        self.exact = set()
        self.substrings = []
        self.regexes = []
        self._load(filepath)

    def _load(self, filepath):
        if not os.path.exists(filepath):
            print(f"⚠ Hallucinations file not found: {filepath}")
            return
        with open(filepath, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line or line.startswith("#"):
                    continue
                if line.startswith("^"):
                    try:
                        self.regexes.append(re.compile(line, re.IGNORECASE | re.UNICODE))
                    except re.error as e:
                        print(f"⚠ Bad regex '{line}': {e}")
                elif line.startswith("~"):
                    self.substrings.append(line[1:].strip().lower())
                else:
                    self.exact.add(self._normalize(line))
        print(f"Hallucination filter: {len(self.exact)} exact, "
              f"{len(self.substrings)} substrings, {len(self.regexes)} regex")

    @staticmethod
    def _normalize(text):
        return " ".join(text.lower().split())

    def check(self, text):
        if not text:
            return "", []
        removed = []
        norm = self._normalize(text)

        if norm in self.exact:
            removed.append(("exact", text.strip()))
            return "", removed

        for rx in self.regexes:
            if rx.fullmatch(text.strip()):
                removed.append(("regex", text.strip()))
                return "", removed

        fragments = re.split(r'(?<=[.!?…])\s+', text.strip())
        clean_fragments = []

        for frag in fragments:
            frag_stripped = frag.strip()
            if not frag_stripped:
                continue
            frag_norm = self._normalize(frag_stripped)
            is_halluc = False

            if frag_norm in self.exact:
                removed.append(("exact", frag_stripped))
                is_halluc = True

            if not is_halluc:
                for sub in self.substrings:
                    if sub in frag_norm:
                        removed.append(("substr", frag_stripped))
                        is_halluc = True
                        break

            if not is_halluc:
                for rx in self.regexes:
                    if rx.fullmatch(frag_stripped) or rx.search(frag_stripped):
                        removed.append(("regex", frag_stripped))
                        is_halluc = True
                        break

            if not is_halluc:
                clean_fragments.append(frag_stripped)

        clean = " ".join(clean_fragments).strip()
        return clean, removed


# ============== HOTKEY SETUP ====================

def record_hotkey():
    """
    Listens for a key/combination/mouse button press.
    Supports arbitrary combos: ctrl+shift+z, mouse_x+keyboard_f1, etc.
    Returns dict: {"keys": [{"type": "keyboard"|"mouse", "key": "..."}]}
    """
    print("\n" + "=" * 60)
    print("  RECORD KEY / COMBINATION SETUP")
    print("=" * 60)
    print("  Hold the desired key, key combo, or mouse button.")
    print("  You can combine keyboard + mouse.")
    print("  Hold for ~1 second, then release.")
    print("  Waiting...")
    print()

    time.sleep(0.5)

    pressed_kb = set()   # Names of pressed keyboard keys
    pressed_ms = set()   # Names of pressed mouse buttons
    capture_done = False

    def on_key(event):
        if event.event_type == "down":
            pressed_kb.add(event.name)
        # Ignore "up" to collect simultaneous presses

    def on_mouse(event):
        if isinstance(event, mouse.ButtonEvent) and event.event_type == "down":
            if event.button == "left":
                return
            pressed_ms.add(event.button)

    keyboard.hook(on_key)
    mouse.hook(on_mouse)

    # Wait for the first press
    while not pressed_kb and not pressed_ms:
        time.sleep(0.01)

    # Give some time to press the full combo
    time.sleep(0.8)

    keyboard.unhook_all()
    mouse.unhook_all()

    # Build result
    keys = []
    for k in sorted(pressed_kb):
        keys.append({"type": "keyboard", "key": k})
    for m in sorted(pressed_ms):
        keys.append({"type": "mouse", "key": m})

    time.sleep(0.3)
    return {"keys": keys}


def format_combo(cfg):
    """Pretty name for the combo."""
    parts = []
    mouse_names = {
        "right": "RMB",
        "middle": "MMB",
        "x": "Mouse X1",
        "x2": "Mouse X2",
    }
    for k in cfg["keys"]:
        if k["type"] == "mouse":
            parts.append(mouse_names.get(k["key"], f"Mouse[{k['key']}]"))
        else:
            parts.append(k["key"].upper())
    return " + ".join(parts)


def is_combo_pressed(cfg):
    """Checks that ALL keys/buttons in the combo are currently held."""
    for k in cfg["keys"]:
        if k["type"] == "mouse":
            if not mouse.is_pressed(k["key"]):
                return False
        else:
            if not keyboard.is_pressed(k["key"]):
                return False
    return True


def select_model_backend():
    print("\n" + "=" * 60)
    print("  MODEL SELECTION")
    print("=" * 60)
    print("  [1]  Whisper large-v3          multilingual, ~3.1 GB VRAM")
    print("  [2]  GigaAM v3 e2e_rnnt       Russian, ~0.8 GB VRAM")
    print("                                 punctuation + normalization")
    print()
    print("  Press 1 or 2:")

    while True:
        ev = keyboard.read_event(suppress=False)
        if ev.event_type == "down" and ev.name in ("1", "2"):
            choice = ev.name
            break

    options = {
        "1": ("whisper", "large-v3", "Whisper large-v3"),
        "2": ("gigaam",  "e2e_rnnt",       "GigaAM v3 e2e_rnnt"),
    }
    backend, model_name, label = options[choice]
    print(f"\n  → Selected: {label}")
    print("  ENTER — confirm, ESC — choose again.")

    while True:
        ev = keyboard.read_event(suppress=False)
        if ev.event_type == "down":
            if ev.name == "enter":
                return backend, model_name
            elif ev.name == "esc":
                return select_model_backend()


def load_or_setup_config():
    rebind  = "--rebind"  in sys.argv
    remodel = "--remodel" in sys.argv

    cfg = {}
    if os.path.exists(CONFIG_FILE):
        try:
            with open(CONFIG_FILE, "r", encoding="utf-8") as f:
                cfg = json.load(f)
        except Exception:
            pass

    # --- Hotkey ---
    hotkey_cfg = {"keys": cfg["keys"]} if cfg.get("keys") else None
    if not rebind and hotkey_cfg:
        print(f"Record key: [{format_combo(hotkey_cfg)}]  (--rebind to change)")
    else:
        hotkey_cfg = record_hotkey()
        label = format_combo(hotkey_cfg)
        print(f"\n  → Selected combo: [{label}]")
        print("  ENTER — confirm, ESC — choose again.")
        while True:
            ev = keyboard.read_event(suppress=False)
            if ev.event_type == "down":
                if ev.name == "enter":
                    break
                elif ev.name == "esc":
                    hotkey_cfg = record_hotkey()
                    print(f"\n  → Selected combo: [{format_combo(hotkey_cfg)}]")
                    print("  ENTER — confirm, ESC — choose again.")

    # --- Model ---
    backend    = cfg.get("backend")
    model_name = cfg.get("model_name")
    model_ok   = backend in ("whisper", "gigaam") and bool(model_name)

    if not remodel and model_ok:
        label = f"GigaAM v3 {model_name}" if backend == "gigaam" else f"Whisper {model_name}"
        print(f"Model: {label}  (--remodel to change)")
    else:
        backend, model_name = select_model_backend()

    new_cfg = {"keys": hotkey_cfg["keys"], "backend": backend, "model_name": model_name}
    with open(CONFIG_FILE, "w", encoding="utf-8") as f:
        json.dump(new_cfg, f, ensure_ascii=False, indent=2)

    time.sleep(0.3)
    return hotkey_cfg, backend, model_name


def load_model(backend, model_name):
    if backend == "gigaam":
        _ensure_deps(GIGAAM_DEPS, "GigaAM")
        import torch
        from transformers import AutoModel
        print(f"Loading GigaAM v3 {model_name}...")
        m = AutoModel.from_pretrained(
            "ai-sage/GigaAM-v3",
            revision=model_name,
            trust_remote_code=True,
        )
        if DEVICE == "cuda":
            m = m.cuda()
        m.eval()
        return m
    else:
        _ensure_deps(WHISPER_DEPS, "Whisper")
        from faster_whisper import WhisperModel
        print(f"Loading Whisper {model_name} on {DEVICE}...")
        return WhisperModel(model_name, device=DEVICE, compute_type="float16")


# ====================== COMMON FUNCTIONS ========================

### Cross-platform clipboard save/restore via copykitten.
### Backed by Rust's arboard crate — talks directly to Win32 / NSPasteboard /
### X11 / Wayland with no external tools (no xclip, pywin32, PyObjC needed).
### Preserves text and images across the dictation paste.

def _clipboard_snapshot():
    """Returns ('text', str), ('image', (rgba_bytes, w, h)), or None."""
    try:
        return ("text", copykitten.paste())
    except copykitten.CopykittenError:
        pass
    try:
        return ("image", copykitten.paste_image())
    except copykitten.CopykittenError:
        pass
    return None


def _clipboard_restore(snapshot):
    if snapshot is None:
        return
    kind, data = snapshot
    try:
        if kind == "text":
            copykitten.copy(data)
        elif kind == "image":
            pixels, w, h = data
            copykitten.copy_image(pixels, w, h)
    except copykitten.CopykittenError:
        pass


_PASTE_HOTKEY = "command+v" if sys.platform == "darwin" else "ctrl+v"


def safe_paste(text):
    snapshot = _clipboard_snapshot()
    copykitten.copy(text)
    time.sleep(0.03)
    keyboard.press_and_release(_PASTE_HOTKEY)
    time.sleep(0.05)
    _clipboard_restore(snapshot)


def get_rms(audio_flat):
    n = int(RMS_WINDOW * FS)
    if len(audio_flat) == 0:
        return 0.0
    chunk = audio_flat[-n:] if len(audio_flat) >= n else audio_flat
    return float(np.sqrt(np.mean(chunk ** 2)))


def recognize(audio_flat, prev_text=""):
    if BACKEND == "gigaam":
        import torch
        asr = model.model
        wav = torch.FloatTensor(audio_flat).to(asr._device).to(asr._dtype).unsqueeze(0)
        length = torch.full([1], wav.shape[-1], device=asr._device)
        with torch.inference_mode():
            encoded, encoded_len = asr.forward(wav, length)
            result = asr.decoding.decode(asr.head, encoded, encoded_len)[0]
        return result.strip() if result else ""
    else:
        segments, _ = model.transcribe(
            audio_flat,
            language=LANGUAGE,
            beam_size=5,
            vad_filter=True,
            vad_parameters=dict(
                min_silence_duration_ms=200,
                speech_pad_ms=100,
            ),
            condition_on_previous_text=True,
            initial_prompt=prev_text[-300:] if prev_text else None,
        )
        return " ".join(s.text.strip() for s in segments).strip()


def filter_and_paste(raw_text, prev_text, halluc_filter):
    """Filters, pastes, and returns updated prev_text."""
    if not raw_text:
        return prev_text

    clean_text, removed = halluc_filter.check(raw_text)

    if removed:
        for rule_type, halluc_text in removed:
            print(f"\n  ⛔ HALLUCINATION [{rule_type}]: «{halluc_text}»", end="", flush=True)

    if not clean_text:
        return prev_text

    prefix = " " if prev_text else ""
    safe_paste(prefix + clean_text)
    print(clean_text, end=" ", flush=True)
    return prev_text + prefix + clean_text


# ============== MODE 1: FULL PHRASE (default) ==============

def record_full(hotkey_cfg, halluc_filter):
    """
    Records all audio while the combo is held.
    Recognizes the FULL phrase after release.
    Best quality — Whisper sees the full context.
    """
    incoming = []

    def callback(indata, frames, time_info, status):
        if status:
            print(status, file=sys.stderr)
        incoming.append(indata.copy())

    with sd.InputStream(samplerate=FS, channels=1, callback=callback):
        print("\n🎙  recording... ", end="", flush=True)

        while is_combo_pressed(hotkey_cfg):
            time.sleep(0.03)

    # Combo released — collect and recognize
    if not incoming:
        print("(empty)")
        return

    audio = np.concatenate(incoming).flatten()
    duration = len(audio) / FS

    if duration < 0.3:
        print(f"(too short: {duration:.1f}s)")
        return

    print(f"({duration:.1f}s) → ", end="", flush=True)

    raw_text = recognize(audio)

    if raw_text:
        filter_and_paste(raw_text, "", halluc_filter)
    else:
        print("(nothing recognized)", end="")

    print(" ✅")


# ============== MODE 2: STREAMING ============================

def record_streaming(hotkey_cfg, halluc_filter):
    """
    Streaming: splits speech by pauses / timer
    and inserts fragments during recording.
    """
    incoming = []

    def callback(indata, frames, time_info, status):
        if status:
            print(status, file=sys.stderr)
        incoming.append(indata.copy())

    phrase_chunks = []
    prev_text = ""
    has_speech = False
    silence_start = None
    phrase_start = None

    def flush_phrase():
        nonlocal prev_text, phrase_chunks, has_speech, silence_start, phrase_start
        if not phrase_chunks:
            return
        audio = np.concatenate(phrase_chunks).flatten()
        if len(audio) / FS >= MIN_PHRASE_SEC:
            raw = recognize(audio, prev_text)
            prev_text = filter_and_paste(raw, prev_text, halluc_filter)
        phrase_chunks.clear()
        has_speech = False
        silence_start = None
        phrase_start = None

    with sd.InputStream(samplerate=FS, channels=1, callback=callback):
        print("\n🎙  ", end="", flush=True)

        while is_combo_pressed(hotkey_cfg):
            time.sleep(0.03)

            if not incoming:
                continue

            new_data = incoming.copy()
            incoming.clear()
            phrase_chunks.extend(new_data)

            current = np.concatenate(phrase_chunks).flatten()
            rms = get_rms(current)
            duration = len(current) / FS
            now = time.time()

            if rms > SPEECH_START_RMS:
                has_speech = True
                silence_start = None
                if phrase_start is None:
                    phrase_start = now
            elif rms < SILENCE_RMS:
                if has_speech and silence_start is None:
                    silence_start = now

            if (has_speech
                    and silence_start is not None
                    and (now - silence_start) >= SILENCE_FOR_PHRASE
                    and duration >= MIN_PHRASE_SEC):
                flush_phrase()

            if (phrase_start is not None
                    and (now - phrase_start) >= MAX_PHRASE_SEC):
                flush_phrase()

        # Recognize the remainder
        if incoming:
            phrase_chunks.extend(incoming)
            incoming.clear()
        flush_phrase()

    print(" ✅")


# ======================== START =============================

hotkey_cfg, BACKEND, MODEL_NAME = load_or_setup_config()

print(f"\nLoading hallucination filter...")
halluc_filter = HallucinationFilter(HALLUC_FILE)

model = load_model(BACKEND, MODEL_NAME)

if BACKEND == "whisper":
    print(f"Selected detection language: {LANGUAGE if LANGUAGE is not None else 'auto-detect'}")

mode_label = "STREAMING (chunked)" if STREAMING_MODE else "FULL (after release)"
print(f"Mode: {mode_label}")
print("Model is ready.")
print(f"\nHold [{format_combo(hotkey_cfg)}] and speak.")
if STREAMING_MODE:
    print("Pauses → text is inserted during recording.")
else:
    print("Release → text will be recognized and inserted.")
# print("Esc — exit.")
print()

record_fn = record_streaming if STREAMING_MODE else record_full

try:
    while True:
        if is_combo_pressed(hotkey_cfg):
            record_fn(hotkey_cfg, halluc_filter)
        time.sleep(0.01)
except KeyboardInterrupt:
    print("\nBye.")