# Mowzi（毛仔）- 儿童AI语音伴侣设计规格说明书

## 概述

**产品名**：Mowzi（中文名：毛仔）

一款面向4-9岁儿童、运行在Android平板电脑上的语音AI伴侣应用。应用提供自然的语音对话体验——儿童与AI角色对话，获得语音回复（可选文字显示），家长通过受保护的控制面板进行监督。

## 系统架构

**Android应用（Kotlin/Compose）+ Python FastAPI后端**

应用端从不直接调用第三方API。所有外部服务调用（LLM、讯飞STT/TTS）均通过后端代理，后端持有所有API密钥、执行内容安全检查、管理对话记忆。

```
┌──────────────────────────┐
│    Android应用 (Kotlin)   │
│    Jetpack Compose + Hilt│
│    仅与后端通信           │
└────────────┬─────────────┘
             │ HTTPS
┌────────────▼─────────────┐     ┌──────────────┐
│  后端 (FastAPI)          │────▶│ LLM API      │
│  - API密钥管理           │────▶│ (OpenAI兼容)  │
│  - 记忆管理              │     └──────────────┘
│  - 内容安全检查          │     ┌──────────────┐
│  - 使用时长统计          │────▶│ 讯飞语音听写  │
│  - SQLite存储            │     └──────────────┘
│                          │     ┌──────────────┐
│                          │────▶│ 讯飞语音合成  │
│                          │     └──────────────┘
└──────────────────────────┘
```

部署在云服务器上，可从任何地方访问。

## 技术栈

### Android应用

| 组件 | 选型 | 选型理由 |
|------|------|----------|
| UI | Jetpack Compose + Material 3 | 现代Android UI，适合儿童操作的大按钮 |
| 架构 | MVVM + Repository模式 | 清晰的职责分离 |
| 依赖注入 | Hilt | Google推荐的DI框架 |
| 网络 | Retrofit + OkHttp | 成熟的HTTP客户端，用于后端通信 |
| 音频 | AudioRecord（录音）+ MediaPlayer（播放）+ android-opus-codec（Opus压缩） | Android内置API + 第三方Opus编解码库 |
| 本地存储 | Room（对话缓存）+ DataStore（设置） | 结构化数据 + 键值配置 |

### 后端

| 组件 | 选型 | 选型理由 |
|------|------|----------|
| 框架 | Python FastAPI | 开发速度快、支持异步、支持SSE流式推送 |
| 数据库 | SQLite via SQLAlchemy | 轻量级，适用于单家庭使用场景 |
| LLM客户端 | OpenAI Python SDK（兼容模式） | 兼容任何OpenAI兼容的接口端点 |
| 语音识别 | 讯飞语音听写WebSocket API | 中文语音识别效果最佳，支持流式，免费额度（500次/天） |
| 语音合成 | 讯飞语音合成WebSocket API | 多音色预设，支持MP3/Opus输出，与STT同一平台 |
| 认证 | 简单设备令牌 + 家长PIN码 | 适用于单家庭场景的轻量级认证 |

## 核心功能

### 1. 语音对话

主要交互流程：

```
儿童按下麦克风 → 说话 → 应用录制PCM音频
  → POST /api/v1/chat/stt { audio (multipart) }
  → 后端代理至讯飞STT → 返回文字
  → POST /api/v1/chat/stream { conversation_id, text }
  → 后端组装上下文（系统提示词 + 记忆 + 近期消息）
  → 后端调用LLM（流式）→ SSE推送文本片段至应用
  → 每积累一个完整句子（句号/问号/感叹号/省略号）→ 后端调用讯飞TTS → SSE推送句子音频
  → 应用逐句显示文字 + 逐句播放音频（边生成边播放）
```

**关键行为：**
- 语音是默认输出方式；文字作为辅助显示
- LLM流式输出：文字随生成实时显示
- **按句TTS**：LLM流式输出时，以句号、问号、感叹号、省略号为分割点，每积累一个完整句子立即触发TTS合成，通过SSE推送音频。首次语音延迟从10-20秒降至2-4秒
- 按住麦克风按钮录音，松开发送
- 可选文字输入框，供喜欢打字的儿童使用
- 应用端在LLM响应期间禁用麦克风按钮，防止并发请求

### 2. 对话生命周期

**对话状态机：**

```
创建 → active（正在对话）
         ↓ 无活动超过30分钟
        idle（暂停）
         ↓ 无活动超过24小时 / 手动归档
        archived（归档）
```

**创建与恢复：**
- 对话由服务端创建：`POST /api/v1/conversations { character_id }` 返回 `conversation_id`（UUID）
- 对话标题由服务端从第一条用户消息的前15个字符自动生成，LLM完成后异步替换为智能摘要标题
- 应用启动时查询 `GET /api/v1/conversations/active`，若有活跃对话则恢复，无则进入角色选择→创建新对话
- 归档对话通过 `GET /api/v1/conversations` 列表查看，可点击恢复为 active（重置 idle 计时器）

