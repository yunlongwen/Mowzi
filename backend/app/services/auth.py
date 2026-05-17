"""Authentication middleware and token verification services."""

from fastapi import Header, HTTPException, Depends
from sqlalchemy.orm import Session

from app.database import get_db

# Legacy in-memory storage (kept for backward compatibility during migration)
DEVICE_TOKENS = {}
PARENT_TOKENS = {}


def verify_device_token_in_db(db: Session, token: str) -> int | None:
    """Verify device token from database and return child_id if valid.

    Args:
        db: Database session
        token: Device token (format: dev_<hex>)

    Returns:
        child_id if token is valid, None otherwise
    """
    from app.models.device_token import DeviceToken
    device_token = db.query(DeviceToken).filter(DeviceToken.token == token).first()
    return device_token.child_id if device_token else None


def register_device_token_to_db(db: Session, token: str, child_id: int, device_id: str) -> None:
    """Register a device token to database.

    Args:
        db: Database session
        token: Device token
        child_id: Child profile ID
        device_id: Device identifier
    """
    from app.models.device_token import DeviceToken
    existing = db.query(DeviceToken).filter(DeviceToken.token == token).first()
    if existing:
        existing.child_id = child_id
    else:
        device_token = DeviceToken(token=token, child_id=child_id, device_id=device_id)
        db.add(device_token)
    db.commit()


def verify_parent_token_in_db(db: Session, token: str) -> bool:
    """Verify parent token from database.

    Args:
        db: Database session
        token: Parent token (format: parent_<hex>)

    Returns:
        True if token is valid, False otherwise
    """
    from app.models.device_token import DeviceToken
    parent_token = db.query(DeviceToken).filter(DeviceToken.token == token).first()
    return parent_token is not None


def register_parent_token_to_db(db: Session, token: str, device_id: str = "") -> None:
    """Register a parent token to database.

    Args:
        db: Database session
        token: Parent token
        device_id: Device identifier (optional)
    """
    from app.models.device_token import DeviceToken
    existing = db.query(DeviceToken).filter(DeviceToken.token == token).first()
    if not existing:
        device_token = DeviceToken(token=token, child_id=0, device_id=device_id)
        db.add(device_token)
        db.commit()


# Legacy function for backward compatibility
def register_parent_token(token: str) -> None:
    """Register a parent token (legacy in-memory version)."""
    PARENT_TOKENS[token] = True


# Legacy function for backward compatibility
def register_device_token(token: str, child_id: int) -> None:
    """Register a device token (legacy in-memory version)."""
    DEVICE_TOKENS[token] = child_id


# Legacy function for backward compatibility
def verify_device_token_valid(token: str) -> int | None:
    """Verify device token by checking in-memory store."""
    # This is a synchronous function used by tests - for production use
    # the async verify_device_token which checks database
    return DEVICE_TOKENS.get(token)


# Legacy function for backward compatibility
def verify_parent_token_valid(token: str) -> bool:
    """Verify parent token (legacy in-memory version)."""
    return token in PARENT_TOKENS


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

    # Try database first, fall back to in-memory for backward compatibility
    child_id = verify_device_token_in_db(db, token)
    if not child_id:
        # Legacy in-memory check
        child_id = DEVICE_TOKENS.get(token)

    if not child_id:
        raise HTTPException(status_code=401, detail="无效的设备令牌")

    return child_id


async def verify_parent_token(
    authorization: str = Header(...),
    db: Session = Depends(get_db)
) -> bool:
    """FastAPI dependency to verify parent token.

    Args:
        authorization: Authorization header value
        db: Database session

    Returns:
        True if token is valid

    Raises:
        HTTPException: 401 if token is invalid
    """
    token = authorization.replace("Bearer ", "")

    # Try database first, fall back to in-memory for backward compatibility
    if not verify_parent_token_in_db(db, token):
        # Legacy in-memory check
        if token not in PARENT_TOKENS:
            raise HTTPException(status_code=401, detail="无效的家长令牌")

    return True