"""Chat API endpoints for STT and conversation handling."""

from fastapi import APIRouter, Depends, HTTPException, UploadFile, File, Form, Response
from sqlalchemy.orm import Session
from sse_starlette.sse import EventSourceResponse
import json

from app.database import get_db
from app.services.auth import verify_device_token
from app.services.xfyun_stt import XfyunSTTService
from app.services.llm import LLMService, SentenceSplitter
from app.services.xfyun_tts import XfyunTTSService
from app.services.safety import SafetyService
from app.services.usage import UsageService
from app.schemas.common import ErrorResponse, ErrorDetail, ErrorCode
from app.config import settings

router = APIRouter()

# Initialize services with credentials from settings
stt_service = XfyunSTTService(
    app_id=settings.xfyun_app_id,
    api_key=settings.xfyun_api_key,
    api_secret=settings.xfyun_api_secret
)

llm_service = LLMService(
    api_url=settings.llm_api_url,
    api_key=settings.llm_api_key,
    model=settings.llm_model
)

tts_service = XfyunTTSService(
    app_id=settings.xfyun_app_id,
    api_key=settings.xfyun_api_key,
    api_secret=settings.xfyun_api_secret
)

safety_service = SafetyService()
usage_service = UsageService()


@router.post("/chat/stt")
async def speech_to_text(
    audio: UploadFile = File(...),
    format: str = Form("opus"),
    child_id: int = Depends(verify_device_token),
    db: Session = Depends(get_db)
):
    """Convert speech audio to text using IFLYTEK STT.

    Args:
        audio: Uploaded audio file (Opus or PCM)
        format: Audio format ("opus" or "pcm")
        child_id: Verified child ID from device token
        db: Database session

    Returns:
        JSON with recognized text and confidence score

    Raises:
        400: Audio too short
        413: Audio too large (>500KB)
        422: Low confidence or empty result
        502: STT service error
    """
    audio_data = await audio.read()

    # Validate audio size (60s Opus ~200KB)
    if len(audio_data) > 500_000:
        raise HTTPException(
            status_code=413,
            detail="Audio too large (max 500KB)"
        )
    if len(audio_data) < 100:
        raise HTTPException(
            status_code=400,
            detail="Audio too short (min 100 bytes)"
        )

    try:
        text, confidence = await stt_service.recognize(audio_data, format)
    except Exception as e:
        raise HTTPException(
            status_code=502,
            detail=ErrorResponse(
                error=ErrorDetail(
                    code="STT_FAILED",
                    message="语音识别失败，请再说一次"
                )
            ).model_dump()
        )

    # Check confidence threshold
    if confidence < 0.3 or not text.strip():
        raise HTTPException(
            status_code=422,
            detail=ErrorResponse(
                error=ErrorDetail(
                    code="STT_LOW_CONFIDENCE",
                    message="没听清哦，再说一次吧？"
                )
            ).model_dump()
        )

    return {"text": text, "confidence": confidence}


