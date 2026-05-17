"""Chat API endpoints for conversation handling."""

from fastapi import APIRouter, Depends, HTTPException, Response
from sqlalchemy.orm import Session
from sse_starlette.sse import EventSourceResponse
import json

from app.database import get_db
from app.services.auth import verify_device_token
from app.services.llm import LLMService
from app.services.safety import SafetyService
from app.services.usage import UsageService
from app.schemas.common import ErrorResponse, ErrorDetail, ErrorCode
from app.schemas.chat import ChatStreamRequest
from app.config import settings

router = APIRouter()

llm_service = LLMService(
    api_url=settings.llm_api_url,
    api_key=settings.llm_api_key,
    model=settings.llm_model
)

safety_service = SafetyService()
usage_service = UsageService()


@router.post("/chat/stream")
async def chat_stream(
    request: ChatStreamRequest,
    child_id: int = Depends(verify_device_token),
    db: Session = Depends(get_db),
):
    """Stream chat endpoint — text only, TTS handled on Android via MSC SDK."""
    from app.models.message import Message
    from app.models.conversation import Conversation
    from app.models.session import ActiveSession
    from app.models.character import AICharacter

    message = request.text
    conv_id = request.conversationId
    character_id = request.characterId

    async def event_generator():
        full_text = ""
        _conv_id = conv_id  # Use separate variable to avoid Python scoping issues

        try:
            # Step 1: Check blocked hours
            blocked, minutes_until = usage_service.check_blocked_hours()
            if blocked:
                yield {
                    "event": "error",
                    "data": json.dumps({
                        "type": "error",
                        "code": ErrorCode.BLOCKED_HOURS,
                        "content": f"当前时段不可使用，请在{minutes_until}分钟后重试"
                    })
                }
                yield {"event": "done", "data": json.dumps({"type": "done"})}
                return

            # Step 2: Check daily usage limit
            daily_exceeded, daily_remaining = usage_service.check_daily_limit(child_id, db)
            if daily_exceeded:
                yield {
                    "event": "error",
                    "data": json.dumps({
                        "type": "error",
                        "code": ErrorCode.USAGE_DAILY_LIMIT,
                        "content": "今日使用时长已到，明天再来吧"
                    })
                }
                yield {"event": "done", "data": json.dumps({"type": "done"})}
                return

            # Step 3: Check session limit
            session_exceeded, session_remaining = usage_service.check_session_limit(child_id, db)
            if session_exceeded:
                yield {
                    "event": "error",
                    "data": json.dumps({
                        "type": "error",
                        "code": ErrorCode.USAGE_SESSION_LIMIT,
                        "content": "会话时间过长，休息一下吧"
                    })
                }
                yield {"event": "done", "data": json.dumps({"type": "done"})}
                return

            # Step 4: Start or update active session
            active_session = db.query(ActiveSession).filter(
                ActiveSession.child_id == child_id,
                ActiveSession.status == "active"
            ).first()
            if not active_session:
                active_session = usage_service.start_session(child_id, db)
            else:
                usage_service.update_session_activity(child_id, db)

            # Step 5: Content safety check on user input
            safety_result = safety_service.check_content(message)
            if not safety_result["safe"]:
                yield {
                    "event": "error",
                    "data": json.dumps({
                        "type": "error",
                        "code": "CONTENT_BLOCKED",
                        "content": safety_result["message"],
                        "flagged_words": safety_result["flagged_words"]
                    })
                }
                yield {"event": "done", "data": json.dumps({"type": "done"})}
                return

            # Step 6: Save user message to database, create conversation if needed
            if not _conv_id:
                char_id_int = int(character_id) if character_id else 1
                conv = Conversation(
                    child_id=child_id,
                    character_id=char_id_int,
                    status="active"
                )
                db.add(conv)
                db.commit()
                db.refresh(conv)
                _conv_id = str(conv.id)

            user_msg = Message(
                conversation_id=int(_conv_id),
                role="user",
                content=message
            )
            db.add(user_msg)
            db.commit()
            db.refresh(user_msg)

            # Step 7: Build context
            char_id_int = int(character_id) if character_id else 1
            character = db.query(AICharacter).filter(AICharacter.id == char_id_int).first()
            base_prompt = character.system_prompt if character else "你是一个友好的儿童AI伴侣，用温柔有趣的方式和孩子交流。"

            # Force Chinese-only response
            system_prompt = base_prompt + "\n\n重要：必须只用中文回答，禁止出现任何英语单词或句子。"

            messages = [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": message}
            ]

            # Step 8: Stream LLM response (text only, TTS on Android)
            async for chunk_text in llm_service.stream_chat(messages, max_tokens=settings.max_llm_tokens):
                full_text += chunk_text
                yield {
                    "event": "text_chunk",
                    "data": json.dumps({"type": "text_chunk", "content": chunk_text})
                }

            # Step 9: Save assistant message to database
            assistant_msg = Message(
                conversation_id=int(_conv_id),
                role="assistant",
                content=full_text
            )
            db.add(assistant_msg)
            db.commit()

            # Step 10: Record usage activity
            usage_service.record_activity(child_id, db, minutes=1)

            # Step 11: Calculate remaining minutes
            remaining_minutes = usage_service.get_remaining_minutes(child_id, db)

            # Step 12: Send completion events
            yield {
                "event": "text_done",
                "data": json.dumps({
                    "type": "text_done",
                    "content": full_text,
                    "message_id": assistant_msg.id,
                    "remaining_minutes": remaining_minutes
                })
            }
            yield {"event": "done", "data": json.dumps({"type": "done"})}

        except Exception as e:
            yield {
                "event": "error",
                "data": json.dumps({
                    "type": "error",
                    "code": "STREAM_ERROR",
                    "content": str(e)
                })
            }
            yield {"event": "done", "data": json.dumps({"type": "done"})}

    return EventSourceResponse(event_generator())
