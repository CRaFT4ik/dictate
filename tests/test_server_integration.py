# Copyright (c) 2026 Eldar Timraleev

"""
Integration test: boots server.py in a subprocess, posts a 1-second silent WAV,
asserts the response shape and duration_sec.

Skip conditions:
  - Neither faster-whisper-large-v3-turbo nor GigaAM model is cached locally.
"""

import json
import os
import shutil
import subprocess
import sys
import time

import numpy as np
import pytest
import requests

import wav_utils

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

SERVER_URL = "http://127.0.0.1:7531"
HEALTH_TIMEOUT_S = 120
HEALTH_POLL_INTERVAL_S = 1

# The voice_key.json the fixture will use for the test run.
TEST_CONFIG = {
    "keys": [{"type": "keyboard", "key": "ctrl"}, {"type": "keyboard", "key": "space"}],
    "backend": "whisper",
    "model_name": "large-v3-turbo",
}

SCRIPT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
VOICE_KEY_PATH = os.path.join(SCRIPT_DIR, "voice_key.json")
VOICE_KEY_BACKUP_PATH = VOICE_KEY_PATH + ".bak"


# ---------------------------------------------------------------------------
# Cache-presence check (skip guard)
# ---------------------------------------------------------------------------

def _any_model_cached() -> bool:
    """Return True if at least one supported model is already cached locally."""
    hf_cache = os.path.expanduser("~/.cache/huggingface/hub")
    if not os.path.isdir(hf_cache):
        return False

    try:
        entries = os.listdir(hf_cache)
    except OSError:
        return False

    for entry in entries:
        lower = entry.lower()
        # faster-whisper large-v3-turbo (any publisher variant)
        if "faster-whisper-large-v3-turbo" in lower:
            return True
        # GigaAM
        if "gigaam" in lower:
            return True

    return False


# ---------------------------------------------------------------------------
# Pytest fixture
# ---------------------------------------------------------------------------

@pytest.fixture(scope="module")
def running_server():
    """
    Fixture that:
      1. Skips if no model is cached.
      2. Backs up voice_key.json, writes the test config.
      3. Spawns server.py, polls /health until ready (timeout 120 s).
      4. Yields (nothing — consumers just need the server to be up).
      5. Kills the subprocess, waits for it to exit, restores voice_key.json.
    """
    if not _any_model_cached():
        pytest.skip(
            "No ASR model cached locally "
            "(checked ~/.cache/huggingface/hub for faster-whisper-large-v3-turbo and GigaAM). "
            "Run the server once manually to populate the cache, then re-run this test."
        )

    # --- Back up existing voice_key.json ---
    backed_up = False
    if os.path.exists(VOICE_KEY_PATH):
        shutil.copy2(VOICE_KEY_PATH, VOICE_KEY_BACKUP_PATH)
        backed_up = True

    # --- Write test config ---
    with open(VOICE_KEY_PATH, "w", encoding="utf-8") as fh:
        json.dump(TEST_CONFIG, fh, indent=2)

    # --- Spawn server.py ---
    server_py = os.path.join(SCRIPT_DIR, "server.py")
    proc = None
    try:
        proc = subprocess.Popen(
            [sys.executable, server_py],
            cwd=SCRIPT_DIR,
        )

        # --- Poll /health until ready ---
        deadline = time.monotonic() + HEALTH_TIMEOUT_S
        ready = False
        last_exc: Exception | None = None

        while time.monotonic() < deadline:
            try:
                resp = requests.get(f"{SERVER_URL}/health", timeout=2)
                if resp.status_code == 200 and resp.json().get("ready") is True:
                    ready = True
                    break
            except Exception as exc:
                last_exc = exc
            time.sleep(HEALTH_POLL_INTERVAL_S)

        if not ready:
            detail = str(last_exc) if last_exc else "server returned ready=false"
            pytest.fail(
                f"Server did not become ready within {HEALTH_TIMEOUT_S} s. "
                f"Last error: {detail}"
            )

        yield  # ← test body runs here

    finally:
        # --- Teardown: kill subprocess ---
        if proc is not None:
            proc.terminate()
            try:
                proc.wait(timeout=5)
            except Exception:
                proc.kill()
                proc.wait()

        # --- Restore voice_key.json ---
        if backed_up:
            shutil.copy2(VOICE_KEY_BACKUP_PATH, VOICE_KEY_PATH)
            os.remove(VOICE_KEY_BACKUP_PATH)
        elif os.path.exists(VOICE_KEY_PATH):
            # We created it; remove it so no stale test config lingers.
            os.remove(VOICE_KEY_PATH)


# ---------------------------------------------------------------------------
# Integration test
# ---------------------------------------------------------------------------

def test_transcribe_silence(running_server):
    """POST 1 second of silence, assert response shape and duration_sec ≈ 1.0."""
    silence = np.zeros(16000, dtype=np.float32)
    wav_bytes = wav_utils.encode_wav(silence, sample_rate=16000)

    r = requests.post(
        f"{SERVER_URL}/transcribe",
        data=wav_bytes,
        headers={"Content-Type": "application/octet-stream"},
        timeout=60,
    )

    assert r.status_code == 200, f"Expected 200, got {r.status_code}: {r.text}"

    body = r.json()
    assert set(body.keys()) == {"text", "words", "duration_sec"}, (
        f"Unexpected response keys: {set(body.keys())}"
    )

    duration_sec = body["duration_sec"]
    assert 0.99 < duration_sec < 1.01, (
        f"duration_sec {duration_sec!r} is outside expected range (0.99, 1.01)"
    )
