from sqlalchemy import Column, Integer, String, Text, DateTime, ForeignKey, func

from app.database import Base


class Message(Base):
    __tablename__ = "messages"

    id = Column(Integer, primary_key=True, index=True)
    conversation_id = Column(Integer, ForeignKey("conversations.id"), nullable=False, index=True)
    role = Column(String, nullable=False)  # "user"|"assistant"|"system"
    content = Column(Text, nullable=False)
    audio_path = Column(String, nullable=True)
    timestamp = Column(DateTime, server_default=func.now())