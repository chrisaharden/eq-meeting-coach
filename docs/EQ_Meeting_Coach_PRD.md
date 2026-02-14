PRODUCT REQUIREMENTS DOCUMENT

EQ Meeting Coach  |  Confidential







EQ MEETING COACH

Real-Time Emotional Intelligence Feedback During Meetings

Product Requirements Document

Version 1.0

January 31, 2026






1. Document Information

Document Title

EQ Meeting Coach — Product Requirements Document

Version

1.0

Date

January 31, 2026

Status

Draft

Author

Product Team

Classification

Internal — Confidential



2. Executive Summary

EQ Meeting Coach is a real-time feedback application designed to help professionals monitor and regulate their emotional presentation during video meetings. The application continuously watches the user via camera and listens via microphone, using on-premise machine learning models to detect signs of anger, aggression, or low emotional intelligence in both facial expressions and speech.

When the system detects that the user is projecting negative emotions, it delivers an immediate, unobtrusive visual alert — a simple green, yellow, or red color indicator — allowing the user to self-correct in the moment without disrupting the meeting.

The application is architected as a two-tier system: a lightweight Android mobile app that captures audio and video and displays alerts, paired with a Python back-end inference server that runs locally on the user's own hardware. All processing occurs on-premise; no audio or video data is ever transmitted to a third-party cloud provider.



3. Objectives & Success Criteria

3.1  Primary Objectives

Provide real-time feedback on the user's emotional presentation during meetings, with a visual indicator updating every 3–5 seconds.

Detect anger and aggression signals from both facial expressions and speech audio using open-source, locally-hosted ML models.

Keep the user's privacy and corporate data policies intact by processing all data on-premise with zero data leaving the local network.

Deliver a minimal, distraction-free user interface — a single full-screen color indicator — that the user can glance at without losing focus.

Provide a clear path to migrate the inference server to cloud hosting (e.g., AWS EC2) in the future without requiring changes to the mobile app.



3.2  Success Criteria

The app correctly identifies an angry or aggressive facial expression at least 80% of the time under normal indoor lighting conditions.

The app correctly identifies an angry or aggressive speech pattern at least 75% of the time based on tone and content.

The visual indicator updates within 5 seconds of a detectable emotional shift.

The system runs continuously for a 2-hour meeting session without crashes or requiring manual intervention.

Total end-to-end latency from event occurrence to indicator update is under 8 seconds.



4. Users & Stakeholders

4.1  Primary User

The Professional in a Meeting

A working professional who participates in video meetings (Zoom, Teams, Google Meet, etc.) and wants real-time, private feedback to help them present a more emotionally intelligent and composed demeanor. They are self-aware enough to know this is an area for growth, and they want a subtle, non-intrusive nudge — not a detailed report.



5. Platform & Architecture

5.1  Platform Decision

Decision: Android Mobile App + Local Python Back-End Server

The Android phone was selected over a Windows desktop application for the following reasons:

Zoom and similar platforms aggressively lock the Windows camera and microphone during a call. An Android device avoids this conflict entirely by using its own independent hardware.

A phone propped next to the monitor provides a natural, always-visible full-screen display ideal for a single color indicator.

Running ML inference on a phone is possible but drains battery and adds latency. Offloading inference to a local server is the cleaner, more performant architecture.



5.2  System Architecture Overview

The system is composed of three logical layers:

Android App (Client) — Captures camera frames and audio chunks. Sends them to the inference server. Displays the green/yellow/red indicator based on the server response.

Inference Server (Back-End) — A Python application running in Docker on the user's local machine (or, in the future, on an AWS EC2 instance). Receives media from the app, runs the facial emotion model and the speech emotion model, fuses the results, and returns a color verdict.

Communication Layer — HTTPS with TLS encryption over the local network. The API contract between app and server is simple: POST image + audio, receive color verdict. This makes the server location fully swappable (local → cloud) without changing the app.



5.3  Data Flow

Step

Component

Action

1

Android App

Captures a camera frame and a 3-second audio clip every 3–5 seconds.

2

Android App

POSTs the frame and audio to the inference server over HTTPS.

3

Inference Server

Runs DeepFace on the image frame to get facial emotion scores (angry, disgust, neutral, etc.).

4

Inference Server

Runs SenseVoice on the audio clip to get speech emotion scores (angry, happy, neutral, sad).

5

Inference Server

Fuses the two sets of scores using a weighted combination and applies thresholds to produce a single verdict: GREEN, YELLOW, or RED.

6

Inference Server

Returns the color verdict as a JSON response.

7

Android App

Updates the full-screen display to the returned color.




6. Technology Stack & Model Selection

6.1  Recommended Models

Both models below are open source, can run entirely on-premise, and are well-suited to the inference workloads described in this document.



Facial Emotion Detection — DeepFace

Property

Value

Notes

Library

