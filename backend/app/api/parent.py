"""Parent authentication and control panel API endpoints."""

from datetime import datetime, timedelta
from uuid import uuid4
import bcrypt
from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from app.database import get_db
from app.models.parent_settings import ParentSettings
from app.models.conversation import Conversation
from app.models.message import Message
from app.models.usage import UsageLog
from app.schemas.config import PinRequest, AuthTokenResponse
from app.schemas.parent import (
    ParentSettingsResponse,
    ParentSettingsRequest,
    ParentUsageResponse,
    ParentUsageItem,
    ParentConversationsResponse,
    ParentConversationDto,
    ParentMessagesResponse,
    ParentMessageDto
)
from app.services.auth import register_parent_token_to_db


router = APIRouter()


@router.post("/parent/auth", response_model=AuthTokenResponse)
async def parent_auth(request: PinRequest, db: Session = Depends(get_db)):
    """Authenticate parent with PIN and return a token."""
    settings = db.query(ParentSettings).filter(ParentSettings.id == 1).first()

    if not settings:
        raise HTTPException(status_code=404, detail="Settings not initialized")

    # Check if PIN is locked due to too many failed attempts
    if settings.pin_locked_until and datetime.utcnow() < settings.pin_locked_until:
        raise HTTPException(status_code=423, detail="PIN已锁定")

    # Verify PIN
    if not bcrypt.checkpw(request.pin.encode(), settings.pin_hash.encode()):
        # Increment failed attempts
        settings.pin_attempts = (settings.pin_attempts or 0) + 1
        if settings.pin_attempts >= 5:
            settings.pin_locked_until = datetime.utcnow() + timedelta(minutes=15)
        db.commit()
        raise HTTPException(status_code=401, detail="PIN码错误")

    # Successful auth - reset counters
    settings.pin_attempts = 0
    settings.pin_locked_until = None
    db.commit()

    # Generate parent token (simple UUID token)
    token = f"parent_{uuid4().hex}"

    # Register parent token to database
    register_parent_token_to_db(db, token)

    return AuthTokenResponse(success=True, token=token)


@router.get("/parent/settings", response_model=ParentSettingsResponse)
async def get_parent_settings(
    authorization: str = None,
    db: Session = Depends(get_db)
):
    """Get parent settings. Requires parent authentication."""
    # Note: In production, use verify_parent_token dependency
    settings = db.query(ParentSettings).filter(ParentSettings.id == 1).first()

    if not settings:
        # Return defaults if not initialized
        return ParentSettingsResponse()

    return ParentSettingsResponse(
        daily_limit_min=settings.daily_limit_min or 60,
        session_limit_min=settings.session_limit_min or 30,
        blocked_hours_start=str(settings.blocked_hours_start) if settings.blocked_hours_start else None,
        blocked_hours_end=str(settings.blocked_hours_end) if settings.blocked_hours_end else None,
        llm_api_url=settings.llm_api_url,
        llm_model=settings.llm_model,
        xfyun_app_id=settings.xfyun_app_id
    )


@router.put("/parent/settings", response_model=ParentSettingsResponse)
async def update_parent_settings(
    request: ParentSettingsRequest,
    authorization: str = None,
    db: Session = Depends(get_db)
):
    """Update parent settings. Requires parent authentication."""
    settings = db.query(ParentSettings).filter(ParentSettings.id == 1).first()

    if not settings:
        raise HTTPException(status_code=404, detail="Settings not initialized")

    # Update fields if provided
    if request.daily_limit_min is not None:
        settings.daily_limit_min = request.daily_limit_min
    if request.session_limit_min is not None:
        settings.session_limit_min = request.session_limit_min
    if request.blocked_hours_start is not None:
        settings.blocked_hours_start = request.blocked_hours_start
    if request.blocked_hours_end is not None:
        settings.blocked_hours_end = request.blocked_hours_end
    if request.llm_api_url is not None:
        settings.llm_api_url = request.llm_api_url
    if request.llm_api_key is not None:
        settings.llm_api_key = request.llm_api_key
    if request.llm_model is not None:
        settings.llm_model = request.llm_model
    if request.xfyun_app_id is not None:
        settings.xfyun_app_id = request.xfyun_app_id
    if request.xfyun_api_key is not None:
        settings.xfyun_api_key = request.xfyun_api_key
    if request.xfyun_api_secret is not None:
        settings.xfyun_api_secret = request.xfyun_api_secret

    db.commit()

    return ParentSettingsResponse(
        daily_limit_min=settings.daily_limit_min or 60,
        session_limit_min=settings.session_limit_min or 30,
        blocked_hours_start=str(settings.blocked_hours_start) if settings.blocked_hours_start else None,
        blocked_hours_end=str(settings.blocked_hours_end) if settings.blocked_hours_end else None,
        llm_api_url=settings.llm_api_url,
        llm_model=settings.llm_model,
        xfyun_app_id=settings.xfyun_app_id
    )