@router.post("/chat/stream")
async def chat_stream(
    message: str = Form(...),
    conversation_id: int = Form(None),
    child_id: int = Depends(verify_device_token),
    db: Session = Depends(get_db),
    response: Response = None
):
    """Stream chat endpoint with LLM + sentence-by-sentence TTS.

    Args:
        message: User input text
        conversation_id: Optional conversation ID for context
        child_id: Verified child ID from device token
        db: Database session
        response: Response object for headers

    Returns:
        SSE stream with text chunks, audio, and completion events
    """
    from app.models.message import Message
    from app.models.conversation import Conversation
    from app.models.session import ActiveSession

    async def event_generator():
        nonlocal response
        splitter = SentenceSplitter()
        full_text = ""
        sentence_index = 0

        try:
            # Step 1: Check blocked hours
            blocked, minutes_until = usage_service.check_blocked_hours()
            if blocked:
                yield {
                    "event": "error",
                    "data": json.dumps({
                        "code": ErrorCode.BLOCKED_HOURS,
                        "message": f"当前时段不可使用，请在{minutes_until}分钟后重试"
                    })
                }
                yield {"event": "done", "data": ""}
                return

            # Step 2: Check daily usage limit
            daily_exceeded, daily_remaining = usage_service.check_daily_limit(child_id, db)
            if daily_exceeded:
                yield {
                    "event": "error",
                    "data": json.dumps({
                        "code": ErrorCode.USAGE_DAILY_LIMIT,
                        "message": "今日使用时长已到，明天再来吧"
                    })
                }
                yield {"event": "done", "data": ""}
                return

            # Step 3: Check session limit
            session_exceeded, session_remaining = usage_service.check_session_limit(child_id, db)
            if session_exceeded:
                yield {
                    "event": "error",
                    "data": json.dumps({
                        "code": ErrorCode.USAGE_SESSION_LIMIT,
                        "message": "会话时间过长，休息一下吧"
                    })
                }
                yield {"event": "done", "data": ""}
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
                        "code": "CONTENT_BLOCKED",
                        "message": safety_result["message"],
                        "flagged_words": safety_result["flagged_words"]
                    })
                }
                yield {"event": "done", "data": ""}
                return

            # Step 6: Save user message to database
            conv_id = conversation_id
            if not conv_id:
                # Create new conversation if not provided
                conv = Conversation(
                    child_id=child_id,
                    character_id=1,  # Default character
                    status="active"
                )
                db.add(conv)
                db.commit()
                db.refresh(conv)
                conv_id = conv.id

            user_msg = Message(
                conversation_id=conv_id,
                role="user",
                content=message
            )
            db.add(user_msg)
            db.commit()
            db.refresh(user_msg)

            # Step 7: Build context (placeholder - context_service not yet available)
            messages = [
                {"role": "system", "content": "你是一个友好的儿童AI伴侣，用温柔有趣的方式和孩子交流。"},
                {"role": "user", "content": message}
            ]

            # Step 8: Stream LLM response with sentence splitting + TTS
            sentence_index = 0
            async for chunk_text in llm_service.stream_chat(messages, max_tokens=settings.max_llm_tokens):
                # Send text chunk immediately
                full_text += chunk_text
                yield {
                    "event": "text_chunk",
                    "data": json.dumps({"chunk": chunk_text})
                }

                # Check for complete sentences and trigger TTS
                sentences = splitter.add_chunk(chunk_text)
                for sentence in sentences:
                    try:
                        # Filter content for TTS (safety check on LLM output)
                        filtered_sentence = safety_service.filter_content(sentence)
                        audio_b64, duration_ms = await tts_service.synthesize(filtered_sentence)
                        yield {
                            "event": "sentence_audio",
                            "data": json.dumps({
                                "sentence_index": sentence_index,
                                "text": sentence,
                                "audio_base64": audio_b64,
                                "duration_ms": duration_ms
                            })
                        }
                        sentence_index += 1
                    except Exception as e:
                        # Skip this sentence's audio, log error, continue with next sentence
                        # Don't crash the entire stream
                        sentence_index += 1
                        continue

            # Step 9: Flush remaining text in buffer
            remaining = splitter.flush()
            if remaining:
                try:
                    # Filter remaining content for TTS
                    filtered_remaining = safety_service.filter_content(remaining)
                    audio_b64, duration_ms = await tts_service.synthesize(filtered_remaining)
                    yield {
                        "event": "sentence_audio",
                        "data": json.dumps({
                            "sentence_index": sentence_index,
                            "text": remaining,
                            "audio_base64": audio_b64,
                            "duration_ms": duration_ms
                        })
                    }
                except Exception as e:
                    # Skip remaining audio, don't crash the stream
                    pass

            # Step 10: Save assistant message to database
            assistant_msg = Message(
                conversation_id=conv_id,
                role="assistant",
                content=full_text
            )
            db.add(assistant_msg)
            db.commit()

            # Step 11: Record usage activity (1 minute per request)
            usage_service.record_activity(child_id, db, minutes=1)

            # Step 12: Calculate remaining minutes for header
            remaining_minutes = usage_service.get_remaining_minutes(child_id, db)

            # Step 13: Send completion events
            yield {
                "event": "text_done",
                "data": json.dumps({
                    "full_text": full_text,
                    "message_id": assistant_msg.id,
                    "remaining_minutes": remaining_minutes
                })
            }
            yield {"event": "done", "data": json.dumps({"message_id": assistant_msg.id, "remaining_minutes": remaining_minutes})}

        except Exception as e:
            yield {
                "event": "error",
                "data": json.dumps({
                    "code": "STREAM_ERROR",
                    "message": str(e)
                })
            }
            yield {"event": "done", "data": ""}

    return EventSourceResponse(event_generator())