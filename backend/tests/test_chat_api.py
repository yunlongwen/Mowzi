"""Tests for chat API endpoints (STT validation)."""

import pytest
from unittest.mock import AsyncMock, patch


class TestChatSTTEndpoint:
    """Tests for POST /api/v1/chat/stt."""

    @pytest.fixture
    def auth_headers(self):
        """Return valid authorization headers for testing."""
        return {"Authorization": "Bearer dev_test_token"}

    def test_stt_empty_audio_returns_error(self, client, auth_headers):
        """Test that empty audio (0 bytes) returns 400."""
        response = client.post(
            "/api/v1/chat/stt",
            files={"audio": ("empty.wav", b"", "audio/wav")},
            data={"format": "opus"},
            headers=auth_headers,
        )
        assert response.status_code == 400
        assert "short" in response.json()["detail"].lower()

    def test_stt_too_short_audio_returns_error(self, client, auth_headers):
        """Test that audio shorter than 100 bytes returns 400."""
        audio_data = b"x" * 50  # Well below 100-byte minimum
        response = client.post(
            "/api/v1/chat/stt",
            files={"audio": ("short.wav", audio_data, "audio/wav")},
            data={"format": "opus"},
            headers=auth_headers,
        )
        assert response.status_code == 400
        assert "short" in response.json()["detail"].lower()

    def test_stt_too_large_audio_returns_413(self, client, auth_headers):
        """Test that audio larger than 500KB returns 413."""
        audio_data = b"x" * (500_001)  # Just over 500KB
        response = client.post(
            "/api/v1/chat/stt",
            files={"audio": ("large.wav", audio_data, "audio/wav")},
            data={"format": "opus"},
            headers=auth_headers,
        )
        assert response.status_code == 413

    def test_stt_exact_min_size_passes_validation(self, client, auth_headers):
        """Test that audio at exactly 100 bytes passes size validation (may still fail STT)."""
        audio_data = b"x" * 100  # Exactly at minimum
        # Mock the STT service to avoid external calls
        with patch("app.api.chat.stt_service") as mock_stt:
            mock_stt.recognize = AsyncMock(return_value=("你好", 0.95))
            response = client.post(
                "/api/v1/chat/stt",
                files={"audio": ("min.wav", audio_data, "audio/wav")},
                data={"format": "opus"},
                headers=auth_headers,
            )
            # Should not return 400 for size issues
            assert response.status_code == 200

    def test_stt_missing_auth_returns_error(self, client):
        """Test that request without auth returns 422 or 401."""
        audio_data = b"x" * 200
        response = client.post(
            "/api/v1/chat/stt",
            files={"audio": ("test.opus", audio_data, "audio/opus")},
            data={"format": "opus"},
        )
        assert response.status_code in (401, 422)

    def test_stt_missing_audio_field_returns_422(self, client, auth_headers):
        """Test that missing audio upload field returns 422."""
        response = client.post(
            "/api/v1/chat/stt",
            headers=auth_headers,
        )
        assert response.status_code == 422