DeepFace (Python)

Wraps multiple SOTA face analysis models in a single lightweight package.

Emotion Labels

angry, disgust, fear, happy, sad, surprise, neutral

7-class output with confidence scores. We primarily gate on 'angry' and 'disgust'.

Inference Speed

Real-time capable on GPU

Processes a single frame in well under 1 second on an RTX 4090.

Input

Single image frame

Standard webcam resolution (720p or 1080p). Grayscale internally.

License

MIT / model-dependent

Core library is MIT. Underlying models have their own licenses; all permit local inference.

GPU Requirement

Optional (CPU fallback)

Significantly faster on GPU, but will run on CPU if needed.



Speech Emotion Detection — SenseVoice (FunAudioLLM)

Property

Value

Notes

Library

SenseVoice-Small (Python)

Open-source model from Alibaba's FunAudioLLM project. Designed for speech understanding.

Emotion Labels

angry, happy, sad, neutral

4-class emotion output. Detects anger directly, which is our primary target.

Inference Speed

~70ms for 10 seconds of audio

Non-autoregressive architecture. 15x faster than Whisper-Large.

Input

Raw audio (WAV/PCM)

Short clips of 3–5 seconds are sufficient for emotion inference.

Bonus Capability

Speech recognition + audio event detection

Can also transcribe speech and detect events like laughter — useful for future enhancements.

License

Apache 2.0

Permissive license. No restrictions on local or commercial use.



6.2  Full Technology Stack

Mobile App

Kotlin (Android), Jetpack Compose for UI, Android Camera2 API, MediaRecorder for audio

Back-End Language

Python 3.10+

Web Framework

FastAPI (async, lightweight, ideal for real-time endpoints)

Vision Model

DeepFace (via deepface Python package)

Audio Model

SenseVoice-Small (via FunAudioLLM / FunASR)

Containerization

Docker (single container wrapping the inference server)

GPU Acceleration

NVIDIA CUDA via PyTorch (RTX 4090 locally; T4/A10G on EC2 in future)

Communication

HTTPS + TLS (REST API, JSON payloads, base64-encoded media)

Future Cloud Path

AWS EC2 G4dn or G5 instances, AWS VPN or HTTPS for secure transit



7. Functional Requirements

7.1  FR-01 — Camera Capture

The app shall continuously capture frames from the device's front-facing camera while a monitoring session is active.

The app shall capture at least one frame every 3–5 seconds for analysis.

The app shall request camera permission on first launch and handle denial gracefully with a clear explanation.



7.2  FR-02 — Audio Capture

The app shall continuously capture audio from the device's microphone while a monitoring session is active.

The app shall buffer audio in 3–5 second chunks and send each chunk to the server for analysis.

The app shall request microphone permission on first launch and handle denial gracefully.



7.3  FR-03 — Server Communication

The app shall transmit captured frames and audio to the inference server over HTTPS.

The server URL shall be configurable (to support local and future cloud deployment).

The app shall handle network errors and display a neutral/gray indicator if the server is unreachable.

Communication shall use TLS encryption. Certificate validation shall be enforced.



7.4  FR-04 — Facial Emotion Analysis (Server)

The server shall accept an image frame and run facial emotion detection using DeepFace.

The server shall extract confidence scores for all 7 emotion categories.

The server shall flag the result as 'visually concerning' if the combined confidence for 'angry' and 'disgust' exceeds a configurable threshold (default: 40%).



7.5  FR-05 — Speech Emotion Analysis (Server)

The server shall accept an audio clip and run speech emotion detection using SenseVoice.

The server shall extract emotion scores from the audio, focusing on the 'angry' category.

The server shall flag the result as 'vocally concerning' if the anger confidence exceeds a configurable threshold (default: 45%).



7.6  FR-06 — Score Fusion & Verdict

The server shall combine facial and speech emotion scores using a weighted fusion algorithm.

Default weights: facial emotion 60%, speech emotion 40%. Weights shall be configurable in server config.

The server shall map the fused score to one of three verdicts using configurable thresholds:

GREEN  —  No concerning signals detected. Presentation appears calm and professional.

YELLOW  —  Mild warning. One signal (facial or vocal) is slightly elevated. User may want to check their tone or expression.

RED  —  Strong warning. One or both signals are significantly elevated. User should actively regulate their emotional presentation.



7.7  FR-07 — Visual Indicator Display

The app shall display a single, full-screen colored rectangle reflecting the current verdict.

The indicator shall update smoothly each time a new verdict is received (no flickering or harsh transitions).

The app shall display a neutral gray indicator on startup and while waiting for the first verdict.

The screen shall remain on (prevent sleep) while a monitoring session is active.



7.8  FR-08 — Session Management

The user shall be able to start and stop a monitoring session with a single tap.

While a session is active, the indicator shall be displayed full-screen. A small, unobtrusive stop button shall remain accessible.

