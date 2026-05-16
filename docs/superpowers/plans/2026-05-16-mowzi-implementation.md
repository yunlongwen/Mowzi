# Mowzi（毛仔）实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**目标：** 实现儿童AI语音伴侣应用的完整MVP，包含后端API和Android客户端

**架构：** Python FastAPI后端代理所有外部服务（讯飞STT/TTS、LLM），Android客户端只与后端通信。按功能交替推进：每个功能从后端API到Android UI一起完成。

**技术栈：**
- 后端：Python 3.11+ / FastAPI / SQLAlchemy / SQLite (WAL) / OpenAI SDK / websockets
- Android：Kotlin / Jetpack Compose + Material 3 / Hilt / Retrofit + OkHttp / Room / android-opus-codec
- 外部：讯飞STT (WebSocket) / 讯飞TTS (WebSocket) / OpenAI兼容LLM API

---

## 文件结构

### 后端

```
backend/
├── app/
│   ├── __init__.py
│   ├── main.py                  # FastAPI应用入口，中间件注册
│   ├── config.py                # 配置管理（环境变量 + 数据库读取）
│   ├── database.py              # SQLAlchemy引擎和会话管理
│   ├── models/
│   │   ├── __init__.py          # 导出所有模型
│   │   ├── child.py             # ChildProfile
│   │   ├── conversation.py      # Conversation
│   │   ├── message.py           # Message
│   │   ├── character.py         # AICharacter
│   │   ├── parent_settings.py   # ParentSettings
│   │   ├── memory.py            # KeyMemory, ConversationSummary
│   │   ├── session.py           # ActiveSession
│   │   └── usage.py             # UsageLog
│   ├── api/
│   │   ├── __init__.py
│   │   ├── router.py            # 汇总所有路由
│   │   ├── chat.py              # POST /chat/stt, POST /chat/stream, POST /chat/tts
│   │   ├── conversations.py     # CRUD /conversations
│   │   ├── parent.py            # /parent/auth, /parent/settings, /parent/conversations, /parent/usage
│   │   └── config.py            # /config/characters, /device/register
│   ├── services/
│   │   ├── __init__.py
│   │   ├── xfyun_stt.py         # 讯飞STT WebSocket代理
│   │   ├── xfyun_tts.py         # 讯飞TTS WebSocket代理
│   │   ├── llm.py               # LLM流式调用 + 句子分割
│   │   ├── context.py           # 上下文组装 + Token预算裁剪
│   │   ├── memory.py            # 关键记忆提取 + 对话摘要
│   │   ├── safety.py            # 内容安全管道（关键词过滤 + 拦截逻辑）
│   │   ├── usage.py             # 使用时长追踪 + 限制检查
│   │   └── conversation.py      # 对话状态机
│   └── schemas/
│       ├── __init__.py
│       ├── chat.py              # STT/TTS/Stream请求/响应模型
│       ├── conversation.py      # 对话CRUD模型
│       ├── parent.py            # 家长面板模型
│       └── common.py            # 统一错误响应模型
├── tests/
│   ├── conftest.py              # 测试fixtures（内存数据库、测试客户端）
│   ├── test_stt.py
│   ├── test_llm.py
│   ├── test_context.py
│   ├── test_memory.py
│   ├── test_safety.py
│   ├── test_usage.py
│   ├── test_conversations.py
│   └── test_api_chat.py
├── requirements.txt
└── init_db.py                   # 数据库初始化脚本（建表 + 预设角色）
```

### Android应用

```
android/
├── app/
│   ├── src/main/java/com/mowzi/app/
│   │   ├── MowziApp.kt                  # Application类
│   │   ├── MainActivity.kt              # 单Activity
│   │   ├── di/
│   │   │   ├── NetworkModule.kt         # Retrofit, OkHttp, SSE客户端
│   │   │   ├── DatabaseModule.kt        # Room数据库
│   │   │   └── RepositoryModule.kt      # Repository绑定
│   │   ├── data/local/
│   │   │   ├── AppDatabase.kt
│   │   │   ├── dao/ConversationDao.kt
│   │   │   ├── dao/MessageDao.kt
│   │   │   └── entity/                  # Room实体（Conversation, CachedMessage, CharacterInfo）
│   │   ├── data/remote/
│   │   │   ├── MowziApi.kt              # Retrofit接口定义
│   │   │   └── dto/                     # 网络数据传输对象
│   │   ├── data/repository/
│   │   │   ├── ChatRepository.kt
│   │   │   ├── ConversationRepository.kt
│   │   │   └── ParentRepository.kt
│   │   ├── audio/
│   │   │   ├── AudioRecorder.kt         # PCM录音 + 静音检测
│   │   │   ├── AudioPlayer.kt           # MP3播放队列
│   │   │   └── OpusEncoder.kt           # PCM→Opus压缩
│   │   ├── sse/
│   │   │   └── SSEClient.kt             # OkHttp SSE事件流解析
│   │   ├── ui/
│   │   │   ├── navigation/MowziNavGraph.kt
│   │   │   ├── chat/
│   │   │   │   ├── ChatScreen.kt
│   │   │   │   └── ChatViewModel.kt
│   │   │   ├── characters/
│   │   │   │   ├── CharacterSelectScreen.kt
│   │   │   │   └── CharacterSelectViewModel.kt
│   │   │   ├── conversations/
│   │   │   │   ├── ConversationListScreen.kt
│   │   │   │   └── ConversationListViewModel.kt
│   │   │   ├── parent/
│   │   │   │   ├── PinEntryScreen.kt
│   │   │   │   ├── ParentDashboardScreen.kt
│   │   │   │   └── ParentViewModel.kt
│   │   │   └── onboarding/
│   │   │       └── WelcomeScreen.kt     # 首次启动引导
│   │   └── util/
│   │       └── TokenManager.kt          # 设备令牌存储
│   └── src/main/res/
│       ├── values/strings.xml
│       └── drawable/                    # 角色头像等资源
└── build.gradle.kts
```

---

## Task 1: 后端项目基础搭建

**文件：**
- Create: `backend/requirements.txt`
- Create: `backend/app/__init__.py`
- Create: `backend/app/main.py`
- Create: `backend/app/config.py`
- Create: `backend/app/database.py`
- Create: `backend/app/models/__init__.py`
- Create: `backend/app/models/child.py`
- Create: `backend/app/models/conversation.py`
- Create: `backend/app/models/message.py`
- Create: `backend/app/models/character.py`
- Create: `backend/app/models/parent_settings.py`
- Create: `backend/app/models/memory.py`
- Create: `backend/app/models/session.py`
- Create: `backend/app/models/usage.py`
- Create: `backend/app/schemas/__init__.py`
- Create: `backend/app/schemas/common.py`
- Create: `backend/app/api/__init__.py`
- Create: `backend/app/api/router.py`
- Create: `backend/init_db.py`
- Test: `backend/tests/conftest.py`

