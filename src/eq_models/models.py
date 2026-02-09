"""Shared Pydantic models and enums for the EQ ML pipeline."""

from enum import Enum

from pydantic import BaseModel


class FacialEmotionResult(BaseModel):
    """Result of facial emotion detection via DeepFace."""

    emotions: dict[str, float]  # e.g. {"angry": 0.7, "disgust": 0.1, ...}
    dominant: str               # e.g. "angry"
    is_concerning: bool         # True if (angry + disgust) > threshold


class SpeechEmotionResult(BaseModel):
    """Result of speech emotion detection via SenseVoice."""

    emotions: dict[str, float]  # e.g. {"angry": 0.5, "neutral": 0.4, ...}
    dominant: str               # e.g. "angry"
    is_concerning: bool         # True if angry > threshold


class Verdict(str, Enum):
    """Fused emotional state verdict."""

    GREEN = "GREEN"
    YELLOW = "YELLOW"
    RED = "RED"
