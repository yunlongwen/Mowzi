"""使用时长追踪服务。"""

from datetime import datetime, timedelta
from typing import Tuple, Optional
from sqlalchemy.orm import Session

from app.models.usage import UsageLog
from app.models.session import ActiveSession
from app.models.child import ChildProfile


class UsageService:
    """使用时长追踪服务。"""

    # 默认配置
    DEFAULT_DAILY_LIMIT_MINUTES = 60  # 每日限制60分钟
    DEFAULT_SESSION_LIMIT_MINUTES = 30  # 单次会话限制30分钟
    DEFAULT_BLOCKED_HOURS_START = 21  # 晚上9点开始禁用
    DEFAULT_BLOCKED_HOURS_END = 7  # 早上7点结束禁用

    def __init__(
        self,
        daily_limit_minutes: int = None,
        session_limit_minutes: int = None,
        blocked_hours_start: int = None,
        blocked_hours_end: int = None
    ):
        """初始化使用时长服务。

        Args:
            daily_limit_minutes: 每日时长限制（分钟）
            session_limit_minutes: 单次会话时长限制（分钟）
            blocked_hours_start: 禁用时段开始小时（0-23）
            blocked_hours_end: 禁用时段结束小时（0-23）
        """
        self.daily_limit_minutes = daily_limit_minutes or self.DEFAULT_DAILY_LIMIT_MINUTES
        self.session_limit_minutes = session_limit_minutes or self.DEFAULT_SESSION_LIMIT_MINUTES
        self.blocked_hours_start = blocked_hours_start or self.DEFAULT_BLOCKED_HOURS_START
        self.blocked_hours_end = blocked_hours_end or self.DEFAULT_BLOCKED_HOURS_END

    def record_activity(self, child_id: int, db: Session, minutes: int = 1) -> None:
        """记录活动，更新累计时长。

        Args:
            child_id: 儿童ID
            db: 数据库会话
            minutes: 增加的分钟数
        """
        today = datetime.now().date()

        # 查找或创建今日的使用记录
        usage_log = db.query(UsageLog).filter(
            UsageLog.child_id == child_id,
            UsageLog.date == today
        ).first()

        if usage_log:
            usage_log.total_minutes += minutes
            usage_log.message_count += 1
        else:
            usage_log = UsageLog(
                child_id=child_id,
                date=today,
                total_minutes=minutes,
                message_count=1
            )
            db.add(usage_log)

        db.commit()

    def check_daily_limit(self, child_id: int, db: Session) -> Tuple[bool, int]:
        """检查是否超过每日时长限制。

        Args:
            child_id: 儿童ID
            db: 数据库会话

        Returns:
            (是否超限, 剩余分钟数)
        """
        today = datetime.now().date()

        usage_log = db.query(UsageLog).filter(
            UsageLog.child_id == child_id,
            UsageLog.date == today
        ).first()

        if usage_log:
            used_minutes = usage_log.total_minutes
        else:
            used_minutes = 0

        remaining = max(0, self.daily_limit_minutes - used_minutes)
        is_exceeded = used_minutes >= self.daily_limit_minutes

        return is_exceeded, remaining

    def check_session_limit(self, child_id: int, db: Session) -> Tuple[bool, int]:
        """检查单次会话时长限制。

        Args:
            child_id: 儿童ID
            db: 数据库会话

        Returns:
            (是否超限, 剩余分钟数)
        """
        # 查找该儿童的活跃会话
        active_session = db.query(ActiveSession).filter(
            ActiveSession.child_id == child_id,
            ActiveSession.status == "active"
        ).first()

        if not active_session:
            # 没有活跃会话，不受限
            return False, self.session_limit_minutes

        # 计算会话已持续时间
        elapsed = datetime.now() - active_session.started_at
        elapsed_minutes = int(elapsed.total_seconds() / 60)

        remaining = max(0, self.session_limit_minutes - elapsed_minutes)
        is_exceeded = elapsed_minutes >= self.session_limit_minutes

        return is_exceeded, remaining

    def check_blocked_hours(self) -> Tuple[bool, int]:
        """检查是否处于禁用时段。

        Returns:
            (是否禁用, 剩余分钟数)
        """
        now = datetime.now()
        current_hour = now.hour

        # 判断是否在禁用时段
        # 例如：21:00 - 07:00 表示晚上9点到次日早上7点
        if self.blocked_hours_start > self.blocked_hours_end:
            # 跨午夜的情况，例如 21:00 到 07:00
            is_blocked = current_hour >= self.blocked_hours_start or current_hour < self.blocked_hours_end
        else:
            # 同一天的情况
            is_blocked = self.blocked_hours_start <= current_hour < self.blocked_hours_end

        if is_blocked:
            # 计算距离解除禁用的分钟数
            if current_hour >= self.blocked_hours_start:
                # 当前在禁时段的开始之后，计算到次日结束
                blocked_end = datetime(now.year, now.month, now.day, self.blocked_hours_end)
                if self.blocked_hours_start > self.blocked_hours_end:
                    # 跨午夜，需要加一天
                    blocked_end += timedelta(days=1)
                minutes_until_unblock = int((blocked_end - now).total_seconds() / 60)
            else:
                # 当前在禁时段的开始之前
                blocked_end = datetime(now.year, now.month, now.day, self.blocked_hours_end)
                minutes_until_unblock = int((blocked_end - now).total_seconds() / 60)

            return True, max(0, minutes_until_unblock)

        return False, 0

    def start_session(self, child_id: int, db: Session) -> ActiveSession:
        """开始一个新的活跃会话。

        Args:
            child_id: 儿童ID
            db: 数据库会话

        Returns:
            ActiveSession 对象
        """
        # 先结束该儿童的所有活跃会话
        existing = db.query(ActiveSession).filter(
            ActiveSession.child_id == child_id,
            ActiveSession.status == "active"
        ).all()
        for session in existing:
            session.status = "ended"

        # 创建新会话
        session = ActiveSession(
            child_id=child_id,
            started_at=datetime.now(),
            last_activity_at=datetime.now(),
            status="active"
        )
        db.add(session)
        db.commit()
        db.refresh(session)
        return session

    def update_session_activity(self, child_id: int, db: Session) -> None:
        """更新会话的最后活跃时间。

        Args:
            child_id: 儿童ID
            db: 数据库会话
        """
        active_session = db.query(ActiveSession).filter(
            ActiveSession.child_id == child_id,
            ActiveSession.status == "active"
        ).first()

        if active_session:
            active_session.last_activity_at = datetime.now()
            db.commit()

    def expire_session(self, child_id: int, db: Session) -> None:
        """结束一个活跃会话。

        Args:
            child_id: 儿童ID
            db: 数据库会话
        """
        active_session = db.query(ActiveSession).filter(
            ActiveSession.child_id == child_id,
            ActiveSession.status == "active"
        ).first()

        if active_session:
            active_session.status = "ended"
            db.commit()

    def get_remaining_minutes(self, child_id: int, db: Session) -> int:
        """获取儿童剩余的使用分钟数。

        Args:
            child_id: 儿童ID
            db: 数据库会话

        Returns:
            剩余分钟数（取每日限制和会话限制中的最小值）
        """
        # 检查每日限制
        daily_exceeded, daily_remaining = self.check_daily_limit(child_id, db)

        # 检查会话限制
        session_exceeded, session_remaining = self.check_session_limit(child_id, db)

        # 返回两者中的最小值
        return min(daily_remaining, session_remaining)