- [ ] **Step 1: 创建项目结构和依赖文件**

`backend/requirements.txt`:
```
fastapi==0.115.0
uvicorn[standard]==0.30.0
sqlalchemy==2.0.35
pydantic==2.9.0
pydantic-settings==2.5.0
httpx==0.27.0
websockets==13.0
openai==1.47.0
python-multipart==0.0.9
bcrypt==4.2.0
pytest==8.3.0
pytest-asyncio==0.24.0
httpx  # 也用于TestClient
```

- [ ] **Step 2: 实现配置管理**

`backend/app/config.py`:
```python
from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    database_url: str = "sqlite:///./mowzi.db"
    llm_api_url: str = ""
    llm_api_key: str = ""
    llm_model: str = "gpt-4o-mini"
    xfyun_app_id: str = ""
    xfyun_api_key: str = ""
    xfyun_api_secret: str = ""
    max_audio_duration_sec: int = 60
    min_audio_duration_sec: float = 0.5
    silence_detection_sec: float = 3.0
    max_llm_tokens: int = 300
    context_window_tokens: int = 8000

    class Config:
        env_file = ".env"

settings = Settings()
```

- [ ] **Step 3: 实现数据库初始化**

`backend/app/database.py`:
```python
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker, DeclarativeBase

engine = create_engine(
    "sqlite:///./mowzi.db",
    connect_args={"check_same_thread": False},
)
engine.execute("PRAGMA journal_mode=WAL")
engine.execute("PRAGMA busy_timeout=5000")

SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

class Base(DeclarativeBase):
    pass

def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
```

- [ ] **Step 4: 实现所有SQLAlchemy模型**

每个模型文件对应规格文档中的数据模型定义。关键模型：

`backend/app/models/child.py` — ChildProfile（id, name, device_id, created_at）
`backend/app/models/conversation.py` — Conversation（id, child_id, character_id, title, status, created_at, updated_at, last_message_at）
`backend/app/models/message.py` — Message（id, conversation_id, role, content, audio_path, timestamp）
`backend/app/models/character.py` — AICharacter（id, name, avatar_url, description, system_prompt, tts_voice_name）
`backend/app/models/parent_settings.py` — ParentSettings（id=1固定, pin_hash, daily_limit_min, session_limit_min, blocked_hours_start, blocked_hours_end, llm_api_url, llm_api_key, llm_model, xfyun_app_id, xfyun_api_key, xfyun_api_secret）
`backend/app/models/memory.py` — KeyMemory（id, child_id, key, value, updated_at）+ ConversationSummary（id, conversation_id, summary_text, message_count_at_summary, updated_at）
`backend/app/models/session.py` — ActiveSession（id, child_id, started_at, last_activity_at, status）
`backend/app/models/usage.py` — UsageLog（id, child_id, date, total_minutes, message_count）

`backend/app/models/__init__.py` 导入并导出所有模型类。

- [ ] **Step 5: 实现统一错误响应模型**

`backend/app/schemas/common.py`:
```python
from pydantic import BaseModel

class ErrorDetail(BaseModel):
    code: str
    message: str

class ErrorResponse(BaseModel):
    error: ErrorDetail

# 错误码常量
class ErrorCode:
    STT_FAILED = "STT_FAILED"
    TTS_FAILED = "TTS_FAILED"
    LLM_FAILED = "LLM_FAILED"
    NETWORK_ERROR = "NETWORK_ERROR"
    USAGE_DAILY_LIMIT = "USAGE_DAILY_LIMIT"
    USAGE_SESSION_LIMIT = "USAGE_SESSION_LIMIT"
    BLOCKED_HOURS = "BLOCKED_HOURS"
    INVALID_PIN = "INVALID_PIN"
    CONCURRENT_REQUEST = "CONCURRENT_REQUEST"
    XFYUN_QUOTA_EXCEEDED = "XFYUN_QUOTA_EXCEEDED"
```

- [ ] **Step 6: 创建FastAPI应用入口和路由骨架**

`backend/app/main.py`:
```python
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.api.router import api_router

app = FastAPI(title="Mowzi API", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(api_router, prefix="/api/v1")

@app.get("/health")
async def health():
    return {"status": "ok"}
```

`backend/app/api/router.py` — 汇总所有子路由（先占位，后续task填充）。

- [ ] **Step 7: 实现数据库初始化脚本**

`backend/init_db.py` — 建表 + 插入4个预设AI角色（猫头鹰医生、故事兔兔、搞笑机器人、冒险猫咪），每个角色包含完整的 system_prompt 和 tts_voice_name。

- [ ] **Step 8: 创建测试fixtures**

`backend/tests/conftest.py`:
```python
import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from fastapi.testclient import TestClient
from app.database import Base, get_db
from app.main import app

@pytest.fixture
def db_session():
    engine = create_engine("sqlite:///:memory:")
    Base.metadata.create_all(engine)
    Session = sessionmaker(bind=engine)
    session = Session()
    yield session
    session.close()

@pytest.fixture
def client(db_session):
    def override_get_db():
        yield db_session
    app.dependency_overrides[get_db] = override_get_db
    yield TestClient(app)
    app.dependency_overrides.clear()
```

- [ ] **Step 9: 验证并提交**

```bash
cd backend
pip install -r requirements.txt
python -m pytest tests/ -v
python init_db.py  # 验证建表和预设数据
uvicorn app.main:app --reload  # 验证 /health 端点
```

```bash
git add backend/
git commit -m "feat: 后端项目基础搭建 - FastAPI + SQLAlchemy + 数据模型"
```

---

## Task 2: Android项目基础搭建

**文件：**
- Create: `android/app/build.gradle.kts`
- Create: `android/app/src/main/java/com/mowzi/app/MowziApp.kt`
- Create: `android/app/src/main/java/com/mowzi/app/MainActivity.kt`
- Create: `android/app/src/main/java/com/mowzi/app/di/NetworkModule.kt`
- Create: `android/app/src/main/java/com/mowzi/app/di/DatabaseModule.kt`
- Create: `android/app/src/main/java/com/mowzi/app/di/RepositoryModule.kt`
- Create: `android/app/src/main/java/com/mowzi/app/data/local/AppDatabase.kt`
- Create: `android/app/src/main/java/com/mowzi/app/data/local/dao/` (ConversationDao, MessageDao)
- Create: `android/app/src/main/java/com/mowzi/app/data/local/entity/` (Room实体)
- Create: `android/app/src/main/java/com/mowzi/app/data/remote/MowziApi.kt`
- Create: `android/app/src/main/java/com/mowzi/app/ui/navigation/MowziNavGraph.kt`

- [ ] **Step 1: 创建Android项目**

