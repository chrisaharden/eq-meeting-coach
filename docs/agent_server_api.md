# Server API Agent — EPIC-3: Inference Server Setup & REST API

## Your Role
You are the Server API Agent responsible for building the Python/FastAPI inference server that receives media from the Android app, routes it to the ML models for analysis, and returns a color verdict. Your work includes Docker setup, project structure, the REST API endpoint, and the orchestration logic that ties the ML models together.

## Context
This is the back-end inference server for the EQ Meeting Coach app. The Android app sends a JPEG image frame and a WAV audio clip to your `/analyze` endpoint every 4 seconds. You call the facial emotion analysis function (built by the ML Models Agent) and the speech emotion analysis function (also built by the ML Models Agent), then use the score fusion function (also from ML Models Agent) to produce a single verdict: GREEN, YELLOW, or RED. You return that verdict as JSON.

The ML Models Agent (EPIC-4) is building the actual analysis and fusion functions. You are the API layer and orchestrator.

## Your Epics & Stories
You own **EPIC-3: Inference Server — Project Setup & API Layer** with 2 stories totaling **6 story points**:

### STORY-3.1: Server Project Setup, Docker, & Configuration (3 points)
**User Story**: As a developer deploying the inference server, I want a fully containerized Python project that starts with a single Docker command, so that the server is easy to deploy, portable, and requires zero manual setup beyond having Docker installed.

**Acceptance Criteria**:
1. A Python 3.10+ project is scaffolded with a clean directory structure: main.py (entry point), routes/, models/, config/, and a requirements.txt.
2. A Dockerfile is provided that builds a single image containing all dependencies, including PyTorch with CUDA support for GPU acceleration.
3. A docker-compose.yml is provided that maps the host GPU to the container (using nvidia-docker / --gpus all) and exposes the API port (default: 8000).
4. All tunable parameters (thresholds, fusion weights, capture interval expectations, model paths) are externalized in a config.yaml file that is mounted into the container — no rebuild required to change settings.
5. The server starts up successfully, loads models, and logs a ready message within 30 seconds on the RTX 4090 hardware.
6. A health-check endpoint (GET /health) returns HTTP 200 with a JSON body indicating server status and whether models are loaded.
7. A README documents: how to build the image, how to run it, how to edit config.yaml, and the full API contract (request/response format for /analyze).

**Tech Notes**: Use the official NVIDIA PyTorch Docker base image (e.g., pytorch/pytorch:latest with CUDA). FastAPI with Uvicorn as the ASGI server. Use Pydantic models to define the response schema strictly. The /analyze endpoint signature should be defined here even if the ML logic is stubbed — return a hardcoded GREEN verdict initially so EPIC-2 can integrate and test the full loop end-to-end before EPIC-4 is complete.

---

### STORY-3.2: API Endpoint — /analyze (Receive, Route, Respond) (3 points)
**User Story**: As the inference server receiving a request from the Android app, I want to accept the image frame and audio chunk, route them to the correct analysis pipelines, and return a unified color verdict, so that the Android app has a single, clean endpoint to call and a predictable response to parse.

**Acceptance Criteria**:
1. POST /analyze accepts a multipart/form-data request with two parts: 'frame' (image/jpeg) and 'audio' (audio/wav).
2. The endpoint validates that both parts are present and are of the expected content types. If validation fails, it returns HTTP 422 with a clear error message.
3. The endpoint passes the image bytes to the facial emotion analysis function (defined in EPIC-4) and the audio bytes to the speech emotion analysis function (defined in EPIC-4).
4. The endpoint receives the two sets of emotion scores back, passes them to the score fusion function (EPIC-4), and receives a single verdict (GREEN, YELLOW, or RED).
5. The endpoint returns HTTP 200 with a JSON response body: { "verdict": "GREEN" } (or YELLOW or RED).
6. If either analysis function raises an exception, the endpoint catches it, logs the error, and returns HTTP 500 with a JSON error body — it does not crash the server.
7. The endpoint does not store or log the image or audio data beyond the current request lifecycle.
8. Response time under normal conditions (both models loaded, GPU available) is under 3 seconds.

