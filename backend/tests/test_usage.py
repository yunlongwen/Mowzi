"""Tests for usage tracking service."""

import pytest
from datetime import datetime, timedelta
from unittest.mock import MagicMock, patch
from app.services.usage import UsageService
from app.models.usage import UsageLog
from app.models.session import ActiveSession


class TestDailyLimitCheck:
    """Test daily usage limit checking."""

    def test_daily_limit_check_no_usage(self, db_session):
        """Test daily limit when no usage recorded."""
        usage_service = UsageService(daily_limit_minutes=60)

        # No usage for child_id=1
        is_exceeded, remaining = usage_service.check_daily_limit(1, db_session)

        assert is_exceeded is False
        assert remaining == 60

    def test_daily_limit_check_within_limit(self, db_session):
        """Test daily limit when usage is within limit."""
        usage_service = UsageService(daily_limit_minutes=60)

        # Add usage log for today
        today = datetime.now().date()
        usage_log = UsageLog(
            child_id=1,
            date=today,
            total_minutes=30,
            message_count=5
        )
        db_session.add(usage_log)
        db_session.commit()

        is_exceeded, remaining = usage_service.check_daily_limit(1, db_session)

        assert is_exceeded is False
        assert remaining == 30

    def test_daily_limit_check_at_limit(self, db_session):
        """Test daily limit when exactly at limit."""
        usage_service = UsageService(daily_limit_minutes=60)

        today = datetime.now().date()
        usage_log = UsageLog(
            child_id=1,
            date=today,
            total_minutes=60,
            message_count=10
        )
        db_session.add(usage_log)
        db_session.commit()

        is_exceeded, remaining = usage_service.check_daily_limit(1, db_session)

        assert is_exceeded is True
        assert remaining == 0

    def test_daily_limit_check_exceeded(self, db_session):
        """Test daily limit when exceeded."""
        usage_service = UsageService(daily_limit_minutes=60)

        today = datetime.now().date()
        usage_log = UsageLog(
            child_id=1,
            date=today,
            total_minutes=90,
            message_count=15
        )
        db_session.add(usage_log)
        db_session.commit()

        is_exceeded, remaining = usage_service.check_daily_limit(1, db_session)

        assert is_exceeded is True
        assert remaining == 0


class TestSessionLimitCheck:
    """Test session limit checking."""

    def test_session_limit_check_no_session(self, db_session):
        """Test session limit when no active session."""
        usage_service = UsageService(session_limit_minutes=30)

        is_exceeded, remaining = usage_service.check_session_limit(1, db_session)

        assert is_exceeded is False
        assert remaining == 30

    def test_session_limit_check_within_limit(self, db_session):
        """Test session limit when within limit."""
        usage_service = UsageService(session_limit_minutes=30)

        # Create active session started 10 minutes ago
        active_session = ActiveSession(
            child_id=1,
            started_at=datetime.now() - timedelta(minutes=10),
            last_activity_at=datetime.now() - timedelta(minutes=1),
            status="active"
        )
        db_session.add(active_session)
        db_session.commit()

        is_exceeded, remaining = usage_service.check_session_limit(1, db_session)

        assert is_exceeded is False
        assert remaining == 20  # 30 - 10

    def test_session_limit_check_at_limit(self, db_session):
        """Test session limit when exactly at limit."""
        usage_service = UsageService(session_limit_minutes=30)

        # Create active session started 30 minutes ago
        active_session = ActiveSession(
            child_id=1,
            started_at=datetime.now() - timedelta(minutes=30),
            last_activity_at=datetime.now() - timedelta(minutes=1),
            status="active"
        )
        db_session.add(active_session)
        db_session.commit()

        is_exceeded, remaining = usage_service.check_session_limit(1, db_session)

        assert is_exceeded is True
        assert remaining == 0

    def test_session_limit_check_expired(self, db_session):
        """Test session limit when session expired."""
        usage_service = UsageService(session_limit_minutes=30)

        # Create active session started 45 minutes ago
        active_session = ActiveSession(
            child_id=1,
            started_at=datetime.now() - timedelta(minutes=45),
            last_activity_at=datetime.now() - timedelta(minutes=1),
            status="active"
        )
        db_session.add(active_session)
        db_session.commit()

        is_exceeded, remaining = usage_service.check_session_limit(1, db_session)

        assert is_exceeded is True
        assert remaining == 0

    def test_session_limit_check_ended_session(self, db_session):
        """Test session limit ignores ended sessions."""
        usage_service = UsageService(session_limit_minutes=30)

        # Create ended session
        active_session = ActiveSession(
            child_id=1,
            started_at=datetime.now() - timedelta(minutes=60),
            last_activity_at=datetime.now() - timedelta(minutes=1),
            status="ended"
        )
        db_session.add(active_session)
        db_session.commit()

        is_exceeded, remaining = usage_service.check_session_limit(1, db_session)

        # No active session, so should not be exceeded
        assert is_exceeded is False
        assert remaining == 30