使用 Android Studio 创建新项目：
- Template: Empty Compose Activity
- Package: `com.mowzi.app`
- Min SDK: 26 (Android 8.0)
- Language: Kotlin

`build.gradle.kts` 添加依赖：
```kotlin
dependencies {
    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.navigation:navigation-compose:2.8.0")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-compiler:2.52")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Network
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Audio - Opus编码
    implementation("com.github.theeasiestway:android-opus-codec:1.0.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.0")
}
```

- [ ] **Step 2: 实现Room数据库和实体**

按规格文档创建 Room 实体：ConversationEntity（含 status, lastMessageAt）、CachedMessageEntity、CharacterInfoEntity。
创建 DAO 接口：ConversationDao（getActive, getAll, upsert, updateStatus）、MessageDao（getByConversation, insert）。
创建 AppDatabase（version=1, exportSchema=false）。

- [ ] **Step 3: 实现Hilt依赖注入模块**

- `NetworkModule`: 提供 OkHttpClient、Retrofit（baseURL从DataStore读取）、MowziApi
- `DatabaseModule`: 提供 AppDatabase、各DAO
- `RepositoryModule`: 绑定 Repository 接口到实现

- [ ] **Step 4: 实现Retrofit API接口骨架**

`MowziApi.kt` 定义所有端点（先定义接口，后续task实现DTO和调用）：
```kotlin
interface MowziApi {
    @Multipart
    @POST("/api/v1/chat/stt")
    suspend fun speechToText(@Part audio: MultipartBody.Part, @Part("format") format: String): Response<SttResponse>

    @POST("/api/v1/chat/stream")
    fun chatStream(@Body request: ChatStreamRequest): Call<ResponseBody>  // SSE用原始ResponseBody

    @POST("/api/v1/conversations")
    suspend fun createConversation(@Body request: CreateConversationRequest): Response<ConversationResponse>

    @GET("/api/v1/conversations/active")
    suspend fun getActiveConversation(): Response<ActiveConversationResponse?>

    @GET("/api/v1/config/characters")
    suspend fun getCharacters(): Response<CharactersResponse>

    @POST("/api/v1/device/register")
    suspend fun registerDevice(@Body request: DeviceRegisterRequest): Response<DeviceRegisterResponse>

    @POST("/api/v1/parent/auth")
    suspend fun parentAuth(@Body request: PinRequest): Response<AuthTokenResponse>
}
```

- [ ] **Step 5: 实现导航骨架**

`MowziNavGraph.kt` 定义路由：
- `onboarding` — 首次启动引导
- `characterSelect` — 角色选择
- `chat/{conversationId}` — 聊天界面
- `conversationList` — 对话历史
- `pinEntry` — PIN码输入
- `parentDashboard` — 家长控制面板

- [ ] **Step 6: 验证并提交**

在 Android Studio 中 Build & Run，确认空导航框架正常启动。

```bash
git add android/
git commit -m "feat: Android项目基础搭建 - Compose + Hilt + Room + Retrofit骨架"
```

---

## Task 3: 设备注册与认证

**文件：**
- Create: `backend/app/api/config.py`
- Create: `backend/app/services/auth.py`
- Create: `backend/app/schemas/config.py`
- Modify: `backend/app/api/router.py`
- Test: `backend/tests/test_auth.py`
- Create: `android/.../data/remote/dto/AuthDto.kt`
- Create: `android/.../util/TokenManager.kt`

- [ ] **Step 1: 实现设备注册API**

`backend/app/api/config.py`:
```python
@router.post("/device/register")
async def register_device(request: DeviceRegisterRequest, db: Session = Depends(get_db)):
    # 检查device_id是否已注册
    child = db.query(ChildProfile).filter(ChildProfile.device_id == request.device_id).first()
    if not child:
        child = ChildProfile(
            id=str(uuid4()),
            name=request.child_name or "小朋友",
            device_id=request.device_id
        )
        db.add(child)
        db.commit()
    # 生成device_token（简单的JWT或UUID token）
    token = generate_device_token(child.id)
    return {"device_token": token}
```

- [ ] **Step 2: 实现家长PIN认证API**

`backend/app/api/parent.py`:
```python
@router.post("/parent/auth")
async def parent_auth(request: PinRequest, db: Session = Depends(get_db)):
    settings = db.query(ParentSettings).filter(ParentSettings.id == 1).first()
    if not settings:
        raise HTTPException(status_code=404, detail="Settings not initialized")
    if not bcrypt.checkpw(request.pin.encode(), settings.pin_hash.encode()):
        raise HTTPException(status_code=401, detail=ErrorResponse(error=ErrorDetail(code="INVALID_PIN", message="PIN码错误")).model_dump())
    token = generate_parent_token()
    return {"token": token}
```

注意：PIN尝试限制——在 ParentSettings 中增加 `pin_attempts` 和 `pin_locked_until` 字段，5次失败后锁定15分钟。

- [ ] **Step 3: 实现Token验证中间件**

`backend/app/services/auth.py`:
```python
from fastapi import Depends, HTTPException, Header

async def verify_device_token(authorization: str = Header(...), db = Depends(get_db)):
    """验证设备令牌，返回child_id"""
    token = authorization.replace("Bearer ", "")
    child_id = decode_device_token(token)
    if not child_id:
        raise HTTPException(status_code=401)
    return child_id

async def verify_parent_token(authorization: str = Header(...)):
    """验证家长令牌"""
    token = authorization.replace("Bearer ", "")
    if not validate_parent_token(token):
        raise HTTPException(status_code=401)
    return True
```

- [ ] **Step 4: 测试认证流程**

```bash
pytest tests/test_auth.py -v
# 测试：设备注册、重复注册、PIN验证、PIN错误、PIN锁定
```

- [ ] **Step 5: Android端实现TokenManager和认证拦截器**

`TokenManager.kt` 使用 DataStore 存储设备令牌。
OkHttp Interceptor 自动在请求头附加 `Authorization: Bearer {device_token}`。

- [ ] **Step 6: 提交**

```bash
git add backend/app/api/config.py backend/app/api/parent.py backend/app/services/auth.py
git add android/.../util/TokenManager.kt
git commit -m "feat: 设备注册与PIN认证 - 后端API + Android令牌管理"
```

---

## Task 4: 讯飞STT服务（后端WebSocket代理）

**文件：**
- Create: `backend/app/services/xfyun_stt.py`
- Create: `backend/app/api/chat.py`（STT端点部分）
- Create: `backend/app/schemas/chat.py`
- Test: `backend/tests/test_stt.py`

- [ ] **Step 1: 实现讯飞STT WebSocket代理**

这是关键技术组件。后端作为WebSocket客户端连接讯飞，将完整音频快速分帧发送。

