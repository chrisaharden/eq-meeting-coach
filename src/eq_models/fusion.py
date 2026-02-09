"""STORY-4.3 — Score Fusion & Verdict Engine.

Pure function — no side effects, no I/O, no model loading.
Combines facial and speech emotion results into a single verdict.

NOTE: Full implementation is Wave 2.  The function is stubbed here so that
the package is importable and the Server API Agent can reference it.
"""

from eq_models.config import config
from eq_models.models import FacialEmotionResult, SpeechEmotionResult, Verdict

_FACIAL_WEIGHT: float = config["fusion"]["facial_weight"]
_SPEECH_WEIGHT: float = config["fusion"]["speech_weight"]
_GREEN_THRESHOLD: float = config["fusion"]["green_threshold"]
_RED_THRESHOLD: float = config["fusion"]["red_threshold"]


def compute_verdict(
    facial: FacialEmotionResult,
    speech: SpeechEmotionResult,
) -> Verdict:
    """Fuse facial and speech emotion results into a single verdict.

    Args:
        facial: Result from analyze_face.
        speech: Result from analyze_speech.

    Returns:
        Verdict (GREEN, YELLOW, or RED).
    """
    fused_score = (
        facial.emotions.get("angry", 0.0) * _FACIAL_WEIGHT
        + speech.emotions.get("angry", 0.0) * _SPEECH_WEIGHT
    )

    # Base verdict from thresholds.
    if fused_score < _GREEN_THRESHOLD:
        verdict = Verdict.GREEN
    elif fused_score < _RED_THRESHOLD:
        verdict = Verdict.YELLOW
    else:
        verdict = Verdict.RED

    # Escalation rule: if either modality is concerning, escalate by one level.
    if facial.is_concerning or speech.is_concerning:
        if verdict == Verdict.GREEN:
            verdict = Verdict.YELLOW
        elif verdict == Verdict.YELLOW:
            verdict = Verdict.RED

    return verdict
