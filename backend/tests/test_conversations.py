"""Tests for conversation lifecycle API."""

import pytest
import asyncio
from datetime import datetime, timedelta


def run_sync(coro):
    """Run an async coroutine in sync context."""
    return asyncio.get_event_loop().run_until_complete(coro)


class TestConversationService:
    """Tests for ConversationService."""

    def test_create_conversation(self, db_session):
        """Test creating a new conversation."""
        from app.services.conversation import ConversationService

        service = ConversationService()
        conv = run_sync(service.create(db_session, child_id=1, character_id=1))

        assert conv is not None
        assert conv.child_id == 1
        assert conv.character_id == 1
        assert conv.status == "active"
        assert conv.title == "新对话"

    def test_get_active_conversation(self, db_session):
        """Test getting active conversation."""
        from app.services.conversation import ConversationService

        service = ConversationService()
        # Create a conversation first
        run_sync(service.create(db_session, child_id=1, character_id=1))

        active = run_sync(service.get_active(db_session, child_id=1))

        assert active is not None
        assert active.child_id == 1
        assert active.status == "active"

    def test_transition_active_to_idle(self, db_session):
        """Test that active conversation transitions to idle after timeout."""
        from app.services.conversation import ConversationService
        from app.models.conversation import Conversation

        service = ConversationService()
        # Create a conversation with old last_message_at
        conv = Conversation(
            child_id=1,
            character_id=1,
            status="active",
            last_message_at=datetime.utcnow() - timedelta(minutes=31)
        )
        db_session.add(conv)
        db_session.commit()

        run_sync(service.get_active(db_session, child_id=1))

        db_session.refresh(conv)
        assert conv.status == "idle"

    def test_transition_idle_to_archived(self, db_session):
        """Test that idle conversation transitions to archived after timeout."""
        from app.services.conversation import ConversationService
        from app.models.conversation import Conversation

        service = ConversationService()
        # Create an idle conversation with old last_message_at
        conv = Conversation(
            child_id=1,
            character_id=1,
            status="idle",
            last_message_at=datetime.utcnow() - timedelta(hours=25)
        )
        db_session.add(conv)
        db_session.commit()

        run_sync(service.get_active(db_session, child_id=1))

        db_session.refresh(conv)
        assert conv.status == "archived"

    def test_resume_conversation(self, db_session):
        """Test resuming an idle conversation."""
        from app.services.conversation import ConversationService
        from app.models.conversation import Conversation

        service = ConversationService()
        # Create an idle conversation
        conv = Conversation(
            child_id=1,
            character_id=1,
            status="idle",
            last_message_at=datetime.utcnow() - timedelta(minutes=31)
        )
        db_session.add(conv)
        db_session.commit()
        conv_id = conv.id

        resumed = run_sync(service.resume(db_session, conversation_id=conv_id, child_id=1))

        assert resumed is not None
        assert resumed.status == "active"
        assert resumed.id == conv_id

    def test_resume_archived_conversation(self, db_session):
        """Test resuming an archived conversation."""
        from app.services.conversation import ConversationService
        from app.models.conversation import Conversation

        service = ConversationService()
        # Create an archived conversation
        conv = Conversation(
            child_id=1,
            character_id=1,
            status="archived",
            last_message_at=datetime.utcnow() - timedelta(hours=25)
        )
        db_session.add(conv)
        db_session.commit()
        conv_id = conv.id

        resumed = run_sync(service.resume(db_session, conversation_id=conv_id, child_id=1))

        assert resumed is not None
        assert resumed.status == "active"
        assert resumed.id == conv_id

    def test_resume_conversation_wrong_child(self, db_session):
        """Test resuming a conversation with wrong child_id returns None."""
        from app.services.conversation import ConversationService
        from app.models.conversation import Conversation

        service = ConversationService()
        # Create an idle conversation owned by child_id=1
        conv = Conversation(
            child_id=1,
            character_id=1,
            status="idle",
            last_message_at=datetime.utcnow() - timedelta(minutes=31)
        )
        db_session.add(conv)
        db_session.commit()
        conv_id = conv.id

        # Try to resume as child_id=2
        resumed = run_sync(service.resume(db_session, conversation_id=conv_id, child_id=2))

        assert resumed is None


class TestConversationAPI:
    """Tests for conversation API endpoints."""

    def test_create_conversation_api(self, client):
        """Test POST /conversations endpoint."""
        response = client.post(
            "/api/v1/conversations",
            json={"character_id": 1},
            headers={"Authorization": "Bearer dev_test_token"}
        )
        assert response.status_code == 200
        data = response.json()
        assert data["character_id"] == 1
        assert data["status"] == "active"

    def test_get_active_conversation_api(self, client):
        """Test GET /conversations/active endpoint."""
        # Create a conversation first
        client.post(
            "/api/v1/conversations",
            json={"character_id": 1},
            headers={"Authorization": "Bearer dev_test_token"}
        )

        response = client.get(
            "/api/v1/conversations/active",
            headers={"Authorization": "Bearer dev_test_token"}
        )
        assert response.status_code == 200
        data = response.json()
        assert data is not None
        assert data["status"] == "active"

    def test_list_conversations_api(self, client):
        """Test GET /conversations endpoint with pagination."""
        # Create a conversation first
        client.post(
            "/api/v1/conversations",
            json={"character_id": 1},
            headers={"Authorization": "Bearer dev_test_token"}
        )

        response = client.get(
            "/api/v1/conversations?page=1&page_size=10",
            headers={"Authorization": "Bearer dev_test_token"}
        )
        assert response.status_code == 200
        data = response.json()
        assert "items" in data
        assert "total" in data
        assert "page" in data
        assert data["page"] == 1

    def test_list_conversations_with_status_filter(self, client):
        """Test GET /conversations with status filter."""
        # Create an active conversation
        client.post(
            "/api/v1/conversations",
            json={"character_id": 1},
            headers={"Authorization": "Bearer dev_test_token"}
        )

        response = client.get(
            "/api/v1/conversations?status=active",
            headers={"Authorization": "Bearer dev_test_token"}
        )
        assert response.status_code == 200
        data = response.json()
        for item in data["items"]:
            assert item["status"] == "active"

    def test_resume_conversation_api(self, client, db_session):
        """Test PUT /conversations/{id}/resume endpoint."""
        from app.services.conversation import ConversationService

        service = ConversationService()
        conv = run_sync(service.create(db_session, child_id=1, character_id=1))
        conv_id = conv.id

        # Manually set to idle
        conv.status = "idle"
        db_session.commit()

        response = client.put(
            f"/api/v1/conversations/{conv_id}/resume",
            headers={"Authorization": "Bearer dev_test_token"}
        )
        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "active"

    def test_resume_conversation_not_found(self, client):
        """Test resume with non-existent conversation."""
        response = client.put(
            "/api/v1/conversations/99999/resume",
            headers={"Authorization": "Bearer dev_test_token"}
        )
        assert response.status_code == 404

    def test_create_conversation_requires_auth(self, client):
        """Test create conversation without auth returns 422 (missing required header)."""
        response = client.post(
            "/api/v1/conversations",
            json={"character_id": 1}
        )
        # FastAPI returns 422 when required header is missing, not 401
        assert response.status_code == 422