`backend/app/services/xfyun_stt.py` 核心逻辑：
```python
import hashlib
import hmac
import base64
import json
import asyncio
import websockets

class XfyunSTTService:
    def __init__(self, app_id: str, api_key: str, api_secret: str):
        self.app_id = app_id
        self.api_key = api_key
        self.api_secret = api_secret

    def _build_auth_url(self) -> str:
        """生成讯飞WebSocket鉴权URL"""
        import time
        url = "wss://iat-api.xfyun.cn/v2/iat"
        timestamp = str(int(time.time()))
        signature_origin = f"host: iat-api.xfyun.cn\ndate: {timestamp}\nGET /v2/iat HTTP/1.1"
        signature = hmac.new(
            self.api_secret.encode(), signature_origin.encode(), hashlib.sha256
        ).digest()
        authorization = base64.b64encode(signature).decode()
        return f"{url}?authorization={authorization}&date={timestamp}&host=iat-api.xfyun.cn"

    async def recognize(self, audio_data: bytes, format: str = "opus") -> tuple[str, float]:
        """
        将完整音频发送给讯飞STT，返回（识别文本，置信度）。
        音频按1280B分帧快速连续发送，最后标记status=2结束。
        """
        auth_url = self._build_auth_url()
        result_text = ""
        confidence_sum = 0.0
        result_count = 0

        async with websockets.connect(auth_url) as ws:
            frame_size = 1280
            offset = 0
            while offset < len(audio_data):
                frame = audio_data[offset:offset + frame_size]
                status = 2 if (offset + frame_size >= len(audio_data)) else 1
                if offset == 0:
                    status = 0  # 首帧
                payload = {
                    "common": {"app_id": self.app_id},
                    "business": {
                        "language": "zh_cn",
                        "domain": "iat",
                        "accent": "mandarin",
                        "dwa": "wpgs",  # 动态修正
                    },
                    "data": {
                        "status": status,
                        "format": "audio/opus" if format == "opus" else "audio/L16;rate=16000",
                        "encoding": "raw" if format == "pcm" else "raw",
                        "audio": base64.b64encode(frame).decode()
                    }
                }
                await ws.send(json.dumps(payload))
                response = json.loads(await ws.recv())
                code = response.get("code", -1)
                if code != 0:
                    raise Exception(f"讯飞STT错误: code={code}, message={response.get('message')}")
                data = response.get("data", {})
                if data.get("result"):
                    ws_list = data["result"].get("ws", [])
                    for ws_item in ws_list:
                        for cw in ws_item.get("cw", []):
                            result_text += cw.get("w", "")
                            confidence_sum += cw.get("wp", "n") != "n" and 0.9 or float(cw.get("c", 0.5))
                            result_count += 1
                offset += frame_size

        avg_confidence = confidence_sum / max(result_count, 1) if result_count > 0 else 0.0
        return result_text, avg_confidence
```

- [ ] **Step 2: 实现STT API端点**

`backend/app/api/chat.py`:
```python
@router.post("/chat/stt")
async def speech_to_text(
    audio: UploadFile = File(...),
    format: str = Form("opus"),
    child_id: str = Depends(verify_device_token),
    db: Session = Depends(get_db)
):
    audio_data = await audio.read()
    # 验证音频大小（60秒 Opus 约 200KB）
    if len(audio_data) > 500_000:
        raise HTTPException(status_code=413, detail="Audio too large")
    if len(audio_data) < 100:
        raise HTTPException(status_code=400, detail="Audio too short")

    try:
        text, confidence = await stt_service.recognize(audio_data, format)
    except Exception as e:
        raise HTTPException(status_code=502, detail=ErrorResponse(
            error=ErrorDetail(code="STT_FAILED", message="语音识别失败")
        ).model_dump())

    if confidence < 0.3 or not text.strip():
        raise HTTPException(status_code=422, detail=ErrorResponse(
            error=ErrorDetail(code="STT_LOW_CONFIDENCE", message="没听清哦，再说一次吧？")
        ).model_dump())

    return {"text": text, "confidence": confidence}
```

- [ ] **Step 3: 测试STT服务**

```bash
pytest tests/test_stt.py -v
# 测试：空音频、超短音频、超大音频、正常音频（mock讯飞WebSocket）、低置信度处理
```

- [ ] **Step 4: 提交**

```bash
git add backend/app/services/xfyun_stt.py backend/app/api/chat.py backend/app/schemas/chat.py
git commit -m "feat: 讯飞STT WebSocket代理 + STT API端点"
```

---

## Task 5: LLM流式调用 + 按句TTS（后端核心）

**文件：**
- Create: `backend/app/services/llm.py`
- Create: `backend/app/services/xfyun_tts.py`
- Modify: `backend/app/api/chat.py`（stream端点）
- Create: `backend/app/services/safety.py`
- Test: `backend/tests/test_llm.py`

这是整个系统最复杂的技术组件。核心流程：LLM流式输出 → 句子分割 → 每句触发TTS → SSE推送。

- [ ] **Step 1: 实现句子分割器**

`backend/app/services/llm.py`:
```python
import re

class SentenceSplitter:
    """从LLM流式输出中提取完整句子。"""

    SENTENCE_ENDINGS = re.compile(r'[。！？…\.\!\?]')

    def __init__(self):
        self.buffer = ""

    def add_chunk(self, chunk: str) -> list[str]:
        """添加文本片段，返回已完成的句子列表。"""
        self.buffer += chunk
        sentences = []
        while True:
            match = self.SENTENCE_ENDINGS.search(self.buffer)
            if not match:
                break
            end_pos = match.end()
            sentence = self.buffer[:end_pos].strip()
            if sentence:
                sentences.append(sentence)
            self.buffer = self.buffer[end_pos:]
        return sentences

    def flush(self) -> str:
        """返回缓冲区剩余文本。"""
        remaining = self.buffer.strip()
        self.buffer = ""
        return remaining if remaining else ""
```

- [ ] **Step 2: 实现讯飞TTS WebSocket代理**

`backend/app/services/xfyun_tts.py`:
```python
class XfyunTTSService:
    """讯飞TTS WebSocket代理。串行调用，等上一句返回再请求下一句（免费版2路并发限制）。"""

    async def synthesize(self, text: str, voice_name: str = "xiaoyan") -> tuple[str, int]:
        """
        合成单句语音。
        返回 (mp3_base64, duration_ms)。
        """
        auth_url = self._build_auth_url()
        audio_chunks = []

        async with websockets.connect(auth_url) as ws:
            payload = {
                "common": {"app_id": self.app_id},
                "business": {
                    "aue": "lame",       # MP3格式
                    "sfl": 1,            # 流式返回
                    "auf": "audio/L16;rate=16000",
                    "vcn": voice_name,   # 音色名称
                    "speed": 50,
                    "volume": 50,
                    "pitch": 50,
                },
                "data": {
                    "status": 2,         # 一次性发送
                    "text": base64.b64encode(text.encode()).decode()
                }
            }
            await ws.send(json.dumps(payload))

            while True:
                response = json.loads(await ws.recv())
                code = response.get("code", -1)
                if code != 0:
                    raise Exception(f"TTS错误: {code}")
                data = response.get("data", {})
                if data.get("audio"):
                    audio_chunks.append(base64.b64decode(data["audio"]))
                if data.get("status") == 2:  # 合成完成
                    break

        audio_data = b"".join(audio_chunks)
        duration_ms = int(len(audio_data) / 32)  # 粗略估算：16kHz*16bit mono = 32KB/s
        return base64.b64encode(audio_data).decode(), duration_ms
```

