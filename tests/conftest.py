"""Shared test fixtures for the EQ ML models test suite."""

from pathlib import Path

import pytest

TEST_DIR = Path(__file__).parent
TEST_IMAGES_DIR = TEST_DIR / "test_images"
TEST_AUDIO_DIR = TEST_DIR / "test_audio"


@pytest.fixture
def angry_face_bytes() -> bytes:
    return (TEST_IMAGES_DIR / "angry_face.jpg").read_bytes()


@pytest.fixture
def happy_face_bytes() -> bytes:
    return (TEST_IMAGES_DIR / "happy_face.jpg").read_bytes()


@pytest.fixture
def neutral_face_bytes() -> bytes:
    return (TEST_IMAGES_DIR / "neutral_face.jpg").read_bytes()


@pytest.fixture
def no_face_bytes() -> bytes:
    return (TEST_IMAGES_DIR / "no_face.jpg").read_bytes()


@pytest.fixture
def angry_speech_bytes() -> bytes:
    return (TEST_AUDIO_DIR / "angry_speech.wav").read_bytes()


@pytest.fixture
def calm_speech_bytes() -> bytes:
    return (TEST_AUDIO_DIR / "calm_speech.wav").read_bytes()


@pytest.fixture
def silence_bytes() -> bytes:
    return (TEST_AUDIO_DIR / "silence.wav").read_bytes()
