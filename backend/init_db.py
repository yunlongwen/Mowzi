import sys
import os

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from sqlalchemy import text
from app.database import engine, Base
from app.models import (
    ChildProfile,
    Conversation,
    Message,
    AICharacter,
    ParentSettings,
    KeyMemory,
    ConversationSummary,
    ActiveSession,
    UsageLog,
)


def init_db():
    print("Creating tables...")
    Base.metadata.create_all(bind=engine)

    with engine.connect() as conn:
        conn.execute(text("DELETE FROM ai_characters"))
        conn.commit()

    print("Inserting default AI characters...")

    characters = [
        {
            "name": "猫头鹰医生",
            "avatar_url": None,
            "description": "知识渊博、耐心、温和",
            "system_prompt": """你是一个博学多才、和蔼可亲的猫头鹰医生。你总是耐心倾听，用温暖的声音回答孩子们的问题。你善于用简单的语言解释复杂的事物，让学习变得有趣。你是孩子们的好朋友和好老师。安全约束：你永远不会提供任何可能伤害儿童的内容，你会拒绝回答任何涉及暴力、色情、政治或仇恨的问题。""",
            "tts_voice_name": "xiaoyan",
        },
        {
            "name": "故事兔兔",
            "avatar_url": None,
            "description": "温暖、富有想象力、善于讲故事",
            "system_prompt": """你是一个温暖可爱、想象力丰富的故事兔兔。你最喜欢给小朋友们讲各种奇妙有趣的童话故事，你的声音甜美温柔。你会根据小朋友的喜好即兴发挥创作故事。安全约束：你永远不会提供任何可能伤害儿童的内容，你会拒绝回答任何涉及暴力、色情、政治或仇恨的问题。""",
            "tts_voice_name": "aisjiuxu",
        },
        {
            "name": "搞笑机器人",
            "avatar_url": None,
            "description": "幽默、充满活力、爱开玩笑",
            "system_prompt": """你是一个幽默搞笑、充满活力的机器人！你喜欢讲笑话、说趣事，让孩子们开心大笑。你活力四射，说话有趣，是孩子们的开心果。但你也会在适当的时候认真回答问题。安全约束：你永远不会提供任何可能伤害儿童的内容，你会拒绝回答任何涉及暴力、色情、政治或仇恨的问题。""",
            "tts_voice_name": "yanyu",
        },
        {
            "name": "冒险猫咪",
            "avatar_url": None,
            "description": "勇敢、好奇、热爱冒险",
            "system_prompt": """你是一个勇敢无畏、充满好奇心的冒险猫咪！你喜欢和小朋友们一起探索世界的奥秘，分享各种有趣的冒险故事。你的声音年轻有活力，充满好奇心和探险精神。安全约束：你永远不会提供任何可能伤害儿童的内容，你会拒绝回答任何涉及暴力、色情、政治或仇恨的问题。""",
            "tts_voice_name": "aisxping",
        },
    ]

    with engine.connect() as conn:
        for char in characters:
            conn.execute(
                text("""INSERT INTO ai_characters (name, avatar_url, description, system_prompt, tts_voice_name) VALUES (:name, :avatar_url, :description, :system_prompt, :tts_voice_name)"""),
                char,
            )
        conn.commit()

    print("Database initialized successfully!")
    print("4 default AI characters inserted.")


if __name__ == "__main__":
    init_db()