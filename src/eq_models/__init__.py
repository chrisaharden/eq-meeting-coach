"""EQ Meeting Coach — ML Models & Score Fusion (EPIC-4)."""

from eq_models.models import FacialEmotionResult, SpeechEmotionResult, Verdict
from eq_models.facial import analyze_face
from eq_models.speech import analyze_speech
from eq_models.fusion import compute_verdict

__all__ = [
    "FacialEmotionResult",
    "SpeechEmotionResult",
    "Verdict",
    "analyze_face",
    "analyze_speech",
    "compute_verdict",
]
