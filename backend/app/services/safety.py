"""内容安全检查服务。"""

from typing import Dict, List


class SafetyService:
    """内容安全检查管道。"""

    # Basic Chinese profanity list (minimal for demonstration)
    BLOCKED_WORDS = [
        "滚开", "去死", "笨蛋", "白痴", "智障", "傻子",
        "废物", "垃圾", "讨厌", "烦人", "王八蛋"
    ]

    def __init__(self, blocked_words: List[str] = None):
        """初始化安全服务。

        Args:
            blocked_words: 自定义屏蔽词列表
        """
        self.blocked_words = blocked_words or self.BLOCKED_WORDS

    def check_content(self, text: str) -> Dict[str, any]:
        """检查文本内容是否安全。

        Args:
            text: 待检查文本

        Returns:
            {"safe": bool, "flagged_words": List[str], "message": str}
        """
        if not text or not text.strip():
            return {"safe": True, "flagged_words": [], "message": ""}

        flagged = []
        text_lower = text.lower()

        for word in self.blocked_words:
            if word in text_lower:
                flagged.append(word)

        if flagged:
            return {
                "safe": False,
                "flagged_words": flagged,
                "message": "内容包含不当词汇"
            }

        return {"safe": True, "flagged_words": [], "message": ""}