- [ ] **Step 3: 实现LLM流式调用服务**

```python
from openai import AsyncOpenAI

class LLMService:
    def __init__(self, api_url: str, api_key: str, model: str):
        self.client = AsyncOpenAI(base_url=api_url, api_key=api_key)
        self.model = model

    async def stream_chat(self, messages: list[dict], max_tokens: int = 300):
        """流式调用LLM，yield每个文本片段。"""
        stream = await self.client.chat.completions.create(
            model=self.model,
            messages=messages,
            max_tokens=max_tokens,
            stream=True
        )
        async for chunk in stream:
            if chunk.choices[0].delta.content:
                yield chunk.choices[0].delta.content
```

- [ ] **Step 4: 实现SSE流式聊天端点**

`backend/app/api/chat.py` 的 `/chat/stream` 端点核心逻辑：
```python
@router.post("/chat/stream")
async def chat_stream(
    request: ChatStreamRequest,
    child_id: str = Depends(verify_device_token),
    db: Session = Depends(get_db)
):
    # 1. 检查使用时长限制
    await check_usage_limits(child_id, db)

    # 2. 内容安全检查（用户输入）
    safety_result = safety_service.check_input(request.text)
    if not safety_result.safe:
        # 返回友好引导回复而非暴露拦截
        return StreamingResponse(
            _generate_safe_redirect_response(),
            media_type="text/event-stream"
        )

    # 3. 保存用户消息到数据库
    save_message(db, request.conversation_id, "user", request.text)

    # 4. 组装上下文
    messages = context_service.build_context(
        db=db,
        child_id=child_id,
        conversation_id=request.conversation_id,
        character_id=request.character_id,
        user_text=request.text
    )

    # 5. 流式生成 + 按句TTS + SSE推送
    async def generate():
        splitter = SentenceSplitter()
        sentence_index = 0
        full_text = ""
        try:
            async for chunk in llm_service.stream_chat(messages):
                full_text += chunk
                # 推送文本片段
                yield f"event: text_chunk\ndata: {json.dumps({'content': chunk})}\n\n"

                # 检查是否有完整句子
                sentences = splitter.add_chunk(chunk)
                for sentence in sentences:
                    try:
                        audio_b64, duration = await tts_service.synthesize(
                            sentence, character_voice_name
                        )
                        yield f"event: sentence_audio\ndata: {json.dumps({'sentence_index': sentence_index, 'audio_base64': audio_b64, 'duration_ms': duration})}\n\n"
                        sentence_index += 1
                    except Exception:
                        pass  # TTS失败不影响文字显示

            # 处理缓冲区剩余文本
            remaining = splitter.flush()
            if remaining:
                try:
                    audio_b64, duration = await tts_service.synthesize(remaining, character_voice_name)
                    yield f"event: sentence_audio\ndata: {json.dumps({'sentence_index': sentence_index, 'audio_base64': audio_b64, 'duration_ms': duration})}\n\n"
                except Exception:
                    pass

            # 内容安全检查（LLM输出）
            safety_result = safety_service.check_output(full_text)

            # 保存助手消息
            message_id = str(uuid4())
            save_message(db, request.conversation_id, "assistant", full_text)

            yield f"event: text_done\ndata: {json.dumps({'full_text': full_text, 'message_id': message_id})}\n\n"
            yield f"event: done\ndata: {json.dumps({'message_id': message_id})}\n\n"

        except Exception as e:
            yield f"event: error\ndata: {json.dumps({'code': 'LLM_FAILED', 'message': '毛仔在想呢，等一下再来找我吧'})}\n\n"
            yield f"event: done\ndata: {{}}\n\n"

    return StreamingResponse(generate(), media_type="text/event-stream")
```

- [ ] **Step 5: 测试LLM + TTS管道**

```bash
pytest tests/test_llm.py -v
# 测试：句子分割（各种标点）、TTS串行调用、SSE事件顺序、错误恢复
```

- [ ] **Step 6: 提交**

```bash
git add backend/app/services/llm.py backend/app/services/xfyun_tts.py
git commit -m "feat: LLM流式调用 + 句子分割 + 按句TTS + SSE流式端点"
```

---

## Task 6: Android语音聊天界面

**文件：**
- Create: `android/.../audio/AudioRecorder.kt`
- Create: `android/.../audio/AudioPlayer.kt`
- Create: `android/.../audio/OpusEncoder.kt`
- Create: `android/.../sse/SSEClient.kt`
- Create: `android/.../ui/chat/ChatScreen.kt`
- Create: `android/.../ui/chat/ChatViewModel.kt`
- Create: `android/.../data/repository/ChatRepository.kt`
- Create: `android/.../data/remote/dto/ChatDto.kt`

- [ ] **Step 1: 实现PCM录音 + 静音检测**

`AudioRecorder.kt`:
```kotlin
class AudioRecorder(private val silenceTimeoutMs: Long = 3000) {
    private var recorder: AudioRecord? = null
    private var isRecording = false
    private var lastSoundTime = 0L

    fun startRecording(): Flow<ShortArray> = callbackFlow {
        val bufferSize = AudioRecord.getMinBufferSize(16000, CHANNEL_IN_MONO, ENCODING_PCM_16BIT)
        recorder = AudioRecord(MediaRecorder.AudioSource.MIC, 16000, CHANNEL_IN_MONO, ENCODING_PCM_16BIT, bufferSize)
        recorder?.startRecording()
        isRecording = true

        val buffer = ShortArray(bufferSize)
        while (isRecording) {
            val read = recorder?.read(buffer, 0, buffer.size) ?: 0
            if (read > 0) {
                val chunk = buffer.copyOf(read)
                // 静音检测：计算RMS
                val rms = sqrt(chunk.map { it * it.toLong() }.sum().toDouble() / read)
                if (rms > SILENCE_THRESHOLD) lastSoundTime = System.currentTimeMillis()
                trySend(chunk)
            }
            // 静音超时自动停止
            if (System.currentTimeMillis() - lastSoundTime > silenceTimeoutMs && lastSoundTime > 0) {
                break
            }
        }
        close()
    }
}
```

- [ ] **Step 2: 实现Opus编码器封装**

