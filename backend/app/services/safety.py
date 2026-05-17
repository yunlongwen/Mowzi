"""内容安全检查服务。"""

from typing import Dict, List


class SafetyService:
    """内容安全检查管道。"""

    # 敏感词列表（暴力/恐怖/色情相关，中文为主）
    BLOCKED_WORDS = [
        # 暴力威胁
        "杀人", "杀死", "杀", "砍死", "打死", "捅死", "勒死", "溺死",
        "打架", "揍人", "打人", "砍人", "捅人", "虐待", "暴力",
        # 死亡威胁
        "去死", "该死", "要死", "死了", "死掉", "自杀", "轻生",
        # 恐怖内容
        "鬼", "吓人", "恐怖", "血腥", "尸体", "血", "断头", "分尸",
        # 脏话/粗口
        "滚开", "笨蛋", "白痴", "智障", "傻子", "废物", "垃圾",
        "王八蛋", "混蛋", "该死的", "他妈的", "妈的", "操", "肏",
        # 不良行为
        "偷东西", "偷钱", "抢劫", "偷窃", "作弊", "撒谎", "骗人",
    ]

    # 组合敏感词（更复杂的检测）
    BLOCKED_PATTERNS = [
        "我要杀", "我要打", "我要砍", "杀你了", "打死你",
        "弄死你", "干掉你", "滚蛋", "去死吧",
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

        # 检查单个敏感词
        for word in self.blocked_words:
            if word in text_lower:
                flagged.append(word)

        # 检查敏感模式组合
        for pattern in self.BLOCKED_PATTERNS:
            if pattern in text_lower:
                flagged.append(pattern)

        if flagged:
            return {
                "safe": False,
                "flagged_words": list(set(flagged)),  # 去重
                "message": "内容包含不当词汇"
            }

        return {"safe": True, "flagged_words": [], "message": ""}

    def contains_blocked_content(self, text: str) -> bool:
        """检测文本是否包含敏感词。

        Args:
            text: 待检测文本

        Returns:
            True if contains blocked content, False otherwise
        """
        if not text or not text.strip():
            return False

        text_lower = text.lower()

        # 检查单个敏感词
        for word in self.blocked_words:
            if word in text_lower:
                return True

        # 检查敏感模式组合
        for pattern in self.BLOCKED_PATTERNS:
            if pattern in text_lower:
                return True

        return False

    def filter_content(self, text: str) -> str:
        """过滤敏感词，将敏感词替换为 ***（保留长度提示）。

        Args:
            text: 待过滤文本

        Returns:
            过滤后的文本，敏感词被替换为 ***（保留原词长度）
        """
        if not text or not text.strip():
            return text

        result = text

        # 替换单个敏感词（保留长度）
        for word in self.blocked_words:
            if word in result:
                result = result.replace(word, "*" * len(word))

        # 替换敏感模式组合（使用固定长度 ***）
        for pattern in self.BLOCKED_PATTERNS:
            if pattern in result:
                result = result.replace(pattern, "***")

        return result