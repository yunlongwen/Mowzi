"""Tests for parent control panel API endpoints."""

import pytest
import bcrypt
from datetime import datetime, date, timedelta

from app.models.parent_settings import ParentSettings
from app.models.conversation import Conversation
from app.models.message import Message
from app.models.character import AICharacter
from app.models.usage import UsageLog


@pytest.fixture
def setup_parent_settings(db_session):
    """Set up parent settings with a known PIN."""
    pin_hash = bcrypt.hashpw("123456".encode(), bcrypt.gensalt()).decode()
    settings = ParentSettings(
        id=1,
        pin_hash=pin_hash,
        pin_attempts=0,
        pin_locked_until=None,
        daily_limit_min=60,
        session_limit_min=30,
    )
    db_session.add(settings)
    db_session.commit()
    return settings


class TestParentAuthAPI:
    """Tests for POST /api/v1/parent/auth."""

    def test_parent_auth_correct_pin(self, client, db_session, setup_parent_settings):
        """Test parent auth with correct PIN returns success with token."""
        response = client.post(
            "/api/v1/parent/auth",
            json={"pin": "123456"},
        )
        assert response.status_code == 200
        data = response.json()
        assert data["success"] is True
        assert "token" in data
        assert data["token"].startswith("parent_")

    def test_parent_auth_wrong_pin(self, client, db_session, setup_parent_settings):
        """Test parent auth with wrong PIN returns 401."""
        response = client.post(
            "/api/v1/parent/auth",
            json={"pin": "654321"},
        )
        assert response.status_code == 401
        assert "PIN码错误" in response.json()["detail"]

    def test_parent_auth_locked_after_5_failures(self, client, db_session, setup_parent_settings):
        """Test account locks after 5 consecutive failed PIN attempts."""
        for _ in range(5):
            client.post("/api/v1/parent/auth", json={"pin": "wrong"})

        response = client.post("/api/v1/parent/auth", json={"pin": "123456"})
        assert response.status_code == 423
        assert "锁定" in response.json()["detail"]

    def test_parent_auth_no_settings_returns_404(self, client, db_session):
        """Test parent auth when no settings exist returns 404."""
        response = client.post(
            "/api/v1/parent/auth",
            json={"pin": "123456"},
        )
        assert response.status_code == 404


class TestParentSettingsAPI:
    """Tests for GET/PUT /api/v1/parent/settings."""

    def test_get_settings_returns_defaults_when_not_initialized(self, client, db_session):
        """Test get settings returns defaults when no settings row exists."""
        response = client.get("/api/v1/parent/settings")
        assert response.status_code == 200
        data = response.json()
        assert data["daily_limit_min"] == 60
        assert data["session_limit_min"] == 30

    def test_get_settings_returns_saved_values(self, client, db_session, setup_parent_settings):
        """Test get settings returns the saved configuration."""
        response = client.get("/api/v1/parent/settings")
        assert response.status_code == 200
        data = response.json()
        assert data["daily_limit_min"] == 60
        assert data["session_limit_min"] == 30

    def test_update_settings_success(self, client, db_session, setup_parent_settings):
        """Test updating parent settings succeeds."""
        response = client.put(
            "/api/v1/parent/settings",
            json={
                "daily_limit_min": 45,
                "session_limit_min": 20,
                "llm_model": "gpt-4o-mini",
            },
        )
        assert response.status_code == 200
        data = response.json()
        assert data["daily_limit_min"] == 45
        assert data["session_limit_min"] == 20
        assert data["llm_model"] == "gpt-4o-mini"

    def test_update_settings_persists_to_db(self, client, db_session, setup_parent_settings):
        """Test that settings update persists in the database."""
        client.put(
            "/api/v1/parent/settings",
            json={"daily_limit_min": 90, "session_limit_min": 15},
        )

        # Re-fetch to verify persistence
        response = client.get("/api/v1/parent/settings")
        data = response.json()
        assert data["daily_limit_min"] == 90
        assert data["session_limit_min"] == 15

    def test_update_settings_partial_update(self, client, db_session, setup_parent_settings):
        """Test that partial update only changes specified fields."""
        client.put(
            "/api/v1/parent/settings",
            json={"daily_limit_min": 120},
        )

        response = client.get("/api/v1/parent/settings")
        data = response.json()
        assert data["daily_limit_min"] == 120
        # session_limit_min should remain unchanged
        assert data["session_limit_min"] == 30

    def test_update_settings_not_initialized_returns_404(self, client, db_session):
        """Test updating settings when not initialized returns 404."""
        response = client.put(
            "/api/v1/parent/settings",
            json={"daily_limit_min": 45},
        )
        assert response.status_code == 404


