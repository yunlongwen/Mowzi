# Mowzi（毛仔）- 儿童AI语音伴侣

一款面向4-9岁儿童的Android语音AI伴侣应用。儿童与AI角色对话，获得自然的语音回复，家长通过PIN码保护的控制面板进行监督。

## 技术架构

```
┌──────────────────────────────────────┐
│        Android (Kotlin/Compose)     │
│    ┌─────────────┐  ┌─────────────┐ │
│    │ 讯飞MSC SDK  │  │ 讯飞MSC SDK  │ │
│    │ STT本地听写  │  │ TTS本地合成  │ │
│    └─────────────┘  └─────────────┘ │
└────────────┬─────────────────────────┘
             │ HTTPS (仅文字)
┌────────────▼─────────────┐
│  后端 (FastAPI)          │
│  LLM流式对话 / 记忆管理   │
│  内容安全 / 使用时长统计  │
└──────────────────────────┘
```

**特点：**
- STT/TTS完全在设备端完成，首次激活后支持离线使用
- LLM流式输出 + 本地逐句TTS合成，首句延迟<2秒
- 后端仅处理文字，保护儿童隐私
- 家长PIN码保护的控制面板

## 配置

### 后端 `.env`

```bash
cd backend
cp .env.example .env
```

编辑 `.env` 填写以下配置：

```env
# LLM（支持OpenAI兼容接口，推荐MiniMax）
LLM_API_URL=https://api.minimaxi.com/v1
LLM_API_KEY=你的API密钥
LLM_MODEL=MiniMax-M2.7

# 讯飞MSC SDK（语音听写+合成）
# 在 https://console.xfyun.cn 申请，SDK已集成在项目中
XFYUN_APP_ID=你的AppID
XFYUN_API_KEY=你的APIKey
XFYUN_API_SECRET=你的APISecret
```

### 启动后端

```bash
cd backend
pip install -r requirements.txt
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

### Android APK

```bash
cd android
./gradlew assembleDebug
# 输出: app/build/outputs/apk/debug/app-debug.apk
```

Android端已内置讯飞SDK（`app_id=1b20cd0f`），无需额外配置。首次使用需联网激活SDK，激活后可离线使用。

## 技术栈

| 端 | 组件 |
|---|------|
| Android | Kotlin + Jetpack Compose + Hilt + Room + DataStore |
| 音频 | 讯飞MSC SDK（本地STT/TTS）|
| 后端 | Python FastAPI + SQLAlchemy + SSE |
| LLM | OpenAI兼容API（MiniMax/MiniMax-M2）|

## 核心API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/chat/stream` | LLM流式对话（SSE） |
| GET | `/api/v1/config/characters` | 获取AI角色列表 |
| POST | `/api/v1/conversations` | 创建对话 |
| GET | `/api/v1/parent/settings` | 家长设置（需PIN验证） |

## 目录结构

```
├── android/          # Android应用（Kotlin/Compose）
├── backend/          # 后端服务（Python/FastAPI）
│   └── app/
│       ├── api/      # API路由
│       ├── models/   # 数据库模型
│       └── services/ # 业务逻辑
└── docs/             # 设计文档和计划
```

## 开发

- 后端测试：`cd backend && pytest tests/`
- Android测试：`cd android && ./gradlew test`
- 预提交hook：backend测试 + Android测试