`OpusEncoder.kt` — 封装 `theeasiestway/android-opus-codec`：
- 初始化编码器（16kHz, mono, bitrate=32000）
- 将PCM ShortArray转换为字节数组
- 编码为Opus帧
- 返回编码后的字节数组

- [ ] **Step 3: 实现SSE客户端**

`SSEClient.kt` — 使用OkHttp解析SSE事件流：
```kotlin
class SSEClient(private val okHttpClient: OkHttpClient) {
    fun connect(request: Request): Flow<SSEEvent> = callbackFlow {
        val call = okHttpClient.newCall(request)
        val response = call.execute()
        val reader = response.body?.byteStream()?.bufferedReader()

        var currentEvent = ""
        var currentData = ""

        reader?.useLines { lines ->
            for (line in lines) {
                when {
                    line.startsWith("event: ") -> currentEvent = line.removePrefix("event: ").trim()
                    line.startsWith("data: ") -> currentData = line.removePrefix("data: ").trim()
                    line.isBlank() -> {
                        // 空行 = 事件结束
                        if (currentEvent.isNotEmpty() && currentData.isNotEmpty()) {
                            trySend(SSEEvent(event = currentEvent, data = currentData))
                        }
                        currentEvent = ""
                        currentData = ""
                    }
                }
            }
        }
        close()
    }
}

data class SSEEvent(val event: String, val data: String)
```

- [ ] **Step 4: 实现音频播放队列**

`AudioPlayer.kt` — 管理逐句音频播放队列：
```kotlin
class AudioPlayer {
    private var mediaPlayer: MediaPlayer? = null
    private val queue = ConcurrentLinkedQueue<String>()  // MP3 base64
    private var isPlaying = false

    fun enqueue(audioBase64: String) {
        queue.add(audioBase64)
        if (!isPlaying) playNext()
    }

    private fun playNext() {
        val audio = queue.poll() ?: run { isPlaying = false; return }
        isPlaying = true
        val bytes = Base64.decode(audio, Base64.DEFAULT)
        val tempFile = File.createTempFile("tts_", ".mp3")
        tempFile.writeBytes(bytes)

        mediaPlayer = MediaPlayer().apply {
            setDataSource(tempFile.absolutePath)
            setOnCompletionListener {
                tempFile.delete()
                playNext()
            }
            prepare()
            start()
        }
    }

    fun stop() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        queue.clear()
        isPlaying = false
    }
}
```

- [ ] **Step 5: 实现ChatViewModel**

`ChatViewModel.kt` 管理聊天状态：
- 消息列表（Flow<StateFlow<List<ChatMessage>>>）
- 录音状态（idle / recording / processing / streaming）
- `sendVoiceMessage()`: 录音 → Opus编码 → multipart上传STT → 获取文字 → 调用stream端点
- `sendTextMessage()`: 直接调用stream端点
- SSE事件处理：text_chunk追加到当前流式消息、sentence_audio送入AudioPlayer队列
- 流式完成后保存消息到Room

- [ ] **Step 6: 实现ChatScreen**

Compose UI：
- 顶部：角色名称 + 切换按钮
- 中间：消息列表（LazyColumn），流式文字实时追加
- 底部：麦克风按钮（按住录音，松开发送）+ 文字输入框
- 状态指示：录音中波形动画、处理中加载动画
- 错误状态：友好提示 + 重试/重新录音按钮

- [ ] **Step 7: 提交**

```bash
git add android/.../audio/ android/.../sse/ android/.../ui/chat/ android/.../data/repository/ChatRepository.kt
git commit -m "feat: Android语音聊天界面 - 录音+Opus+SSE+按句播放+Chat UI"
```

---

## Task 7: 对话生命周期管理

**文件：**
- Create: `backend/app/api/conversations.py`
- Create: `backend/app/services/conversation.py`
- Create: `backend/app/schemas/conversation.py`
- Test: `backend/tests/test_conversations.py`
- Create: `android/.../ui/conversations/ConversationListScreen.kt`
- Create: `android/.../ui/conversations/ConversationListViewModel.kt`
- Create: `android/.../data/repository/ConversationRepository.kt`

- [ ] **Step 1: 实现后端对话状态机服务**

`backend/app/services/conversation.py`:
```python
class ConversationService:
    ACTIVE_TIMEOUT_MINUTES = 30
    ARCHIVE_TIMEOUT_HOURS = 24

    async def create(self, db, child_id, character_id):
        conversation = Conversation(
            id=str(uuid4()),
            child_id=child_id,
            character_id=character_id,
            title="新对话",
            status="active",
            last_message_at=datetime.utcnow()
        )
        db.add(conversation)
        db.commit()
        return conversation

    async def get_active(self, db, child_id):
        self._transition_states(db, child_id)
        return db.query(Conversation).filter(
            Conversation.child_id == child_id,
            Conversation.status == "active"
        ).order_by(Conversation.last_message_at.desc()).first()

    async def resume(self, db, conversation_id, child_id):
        conv = db.query(Conversation).filter(Conversation.id == conversation_id).first()
        if conv and conv.child_id == child_id and conv.status in ("idle", "archived"):
            conv.status = "active"
            conv.last_message_at = datetime.utcnow()
            db.commit()
        return conv

    def _transition_states(self, db, child_id):
        """检查并转换对话状态：active→idle→archived"""
        now = datetime.utcnow()
        conversations = db.query(Conversation).filter(
            Conversation.child_id == child_id,
            Conversation.status.in_(["active", "idle"])
        ).all()
        for conv in conversations:
            idle_time = now - conv.last_message_at
            if conv.status == "active" and idle_time > timedelta(minutes=self.ACTIVE_TIMEOUT_MINUTES):
                conv.status = "idle"
            elif conv.status == "idle" and idle_time > timedelta(hours=self.ARCHIVE_TIMEOUT_HOURS):
                conv.status = "archived"
        db.commit()
```

- [ ] **Step 2: 实现对话CRUD API**

`backend/app/api/conversations.py`:
- `POST /conversations` — 创建新对话
- `GET /conversations/active` — 获取活跃对话
- `GET /conversations?status=&page=&page_size=` — 列表（分页）
- `PUT /conversations/{id}/resume` — 恢复对话

- [ ] **Step 3: 实现Android对话列表**

`ConversationListScreen.kt` — 按状态分组显示对话列表（活跃 → 暂停 → 归档），点击进入聊天或恢复。
`ConversationListViewModel.kt` — 从后端获取对话列表，Room缓存。

- [ ] **Step 4: 测试并提交**

```bash
pytest tests/test_conversations.py -v
# 测试：创建对话、获取活跃、状态转换（active→idle→archived）、恢复对话、分页
git commit -m "feat: 对话生命周期管理 - 状态机 + CRUD API + Android对话列表"
```

---

## Task 8: 角色系统

