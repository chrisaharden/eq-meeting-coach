from enum import Enum

from pydantic import BaseModel


class FacialEmotionResult(BaseModel):
    emotions: dict[str, float]  # e.g., {"angry": 0.7, "neutral": 0.2, ...}
    dominant: str
    is_concerning: bool


class SpeechEmotionResult(BaseModel):
    emotions: dict[str, float]  # e.g., {"angry": 0.5, "neutral": 0.4, ...}
    dominant: str
    is_concerning: bool


class Verdict(str, Enum):
    GREEN = "GREEN"
    YELLOW = "YELLOW"
    RED = "RED"


class AnalyzeResponse(BaseModel):
    verdict: Verdict
    debug: dict[str, object] | None = None


class HealthResponse(BaseModel):
    status: str
    models_loaded: bool