**并发保护：**
- 同一对话同时只允许一个SSE流，新请求自动取消前一个未完成的流
- 对话摘要使用乐观锁：检查 `message_count_at_summary` 与当前消息数是否一致，不一致则重新摘要

**新增API：**

```
POST /api/v1/conversations
  请求:  { character_id: str }
  响应: { conversation_id: str, created_at: datetime }

GET  /api/v1/conversations/active
  响应: { conversation: { id, character_id, title, ... } | null }

GET  /api/v1/conversations
  查询参数: ?status=active|idle|archived&page=1&page_size=20
  响应: { conversations: [...], total: int, page: int }

PUT  /api/v1/conversations/{id}/resume
  响应: { conversation: {...} }
```

### 3. AI角色系统

预设角色存储在后端数据库中：

| 角色 | 性格 | TTS音色 | 描述 |
|------|------|---------|------|
| 猫头鹰医生 | 知识渊博、耐心 | 沉稳女声 | 回答科学问题，讲解知识 |
| 故事兔兔 | 温暖、富有想象力 | 甜美女声 | 讲故事，玩想象力游戏 |
| 搞笑机器人 | 幽默、充满活力 | 古怪男声 | 笑话、谜语、趣味知识 |
| 冒险猫咪 | 勇敢、好奇 | 年轻男声 | 探索、自然、动物 |

每个角色包含：
- `id`、`name`、`avatar_url`、`system_prompt`、`tts_voice_name`
- 系统提示词包含性格设定 + 安全约束（服务端强制执行）
- 儿童可在聊天界面自由切换角色

### 4. 记忆管理

采用三种技术相结合的混合策略：

**a. 关键记忆提取**
- 后端定期提取重要信息：儿童的名字、兴趣、偏好、宠物、家庭成员
- 以结构化键值对形式存储在每个儿童档案中
- 在每次请求中作为系统消息注入

**b. 对话摘要**
- 当对话超过20条消息时，后端使用LLM对较早的消息进行摘要
- 摘要存储在数据库中，替代原始消息参与上下文构建
- 在对话结束后异步触发

**c. 滑动窗口**
- 始终包含最近10条消息（5轮对话）的原文
- 与摘要 + 关键记忆组合形成完整上下文

每次请求的上下文组装顺序：
1. 系统提示词（角色性格 + 安全规则）
2. 关于儿童的关键记忆
3. 对话摘要（压缩后的早期消息）
4. 最近10条消息（原文）
5. 当前用户消息

**Token预算与裁剪策略：**

上下文组装后必须满足LLM的token窗口限制。预留规则：`总窗口 = 系统提示词 + 拼装上下文 + 用户消息 + max_tokens（输出预留）`

- 输出预留：`max_tokens = 300`（约1-2分钟语音，足够一个完整回答）
- 系统提示词 + 安全规则：固定约200-400 token，不可裁剪
- 上下文总量上限 = `模型窗口大小 - 系统提示词token - 用户消息token - max_tokens`

当上下文总量超出上限时，按优先级从低到高裁剪：

| 裁剪顺序 | 内容 | 策略 |
|----------|------|------|
| 1（最先裁剪） | 对话摘要 | 截断至最近摘要，或完全移除 |
| 2 | 较早的滑动窗口消息 | 从第1条开始逐条移除，保留最近的消息 |
| 3 | 关键记忆 | 仅保留最近更新的N条（如名字、年龄等核心信息） |
| 不裁剪 | 系统提示词 + 安全规则 | 固定内容，永不裁剪 |
| 不裁剪 | 当前用户消息 | 永不裁剪 |

后端在每次LLM调用前计算token数（使用 tiktoken 或对应模型的tokenizer），若裁剪后仍超限则返回错误。家长配置LLM模型时，设置页面显示当前模型的上下文窗口大小供参考。

### 5. 家长控制面板

受4位PIN码保护。从应用设置中进入。

**功能：**
- 每日使用时长限制（默认：60分钟）
- 单次会话时长限制（默认：30分钟）
- 禁用时段（例如：21:00-07:00不可使用）
- 查看对话历史记录（含时间戳）
- 查看使用统计（按日/按周）
- 配置LLM API设置（URL、模型名称）
- 配置讯飞凭证

**实现方式：**
- 家长设置存储在后端数据库中
- PIN码通过后端API验证
- 设置变更立即同步

**时长限制执行模型（后端强制执行）：**

"使用时长"定义为：应用在前台且用户有交互活动（发消息、录音）的累计时间。最后一条消息后5分钟无活动则计时暂停，再次发消息时继续累计。

