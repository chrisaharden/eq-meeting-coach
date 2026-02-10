# EQ Meeting Coach

Real-time emotional intelligence feedback during meetings. This app captures video and audio, sends it to a local inference server, and displays a full-screen color indicator (green/yellow/red) reflecting the user's emotional presentation.

## Project Structure

```
eq-meeting-coach/
├── app/                          # Android app (EPIC-1 & EPIC-2)
│   └── src/main/kotlin/com/eqcoach/
│       ├── config/AppConfig.kt
│       ├── model/SessionState.kt, Verdict.kt
│       ├── service/CaptureService.kt
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
```bash
./gradlew assembleDebug
```

### Server URL Configuration
Edit `app/src/main/kotlin/com/eqcoach/config/AppConfig.kt`:
```kotlin
object AppConfig {
    const val SERVER_URL = "https://192.168.1.100:8000"
}
```
Change `SERVER_URL` to the local IP of the machine running the inference server.

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
- **Min SDK**: 26 (Android 8.0) | **Target SDK**: 34

## Inference Server (EPIC-3)

```bash
cd inference-server
docker-compose up -d
curl http://localhost:8000/health
```

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
