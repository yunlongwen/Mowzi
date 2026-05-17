"""Tests for Xfyun TTS service signature generation."""

import base64
import hashlib
import hmac
from unittest.mock import patch

import pytest


class TestXfyunTTSService:
    """Unit tests for XfyunTTSService."""

    def test_build_url_contains_required_components(self):
        """Test that _build_url returns a valid WebSocket URL."""
        from app.services.xfyun_tts import XfyunTTSService

        service = XfyunTTSService(
            app_id="test_app_id",
            api_key="test_api_key",
            api_secret="test_api_secret",
        )

        with patch("app.services.xfyun_tts.time") as mock_time:
            mock_time.time.return_value = 1700000000
            url = service._build_url()

        assert url.startswith("wss://tts-api.xfyun.cn/v3/tts?")
        assert "authorization=" in url
        assert "date=" in url
        assert "host=" in url

    def test_build_url_signature_valid_hmac(self):
        """Test that the authorization in the URL is a valid HMAC-SHA256 signature."""
        from app.services.xfyun_tts import XfyunTTSService

        api_secret = "my_secret_key"
        service = XfyunTTSService(
            app_id="test_app_id",
            api_key="test_api_key",
            api_secret=api_secret,
        )

        with patch("app.services.xfyun_tts.time") as mock_time:
            mock_time.time.return_value = 1700000000
            url = service._build_url()

        # Extract authorization from URL
        auth_param = url.split("authorization=")[1].split("&")[0]
        decoded_auth = base64.b64decode(auth_param)

        # Recreate the expected signature
        host = "tts-api.xfyun.cn"
        ts = "1700000000"
        signature_origin = f"host: {host}\r\ndate: {ts}\r\nGET /v3/tts HTTP/1.1"
        expected_sig = hmac.new(
            api_secret.encode(),
            signature_origin.encode(),
            hashlib.sha256,
        ).digest()

        assert decoded_auth == expected_sig

    def test_build_url_contains_correct_host(self):
        """Test that host parameter in URL matches the expected TTS host."""
        from app.services.xfyun_tts import XfyunTTSService

        service = XfyunTTSService(
            app_id="test_app",
            api_key="test_key",
            api_secret="test_secret",
        )

        with patch("app.services.xfyun_tts.time") as mock_time:
            mock_time.time.return_value = 1700000000
            url = service._build_url()

        assert "host=tts-api.xfyun.cn" in url

    def test_build_url_date_matches_timestamp(self):
        """Test that date parameter in URL matches the timestamp used in signature."""
        from app.services.xfyun_tts import XfyunTTSService

        service = XfyunTTSService(
            app_id="test_app",
            api_key="test_key",
            api_secret="test_secret",
        )

        fixed_ts = 1700000000
        with patch("app.services.xfyun_tts.time") as mock_time:
            mock_time.time.return_value = fixed_ts
            url = service._build_url()

        assert f"date={fixed_ts}" in url

    def test_build_url_deterministic(self):
        """Test that same timestamp produces same URL."""
        from app.services.xfyun_tts import XfyunTTSService

        service = XfyunTTSService(
            app_id="test_app",
            api_key="test_key",
            api_secret="test_secret",
        )

        with patch("app.services.xfyun_tts.time") as mock_time:
            mock_time.time.return_value = 1700000000
            url1 = service._build_url()
            url2 = service._build_url()

        assert url1 == url2

    def test_build_url_different_secrets_produce_different_auth(self):
        """Test that different API secrets produce different authorization values."""
        from app.services.xfyun_tts import XfyunTTSService

        service1 = XfyunTTSService(
            app_id="test_app", api_key="test_key", api_secret="secret1"
        )
        service2 = XfyunTTSService(
            app_id="test_app", api_key="test_key", api_secret="secret2"
        )

        with patch("app.services.xfyun_tts.time") as mock_time:
            mock_time.time.return_value = 1700000000
            url1 = service1._build_url()
            url2 = service2._build_url()

        auth1 = url1.split("authorization=")[1].split("&")[0]
        auth2 = url2.split("authorization=")[1].split("&")[0]
        assert auth1 != auth2
