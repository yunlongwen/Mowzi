from sqlalchemy import Column, Integer, String, DateTime, func

from app.database import Base


class ChildProfile(Base):
    __tablename__ = "child_profiles"

    id = Column(Integer, primary_key=True, index=True)
    name = Column(String, nullable=False)
    device_id = Column(String, unique=True, nullable=False, index=True)
    created_at = Column(DateTime, server_default=func.now())