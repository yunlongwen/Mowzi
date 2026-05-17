from sqlalchemy import Column, Integer, String, DateTime, ForeignKey, func
from sqlalchemy.orm import relationship

from app.database import Base


class Conversation(Base):
    __tablename__ = "conversations"

    id = Column(Integer, primary_key=True, index=True)
    child_id = Column(Integer, ForeignKey("child_profiles.id"), nullable=False, index=True)
    character_id = Column(Integer, ForeignKey("ai_characters.id"), nullable=False, index=True)
    title = Column(String, nullable=True)
    status = Column(String, nullable=False, default="active")  # "active"|"idle"|"archived"
    created_at = Column(DateTime, server_default=func.now())
    updated_at = Column(DateTime, server_default=func.now(), onupdate=func.now())
    last_message_at = Column(DateTime, server_default=func.now(), onupdate=func.now())

    child = relationship("ChildProfile", backref="conversations")
    character = relationship("AICharacter", backref="conversations")