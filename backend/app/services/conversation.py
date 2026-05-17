"""Conversation state machine service for lifecycle management."""

from datetime import datetime, timedelta

from sqlalchemy.orm import Session

from app.models.conversation import Conversation


class ConversationService:
    """Service for managing conversation lifecycle states.

    State transitions:
    - active: Conversation is currently in use
    - idle: No activity for ACTIVE_TIMEOUT_MINUTES (30 min)
    - archived: No activity for ARCHIVE_TIMEOUT_HOURS (24 hours)
    """

    ACTIVE_TIMEOUT_MINUTES = 30
    ARCHIVE_TIMEOUT_HOURS = 24

    async def create(self, db: Session, child_id: int, character_id: int) -> Conversation:
        """Create a new conversation.

        Args:
            db: Database session
            child_id: Child profile ID
            character_id: AI character ID

        Returns:
            Created conversation instance
        """
        conversation = Conversation(
            child_id=child_id,
            character_id=character_id,
            title="新对话",
            status="active",
            last_message_at=datetime.utcnow()
        )
        db.add(conversation)
        db.commit()
        db.refresh(conversation)
        return conversation

    async def get_active(self, db: Session, child_id: int) -> Conversation | None:
        """Get the active conversation for a child.

        Performs state transition check before returning.

        Args:
            db: Database session
            child_id: Child profile ID

        Returns:
            Active conversation or None
        """
        self._transition_states(db, child_id)
        return db.query(Conversation).filter(
            Conversation.child_id == child_id,
            Conversation.status == "active"
        ).order_by(Conversation.last_message_at.desc()).first()

    async def resume(
        self, db: Session, conversation_id: int, child_id: int
    ) -> Conversation | None:
        """Resume an idle or archived conversation.

        Args:
            db: Database session
            conversation_id: Conversation ID
            child_id: Child profile ID (for ownership verification)

        Returns:
            Resumed conversation or None if not found/not owned
        """
        conv = db.query(Conversation).filter(
            Conversation.id == conversation_id
        ).first()
        if conv and conv.child_id == child_id and conv.status in ("idle", "archived"):
            conv.status = "active"
            conv.last_message_at = datetime.utcnow()
            db.commit()
            db.refresh(conv)
            return conv
        return None

    def _transition_states(self, db: Session, child_id: int) -> None:
        """Check and transition conversation states.

        Transitions:
        - active -> idle: after ACTIVE_TIMEOUT_MINUTES of inactivity
        - idle -> archived: after ARCHIVE_TIMEOUT_HOURS of inactivity

        Args:
            db: Database session
            child_id: Child profile ID
        """
        now = datetime.utcnow()
        conversations = db.query(Conversation).filter(
            Conversation.child_id == child_id,
            Conversation.status.in_(["active", "idle"])
        ).all()

        for conv in conversations:
            idle_time = now - conv.last_message_at
            if conv.status == "active" and idle_time > timedelta(minutes=self.ACTIVE_TIMEOUT_MINUTES):
                conv.status = "idle"
            elif conv.status == "idle" and idle_time > timedelta(hours=self.ARCHIVE_TIMEOUT_HOURS):
                conv.status = "archived"

        db.commit()