**文件：**
- Modify: `backend/app/api/config.py`（characters端点）
- Create: `android/.../ui/characters/CharacterSelectScreen.kt`
- Create: `android/.../ui/characters/CharacterSelectViewModel.kt`

- [ ] **Step 1: 实现角色列表API**

`GET /config/characters` 返回4个预设角色的 id, name, avatar_url, description。从数据库读取，`init_db.py` 中已预置。

- [ ] **Step 2: 实现Android角色选择界面**

`CharacterSelectScreen.kt` — 网格布局展示4个角色卡片（头像 + 名字 + 描述），点击选择后创建新对话并跳转到聊天界面。首次启动时自动展示此界面。

- [ ] **Step 3: 提交**

```bash
git commit -m "feat: 角色系统 - 角色列表API + Android角色选择界面"
```

---

## Task 9: 记忆管理

**文件：**
- Create: `backend/app/services/memory.py`
- Create: `backend/app/services/context.py`
- Test: `backend/tests/test_memory.py`
- Test: `backend/tests/test_context.py`

- [ ] **Step 1: 实现关键记忆提取**

`backend/app/services/memory.py`:
```python
class MemoryService:
    EXTRACTION_PROMPT = """从以下对话中提取关于儿童的重要信息。
返回JSON格式，只包含确定的信息，不要猜测。
示例：{"name": "小明", "favorite_animal": "恐龙", "has_pet": "是的，一只叫旺财的小狗"}
如果没有新信息，返回空对象 {}。

对话内容：
{conversation_text}"""

    async def extract_memories(self, db, child_id, recent_messages: list[Message]):
        """每5轮对话触发一次记忆提取。"""
        text = "\n".join([f"{m.role}: {m.content}" for m in recent_messages])
        response = await self.llm_service.single_call(
            self.EXTRACTION_PROMPT.format(conversation_text=text)
        )
        new_memories = json.loads(response)
        for key, value in new_memories.items():
            existing = db.query(KeyMemory).filter(
                KeyMemory.child_id == child_id,
                KeyMemory.key == key
            ).first()
            if existing:
                existing.value = str(value)
                existing.updated_at = datetime.utcnow()
            else:
                db.add(KeyMemory(id=str(uuid4()), child_id=child_id, key=key, value=str(value)))
        db.commit()
```

- [ ] **Step 2: 实现对话摘要**

当对话超过20条消息时，使用LLM对较早消息进行摘要，存储到 `ConversationSummary`。使用乐观锁：检查 `message_count_at_summary` 与当前消息数是否一致。

- [ ] **Step 3: 实现上下文组装 + Token预算裁剪**

`backend/app/services/context.py`:
```python
class ContextService:
    def build_context(self, db, child_id, conversation_id, character_id, user_text) -> list[dict]:
        """按优先级组装上下文，超限时裁剪。"""
        # 1. 系统提示词（不可裁剪）
        character = db.query(AICharacter).get(character_id)
        system_prompt = character.system_prompt + SAFETY_RULES_PROMPT
        system_tokens = count_tokens(system_prompt)

        # 2. 关键记忆
        memories = db.query(KeyMemory).filter(KeyMemory.child_id == child_id).all()
        memory_text = "关于这个孩子的信息：\n" + "\n".join([f"- {m.key}: {m.value}" for m in memories])

        # 3. 对话摘要
        summary = db.query(ConversationSummary).filter(
            ConversationSummary.conversation_id == conversation_id
        ).first()
        summary_text = summary.summary_text if summary else ""

        # 4. 最近消息
        recent = db.query(Message).filter(
            Message.conversation_id == conversation_id
        ).order_by(Message.timestamp.desc()).limit(10).all()
        recent.reverse()

        # 5. Token预算计算和裁剪
        user_tokens = count_tokens(user_text)
        budget = settings.context_window_tokens - system_tokens - user_tokens - settings.max_llm_tokens

        # 按优先级分配：摘要 → 早期消息 → 关键记忆
        result = [{"role": "system", "content": system_prompt}]

        # 尝试加入关键记忆
        memory_tokens = count_tokens(memory_text)
        if memory_tokens <= budget:
            result.append({"role": "system", "content": memory_text})
            budget -= memory_tokens
        else:
            # 裁剪：只保留最近N条记忆
            trimmed = self._trim_memories(memories, budget)
            result.append({"role": "system", "content": trimmed})
            budget -= count_tokens(trimmed)

        # 尝试加入摘要
        if summary_text:
            summary_tokens = count_tokens(summary_text)
            if summary_tokens <= budget:
                result.append({"role": "system", "content": f"之前对话的摘要：{summary_text}"})
                budget -= summary_tokens

        # 加入最近消息（从最早的开始，超限时裁剪最早的消息）
        for msg in recent:
            msg_tokens = count_tokens(msg.content)
            if msg_tokens > budget:
                break
            result.append({"role": msg.role, "content": msg.content})
            budget -= msg_tokens

        # 6. 当前用户消息（不可裁剪）
        result.append({"role": "user", "content": user_text})

        return result
```

- [ ] **Step 4: 测试记忆和上下文**

```bash
pytest tests/test_memory.py tests/test_context.py -v
# 测试：记忆提取（mock LLM）、摘要生成、上下文组装、Token裁剪各层级、极端情况
```

- [ ] **Step 5: 提交**

```bash
git commit -m "feat: 记忆管理 - 关键记忆提取 + 对话摘要 + 上下文Token预算裁剪"
```

---

## Task 10: 内容安全 + 使用时长限制

**文件：**
- Create: `backend/app/services/safety.py`
- Create: `backend/app/services/usage.py`
- Test: `backend/tests/test_safety.py`
- Test: `backend/tests/test_usage.py`

- [ ] **Step 1: 实现内容安全服务**

`backend/app/services/safety.py`:
```python
class SafetyService:
    BLOCKED_KEYWORDS = [...]  # 不当主题关键词列表，可从配置文件加载

    def check_input(self, text: str) -> SafetyResult:
        """检查用户输入，返回是否安全。"""
        for keyword in self.BLOCKED_KEYWORDS:
            if keyword in text:
                return SafetyResult(safe=False, reason="keyword", redirect_topic=self._get_safe_redirect())
        return SafetyResult(safe=True)

    def check_output(self, text: str) -> SafetyResult:
        """检查LLM输出，返回是否安全。如果检测到不当内容，记录审计日志但不修改（已经生成了）。"""
        for keyword in self.BLOCKED_KEYWORDS:
            if keyword in text:
                log_safety_event("output_blocked", text[:100])
                return SafetyResult(safe=False, reason="keyword")
        return SafetyResult(safe=True)

    def _get_safe_redirect(self) -> str:
        """返回一个安全的话题引导回复。"""
        redirects = [
            "我们来聊点别的吧！你知道吗，猫咪一天要睡16个小时呢！",
            "换个话题吧！要不要听一个关于太空的故事？",
            "这个话题毛仔不太懂，我们聊聊你最喜欢的动物是什么？"
        ]
        return random.choice(redirects)

@dataclass
class SafetyResult:
    safe: bool
    reason: str = ""
    redirect_topic: str = ""
```

