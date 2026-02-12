# EQ Meeting Coach

Real-time emotional intelligence feedback during meetings. This app captures video and audio, sends it to a local inference server, and displays a full-screen color indicator (green/yellow/red) reflecting the user's emotional presentation.

## Project Structure

```
eq-meeting-coach/
├── app/                          # Android app (EPIC-1 & EPIC-2)
│   └── src/main/kotlin/com/eqcoach/
│       ├── capture/              # Camera & audio capture (EPIC-2)
│       ├── config/AppConfig.kt
│       ├── model/SessionState.kt, Verdict.kt
│       ├── network/AnalyzeClient.kt
│       ├── service/CaptureService.kt, CaptureServiceImpl.kt
│       ├── ui/navigation/, screens/, theme/
│       ├── viewmodel/SessionViewModel.kt
│       ├── EQCoachApplication.kt
│       └── MainActivity.kt
├── inference-server/             # Python/FastAPI server (EPIC-3)
│   ├── main.py, Dockerfile, docker-compose.yml
│   ├── routes/analyze.py
│   ├── models/schemas.py, stubs.py
│   └── config/settings.py, config.yaml
├── src/eq_models/                # ML models package (EPIC-4)
│   ├── facial.py                 # DeepFace integration
│   ├── speech.py                 # SenseVoice integration
│   ├── fusion.py                 # Score fusion engine
│   ├── models.py                 # Pydantic models & Verdict enum
│   └── config.py                 # YAML config loader
└── tests/                        # ML model tests
    ├── test_facial.py
    ├── test_speech.py
    └── test_fusion.py
```

## Android App

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK 34

### Build
1. Open the project in Android Studio (File -> Open -> select project root).
2. Let Gradle sync complete.
3. Select a device/emulator (minSdk 26 / Android 8.0+).
4. Click **Run** (or `./gradlew assembleDebug` from CLI).

```bash
./gradlew assembleDebug
```

### Server URL Configuration
Edit `app/src/main/kotlin/com/eqcoach/config/AppConfig.kt`:
```kotlin
object AppConfig {
    const val SERVER_URL = "http://<YOUR_PC_IP>:8000"
    const val ANALYZE_ENDPOINT = "/analyze"
}
```
Change `SERVER_URL` to the local IP of the machine running the inference server.
Also update the IP in `app/src/main/res/xml/network_security_config.xml` to match.

### App Flow
1. Launch -> Permission screen (requests CAMERA + RECORD_AUDIO)
2. Permissions granted -> Home screen with "Start Session" button
3. Start Session -> Full-screen color indicator (begins GRAY)
4. Inference server returns verdicts -> color updates (green/yellow/red)
5. Tap stop -> returns to Home screen

### Architecture
- **UI**: Jetpack Compose with Material 3
- **Navigation**: Compose Navigation, single-Activity
- **State**: ViewModel + StateFlow
- **Networking**: OkHttp
- **Serialization**: kotlinx.serialization
- **Permissions**: Accompanist Permissions library
- **Min SDK**: 26 (Android 8.0) | **Target SDK**: 34

## Running the Demo (End-to-End)

### Prerequisites
- Docker Desktop running on your PC
- Android phone with USB debugging enabled
- Phone and PC on the same Wi-Fi network
- Android SDK / `gradlew` available (or Android Studio)

### Step 1 — Find your PC's local IP
```bash
ipconfig    # Windows
ifconfig    # macOS/Linux
```
Update the IP in `AppConfig.kt` and `network_security_config.xml` if it differs from the current value.

### Step 2 — Download SenseVoice model weights (first time only)
```bash
pip install modelscope
python -c "from modelscope import snapshot_download; snapshot_download('iic/SenseVoiceSmall', local_dir='inference-server/models/sensevoice-small')"
```
This downloads ~893MB of model weights. DeepFace weights (~35MB) download automatically on first use inside the container.

### Step 3 — Start the inference server
First time (or after code/dependency changes):
```bash
cd inference-server
docker-compose up --build
```
Subsequent runs (no code changes):
```bash
cd inference-server
docker-compose up
```
Wait for the log message: `Server ready — real ML models available`

Verify the server is running:
```bash
curl http://localhost:8000/health
# Expected: {"status":"ok","models_loaded":true}
```

### Step 4 — Build and install the Android app
Connect your phone via USB, then:
```bash
./gradlew installDebug
```
Or open the project in Android Studio and click **Run**.

### Step 5 — Run on your phone
1. Open the **EQ Coach** app
2. Grant camera and microphone permissions when prompted
3. Tap **Start Session**
4. The screen color updates every ~4 seconds: GREEN (calm), YELLOW (elevated), or RED (high concern)

### Notes
- First analysis request may be slow (~30-60s) as models load for the first time inside the container
- Subsequent requests take ~5-8 seconds per verdict update
- The server runs CPU-only unless nvidia-docker is configured for GPU passthrough
- To remove GPU requirements from docker-compose, delete the `deploy.resources.reservations` block

## Inference Server (EPIC-3)

```bash
cd inference-server
docker-compose up --build
curl http://localhost:8000/health
```

### API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/health` | Health check — returns `{"status":"ok","models_loaded":true}` |
| `POST` | `/analyze` | Multipart form: `frame` (image/jpeg) + `audio` (audio/wav) — returns `{"verdict":"GREEN\|YELLOW\|RED"}` |

## ML Models (EPIC-4)

### Quick Start
```bash
pip install -e ".[dev]"
python -m tests.generate_fixtures   # Create synthetic test data
pytest                               # Run unit tests
```

### Usage
```python
from eq_models import analyze_face, analyze_speech, compute_verdict

facial_result = analyze_face(jpeg_bytes)
speech_result = analyze_speech(wav_bytes)
verdict = compute_verdict(facial_result, speech_result)
# verdict is Verdict.GREEN, Verdict.YELLOW, or Verdict.RED
```
