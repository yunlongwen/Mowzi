"""Request/Response schemas for device registration and authentication."""

from pydantic import BaseModel


class DeviceRegisterRequest(BaseModel):
    """Matches Android's DeviceRegisterRequest (camelCase via JSON)."""
    deviceId: str
    deviceName: str | None = None
    deviceModel: str | None = None


class DeviceRegisterResponse(BaseModel):
    """Matches Android's DeviceRegisterResponse (camelCase via JSON)."""
    success: bool = True
    deviceToken: str


class PinRequest(BaseModel):
    pin: str


class AuthTokenResponse(BaseModel):
    success: bool
    token: str | None = None
    expiresAt: int | None = None