**Tech Notes**: Use FastAPI's UploadFile for multipart handling. Run the ML inference calls in a threadpool executor (run_in_executor) to avoid blocking the async event loop. Use Python's logging module — log at INFO level for requests and WARN/ERROR for failures. Do NOT log image or audio content.

**Depends on**: STORY-3.1 (project and Docker setup). **Coordinates with EPIC-4** — calls the analysis and fusion functions, but those can be stubs initially.

---

## Coordination with Other Agents

### With Android Capture Agent (EPIC-2)
You need to agree on the API contract. Here's the recommended spec (communicate this clearly):

**Endpoint**: `POST /analyze`

**Request** (multipart/form-data):
- Part 1: `frame` — Content-Type: `image/jpeg`, binary JPEG image
- Part 2: `audio` — Content-Type: `audio/wav`, binary WAV audio

**Response** (application/json) on success (HTTP 200):
```json
{
  "verdict": "GREEN"
}
```
Possible values: `"GREEN"`, `"YELLOW"`, `"RED"`

**Error responses**:
- **422 Unprocessable Entity** if either part is missing, wrong content type, or corrupted
  ```json
  {
    "detail": "Missing required part: frame"
  }
  ```
- **500 Internal Server Error** if analysis fails
  ```json
  {
    "detail": "Facial emotion analysis failed"
  }
  ```

### With ML Models Agent (EPIC-4)
The ML Models Agent is building three functions that you will call:

1. **`analyze_face(image_bytes: bytes) -> FacialEmotionResult`**
   - Input: Raw JPEG bytes
   - Output: Object with emotion scores and `is_concerning` flag

2. **`analyze_speech(audio_bytes: bytes) -> SpeechEmotionResult`**
   - Input: Raw WAV bytes
   - Output: Object with emotion scores and `is_concerning` flag

3. **`compute_verdict(facial: FacialEmotionResult, speech: SpeechEmotionResult) -> Verdict`**
   - Input: Both result objects
   - Output: Enum (GREEN, YELLOW, or RED)

**Coordinate on the exact return types.** Suggest Pydantic models:

```python
from pydantic import BaseModel
from enum import Enum

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
```

**In STORY-3.1, create stub versions of these functions**:
```python
# models/stubs.py (temporary, replaced by EPIC-4)
def analyze_face(image_bytes: bytes) -> FacialEmotionResult:
    return FacialEmotionResult(
        emotions={"neutral": 1.0},
        dominant="neutral",
        is_concerning=False
    )

def analyze_speech(audio_bytes: bytes) -> SpeechEmotionResult:
    return SpeechEmotionResult(
        emotions={"neutral": 1.0},
        dominant="neutral",
        is_concerning=False
    )

def compute_verdict(facial: FacialEmotionResult, speech: SpeechEmotionResult) -> Verdict:
    return Verdict.GREEN
```

This lets you complete STORY-3.2 and test the full Android → Server → Android loop before the real ML models are ready.

---

## Key Files to Create

### In STORY-3.1:

**`config.yaml`** (mounted into container):
```yaml
# ─── Server ───
server_port: 8000
log_level: INFO

# ─── Facial Emotion ───
facial:
  concerning_threshold: 0.40    # angry + disgust combined
  backend: tensorflow            # or pytorch

# ─── Speech Emotion ───
speech:
  concerning_threshold: 0.45    # angry confidence
  model_path: ./models/sensevoice-small
  sample_rate: 16000
  channels: 1

# ─── Score Fusion ───
fusion:
  facial_weight: 0.60
  speech_weight: 0.40
  green_threshold: 0.25          # below this = GREEN
  red_threshold: 0.50            # at or above this = RED
```

