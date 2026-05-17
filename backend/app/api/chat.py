"""Chat API endpoints for STT and conversation handling."""

from fastapi import APIRouter, Depends, HTTPException, UploadFile, File, Form
from sqlalchemy.orm import Session

from app.database import get_db
from app.services.auth import verify_device_token
from app.services.xfyun_stt import XfyunSTTService
from app.schemas.common import ErrorResponse, ErrorDetail
from app.config import settings

router = APIRouter()

# Initialize STT service with credentials from settings
stt_service = XfyunSTTService(
    app_id=settings.xfyun_app_id,
    api_key=settings.xfyun_api_key,
    api_secret=settings.xfyun_api_secret
)


@router.post("/chat/stt")
async def speech_to_text(
    audio: UploadFile = File(...),
    format: str = Form("opus"),
    child_id: int = Depends(verify_device_token),
    db: Session = Depends(get_db)
):
    """Convert speech audio to text using IFLYTEK STT.

    Args:
        audio: Uploaded audio file (Opus or PCM)
        format: Audio format ("opus" or "pcm")
        child_id: Verified child ID from device token
        db: Database session

    Returns:
        JSON with recognized text and confidence score

    Raises:
        400: Audio too short
        413: Audio too large (>500KB)
        422: Low confidence or empty result
        502: STT service error
    """
    audio_data = await audio.read()

    # Validate audio size (60s Opus ~200KB)
    if len(audio_data) > 500_000:
        raise HTTPException(
            status_code=413,
            detail="Audio too large (max 500KB)"
        )
    if len(audio_data) < 100:
        raise HTTPException(
            status_code=400,
            detail="Audio too short (min 100 bytes)"
        )

    try:
        text, confidence = await stt_service.recognize(audio_data, format)
    except Exception as e:
        raise HTTPException(
            status_code=502,
            detail=ErrorResponse(
                error=ErrorDetail(
                    code="STT_FAILED",
                    message="语音识别失败，请再说一次"
                )
            ).model_dump()
        )

    # Check confidence threshold
    if confidence < 0.3 or not text.strip():
        raise HTTPException(
            status_code=422,
            detail=ErrorResponse(
                error=ErrorDetail(
                    code="STT_LOW_CONFIDENCE",
                    message="没听清哦，再说一次吧？"
                )
            ).model_dump()
        )

    return {"text": text, "confidence": confidence}