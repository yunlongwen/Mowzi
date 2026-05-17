"""Chat schemas for STT and conversation endpoints."""

from pydantic import BaseModel


class STTRequest(BaseModel):
    """Request model for speech-to-text."""
    format: str = "opus"  # "opus" or "pcm"


class STTResponse(BaseModel):
    """Response model for speech-to-text."""
    text: str  # Recognized text
    confidence: float  # Average confidence score (0-1)