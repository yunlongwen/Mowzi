from sqlalchemy import Column, Integer, String, Time, DateTime

from app.database import Base


class ParentSettings(Base):
    __tablename__ = "parent_settings"

    id = Column(Integer, primary_key=True, default=1)  # Fixed id=1
    pin_hash = Column(String, nullable=False)
    pin_attempts = Column(Integer, default=0, nullable=False)
    pin_locked_until = Column(DateTime, nullable=True)
    daily_limit_min = Column(Integer, nullable=True)
    session_limit_min = Column(Integer, nullable=True)
    blocked_hours_start = Column(Time, nullable=True)
    blocked_hours_end = Column(Time, nullable=True)
    llm_api_url = Column(String, nullable=True)
    llm_api_key = Column(String, nullable=True)  # Encrypted
    llm_model = Column(String, nullable=True)
    xfyun_app_id = Column(String, nullable=True)
    xfyun_api_key = Column(String, nullable=True)  # Encrypted
    xfyun_api_secret = Column(String, nullable=True)  # Encrypted