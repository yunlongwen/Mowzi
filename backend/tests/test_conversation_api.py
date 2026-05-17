"""Tests for conversation CRUD API endpoints."""

import pytest
import asyncio
from datetime import datetime, timedelta

from app.models.conversation import Conversation
from app.models.character import AICharacter


def run_sync(coro):
    """Run an async coroutine in sync context."""
    return asyncio.get_event_loop().run_until_complete(coro)


class TestConversationCreateAPI:
    """Tests for POST /api/v1/conversations."""

    @pytest.fixture
    def auth_headers(self):
        return {"Authorization": "Bearer dev_test_token"}

    @pytest.fixture
    def seed_character(self, db_session):
        """Seed an AICharacter so FK constraint passes."""
        char = AICharacter(
            id=1, name="小智", system_prompt="你是小智", tts_voice_name="xiaoyan"
        )
        db_session.add(char)
        db_session.commit()
        return char

    def test_create_conversation_success(self, client, db_session, auth_headers, seed_character):
        """Test creating a conversation returns 200 with correct fields."""
        response = client.post(
            "/api/v1/conversations",
            json={"character_id": 1},
            headers=auth_headers,
        )
        assert response.status_code == 200
        data = response.json()
        assert data["character_id"] == 1
        assert data["child_id"] == 1
        assert data["status"] == "active"
        assert "id" in data
        assert "created_at" in data

    def test_create_conversation_without_auth_returns_422(self, client, db_session, seed_character):
        """Test create conversation without auth returns 422."""
        response = client.post(
            "/api/v1/conversations",
            json={"character_id": 1},
        )
        assert response.status_code == 422

    def test_create_conversation_missing_body_returns_422(self, client, db_session, auth_headers, seed_character):
        """Test create conversation without request body returns 422."""
        response = client.post(
            "/api/v1/conversations",
            headers=auth_headers,
        )
        assert response.status_code == 422


class TestConversationActiveAPI:
    """Tests for GET /api/v1/conversations/active."""

    @pytest.fixture
    def auth_headers(self):
        return {"Authorization": "Bearer dev_test_token"}

    @pytest.fixture
    def seed_character(self, db_session):
        char = AICharacter(
            id=1, name="小智", system_prompt="你是小智", tts_voice_name="xiaoyan"
        )
        db_session.add(char)
        db_session.commit()
        return char

    def test_get_active_returns_none_when_no_conversations(self, client, db_session, auth_headers, seed_character):
        """Test get active returns null when no conversations exist."""
        response = client.get(
            "/api/v1/conversations/active",
            headers=auth_headers,
        )
        assert response.status_code == 200
        assert response.json() is None

    def test_get_active_returns_active_conversation(self, client, db_session, auth_headers, seed_character):
        """Test get active returns the active conversation."""
        # Create a conversation via API
        client.post(
            "/api/v1/conversations",
            json={"character_id": 1},
            headers=auth_headers,
        )

        response = client.get(
            "/api/v1/conversations/active",
            headers=auth_headers,
        )
        assert response.status_code == 200
        data = response.json()
        assert data is not None
        assert data["status"] == "active"
        assert data["character_id"] == 1

    def test_get_active_returns_none_when_only_idle_exists(self, client, db_session, auth_headers, seed_character):
        """Test get active returns null when conversation has gone idle."""
        # Create an idle conversation directly
        conv = Conversation(
            child_id=1,
            character_id=1,
            status="idle",
            last_message_at=datetime.utcnow() - timedelta(minutes=31),
        )
        db_session.add(conv)
        db_session.commit()

        response = client.get(
            "/api/v1/conversations/active",
            headers=auth_headers,
        )
        assert response.status_code == 200
        assert response.json() is None


