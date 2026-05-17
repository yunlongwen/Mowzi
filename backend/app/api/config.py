"""Device registration API endpoints."""

from uuid import uuid4
from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.database import get_db
from app.models.child import ChildProfile
from app.schemas.config import DeviceRegisterRequest, DeviceRegisterResponse
from app.services.auth import register_device_token


router = APIRouter()


@router.post("/device/register", response_model=DeviceRegisterResponse)
async def register_device(request: DeviceRegisterRequest, db: Session = Depends(get_db)):
    """Register a device and return a device token."""
    # Check if device_id is already registered
    child = db.query(ChildProfile).filter(ChildProfile.device_id == request.device_id).first()

    if not child:
        # Create new child profile
        child = ChildProfile(
            device_id=request.device_id,
            name=request.child_name or "小朋友"
        )
        db.add(child)
        db.commit()
        db.refresh(child)

    # Generate device token (simple UUID token)
    token = f"dev_{uuid4().hex}"

    # Register token with child_id
    register_device_token(token, child.id)

    return {"device_token": token}