- [ ] **Step 2: 实现使用时长追踪服务**

`backend/app/services/usage.py`:
```python
class UsageService:
    async def check_limits(self, child_id: str, db: Session) -> None:
        """检查使用时长限制，超限则抛出HTTPException。"""
        settings = db.query(ParentSettings).get(1)

        # 检查禁用时段
        self._check_blocked_hours(settings)

        # 获取或创建活跃会话
        session = self._get_or_create_session(child_id, db)

        # 计算当日累计使用时长
        today = date.today()
        usage = db.query(UsageLog).filter(
            UsageLog.child_id == child_id,
            UsageLog.date == today
        ).first()

        total_minutes = usage.total_minutes if usage else 0
        session_minutes = (datetime.utcnow() - session.started_at).total_seconds() / 60

        if total_minutes >= settings.daily_limit_min:
            raise UsageLimitException("USAGE_DAILY_LIMIT", "今天的时间用完啦")
        if session_minutes >= settings.session_limit_min:
            raise UsageLimitException("USAGE_SESSION_LIMIT", "这次聊天时间到啦")

        # 计算剩余时间，用于预警Header
        remaining = min(
            settings.daily_limit_min - total_minutes,
            settings.session_limit_min - session_minutes
        )
        return remaining

    def _check_blocked_hours(self, settings):
        """检查当前是否在禁用时段内（服务器时区，正确处理跨午夜）。"""
        if not settings.blocked_hours_start or not settings.blocked_hours_end:
            return
        now = datetime.now().time()
        start = datetime.strptime(settings.blocked_hours_start, "%H:%M").time()
        end = datetime.strptime(settings.blocked_hours_end, "%H:%M").time()
        if start > end:
            # 跨午夜：21:00-07:00 → now >= 21:00 OR now < 07:00
            if now >= start or now < end:
                raise BlockedHoursException()
        else:
            if start <= now < end:
                raise BlockedHoursException()
```

- [ ] **Step 3: 实现使用时长预警Header中间件**

在 `/chat/stream` 响应中注入 `X-Usage-Warning: remaining_minutes=N` Header（当剩余 ≤ 5分钟时）。

- [ ] **Step 4: 测试并提交**

```bash
pytest tests/test_safety.py tests/test_usage.py -v
# 安全：关键词匹配、安全通过、引导回复生成
# 时长：每日/会话限制、禁用时段（正常+跨午夜）、预警阈值、活动超时
git commit -m "feat: 内容安全 + 使用时长限制 - 关键词过滤 + 后端强制时长检查"
```

---

## Task 11: Android家长控制面板

**文件：**
- Create: `android/.../ui/parent/PinEntryScreen.kt`
- Create: `android/.../ui/parent/ParentDashboardScreen.kt`
- Create: `android/.../ui/parent/ParentViewModel.kt`
- Create: `android/.../data/repository/ParentRepository.kt`
- Create: `android/.../data/remote/dto/ParentDto.kt`

- [ ] **Step 1: 实现PIN码输入界面**

`PinEntryScreen.kt` — 4位数字PIN输入，调用 `POST /parent/auth` 验证，错误提示"密码不对哦，再试试"。

- [ ] **Step 2: 实现家长面板主界面**

`ParentDashboardScreen.kt` — Material 3 设置页面：
- 使用时长设置（每日限制、会话限制）— Slider组件
- 禁用时段设置 — 时间选择器
- 使用统计 — 简单柱状图（按日/按周）
- 对话历史 — 列表 + 点击查看详情
- LLM API配置 — URL和模型名称输入框
- 讯飞凭证配置

- [ ] **Step 3: 实现ParentViewModel**

管理设置状态、加载/保存设置、获取使用统计和对话历史。

- [ ] **Step 4: 提交**

```bash
git commit -m "feat: Android家长控制面板 - PIN验证 + 设置 + 使用统计 + 对话历史"
```

---

## Task 12: 首次启动流程 + 错误处理集成

**文件：**
- Create: `android/.../ui/onboarding/WelcomeScreen.kt`
- Modify: 所有ViewModel（增加错误状态处理）
- Modify: 所有Screen（增加错误UI状态）

- [ ] **Step 1: 实现首次启动引导**

`WelcomeScreen.kt` — 检查是否已有设备令牌：
- 无令牌：显示欢迎页 → 输入孩子名字 → 调用 `/device/register` → 进入角色选择
- 有令牌：直接检查活跃对话 → 跳转到聊天或角色选择

- [ ] **Step 2: 统一错误状态处理**

所有ViewModel中统一处理错误：
- 网络错误 → 显示"毛仔现在连不上"
- API错误码 → 根据错误码显示对应的友好文案（来自规格文档的错误提示表）
- SSE断流 → 保留已显示的文字 + "回答被打断了"
- 超时 → 自动重试或显示重试按钮

- [ ] **Step 3: Android端使用时长预警**

在 ChatViewModel 中检查响应Header `X-Usage-Warning`，剩余 ≤ 5分钟时显示倒计时Toast，达到限制时显示结束画面。

- [ ] **Step 4: 端到端手动测试**

完整流程验证：
1. 首次启动 → 注册 → 选角色 → 语音对话
2. 切换角色 → 新对话
3. 退出后重新进入 → 恢复对话
4. 禁用网络 → 友好提示 → 恢复网络 → 继续
5. 进入家长面板 → 修改设置 → 验证生效
6. 使用时长达到限制 → 结束画面

- [ ] **Step 5: 提交**

```bash
git commit -m "feat: 首次启动引导 + 全局错误处理 + 使用时长预警集成"
```

---

## 自检清单

| 规格要求 | 对应Task | 状态 |
|----------|----------|------|
| 语音对话（按句TTS） | Task 4, 5, 6 | ✅ |
| 对话生命周期 | Task 7 | ✅ |
| AI角色系统 | Task 8 | ✅ |
| 记忆管理（三层） | Task 9 | ✅ |
| 家长控制面板 | Task 11 | ✅ |
| 内容安全 | Task 10 | ✅ |
| 错误处理 | Task 12 | ✅ |
| 使用时长限制 | Task 10, 12 | ✅ |
| 音频格式规范 | Task 4, 6 | ✅ |
| 认证与授权 | Task 3 | ✅ |
| Token预算裁剪 | Task 9 | ✅ |
| 设备注册 | Task 3 | ✅ |
| 后端数据模型 | Task 1 | ✅ |
| Android数据模型 | Task 2 | ✅ |
| 分页API | Task 7 | ✅ |
| 离线缓存 | Task 2, 6 (Room) | ✅ |
