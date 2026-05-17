"""Chat schemas for conversation endpoints."""

from pydantic import BaseModel


class ChatStreamRequest(BaseModel):
    """Request model for streaming chat — matches Android's ChatStreamRequest."""
    text: str
    conversationId: str | None = None
    characterId: str | None = None
