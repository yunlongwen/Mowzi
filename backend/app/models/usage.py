from sqlalchemy import Column, Integer, Date, DateTime, ForeignKey, func

from app.database import Base


class UsageLog(Base):
    __tablename__ = "usage_logs"

    id = Column(Integer, primary_key=True, index=True)
    child_id = Column(Integer, ForeignKey("child_profiles.id"), nullable=False, index=True)
    date = Column(Date, nullable=False, index=True)
    total_minutes = Column(Integer, nullable=False, default=0)
    message_count = Column(Integer, nullable=False, default=0)