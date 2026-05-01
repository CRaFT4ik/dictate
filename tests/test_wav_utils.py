import struct
import numpy as np
import pytest

import wav_utils


def _make_wav(
    num_channels=1,
    sample_rate=16000,
    bits_per_sample=16,
    audio_format=1,
    extra_data=b"",
    pcm_samples=None,
):
    """Build a minimal WAV byte string for testing edge cases."""
    if pcm_samples is None:
        pcm_samples = b"\x00\x00" * 16000

    fmt_size = 16
    subchunk1 = struct.pack(
        "<4sIHHIIHH",
        b"fmt ",
        fmt_size,
        audio_format,
        num_channels,
        sample_rate,
        sample_rate * num_channels * bits_per_sample // 8,
        num_channels * bits_per_sample // 8,
        bits_per_sample,
    )
    subchunk2 = struct.pack("<4sI", b"data", len(pcm_samples)) + pcm_samples
    wave_body = subchunk1 + extra_data + subchunk2
    riff = struct.pack("<4sI4s", b"RIFF", 4 + len(wave_body), b"WAVE")
    return riff + wave_body


class TestEncodeWav:
    def test_returns_bytes(self):
        samples = np.zeros(16000, dtype=np.float32)
        result = wav_utils.encode_wav(samples)
        assert isinstance(result, bytes)

    def test_header_is_44_bytes(self):
        samples = np.zeros(1000, dtype=np.float32)
        result = wav_utils.encode_wav(samples)
        assert result[:4] == b"RIFF"
        assert result[8:12] == b"WAVE"
        assert result[12:16] == b"fmt "
        assert result[36:40] == b"data"

    def test_fmt_subchunk(self):
        result = wav_utils.encode_wav(np.zeros(100, dtype=np.float32))
        assert result[12:16] == b"fmt "
        fmt_size = struct.unpack_from("<I", result, 16)[0]
        assert fmt_size == 16
        audio_format = struct.unpack_from("<H", result, 20)[0]
        assert audio_format == 1  # PCM
        num_channels = struct.unpack_from("<H", result, 22)[0]
        assert num_channels == 1
        sample_rate = struct.unpack_from("<I", result, 24)[0]
        assert sample_rate == 16000
        bits_per_sample = struct.unpack_from("<H", result, 34)[0]
        assert bits_per_sample == 16

    def test_data_subchunk_size(self):
        n = 500
        result = wav_utils.encode_wav(np.zeros(n, dtype=np.float32))
        data_size = struct.unpack_from("<I", result, 40)[0]
        assert data_size == n * 2  # 2 bytes per s16 sample

    def test_custom_sample_rate(self):
        result = wav_utils.encode_wav(np.zeros(100, dtype=np.float32), sample_rate=8000)
        sample_rate = struct.unpack_from("<I", result, 24)[0]
        assert sample_rate == 8000

    def test_total_length(self):
        n = 300
        result = wav_utils.encode_wav(np.zeros(n, dtype=np.float32))
        assert len(result) == 44 + n * 2

    def test_silence_encodes_to_zeros(self):
        result = wav_utils.encode_wav(np.zeros(100, dtype=np.float32))
        pcm = result[44:]
        assert all(b == 0 for b in pcm)

    def test_positive_clip_value(self):
        samples = np.array([1.0], dtype=np.float32)
        result = wav_utils.encode_wav(samples)
        val = struct.unpack_from("<h", result, 44)[0]
        assert val == 32767

    def test_negative_clip_value(self):
        samples = np.array([-1.0], dtype=np.float32)
        result = wav_utils.encode_wav(samples)
        val = struct.unpack_from("<h", result, 44)[0]
        assert val == -32768


class TestDecodeWav:
    def test_roundtrip_zeros(self):
        samples = np.zeros(16000, dtype=np.float32)
        result, sr = wav_utils.decode_wav(wav_utils.encode_wav(samples))
        assert sr == 16000
        assert np.allclose(result, samples, atol=1 / 32768)

    def test_roundtrip_sine(self):
        t = np.linspace(0, 1.0, 16000, endpoint=False, dtype=np.float32)
        samples = (0.5 * np.sin(2 * np.pi * 440 * t)).astype(np.float32)
        result, sr = wav_utils.decode_wav(wav_utils.encode_wav(samples))
        assert sr == 16000
        assert np.allclose(result, samples, atol=1 / 32768)

    def test_roundtrip_random(self):
        rng = np.random.default_rng(42)
        samples = rng.uniform(-1.0, 1.0, 8000).astype(np.float32)
        result, sr = wav_utils.decode_wav(wav_utils.encode_wav(samples))
        assert sr == 16000
        assert np.allclose(result, samples, atol=1 / 32768)

    def test_returns_float32(self):
        samples = np.zeros(100, dtype=np.float32)
        result, _ = wav_utils.decode_wav(wav_utils.encode_wav(samples))
        assert result.dtype == np.float32

    def test_output_range(self):
        rng = np.random.default_rng(7)
        samples = rng.uniform(-1.0, 1.0, 4000).astype(np.float32)
        result, _ = wav_utils.decode_wav(wav_utils.encode_wav(samples))
        assert result.min() >= -1.0
        assert result.max() <= 1.0

    def test_accepts_larger_header_with_list_chunk(self):
        # WAV with a LIST INFO chunk inserted before data
        list_chunk = b"LIST" + struct.pack("<I", 4) + b"INFO"
        wav = _make_wav(extra_data=list_chunk)
        samples, sr = wav_utils.decode_wav(wav)
        assert sr == 16000
        assert len(samples) == 16000

    def test_rejects_non_pcm(self):
        wav = _make_wav(audio_format=3)  # IEEE float, not PCM
        with pytest.raises(ValueError):
            wav_utils.decode_wav(wav)

    def test_rejects_stereo(self):
        pcm = b"\x00\x00" * 16000 * 2
        wav = _make_wav(num_channels=2, pcm_samples=pcm)
        with pytest.raises(ValueError):
            wav_utils.decode_wav(wav)

    def test_rejects_wrong_sample_rate(self):
        wav = _make_wav(sample_rate=44100)
        with pytest.raises(ValueError):
            wav_utils.decode_wav(wav)

    def test_rejects_truncated_data(self):
        wav = wav_utils.encode_wav(np.zeros(100, dtype=np.float32))
        with pytest.raises(ValueError):
            wav_utils.decode_wav(wav[:20])

    def test_rejects_non_wav(self):
        with pytest.raises(ValueError):
            wav_utils.decode_wav(b"not a wav file at all")
