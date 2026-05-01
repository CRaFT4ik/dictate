# Copyright (c) 2026 Eldar Timraleev

import struct
import numpy as np


def encode_wav(audio_flat: np.ndarray, sample_rate: int = 16000) -> bytes:
    """Take float32 samples in [-1, 1], return a complete WAV byte string (44-byte header + PCM s16le)."""
    pcm = np.clip(audio_flat * 32768.0, -32768, 32767).astype("<i2").tobytes()
    num_channels = 1
    bits_per_sample = 16
    byte_rate = sample_rate * num_channels * bits_per_sample // 8
    block_align = num_channels * bits_per_sample // 8
    data_size = len(pcm)
    header = struct.pack(
        "<4sI4s4sIHHIIHH4sI",
        b"RIFF",
        36 + data_size,
        b"WAVE",
        b"fmt ",
        16,
        1,
        num_channels,
        sample_rate,
        byte_rate,
        block_align,
        bits_per_sample,
        b"data",
        data_size,
    )
    return header + pcm


def decode_wav(wav_bytes: bytes) -> tuple[np.ndarray, int]:
    """Parse a WAV byte string. Return (float32 samples in [-1, 1], sample_rate)."""
    if len(wav_bytes) < 12 or wav_bytes[:4] != b"RIFF" or wav_bytes[8:12] != b"WAVE":
        raise ValueError("Not a RIFF/WAVE file")

    offset = 12
    fmt_found = False
    audio_format = num_channels = sample_rate = bits_per_sample = None

    while offset + 8 <= len(wav_bytes):
        chunk_id = wav_bytes[offset:offset + 4]
        try:
            chunk_size = struct.unpack_from("<I", wav_bytes, offset + 4)[0]
        except struct.error as exc:
            raise ValueError(f"Truncated WAV: cannot read chunk size at offset {offset}") from exc
        chunk_data_start = offset + 8

        if chunk_id == b"fmt ":
            if chunk_size < 16:
                raise ValueError("fmt chunk too small")
            try:
                audio_format, num_channels, sample_rate = struct.unpack_from("<HHI", wav_bytes, chunk_data_start)
                bits_per_sample = struct.unpack_from("<H", wav_bytes, chunk_data_start + 14)[0]
            except struct.error as exc:
                raise ValueError(f"Truncated WAV: fmt chunk shorter than expected") from exc
            fmt_found = True

        elif chunk_id == b"data":
            if not fmt_found:
                raise ValueError("data chunk appears before fmt chunk")
            if audio_format != 1:
                raise ValueError(f"Unsupported audio format {audio_format}; expected PCM (1)")
            if num_channels != 1:
                raise ValueError(f"Expected mono (1 channel), got {num_channels}")
            if sample_rate != 16000:
                raise ValueError(f"Expected sample rate 16000, got {sample_rate}")
            if bits_per_sample != 16:
                raise ValueError(f"Expected 16 bits per sample, got {bits_per_sample}")

            raw = wav_bytes[chunk_data_start:chunk_data_start + chunk_size]
            samples = np.frombuffer(raw, dtype="<i2").astype(np.float32) / 32768.0
            return samples, sample_rate

        offset = chunk_data_start + chunk_size
        if chunk_size % 2 != 0:
            offset += 1

    raise ValueError("No data chunk found in WAV file")
