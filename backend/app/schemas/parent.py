"""Request/Response schemas for parent control panel."""

from pydantic import BaseModel
from typing import Optional


class ParentSettingsResponse(BaseModel):
    daily_limit_min: int = 60
    session_limit_min: int = 30
    blocked_hours_start: Optional[str] = None
    blocked_hours_end: Optional[str] = None
    llm_api_url: Optional[str] = None
    llm_model: Optional[str] = None
    xfyun_app_id: Optional[str] = None


class ParentSettingsRequest(BaseModel):
    daily_limit_min: Optional[int] = None
    session_limit_min: Optional[int] = None
    blocked_hours_start: Optional[str] = None
    blocked_hours_end: Optional[str] = None
    llm_api_url: Optional[str] = None
    llm_api_key: Optional[str] = None
    llm_model: Optional[str] = None
    xfyun_app_id: Optional[str] = None
    xfyun_api_key: Optional[str] = None
    xfyun_api_secret: Optional[str] = None


class ParentUsageItem(BaseModel):
    date: str
    minutes: int
    message_count: int


class ParentUsageResponse(BaseModel):
    usage: list[ParentUsageItem]


class ParentConversationDto(BaseModel):
    id: str
    character_name: str
    title: str
    message_count: int
    created_at: int
    last_message_at: Optional[int] = None


class ParentConversationsResponse(BaseModel):
    conversations: list[ParentConversationDto]


class ParentMessageDto(BaseModel):
    role: str
    content: str
    timestamp: int


class ParentMessagesResponse(BaseModel):
    messages: list[ParentMessageDto]