# 🧠 EQ Meeting Coach: Product Backlog
**Version:** 1.0 | **Date:** January 31, 2026  
*Optimized for Parallel Claude Code Instances*

---

## 📖 1. Execution Strategy
This backlog is designed for **parallel execution**. Each Epic can be assigned to a separate AI instance to maximize development speed.

* **Wave 1:** Foundation stories. Start these simultaneously across instances.
* **Wave 2:** Core logic and internal wiring.
* **Wave 3:** Final integration and end-to-end testing.
* **Stubs:** Items marked `(stub)` should use hardcoded returns initially to unblock downstream stories.

---

## 📊 2. Backlog Summary

| Epic | Component | Stories | Points |
| :--- | :--- | :---: | :---: |
| **EPIC-1** | Android App — Core Shell & UI | 4 | 10 |
| **EPIC-2** | Android App — Capture & Communication | 3 | 11 |
| **EPIC-3** | Inference Server — Project Setup & API | 2 | 6 |
| **EPIC-4** | Inference Server — ML Models & Fusion | 3 | 13 |
| **TOTAL** | | **12** | **40** |

---

## 🌊 3. Dependency & Execution Map



| Wave | Epic 1 (UI) | Epic 2 (Capture) | Epic 3 (API) | Epic 4 (ML) |
| :--- | :--- | :--- | :--- | :--- |
| **Wave 1** | STORY-1.1 | STORY-2.1 (stub) | STORY-3.1 | STORY-4.1, 4.2 |
| **Wave 2** | STORY-1.2, 1.3 | STORY-2.2 | STORY-3.2 (stub) | STORY-4.3 |
| **Wave 3** | STORY-1.4 | STORY-2.3 | — | — |

---

## 📱 EPIC-1: Android App — Core Shell & UI
> Everything related to the Android application project setup, permissions, and the visual feedback loop.

### STORY-1.1: Project Setup & Base Architecture `[3 pts]`
* **Role:** Developer
* **Goal:** Clean Android project scaffolded with Jetpack Compose.
* **Acceptance Criteria:**
    * [ ] Kotlin + Jetpack Compose project created.
    * [ ] Clean Architecture (ViewModel + Repository) implemented.
    * [ ] Dependencies: OkHttp, MediaRecorder/Camera2, kotlinx.serialization.
    * [ ] `AppConfig.kt` created for central configuration.

### STORY-1.2: Permission Request Handling `[2 pts]`
* **Role:** User
* **Goal:** Prompted for Camera/Mic permissions on first launch.
* **Acceptance Criteria:**
    * [ ] Request `CAMERA` and `RECORD_AUDIO` on start.
    * [ ] Non-dismissible "Rationale Screen" shown if denied.
    * [ ] Direct link to System Settings for permanent denials.

### STORY-1.3: Full-Screen Color Indicator UI `[3 pts]`
* **Role:** Active User
* **Goal:** Edge-to-edge color feedback (Green/Yellow/Red).
* **Acceptance Criteria:**
    * [ ] Edge-to-edge color fill with 250ms crossfade transitions.
    * [ ] Forced maximum screen brightness and **Wake Lock** active.
    * [ ] Semi-transparent 'Stop' button in corner with 48dp tap target.

### STORY-1.4: Session Start & Stop Lifecycle `[2 pts]`
* **Acceptance Criteria:**
    * [ ] Prominent 'Start Session' button on home screen.
    * [ ] Clean shutdown of capture and network on 'Stop'.

---

## 📸 EPIC-2: Android App — Capture & Communication
> Camera frame capture, microphone audio capture, and the HTTPS POST loop.

### STORY-2.1: Camera Frame Capture `[3 pts]`
* **Acceptance Criteria:**
    * [ ] Capture JPEG from front-facing camera every 4s.
    * [ ] Headless capture (no visible preview surface).
    * [ ] Thread-safe byte array hand-off to Communication Layer.

### STORY-2.2: Microphone Audio Capture `[3 pts]`
* **Acceptance Criteria:**
    * [ ] Record 4s chunks of 16kHz mono PCM/WAV.
    * [ ] Background thread execution to prevent UI blocking.

### STORY-2.3: Server Communication & Verdict Handling `[5 pts]`
* **Acceptance Criteria:**
    * [ ] Bundle JPEG + WAV into `multipart/form-data`.
    * [ ] POST to `/analyze` endpoint with TLS validation.
    * [ ] Update UI color based on JSON response (`GREEN`, `YELLOW`, `RED`).

---

## ⚙️ EPIC-3: Inference Server — API Layer
> The Python/FastAPI back-end server, Docker setup, and REST API.

### STORY-3.1: Server Setup & Docker `[3 pts]`
* **Acceptance Criteria:**
    * [ ] FastAPI project with `nvidia/cuda` base Docker image.
    * [ ] `docker-compose.yml` mapping host GPU to container.
    * [ ] `config.yaml` externalized for threshold tuning.
    * [ ] `/health` endpoint returns 200 OK.

---

## 🤖 EPIC-4: Inference Server — ML Models & Fusion
> Machine learning core: DeepFace and SenseVoice integration.

### STORY-4.1: Facial Emotion (DeepFace) `[5 pts]`
* **Acceptance Criteria:**
    * [ ] `analyze_face(bytes)` returns 7 emotion scores using GPU.
    * [ ] Graceful handling of "No Face Detected" (returns Neutral).

### STORY-4.2: Speech Emotion (SenseVoice) `[5 pts]`
* **Acceptance Criteria:**
    * [ ] FunASR toolkit installed with SenseVoice-Small.
    * [ ] Returns emotion labels (Angry, Happy, Sad, Neutral).

### STORY-4.3: Score Fusion & Verdict Engine `[3 pts]`
* **Acceptance Criteria:**
    * [ ] Weighted fusion: `(Facial * 0.6) + (Speech * 0.4)`.
    * **Escalation Rule:** If either input is `is_concerning`, escalate color (e.g., Green → Yellow).
    * [ ] Thresholds: `GREEN` (<0.25), `YELLOW` (<0.50), `RED` (>=0.50).

---

## 🛠️ Appendix: Reference Files

### `config.yaml` (Inference Server)
```yaml
fusion:
  facial_weight: 0.60
  speech_weight: 0.40
  green_threshold: 0.25
  red_threshold: 0.50