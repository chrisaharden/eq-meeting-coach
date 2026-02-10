"""STORY-4.1 — Facial Emotion Detection via DeepFace.

Accepts raw JPEG bytes, runs DeepFace emotion analysis, and returns a
structured FacialEmotionResult.  Gracefully handles no-face, corrupted
images, and low-confidence outputs.
"""

import io
import logging

import numpy as np
from PIL import Image

from eq_models.config import config
from eq_models.models import FacialEmotionResult

logger = logging.getLogger(__name__)

_EMOTION_LABELS = ["angry", "disgust", "fear", "happy", "sad", "surprise", "neutral"]
_CONCERNING_THRESHOLD: float = config["facial"]["concerning_threshold"]
_BACKEND: str = config["facial"]["backend"]


def _neutral_result() -> FacialEmotionResult:
    """Return a safe neutral result when face detection fails."""
    return FacialEmotionResult(
        emotions={label: 0.0 for label in _EMOTION_LABELS},
        dominant="neutral",
        is_concerning=False,
    )


def _get_deepface():
    """Lazy import of DeepFace to avoid heavy load at module import time."""
    from deepface import DeepFace
    return DeepFace


def analyze_face(image_bytes: bytes) -> FacialEmotionResult:
    """Run facial emotion detection on a JPEG image.

    Args:
        image_bytes: Raw JPEG image data.

    Returns:
        FacialEmotionResult with emotion scores, dominant emotion, and
        concerning flag.  Never raises — returns a neutral result on
        any failure.
    """
    try:
        image = Image.open(io.BytesIO(image_bytes))
        img_array = np.array(image)

        DeepFace = _get_deepface()

        # DeepFace returns a list of dicts (one per detected face).
        results = DeepFace.analyze(
            img_path=img_array,
            actions=["emotion"],
            enforce_detection=False,
            detector_backend="opencv",
        )

        if not results:
            return _neutral_result()

        # Take the first detected face.
        face = results[0] if isinstance(results, list) else results

        raw_emotions: dict[str, float] = face.get("emotion", {})
        if not raw_emotions:
            return _neutral_result()

        # DeepFace returns percentages (0-100); normalize to 0.0-1.0.
        emotions = {
            label: raw_emotions.get(label, 0.0) / 100.0
            for label in _EMOTION_LABELS
        }

        dominant = face.get("dominant_emotion", "neutral")

        # Concerning flag: (angry + disgust) > threshold
        angry_score = emotions.get("angry", 0.0)
        disgust_score = emotions.get("disgust", 0.0)
        is_concerning = (angry_score + disgust_score) > _CONCERNING_THRESHOLD

        return FacialEmotionResult(
            emotions=emotions,
            dominant=dominant,
            is_concerning=is_concerning,
        )

    except Exception:
        logger.exception("analyze_face failed — returning neutral result")
        return _neutral_result()
