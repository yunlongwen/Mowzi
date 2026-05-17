"""LLM streaming service with sentence splitting."""

import re
from typing import AsyncGenerator, List, Dict, Tuple
from openai import AsyncOpenAI


class SentenceSplitter:
    """从LLM流式输出中提取完整句子。"""
    SENTENCE_ENDINGS = re.compile(r'[。！？…\.\!\?]')

    def __init__(self):
        self.buffer = ""

    def add_chunk(self, chunk: str) -> List[str]:
        """添加文本片段，返回已完成的句子列表。"""
        self.buffer += chunk
        sentences = []
        while True:
            match = self.SENTENCE_ENDINGS.search(self.buffer)
            if not match:
                break
            end_pos = match.end()
            sentence = self.buffer[:end_pos].strip()
            if sentence:
                sentences.append(sentence)
            self.buffer = self.buffer[end_pos:]
        return sentences

    def flush(self) -> str:
        """返回缓冲区剩余文本。"""
        remaining = self.buffer.strip()
        self.buffer = ""
        return remaining if remaining else ""


class LLMService:
    """LLM流式调用服务。"""

    def __init__(self, api_url: str, api_key: str, model: str):
        self.client = AsyncOpenAI(base_url=api_url, api_key=api_key)
        self.model = model

    async def single_call(self, prompt: str, max_tokens: int = 300) -> str:
        """单次LLM调用（非流式），用于记忆提取和摘要等场景。

        Args:
            prompt: 提示词
            max_tokens: 最大返回token数

        Returns:
            LLM回复的文本内容
        """
        response = await self.client.chat.completions.create(
            model=self.model,
            messages=[{"role": "user", "content": prompt}],
            max_tokens=max_tokens,
            stream=False
        )
        return response.choices[0].message.content

    async def stream_chat(
        self,
        messages: List[Dict[str, str]],
        max_tokens: int = 300
    ) -> AsyncGenerator[str, None]:
        """流式调用LLM，yield每个文本片段。"""
        stream = await self.client.chat.completions.create(
            model=self.model,
            messages=messages,
            max_tokens=max_tokens,
            stream=True
        )
        async for chunk in stream:
            if chunk.choices[0].delta.content:
                yield chunk.choices[0].delta.content