"""Stub ML functions — replaced by EPIC-4 with real implementations."""

from models.schemas import FacialEmotionResult, SpeechEmotionResult, Verdict


def analyze_face(image_bytes: bytes) -> FacialEmotionResult:
    """Stub: always returns neutral facial emotion."""
    return FacialEmotionResult(
        emotions={"neutral": 1.0},
        dominant="neutral",
        is_concerning=False,
    )


def analyze_speech(audio_bytes: bytes) -> SpeechEmotionResult:
    """Stub: always returns neutral speech emotion."""
    return SpeechEmotionResult(
        emotions={"neutral": 1.0},
        dominant="neutral",
        is_concerning=False,
    )


def compute_verdict(
    facial: FacialEmotionResult, speech: SpeechEmotionResult
) -> Verdict:
    """Stub: always returns GREEN."""
    return Verdict.GREEN
