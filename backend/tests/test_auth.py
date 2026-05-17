"""Tests for device registration and parent PIN authentication."""

import pytest
from datetime import datetime, timedelta
from unittest.mock import patch
from fastapi import HTTPException


class TestDeviceRegister:
    """Test device registration endpoint."""

    def test_device_register_new(self, client, db_session):
        """Test registering a new device creates a new child profile."""
        response = client.post(
            "/api/v1/device/register",
            json={"device_id": "test-device-001", "child_name": "小明"}
        )
        assert response.status_code == 200
        data = response.json()
        assert "device_token" in data
        assert data["device_token"].startswith("dev_")

    def test_device_register_existing(self, client, db_session):
        """Test re-registering an existing device creates new token."""
        device_id = "test-device-002"
        # First registration
        response1 = client.post(
            "/api/v1/device/register",
            json={"device_id": device_id, "child_name": "小红"}
        )
        assert response1.status_code == 200
        token1 = response1.json()["device_token"]

        # Second registration with same device_id
        response2 = client.post(
            "/api/v1/device/register",
            json={"device_id": device_id, "child_name": "小红"}
        )
        assert response2.status_code == 200
        token2 = response2.json()["device_token"]

        # Each registration generates a new token
        assert token1 != token2
        assert token1.startswith("dev_")
        assert token2.startswith("dev_")

    def test_device_register_without_child_name(self, client, db_session):
        """Test registering device without child_name uses default name."""
        response = client.post(
            "/api/v1/device/register",
            json={"device_id": "test-device-003"}
        )
        assert response.status_code == 200
        data = response.json()
        assert "device_token" in data


class TestParentAuth:
    """Test parent PIN authentication endpoint."""

    @pytest.fixture
    def setup_parent_settings(self, db_session):
        """Set up parent settings with a known PIN."""
        from app.models.parent_settings import ParentSettings
        import bcrypt

        # Create parent settings with PIN "123456"
        pin_hash = bcrypt.hashpw("123456".encode(), bcrypt.gensalt()).decode()
        settings = ParentSettings(
            id=1,
            pin_hash=pin_hash,
            pin_attempts=0,
            pin_locked_until=None
        )
        db_session.add(settings)
        db_session.commit()
        return settings

    def test_parent_auth_correct_pin(self, client, db_session, setup_parent_settings):
        """Test parent auth with correct PIN returns token."""
        response = client.post(
            "/api/v1/parent/auth",
            json={"pin": "123456"}
        )
        assert response.status_code == 200
        data = response.json()
        assert "token" in data
        assert data["token"].startswith("parent_")

    def test_parent_auth_wrong_pin(self, client, db_session, setup_parent_settings):
        """Test parent auth with wrong PIN returns 401."""
        response = client.post(
            "/api/v1/parent/auth",
            json={"pin": "wrongpin"}
        )
        assert response.status_code == 401
        assert "PIN码错误" in response.json()["detail"]

    def test_parent_auth_locked_after_5_attempts(self, client, db_session, setup_parent_settings):
        """Test parent account locks after 5 failed PIN attempts."""
        # First 5 wrong attempts
        for i in range(5):
            response = client.post(
                "/api/v1/parent/auth",
                json={"pin": "wrongpin"}
            )
            assert response.status_code == 401

        # 6th attempt should be locked
        response = client.post(
            "/api/v1/parent/auth",
            json={"pin": "wrongpin"}
        )
        assert response.status_code == 423
        assert "锁定" in response.json()["detail"]

    def test_parent_auth_unlock_after_timeout(self, client, db_session, setup_parent_settings):
        """Test parent account unlocks after 15 minute timeout."""
        from app.models.parent_settings import ParentSettings

        # Lock the account
        for i in range(5):
            client.post("/api/v1/parent/auth", json={"pin": "wrongpin"})

        # Verify locked
        response = client.post("/api/v1/parent/auth", json={"pin": "123456"})
        assert response.status_code == 423

        # Manually set locked_until to past time to simulate timeout
        settings = db_session.query(ParentSettings).filter(ParentSettings.id == 1).first()
        settings.pin_locked_until = datetime.utcnow() - timedelta(minutes=1)
        db_session.commit()

        # Now should work
        response = client.post("/api/v1/parent/auth", json={"pin": "123456"})
        assert response.status_code == 200
        assert "token" in response.json()

    def test_parent_auth_missing_settings(self, client, db_session):
        """Test parent auth when settings not initialized returns 404."""
        response = client.post(
            "/api/v1/parent/auth",
            json={"pin": "123456"}
        )
        assert response.status_code == 404
        assert "Settings not initialized" in response.json()["detail"]


class TestTokenVerification:
    """Test token verification middleware."""

    def test_verify_device_token_valid(self, client, db_session):
        """Test valid device token is accepted."""
        # Register device
        reg_response = client.post(
            "/api/v1/device/register",
            json={"device_id": "test-device-004", "child_name": "测试"}
        )
        token = reg_response.json()["device_token"]

        # Verify using the token (mock auth endpoint for testing)
        from app.services.auth import verify_device_token_valid
        child_id = verify_device_token_valid(token)
        assert child_id is not None

    def test_verify_device_token_invalid(self, client, db_session):
        """Test invalid device token is rejected."""
        from app.services.auth import verify_device_token_valid

        child_id = verify_device_token_valid("invalid_token")
        assert child_id is None

    def test_verify_parent_token_valid(self, client, db_session):
        """Test valid parent token is accepted."""
        from app.models.parent_settings import ParentSettings
        import bcrypt
        from app.services.auth import register_parent_token

        # Create parent settings with PIN "123456"
        pin_hash = bcrypt.hashpw("123456".encode(), bcrypt.gensalt()).decode()
        settings = ParentSettings(
            id=1,
            pin_hash=pin_hash,
            pin_attempts=0,
            pin_locked_until=None
        )
        db_session.add(settings)
        db_session.commit()

        # Get a valid parent token
        response = client.post("/api/v1/parent/auth", json={"pin": "123456"})
        assert response.status_code == 200
        token = response.json()["token"]

        # Verify the token is valid
        from app.services.auth import verify_parent_token_valid
        assert verify_parent_token_valid(token) is True

    def test_verify_parent_token_invalid(self, client, db_session):
        """Test invalid parent token is rejected."""
        from app.services.auth import verify_parent_token_valid

        assert verify_parent_token_valid("invalid_parent_token") is False