class TestConversationListAPI:
    """Tests for GET /api/v1/conversations."""

    @pytest.fixture
    def auth_headers(self):
        return {"Authorization": "Bearer dev_test_token"}

    @pytest.fixture
    def seed_character(self, db_session):
        char = AICharacter(
            id=1, name="小智", system_prompt="你是小智", tts_voice_name="xiaoyan"
        )
        db_session.add(char)
        db_session.commit()
        return char

    def test_list_conversations_default_pagination(self, client, db_session, auth_headers, seed_character):
        """Test listing conversations with default pagination."""
        # Create one conversation
        client.post(
            "/api/v1/conversations",
            json={"character_id": 1},
            headers=auth_headers,
        )

        response = client.get(
            "/api/v1/conversations",
            headers=auth_headers,
        )
        assert response.status_code == 200
        data = response.json()
        assert "items" in data
        assert "total" in data
        assert "page" in data
        assert "page_size" in data
        assert "total_pages" in data
        assert data["total"] >= 1
        assert data["page"] == 1

    def test_list_conversations_with_status_filter(self, client, db_session, auth_headers, seed_character):
        """Test listing conversations filtered by status."""
        # Create an active conversation
        client.post(
            "/api/v1/conversations",
            json={"character_id": 1},
            headers=auth_headers,
        )

        response = client.get(
            "/api/v1/conversations?status=active",
            headers=auth_headers,
        )
        assert response.status_code == 200
        data = response.json()
        for item in data["items"]:
            assert item["status"] == "active"

    def test_list_conversations_empty_result(self, client, db_session, auth_headers, seed_character):
        """Test listing conversations when none exist."""
        response = client.get(
            "/api/v1/conversations",
            headers=auth_headers,
        )
        assert response.status_code == 200
        data = response.json()
        assert data["items"] == []
        assert data["total"] == 0

    def test_list_conversations_pagination_page_2_empty(self, client, db_session, auth_headers, seed_character):
        """Test page 2 is empty when only 1 conversation exists."""
        client.post(
            "/api/v1/conversations",
            json={"character_id": 1},
            headers=auth_headers,
        )

        response = client.get(
            "/api/v1/conversations?page=2&page_size=10",
            headers=auth_headers,
        )
        assert response.status_code == 200
        data = response.json()
        assert data["items"] == []
        assert data["page"] == 2


class TestConversationResumeAPI:
    """Tests for PUT /api/v1/conversations/{id}/resume."""

    @pytest.fixture
    def auth_headers(self):
        return {"Authorization": "Bearer dev_test_token"}

    @pytest.fixture
    def seed_character(self, db_session):
        char = AICharacter(
            id=1, name="小智", system_prompt="你是小智", tts_voice_name="xiaoyan"
        )
        db_session.add(char)
        db_session.commit()
        return char

    def test_resume_idle_conversation(self, client, db_session, auth_headers, seed_character):
        """Test resuming an idle conversation sets it to active."""
        conv = Conversation(
            child_id=1,
            character_id=1,
            status="idle",
            last_message_at=datetime.utcnow(),
        )
        db_session.add(conv)
        db_session.commit()
        conv_id = conv.id

        response = client.put(
            f"/api/v1/conversations/{conv_id}/resume",
            headers=auth_headers,
        )
        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "active"
        assert data["id"] == conv_id

    def test_resume_archived_conversation(self, client, db_session, auth_headers, seed_character):
        """Test resuming an archived conversation sets it to active."""
        conv = Conversation(
            child_id=1,
            character_id=1,
            status="archived",
            last_message_at=datetime.utcnow() - timedelta(hours=25),
        )
        db_session.add(conv)
        db_session.commit()
        conv_id = conv.id

        response = client.put(
            f"/api/v1/conversations/{conv_id}/resume",
            headers=auth_headers,
        )
        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "active"

    def test_resume_active_conversation_returns_404(self, client, db_session, auth_headers, seed_character):
        """Test resuming an already active conversation returns 404 (service returns None)."""
        conv = Conversation(
            child_id=1,
            character_id=1,
            status="active",
            last_message_at=datetime.utcnow(),
        )
        db_session.add(conv)
        db_session.commit()

        response = client.put(
            f"/api/v1/conversations/{conv.id}/resume",
            headers=auth_headers,
        )
        assert response.status_code == 404

    def test_resume_nonexistent_conversation_returns_404(self, client, db_session, auth_headers, seed_character):
        """Test resuming a non-existent conversation returns 404."""
        response = client.put(
            "/api/v1/conversations/99999/resume",
            headers=auth_headers,
        )
        assert response.status_code == 404
