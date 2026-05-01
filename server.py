# Copyright (c) 2026 Eldar Timraleev

import os
import re
import sys
import json
import subprocess
import importlib.util

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
CONFIG_FILE = os.path.join(SCRIPT_DIR, "voice_key.json")
HALLUC_FILE = os.path.join(SCRIPT_DIR, "hallucinations.txt")
HOST = "0.0.0.0"
PORT = 7531

# ---- ASR settings (edit to change) ----
DEVICE = "cuda"
LANGUAGE = "ru"  # e.g. "en", "de", "uk", "es". Use None for auto-detect.

# ============== STEP 1: Validate config (before any heavy imports) ======

try:
    with open(CONFIG_FILE, "r", encoding="utf-8") as _f:
        _cfg = json.load(_f)
except FileNotFoundError:
    print("voice_key.json not found. Run client.py first to set up your hotkey and model.", file=sys.stderr)
    sys.exit(1)
except Exception as _e:
    print(f"voice_key.json is unreadable: {_e}. Run client.py first.", file=sys.stderr)
    sys.exit(1)

_BACKEND = _cfg.get("backend")
_MODEL_NAME = _cfg.get("model_name")
if not _BACKEND or not _MODEL_NAME:
    print("voice_key.json is missing 'backend' or 'model_name'. Run client.py first.", file=sys.stderr)
    sys.exit(1)


# ============== STEP 2: Install server core deps ==============================

SERVER_CORE_DEPS = [
    ("flask", "flask"),
    ("numpy", "numpy"),
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


_ensure_deps(SERVER_CORE_DEPS, "server core")


# ============== STEP 3: Install backend-specific deps =========================

if _BACKEND == "gigaam":
    _ensure_deps(GIGAAM_DEPS, "GigaAM")
else:
    _ensure_deps(WHISPER_DEPS, "Whisper")


# ============== HALLUCINATION FILTER =========================================

class HallucinationFilter:
    def __init__(self, filepath):
        self.exact = set()
        self.substrings = []
        self.regexes = []
        self._load(filepath)

    def _load(self, filepath):
        if not os.path.exists(filepath):
            print(f"Warning: hallucinations file not found: {filepath}. Proceeding with empty filter.")
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
                        print(f"Warning: bad regex '{line}': {e}")
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


# ============== MODEL LOADING ================================================

def load_model(backend, model_name):
    if backend == "gigaam":
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
        from faster_whisper import WhisperModel
        print(f"Loading Whisper {model_name} on {DEVICE}...")
        return WhisperModel(model_name, device=DEVICE, compute_type="float16")


# ============== RECOGNITION ==================================================

def recognize(audio_flat, prev_text=""):
    if _BACKEND == "gigaam":
        import torch
        asr = _MODEL.model
        wav = torch.FloatTensor(audio_flat).to(asr._device).to(asr._dtype).unsqueeze(0)
        length = torch.full([1], wav.shape[-1], device=asr._device)
        with torch.inference_mode():
            encoded, encoded_len = asr.forward(wav, length)
            result = asr.decoding.decode(asr.head, encoded, encoded_len)[0]
        return result.strip() if result else ""
    else:
        segments, _ = _MODEL.transcribe(
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


# ============== STEP 4: Load hallucination filter + model ====================

print("Loading hallucination filter...")
_HALLUC = HallucinationFilter(HALLUC_FILE)

print(f"Loading model ({_BACKEND} / {_MODEL_NAME})...")
_MODEL = load_model(_BACKEND, _MODEL_NAME)
print("Model is ready.")

# ============== FLASK APP ====================================================

import wav_utils
from flask import Flask, jsonify, request

app = Flask(__name__)


@app.route("/health")
def health():
    return jsonify({"ready": _MODEL is not None, "backend": _BACKEND, "model": _MODEL_NAME})


@app.route("/transcribe", methods=["POST"])
def transcribe():
    try:
        audio_array, _ = wav_utils.decode_wav(request.data)
    except ValueError as e:
        return jsonify({"error": str(e)}), 400

    text = recognize(audio_flat=audio_array)
    clean_text, _ = _HALLUC.check(text)
    duration_sec = len(audio_array) / 16000.0
    words = len(clean_text.split())
    return jsonify({"text": clean_text, "words": words, "duration_sec": duration_sec})


if __name__ == "__main__":
    app.run(host=HOST, port=PORT, threaded=False)
