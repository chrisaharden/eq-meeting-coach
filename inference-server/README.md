# EQ Meeting Coach — Inference Server

Python/FastAPI inference server that receives image frames and audio clips from the Android app, analyzes them for emotional content, and returns a color verdict (GREEN, YELLOW, or RED).

## Prerequisites

- Docker with NVIDIA Container Toolkit (`nvidia-docker`)
- NVIDIA GPU with CUDA support (tested on RTX 4090)

## Quick Start

### 1. Build and Run with Docker Compose

```bash
docker-compose up --build
```

The server starts on port **8000** by default. It maps the host GPU into the container automatically.

### 2. Verify the Server is Running

```bash
curl http://localhost:8000/health
```

Expected response:
```json
{
  "status": "ok",
  "models_loaded": true
}
```

## Configuration

All tunable parameters are in **`config.yaml`**, which is mounted into the container as a read-only volume. Edit it without rebuilding the image.

```yaml
# ─── Server ───
server_port: 8000
log_level: INFO

# ─── Facial Emotion ───
facial:
  concerning_threshold: 0.40    # angry + disgust combined score
  backend: tensorflow

# ─── Speech Emotion ───
speech:
  concerning_threshold: 0.45    # angry confidence threshold
  model_path: ./models/sensevoice-small
  sample_rate: 16000
  channels: 1

# ─── Score Fusion ───
fusion:
  facial_weight: 0.60           # weight for facial emotion score
  speech_weight: 0.40           # weight for speech emotion score
  green_threshold: 0.25         # below this = GREEN
  red_threshold: 0.50           # at or above this = RED
```

After editing `config.yaml`, restart the container:
```bash
docker-compose restart
```

## API Contract

### `GET /health`

Health check endpoint.

**Response** (HTTP 200):
```json
{
  "status": "ok",
  "models_loaded": true
}
```

### `POST /analyze`

Accepts an image frame and audio clip, returns an emotion verdict.

**Request** — `multipart/form-data`:
| Part    | Content-Type  | Description             |
|---------|---------------|-------------------------|
| `frame` | `image/jpeg`  | JPEG image frame        |
| `audio` | `audio/wav`   | WAV audio clip          |

**Success Response** (HTTP 200):
```json
{
  "verdict": "GREEN"
}
```

Possible verdict values: `"GREEN"`, `"YELLOW"`, `"RED"`

**Error Responses**:

- **422 Unprocessable Entity** — missing or invalid parts:
  ```json
  {
    "detail": "Invalid content type for frame: text/plain. Expected image/jpeg."
  }
  ```

- **500 Internal Server Error** — analysis failure:
  ```json
  {
    "detail": "Facial emotion analysis failed"
  }
  ```

**Example** — `curl`:
```bash
curl -X POST http://localhost:8000/analyze \
  -F "frame=@test_frame.jpg;type=image/jpeg" \
  -F "audio=@test_audio.wav;type=audio/wav"
```

## Project Structure

```
inference-server/
├── main.py              # FastAPI app entry point, health endpoint
├── routes/
│   ├── __init__.py
│   └── analyze.py       # POST /analyze endpoint
├── models/
│   ├── __init__.py
│   ├── schemas.py       # Pydantic models (FacialEmotionResult, etc.)
│   └── stubs.py         # Stub ML functions (replaced by EPIC-4)
├── config/
│   ├── __init__.py
│   └── settings.py      # Loads and validates config.yaml
├── config.yaml          # Externalized configuration
├── requirements.txt     # Python dependencies
├── Dockerfile           # Container build instructions
├── docker-compose.yml   # Orchestration with GPU support
└── README.md            # This file
```

## Development

### Running Locally (without Docker)

```bash
cd inference-server
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

### Building the Docker Image Manually

```bash
docker build -t eq-inference-server .
docker run --gpus all -p 8000:8000 -v ./config.yaml:/app/config.yaml:ro eq-inference-server
```
