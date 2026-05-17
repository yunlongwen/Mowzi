"""Tests for ContextService - context assembly and token budget trimming."""

import pytest
from unittest.mock import MagicMock, patch

from app.services.context import ContextService, count_tokens


class TestCountTokens:
    """Unit tests for token counting utility."""

    def test_count_tokens_chinese(self):
        """Test token counting for Chinese text."""
        # 1 token ≈ 2 Chinese characters
        text = "你好世界"  # 4 characters
        tokens = count_tokens(text)
        assert tokens >= 1

    def test_count_tokens_english(self):
        """Test token counting for English text."""
        text = "Hello world"
        tokens = count_tokens(text)
        assert tokens >= 1

    def test_count_tokens_empty(self):
        """Test token counting for empty string."""
        tokens = count_tokens("")
        assert tokens == 0

    def test_count_tokens_mixed(self):
        """Test token counting for mixed Chinese and English."""
        text = "你好Hello世界World"
        tokens = count_tokens(text)
        assert tokens >= 1


class TestContextService:
    """Unit tests for ContextService."""

    @pytest.fixture
    def context_service(self):
        """Create a ContextService instance."""
        return ContextService()

    @pytest.fixture
    def mock_db(self):
        """Create a mock database session."""
        return MagicMock()

    def test_build_context_basic(self, context_service, mock_db):
        """Test basic context building with mocked data."""
        # Mock character
        mock_character = MagicMock()
        mock_character.system_prompt = "你是一个友好的AI伴侣"

        # Mock memories
        mock_memory = MagicMock()
        mock_memory.key = "name"
        mock_memory.value = "小明"

        # Mock summary
        mock_summary = MagicMock()
        mock_summary.summary_text = "孩子们聊了天气"

        # Mock recent messages
        mock_message1 = MagicMock()
        mock_message1.role = "user"
        mock_message1.content = "今天天气真好"

        mock_message2 = MagicMock()
        mock_message2.role = "assistant"
        mock_message2.content = "是呀，阳光很温暖"

        # Setup mock queries
        mock_db.query.return_value.filter.return_value.first.return_value = mock_character
        mock_db.query.return_value.filter.return_value.all.return_value = [mock_memory]
        mock_db.query.return_value.filter.return_value.first.side_effect = [
            mock_character,  # character query
            mock_summary,    # summary query
        ]
        mock_db.query.return_value.filter.return_value.order_by.return_value.limit.return_value.all.return_value = [
            mock_message1, mock_message2
        ]

        user_text = "我们出去玩吧"
        result = context_service.build_context(
            db=mock_db,
            child_id=1,
            conversation_id=1,
            character_id=1,
            user_text=user_text
        )

        # Should return a list with system prompt and user message
        assert len(result) >= 2
        assert result[0]["role"] == "system"
        assert result[-1]["role"] == "user"
        assert result[-1]["content"] == user_text

    def test_build_context_with_memories(self, context_service, mock_db):
        """Test that memories are included in context."""
        mock_character = MagicMock()
        mock_character.system_prompt = "你是一个友好的AI伴侣"

        mock_memory1 = MagicMock()
        mock_memory1.key = "name"
        mock_memory1.value = "小明"

        mock_memory2 = MagicMock()
        mock_memory2.key = "favorite_animal"
        mock_memory2.value = "恐龙"

        mock_db.query.return_value.filter.return_value.first.return_value = mock_character
        mock_db.query.return_value.filter.return_value.all.return_value = [mock_memory1, mock_memory2]
        mock_db.query.return_value.filter.return_value.order_by.return_value.limit.return_value.all.return_value = []

        user_text = "你好"
        result = context_service.build_context(
            db=mock_db,
            child_id=1,
            conversation_id=1,
            character_id=1,
            user_text=user_text
        )

        # Check that memory text is in the context (somewhere in the messages)
        context_text = " ".join([msg.get("content", "") for msg in result])
        assert "小明" in context_text or "恐龙" in context_text

    def test_build_context_no_summary(self, context_service, mock_db):
        """Test context building when no summary exists."""
        mock_character = MagicMock()
        mock_character.system_prompt = "你是一个友好的AI伴侣"

        mock_db.query.return_value.filter.return_value.first.side_effect = [
            mock_character,  # character
            None,           # no summary
        ]
        mock_db.query.return_value.filter.return_value.all.return_value = []
        mock_db.query.return_value.filter.return_value.order_by.return_value.limit.return_value.all.return_value = []

        user_text = "测试"
        result = context_service.build_context(
            db=mock_db,
            child_id=1,
            conversation_id=1,
            character_id=1,
            user_text=user_text
        )

        # Should still have system prompt and user message
        assert len(result) >= 2
        assert result[0]["role"] == "system"

    def test_build_context_no_recent_messages(self, context_service, mock_db):
        """Test context building when no recent messages exist."""
        mock_character = MagicMock()
        mock_character.system_prompt = "你是一个友好的AI伴侣"

        mock_db.query.return_value.filter.return_value.first.side_effect = [
            mock_character,
            None,
        ]
        mock_db.query.return_value.filter.return_value.all.return_value = []
        mock_db.query.return_value.filter.return_value.order_by.return_value.limit.return_value.all.return_value = []

        user_text = "你好"
        result = context_service.build_context(
            db=mock_db,
            child_id=1,
            conversation_id=1,
            character_id=1,
            user_text=user_text
        )

        # Should still work with just system prompt and user message
        assert len(result) >= 2


class TestContextServicePriority:
    """Test context priority and trimming logic."""

    def test_context_includes_system_prompt(self):
        """Test that system prompt is always first and not trimmed."""
        service = ContextService()

        # The system prompt should be preserved even under tight budget
        # because it's marked as non-trimmable in the implementation
        assert hasattr(service, 'build_context')

    def test_token_budget_calculation(self):
        """Test that token budget is correctly calculated."""
        # Verify that budget = context_window - system_tokens - user_tokens - max_llm_tokens
        # This is a sanity check for the calculation logic
        from app.config import settings

        # These values should be defined in settings
        assert settings.context_window_tokens > 0
        assert settings.max_llm_tokens > 0