The app shall gracefully shut down active camera and microphone capture when a session is stopped.




8. Non-Functional Requirements

8.1  Performance

End-to-end latency (capture → analysis → display update) shall be under 8 seconds under normal conditions.

The inference server shall be capable of processing requests continuously without memory leaks or degradation over a 2-hour session.

The Android app shall consume no more than 30% CPU during active capture to preserve battery life.



8.2  Privacy & Security

No audio or video data shall be transmitted outside the local network in the initial deployment.

All communication between the app and server shall be encrypted via TLS.

The server shall not log or persist any captured audio or video frames beyond the current inference cycle.

The architecture shall support a future migration to AWS with VPN or HTTPS-only transit, without app changes.



8.3  Reliability

The app shall recover automatically from transient network errors without requiring a restart.

The app shall not crash if the inference server returns an unexpected response format.

The Docker container shall restart automatically on failure (restart policy: always).



8.4  Maintainability & Deployability

The inference server shall be fully containerized in a single Docker image requiring no manual setup beyond Docker installation.

All ML model thresholds and fusion weights shall be externalized in a configuration file — no code changes required to tune sensitivity.

The server shall expose a health-check endpoint for monitoring.



9. Future Considerations (Out of Scope for v1.0)

The following enhancements are explicitly out of scope for the initial release but have been considered in the architecture decisions to ensure they remain feasible:

Volume meter overlay on the indicator screen (mentioned as a near-term addition).

Migration of the inference server to AWS EC2 (G4dn or G5 instance). The app's API-based architecture supports this with zero app code changes. Security for cloud deployment would be addressed via AWS VPN or enforced HTTPS with certificate pinning.

Support for additional emotion categories beyond anger (e.g., sarcasm detection, interruption frequency).

iOS version of the mobile app.

A settings UI on the mobile app (currently, all tuning is done via server config files).

Session history or post-meeting summary reports.



10. Assumptions & Risks

10.1  Assumptions

The user will have an Android phone available to prop next to their computer during meetings.

The user's local machine (gaming laptop with RTX 4090) will be powered on and accessible during meetings to run the inference server.

The local network between the phone and laptop provides reliable, low-latency connectivity (Wi-Fi or hotspot).

The user's corporate network policies do not block local device-to-device communication over the LAN.

Ambient lighting during meetings is sufficient for facial expression detection (standard indoor office lighting).



10.2  Risks

Risk

Likelihood

Mitigation

Facial emotion models perform poorly in low-light or unusual camera angles.

Medium

Test under various lighting conditions during development. DeepFace supports multiple detection backends — fall back to a more robust detector if needed.

Speech emotion detection is inaccurate for the user's specific voice or accent.

Medium

SenseVoice is trained on diverse multilingual data. Thresholds are tunable via config. Consider emotion2vec as an alternative model if accuracy is insufficient.

Android battery drains too quickly during a 2-hour session.

Low

Camera and audio capture are the main consumers. Optimize capture intervals. User can also plug in the phone.

Zoom or Teams blocks the Android app from accessing the mic/camera.

Very Low

Android apps have independent hardware access. This is not a desktop-level resource lock scenario.

Latency exceeds 8 seconds under heavy GPU load.

Low

The RTX 4090 has significant headroom for these model sizes. Monitor with health-check endpoint. Optimize batch size if needed.






Appendix A — Glossary

DeepFace

An open-source Python library for facial recognition and attribute analysis, including emotion detection. Developed by Sefik Ilkin Serengil.

SenseVoice

An open-source multilingual speech understanding model from Alibaba's FunAudioLLM project. Supports speech recognition, emotion recognition, and audio event detection.

FER

Facial Expression Recognition — the task of automatically classifying emotions from facial images.

SER

Speech Emotion Recognition — the task of automatically classifying emotions from audio speech signals.

Score Fusion

The technique of combining scores from multiple independent models (e.g., vision + audio) into a single unified prediction.

TLS

Transport Layer Security — the encryption protocol used to secure data in transit over HTTPS.

FastAPI

A modern, high-performance Python web framework for building APIs, built on top of Starlette and Pydantic.

CUDA

NVIDIA's parallel computing platform that allows software to leverage GPU hardware for accelerated processing.

Docker

A platform for developing, shipping, and running applications in isolated containers.

EC2

Amazon Elastic Compute Cloud — AWS's on-demand virtual machine service.



Appendix B — Color Indicator Legend





GREEN — Everything looks good. Your presentation is calm and professional. No action needed.





YELLOW — Mild warning. Something is slightly elevated — your tone or your expression. Take a breath and check in with yourself.





RED — Strong warning. You are coming across as agitated or aggressive. Pause, breathe, and consciously reset your tone and body language.

Page   |  EQ Meeting Coach PRD  |  v1.0  |  January 2026