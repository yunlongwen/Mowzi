from fastapi import APIRouter

from app.api import config, parent

api_router = APIRouter()

api_router.include_router(config.router, tags=["config"])
api_router.include_router(parent.router, tags=["parent"])