"""Parent authentication API endpoints."""

from datetime import datetime, timedelta
from uuid import uuid4
import bcrypt
from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from app.database import get_db
from app.models.parent_settings import ParentSettings
from app.schemas.config import PinRequest, AuthTokenResponse
from app.services.auth import register_parent_token


router = APIRouter()


@router.post("/parent/auth", response_model=AuthTokenResponse)
async def parent_auth(request: PinRequest, db: Session = Depends(get_db)):
    """Authenticate parent with PIN and return a token."""
    settings = db.query(ParentSettings).filter(ParentSettings.id == 1).first()

    if not settings:
        raise HTTPException(status_code=404, detail="Settings not initialized")

    # Check if PIN is locked due to too many failed attempts
    if settings.pin_locked_until and datetime.utcnow() < settings.pin_locked_until:
        raise HTTPException(status_code=423, detail="PIN已锁定")

    # Verify PIN
    if not bcrypt.checkpw(request.pin.encode(), settings.pin_hash.encode()):
        # Increment failed attempts
        settings.pin_attempts = (settings.pin_attempts or 0) + 1
        if settings.pin_attempts >= 5:
            settings.pin_locked_until = datetime.utcnow() + timedelta(minutes=15)
        db.commit()
        raise HTTPException(status_code=401, detail="PIN码错误")

    # Successful auth - reset counters
    settings.pin_attempts = 0
    settings.pin_locked_until = None
    db.commit()

    # Generate parent token (simple UUID token)
    token = f"parent_{uuid4().hex}"

    # Register parent token
    register_parent_token(token)

    return {"token": token}