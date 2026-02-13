import asyncio
import logging
from functools import partial

from fastapi import APIRouter, HTTPException, UploadFile, File

from models import analyze_face, analyze_speech, compute_verdict
from models.schemas import AnalyzeResponse

logger = logging.getLogger(__name__)

router = APIRouter()

ALLOWED_IMAGE_TYPES = {"image/jpeg", "image/jpg"}
ALLOWED_AUDIO_TYPES = {"audio/wav", "audio/x-wav", "audio/wave"}


@router.post("/analyze", response_model=AnalyzeResponse)
async def analyze(
    frame: UploadFile = File(...),
    audio: UploadFile = File(...),
) -> AnalyzeResponse:
    """Accept an image frame and audio clip, return an emotion verdict."""
    logger.info("Received /analyze request — frame=%s (%s), audio=%s (%s)",
                frame.filename, frame.content_type, audio.filename, audio.content_type)

    # Validate content types
    if frame.content_type not in ALLOWED_IMAGE_TYPES:
        logger.warning("Rejected: invalid frame content_type=%s", frame.content_type)
        raise HTTPException(
            status_code=422,
            detail=f"Invalid content type for frame: {frame.content_type}. Expected image/jpeg.",
        )
    if audio.content_type not in ALLOWED_AUDIO_TYPES:
        logger.warning("Rejected: invalid audio content_type=%s", audio.content_type)
        raise HTTPException(
            status_code=422,
            detail=f"Invalid content type for audio: {audio.content_type}. Expected audio/wav.",
        )

    # Read file bytes
    image_bytes = await frame.read()
    audio_bytes = await audio.read()
    logger.info("Payload sizes — frame=%d bytes, audio=%d bytes", len(image_bytes), len(audio_bytes))

    if not image_bytes:
        logger.warning("Rejected: frame file is empty")
        raise HTTPException(status_code=422, detail="Frame file is empty.")
    if not audio_bytes:
        logger.warning("Rejected: audio file is empty")
        raise HTTPException(status_code=422, detail="Audio file is empty.")

    loop = asyncio.get_event_loop()

    # Run ML inference in threadpool to avoid blocking the async event loop
    try:
        facial_result = await loop.run_in_executor(
            None, partial(analyze_face, image_bytes)
        )
    except Exception:
        logger.exception("Facial emotion analysis failed")
        raise HTTPException(status_code=500, detail="Facial emotion analysis failed")

    try:
        speech_result = await loop.run_in_executor(
            None, partial(analyze_speech, audio_bytes)
        )
    except Exception:
        logger.exception("Speech emotion analysis failed")
        raise HTTPException(status_code=500, detail="Speech emotion analysis failed")

    try:
        verdict = await loop.run_in_executor(
            None, partial(compute_verdict, facial_result, speech_result)
        )
    except Exception:
        logger.exception("Score fusion failed")
        raise HTTPException(status_code=500, detail="Score fusion failed")

    fused_score = (
        facial_result.emotions.get("angry", 0.0) * 0.60
        + speech_result.emotions.get("angry", 0.0) * 0.40
    )

    logger.info("Analysis complete — verdict: %s | facial=%s dominant=%s | speech=%s dominant=%s | fused=%.3f",
                verdict.value,
                {k: round(v, 3) for k, v in facial_result.emotions.items()},
                facial_result.dominant,
                {k: round(v, 3) for k, v in speech_result.emotions.items()},
                speech_result.dominant,
                fused_score)

    debug = {
        "facial_emotions": {k: round(v, 3) for k, v in facial_result.emotions.items()},
        "facial_dominant": facial_result.dominant,
        "speech_emotions": {k: round(v, 3) for k, v in speech_result.emotions.items()},
        "speech_dominant": speech_result.dominant,
        "fused_score": round(fused_score, 3),
    }

    return AnalyzeResponse(verdict=verdict, debug=debug)
