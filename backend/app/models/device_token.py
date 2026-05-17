from sqlalchemy import Column, Integer, String, DateTime, ForeignKey, func

from app.database import Base


class DeviceToken(Base):
    __tablename__ = "device_tokens"

    id = Column(Integer, primary_key=True, index=True)
    token = Column(String, unique=True, nullable=False, index=True)
    child_id = Column(Integer, ForeignKey("child_profiles.id"), nullable=False, index=True)
    device_id = Column(String, nullable=False)
    created_at = Column(DateTime, server_default=func.now())