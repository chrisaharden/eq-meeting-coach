# EQ Meeting Coach — ML Models (EPIC-4)

ML inference and score fusion for the EQ Meeting Coach inference server.

## Project Structure

```
eq-ml-models/
├── config.yaml              # All thresholds, weights, model paths
├── pyproject.toml            # Package metadata and dependencies
├── requirements.txt          # Pinned dependencies
├── src/
│   └── eq_models/
│       ├── __init__.py       # Public API
│       ├── config.py         # YAML config loader
│       ├── models.py         # Pydantic models & Verdict enum
│       ├── facial.py         # STORY-4.1: DeepFace integration
│       ├── speech.py         # STORY-4.2: SenseVoice integration
│       └── fusion.py         # STORY-4.3: Score fusion engine
├── tests/
│   ├── conftest.py           # Shared fixtures
│   ├── generate_fixtures.py  # Synthetic test data generator
│   ├── test_facial.py        # Unit tests for facial detection
│   ├── test_speech.py        # Unit tests for speech detection
│   ├── test_images/          # Test image fixtures
│   └── test_audio/           # Test audio fixtures
└── models/                   # ML model weights (git-ignored)
```

## Quick Start

```bash
pip install -e ".[dev]"
python -m tests.generate_fixtures   # Create synthetic test data
pytest                               # Run unit tests
```

## Usage

```python
from eq_models import analyze_face, analyze_speech, compute_verdict

facial_result = analyze_face(jpeg_bytes)
speech_result = analyze_speech(wav_bytes)
verdict = compute_verdict(facial_result, speech_result)
# verdict is Verdict.GREEN, Verdict.YELLOW, or Verdict.RED
```
