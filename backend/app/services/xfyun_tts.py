"""讯飞TTS WebSocket服务。"""

import base64
import hashlib
import hmac
import json
from datetime import datetime, timezone
from typing import Tuple
from urllib.parse import urlencode
import websockets


class XfyunTTSService:
    """讯飞TTS WebSocket服务。串行调用（免费版2路并发限制）。"""

    def __init__(self, app_id: str, api_key: str, api_secret: str):
        self.app_id = app_id
        self.api_key = api_key
        self.api_secret = api_secret

    async def synthesize(self, text: str, voice_name: str = "xiaoyan") -> Tuple[str, int]:
        """合成单句语音，返回 (mp3_base64, duration_ms)。"""
        url = self._build_url()
        audio_chunks = []

        async with websockets.connect(url) as ws:
            payload = {
                "common": {"app_id": self.app_id},
                "business": {
                    "aue": "lame",
                    "sfl": 1,
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
                    raise Exception(f"TTS错误: {code}, {response.get('message', '')}")
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
        """构建讯飞TTS WebSocket鉴权URL。"""
        host = "tts-api.xfyun.cn"
        path = "/v2/tts"

        now = datetime.now(timezone.utc)
        date = now.strftime('%a, %d %b %Y %H:%M:%S GMT')

        signature_origin = f"host: {host}\ndate: {date}\nGET {path} HTTP/1.1"
        signature = hmac.new(
            self.api_secret.encode('utf-8'),
            signature_origin.encode('utf-8'),
            digestmod=hashlib.sha256
        ).digest()
        signature_b64 = base64.b64encode(signature).decode('utf-8')

        authorization_origin = (
            f'api_key="{self.api_key}", '
            f'algorithm="hmac-sha256", '
            f'headers="host date request-line", '
            f'signature="{signature_b64}"'
        )
        authorization = base64.b64encode(authorization_origin.encode('utf-8')).decode('utf-8')

        params = {"authorization": authorization, "date": date, "host": host}
        return f"wss://{host}{path}?{urlencode(params)}"
