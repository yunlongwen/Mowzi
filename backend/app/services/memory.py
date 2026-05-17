"""记忆服务 - 关键记忆提取 + 对话摘要。"""

import json
from datetime import datetime
from uuid import uuid4

from sqlalchemy.orm import Session

from app.models.memory import KeyMemory, ConversationSummary
from app.models.message import Message
from app.models.conversation import Conversation


class MemoryService:
    """记忆管理服务。

    负责从对话中提取关键儿童信息，并对长对话进行摘要压缩。
    """

    EXTRACTION_PROMPT = """从以下对话中提取关于儿童的重要信息。
返回JSON格式，只包含确定的信息，不要猜测。
示例：{{"name": "小明", "favorite_animal": "恐龙", "has_pet": "是的，一只叫旺财的小狗"}}
如果没有新信息，返回空对象 {{}}。

对话内容：
{conversation_text}"""

    def __init__(self, llm_service):
        """初始化记忆服务。

        Args:
            llm_service: LLM服务实例，用于调用单次LLM接口进行记忆提取
        """
        self.llm_service = llm_service

    async def extract_memories(self, db: Session, child_id: int, recent_messages: list[Message]) -> None:
        """从最近对话中提取关键记忆。

        Args:
            db: 数据库会话
            child_id: 儿童ID
            recent_messages: 最近的消息列表（通常5轮对话）
        """
        if len(recent_messages) < 5:
            return

        # 构建对话文本
        text = "\n".join([f"{m.role}: {m.content}" for m in recent_messages])

        # 调用LLM提取记忆
        response = await self.llm_service.single_call(
            self.EXTRACTION_PROMPT.format(conversation_text=text)
        )

        try:
            new_memories = json.loads(response)
        except json.JSONDecodeError:
            return

        if not new_memories or not isinstance(new_memories, dict):
            return

        # 更新或创建记忆
        for key, value in new_memories.items():
            existing = db.query(KeyMemory).filter(
                KeyMemory.child_id == child_id,
                KeyMemory.key == key
            ).first()

            if existing:
                existing.value = str(value)
                existing.updated_at = datetime.utcnow()
            else:
                db.add(KeyMemory(
                    id=str(uuid4()),
                    child_id=child_id,
                    key=key,
                    value=str(value)
                ))

        db.commit()

    async def summarize_conversation(self, db: Session, conversation_id: int) -> None:
        """对长对话进行摘要压缩。

        当对话超过20条消息时，使用LLM对较早的消息进行摘要。
        使用乐观锁机制：如果摘要的消息数量与当前消息数量一致，则跳过。

        Args:
            db: 数据库会话
            conversation_id: 对话ID
        """
        # 获取对话
        conversation = db.query(Conversation).filter(
            Conversation.id == conversation_id
        ).first()

        if not conversation:
            return

        # 获取按时间排序的所有消息
        messages = db.query(Message).filter(
            Message.conversation_id == conversation_id
        ).order_by(Message.timestamp.asc()).all()

        # 消息少于20条，不进行摘要
        if len(messages) < 20:
            return

        # 检查是否已有摘要（乐观锁）
        summary = db.query(ConversationSummary).filter(
            ConversationSummary.conversation_id == conversation_id
        ).first()

        # 如果有摘要且消息数量一致，说明摘要已更新
        if summary and summary.message_count_at_summary == len(messages):
            return

        # 保留最近10条消息，对较早的消息进行摘要
        older_messages = messages[:-10]
        older_text = "\n".join([
            f"{m.role}: {m.content}" for m in older_messages
        ])

        # 调用LLM生成摘要
        summary_text = await self.llm_service.single_call(
            f"请简要总结以下对话的主要内容，保持关键信息：\n{older_text}"
        )

        if summary:
            # 更新现有摘要
            summary.summary_text = summary_text
            summary.message_count_at_summary = len(messages)
            summary.updated_at = datetime.utcnow()
        else:
            # 创建新摘要
            db.add(ConversationSummary(
                id=str(uuid4()),
                conversation_id=conversation_id,
                summary_text=summary_text,
                message_count_at_summary=len(messages),
                updated_at=datetime.utcnow()
            ))

        db.commit()