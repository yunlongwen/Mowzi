"""Device registration API endpoints."""

from uuid import uuid4
from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.database import get_db
from app.models.child import ChildProfile
from app.models.character import AICharacter
from app.schemas.config import DeviceRegisterRequest, DeviceRegisterResponse
from app.services.auth import register_device_token


router = APIRouter()


@router.get("/config/characters")
async def get_characters(db: Session = Depends(get_db)):
    """Return all available AI characters."""
    characters = db.query(AICharacter).all()
    return {
        "characters": [
            {
                "id": c.id,
                "name": c.name,
                "avatar_url": c.avatar_url,
                "description": c.description
            }
            for c in characters
        ]
    }


@router.post("/device/register", response_model=DeviceRegisterResponse)
async def register_device(request: DeviceRegisterRequest, db: Session = Depends(get_db)):
    """Register a device and return a device token."""
    # Check if device_id is already registered
    child = db.query(ChildProfile).filter(ChildProfile.device_id == request.deviceId).first()

    if not child:
        # Create new child profile
        child = ChildProfile(
            device_id=request.deviceId,
            name=request.deviceName or "小朋友"
        )
        db.add(child)
        db.commit()
        db.refresh(child)

    # Generate device token (simple UUID token)
    token = f"dev_{uuid4().hex}"

    # Register token with child_id
    register_device_token(token, child.id)

    return DeviceRegisterResponse(success=True, deviceToken=token)