class TestParentUsageAPI:
    """Tests for GET /api/v1/parent/usage."""

    def test_get_usage_daily_default(self, client, db_session):
        """Test getting daily usage returns 7 days of data."""
        response = client.get("/api/v1/parent/usage")
        assert response.status_code == 200
        data = response.json()
        assert "usage" in data
        assert len(data["usage"]) == 7

    def test_get_usage_daily_structure(self, client, db_session):
        """Test daily usage items have correct fields."""
        response = client.get("/api/v1/parent/usage?period=daily")
        assert response.status_code == 200
        data = response.json()
        for item in data["usage"]:
            assert "date" in item
            assert "minutes" in item
            assert "message_count" in item

    def test_get_usage_daily_with_data(self, client, db_session):
        """Test daily usage returns actual data when usage logs exist."""
        today = date.today()
        log = UsageLog(
            child_id=1,
            date=today,
            total_minutes=30,
            message_count=15,
        )
        db_session.add(log)
        db_session.commit()

        response = client.get("/api/v1/parent/usage?period=daily")
        assert response.status_code == 200
        data = response.json()
        today_entry = [u for u in data["usage"] if u["date"] == today.isoformat()]
        assert len(today_entry) == 1
        assert today_entry[0]["minutes"] == 30
        assert today_entry[0]["message_count"] == 15

    def test_get_usage_weekly(self, client, db_session):
        """Test getting weekly usage returns 7 weeks of data."""
        response = client.get("/api/v1/parent/usage?period=weekly")
        assert response.status_code == 200
        data = response.json()
        assert len(data["usage"]) == 7

    def test_get_usage_daily_empty_returns_zero(self, client, db_session):
        """Test that days without logs show zero minutes and messages."""
        response = client.get("/api/v1/parent/usage?period=daily")
        data = response.json()
        for item in data["usage"]:
            assert item["minutes"] == 0
            assert item["message_count"] == 0


class TestParentConversationsAPI:
    """Tests for GET /api/v1/parent/conversations."""

    def test_get_conversations_empty(self, client, db_session):
        """Test getting conversations when none exist returns empty list."""
        response = client.get("/api/v1/parent/conversations")
        assert response.status_code == 200
        data = response.json()
        assert "conversations" in data
        assert data["conversations"] == []

    def test_get_conversations_with_data(self, client, db_session):
        """Test getting conversations returns conversation list."""
        char = AICharacter(
            id=1, name="小智", system_prompt="你是小智", tts_voice_name="xiaoyan"
        )
        db_session.add(char)

        conv = Conversation(
            child_id=1,
            character_id=1,
            title="测试对话",
            status="active",
            last_message_at=datetime.utcnow(),
        )
        db_session.add(conv)
        db_session.commit()

        response = client.get("/api/v1/parent/conversations")
        assert response.status_code == 200
        data = response.json()
        assert len(data["conversations"]) == 1
        c = data["conversations"][0]
        assert c["title"] == "测试对话"
        assert c["character_name"] == "小智"
        assert "message_count" in c
        assert "created_at" in c

    def test_get_conversations_data_structure(self, client, db_session):
        """Test conversation items have the expected fields."""
        char = AICharacter(
            id=1, name="小智", system_prompt="你是小智", tts_voice_name="xiaoyan"
        )
        db_session.add(char)

        conv = Conversation(
            child_id=1,
            character_id=1,
            title="结构测试",
            status="active",
            last_message_at=datetime.utcnow(),
        )
        db_session.add(conv)
        db_session.commit()

        response = client.get("/api/v1/parent/conversations")
        c = response.json()["conversations"][0]
        assert "id" in c
        assert "character_name" in c
        assert "title" in c
        assert "message_count" in c
        assert "created_at" in c
        assert "last_message_at" in c

    def test_get_conversations_unknown_character(self, client, db_session):
        """Test conversation with deleted/unknown character shows default name."""
        conv = Conversation(
            child_id=1,
            character_id=999,  # Non-existent character
            title="未知角色对话",
            status="active",
            last_message_at=datetime.utcnow(),
        )
        db_session.add(conv)
        db_session.commit()

        response = client.get("/api/v1/parent/conversations")
        data = response.json()
        assert len(data["conversations"]) == 1
        assert data["conversations"][0]["character_name"] == "未知角色"


class TestParentMessagesAPI:
    """Tests for GET /api/v1/parent/conversations/{id}/messages."""

    def test_get_messages_empty(self, client, db_session):
        """Test getting messages for a conversation with no messages."""
        response = client.get("/api/v1/parent/conversations/1/messages")
        assert response.status_code == 200
        data = response.json()
        assert "messages" in data
        assert data["messages"] == []

    def test_get_messages_with_data(self, client, db_session):
        """Test getting messages returns correct message list."""
        char = AICharacter(
            id=1, name="小智", system_prompt="你是小智", tts_voice_name="xiaoyan"
        )
        db_session.add(char)

        conv = Conversation(
            child_id=1,
            character_id=1,
            status="active",
            last_message_at=datetime.utcnow(),
        )
        db_session.add(conv)
        db_session.commit()

        msg1 = Message(
            conversation_id=conv.id,
            role="user",
            content="你好呀",
        )
        msg2 = Message(
            conversation_id=conv.id,
            role="assistant",
            content="你好！今天想聊什么？",
        )
        db_session.add_all([msg1, msg2])
        db_session.commit()

        response = client.get(f"/api/v1/parent/conversations/{conv.id}/messages")
        assert response.status_code == 200
        data = response.json()
        assert len(data["messages"]) == 2

    def test_get_messages_data_structure(self, client, db_session):
        """Test message items have the expected fields."""
        char = AICharacter(
            id=1, name="小智", system_prompt="你是小智", tts_voice_name="xiaoyan"
        )
        db_session.add(char)

        conv = Conversation(
            child_id=1,
            character_id=1,
            status="active",
            last_message_at=datetime.utcnow(),
        )
        db_session.add(conv)
        db_session.commit()

        msg = Message(
            conversation_id=conv.id,
            role="user",
            content="结构测试消息",
        )
        db_session.add(msg)
        db_session.commit()

        response = client.get(f"/api/v1/parent/conversations/{conv.id}/messages")
        m = response.json()["messages"][0]
        assert "role" in m
        assert "content" in m
        assert "timestamp" in m
        assert m["role"] == "user"
        assert m["content"] == "结构测试消息"