- 每次API请求（STT、chat/stream）后端检查 `child_id` 当日累计使用时长，超限则返回错误码 `USAGE_DAILY_LIMIT` 或 `USAGE_SESSION_LIMIT`
- 后端维护 `ActiveSession` 表追踪实时会话，会话在最后活动后30分钟自动标记为 idle
- 达到限制前5分钟和1分钟，后端在响应Header中返回 `X-Usage-Warning: remaining_minutes=N`，客户端据此显示倒计时
- 禁用时段使用服务器时区（配置时注明时区），跨午夜场景正确处理（如 `blocked_hours_start: "21:00"`, `blocked_hours_end: "07:00"` 判断逻辑为 `now >= 21:00 OR now < 07:00`）
- 所有时长限制检查在服务端完成，客户端仅负责显示友好提示，不参与限制决策

### 6. 内容安全

多层保护机制，全部在服务端执行：

**第一层 - 系统提示词（强制）**
- 每次LLM调用都在系统提示词中包含严格的行为规则
- 规则包括：使用适龄语言、禁止暴力/恐怖内容、不收集个人数据、鼓励善良
- 由于提示词在服务端构建，无法被绕过

**第二层 - 关键词过滤（备份）**
- 后端维护不当主题的屏蔽词列表
- 同时应用于用户输入和LLM输出
- 捕捉系统提示词可能遗漏的边缘情况

**第三层 - LLM自我守卫**
- 系统提示词指示LLM得体地拒绝不当请求
- 在需要时将话题引导至安全内容

### 7. 错误处理

所有API使用统一的错误响应格式：

```json
{
  "error": {
    "code": "STT_FAILED",
    "message": "语音识别失败，请再说一次"
  }
}
```

**SSE流错误规则：**
- 所有SSE流必须以 `done` 或 `error` 事件终止，确保客户端能正确清理状态
- LLM生成中途失败时，保留已生成的文本，发送 `text_done`（标记为不完整）+ `error` + `done`
- 内容安全拦截用户输入时，返回友好的引导回复（如"我们来聊点别的吧"），而非暴露拦截事实

**面向儿童的错误提示（不在界面显示技术信息）：**

| 场景 | 提示文案 | 行为 |
|------|----------|------|
| STT识别失败/置信度低 | "没听清哦，再说一次吧？" | 显示重新录音按钮 |
| 网络不可用 | "毛仔现在连不上，等一下再试" | 显示缓存的历史对话 |
| SSE流中途断开 | "回答被打断了" | 保留已显示的文字 |
| TTS合成失败 | （无提示） | 仅显示文字，不播放音频 |
| LLM调用失败 | "毛仔在想呢，等一下再来找我吧" | 显示重试按钮 |
| 使用时长已到 | "今天的时间用完啦，明天再来找毛仔吧！" | 显示结束画面 |
| 禁用时段 | "毛仔休息啦，XX点再来找我吧" | 显示倒计时到可用时段 |
| 讯飞配额耗尽 | 自动切换为文字模式，显示"现在只能打字聊天哦" | 隐藏麦克风按钮 |
| 并发请求（上一条未完成） | （静默处理） | 麦克风按钮保持禁用状态 |

**重试策略：**
- STT、TTS、LLM调用失败：后端自动重试1次，间隔1秒
- 应用端网络请求失败：指数退避，最多2次，总等待不超过10秒
- 重试仍然失败则显示上表中的友好提示

## 后端API设计

### 聊天接口

```
POST /api/v1/chat/stt
  请求:  multipart/form-data { audio: file, format: "pcm"|"opus" }
  响应: { text: str, confidence: float }

POST /api/v1/chat/stream
  请求:  { conversation_id: str, text: str, character_id: str }
  响应: SSE流
    event: text_chunk      data: { content: str }
    event: sentence_audio  data: { sentence_index: int, audio_base64: str, duration_ms: int }
    event: text_done       data: { full_text: str, message_id: str }
    event: error           data: { code: str, message: str }
    event: done            data: { message_id: str }

POST /api/v1/chat/tts
  请求:  { text: str, voice_name: str }
  响应: { audio_base64: str, duration_ms: int }
```

### 家长接口

```
POST /api/v1/parent/auth
  请求:  { pin: str }
  响应: { token: str }

GET  /api/v1/parent/settings
  响应: { daily_limit_min, session_limit_min, blocked_start, blocked_end, ... }

PUT  /api/v1/parent/settings
  请求:  { daily_limit_min?, session_limit_min?, blocked_start?, blocked_end?, ... }
  响应: { settings }

GET  /api/v1/parent/conversations
  查询参数: ?child_id=str&date_from=iso&date_to=iso
  响应: { conversations: [{ id, character_name, title, message_count, created_at }] }

GET  /api/v1/parent/conversations/{id}/messages
  响应: { messages: [{ role, content, timestamp }] }

GET  /api/v1/parent/usage
  查询参数: ?period=daily|weekly
  响应: { usage: [{ date, minutes, message_count }] }
```

