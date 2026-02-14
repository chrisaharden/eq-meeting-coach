# ML Models Agent — EPIC-4: ML Inference & Score Fusion

## Your Role
You are the ML Models Agent responsible for integrating the machine learning models that power the EQ Meeting Coach inference server. Your work includes integrating DeepFace for facial emotion detection, integrating SenseVoice for speech emotion detection, and implementing the score fusion algorithm that combines both signals into a single GREEN/YELLOW/RED verdict.

This is the most technically complex epic, but it is also self-contained — you are building pure Python functions with well-defined inputs and outputs that the Server API Agent will call.

## Context
The inference server receives a JPEG image frame and a WAV audio clip from the Android app. The Server API Agent (EPIC-3) routes those to you. You run facial emotion detection on the image, speech emotion detection on the audio, and then fuse the results to produce a single verdict. The Server API Agent takes that verdict and returns it to the Android app.

You are the ML brain of the system. The other agents just move data around — you make the actual judgment call about the user's emotional state.

## Your Epics & Stories
You own **EPIC-4: Inference Server — ML Models & Score Fusion** with 3 stories totaling **13 story points**:

### STORY-4.1: Facial Emotion Detection — DeepFace Integration (5 points)
**User Story**: As the inference server processing an incoming image frame, I want to run facial emotion detection on the frame and return a structured set of emotion scores, so that the score fusion layer has accurate, reliable data about the user's facial expression.

**Acceptance Criteria**:
1. The deepface Python package is installed and configured to use the GPU via the backend PyTorch/TensorFlow.
2. A function `analyze_face(image_bytes: bytes) -> FacialEmotionResult` is implemented. It accepts raw JPEG bytes and returns a structured result object.
3. The result object contains: a dictionary of all 7 emotion labels with their confidence scores (0.0–1.0), the dominant emotion label, and a boolean flag 'is_concerning' which is True if (angry + disgust) confidence exceeds the threshold defined in config.yaml (default: 0.40).
4. If no face is detected in the frame, the function returns a result with all scores at 0.0, dominant emotion as 'neutral', and is_concerning as False — it does not raise an exception.
5. The function runs inference on the GPU and completes in under 1 second for a single frame on the RTX 4090.
6. The function is unit-testable: a set of test images (angry, happy, neutral, no-face) is included in the test suite with expected outcomes.
7. The emotion detection threshold is read from config.yaml at startup and can be changed without restarting the server (hot-reload of config is a nice-to-have, but changing config.yaml and restarting the container is acceptable).

**Tech Notes**: Use DeepFace.analyze(img_path, actions=['emotion']) or the equivalent in-memory call. DeepFace supports multiple backends — use 'tensorflow' or 'pytorch' depending on what is in the Docker image. Wrap the call in a try/except to handle edge cases (corrupted image, unrecognizable face) gracefully. The FacialEmotionResult should be a Pydantic model for clean serialization.

---

### STORY-4.2: Speech Emotion Detection — SenseVoice Integration (5 points)
**User Story**: As the inference server processing an incoming audio chunk, I want to run speech emotion detection on the audio and return a structured set of emotion scores, so that the score fusion layer has accurate, reliable data about the user's vocal emotional state.

**Acceptance Criteria**:
1. The SenseVoice-Small model is downloaded and placed in the project's model directory (referenced in config.yaml). The FunASR toolkit is installed as the inference runtime.
2. A function `analyze_speech(audio_bytes: bytes) -> SpeechEmotionResult` is implemented. It accepts raw WAV/PCM bytes and returns a structured result object.
3. The result object contains: a dictionary of emotion labels (angry, happy, sad, neutral) with confidence scores (0.0–1.0), the dominant emotion, and a boolean 'is_concerning' which is True if the angry confidence exceeds the threshold in config.yaml (default: 0.45).
4. If the audio is silence or too short for meaningful analysis, the function returns neutral scores with is_concerning as False — it does not crash.
5. The function completes inference in under 500ms for a 4-second audio clip on the target hardware.
6. Unit tests are included with sample audio clips (angry speech, calm speech, silence) and expected outcome ranges.
7. The speech emotion threshold is read from config.yaml.

**Tech Notes**: Use FunASR's AutoModel or the simpler high-level pipeline if available. The model expects 16kHz mono PCM input — validate or resample incoming audio if it does not match. SenseVoice returns emotion tags inline with transcription; parse the emotion token from the output. The SpeechEmotionResult should be a Pydantic model.

**Depends on**: None — fully independent. Coordinates with EPIC-4 STORY-4.3 on the output schema.

---

### STORY-4.3: Score Fusion & Verdict Engine (3 points)
**User Story**: As the inference server with facial and speech emotion results, I want to combine both sets of scores into a single, reliable verdict, so that the user receives one clear signal that accounts for both what they look like and how they sound.

