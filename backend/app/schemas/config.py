"""Request/Response schemas for device registration and authentication."""

from pydantic import BaseModel


class DeviceRegisterRequest(BaseModel):
    device_id: str
    child_name: str | None = None


class DeviceRegisterResponse(BaseModel):
    device_token: str


class PinRequest(BaseModel):
    pin: str


class AuthTokenResponse(BaseModel):
    token: str