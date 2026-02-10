"""Unit tests for STORY-4.1 — Facial Emotion Detection (DeepFace).

These tests mock DeepFace.analyze so they can run without a GPU or the
actual model weights.  Integration tests with the real model should be
run separately on hardware that has DeepFace installed.
"""

from unittest.mock import MagicMock, patch

import pytest

from eq_models.facial import analyze_face, _neutral_result, _EMOTION_LABELS
from eq_models.models import FacialEmotionResult


# ─── Helpers ───


def _make_dummy_jpeg() -> bytes:
    """Return minimal valid JPEG bytes (1x1 white pixel)."""
    import io
    from PIL import Image

    buf = io.BytesIO()
    Image.new("RGB", (1, 1), (255, 255, 255)).save(buf, "JPEG")
    return buf.getvalue()


def _deepface_response(
    angry: float = 0.0,
    disgust: float = 0.0,
    fear: float = 0.0,
    happy: float = 0.0,
    sad: float = 0.0,
    surprise: float = 0.0,
    neutral: float = 100.0,
    dominant: str = "neutral",
) -> list[dict]:
    """Build a fake DeepFace.analyze response (percentages 0-100)."""
    return [
        {
            "emotion": {
                "angry": angry,
                "disgust": disgust,
                "fear": fear,
                "happy": happy,
                "sad": sad,
                "surprise": surprise,
                "neutral": neutral,
            },
            "dominant_emotion": dominant,
        }
    ]


def _patch_deepface(return_value=None, side_effect=None):
    """Create a patch for _get_deepface that returns a mock DeepFace module."""
    mock_deepface = MagicMock()
    if side_effect:
        mock_deepface.analyze.side_effect = side_effect
    else:
        mock_deepface.analyze.return_value = return_value
    return patch("eq_models.facial._get_deepface", return_value=mock_deepface)


# ─── Tests ───


class TestNeutralResult:
    def test_all_zeros(self):
        r = _neutral_result()
        assert all(v == 0.0 for v in r.emotions.values())

    def test_dominant_neutral(self):
        assert _neutral_result().dominant == "neutral"

    def test_not_concerning(self):
        assert _neutral_result().is_concerning is False


class TestAnalyzeFaceHappyPath:
    def test_angry_face(self):
        with _patch_deepface(_deepface_response(
            angry=70.0, disgust=5.0, neutral=10.0, dominant="angry"
        )):
            result = analyze_face(_make_dummy_jpeg())

        assert isinstance(result, FacialEmotionResult)
        assert result.dominant == "angry"
        assert result.emotions["angry"] == pytest.approx(0.70, abs=0.01)
        assert result.is_concerning is True  # 0.70 + 0.05 = 0.75 > 0.40

    def test_happy_face(self):
        with _patch_deepface(_deepface_response(
            happy=85.0, neutral=10.0, dominant="happy"
        )):
            result = analyze_face(_make_dummy_jpeg())

        assert result.dominant == "happy"
        assert result.emotions["happy"] == pytest.approx(0.85, abs=0.01)
        assert result.is_concerning is False

    def test_neutral_face(self):
        with _patch_deepface(_deepface_response(neutral=90.0, dominant="neutral")):
            result = analyze_face(_make_dummy_jpeg())

        assert result.dominant == "neutral"
        assert result.is_concerning is False

    def test_all_seven_labels_present(self):
        with _patch_deepface(_deepface_response()):
            result = analyze_face(_make_dummy_jpeg())

        assert set(result.emotions.keys()) == set(_EMOTION_LABELS)


class TestAnalyzeFaceEdgeCases:
    def test_no_face_detected_returns_neutral(self):
        with _patch_deepface([]):
            result = analyze_face(_make_dummy_jpeg())

        assert result == _neutral_result()

    def test_exception_returns_neutral(self):
        with _patch_deepface(side_effect=RuntimeError("model failed")):
            result = analyze_face(_make_dummy_jpeg())

        assert result == _neutral_result()

    def test_corrupted_image_returns_neutral(self):
        result = analyze_face(b"not-a-jpeg")
        assert result == _neutral_result()

    def test_concerning_at_exact_threshold(self):
        # angry + disgust = 0.40 exactly — should NOT be concerning (> not >=).
        with _patch_deepface(_deepface_response(
            angry=30.0, disgust=10.0, neutral=60.0, dominant="neutral"
        )):
            result = analyze_face(_make_dummy_jpeg())
        assert result.is_concerning is False

    def test_concerning_just_above_threshold(self):
        with _patch_deepface(_deepface_response(
            angry=30.0, disgust=11.0, neutral=59.0, dominant="neutral"
        )):
            result = analyze_face(_make_dummy_jpeg())
        assert result.is_concerning is True  # 0.30 + 0.11 = 0.41 > 0.40

    def test_scores_normalized_to_0_1(self):
        with _patch_deepface(_deepface_response(
            angry=50.0, happy=50.0, dominant="angry"
        )):
            result = analyze_face(_make_dummy_jpeg())

        for score in result.emotions.values():
            assert 0.0 <= score <= 1.0
