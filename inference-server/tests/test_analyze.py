"""Tests for the POST /analyze endpoint (STORY-3.2)."""

import io
from unittest.mock import patch

import pytest
from fastapi.testclient import TestClient

from main import app
from models.schemas import FacialEmotionResult, SpeechEmotionResult, Verdict

client = TestClient(app)

# Minimal valid file payloads
FAKE_JPEG = b"\xff\xd8\xff\xe0" + b"\x00" * 100  # starts with JPEG magic bytes
FAKE_WAV = b"RIFF" + b"\x00" * 100  # starts with WAV magic bytes


def _post_analyze(frame=None, audio=None, frame_type="image/jpeg", audio_type="audio/wav"):
    """Helper to POST to /analyze with given file parts."""
    files = {}
    if frame is not None:
        files["frame"] = ("frame.jpg", io.BytesIO(frame), frame_type)
    if audio is not None:
        files["audio"] = ("audio.wav", io.BytesIO(audio), audio_type)
    return client.post("/analyze", files=files)


# ── Happy path ──────────────────────────────────────────────────


class TestAnalyzeHappyPath:
    def test_returns_200_with_green_verdict(self):
        resp = _post_analyze(frame=FAKE_JPEG, audio=FAKE_WAV)
        assert resp.status_code == 200
        assert resp.json() == {"verdict": "GREEN"}

    def test_response_content_type_is_json(self):
        resp = _post_analyze(frame=FAKE_JPEG, audio=FAKE_WAV)
        assert resp.headers["content-type"] == "application/json"


# ── Validation (422) ────────────────────────────────────────────


class TestAnalyzeValidation:
    def test_missing_frame_returns_422(self):
        resp = _post_analyze(frame=None, audio=FAKE_WAV)
        assert resp.status_code == 422

    def test_missing_audio_returns_422(self):
        resp = _post_analyze(frame=FAKE_JPEG, audio=None)
        assert resp.status_code == 422

    def test_missing_both_returns_422(self):
        resp = client.post("/analyze")
        assert resp.status_code == 422

    def test_wrong_frame_content_type_returns_422(self):
        resp = _post_analyze(frame=FAKE_JPEG, audio=FAKE_WAV, frame_type="image/png")
        assert resp.status_code == 422
        assert "frame" in resp.json()["detail"].lower()

    def test_wrong_audio_content_type_returns_422(self):
        resp = _post_analyze(frame=FAKE_JPEG, audio=FAKE_WAV, audio_type="audio/mp3")
        assert resp.status_code == 422
        assert "audio" in resp.json()["detail"].lower()

    def test_empty_frame_returns_422(self):
        resp = _post_analyze(frame=b"", audio=FAKE_WAV)
        assert resp.status_code == 422
        assert "empty" in resp.json()["detail"].lower()

    def test_empty_audio_returns_422(self):
        resp = _post_analyze(frame=FAKE_JPEG, audio=b"")
        assert resp.status_code == 422
        assert "empty" in resp.json()["detail"].lower()


# ── ML function errors (500) ───────────────────────────────────


class TestAnalyzeMLErrors:
    def test_facial_analysis_failure_returns_500(self):
        with patch("routes.analyze.analyze_face", side_effect=RuntimeError("model crash")):
            resp = _post_analyze(frame=FAKE_JPEG, audio=FAKE_WAV)
        assert resp.status_code == 500
        assert "facial" in resp.json()["detail"].lower()

    def test_speech_analysis_failure_returns_500(self):
        with patch("routes.analyze.analyze_speech", side_effect=RuntimeError("model crash")):
            resp = _post_analyze(frame=FAKE_JPEG, audio=FAKE_WAV)
        assert resp.status_code == 500
        assert "speech" in resp.json()["detail"].lower()

    def test_fusion_failure_returns_500(self):
        with patch("routes.analyze.compute_verdict", side_effect=RuntimeError("fusion crash")):
            resp = _post_analyze(frame=FAKE_JPEG, audio=FAKE_WAV)
        assert resp.status_code == 500
        assert "fusion" in resp.json()["detail"].lower()


# ── Stub function wiring ───────────────────────────────────────


class TestAnalyzeStubWiring:
    def test_calls_analyze_face_with_image_bytes(self):
        with patch("routes.analyze.analyze_face", wraps=lambda b: FacialEmotionResult(
            emotions={"neutral": 1.0}, dominant="neutral", is_concerning=False
        )) as mock_face:
            _post_analyze(frame=FAKE_JPEG, audio=FAKE_WAV)
        mock_face.assert_called_once_with(FAKE_JPEG)

    def test_calls_analyze_speech_with_audio_bytes(self):
        with patch("routes.analyze.analyze_speech", wraps=lambda b: SpeechEmotionResult(
            emotions={"neutral": 1.0}, dominant="neutral", is_concerning=False
        )) as mock_speech:
            _post_analyze(frame=FAKE_JPEG, audio=FAKE_WAV)
        mock_speech.assert_called_once_with(FAKE_WAV)

    def test_calls_compute_verdict_with_both_results(self):
        with patch("routes.analyze.compute_verdict", wraps=lambda f, s: Verdict.GREEN) as mock_verdict:
            _post_analyze(frame=FAKE_JPEG, audio=FAKE_WAV)
        mock_verdict.assert_called_once()
        args = mock_verdict.call_args[0]
        assert isinstance(args[0], FacialEmotionResult)
        assert isinstance(args[1], SpeechEmotionResult)
