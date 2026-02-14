# API Contract & Integration Interfaces

## Android ↔ Server API

**Endpoint:** `POST /analyze`

**Request Format:** multipart/form-data
- Part 1: `frame` (Content-Type: image/jpeg, binary JPEG data)
- Part 2: `audio` (Content-Type: audio/wav, binary WAV data)

**Response Format:** application/json
```json
{"verdict": "GREEN"}
```
Possible values: "GREEN", "YELLOW", "RED"

**Error Responses:**
- 422: Missing or invalid parts
- 500: Server-side analysis failure

---

## Server API ↔ ML Models

**Function Signatures:**
```python
from pydantic import BaseModel
from enum import Enum

class FacialEmotionResult(BaseModel):
    emotions: dict[str, float]
    dominant: str
    is_concerning: bool

class SpeechEmotionResult(BaseModel):
    emotions: dict[str, float]
    dominant: str
    is_concerning: bool

class Verdict(str, Enum):
    GREEN = "GREEN"
    YELLOW = "YELLOW"
    RED = "RED"

def analyze_face(image_bytes: bytes) -> FacialEmotionResult: ...
def analyze_speech(audio_bytes: bytes) -> SpeechEmotionResult: ...
def compute_verdict(facial: FacialEmotionResult, speech: SpeechEmotionResult) -> Verdict: ...
```

---

## Configuration

**AppConfig.kt** (Android):
- SERVER_URL = "https://192.168.1.100:8000"
- ANALYZE_ENDPOINT = "/analyze"
- CAPTURE_INTERVAL_SECONDS = 4L

**config.yaml** (Server):
- server_port: 8000
- facial.concerning_threshold: 0.40
- speech.concerning_threshold: 0.45
``