"""Tests for all SQLAlchemy models — verify they can be created and persisted."""
from datetime import date, datetime

from app.models import (
    ChildProfile,
    Conversation,
    Message,
    AICharacter,
    ParentSettings,
    ConversationSummary,
    KeyMemory,
    ActiveSession,
    UsageLog,
)


class TestChildProfile:
    def test_create_child_profile(self, db_session):
        child = ChildProfile(
            id=1,
            name="Alice",
            device_id="device-001",
        )
        db_session.add(child)
        db_session.commit()

        result = db_session.query(ChildProfile).filter_by(id=1).first()
        assert result is not None
        assert result.name == "Alice"
        assert result.device_id == "device-001"
        assert result.created_at is not None

    def test_child_profile_unique_device_id(self, db_session):
        child1 = ChildProfile(id=1, name="Alice", device_id="dev-1")
        child2 = ChildProfile(id=2, name="Bob", device_id="dev-1")
        db_session.add(child1)
        db_session.commit()
        db_session.add(child2)
        from sqlalchemy.exc import IntegrityError

        try:
            db_session.commit()
            assert False, "Should have raised IntegrityError"
        except IntegrityError:
            db_session.rollback()


class TestAICharacter:
    def test_create_ai_character(self, db_session):
        char = AICharacter(
            id=1,
            name="猫头鹰医生",
            avatar_url="/avatars/owl.png",
            description="知识渊博的猫头鹰医生",
            system_prompt="你是一位知识渊博的猫头鹰医生",
            tts_voice_name="xiaoyan",
        )
        db_session.add(char)
        db_session.commit()

        result = db_session.query(AICharacter).filter_by(id=1).first()
        assert result is not None
        assert result.name == "猫头鹰医生"
        assert result.tts_voice_name == "xiaoyan"


class TestConversation:
    def test_create_conversation(self, db_session):
        # Prerequisite: child and character
        child = ChildProfile(id=1, name="Alice", device_id="dev-1")
        char = AICharacter(
            id=1, name="Test Char", avatar_url="/a.png",
            description="desc", system_prompt="prompt", tts_voice_name="voice"
        )
        db_session.add_all([child, char])
        db_session.commit()

        conv = Conversation(
            id=1,
            child_id=1,
            character_id=1,
            title="Test Conversation",
        )
        db_session.add(conv)
        db_session.commit()

        result = db_session.query(Conversation).filter_by(id=1).first()
        assert result is not None
        assert result.title == "Test Conversation"
        assert result.status == "active"
        assert result.created_at is not None


class TestMessage:
    def test_create_message(self, db_session):
        child = ChildProfile(id=1, name="Alice", device_id="dev-1")
        char = AICharacter(
            id=1, name="Test", avatar_url="/a.png",
            description="d", system_prompt="p", tts_voice_name="v"
        )
        db_session.add_all([child, char])
        db_session.commit()

        conv = Conversation(id=1, child_id=1, character_id=1, title="T")
        db_session.add(conv)
        db_session.commit()

        msg = Message(
            id=1,
            conversation_id=1,
            role="user",
            content="Hello!",
        )
        db_session.add(msg)
        db_session.commit()

        result = db_session.query(Message).filter_by(id=1).first()
        assert result is not None
        assert result.role == "user"
        assert result.content == "Hello!"
        assert result.audio_path is None
        assert result.timestamp is not None


class TestParentSettings:
    def test_create_parent_settings(self, db_session):
        settings = ParentSettings(
            id=1,
            pin_hash="$2b$12$somehashedvalue",
        )
        db_session.add(settings)
        db_session.commit()

        result = db_session.query(ParentSettings).filter_by(id=1).first()
        assert result is not None
        assert result.pin_hash == "$2b$12$somehashedvalue"
        assert result.daily_limit_min is None
        assert result.session_limit_min is None


class TestConversationSummary:
    def test_create_conversation_summary(self, db_session):
        child = ChildProfile(id=1, name="Alice", device_id="dev-1")
        char = AICharacter(
            id=1, name="Test", avatar_url="/a.png",
            description="d", system_prompt="p", tts_voice_name="v"
        )
        db_session.add_all([child, char])
        db_session.commit()

        conv = Conversation(id=1, child_id=1, character_id=1, title="T")
        db_session.add(conv)
        db_session.commit()

        summary = ConversationSummary(
            id=1,
            conversation_id=1,
            summary_text="This was a conversation about dinosaurs.",
            message_count_at_summary=10,
        )
        db_session.add(summary)
        db_session.commit()

        result = db_session.query(ConversationSummary).filter_by(id=1).first()
        assert result is not None
        assert result.summary_text == "This was a conversation about dinosaurs."
        assert result.message_count_at_summary == 10


class TestKeyMemory:
    def test_create_key_memory(self, db_session):
        child = ChildProfile(id=1, name="Alice", device_id="dev-1")
        db_session.add(child)
        db_session.commit()

        memory = KeyMemory(
            id=1,
            child_id=1,
            key="favorite_animal",
            value="dinosaurs",
        )
        db_session.add(memory)
        db_session.commit()

        result = db_session.query(KeyMemory).filter_by(id=1).first()
        assert result is not None
        assert result.key == "favorite_animal"
        assert result.value == "dinosaurs"


class TestActiveSession:
    def test_create_active_session(self, db_session):
        child = ChildProfile(id=1, name="Alice", device_id="dev-1")
        db_session.add(child)
        db_session.commit()

        session = ActiveSession(
            id=1,
            child_id=1,
        )
        db_session.add(session)
        db_session.commit()

        result = db_session.query(ActiveSession).filter_by(id=1).first()
        assert result is not None
        assert result.status == "active"
        assert result.started_at is not None
        assert result.last_activity_at is not None


class TestUsageLog:
    def test_create_usage_log(self, db_session):
        child = ChildProfile(id=1, name="Alice", device_id="dev-1")
        db_session.add(child)
        db_session.commit()

        log = UsageLog(
            id=1,
            child_id=1,
            date=date(2026, 5, 16),
            total_minutes=15,
            message_count=42,
        )
        db_session.add(log)
        db_session.commit()

        result = db_session.query(UsageLog).filter_by(id=1).first()
        assert result is not None
        assert result.total_minutes == 15
        assert result.message_count == 42
        assert result.date == date(2026, 5, 16)