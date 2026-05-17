"""上下文服务 - 上下文组装 + Token预算裁剪。"""

from typing import List, Dict

from sqlalchemy.orm import Session

from app.models.character import AICharacter
from app.models.memory import KeyMemory, ConversationSummary
from app.models.message import Message
from app.config import settings


# 安全规则提示词（不可裁剪）
SAFETY_RULES_PROMPT = """



## 安全规则
- 禁止讨论暴力、赌博、色情等不当内容
- 禁止分享个人隐私信息（地址、电话、密码等）
- 如果孩子遇到危险或不适，及时提醒家长
"""


def count_tokens(text: str) -> int:
    """计算文本的token数量。

    使用简单估算：中文按每2字符1 token，英文按每4字符1 token。
    实际应用中可以使用 tiktoken 等精确库。

    Args:
        text: 输入文本

    Returns:
        估算的token数量
    """
    if not text:
        return 0

    chinese_chars = sum(1 for c in text if '一' <= c <= '鿿')
    english_chars = len(text) - chinese_chars

    # 中文约2字符/token，英文约4字符/token
    return chinese_chars // 2 + english_chars // 4 + 1


class ContextService:
    """上下文管理服务。

    负责按优先级组装上下文，并在Token预算内进行裁剪。
    优先级顺序（不可裁剪 -> 低优先级）：
    1. 系统提示词（不可裁剪）
    2. 关键记忆（高优先级）
    3. 对话摘要（中优先级）
    4. 最近消息（低优先级，可裁剪）
    """

    def build_context(
        self,
        db: Session,
        child_id: int,
        conversation_id: int,
        character_id: int,
        user_text: str
    ) -> List[Dict[str, str]]:
        """构建上下文消息列表，超Token预算时自动裁剪。

        Args:
            db: 数据库会话
            child_id: 儿童ID
            conversation_id: 对话ID
            character_id: AI角色ID
            user_text: 用户输入文本

        Returns:
            上下文字典列表，每项包含 role 和 content
        """
        # 1. 系统提示词（不可裁剪）
        character = db.query(AICharacter).filter(
            AICharacter.id == character_id
        ).first()

        if not character:
            system_prompt = "你是一个友好的AI儿童伴侣，用温柔有趣的方式和孩子交流。"
        else:
            system_prompt = character.system_prompt + SAFETY_RULES_PROMPT

        system_tokens = count_tokens(system_prompt)

        # 2. 关键记忆
        memories = db.query(KeyMemory).filter(
            KeyMemory.child_id == child_id
        ).all()

        if memories:
            memory_text = "关于这个孩子的信息：\n" + "\n".join([
                f"- {m.key}: {m.value}" for m in memories
            ])
        else:
            memory_text = ""

        # 3. 对话摘要
        summary = db.query(ConversationSummary).filter(
            ConversationSummary.conversation_id == conversation_id
        ).first()

        summary_text = summary.summary_text if summary else ""

        # 4. 最近消息（最近10条，按时间正序）
        recent = db.query(Message).filter(
            Message.conversation_id == conversation_id
        ).order_by(Message.timestamp.desc()).limit(10).all()

        recent.reverse()
        recent_text = "\n".join([
            f"{m.role}: {m.content}" for m in recent
        ]) if recent else ""

        # 5. Token预算计算
        user_tokens = count_tokens(user_text)
        budget = settings.context_window_tokens - system_tokens - user_tokens - settings.max_llm_tokens

        # 预算不足时，至少保证能处理请求
        if budget < 0:
            budget = 1000

        # 按优先级构建上下文
        result = [{"role": "system", "content": system_prompt}]

        # 计算各部分token
        memory_tokens = count_tokens(memory_text)
        summary_tokens = count_tokens(summary_text)
        recent_tokens = count_tokens(recent_text)

        # 按优先级加入上下文，超预算则裁剪
        remaining_budget = budget

        # 优先级1：关键记忆
        if memory_tokens <= remaining_budget and memory_text:
            result.append({"role": "system", "content": memory_text})
            remaining_budget -= memory_tokens
        elif memory_text:
            # 裁剪记忆文本
            trimmed_memory = self._trim_to_budget(memory_text, remaining_budget)
            if trimmed_memory:
                result.append({"role": "system", "content": trimmed_memory})
                remaining_budget -= count_tokens(trimmed_memory)

        # 优先级2：对话摘要
        if summary_tokens <= remaining_budget and summary_text:
            result.append({"role": "system", "content": f"对话摘要：{summary_text}"})
            remaining_budget -= summary_tokens
        elif summary_text:
            trimmed_summary = self._trim_to_budget(summary_text, remaining_budget)
            if trimmed_summary:
                result.append({"role": "system", "content": f"对话摘要：{trimmed_summary}"})
                remaining_budget -= count_tokens(trimmed_summary)

        # 优先级3：最近消息
        if recent_tokens <= remaining_budget and recent_text:
            for msg in recent:
                result.append({"role": msg.role, "content": msg.content})
        elif recent_text:
            trimmed_recent = self._trim_to_budget(recent_text, remaining_budget)
            if trimmed_recent:
                for msg_text in trimmed_recent.split("\n"):
                    if msg_text.strip():
                        parts = msg_text.split(": ", 1)
                        if len(parts) == 2:
                            result.append({"role": parts[0], "content": parts[1]})

        # 最后添加用户消息
        result.append({"role": "user", "content": user_text})

        return result

    def _trim_to_budget(self, text: str, max_tokens: int) -> str:
        """将文本裁剪到指定token预算内。

        Args:
            text: 原始文本
            max_tokens: 最大token数

        Returns:
            裁剪后的文本
        """
        tokens = count_tokens(text)
        if tokens <= max_tokens:
            return text

        # 二分搜索找到合适的截断位置
        chars_to_keep = int(max_tokens * 2)  # 简单估算：2字符≈1 token

        if chars_to_keep >= len(text):
            return text

        return text[:chars_to_keep] + "..."