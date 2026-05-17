"""Tests for STT (Speech-to-Text) functionality."""

import pytest
from unittest.mock import AsyncMock, patch, MagicMock
from fastapi.testclient import TestClient
import json


class TestXfyunSTTService:
    """Unit tests for XfyunSTTService."""

    def test_generate_signature(self):
        """Test signature generation produces valid authorization."""
        from app.services.xfyun_stt import XfyunSTTService

        service = XfyunSTTService(
            app_id="test_app_id",
            api_key="test_api_key",
            api_secret="test_api_secret"
        )

        authorization, timestamp = service._generate_signature()

        assert authorization is not None
        assert timestamp is not None
        assert len(authorization) > 0

    def test_signature_deterministic(self):
        """Test that same inputs produce same signature."""
        from app.services.xfyun_stt import XfyunSTTService

        service = XfyunSTTService(
            app_id="test_app_id",
            api_key="test_api_key",
            api_secret="test_api_secret"
        )

        auth1, ts1 = service._generate_signature()
        auth2, ts2 = service._generate_signature()

        # Same timestamp should produce same authorization
        assert auth1 == auth2


class TestSTTEndpoint:
    """Integration tests for POST /chat/stt endpoint."""

    @pytest.fixture
    def auth_headers(self):
        """Return valid authorization headers for testing."""
        return {"Authorization": "Bearer dev_test_token"}

    @pytest.fixture
    def mock_stt_service(self):
        """Create a mock STT service for testing."""
        with patch("app.api.chat.stt_service") as mock:
            mock.recognize = AsyncMock(return_value=("测试文本", 0.95))
            yield mock

    def test_stt_empty_audio(self, client, auth_headers):
        """Test that empty audio returns 400."""
        response = client.post(
            "/api/v1/chat/stt",
            files={"audio": ("empty.wav", b"", "audio/wav")},
            data={"format": "opus"},
            headers=auth_headers
        )
        # Empty audio (< 100 bytes) should return 400
        assert response.status_code == 400

    def test_stt_too_short_audio(self, client, auth_headers):
        """Test that audio shorter than 100 bytes returns 400."""
        audio_data = b"x" * 50  # 50 bytes - below minimum
        response = client.post(
            "/api/v1/chat/stt",
            files={"audio": ("short.wav", audio_data, "audio/wav")},
            data={"format": "opus"},
            headers=auth_headers
        )
        # Audio < 100 bytes should return 400
        assert response.status_code == 400

    def test_stt_too_large_audio(self, client, auth_headers):
        """Test that audio larger than 500KB returns 413."""
        audio_data = b"x" * 500_001  # Just over 500KB limit
        response = client.post(
            "/api/v1/chat/stt",
            files={"audio": ("large.wav", audio_data, "audio/wav")},
            data={"format": "opus"},
            headers=auth_headers
        )
        # Audio > 500KB should return 413
        assert response.status_code == 413

    def test_stt_normal_audio(self, client, auth_headers):
        """Test successful STT recognition with mock."""
        audio_data = b"x" * 2000  # Valid size audio

        # Mock the WebSocket connection and response
        mock_response = {
            "code": 0,
            "data": {
                "result": {
                    "ws": [
                        {"cw": [{"w": "测试", "c": 0.95}]},
                        {"cw": [{"w": "文本", "c": 0.90}]}
                    ]
                }
            }
        }

        with patch("app.services.xfyun_stt.websockets.connect") as mock_ws:
            mock_ws.return_value.__aenter__.return_value.recv = AsyncMock(
                return_value=json.dumps(mock_response)
            )
            mock_ws.return_value.__aenter__.return_value.send = AsyncMock()

            response = client.post(
                "/api/v1/chat/stt",
                files={"audio": ("test.opus", audio_data, "audio/opus")},
                data={"format": "opus"},
                headers=auth_headers
            )

            assert response.status_code == 200
            data = response.json()
            assert "text" in data
            assert "confidence" in data

    def test_stt_low_confidence(self, client, auth_headers):
        """Test that low confidence response returns 422."""
        audio_data = b"x" * 2000

        # Mock low confidence response
        mock_response = {
            "code": 0,
            "data": {
                "result": {
                    "ws": [
                        {"cw": [{"w": "?", "c": 0.1}]}
                    ]
                }
            }
        }

        with patch("app.services.xfyun_stt.websockets.connect") as mock_ws:
            mock_ws.return_value.__aenter__.return_value.recv = AsyncMock(
                return_value=json.dumps(mock_response)
            )
            mock_ws.return_value.__aenter__.return_value.send = AsyncMock()

            response = client.post(
                "/api/v1/chat/stt",
                files={"audio": ("low.opus", audio_data, "audio/opus")},
                data={"format": "opus"},
                headers=auth_headers
            )

            assert response.status_code == 422

    def test_stt_xfyun_error(self, client, auth_headers):
        """Test that xfyun API error returns 502."""
        audio_data = b"x" * 2000

        # Mock error response from xfyun
        mock_response = {
            "code": 10106,
            "data": {},
            "message": "invalid credentials"
        }

        with patch("app.services.xfyun_stt.websockets.connect") as mock_ws:
            mock_ws.return_value.__aenter__.return_value.recv = AsyncMock(
                return_value=json.dumps(mock_response)
            )
            mock_ws.return_value.__aenter__.return_value.send = AsyncMock()

            response = client.post(
                "/api/v1/chat/stt",
                files={"audio": ("error.opus", audio_data, "audio/opus")},
                data={"format": "opus"},
                headers=auth_headers
            )

            assert response.status_code == 502

    def test_stt_missing_audio(self, client, auth_headers):
        """Test that missing audio file returns 422."""
        response = client.post("/api/v1/chat/stt", headers=auth_headers)
        # FastAPI will return 422 for missing required field
        assert response.status_code == 422

    def test_stt_unauthorized(self, client):
        """Test that request without auth returns 401."""
        audio_data = b"x" * 2000
        response = client.post(
            "/api/v1/chat/stt",
            files={"audio": ("test.opus", audio_data, "audio/opus")},
            data={"format": "opus"}
        )
        # Missing auth header returns 422 (FastAPI validation), invalid token returns 401
        assert response.status_code in (401, 422)