### 配置接口

```
GET  /api/v1/config/characters
  响应: { characters: [{ id, name, avatar_url, description }] }

POST /api/v1/device/register
  请求:  { device_id: str, child_name?: str }
  响应: { device_token: str }
```

## 数据模型

### 后端（SQLite via SQLAlchemy）

```python
class ChildProfile(Base):
    id: str (PK)
    name: str
    device_id: str
    created_at: datetime

class Conversation(Base):
    id: str (PK)
    child_id: str (FK)
    character_id: str (FK)
    title: str
    status: str  # "active" | "idle" | "archived"
    created_at: datetime
    updated_at: datetime
    last_message_at: datetime

class Message(Base):
    id: str (PK)
    conversation_id: str (FK)
    role: str  # "user" | "assistant" | "system"
    content: str
    audio_path: str (nullable)
    timestamp: datetime

class ConversationSummary(Base):
    id: str (PK)
    conversation_id: str (FK, unique)
    summary_text: str
    message_count_at_summary: int
    updated_at: datetime

class KeyMemory(Base):
    id: str (PK)
    child_id: str (FK)
    key: str  # 例如："name"、"favorite_animal"、"has_pet"
    value: str
    updated_at: datetime

class AICharacter(Base):
    id: str (PK)
    name: str
    avatar_url: str
    description: str
    system_prompt: str
    tts_voice_name: str

class ParentSettings(Base):
    id: int (PK, 固定为1)
    pin_hash: str
    daily_limit_min: int (默认60)
    session_limit_min: int (默认30)
    blocked_hours_start: str (nullable)  # "21:00"，服务器时区
    blocked_hours_end: str (nullable)    # "07:00"，服务器时区
    llm_api_url: str
    llm_api_key: str (加密)
    llm_model: str (默认 "gpt-4o-mini")
    xfyun_app_id: str
    xfyun_api_key: str (加密)
    xfyun_api_secret: str (加密)

class ActiveSession(Base):
    id: str (PK)
    child_id: str (FK)
    started_at: datetime
    last_activity_at: datetime
    status: str  # "active" | "idle"

class UsageLog(Base):
    id: str (PK)
    child_id: str (FK)
    date: date
    total_minutes: int
    message_count: int
```

### Android应用（Room）

```kotlin
@Entity data class Conversation(
    @PrimaryKey val id: String,
    val characterId: String,
    val title: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastMessageAt: Long
)

@Entity data class CachedMessage(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val audioLocalPath: String?,
    val timestamp: Long
)

@Entity data class CharacterInfo(
    @PrimaryKey val id: String,
    val name: String,
    val avatarUrl: String,
    val description: String
)
```

## 非功能性需求

- **延迟**：STT响应 < 2秒，首个LLM token < 3秒，按句TTS首句合成 < 2秒（Wi-Fi环境，测量点为应用端，音频长度 ≤ 10秒）
- **离线**：离线时应用显示已缓存的对话；新建对话需要网络连接
- **安全**：所有API密钥静态加密；所有后端通信使用HTTPS；PIN码哈希使用bcrypt存储
- **可扩展性**：单家庭设计；SQLite即可满足（需启用WAL模式，设置 busy_timeout=5000ms）。数据库抽象层允许未来迁移至PostgreSQL

### 音频格式规范

**录音参数：**
- 采样率：16kHz，位深：16-bit，声道：单声道
- 格式：PCM（本地录音）→ Opus压缩（上传传输）
- 最大录音时长：60秒，最小录音时长：0.5秒（低于此自动丢弃）
- 静音检测：连续3秒无声自动停止录音并发送
- 上传方式：`multipart/form-data`（非base64 JSON），减少33%带宽开销

**音频全链路格式：**

| 环节 | 格式 | 说明 |
|------|------|------|
| 应用录音 | PCM 16kHz 16bit mono | Android AudioRecord |
| 上传至后端 | Opus（封装在multipart） | 客户端压缩后上传，大幅减小体积 |
| 后端→讯飞STT | PCM 16kHz 16bit mono | 后端解码Opus→PCM后转发 |
| TTS输出 | MP3 | 讯飞TTS默认输出格式 |
| SSE推送音频 | MP3 base64 | 按句推送，单句通常 < 50KB |
| 本地缓存 | 原始MP3文件 | 存储在应用私有目录，数据库记路径 |
| 后端存储 | 原始MP3文件 | 存储在文件系统，数据库记audio_path |

## 未来考虑（非MVP范围）

- 语音克隆（通过CosyVoice/GPT-SoVITS克隆家长声音）
- 同一设备上的多个儿童档案
- 多设备同步
- 离线模式，带本地TTS降级方案
- 使用时长限制预警推送通知
