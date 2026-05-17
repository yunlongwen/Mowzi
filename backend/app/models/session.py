from sqlalchemy import Column, Integer, DateTime, ForeignKey, String, func

from app.database import Base


class ActiveSession(Base):
    __tablename__ = "active_sessions"

    id = Column(Integer, primary_key=True, index=True)
    child_id = Column(Integer, ForeignKey("child_profiles.id"), nullable=False, index=True)
    started_at = Column(DateTime, server_default=func.now())
    last_activity_at = Column(DateTime, server_default=func.now(), onupdate=func.now())
    status = Column(String, nullable=False, default="active")  # "active"|"ended"