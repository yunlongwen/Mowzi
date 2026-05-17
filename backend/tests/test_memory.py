"""Tests for MemoryService - key memory extraction and conversation summarization."""

import pytest
from unittest.mock import AsyncMock, MagicMock
from datetime import datetime

from app.services.memory import MemoryService


class TestMemoryService:
    """Unit tests for MemoryService."""

    @pytest.fixture
    def memory_service(self):
        """Create a MemoryService instance with mocked LLM."""
        service = MemoryService.__new__(MemoryService)
        service.llm_service = AsyncMock()
        return service

    @pytest.fixture
    def mock_db(self):
        """Create a mock database session with proper chain setup."""
        mock_db = MagicMock()

        # Configure query().filter().first() chain
        def setup_query_chain():
            query_mock = MagicMock()
            mock_db.query.return_value = query_mock

            # filter().first() returns None by default
            filter_mock = MagicMock()
            query_mock.filter.return_value = filter_mock
            filter_mock.first.return_value = None

            return query_mock

        setup_query_chain()
        return mock_db

    @pytest.mark.asyncio
    async def test_extract_memories_empty_response(self, memory_service, mock_db):
        """Test memory extraction with empty response (no new info)."""
        memory_service.llm_service.single_call = AsyncMock(return_value="{}")

        messages = [
            MagicMock(role="user", content="你好"),
            MagicMock(role="assistant", content="你好呀！"),
            MagicMock(role="user", content="今天天气真好"),
            MagicMock(role="assistant", content="是呀！"),
            MagicMock(role="user", content="我们出去玩吧"),
        ]

        await memory_service.extract_memories(mock_db, child_id=1, recent_messages=messages)

        # When LLM returns {}, no memories are added
        assert not mock_db.add.called

    @pytest.mark.asyncio
    async def test_extract_memories_with_new_info(self, memory_service, mock_db):
        """Test memory extraction with new child information."""
        memory_service.llm_service.single_call = AsyncMock(
            return_value='{"name": "小明", "favorite_animal": "恐龙"}'
        )

        messages = [
            MagicMock(role="user", content="我叫小明，我最喜欢恐龙！"),
            MagicMock(role="assistant", content="哇，小明！恐龙专家呢！"),
            MagicMock(role="user", content="你知道霸王龙吗"),
            MagicMock(role="assistant", content="当然知道！"),
            MagicMock(role="user", content="我觉得它很酷"),
        ]

        await memory_service.extract_memories(mock_db, child_id=1, recent_messages=messages)

        # When LLM returns memory info, db.add should be called
        assert mock_db.add.called

    @pytest.mark.asyncio
    async def test_extract_memories_update_existing(self, memory_service, mock_db):
        """Test that existing memories are updated, not duplicated."""
        memory_service.llm_service.single_call = AsyncMock(
            return_value='{"name": "小明"}'
        )

        # Setup existing memory
        existing_memory = MagicMock()
        existing_memory.key = "name"
        existing_memory.value = "老名字"
        mock_db.query.return_value.filter.return_value.first.return_value = existing_memory

        messages = [
            MagicMock(role="user", content="其实我叫小强"),
            MagicMock(role="assistant", content="好的小强！"),
            MagicMock(role="user", content="我喜欢画画"),
            MagicMock(role="assistant", content="画得怎么样？"),
            MagicMock(role="user", content="还不错"),
        ]

        await memory_service.extract_memories(mock_db, child_id=1, recent_messages=messages)

        # Existing memory should be updated to LLM response value
        assert existing_memory.value == "小明"
        # db.add should NOT be called since we're updating existing
        assert not mock_db.add.called

    @pytest.mark.asyncio
    async def test_summarize_conversation_short(self, memory_service, mock_db):
        """Test that short conversations (< 20 messages) are not summarized."""
        # Return short message list (only 2)
        mock_db.query.return_value.filter.return_value.order_by.return_value.all.return_value = [
            MagicMock(role="user", content="Hi"),
            MagicMock(role="assistant", content="Hello!")
        ]

        await memory_service.summarize_conversation(mock_db, conversation_id=1)

        # single_call should NOT be called for short conversations
        memory_service.llm_service.single_call.assert_not_called()

    @pytest.mark.asyncio
    async def test_summarize_conversation_long(self, memory_service, mock_db):
        """Test conversation summarization for long conversations (>= 20 messages)."""
        # Create 20+ messages
        messages = [
            MagicMock(role="user" if i % 2 == 0 else "assistant", content=f"Message {i}")
            for i in range(25)
        ]

        # Create a fresh mock_db with properly chained returns
        fresh_mock_db = MagicMock()

        # First query: Conversation - return a mock conversation
        conversation_mock = MagicMock(id=1, child_id=1)

        # Second query: Message - return messages
        # Third query: ConversationSummary - return None (no existing summary)

        # We need to track call count to return different values
        call_count = [0]

        def first_side_effect(*args, **kwargs):
            call_count[0] += 1
            if call_count[0] == 1:
                return conversation_mock  # Conversation query
            elif call_count[0] == 3:
                return None  # ConversationSummary query (no existing summary)
            return None

        def all_side_effect(*args, **kwargs):
            return messages

        fresh_mock_db.query.return_value.filter.return_value.first.side_effect = first_side_effect
        fresh_mock_db.query.return_value.filter.return_value.order_by.return_value.all.side_effect = all_side_effect

        memory_service.llm_service.single_call = AsyncMock(return_value="对话摘要：孩子们聊得很开心")

        await memory_service.summarize_conversation(fresh_mock_db, conversation_id=1)

        # Should call LLM for summary
        memory_service.llm_service.single_call.assert_called_once()
        # Should add new summary
        fresh_mock_db.add.assert_called_once()

    @pytest.mark.asyncio
    async def test_summarize_conversation_optimistic_lock(self, memory_service, mock_db):
        """Test that summarization skips if message count hasn't changed."""
        # Create 20 messages
        messages = [
            MagicMock(role="user" if i % 2 == 0 else "assistant", content=f"Message {i}")
            for i in range(20)
        ]

        # Setup conversation query
        conv_mock = MagicMock(id=1, child_id=1)
        mock_db.query.return_value.filter.return_value.first.return_value = conv_mock

        # Setup messages query
        mock_db.query.return_value.filter.return_value.order_by.return_value.all.return_value = messages

        # Existing summary with same message count (optimistic lock)
        existing_summary = MagicMock()
        existing_summary.message_count_at_summary = 20

        # When we query for summary, return existing_summary
        mock_db.query.return_value.filter.return_value.first.return_value = existing_summary

        await memory_service.summarize_conversation(mock_db, conversation_id=1)

        # Should NOT call LLM since summary is up-to-date
        memory_service.llm_service.single_call.assert_not_called()

    @pytest.mark.asyncio
    async def test_summarize_conversation_update_existing_summary(self, memory_service, mock_db):
        """Test that existing summary is updated, not duplicated."""
        messages = [
            MagicMock(role="user" if i % 2 == 0 else "assistant", content=f"Message {i}")
            for i in range(22)
        ]

        # Setup conversation query
        conv_mock = MagicMock(id=1, child_id=1)
        mock_db.query.return_value.filter.return_value.first.return_value = conv_mock

        # Setup messages query
        mock_db.query.return_value.filter.return_value.order_by.return_value.all.return_value = messages

        # Existing summary with different message count (needs update)
        existing_summary = MagicMock()
        existing_summary.message_count_at_summary = 10  # Different from current (22)

        # When we query for summary, return existing_summary
        mock_db.query.return_value.filter.return_value.first.return_value = existing_summary

        memory_service.llm_service.single_call = AsyncMock(return_value="更新后的摘要")

        await memory_service.summarize_conversation(mock_db, conversation_id=1)

        # Should update existing summary
        assert existing_summary.summary_text == "更新后的摘要"
        assert existing_summary.message_count_at_summary == 22
        mock_db.add.assert_not_called()  # Should NOT add new, should update existing


class TestMemoryExtractionPrompt:
    """Test the memory extraction prompt format."""

    def test_extraction_prompt_format(self):
        """Test that extraction prompt contains conversation text."""
        from app.services.memory import MemoryService

        prompt_template = MemoryService.EXTRACTION_PROMPT
        prompt = prompt_template.format(conversation_text="user: 你好\nassistant: 你好呀！")
        assert "user: 你好" in prompt
        assert "assistant: 你好呀！" in prompt
        assert "JSON格式" in prompt