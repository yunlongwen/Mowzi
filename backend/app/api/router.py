from fastapi import APIRouter

from app.api import config, parent, chat, conversations

api_router = APIRouter()

api_router.include_router(config.router, tags=["config"])
api_router.include_router(parent.router, tags=["parent"])
api_router.include_router(chat.router, tags=["chat"])
api_router.include_router(conversations.router, tags=["conversations"])