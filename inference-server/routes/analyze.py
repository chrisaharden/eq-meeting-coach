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

    # Validate content types
    if frame.content_type not in ALLOWED_IMAGE_TYPES:
        raise HTTPException(
            status_code=422,
            detail=f"Invalid content type for frame: {frame.content_type}. Expected image/jpeg.",
        )
    if audio.content_type not in ALLOWED_AUDIO_TYPES:
        raise HTTPException(
            status_code=422,
            detail=f"Invalid content type for audio: {audio.content_type}. Expected audio/wav.",
        )

    # Read file bytes
    image_bytes = await frame.read()
    audio_bytes = await audio.read()

    if not image_bytes:
        raise HTTPException(status_code=422, detail="Frame file is empty.")
    if not audio_bytes:
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

    logger.info("Analysis complete — verdict: %s", verdict.value)

    return AnalyzeResponse(verdict=verdict)
