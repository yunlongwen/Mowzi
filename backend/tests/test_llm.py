"""Tests for LLM streaming, sentence splitting, and TTS functionality."""

import pytest
from unittest.mock import AsyncMock, patch, MagicMock
import json


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

        # Buffer should be cleared
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
        # client.chat.completions.create is an async method that returns awaitable
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


class TestXfyunTTSService:
    """Unit tests for XfyunTTSService."""

    @pytest.fixture
    def tts_service(self):
        """Create a TTS service instance."""
        from app.services.xfyun_tts import XfyunTTSService
        return XfyunTTSService(
            app_id="test_app_id",
            api_key="test_api_key",
            api_secret="test_api_secret"
        )

    @pytest.mark.asyncio
    async def test_tts_synthesize(self, tts_service):
        """Test TTS synthesis with mocked WebSocket."""
        mock_audio = b"fake_audio_data"
        mock_response = {
            "code": 0,
            "data": {
                "audio": base64.b64encode(mock_audio).decode(),
                "status": 2
            }
        }

        mock_ws = AsyncMock()
        mock_ws.__aenter__.return_value = mock_ws
        mock_ws.__aexit__.return_value = None
        mock_ws.send = AsyncMock()
        mock_ws.recv = AsyncMock(return_value=json.dumps(mock_response))

        with patch("app.services.xfyun_tts.websockets.connect", return_value=mock_ws):
            audio_b64, duration = await tts_service.synthesize("测试文本")
            assert audio_b64 == base64.b64encode(mock_audio).decode()
            assert duration == int(len(mock_audio) / 32)

    @pytest.mark.asyncio
    async def test_tts_error_response(self, tts_service):
        """Test TTS error handling."""
        mock_response = {
            "code": 10106,
            "message": "invalid credentials"
        }

        mock_ws = AsyncMock()
        mock_ws.__aenter__.return_value = mock_ws
        mock_ws.__aexit__.return_value = None
        mock_ws.send = AsyncMock()
        mock_ws.recv = AsyncMock(return_value=json.dumps(mock_response))

        with patch("app.services.xfyun_tts.websockets.connect", return_value=mock_ws):
            with pytest.raises(Exception) as exc_info:
                await tts_service.synthesize("测试文本")
            assert "TTS错误" in str(exc_info.value)


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


import base64


class TestChatStreamIntegration:
    """Integration tests for chat streaming endpoint."""

    @pytest.fixture
    def auth_headers(self):
        """Return valid authorization headers for testing."""
        return {"Authorization": "Bearer dev_test_token"}

    @pytest.fixture
    def mock_llm_service(self):
        """Create a mock LLM service."""
        with patch("app.api.chat.llm_service") as mock:
            mock.stream_chat = AsyncMock()
            mock.stream_chat.return_value.__aiter__.return_value = iter([
                "今天", "天气", "真", "好！", "我们", "去", "玩吧？"
            ])
            yield mock

    @pytest.fixture
    def mock_tts_service(self):
        """Create a mock TTS service."""
        with patch("app.api.chat.tts_service") as mock:
            mock.synthesize = AsyncMock(return_value=("fake_audio_b64", 1000))
            yield mock

    @pytest.mark.asyncio
    async def test_chat_stream_integration(self, client, auth_headers, mock_llm_service, mock_tts_service):
        """Test full streaming pipeline: LLM -> sentence split -> TTS."""
        # This test would require the full context_service which doesn't exist yet
        # For now, we test the components individually
        pass

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