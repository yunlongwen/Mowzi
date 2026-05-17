from pydantic import BaseModel


class ErrorDetail(BaseModel):
    code: str
    message: str


class ErrorResponse(BaseModel):
    error: ErrorDetail


class ErrorCode:
    STT_FAILED = "STT_FAILED"
    TTS_FAILED = "TTS_FAILED"
    LLM_FAILED = "LLM_FAILED"
    NETWORK_ERROR = "NETWORK_ERROR"
    USAGE_DAILY_LIMIT = "USAGE_DAILY_LIMIT"
    USAGE_SESSION_LIMIT = "USAGE_SESSION_LIMIT"
    BLOCKED_HOURS = "BLOCKED_HOURS"
    INVALID_PIN = "INVALID_PIN"
    CONCURRENT_REQUEST = "CONCURRENT_REQUEST"
    XFYUN_QUOTA_EXCEEDED = "XFYUN_QUOTA_EXCEEDED"