class TestBlockedHours:
    """Test blocked hours checking."""

    def test_blocked_hours_not_blocked(self):
        """Test not blocked during allowed hours."""
        # Default blocked hours: 21:00 - 07:00
        usage_service = UsageService(
            blocked_hours_start=21,
            blocked_hours_end=7
        )

        # Test during daytime (e.g., 12:00)
        with patch('app.services.usage.datetime') as mock_datetime:
            mock_datetime.now.return_value = datetime(2024, 6, 15, 12, 0, 0)
            mock_datetime.side_effect = lambda *args, **kwargs: datetime(*args, **kwargs)

            is_blocked, remaining = usage_service.check_blocked_hours()

        assert is_blocked is False
        assert remaining == 0

    def test_blocked_hours_evening(self):
        """Test blocked during evening blocked hours."""
        usage_service = UsageService(
            blocked_hours_start=21,
            blocked_hours_end=7
        )

        # Test at 22:00 (should be blocked)
        with patch('app.services.usage.datetime') as mock_datetime:
            mock_now = datetime(2024, 6, 15, 22, 0, 0)

            def now_side_effect():
                return mock_now

            mock_datetime.now.return_value = mock_now
            mock_datetime.side_effect = lambda *args, **kwargs: datetime(*args, **kwargs)

            is_blocked, remaining = usage_service.check_blocked_hours()

        assert is_blocked is True
        assert remaining > 0  # Should have minutes until unblock

    def test_blocked_hours_early_morning(self):
        """Test blocked during early morning blocked hours."""
        usage_service = UsageService(
            blocked_hours_start=21,
            blocked_hours_end=7
        )

        # Test at 3:00 (should be blocked)
        with patch('app.services.usage.datetime') as mock_datetime:
            mock_now = datetime(2024, 6, 15, 3, 0, 0)

            def now_side_effect():
                return mock_now

            mock_datetime.now.return_value = mock_now
            mock_datetime.side_effect = lambda *args, **kwargs: datetime(*args, **kwargs)

            is_blocked, remaining = usage_service.check_blocked_hours()

        assert is_blocked is True


