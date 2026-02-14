# EQ Meeting Coach — Multi-Agent Development Guide

## Overview
This guide explains how to use the 4 agent instruction files to develop the EQ Meeting Coach app in parallel using Claude Code instances and git worktrees.

## The Four Agents
1. **`agent_android_shell.md`** — EPIC-1: Android app foundation, permissions, UI, session lifecycle (10 points)
2. **`agent_android_capture.md`** — EPIC-2: Camera, microphone, server communication (11 points)
3. **`agent_server_api.md`** — EPIC-3: Python/FastAPI server, Docker, REST API (6 points)
4. **`agent_ml_models.md`** — EPIC-4: DeepFace, SenseVoice, score fusion (13 points)

**Total**: 13 stories, 42 story points

## Tools You'll Use

### Option 1: Crystal (Recommended for Windows)
Crystal is an open-source desktop app for managing multiple Claude Code sessions with git worktrees.

**Installation**:
```bash
git clone https://github.com/glassBead-tc/crystal.git
cd crystal
npm install
pnpm build:win  # For Windows
# Launch the .exe from the dist/ folder
```

**Usage**:
1. Open Crystal
2. Point it at your project repository
3. Create 4 worktrees (one per agent)
4. Assign each Claude Code session to a worktree
5. Load the agent instruction file into each session's context
6. Monitor progress from the Crystal dashboard

### Option 2: Manual Git Worktrees (Cross-Platform)
If you prefer to manage manually or Crystal doesn't work for you:

```bash
# Create the main repository
git init eq-meeting-coach
cd eq-meeting-coach
git commit --allow-empty -m "Initial commit"

# Create 4 worktrees (one per agent)
git worktree add ../eq-android-shell -b android-shell
git worktree add ../eq-android-capture -b android-capture
git worktree add ../eq-server-api -b server-api
git worktree add ../eq-ml-models -b ml-models

# List all worktrees
git worktree list
```

Then open 4 separate Claude Code instances, each working in a different worktree directory.

## Initial Repository Setup

Before starting the agents, create the repository structure:

```bash
# Main repo
eq-meeting-coach/
├── android/              # Android app (EPIC-1 and EPIC-2 work here)
├── inference-server/     # Python server (EPIC-3 and EPIC-4 work here)
├── docs/                 # PRD, stories, architecture diagrams
└── README.md

# Create initial structure
mkdir -p android inference-server docs
touch README.md

# Add the PRD and Stories docs
cp EQ_Meeting_Coach_PRD.docx docs/
cp EQ_Meeting_Coach_Stories.docx docs/
```

Commit this structure to main:
```bash
git add .
git commit -m "Initial project structure"
```

## Agent Coordination Protocol

### Wave 1: Foundation (All Parallel)
Each agent starts their Wave 1 stories simultaneously:

| Agent | Story | Worktree | What They Build |
|-------|-------|----------|-----------------|
| Android Shell | STORY-1.1 | android-shell | Android project, AppConfig.kt, README |
| Android Capture | STORY-2.1 (stub) | android-capture | Camera capture (stub) |
| Server API | STORY-3.1 | server-api | Docker, config.yaml, /health endpoint, stubs |
| ML Models | STORY-4.1, 4.2 | ml-models | DeepFace + SenseVoice integration |

**Instruction to each agent**:
1. Load their respective `agent_*.md` file
2. Tell them: "You are starting Wave 1. Complete your Wave 1 stories. Coordinate with the other agents on the shared interfaces as noted in your instructions."
3. Monitor their progress

**Expected output after Wave 1**:
- Android Shell: Buildable Android project with permissions flow and blank UI
- Android Capture: Camera capture logic (can be tested in isolation with stub output)
- Server API: Docker container that starts, exposes /health, and /analyze (returns hardcoded GREEN)
- ML Models: DeepFace and SenseVoice models loaded and callable via Python functions

### Wave 2: Build Out (Sequential Per Epic, Parallel Across Epics)
After Wave 1 completes, start Wave 2:

| Agent | Story | Depends On |
|-------|-------|------------|
| Android Shell | STORY-1.2, 1.3 | STORY-1.1 |
| Android Capture | STORY-2.2 | STORY-2.1 |
| Server API | STORY-3.2 | STORY-3.1 |
| ML Models | STORY-4.3 | STORY-4.1, 4.2 |

**Coordination checkpoint before Wave 2**:
- Android Shell and Android Capture: Agree on the `CaptureService` interface
- Android Capture and Server API: Confirm the exact API contract (endpoint, multipart format, JSON response)
- Server API and ML Models: Confirm the function signatures (`analyze_face`, `analyze_speech`, `compute_verdict`)

### Wave 3: Integration (Android-Only)
After Wave 2, only the Android Capture agent has work left:

| Agent | Story | Depends On |
|-------|-------|------------|
| Android Capture | STORY-2.3 | All previous stories |
| Android Shell | STORY-1.4 | STORY-1.1, 1.2, 1.3 |

**STORY-2.3** is the big integration story where the Android app talks to the real server with real models.

**Before starting STORY-2.3**:
1. Verify the inference server is running and responding to /analyze with real verdicts
2. Test manually with curl: `curl -X POST http://localhost:8000/analyze -F "frame=@test.jpg" -F "audio=@test.wav"`
3. Verify you get back `{"verdict": "GREEN"}` (or YELLOW/RED depending on the test data)

**Then** have the Android Capture agent complete STORY-2.3.

## Merging Strategy

### During Development (Waves 1-2)
Each agent works in their own branch/worktree. They should **not** merge to main yet. This avoids conflicts while work is in progress.

