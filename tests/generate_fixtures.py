"""Generate synthetic test fixtures for unit tests.

Run once to create test images and audio files:
    python -m tests.generate_fixtures
"""

import io
from pathlib import Path

import numpy as np
from PIL import Image

TEST_DIR = Path(__file__).parent
TEST_IMAGES_DIR = TEST_DIR / "test_images"
TEST_AUDIO_DIR = TEST_DIR / "test_audio"


def _save_solid_image(path: Path, color: tuple[int, int, int], size: int = 224) -> None:
    """Save a solid-color JPEG — used as a no-face placeholder."""
    img = Image.new("RGB", (size, size), color)
    img.save(path, "JPEG")


def _save_simple_face_image(path: Path, size: int = 224) -> None:
    """Save a minimal synthetic 'face' image (circle with dots).

    This is NOT intended to trigger real emotion detection — it exists so
    that DeepFace has *something* to work with.  Real test images should
    be added manually for integration tests.
    """
    img = Image.new("RGB", (size, size), (200, 180, 160))
    pixels = np.array(img)

    # Draw a crude face oval.
    cy, cx = size // 2, size // 2
    for y in range(size):
        for x in range(size):
            if ((x - cx) / 40) ** 2 + ((y - cy) / 55) ** 2 < 1:
                pixels[y, x] = [220, 195, 170]

    # Eyes.
    for dy, dx in [(-15, -12), (-15, 12)]:
        ey, ex = cy + dy, cx + dx
        for y in range(ey - 3, ey + 3):
            for x in range(ex - 3, ex + 3):
                if 0 <= y < size and 0 <= x < size:
                    pixels[y, x] = [50, 50, 50]

    Image.fromarray(pixels).save(path, "JPEG")


def _save_wav(path: Path, data: np.ndarray, sample_rate: int = 16000) -> None:
    """Write a WAV file from a numpy array."""
    import soundfile as sf
    sf.write(str(path), data, sample_rate, subtype="PCM_16")


def generate_images() -> None:
    TEST_IMAGES_DIR.mkdir(parents=True, exist_ok=True)

    # Synthetic face images — real emotion detection results will vary.
    _save_simple_face_image(TEST_IMAGES_DIR / "angry_face.jpg")
    _save_simple_face_image(TEST_IMAGES_DIR / "happy_face.jpg")
    _save_simple_face_image(TEST_IMAGES_DIR / "neutral_face.jpg")

    # No-face image — solid blue rectangle.
    _save_solid_image(TEST_IMAGES_DIR / "no_face.jpg", (30, 60, 180))

    print("Test images generated.")


def generate_audio() -> None:
    TEST_AUDIO_DIR.mkdir(parents=True, exist_ok=True)
    sr = 16000
    duration = 4.0
    t = np.linspace(0, duration, int(sr * duration), endpoint=False)

    # Angry speech proxy: loud, noisy, with harsh tonal content.
    angry = 0.8 * np.sin(2 * np.pi * 200 * t) + 0.3 * np.random.randn(len(t))
    angry = np.clip(angry, -1.0, 1.0).astype(np.float32)
    _save_wav(TEST_AUDIO_DIR / "angry_speech.wav", angry, sr)

    # Calm speech proxy: soft sine wave.
    calm = 0.15 * np.sin(2 * np.pi * 300 * t).astype(np.float32)
    _save_wav(TEST_AUDIO_DIR / "calm_speech.wav", calm, sr)

    # Silence.
    silence = np.zeros(int(sr * duration), dtype=np.float32)
    _save_wav(TEST_AUDIO_DIR / "silence.wav", silence, sr)

    print("Test audio generated.")


if __name__ == "__main__":
    generate_images()
    generate_audio()
