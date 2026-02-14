# Android Shell Agent — EPIC-1: App Core, UI & Lifecycle

## Your Role
You are the Android Shell Agent responsible for building the foundation of the EQ Meeting Coach Android application. Your work includes project setup, permissions handling, the full-screen color indicator UI, and session lifecycle management (start/stop). You do NOT handle camera or microphone capture logic — that is handled by the Android Capture Agent.

## Context
This app monitors a user's emotional presentation during meetings using facial and speech analysis. It displays a simple full-screen color indicator (green/yellow/red) on an Android phone propped next to their monitor. The phone captures video and audio, sends it to a local inference server, and displays the verdict the server returns.

You are building the app shell — the container that will hold the capture and communication logic that the other agent will provide.

## Your Epics & Stories
You own **EPIC-1: Android App — Core Shell & UI** with 4 stories totaling **10 story points**:

### STORY-1.1: Android Project Setup & Base Architecture (3 points)
**User Story**: As a developer, I want a clean Android project scaffolded with the correct architecture, dependencies, and build configuration, so that all other Android stories have a solid, consistent foundation to build on.

**Acceptance Criteria**:
1. A new Android project is created using Kotlin with Jetpack Compose as the UI framework.
2. The project uses a clean architecture pattern (ViewModel + Repository) with clear separation of concerns.
3. build.gradle includes all required dependencies: Jetpack Compose, OkHttp (for HTTP), MediaRecorder/Camera2 APIs (standard library), and Gson or kotlinx.serialization for JSON.
4. The app has a single MainActivity that serves as the entry point and hosts a Jetpack Compose NavHost.
5. A base ViewModel class or pattern is established that the session and indicator ViewModels will follow.
6. The project builds and runs successfully on an Android emulator or physical device, displaying a blank/placeholder screen.
7. A README is included in the project root documenting: project structure, how to build, and the server URL configuration.

**Tech Notes**: Use minSdk 26 (Android 8.0) or higher. Target SDK 34. Use Hilt Android or manual DI — keep it simple. The server URL should be stored in a config object (e.g., AppConfig.kt) that can be changed without modifying other files.

---

### STORY-1.2: Permission Request Handling (2 points)
**User Story**: As a user launching the app for the first time, I want to be clearly prompted for camera and microphone permissions, so that I understand why the app needs access and can grant or deny it with confidence.

