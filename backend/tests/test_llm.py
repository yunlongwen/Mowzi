"""Tests for LLM streaming, sentence splitting, and safety functionality."""

import pytest
from unittest.mock import AsyncMock, patch, MagicMock


class TestSentenceSplitter:
    """Unit tests for SentenceSplitter."""

    def test_sentence_splitter_basic(self):
        """Test basic sentence splitting."""
        from app.services.llm import SentenceSplitter

        splitter = SentenceSplitter()
        sentences = splitter.add_chunk("你好呀！")
        assert sentences == ["你好呀！"]

    def test_sentence_splitter_multiple_sentences(self):
        """Test splitting multiple sentences."""
        from app.services.llm import SentenceSplitter

        splitter = SentenceSplitter()
        sentences = splitter.add_chunk("今天天气真好！")
        assert sentences == ["今天天气真好！"]

        sentences = splitter.add_chunk("我们出去玩吧？")
        assert sentences == ["我们出去玩吧？"]

    def test_sentence_splitter_flush_remaining(self):
        """Test flushing remaining buffer."""
        from app.services.llm import SentenceSplitter

        splitter = SentenceSplitter()
        splitter.add_chunk("今天天气")
        remaining = splitter.flush()
        assert remaining == "今天天气"

        remaining2 = splitter.flush()
        assert remaining2 == ""

    def test_sentence_splitter_incomplete(self):
        """Test that incomplete sentences stay in buffer."""
        from app.services.llm import SentenceSplitter

        splitter = SentenceSplitter()
        sentences = splitter.add_chunk("今天天气")
        assert sentences == []
        assert splitter.buffer == "今天天气"

    def test_sentence_splitter_english_punctuation(self):
        """Test splitting with English punctuation."""
        from app.services.llm import SentenceSplitter

        splitter = SentenceSplitter()
        sentences = splitter.add_chunk("Hello! How are you?")
        assert sentences == ["Hello!", "How are you?"]


class TestLLMService:
    """Unit tests for LLMService."""

    @pytest.fixture
    def llm_service(self):
        """Create an LLM service instance."""
        from app.services.llm import LLMService
        return LLMService(
            api_url="https://api.example.com",
            api_key="test_key",
            model="test-model"
        )

    @pytest.mark.asyncio
    async def test_llm_stream_chat(self, llm_service):
        """Test streaming chat completion."""
        mock_stream = AsyncMock()
        mock_stream.__aiter__.return_value = iter([
            MagicMock(choices=[MagicMock(delta=MagicMock(content="你好"))]),
            MagicMock(choices=[MagicMock(delta=MagicMock(content="呀！"))]),
        ])
        mock_create = AsyncMock(return_value=mock_stream)

        with patch.object(llm_service.client.chat.completions, 'create', mock_create):
            chunks = []
            async for chunk in llm_service.stream_chat([{"role": "user", "content": "hi"}]):
                chunks.append(chunk)
            assert chunks == ["你好", "呀！"]

    @pytest.mark.asyncio
    async def test_llm_stream_chat_empty_response(self, llm_service):
        """Test streaming with empty response chunks."""
        mock_stream = AsyncMock()
        mock_stream.__aiter__.return_value = iter([
            MagicMock(choices=[MagicMock(delta=MagicMock(content=""))]),
            MagicMock(choices=[MagicMock(delta=MagicMock(content="有内容"))]),
        ])
        mock_create = AsyncMock(return_value=mock_stream)

        with patch.object(llm_service.client.chat.completions, 'create', mock_create):
            chunks = []
            async for chunk in llm_service.stream_chat([{"role": "user", "content": "hi"}]):
                chunks.append(chunk)
            assert chunks == ["有内容"]


class TestSafetyService:
    """Unit tests for SafetyService."""

    def test_safety_check_clean_text(self):
        """Test that clean text passes safety check."""
        from app.services.safety import SafetyService

        service = SafetyService()
        result = service.check_content("今天天气真好")
        assert result["safe"] is True
        assert result["flagged_words"] == []

    def test_safety_check_profanity(self):
        """Test that profanity is flagged."""
        from app.services.safety import SafetyService

        service = SafetyService()
        result = service.check_content("滚开")
        assert result["safe"] is False
        assert len(result["flagged_words"]) > 0

    def test_safety_check_empty(self):
        """Test that empty text passes safety check."""
        from app.services.safety import SafetyService

        service = SafetyService()
        result = service.check_content("")
        assert result["safe"] is True


class TestSentenceSplitterIntegration:
    """Integration tests for sentence splitter with sequential chunks."""

    def test_sentence_splitter_integration(self):
        """Test sentence splitter with sequential chunks."""
        from app.services.llm import SentenceSplitter

        splitter = SentenceSplitter()
        all_sentences = []

        chunks = ["今天", "天气", "真", "好！", "我们", "去", "玩吧？"]
        for chunk in chunks:
            sentences = splitter.add_chunk(chunk)
            all_sentences.extend(sentences)

        remaining = splitter.flush()
        if remaining:
            all_sentences.append(remaining)

        assert all_sentences == ["今天天气真好！", "我们去玩吧？"]
