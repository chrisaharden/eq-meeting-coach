# EQ Meeting Coach — Android App

Real-time emotional intelligence feedback during meetings. This app captures video and audio, sends it to a local inference server, and displays a full-screen color indicator (green/yellow/red) reflecting the user's emotional presentation.

## Project Structure

```
app/src/main/kotlin/com/eqcoach/
├── config/
│   └── AppConfig.kt          # Server URL, capture intervals, UI constants
├── model/
│   ├── SessionState.kt       # Session lifecycle enum (IDLE, ACTIVE, STOPPED)
│   └── Verdict.kt            # Server verdict enum (GREEN, YELLOW, RED, GRAY)
├── service/
│   └── CaptureService.kt     # Interface for capture logic (implemented by EPIC-2)
├── ui/
│   ├── navigation/
│   │   ├── NavGraph.kt        # Compose NavHost with all routes
│   │   └── Screen.kt          # Route definitions
│   ├── screens/
│   │   ├── home/
│   │   │   └── HomeScreen.kt  # Start session button
│   │   ├── indicator/
│   │   │   └── IndicatorScreen.kt  # Full-screen color display
│   │   └── permission/
│   │       └── PermissionScreen.kt # Camera/mic permission flow
│   └── theme/
│       ├── Color.kt           # App color palette
│       └── Theme.kt           # Material3 theme
├── viewmodel/
│   └── SessionViewModel.kt   # Session lifecycle & verdict state
├── EQCoachApplication.kt     # Application class
└── MainActivity.kt           # Single activity entry point
```

## How to Build

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK 34

### Steps
1. Open the project in Android Studio (File → Open → select project root).
2. Let Gradle sync complete.
3. Select a device/emulator (minSdk 26 / Android 8.0+).
4. Click **Run** (or `./gradlew assembleDebug` from CLI).

### CLI Build
```bash
./gradlew assembleDebug
```

## Server URL Configuration

The inference server URL is configured in `app/src/main/kotlin/com/eqcoach/config/AppConfig.kt`:

```kotlin
object AppConfig {
    const val SERVER_URL = "https://192.168.1.100:8000"
    const val ANALYZE_ENDPOINT = "/analyze"
    // ...
}
```

Change `SERVER_URL` to the local IP address of the machine running the inference server. No other files need to be modified.

## Architecture

- **UI**: Jetpack Compose with Material 3
- **Navigation**: Compose Navigation with a single-Activity architecture
- **State**: ViewModel + StateFlow (no external state management library)
- **Networking**: OkHttp (used by EPIC-2 capture implementation)
- **Serialization**: kotlinx.serialization
- **Permissions**: Accompanist Permissions library
- **Min SDK**: 26 (Android 8.0) | **Target SDK**: 34

## App Flow

1. Launch → Permission screen (requests CAMERA + RECORD_AUDIO)
2. Permissions granted → Home screen with "Start Session" button
3. Start Session → Full-screen color indicator (begins GRAY)
4. Inference server returns verdicts → color updates (green/yellow/red)
5. Tap stop → returns to Home screen