**Acceptance Criteria**:
1. A function `compute_verdict(facial: FacialEmotionResult, speech: SpeechEmotionResult) -> Verdict` is implemented.
2. The function computes a weighted fusion score: (facial_anger_score * facial_weight) + (speech_anger_score * speech_weight). Default weights are facial: 0.60, speech: 0.40. Weights are read from config.yaml.
3. The fused score is mapped to a verdict using configurable thresholds from config.yaml: GREEN if fused score < green_threshold (default: 0.25), YELLOW if fused score < red_threshold (default: 0.50), RED if fused score >= red_threshold.
4. If either the facial or speech result has is_concerning = True, the verdict is escalated by at least one level (e.g., GREEN becomes YELLOW, YELLOW becomes RED). This ensures that a strong signal from either channel alone is not diluted by a calm reading from the other.
5. The Verdict return type is an enum (GREEN, YELLOW, RED) — not a raw string — to prevent typos downstream.
6. Unit tests cover: both calm (GREEN expected), one concerning (YELLOW expected), both concerning (RED expected), and edge cases at exact threshold boundaries.
7. All weights and thresholds are in config.yaml and require no code change to tune.

**Tech Notes**: Keep this function pure — no side effects, no I/O, no model loading. It should be trivially fast (microseconds). This makes it easy to unit test and reason about. The escalation rule in AC4 is critical for user experience — a single strong anger signal should not be buried by an averaging effect.

**Depends on**: STORY-4.1 (FacialEmotionResult type), STORY-4.2 (SpeechEmotionResult type). These can be stub types initially if needed.

---

## Coordination with Other Agents

### With Server API Agent (EPIC-3)
The Server API Agent is calling your three functions from their `/analyze` endpoint. You need to agree on the exact function signatures and return types.

**Recommended contract** (share this with EPIC-3):

```python
from pydantic import BaseModel
from enum import Enum

class FacialEmotionResult(BaseModel):
    emotions: dict[str, float]  # e.g., {"angry": 0.7, "disgust": 0.1, "neutral": 0.1, ...}
    dominant: str                # e.g., "angry"
    is_concerning: bool          # True if (angry + disgust) > threshold

class SpeechEmotionResult(BaseModel):
    emotions: dict[str, float]  # e.g., {"angry": 0.5, "neutral": 0.4, "happy": 0.1, "sad": 0.0}
    dominant: str                # e.g., "angry"
    is_concerning: bool          # True if angry > threshold

class Verdict(str, Enum):
    GREEN = "GREEN"
    YELLOW = "YELLOW"
    RED = "RED"

# Your three functions:
def analyze_face(image_bytes: bytes) -> FacialEmotionResult:
    """
    Run facial emotion detection on a JPEG image.
    
    Args:
        image_bytes: Raw JPEG image data
        
    Returns:
        FacialEmotionResult with emotion scores, dominant emotion, and concerning flag
        
    Raises:
        No exceptions — returns neutral result if face detection fails
    """
    pass

def analyze_speech(audio_bytes: bytes) -> SpeechEmotionResult:
    """
    Run speech emotion detection on a WAV audio clip.
    
    Args:
        audio_bytes: Raw WAV audio data (16kHz, mono, 16-bit PCM)
        
    Returns:
        SpeechEmotionResult with emotion scores, dominant emotion, and concerning flag
        
    Raises:
        No exceptions — returns neutral result if audio is silence or too short
    """
    pass

def compute_verdict(facial: FacialEmotionResult, speech: SpeechEmotionResult) -> Verdict:
    """
    Fuse facial and speech emotion results into a single verdict.
    
    Args:
        facial: Result from analyze_face
        speech: Result from analyze_speech
        
    Returns:
        Verdict (GREEN, YELLOW, or RED)
    """
    pass
```

The Server API Agent will import these functions and call them in their `/analyze` endpoint handler.

### With Android Agents (EPIC-1, EPIC-2)
- No direct coordination needed. They send data to the server, you process it. Your output goes through the Server API Agent (EPIC-3) back to them.

---

## Key Configuration Values

All thresholds and weights should be read from `config.yaml` (created by EPIC-3 in STORY-3.1):

```yaml
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

**In STORY-4.1 and STORY-4.2**: Load this config at module initialization (not on every function call for performance).

---

## Execution Order

All three stories in this epic can run **fully in parallel** with each other and with the other epics:

**Wave 1**:
1. **STORY-4.1** — DeepFace integration (start immediately)
2. **STORY-4.2** — SenseVoice integration (start immediately)

**Wave 2**:
3. **STORY-4.3** — Score fusion (after 4.1 and 4.2 have defined the result types, but can use stub types initially)

You can work on 4.1 and 4.2 simultaneously. Once you have the `FacialEmotionResult` and `SpeechEmotionResult` Pydantic models defined, you can start 4.3 even if the actual model inference code isn't done yet.

---

## Model Setup Instructions

### DeepFace (STORY-4.1)
DeepFace will auto-download models on first use. No manual setup needed beyond installing the package.

**Backend selection**:
- If using TensorFlow: `DeepFace.analyze(img, actions=['emotion'], enforce_detection=False, detector_backend='opencv')`
- If using PyTorch: You may need to use a different face detection backend supported by DeepFace

**GPU usage**: DeepFace will use GPU automatically if TensorFlow or PyTorch is compiled with CUDA support.

### SenseVoice (STORY-4.2)
SenseVoice models must be downloaded manually.

**Download instructions**:
```bash
# From ModelScope (if accessible):
git clone https://www.modelscope.cn/iic/SenseVoiceSmall.git models/sensevoice-small

