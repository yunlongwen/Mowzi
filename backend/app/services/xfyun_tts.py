"""讯飞TTS WebSocket代理服务。"""

import base64
import hashlib
import hmac
import json
import time
from typing import Tuple
import websockets


class XfyunTTSService:
    """讯飞TTS WebSocket代理。串行调用（免费版2路并发限制）。"""

    def __init__(self, app_id: str, api_key: str, api_secret: str):
        self.app_id = app_id
        self.api_key = api_key
        self.api_secret = api_secret

    async def synthesize(self, text: str, voice_name: str = "xiaoyan") -> Tuple[str, int]:
        """合成单句语音，返回 (mp3_base64, duration_ms)。

        Args:
            text: 要合成的文本
            voice_name: 语音名称（默认xiaoyan）

        Returns:
            (base64编码的mp3音频, 持续时间毫秒)
        """
        url = self._build_url()
        audio_chunks = []

        async with websockets.connect(url) as ws:
            payload = {
                "common": {"app_id": self.app_id},
                "business": {
                    "aue": "lame",  # MP3格式
                    "sfl": 1,        # 流式返回
                    "auf": "audio/L16;rate=16000",
                    "vcn": voice_name,
                    "speed": 50,
                    "volume": 50,
                    "pitch": 50,
                },
                "data": {
                    "status": 2,
                    "text": base64.b64encode(text.encode("utf-8")).decode()
                }
            }
            await ws.send(json.dumps(payload))

            while True:
                response = json.loads(await ws.recv())
                code = response.get("code", -1)
                if code != 0:
                    raise Exception(f"TTS错误: {code}")
                data = response.get("data", {})
                if data.get("audio"):
                    audio_chunks.append(base64.b64decode(data["audio"]))
                if data.get("status") == 2:
                    break

        audio_data = b"".join(audio_chunks)
        # 16kHz * 16bit = 32KB/s, duration_ms = audio_size / 32
        duration_ms = int(len(audio_data) / 32)
        return base64.b64encode(audio_data).decode(), duration_ms

    def _build_url(self) -> str:
        """构建讯飞TTS WebSocket URL。"""
        ts = str(int(time.time()))
        host = "tts-api.xfyun.cn"
        signature_origin = f"host: {host}\r\ndate: {ts}\r\nGET /v3/tts HTTP/1.1"
        signature = hmac.new(
            self.api_secret.encode(),
            signature_origin.encode(),
            hashlib.sha256
        ).digest()
        auth = base64.b64encode(signature).decode()
        return f"wss://tts-api.xfyun.cn/v3/tts?authorization={auth}&date={ts}&host={host}"