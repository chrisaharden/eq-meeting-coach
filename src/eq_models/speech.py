"""STORY-4.2 — Speech Emotion Detection via SenseVoice / FunASR.

Accepts raw WAV bytes, runs SenseVoice emotion analysis, and returns a
structured SpeechEmotionResult.  Gracefully handles silence, short clips,
and resampling.
"""

import io
import logging
import re
import tempfile
from pathlib import Path

import librosa
import numpy as np
import soundfile as sf

from eq_models.config import config
from eq_models.models import SpeechEmotionResult

logger = logging.getLogger(__name__)

_EMOTION_LABELS = ["angry", "happy", "sad", "neutral"]
_CONCERNING_THRESHOLD: float = config["speech"]["concerning_threshold"]
_MODEL_PATH: str = config["speech"]["model_path"]
_TARGET_SAMPLE_RATE: int = config["speech"]["sample_rate"]
_MIN_DURATION_SECONDS: float = 1.0

# Lazy-loaded model singleton.
_model = None


def _get_model():
    """Load the SenseVoice model once (lazy singleton)."""
    global _model
    if _model is None:
        from funasr import AutoModel
        model_path = str(Path(_MODEL_PATH).resolve())
        _model = AutoModel(model=model_path, device="cuda:0")
    return _model


def _neutral_result() -> SpeechEmotionResult:
    """Return a safe neutral result when speech analysis fails."""
    return SpeechEmotionResult(
        emotions={label: 0.0 for label in _EMOTION_LABELS},
        dominant="neutral",
        is_concerning=False,
    )


# Regex to find SenseVoice emotion tokens like <|HAPPY|>, <|ANGRY|>, etc.
_EMOTION_TAG_PATTERN = re.compile(r"<\|(\w+)\|>")

# Map SenseVoice emotion tags (upper-case) to our canonical labels.
_TAG_TO_LABEL: dict[str, str] = {
    "HAPPY": "happy",
    "ANGRY": "angry",
    "SAD": "sad",
    "NEUTRAL": "neutral",
}


def _parse_emotion_tags(text: str) -> dict[str, float]:
    """Parse SenseVoice emotion tokens from transcription text.

    SenseVoice embeds emotion as special tokens in the output.  When a
    single tag is present we treat it as the dominant emotion with high
    confidence and distribute a small residual to the others.
    """
    tags = _EMOTION_TAG_PATTERN.findall(text.upper())
    detected = [_TAG_TO_LABEL[t] for t in tags if t in _TAG_TO_LABEL]

    if not detected:
        return {label: (1.0 if label == "neutral" else 0.0) for label in _EMOTION_LABELS}

    # Count occurrences and normalise.
    counts: dict[str, int] = {}
    for label in detected:
        counts[label] = counts.get(label, 0) + 1
    total = sum(counts.values())

    emotions: dict[str, float] = {}
    for label in _EMOTION_LABELS:
        emotions[label] = counts.get(label, 0) / total

    return emotions


def analyze_speech(audio_bytes: bytes) -> SpeechEmotionResult:
    """Run speech emotion detection on a WAV audio clip.

    Args:
        audio_bytes: Raw WAV audio data (ideally 16 kHz mono 16-bit PCM).

    Returns:
        SpeechEmotionResult with emotion scores, dominant emotion, and
        concerning flag.  Never raises — returns a neutral result on any
        failure.
    """
    try:
        # Read the WAV bytes.
        audio_data, sample_rate = sf.read(io.BytesIO(audio_bytes), dtype="float32")

        # Convert stereo to mono if necessary.
        if audio_data.ndim > 1:
            audio_data = np.mean(audio_data, axis=1)

        # Resample to target sample rate if needed.
        if sample_rate != _TARGET_SAMPLE_RATE:
            audio_data = librosa.resample(
                audio_data, orig_sr=sample_rate, target_sr=_TARGET_SAMPLE_RATE
            )
            sample_rate = _TARGET_SAMPLE_RATE

        duration = len(audio_data) / sample_rate

        # Too short for meaningful analysis — return neutral.
        if duration < _MIN_DURATION_SECONDS:
            return _neutral_result()

        # Check for silence (RMS below a small threshold).
        rms = np.sqrt(np.mean(audio_data**2))
        if rms < 0.005:
            return _neutral_result()

        # SenseVoice / FunASR expects a file path — write to a temp file.
        with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
            sf.write(tmp.name, audio_data, sample_rate, subtype="PCM_16")
            tmp_path = tmp.name

        model = _get_model()
        result = model.generate(input=tmp_path, language="auto")

        # Clean up temp file.
        Path(tmp_path).unlink(missing_ok=True)

        if not result:
            return _neutral_result()

        # Extract text from result — FunASR returns a list of dicts.
        text = ""
        if isinstance(result, list) and len(result) > 0:
            entry = result[0]
            if isinstance(entry, dict):
                text = entry.get("text", "")
            else:
                text = str(entry)
        elif isinstance(result, dict):
            text = result.get("text", "")

        emotions = _parse_emotion_tags(text)
        dominant = max(emotions, key=emotions.get)  # type: ignore[arg-type]

        angry_score = emotions.get("angry", 0.0)
        is_concerning = angry_score > _CONCERNING_THRESHOLD

        return SpeechEmotionResult(
            emotions=emotions,
            dominant=dominant,
            is_concerning=is_concerning,
        )

    except Exception:
        logger.exception("analyze_speech failed — returning neutral result")
        return _neutral_result()
