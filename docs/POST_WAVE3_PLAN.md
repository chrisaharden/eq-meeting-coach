# EQ Meeting Coach — Post-Wave 3 Execution Plan

## Current Status
All 13 stories / 42 story points across all 4 epics are complete as of Wave 3.

### Execution Progress (2026-02-10)
- [x] **Step 1: Merge branches to main** — All 4 branches merged, conflicts resolved
- [x] **Step 2: Wire real ML models into server** — `models/__init__.py` imports from `eq_models`, Dockerfile updated with build context at repo root, `PYTHONPATH` set to `/app/src`
- [x] **Step 3: Integration testing milestones 1-3** — Server health, /analyze endpoint, and real DeepFace ML inference all working
- [ ] **Step 3: Milestones 4-5** — Requires Android phone on same Wi-Fi as server
- [ ] **Step 4: Configuration tuning**
- [ ] **Step 5: Post-v1.0 roadmap**

### Key fixes during integration:
- Added `libgl1-mesa-glx` and `libglib2.0-0` to Dockerfile (OpenCV dependency)
- Updated `deepface>=0.0.89` with `tf-keras>=2.16.0` (Keras 3 compatibility)
- Relaxed `numpy` version constraint to `>=1.24.0,<2.0` (dependency resolution)
- Changed docker-compose build context from `.` to `..` (repo root) to include `src/eq_models`

| Epic | Stories | Status |
|------|---------|--------|
| EPIC-1: Android Shell | 1.1, 1.2, 1.3, 1.4 | All complete |
| EPIC-2: Android Capture | 2.1, 2.2, 2.3 | All complete |
| EPIC-3: Server API | 3.1, 3.2 | All complete |
| EPIC-4: ML Models | 4.1, 4.2, 4.3 | All complete (48 tests) |

---

## Step 1: Merge Branches to Main

Merge all 4 worktree branches into main in dependency order:

```powershell
cd C:\dev\EQMeetingCoach\eq-meeting-coach
git checkout main

# 1. Merge Android shell foundation first
git merge android-shell --no-ff -m "Merge Android shell (EPIC-1)"

# 2. Merge server-side branches
git merge server-api --no-ff -m "Merge server API (EPIC-3)"
git merge ml-models --no-ff -m "Merge ML models (EPIC-4)"

# 3. Merge Android capture last (depends on all others)
git merge android-capture --no-ff -m "Merge Android capture (EPIC-2)"
```

Resolve any merge conflicts, especially:
- EPIC-1 and EPIC-2 both touch Android code
- EPIC-3 and EPIC-4 both work in `inference-server/`

## Step 2: Wire Real ML Models into the Server

The server (`eq-server-api`) currently uses **stub functions** in `models/stubs.py`. After merge, replace the stub imports in the `/analyze` endpoint with the real implementations from EPIC-4's `eq_models` package:
- `analyze_face` from `eq_models.facial`
- `analyze_speech` from `eq_models.speech`
- `compute_verdict` from `eq_models.fusion`

## Step 3: Integration Testing — 5 Milestones

### Milestone 1: Server Health Check
```bash
cd inference-server
docker-compose up -d
curl http://localhost:8000/health
# Expected: {"status": "ok", "models_loaded": true}
```

### Milestone 2: Stub /analyze Endpoint
```bash
curl -X POST http://localhost:8000/analyze \
  -F "frame=@test_data/neutral_face.jpg" \
  -F "audio=@test_data/calm_speech.wav"
# Expected: {"verdict": "GREEN"}
```

### Milestone 3: Real ML Inference
```bash
curl -X POST http://localhost:8000/analyze \
  -F "frame=@test_data/angry_face.jpg" \
  -F "audio=@test_data/angry_speech.wav"
# Expected: {"verdict": "RED"} or {"verdict": "YELLOW"}
```

### Milestone 4: Android to Server Communication
1. Start inference server on laptop
2. Find laptop's local IP: `ipconfig`
3. Update `AppConfig.kt`: `SERVER_URL = "https://<your-ip>:8000"`
4. Build and run Android app on physical phone
5. Start a session
6. Check Android logs: `adb logcat | grep EQCoach`
7. Check server logs: `docker logs -f inference-server`
8. Verify requests every 4 seconds and verdicts received

### Milestone 5: End-to-End Validation
1. Sit in front of phone camera
2. Start a session
3. Make angry face / speak angrily → indicator turns YELLOW or RED
4. Relax, speak calmly → indicator turns GREEN
5. Run for extended session (target: 2 hours without crashes)

## Step 4: Configuration Tuning

After end-to-end works, tune `config.yaml` thresholds:

```yaml
# If too many RED verdicts (too sensitive):
facial:
  concerning_threshold: 0.50    # Up from 0.40
speech:
  concerning_threshold: 0.55    # Up from 0.45
fusion:
  red_threshold: 0.60           # Up from 0.50

# If not enough warnings (not sensitive enough):
facial:
  concerning_threshold: 0.30    # Down from 0.40
speech:
  concerning_threshold: 0.35    # Down from 0.45
fusion:
  red_threshold: 0.40           # Down from 0.50
```

Restart server after changes: `docker-compose restart`

## Step 5: Post-v1.0 Roadmap

1. **Calibration** — Record test sessions and tune thresholds to preference
2. **Cloud Migration** — Deploy inference server to AWS EC2 (see PRD Section 9)
3. **Volume Meter** — Add volume overlay to indicator screen
4. **Session History** — Post-meeting summaries
5. **iOS Version** — Port the Android app to iOS

## Success Criteria

The project is complete when:
- [ ] The Android app builds and runs on a physical device
- [ ] Permissions are granted and the home screen displays
- [ ] Starting a session shows the full-screen indicator (starting at GRAY)
- [ ] The inference server receives frames and audio every 4 seconds
- [ ] The server processes the data and returns verdicts
- [ ] The indicator updates to GREEN, YELLOW, or RED based on analysis
- [ ] Stopping the session cleanly shuts down capture and returns to home
- [ ] The system runs for a full 2-hour session without crashes
- [ ] All code is merged to main and documented