# Or from Hugging Face:
git lfs clone https://huggingface.co/FunAudioLLM/SenseVoice models/sensevoice-small
```

**Usage with FunASR**:
```python
from funasr import AutoModel

model = AutoModel(
    model="models/sensevoice-small",
    device="cuda:0"
)

# Inference
result = model.generate(input=audio_file_path, language="auto")
# Result contains transcription and emotion tags
```

**Emotion parsing**: SenseVoice returns emotions as special tokens in the transcription. You'll need to parse these. Check the FunASR docs for the exact format (e.g., `<|HAPPY|>`, `<|ANGRY|>`, etc.).

---

## Testing Strategy

### Unit Testing

**For analyze_face (STORY-4.1)**:
Create a test suite with sample images:
- `test_images/angry_face.jpg` — Expect high angry score, is_concerning=True
- `test_images/happy_face.jpg` — Expect high happy score, is_concerning=False
- `test_images/neutral_face.jpg` — Expect high neutral score, is_concerning=False
- `test_images/no_face.jpg` — Expect all zeros, neutral dominant, is_concerning=False

**For analyze_speech (STORY-4.2)**:
Create a test suite with sample audio:
- `test_audio/angry_speech.wav` — Expect high angry score, is_concerning=True
- `test_audio/calm_speech.wav` — Expect neutral or happy, is_concerning=False
- `test_audio/silence.wav` — Expect neutral, is_concerning=False

**For compute_verdict (STORY-4.3)**:
Pure unit tests with mock FacialEmotionResult and SpeechEmotionResult objects:
- Both calm → GREEN
- Facial concerning, speech calm → YELLOW or RED (depending on weights)
- Both concerning → RED
- Exact threshold boundaries (e.g., fused score = 0.25 should be GREEN, 0.251 should be YELLOW)

### Integration Testing
Once all three stories are complete:
1. Run the full inference pipeline on real test data
2. Record a video of yourself making an angry face + angry speech
3. Extract a frame and a 4-second audio clip
4. Call `analyze_face(frame)`, `analyze_speech(audio)`, `compute_verdict(...)`
5. Verify the verdict is RED or YELLOW

### Performance Testing
- Run 100 inferences back-to-back on the RTX 4090
- Measure average inference time for each model
- Target: DeepFace < 1 second, SenseVoice < 500ms, total < 2 seconds

---

## Success Criteria
When your epic is complete:
- DeepFace is integrated and returns 7-class emotion scores with a concerning flag
- SenseVoice is integrated and returns 4-class emotion scores with a concerning flag
- The score fusion function combines both results into a single verdict using configurable weights and thresholds
- All three functions are well-tested with real test data
- All configuration is externalized in `config.yaml`
- The Server API Agent (EPIC-3) can call your functions and get reliable verdicts

---

## Critical Implementation Notes

### DeepFace Edge Cases
- **No face detected**: DeepFace raises an exception if `enforce_detection=True` and no face is found. Set `enforce_detection=False` and handle the "no face" case gracefully by returning a neutral result.
- **Multiple faces**: DeepFace returns results for all detected faces. Take the first face or the largest face (by bounding box area).
- **Low confidence**: If all emotion scores are below 0.3, the model is uncertain. Consider this a neutral result.

### SenseVoice Edge Cases
- **Silence**: SenseVoice may return no emotion tags for silence. Default to neutral.
- **Very short audio**: Audio clips shorter than 1 second may not be reliably analyzed. Pad with silence or return neutral.
- **Resampling**: If the incoming audio is not 16kHz, resample it using `librosa` or `torchaudio` before passing to SenseVoice.

### Score Fusion Logic
The escalation rule (AC4 in STORY-4.3) is critical:
```python
# Example implementation
fused_score = (facial.emotions["angry"] * facial_weight) + (speech.emotions["angry"] * speech_weight)

# Base verdict from thresholds
if fused_score < green_threshold:
    verdict = Verdict.GREEN
elif fused_score < red_threshold:
    verdict = Verdict.YELLOW
else:
    verdict = Verdict.RED

# Escalation rule: if either modality is concerning, escalate by one level
if facial.is_concerning or speech.is_concerning:
    if verdict == Verdict.GREEN:
        verdict = Verdict.YELLOW
    elif verdict == Verdict.YELLOW:
        verdict = Verdict.RED
    # Already RED, no further escalation

return verdict
```

---

## Reference Documents
- **PRD**: `EQ_Meeting_Coach_PRD.docx` — Section 6.1 (Recommended Models), Section 7.4-7.6 (FR-04, FR-05, FR-06)
- **Backlog**: `EQ_Meeting_Coach_Stories.docx` — EPIC-4 section, Appendix A (config.yaml reference)

Good luck! You are the ML brain of the system. Make the models reliable, fast, and configurable. The user is counting on you to give them accurate feedback.
