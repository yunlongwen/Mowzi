"""Conversation schemas for API request/response models."""

from datetime import datetime
from typing import Optional
from pydantic import BaseModel


class ConversationCreate(BaseModel):
    """Request model for creating a conversation."""
    character_id: int


class ConversationResponse(BaseModel):
    """Response model for a conversation."""
    id: int
    child_id: int
    character_id: int
    title: Optional[str] = None
    status: str  # "active" | "idle" | "archived"
    created_at: datetime
    updated_at: datetime
    last_message_at: datetime

    class Config:
        from_attributes = True


class ActiveConversationResponse(BaseModel):
    """Response model for active conversation."""
    id: int
    child_id: int
    character_id: int
    title: Optional[str] = None
    status: str
    created_at: datetime
    updated_at: datetime
    last_message_at: datetime

    class Config:
        from_attributes = True


class ConversationListResponse(BaseModel):
    """Response model for paginated conversation list."""
    items: list[ConversationResponse]
    total: int
    page: int
    page_size: int
    total_pages: int