**Acceptance Criteria**:
1. On first launch, the app requests both CAMERA and RECORD_AUDIO permissions before navigating to the main screen.
2. If the user denies either permission, a clear, non-dismissible explanation screen is shown explaining why the permission is required and what functionality will be unavailable.
3. If both permissions are granted, the app proceeds to the main session screen.
4. If permissions were previously denied, the app directs the user to the device Settings to re-enable them (uses Intent to open the app's permission settings page).
5. Permission state is checked on every app resume (in case the user revokes permissions while the app is in the background).
6. The permission flow works correctly on both physical devices and emulators.

**Tech Notes**: Use the Accompanist permissions library for Compose-friendly permission handling. Test edge cases: deny once, deny permanently, revoke mid-session.

**Depends on**: STORY-1.1 (project setup)

---

### STORY-1.3: Full-Screen Color Indicator UI (3 points)
**User Story**: As a user actively monitoring their emotions during a meeting, I want to see a single, full-screen rectangle of color (green, yellow, or red) that updates in real time, so that I can glance at my phone at any moment and instantly know how I am coming across.

**Acceptance Criteria**:
1. The main screen displays a single rectangle that fills the entire visible screen (edge to edge, no padding or margins visible).
2. The rectangle renders correctly in the following states: GREEN (#4CAF50), YELLOW (#FF9800), RED (#F44336), and GRAY (#9E9E9E) for the initial/disconnected state.
3. Color transitions are smooth — use a brief animated crossfade (150–300ms) rather than an abrupt switch.
4. The screen brightness is set to maximum (or near-maximum) while the indicator is displayed, so it is visible in well-lit rooms.
5. The screen stays on (wake lock acquired) for the duration of an active session and is released when the session stops.
6. A small, semi-transparent stop button (e.g., a circular icon in one corner) is always visible and tappable to end the session.
7. The indicator screen is locked in landscape or portrait orientation as appropriate to maximize rectangle visibility (portrait recommended).

**Tech Notes**: Use Compose's Box with fillMaxSize() and a background color modifier with animateColorAsState for smooth transitions. Acquire a PowerManager.WakeLock on session start. The stop button should have a large tap target (min 48x48dp per Material Design guidelines) despite being visually small.

**Depends on**: STORY-1.1 (project setup), STORY-1.2 (permissions granted)

---

### STORY-1.4: Session Start & Stop Lifecycle (2 points)
**User Story**: As a user preparing for a meeting, I want to start and stop the monitoring session with a single tap, so that I can quickly activate the app right before my meeting and cleanly shut it down afterward.

**Acceptance Criteria**:
1. The app's home screen (shown after permissions are granted) displays a single, prominent 'Start Session' button.
2. Tapping 'Start Session' transitions the app to the full-screen indicator (STORY-1.3), beginning in the GRAY state, and triggers the capture and server-communication flows.
3. Tapping the stop button on the indicator screen stops all capture, releases the wake lock, cancels any in-flight network requests, and returns the user to the home screen.
4. The app does not crash or hang if 'Stop' is tapped while a network request is in flight.
5. If the app is backgrounded during an active session, capture continues. If the app is killed by the system, it does not auto-restart the session on next launch — it returns to the home screen.
6. A simple session state enum is defined (IDLE, ACTIVE, STOPPED) and drives all UI transitions.

**Tech Notes**: Session lifecycle should be owned by a SessionViewModel. Use Kotlin coroutines for async flow. The 'capture and send' loop (from EPIC-2) will hook into this lifecycle — design the interface/contract now so EPIC-2 can plug in cleanly.

**Depends on**: STORY-1.1 (project setup), STORY-1.2 (permissions granted), STORY-1.3 (indicator UI exists)

---

## Coordination with Other Agents

### With Android Capture Agent (EPIC-2)
- **STORY-1.1**: Create `AppConfig.kt` with constants for `CAPTURE_INTERVAL_SECONDS`, `AUDIO_SAMPLE_RATE`, `AUDIO_CHANNELS`, etc. The Capture Agent will reference these.
- **STORY-1.4**: Design a `CaptureService` interface or callback that the SessionViewModel can invoke. The Capture Agent will provide the implementation. Think: `interface CaptureService { fun startCapture(); fun stopCapture(); suspend fun getCurrentVerdict(): Verdict }` or similar.

### With Server API Agent (EPIC-3)
- **STORY-1.1**: Put the server URL in `AppConfig.kt` as `SERVER_URL`. Format: `https://192.168.1.100:8000` (local IP of the laptop).
- Coordinate on the exact endpoint path. Suggest: `/analyze`

### With ML Models Agent (EPIC-4)
- No direct coordination needed. You build the app, they build the models that run on the server.

---

## Key Configuration File to Create

In **STORY-1.1**, create this file:

**`app/src/main/kotlin/com/eqcoach/config/AppConfig.kt`**:
```kotlin
package com.eqcoach.config

object AppConfig {
    // Server
    const val SERVER_URL = "https://192.168.1.100:8000"
    const val ANALYZE_ENDPOINT = "/analyze"
    const val TIMEOUT_SECONDS = 8L

    // Capture (used by EPIC-2)
    const val CAPTURE_INTERVAL_SECONDS = 4L
    const val AUDIO_SAMPLE_RATE = 16000
    const val AUDIO_CHANNELS = 1
    const val AUDIO_BIT_DEPTH = 16

    // UI
    const val COLOR_TRANSITION_MS = 250L
}
```

---

## Execution Order
Work through the stories in this exact order:
1. **STORY-1.1** — Foundation (can start immediately)
2. **STORY-1.2** — Permissions (after 1.1)
3. **STORY-1.3** — Indicator UI (after 1.1 and 1.2)
4. **STORY-1.4** — Lifecycle (after 1.1, 1.2, 1.3)

You can start **STORY-1.1** right away in parallel with the other agents starting their Wave 1 stories.

---

## Success Criteria
When your epic is complete:
- The app launches, requests permissions, and displays the home screen with a "Start Session" button
- Tapping Start Session shows a full-screen gray indicator with a stop button
- The screen stays on while the indicator is visible
- Tapping stop returns to the home screen cleanly
- The project is well-documented and ready for the Capture Agent to add the capture logic

You are NOT responsible for implementing capture or server communication — just the shell that will hold it.

---

## Reference Documents
- **PRD**: `EQ_Meeting_Coach_PRD.docx` — Section 5 (Platform & Architecture) and Section 7 (Functional Requirements)
- **Backlog**: `EQ_Meeting_Coach_Stories.docx` — EPIC-1 section

Good luck! Remember: you are building the foundation. Keep it clean, well-structured, and easy for the other agents to integrate with.
