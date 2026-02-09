"""Unit tests for STORY-4.2 — Speech Emotion Detection (SenseVoice).

These tests mock the FunASR AutoModel so they can run without a GPU or
the SenseVoice model weights.
"""

import io
from unittest.mock import MagicMock, patch

import numpy as np
import pytest
import soundfile as sf

from eq_models.models import SpeechEmotionResult
from eq_models.speech import (
    _neutral_result,
    _parse_emotion_tags,
    analyze_speech,
)


# ─── Helpers ───


def _make_wav(
    duration: float = 4.0,
    sample_rate: int = 16000,
    amplitude: float = 0.5,
    frequency: float = 300.0,
) -> bytes:
    """Generate a synthetic WAV file as bytes."""
    t = np.linspace(0, duration, int(sample_rate * duration), endpoint=False)
    data = (amplitude * np.sin(2 * np.pi * frequency * t)).astype(np.float32)
    buf = io.BytesIO()
    sf.write(buf, data, sample_rate, format="WAV", subtype="PCM_16")
    return buf.getvalue()


def _make_silence_wav(duration: float = 4.0, sample_rate: int = 16000) -> bytes:
    """Generate a silent WAV file as bytes."""
    data = np.zeros(int(sample_rate * duration), dtype=np.float32)
    buf = io.BytesIO()
    sf.write(buf, data, sample_rate, format="WAV", subtype="PCM_16")
    return buf.getvalue()


def _make_short_wav(duration: float = 0.5, sample_rate: int = 16000) -> bytes:
    """Generate a WAV too short for analysis."""
    return _make_wav(duration=duration, sample_rate=sample_rate)


# ─── Tests: _parse_emotion_tags ───


class TestParseEmotionTags:
    def test_happy_tag(self):
        emotions = _parse_emotion_tags("hello <|HAPPY|> world")
        assert emotions["happy"] == 1.0
        assert emotions["angry"] == 0.0

    def test_angry_tag(self):
        emotions = _parse_emotion_tags("<|ANGRY|> some text")
        assert emotions["angry"] == 1.0

    def test_no_tags_returns_neutral(self):
        emotions = _parse_emotion_tags("just some plain text")
        assert emotions["neutral"] == 1.0
        assert emotions["angry"] == 0.0

    def test_multiple_tags(self):
        emotions = _parse_emotion_tags("<|ANGRY|> <|ANGRY|> <|SAD|>")
        assert emotions["angry"] == pytest.approx(2 / 3, abs=0.01)
        assert emotions["sad"] == pytest.approx(1 / 3, abs=0.01)

    def test_case_insensitive(self):
        emotions = _parse_emotion_tags("<|happy|>")
        assert emotions["happy"] == 1.0


# ─── Tests: _neutral_result ───


class TestNeutralResult:
    def test_all_zeros(self):
        r = _neutral_result()
        assert all(v == 0.0 for v in r.emotions.values())

    def test_dominant_neutral(self):
        assert _neutral_result().dominant == "neutral"

    def test_not_concerning(self):
        assert _neutral_result().is_concerning is False


# ─── Tests: analyze_speech ───


class TestAnalyzeSpeechHappyPath:
    @patch("eq_models.speech._get_model")
    def test_angry_speech(self, mock_get_model):
        mock_model = MagicMock()
        mock_model.generate.return_value = [{"text": "<|ANGRY|> you are wrong"}]
        mock_get_model.return_value = mock_model

        result = analyze_speech(_make_wav())

        assert isinstance(result, SpeechEmotionResult)
        assert result.dominant == "angry"
        assert result.emotions["angry"] == 1.0
        assert result.is_concerning is True  # 1.0 > 0.45

    @patch("eq_models.speech._get_model")
    def test_happy_speech(self, mock_get_model):
        mock_model = MagicMock()
        mock_model.generate.return_value = [{"text": "<|HAPPY|> great meeting"}]
        mock_get_model.return_value = mock_model

        result = analyze_speech(_make_wav())

        assert result.dominant == "happy"
        assert result.is_concerning is False

    @patch("eq_models.speech._get_model")
    def test_neutral_speech(self, mock_get_model):
        mock_model = MagicMock()
        mock_model.generate.return_value = [{"text": "<|NEUTRAL|> okay"}]
        mock_get_model.return_value = mock_model

        result = analyze_speech(_make_wav())

        assert result.dominant == "neutral"
        assert result.is_concerning is False


class TestAnalyzeSpeechEdgeCases:
    def test_silence_returns_neutral(self):
        result = analyze_speech(_make_silence_wav())
        assert result == _neutral_result()

    def test_too_short_returns_neutral(self):
        result = analyze_speech(_make_short_wav(duration=0.5))
        assert result == _neutral_result()

    @patch("eq_models.speech._get_model")
    def test_empty_result_returns_neutral(self, mock_get_model):
        mock_model = MagicMock()
        mock_model.generate.return_value = []
        mock_get_model.return_value = mock_model

        result = analyze_speech(_make_wav())
        assert result == _neutral_result()

    @patch("eq_models.speech._get_model")
    def test_no_emotion_tags_returns_neutral(self, mock_get_model):
        mock_model = MagicMock()
        mock_model.generate.return_value = [{"text": "just words no tags"}]
        mock_get_model.return_value = mock_model

        result = analyze_speech(_make_wav())
        assert result.dominant == "neutral"
        assert result.is_concerning is False

    def test_corrupted_audio_returns_neutral(self):
        result = analyze_speech(b"not-wav-data")
        assert result == _neutral_result()

    @patch("eq_models.speech._get_model")
    def test_model_exception_returns_neutral(self, mock_get_model):
        mock_get_model.side_effect = RuntimeError("model load failed")

        result = analyze_speech(_make_wav())
        assert result == _neutral_result()

    @patch("eq_models.speech._get_model")
    def test_concerning_at_exact_threshold(self, mock_get_model):
        # angry = 0.45 exactly — should NOT be concerning (> not >=).
        mock_model = MagicMock()
        # 9 angry out of 20 total = 0.45
        tags = "<|ANGRY|> " * 9 + "<|NEUTRAL|> " * 11
        mock_model.generate.return_value = [{"text": tags}]
        mock_get_model.return_value = mock_model

        result = analyze_speech(_make_wav())
        assert result.is_concerning is False

    @patch("eq_models.speech._get_model")
    def test_stereo_audio_converted_to_mono(self, mock_get_model):
        """Stereo WAV should be handled gracefully."""
        mock_model = MagicMock()
        mock_model.generate.return_value = [{"text": "<|NEUTRAL|>"}]
        mock_get_model.return_value = mock_model

        # Create stereo WAV.
        sr = 16000
        t = np.linspace(0, 4.0, int(sr * 4.0), endpoint=False)
        stereo = np.column_stack([
            0.3 * np.sin(2 * np.pi * 300 * t),
            0.3 * np.sin(2 * np.pi * 400 * t),
        ]).astype(np.float32)
        buf = io.BytesIO()
        sf.write(buf, stereo, sr, format="WAV", subtype="PCM_16")

        result = analyze_speech(buf.getvalue())
        assert isinstance(result, SpeechEmotionResult)
