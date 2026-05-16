<div align="center">

# Mowzi

**儿童 AI 语音伴侣**

[English](#english) | [中文](#中文)

</div>

---

## 中文

Mowzi（毛仔）是一款面向 **4-9 岁儿童**的 AI 语音伴侣应用，运行在 Android 平板电脑上。孩子可以自然地与 AI 角色语音对话，家长通过受保护的控制面板进行监督。

### 为什么做这个

- **语音优先** — 按住说话，松开即回复，不需要识字
- **按句 TTS** — 边生成边播放，首句语音延迟 2-4 秒，而不是等 10+ 秒听完整个回答
- **家长掌控** — 使用时长限制、禁用时段、对话历史，全部后端强制执行
- **服务端安全** — 三层内容过滤，API 密钥零暴露，应用端从不直接调用第三方服务

### 架构

```
┌──────────────────────────┐
│    Android (Kotlin)       │
│    Compose + Hilt + Room  │
└────────────┬─────────────┘
             │ HTTPS
┌────────────▼─────────────┐
│  FastAPI 后端             │
│  SQLite · 记忆 · 安全     │
└──┬──────────┬──────────┬──┘
   │          │          │
   ▼          ▼          ▼
 LLM API   讯飞 STT   讯飞 TTS
```

### 核心特性

| 特性 | 说明 |
|------|------|
| **AI 角色系统** | 猫头鹰医生、故事兔兔、搞笑机器人、冒险猫咪 — 各有性格和专属音色 |
| **混合记忆** | 关键信息提取 + 对话摘要 + 滑动窗口，AI 记得孩子的名字和喜好 |
| **流式对话** | SSE 推送文本 + 按句合成语音，边生成边播放 |
| **家长控制** | 每日/单次时长限制、禁用时段、对话历史查看、使用统计 |
| **内容安全** | 系统提示词约束 + 关键词过滤 + LLM 自我守卫，三层防护 |
| **Token 预算** | 自动裁剪低优先级上下文，确保不超窗口限制 |

### 技术栈

**Android** — Jetpack Compose · Material 3 · MVVM · Hilt · Retrofit · Room · AudioRecord/MediaPlayer

**后端** — Python FastAPI · SQLAlchemy (SQLite) · OpenAI SDK · 讯飞语音听写/合成 WebSocket API

### 项目状态

早期开发阶段。详细设计规格见 [`docs/specs/`](docs/specs/)。

---

<a id="english"></a>

## English

Mowzi is an AI voice companion for **children aged 4-9**, running on Android tablets. Kids talk naturally with AI characters; parents supervise through a PIN-protected control panel.

### Why this exists

- **Voice-first** — Press and speak, release to send; no reading required
- **Per-sentence TTS** — Audio starts playing in 2-4 seconds, not 10+ seconds after the full response
- **Parental control** — Daily/session time limits, blocked hours, conversation history — enforced server-side
- **Server-side safety** — Three-layer content filtering; zero API key exposure to the client

### Architecture

```
┌──────────────────────────┐
│    Android (Kotlin)       │
│    Compose + Hilt + Room  │
└────────────┬─────────────┘
             │ HTTPS
┌────────────▼─────────────┐
│  FastAPI Backend          │
│  SQLite · Memory · Safety │
└──┬──────────┬──────────┬──┘
   │          │          │
   ▼          ▼          ▼
 LLM API   iFlytek STT  iFlytek TTS
```

### Key Features

| Feature | Description |
|---------|-------------|
| **AI Characters** | Dr. Owl, Story Bunny, Funny Robot, Adventure Cat — each with unique personality and voice |
| **Hybrid Memory** | Key fact extraction + conversation summary + sliding window — AI remembers the child's name and preferences |
| **Streaming Chat** | SSE text + per-sentence TTS synthesis, play while generating |
| **Parental Controls** | Daily/session time limits, blocked hours, conversation history, usage stats |
| **Content Safety** | System prompt rules + keyword filtering + LLM self-guard — three layers of protection |
| **Token Budget** | Auto-trims low-priority context to stay within model window limits |

### Tech Stack

**Android** — Jetpack Compose · Material 3 · MVVM · Hilt · Retrofit · Room · AudioRecord/MediaPlayer

**Backend** — Python FastAPI · SQLAlchemy (SQLite) · OpenAI SDK · iFlytek STT/TTS WebSocket API

### Project Status

Early development. See [`docs/specs/`](docs/specs/) for the full design specification.
