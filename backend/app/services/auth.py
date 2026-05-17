"""Authentication middleware and token verification services."""

from fastapi import Header, HTTPException, Depends
from sqlalchemy.orm import Session

from app.database import get_db

# In-memory token storage for quick lookup
# In production, use Redis or database
DEVICE_TOKENS = {}
PARENT_TOKENS = {}


def verify_device_token_valid(token: str) -> int | None:
    """Verify device token and return child_id if valid.

    Args:
        token: Device token (format: dev_<hex>)

    Returns:
        child_id if token is valid, None otherwise
    """
    return DEVICE_TOKENS.get(token)


def verify_parent_token_valid(token: str) -> bool:
    """Verify parent token.

    Args:
        token: Parent token (format: parent_<hex>)

    Returns:
        True if token is valid, False otherwise
    """
    return token in PARENT_TOKENS


def register_device_token(token: str, child_id: int) -> None:
    """Register a device token with its child_id.

    Args:
        token: Device token
        child_id: Child profile ID
    """
    DEVICE_TOKENS[token] = child_id


def register_parent_token(token: str) -> None:
    """Register a parent token.

    Args:
        token: Parent token
    """
    PARENT_TOKENS[token] = True


async def verify_device_token(
    authorization: str = Header(...),
    db: Session = Depends(get_db)
) -> int:
    """FastAPI dependency to verify device token.

    Args:
        authorization: Authorization header value
        db: Database session

    Returns:
        child_id if token is valid

    Raises:
        HTTPException: 401 if token is invalid
    """
    token = authorization.replace("Bearer ", "")
    child_id = verify_device_token_valid(token)

    if not child_id:
        raise HTTPException(status_code=401, detail="无效的设备令牌")

    return child_id


async def verify_parent_token(authorization: str = Header(...)) -> bool:
    """FastAPI dependency to verify parent token.

    Args:
        authorization: Authorization header value

    Returns:
        True if token is valid

    Raises:
        HTTPException: 401 if token is invalid
    """
    token = authorization.replace("Bearer ", "")

    if not verify_parent_token_valid(token):
        raise HTTPException(status_code=401, detail="无效的家长令牌")

    return True