### After Wave 2 (Before Wave 3)
Merge the foundational branches:
```bash
# From the main repo
git checkout main

# Merge Android shell foundation
git merge android-shell --no-ff -m "Merge Android shell (EPIC-1 Wave 1-2)"

# Merge Server API + ML Models
git merge server-api --no-ff -m "Merge server API (EPIC-3)"
git merge ml-models --no-ff -m "Merge ML models (EPIC-4)"

# Note: Don't merge android-capture yet — it depends on the server being ready
```

### After Wave 3 (Final Integration)
```bash
# Merge Android capture (final)
git merge android-capture --no-ff -m "Merge Android capture (EPIC-2)"

# Merge Android shell final stories
git merge android-shell --no-ff -m "Merge Android shell final (STORY-1.4)"
```

Now `main` has the complete, integrated application.

## Testing Milestones

### Milestone 1: Server Health Check (After EPIC-3 STORY-3.1)
```bash
cd inference-server
docker-compose up -d
curl http://localhost:8000/health
# Expected: {"status": "ok", "models_loaded": false}
```

### Milestone 2: Server Stub Endpoint (After EPIC-3 STORY-3.2)
```bash
curl -X POST http://localhost:8000/analyze \
  -F "frame=@test_data/neutral_face.jpg" \
  -F "audio=@test_data/calm_speech.wav"
# Expected: {"verdict": "GREEN"}
```

### Milestone 3: Real ML Inference (After EPIC-4 Complete)
```bash
# Same curl command as Milestone 2, but now with real angry test data
curl -X POST http://localhost:8000/analyze \
  -F "frame=@test_data/angry_face.jpg" \
  -F "audio=@test_data/angry_speech.wav"
# Expected: {"verdict": "RED"} or {"verdict": "YELLOW"}
```

### Milestone 4: Android to Server (After EPIC-2 STORY-2.3)
1. Start the inference server on your laptop
2. Find your laptop's local IP: `ipconfig` (Windows) or `ifconfig` (Mac/Linux)
3. Update `AppConfig.kt` in the Android app: `SERVER_URL = "https://<your-ip>:8000"`
4. Build and run the Android app on your phone
5. Start a session
6. Check the Android logs: `adb logcat | grep EQCoach`
7. Check the server logs: `docker logs -f inference-server`
8. Verify requests are being sent every 4 seconds and verdicts are being received

### Milestone 5: End-to-End (After All Stories Complete)
1. Sit in front of your phone camera
2. Start a session
3. Make an angry face and speak angrily
4. Watch the indicator turn yellow or red
5. Relax, speak calmly
6. Watch the indicator turn green

## Troubleshooting

### Android App Won't Build
- Check that `AppConfig.kt` was created by the Android Shell agent
- Verify all dependencies are in `build.gradle`
- Run `./gradlew clean build` in the `android/` directory

### Server Won't Start
- Check Docker is running: `docker ps`
- Check GPU passthrough: `docker run --rm --gpus all nvidia/cuda:12.1.0-base-ubuntu22.04 nvidia-smi`
- Check logs: `docker logs inference-server`

### Android Can't Reach Server
- Verify server is running: `curl http://localhost:8000/health` from the laptop
- Verify firewall allows port 8000
- Verify phone and laptop are on the same Wi-Fi network
- Try accessing from phone's browser: `http://<laptop-ip>:8000/health`

### Models Are Slow
- Verify GPU is being used: check server logs for "Using device: cuda:0"
- Check GPU usage: `nvidia-smi` on the laptop
- Check config.yaml thresholds aren't causing unnecessary reprocessing

## Configuration Tuning

After the full system is working, tune the sensitivity in `config.yaml`:

```yaml
# If you're getting too many RED verdicts (too sensitive):
facial:
  concerning_threshold: 0.50    # Increase from 0.40
speech:
  concerning_threshold: 0.55    # Increase from 0.45
fusion:
  red_threshold: 0.60           # Increase from 0.50

# If you're not getting enough warnings (not sensitive enough):
facial:
  concerning_threshold: 0.30    # Decrease from 0.40
speech:
  concerning_threshold: 0.35    # Decrease from 0.45
fusion:
  red_threshold: 0.40           # Decrease from 0.50
```

Restart the server after changing config.yaml:
```bash
docker-compose restart
```

## Success Criteria

The project is complete when:
- ✅ The Android app builds and runs on a physical device
- ✅ Permissions are granted and the home screen displays
- ✅ Starting a session shows the full-screen indicator (starting at GRAY)
- ✅ The inference server receives frames and audio every 4 seconds
- ✅ The server processes the data and returns verdicts
- ✅ The indicator updates to GREEN, YELLOW, or RED based on the server's analysis
- ✅ Stopping the session cleanly shuts down capture and returns to the home screen
- ✅ The system runs for a full 2-hour session without crashes
- ✅ All code is merged to main and documented

## Next Steps After v1.0

Once the core system is working:
1. **Calibration**: Record a series of test sessions and tune thresholds to your preference
2. **Cloud Migration**: Deploy the inference server to AWS EC2 (see PRD Section 9)
3. **Volume Meter**: Add the volume overlay to the indicator screen
4. **Session History**: Add post-meeting summaries
5. **iOS Version**: Port the Android app to iOS

## Reference Documents
- **PRD**: `docs/EQ_Meeting_Coach_PRD.docx`
- **Stories**: `docs/EQ_Meeting_Coach_Stories.docx`
- **Agent Instructions**: `agent_android_shell.md`, `agent_android_capture.md`, `agent_server_api.md`, `agent_ml_models.md`

---

Good luck! You're building something genuinely useful. The multi-agent approach will let you move fast while keeping quality high. Stay coordinated, test at each milestone, and you'll have a working system faster than you think.
