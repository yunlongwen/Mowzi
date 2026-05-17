from sqlalchemy import Column, Integer, String, Text

from app.database import Base


class AICharacter(Base):
    __tablename__ = "ai_characters"

    id = Column(Integer, primary_key=True, index=True)
    name = Column(String, nullable=False)
    avatar_url = Column(String, nullable=True)
    description = Column(Text, nullable=True)
    system_prompt = Column(Text, nullable=False)
    tts_voice_name = Column(String, nullable=False)