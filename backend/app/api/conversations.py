"""Conversation CRUD API endpoints."""

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session
from sqlalchemy import desc

from app.database import get_db
from app.services.auth import verify_device_token
from app.services.conversation import ConversationService
from app.schemas.conversation import (
    ConversationCreate,
    ConversationResponse,
    ActiveConversationResponse,
    ConversationListResponse,
)
from app.models.conversation import Conversation

router = APIRouter(prefix="/conversations")
conversation_service = ConversationService()


@router.post("", response_model=ConversationResponse)
async def create_conversation(
    request: ConversationCreate,
    child_id: int = Depends(verify_device_token),
    db: Session = Depends(get_db)
):
    """Create a new conversation.

    Args:
        request: Conversation creation request
        child_id: Verified child ID from device token
        db: Database session

    Returns:
        Created conversation
    """
    conversation = await conversation_service.create(
        db=db,
        child_id=child_id,
        character_id=request.character_id
    )
    return conversation


@router.get("/active", response_model=ActiveConversationResponse | None)
async def get_active_conversation(
    child_id: int = Depends(verify_device_token),
    db: Session = Depends(get_db)
):
    """Get the active conversation for the current child.

    Args:
        child_id: Verified child ID from device token
        db: Database session

    Returns:
        Active conversation or None
    """
    conversation = await conversation_service.get_active(db=db, child_id=child_id)
    return conversation


@router.get("", response_model=ConversationListResponse)
async def list_conversations(
    status: str | None = Query(None, description="Filter by status: active, idle, archived"),
    page: int = Query(1, ge=1),
    page_size: int = Query(20, ge=1, le=100),
    child_id: int = Depends(verify_device_token),
    db: Session = Depends(get_db)
):
    """List conversations with pagination and optional status filter.

    Args:
        status: Optional status filter
        page: Page number (1-indexed)
        page_size: Items per page
        child_id: Verified child ID from device token
        db: Database session

    Returns:
        Paginated conversation list
    """
    query = db.query(Conversation).filter(
        Conversation.child_id == child_id
    )

    if status:
        query = query.filter(
            Conversation.status == status
        )

    total = query.count()
    total_pages = (total + page_size - 1) // page_size

    conversations = query.order_by(
        desc(Conversation.last_message_at)
    ).offset((page - 1) * page_size).limit(page_size).all()

    return ConversationListResponse(
        items=conversations,
        total=total,
        page=page,
        page_size=page_size,
        total_pages=total_pages
    )


@router.put("/{conversation_id}/resume", response_model=ConversationResponse)
async def resume_conversation(
    conversation_id: int,
    child_id: int = Depends(verify_device_token),
    db: Session = Depends(get_db)
):
    """Resume an idle or archived conversation.

    Args:
        conversation_id: Conversation ID to resume
        child_id: Verified child ID from device token
        db: Database session

    Returns:
        Resumed conversation

    Raises:
        404: Conversation not found or not owned by child
    """
    conversation = await conversation_service.resume(
        db=db,
        conversation_id=conversation_id,
        child_id=child_id
    )
    if not conversation:
        raise HTTPException(status_code=404, detail="Conversation not found")
    return conversation