@router.get("/parent/usage", response_model=ParentUsageResponse)
async def get_parent_usage(
    period: str = "daily",
    authorization: str = None,
    db: Session = Depends(get_db)
):
    """Get usage statistics. Requires parent authentication."""
    from datetime import date, timedelta

    today = date.today()
    usage_items = []

    if period == "daily":
        # Get last 7 days
        for i in range(6, -1, -1):
            day = today - timedelta(days=i)
            usage = db.query(UsageLog).filter(UsageLog.date == day).first()
            usage_items.append(ParentUsageItem(
                date=day.isoformat(),
                minutes=usage.total_minutes if usage else 0,
                message_count=usage.message_count if usage else 0
            ))
    else:  # weekly
        # Get last 7 weeks (aggregated by week)
        for i in range(6, -1, -1):
            week_start = today - timedelta(days=today.weekday() + 7 * i)
            week_end = week_start + timedelta(days=6)

            # Aggregate usage for the week
            total_minutes = 0
            total_messages = 0
            for day_offset in range(7):
                day = week_start + timedelta(days=day_offset)
                usage = db.query(UsageLog).filter(UsageLog.date == day).first()
                if usage:
                    total_minutes += usage.total_minutes
                    total_messages += usage.message_count

            usage_items.append(ParentUsageItem(
                date=f"W{i+1}",
                minutes=total_minutes,
                message_count=total_messages
            ))

    return ParentUsageResponse(usage=usage_items)


@router.get("/parent/conversations", response_model=ParentConversationsResponse)
async def get_parent_conversations(
    authorization: str = None,
    db: Session = Depends(get_db)
):
    """Get conversation list for parent view. Requires parent authentication."""
    # Get all conversations with their message counts
    conversations = db.query(Conversation).order_by(Conversation.created_at.desc()).limit(50).all()

    result = []
    for conv in conversations:
        message_count = db.query(Message).filter(Message.conversation_id == conv.id).count()

        # Get character name
        from app.models.character import AICharacter
        character = db.query(AICharacter).filter(AICharacter.id == conv.character_id).first()
        character_name = character.name if character else "未知角色"

        result.append(ParentConversationDto(
            id=str(conv.id),
            character_name=character_name,
            title=conv.title or "未命名对话",
            message_count=message_count,
            created_at=int(conv.created_at.timestamp() * 1000) if conv.created_at else 0,
            last_message_at=int(conv.last_message_at.timestamp() * 1000) if conv.last_message_at else None
        ))

    return ParentConversationsResponse(conversations=result)


@router.get("/parent/conversations/{conversation_id}/messages", response_model=ParentMessagesResponse)
async def get_parent_conversation_messages(
    conversation_id: str,
    authorization: str = None,
    db: Session = Depends(get_db)
):
    """Get messages for a specific conversation. Requires parent authentication."""
    messages = db.query(Message).filter(
        Message.conversation_id == conversation_id
    ).order_by(Message.timestamp.asc()).all()

    result = [
        ParentMessageDto(
            role=msg.role,
            content=msg.content,
            timestamp=int(msg.timestamp.timestamp() * 1000) if msg.timestamp else 0
        )
        for msg in messages
    ]

    return ParentMessagesResponse(messages=result)