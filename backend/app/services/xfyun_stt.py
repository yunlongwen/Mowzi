"""XFYUN Speech-to-Text WebSocket service."""

import hashlib
import hmac
import base64
import json
import time
import websockets
from typing import Tuple


class XfyunSTTService:
    """IFLYTEK STT WebSocket client for speech recognition."""

    def __init__(self, app_id: str, api_key: str, api_secret: str):
        self.app_id = app_id
        self.api_key = api_key
        self.api_secret = api_secret
        self.url = "wss://iat-api.xfyun.cn/v2/iat"

    def _generate_signature(self) -> Tuple[str, str]:
        """Generate HMAC-SHA256 signature for WebSocket auth.

        Returns:
            Tuple of (authorization, timestamp)
        """
        timestamp = str(int(time.time()))
        signature_origin = f"host: iat-api.xfyun.cn\ndate: {timestamp}\nGET /v2/iat HTTP/1.1"
        signature = hmac.new(
            self.api_secret.encode(),
            signature_origin.encode(),
            hashlib.sha256
        ).digest()
        authorization = base64.b64encode(signature).decode()
        return authorization, timestamp

    async def recognize(self, audio_data: bytes, format: str = "opus") -> Tuple[str, float]:
        """Send audio to IFLYTEK STT and return recognized text with confidence.

        Args:
            audio_data: Raw audio bytes (Opus or PCM 16kHz)
            format: Audio format ("opus" or "pcm")

        Returns:
            Tuple of (recognized_text, average_confidence)
        """
        authorization, timestamp = self._generate_signature()
        url = f"{self.url}?authorization={authorization}&date={timestamp}&host=iat-api.xfyun.cn"

        result_text = ""
        confidence_sum = 0.0
        result_count = 0

        async with websockets.connect(url) as ws:
            frame_size = 1280
            offset = 0
            status = 0  # First frame

            while offset < len(audio_data):
                frame = audio_data[offset:offset + frame_size]
                if offset + frame_size >= len(audio_data):
                    status = 2  # Last frame

                payload = {
                    "common": {"app_id": self.app_id},
                    "business": {
                        "language": "zh_cn",
                        "domain": "iat",
                        "accent": "mandarin",
                        "dwa": "wpgs",
                    },
                    "data": {
                        "status": status,
                        "format": "audio/opus" if format == "opus" else "audio/L16;rate=16000",
                        "encoding": "raw",
                        "audio": base64.b64encode(frame).decode()
                    }
                }
                await ws.send(json.dumps(payload))

                response = json.loads(await ws.recv())

                code = response.get("code", -1)
                if code != 0:
                    raise Exception(f"IFLYTEK STT error: code={code}")

                data = response.get("data", {})
                if data.get("result"):
                    ws_list = data["result"].get("ws", [])
                    for ws_item in ws_list:
                        for cw in ws_item.get("cw", []):
                            result_text += cw.get("w", "")
                            c_val = cw.get("c", 0.5)
                            if isinstance(c_val, (int, float)):
                                confidence_sum += c_val
                                result_count += 1

                offset += frame_size
                status = 1  # Middle frames

        avg_confidence = confidence_sum / max(result_count, 1) if result_count > 0 else 0.0
        return result_text, avg_confidence