"""Tests for STORY-4.3 — Score Fusion & Verdict Engine.

Pure unit tests — no mocks or I/O needed.  We construct
FacialEmotionResult / SpeechEmotionResult directly and verify
compute_verdict returns the correct Verdict enum value.

Config defaults used by fusion.py:
    facial_weight:   0.60
    speech_weight:   0.40
    green_threshold: 0.25   (fused < 0.25 → GREEN)
    red_threshold:   0.50   (fused >= 0.50 → RED)
"""

from eq_models.fusion import compute_verdict
from eq_models.models import FacialEmotionResult, SpeechEmotionResult, Verdict


# ── Helpers ──────────────────────────────────────────────────────────

def _facial(
    angry=0.0, disgust=0.0, fear=0.0, happy=0.0,
    sad=0.0, surprise=0.0, neutral=1.0, is_concerning=False,
) -> FacialEmotionResult:
    emotions = {
        "angry": angry, "disgust": disgust, "fear": fear,
        "happy": happy, "sad": sad, "surprise": surprise, "neutral": neutral,
    }
    dominant = max(emotions, key=emotions.get)
    return FacialEmotionResult(
        emotions=emotions, dominant=dominant, is_concerning=is_concerning,
    )


def _speech(
    angry=0.0, happy=0.0, sad=0.0, neutral=1.0, is_concerning=False,
) -> SpeechEmotionResult:
    emotions = {"angry": angry, "happy": happy, "sad": sad, "neutral": neutral}
    dominant = max(emotions, key=emotions.get)
    return SpeechEmotionResult(
        emotions=emotions, dominant=dominant, is_concerning=is_concerning,
    )


# ── AC6: Both calm → GREEN ──────────────────────────────────────────

class TestBothCalm:
    def test_zero_anger(self):
        # fused = 0 → GREEN
        assert compute_verdict(_facial(), _speech()) == Verdict.GREEN

    def test_low_anger_both_channels(self):
        # fused = 0.1*0.6 + 0.1*0.4 = 0.10 → GREEN
        assert compute_verdict(_facial(angry=0.1), _speech(angry=0.1)) == Verdict.GREEN


# ── AC6: One concerning → escalation ────────────────────────────────

class TestOneConcerning:
    def test_facial_concerning_escalates_green_to_yellow(self):
        # fused = 0.10 → GREEN, but facial concerning → YELLOW
        result = compute_verdict(
            _facial(angry=0.1, is_concerning=True),
            _speech(angry=0.1),
        )
        assert result == Verdict.YELLOW

    def test_speech_concerning_escalates_green_to_yellow(self):
        # fused = 0.10 → GREEN, but speech concerning → YELLOW
        result = compute_verdict(
            _facial(angry=0.1),
            _speech(angry=0.1, is_concerning=True),
        )
        assert result == Verdict.YELLOW

    def test_facial_concerning_escalates_yellow_to_red(self):
        # fused = 0.35*0.6 + 0.35*0.4 = 0.35 → YELLOW, facial concerning → RED
        result = compute_verdict(
            _facial(angry=0.35, is_concerning=True),
            _speech(angry=0.35),
        )
        assert result == Verdict.RED


# ── AC6: Both concerning → RED ──────────────────────────────────────

class TestBothConcerning:
    def test_both_concerning_high_anger(self):
        # fused = 0.5*0.6 + 0.6*0.4 = 0.54 → RED, both concerning → RED
        result = compute_verdict(
            _facial(angry=0.5, is_concerning=True),
            _speech(angry=0.6, is_concerning=True),
        )
        assert result == Verdict.RED

    def test_both_concerning_low_anger_escalates_one_level(self):
        # fused = 0.10 → GREEN, escalation is +1 level → YELLOW (not RED)
        result = compute_verdict(
            _facial(angry=0.1, is_concerning=True),
            _speech(angry=0.1, is_concerning=True),
        )
        assert result == Verdict.YELLOW


# ── AC6: Exact threshold boundaries ────────────────────────────────

class TestThresholdBoundaries:
    def test_just_below_green_threshold(self):
        # fused = 0.2*0.6 + 0.2*0.4 = 0.20 < 0.25 → GREEN
        assert compute_verdict(_facial(angry=0.2), _speech(angry=0.2)) == Verdict.GREEN

    def test_at_green_threshold_is_yellow(self):
        # fused = 0.25*0.6 + 0.25*0.4 = 0.25 — NOT < 0.25 → YELLOW
        assert compute_verdict(_facial(angry=0.25), _speech(angry=0.25)) == Verdict.YELLOW

    def test_mid_yellow_range(self):
        # fused = 0.4*0.6 + 0.5*0.4 = 0.44 → YELLOW
        assert compute_verdict(_facial(angry=0.4), _speech(angry=0.5)) == Verdict.YELLOW

    def test_at_red_threshold_is_red(self):
        # fused = 0.5*0.6 + 0.5*0.4 = 0.50 — NOT < 0.50 → RED
        assert compute_verdict(_facial(angry=0.5), _speech(angry=0.5)) == Verdict.RED

    def test_above_red_threshold(self):
        # fused = 0.8*0.6 + 0.8*0.4 = 0.80 → RED
        assert compute_verdict(_facial(angry=0.8), _speech(angry=0.8)) == Verdict.RED


# ── AC4: Escalation cap — already RED stays RED ─────────────────────

class TestEscalationCap:
    def test_already_red_with_concerning_stays_red(self):
        result = compute_verdict(
            _facial(angry=0.7, is_concerning=True),
            _speech(angry=0.7, is_concerning=True),
        )
        assert result == Verdict.RED


# ── AC2: Facial weight (0.6) > speech weight (0.4) ─────────────────

class TestWeightAsymmetry:
    def test_facial_anger_has_more_influence(self):
        # Same total anger, different channels → different verdicts
        # facial=0.6, speech=0.0 → fused = 0.36 → YELLOW
        # facial=0.0, speech=0.6 → fused = 0.24 → GREEN
        high_facial = compute_verdict(_facial(angry=0.6), _speech(angry=0.0))
        high_speech = compute_verdict(_facial(angry=0.0), _speech(angry=0.6))
        assert high_facial == Verdict.YELLOW
        assert high_speech == Verdict.GREEN


# ── AC5: Return type is a Verdict enum ──────────────────────────────

class TestVerdictEnum:
    def test_return_is_verdict_instance(self):
        result = compute_verdict(_facial(), _speech())
        assert isinstance(result, Verdict)

    def test_verdict_string_value(self):
        result = compute_verdict(_facial(), _speech())
        assert result.value == "GREEN"