**`Dockerfile`**:
```dockerfile
FROM pytorch/pytorch:2.1.0-cuda12.1-cudnn8-runtime

WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y \
    libsndfile1 \
    && rm -rf /var/lib/apt/lists/*

# Install Python dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application
COPY . .

# Expose port
EXPOSE 8000

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s \
  CMD curl -f http://localhost:8000/health || exit 1

# Run server
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
```

**`docker-compose.yml`**:
```yaml
version: '3.8'

services:
  inference-server:
    build: .
    ports:
      - "8000:8000"
    volumes:
      - ./config.yaml:/app/config.yaml:ro
      - ./models:/app/models:ro
    deploy:
      resources:
        reservations:
          devices:
            - driver: nvidia
              count: all
              capabilities: [gpu]
    restart: unless-stopped
```

**`requirements.txt`**:
```
fastapi==0.104.1
uvicorn[standard]==0.24.0
python-multipart==0.0.6
pydantic==2.5.0
pyyaml==6.0.1
deepface==0.0.79
funasr==1.0.0
torch==2.1.0
torchaudio==2.1.0
numpy==1.24.3
Pillow==10.1.0
soundfile==0.12.1
```

**Project structure**:
```
inference-server/
├── main.py              # FastAPI app entry point
├── routes/
│   ├── __init__.py
│   └── analyze.py       # /analyze endpoint
├── models/
│   ├── __init__.py
│   ├── stubs.py         # Stub functions (replaced by EPIC-4)
│   └── (actual models added by EPIC-4)
├── config/
│   ├── __init__.py
│   └── settings.py      # Load config.yaml
├── config.yaml          # Configuration file
├── requirements.txt
├── Dockerfile
├── docker-compose.yml
└── README.md
```

---

## Execution Order

**Wave 1**:
1. **STORY-3.1** — Setup, Docker, config, health endpoint (can start immediately, parallel with all other epics)

**Wave 2**:
2. **STORY-3.2** — /analyze endpoint with stub ML functions (after 3.1, coordinate with EPIC-4 on function signatures)

---

## Testing Strategy

### Unit Testing
- Test that config.yaml is correctly loaded and validated
- Test that the /analyze endpoint correctly rejects requests missing parts
- Test that the endpoint correctly calls the analysis and fusion functions (with mocks)

### Integration Testing
With stub ML functions (STORY-3.1):
1. Build the Docker image
2. Start the container
3. Use `curl` or Postman to POST a test JPEG and WAV file to `/analyze`
4. Verify you get back `{"verdict": "GREEN"}`
5. Test error cases: missing frame, missing audio, malformed multipart request

With real ML functions (after EPIC-4 is complete):
1. Post a real image of an angry face + angry speech audio
2. Verify the verdict is RED or YELLOW
3. Post a calm face + calm speech
4. Verify the verdict is GREEN

### End-to-End Testing
Once EPIC-2 (Android Capture Agent) is complete:
1. Start the inference server on your laptop
2. Run the Android app on your phone, pointed at your laptop's IP
3. Start a session
4. Verify that the app is sending requests every 4 seconds
5. Verify that the server is processing them and returning verdicts
6. Check the server logs to see request/response cycles

---

## Success Criteria
When your epic is complete:
- The server starts in a Docker container with GPU support
- The `/health` endpoint returns HTTP 200 with server status
- The `/analyze` endpoint accepts multipart requests and returns JSON verdicts
- The server calls the ML analysis and fusion functions (stubs initially, real implementations from EPIC-4 later)
- All configuration is externalized in `config.yaml`
- The server is fully documented and ready for end-to-end testing with the Android app

---

## Reference Documents
- **PRD**: `EQ_Meeting_Coach_PRD.docx` — Section 5.2 (System Architecture), Section 6.2 (Tech Stack), Section 7.3 (FR-03)
- **Backlog**: `EQ_Meeting_Coach_Stories.docx` — EPIC-3 section, Appendix A (config.yaml reference)

Good luck! You are the orchestrator. Keep the API clean, the error handling robust, and the coordination with the ML Models Agent tight.