class TestActiveSessionTracking:
    """Test active session tracking."""

    def test_start_session(self, db_session):
        """Test starting a new active session."""
        usage_service = UsageService()

        session = usage_service.start_session(1, db_session)

        assert session is not None
        assert session.child_id == 1
        assert session.status == "active"
        assert session.started_at is not None

        # Verify it was saved to DB
        saved_session = db_session.query(ActiveSession).filter(
            ActiveSession.child_id == 1,
            ActiveSession.status == "active"
        ).first()
        assert saved_session is not None

    def test_start_session_ends_existing(self, db_session):
        """Test starting a new session ends existing active session."""
        usage_service = UsageService()

        # Create first session
        session1 = usage_service.start_session(1, db_session)
        first_session_id = session1.id

        # Create second session
        session2 = usage_service.start_session(1, db_session)

        # First session should be ended
        old_session = db_session.query(ActiveSession).filter(
            ActiveSession.id == first_session_id
        ).first()
        assert old_session.status == "ended"

        # New session should be active
        assert session2.status == "active"

    def test_update_session_activity(self, db_session):
        """Test updating session activity."""
        usage_service = UsageService()

        session = usage_service.start_session(1, db_session)
        original_activity = session.last_activity_at

        usage_service.update_session_activity(1, db_session)

        updated_session = db_session.query(ActiveSession).filter(
            ActiveSession.id == session.id
        ).first()

        # last_activity_at should be updated (may be same second)
        assert updated_session.last_activity_at >= original_activity

    def test_expire_session(self, db_session):
        """Test expiring an active session."""
        usage_service = UsageService()

        session = usage_service.start_session(1, db_session)

        usage_service.expire_session(1, db_session)

        expired_session = db_session.query(ActiveSession).filter(
            ActiveSession.id == session.id
        ).first()
        assert expired_session.status == "ended"

    def test_record_activity(self, db_session):
        """Test recording activity updates usage log."""
        usage_service = UsageService(daily_limit_minutes=60)

        # Record activity
        usage_service.record_activity(1, db_session, minutes=5)

        # Check usage log
        today = datetime.now().date()
        usage_log = db_session.query(UsageLog).filter(
            UsageLog.child_id == 1,
            UsageLog.date == today
        ).first()

        assert usage_log is not None
        assert usage_log.total_minutes == 5
        assert usage_log.message_count == 1

    def test_record_activity_updates_existing(self, db_session):
        """Test recording activity updates existing log."""
        usage_service = UsageService(daily_limit_minutes=60)

        today = datetime.now().date()

        # Create existing usage log
        existing_log = UsageLog(
            child_id=1,
            date=today,
            total_minutes=10,
            message_count=2
        )
        db_session.add(existing_log)
        db_session.commit()

        # Record more activity
        usage_service.record_activity(1, db_session, minutes=5)

        # Check updated log
        usage_log = db_session.query(UsageLog).filter(
            UsageLog.child_id == 1,
            UsageLog.date == today
        ).first()

        assert usage_log.total_minutes == 15
        assert usage_log.message_count == 3


class TestGetRemainingMinutes:
    """Test get_remaining_minutes method."""

    def test_get_remaining_minutes_no_limits(self, db_session):
        """Test remaining minutes when no usage."""
        usage_service = UsageService(
            daily_limit_minutes=60,
            session_limit_minutes=30
        )

        remaining = usage_service.get_remaining_minutes(1, db_session)

        # Should be limited by session limit (30)
        assert remaining == 30

    def test_get_remaining_minutes_daily_limit_reached(self, db_session):
        """Test remaining minutes when daily limit reached."""
        usage_service = UsageService(
            daily_limit_minutes=60,
            session_limit_minutes=30
        )

        # Set daily usage at limit
        today = datetime.now().date()
        usage_log = UsageLog(
            child_id=1,
            date=today,
            total_minutes=60,
            message_count=10
        )
        db_session.add(usage_log)
        db_session.commit()

        remaining = usage_service.get_remaining_minutes(1, db_session)

        assert remaining == 0

    def test_get_remaining_minutes_session_limit_reached(self, db_session):
        """Test remaining minutes when session limit reached."""
        usage_service = UsageService(
            daily_limit_minutes=60,
            session_limit_minutes=30
        )

        # Create session at limit
        active_session = ActiveSession(
            child_id=1,
            started_at=datetime.now() - timedelta(minutes=30),
            last_activity_at=datetime.now(),
            status="active"
        )
        db_session.add(active_session)
        db_session.commit()

        remaining = usage_service.get_remaining_minutes(1, db_session)

        assert remaining == 0