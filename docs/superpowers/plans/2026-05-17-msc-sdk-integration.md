# 讯飞MSC SDK集成（STT + TTS）实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将讯飞MSC SDK集成到Android应用，实现本地语音听写（STT）和本地语音合成（TTS），替换原有的后端WebSocket代理方案。

**Architecture:** Android端通过讯飞MSC SDK（Msc.jar + libmsc.so）完成语音识别和合成。STT使用`SpeechRecognizer`（本地/在线混合引擎），TTS使用`SpeechSynthesizer`（本地XTTS引擎）。后端不再处理音频，仅负责LLM流式对话。文字通过SSE推送，TTS在应用端对每个完整句子触发本地合成。

**Tech Stack:** 讯飞MSC SDK 5+（Msc.jar + libmsc.so）、Kotlin协程、Hilt DI、Jetpack Compose StateFlow

**SDK来源:** `D:\ai-workspace\Android_aisound_exp1146_esriat_exp1149_iat1148_tts_online1148_1b20cd0f`

**凭证:** APPID=`1b20cd0f`, APIKey=`54a290b72cdf8417c2cde4b2ac2eec9`, APISecret=`ZDk3ZDhhYTc1OGExODI5MTM1MGI3MGY1`

---

## 文件结构

### SDK文件（从SDK包复制）

```
android/app/libs/
├── Msc.jar                                  # 讯飞MSC Java库（501KB）
android/app/src/main/jniLibs/
├── arm64-v8a/libmsc.so                      # 64位ARM原生库（8.7MB）
├── armeabi-v7a/libmsc.so                    # 32位ARM原生库（4.7MB）
android/app/src/main/assets/
├── iflytek/recognize.xml                    # SDK UI资源
├── iflytek/voice_bg.9.png
├── iflytek/voice_empty.png
├── iflytek/voice_full.png
├── iflytek/waiting.png
├── iflytek/warning.png
├── iat/common.jet                           # 听写资源
├── iat/sms_16k.jet                          # 16kHz听写资源
├── tts/common.jet                           # 合成通用资源
├── tts/xiaoyan.jet                          # 小燕音色
├── tts/xiaofeng.jet                         # 小峰音色
```

### 新增/修改的源码文件

```
android/app/src/main/java/com/mowzi/app/
├── speech/
│   ├── XfyunSpeechService.kt                # 讯飞SDK统一服务（STT + TTS）
│   └── SpeechModule.kt                      # Hilt DI模块，提供XfyunSpeechService
├── MowziApp.kt                              # 修改：添加SDK初始化
├── ui/chat/
│   ├── ChatViewModel.kt                     # 修改：STT改用本地SDK，TTS改用本地合成
│   └── ChatScreen.kt                        # 修改：移除sentence_audio处理
├── data/repository/
│   └── ChatRepository.kt                    # 修改：移除speechToText网络调用和Opus编码
├── data/remote/
│   └── MowziApi.kt                          # 修改：移除/chat/stt端点
├── di/
│   └── RepositoryModule.kt                  # 修改：提供XfyunSpeechService
├── audio/
│   ├── AudioRecorder.kt                     # 保留，不变
│   ├── AudioPlayer.kt                       # 修改：支持PCM播放（TTS输出格式）
│   └── OpusEncoder.kt                       # 删除（不再需要Opus编码）
android/app/src/main/AndroidManifest.xml     # 修改：添加SDK所需权限
android/app/build.gradle.kts                 # 修改：添加Msc.jar依赖
android/app/src/test/java/com/mowzi/app/speech/
├── XfyunSpeechServiceTest.kt                # STT/TTS服务单元测试
├── ChatViewModelWithSpeechTest.kt           # ChatViewModel集成测试
```

### 后端清理

```
backend/app/services/xfyun_stt.py            # 标记废弃（保留但不再使用）
backend/app/services/xfyun_tts.py            # 标记废弃（保留但不再使用）
backend/app/api/chat.py                      # 修改：移除/chat/stt端点
backend/app/schemas/chat.py                  # 修改：移除SttResponse
```

---

## Task 1: SDK文件复制与构建配置

**Files:**
- Modify: `android/app/build.gradle.kts`
- Modify: `android/app/src/main/AndroidManifest.xml`
- Copy: SDK文件到项目目录

- [ ] **Step 1: 复制SDK文件到项目**

```bash
# 创建目标目录
mkdir -p android/app/src/main/jniLibs/arm64-v8a
mkdir -p android/app/src/main/jniLibs/armeabi-v7a
mkdir -p android/app/src/main/assets/iflytek
mkdir -p android/app/src/main/assets/iat
mkdir -p android/app/src/main/assets/tts

SDK_DIR="D:/ai-workspace/Android_aisound_exp1146_esriat_exp1149_iat1148_tts_online1148_1b20cd0f"

# 复制JAR和SO文件
cp "$SDK_DIR/libs/Msc.jar" android/app/libs/
cp "$SDK_DIR/libs/arm64-v8a/libmsc.so" android/app/src/main/jniLibs/arm64-v8a/
cp "$SDK_DIR/libs/armeabi-v7a/libmsc.so" android/app/src/main/jniLibs/armeabi-v7a/

# 复制assets
cp "$SDK_DIR/assets/iflytek/"* android/app/src/main/assets/iflytek/

# 复制识别和合成资源
cp "$SDK_DIR/res/iat/"* android/app/src/main/assets/iat/
cp "$SDK_DIR/res/tts/"* android/app/src/main/assets/tts/
```

- [ ] **Step 2: 修改build.gradle.kts添加Msc.jar依赖**

在 `android/app/build.gradle.kts` 的 `dependencies` 块中添加：

```kotlin
    // 讯飞MSC SDK（语音听写 + 语音合成）
    implementation(files("libs/Msc.jar"))
```

- [ ] **Step 3: 添加SDK所需权限到AndroidManifest.xml**

在 `android/app/src/main/AndroidManifest.xml` 的 `<manifest>` 块中添加：

```xml
    <!-- 讯飞MSC SDK权限 -->
    <uses-permission android:name="android.permission.READ_PHONE_STATE" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
```

- [ ] **Step 4: 验证编译通过**

```bash
cd android && ./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 提交**

```bash
git add android/app/libs/Msc.jar android/app/src/main/jniLibs/ android/app/src/main/assets/ android/app/build.gradle.kts android/app/src/main/AndroidManifest.xml
git commit -m "feat: 添加讯飞MSC SDK文件和构建配置"
```

---

## Task 2: SDK初始化（Application类）

**Files:**
- Modify: `android/app/src/main/java/com/mowzi/app/MowziApp.kt`
- Add: `android/app/src/main/res/values/strings.xml` 中的 `app_id` 字符串

- [ ] **Step 1: 添加appid字符串资源**

在 `android/app/src/main/res/values/strings.xml` 中添加：

```xml
    <string name="app_id">1b20cd0f</string>
```

- [ ] **Step 2: 修改MowziApp.kt添加SDK初始化**

```kotlin
package com.mowzi.app

import android.app.Application
import com.iflytek.cloud.SpeechConstant
import com.iflytek.cloud.SpeechUtility
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MowziApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initXfyunSdk()
    }

    private fun initXfyunSdk() {
        val param = StringBuilder()
            .append("appid=${getString(R.string.app_id)}")
            .append(",")
            .append("${SpeechConstant.ENGINE_MODE}=${SpeechConstant.MODE_MSC}")
            .toString()
        SpeechUtility.createUtility(this, param)
    }
}
```

- [ ] **Step 3: 验证SDK初始化无崩溃**

在设备上运行应用，检查Logcat无讯飞SDK初始化错误。

Expected: 应用正常启动，Logcat可见MSC初始化成功日志

- [ ] **Step 4: 提交**

```bash
git add android/app/src/main/java/com/mowzi/app/MowziApp.kt android/app/src/main/res/values/strings.xml
git commit -m "feat: 在Application中初始化讯飞MSC SDK"
```

---

## Task 3: XfyunSpeechService封装（STT + TTS）

**Files:**
- Create: `android/app/src/main/java/com/mowzi/app/speech/XfyunSpeechService.kt`
- Create: `android/app/src/main/java/com/mowzi/app/speech/SpeechModule.kt`
- Test: `android/app/src/test/java/com/mowzi/app/speech/XfyunSpeechServiceTest.kt`

- [ ] **Step 1: 编写XfyunSpeechService测试**

`android/app/src/test/java/com/mowzi/app/speech/XfyunSpeechServiceTest.kt`:

```kotlin
package com.mowzi.app.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class XfyunSpeechServiceTest {

    @Test
    fun `default voice name is xiaoyan`() {
        val service = XfyunSpeechService(
            appId = "test_app_id",
            engineType = "local"
        )
        assertEquals("xiaoyan", service.defaultVoiceName)
    }

    @Test
    fun `engine type defaults to local`() {
        val service = XfyunSpeechService(
            appId = "test_app_id"
        )
        assertEquals("local", service.engineType)
    }

    @Test
    fun `voice name can be customized`() {
        val service = XfyunSpeechService(
            appId = "test_app_id",
            defaultVoiceName = "xiaofeng"
        )
        assertEquals("xiaofeng", service.defaultVoiceName)
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
cd android && ./gradlew test --tests "com.mowzi.app.speech.XfyunSpeechServiceTest"
```

Expected: FAIL（XfyunSpeechService类不存在）

- [ ] **Step 3: 实现XfyunSpeechService**

`android/app/src/main/java/com/mowzi/app/speech/XfyunSpeechService.kt`:

```kotlin
package com.mowzi.app.speech

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.iflytek.cloud.*
import com.iflytek.cloud.util.ResourceUtil
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resumeWithException

data class SttResult(val text: String, val confidence: Float)

@Singleton
class XfyunSpeechService @Inject constructor(
    private val context: Context,
    val appId: String = "1b20cd0f",
    val engineType: String = "local",
    val defaultVoiceName: String = "xiaoyan"
) {
    companion object {
        private const val TAG = "XfyunSpeechService"
    }

    // ---- STT (语音听写) ----

    fun recognizeFromStream(audioFlow: Flow<ShortArray>): Flow<String> = callbackFlow {
        val recognizer = SpeechRecognizer.createRecognizer(context, null)
            ?: throw IllegalStateException("SpeechRecognizer创建失败，请检查libmsc.so是否正确放置")

        configureRecognizer(recognizer)

        val resultBuffer = StringBuilder()

        recognizer.startListening(object : RecognizerListener {
            override fun onBeginOfSpeech() {
                Log.d(TAG, "STT: 开始说话")
            }

            override fun onEndOfSpeech() {
                Log.d(TAG, "STT: 结束说话")
            }

            override fun onResult(result: RecognizerResult, isLast: Boolean) {
                val text = parseIatResult(result.resultString)
                if (text.isNotEmpty()) {
                    resultBuffer.append(text)
                    trySend(text)
                }
                if (isLast) {
                    Log.d(TAG, "STT最终结果: ${resultBuffer}")
                    close()
                }
            }

            override fun onError(error: SpeechError) {
                Log.e(TAG, "STT错误: ${error.errorCode} ${error.errorDescription}")
                close(Exception("语音识别失败: ${error.errorDescription}"))
            }

            override fun onVolumeChanged(volume: Int, data: ByteArray) {}

            override fun onEvent(eventType: Int, arg1: Int, arg2: Int, obj: Bundle?) {}
        })

        // 将音频流写入识别器
        try {
            val buf = ByteArray(1280)
            audioFlow.collect { shorts ->
                // ShortArray -> ByteArray (16-bit PCM little-endian)
                for (i in shorts.indices) {
                    buf[i * 2] = (shorts[i].toInt() and 0xFF).toByte()
                    buf[i * 2 + 1] = (shorts[i].toInt() shr 8).toByte()
                }
                val len = shorts.size * 2
                recognizer.writeAudio(buf, 0, len)
            }
            recognizer.stopListening()
        } catch (e: Exception) {
            recognizer.cancel()
            close(e)
        }

        awaitClose { recognizer.destroy() }
    }

    suspend fun recognizeFromPcm(pcmData: ByteArray): SttResult =
        suspendCancellableCoroutine { continuation ->
            val recognizer = SpeechRecognizer.createRecognizer(context, null)
                ?: throw IllegalStateException("SpeechRecognizer创建失败")

            configureRecognizer(recognizer)
            recognizer.setParameter(SpeechConstant.AUDIO_SOURCE, "-1")

            val resultBuffer = StringBuilder()
            var confidenceSum = 0f
            var confidenceCount = 0

            recognizer.startListening(object : RecognizerListener {
                override fun onBeginOfSpeech() {}
                override fun onEndOfSpeech() {}

                override fun onResult(result: RecognizerResult, isLast: Boolean) {
                    val text = parseIatResult(result.resultString)
                    if (text.isNotEmpty()) {
                        resultBuffer.append(text)
                    }
                    if (isLast) {
                        recognizer.destroy()
                        val avgConf = if (confidenceCount > 0) confidenceSum / confidenceCount else 0.5f
                        if (continuation.isActive) {
                            continuation.resume(SttResult(resultBuffer.toString(), avgConf))
                        }
                    }
                }

                override fun onError(error: SpeechError) {
                    recognizer.destroy()
                    if (continuation.isActive) {
                        continuation.resumeWithException(Exception("语音识别失败: ${error.errorDescription}"))
                    }
                }

                override fun onVolumeChanged(volume: Int, data: ByteArray) {}
                override fun onEvent(eventType: Int, arg1: Int, arg2: Int, obj: Bundle?) {}
            })

            // 分帧发送PCM数据
            val frameSize = 1280
            var offset = 0
            while (offset < pcmData.size) {
                val end = minOf(offset + frameSize, pcmData.size)
                val frame = pcmData.copyOfRange(offset, end)
                recognizer.writeAudio(frame, 0, frame.size)
                offset = end
            }
            recognizer.stopListening()

            continuation.invokeOnCancellation { recognizer.cancel() }
        }

    private fun configureRecognizer(recognizer: SpeechRecognizer) {
        recognizer.setParameter(SpeechConstant.PARAMS, null)
        recognizer.setParameter(SpeechConstant.ENGINE_TYPE, engineType)
        recognizer.setParameter(SpeechConstant.RESULT_TYPE, "json")

        if (engineType == SpeechConstant.TYPE_LOCAL) {
            recognizer.setParameter(
                ResourceUtil.ASR_RES_PATH,
                getResourcePath("iat")
            )
        }

        recognizer.setParameter(SpeechConstant.LANGUAGE, "zh_cn")
        recognizer.setParameter(SpeechConstant.ACCENT, "mandarin")
        recognizer.setParameter(SpeechConstant.VAD_BOS, "4000")
        recognizer.setParameter(SpeechConstant.VAD_EOS, "1000")
        recognizer.setParameter(SpeechConstant.ASR_PTT, "1")
    }

    // ---- TTS (语音合成) ----

    suspend fun synthesize(text: String, voiceName: String = defaultVoiceName): ByteArray? =
        suspendCancellableCoroutine { continuation ->
            val synthesizer = SpeechSynthesizer.createSynthesizer(context, null)
                ?: throw IllegalStateException("SpeechSynthesizer创建失败")

            configureSynthesizer(synthesizer, voiceName)

            val audioChunks = mutableListOf<ByteArray>()

            // 使用synthesizeToUri保存到临时文件，再读取PCM
            val tempFile = java.io.File.createTempFile("tts_", ".pcm", context.cacheDir)

            val code = synthesizer.synthesizeToUri(text, tempFile.absolutePath, object : SynthesizerListener {
                override fun onSpeakBegin() {}
                override fun onSpeakPaused() {}
                override fun onSpeakResumed() {}
                override fun onBufferProgress(percent: Int, beginPos: Int, endPos: Int, info: String?) {}

                override fun onSpeakProgress(percent: Int, beginPos: Int, endPos: Int) {}

                override fun onCompleted(error: SpeechError?) {
                    synthesizer.destroy()
                    if (error != null) {
                        tempFile.delete()
                        if (continuation.isActive) {
                            continuation.resumeWithException(Exception("TTS合成失败: ${error.errorDescription}"))
                        }
                        return
                    }
                    val pcmData = if (tempFile.exists()) tempFile.readBytes() else null
                    tempFile.delete()
                    if (continuation.isActive) {
                        continuation.resume(pcmData)
                    }
                }

                override fun onEvent(eventType: Int, arg1: Int, arg2: Int, obj: Bundle?) {}
            })

            if (code != ErrorCode.SUCCESS) {
                synthesizer.destroy()
                tempFile.delete()
                if (continuation.isActive) {
                    continuation.resumeWithException(Exception("TTS启动失败: code=$code"))
                }
            }

            continuation.invokeOnCancellation {
                synthesizer.stopSpeaking()
                synthesizer.destroy()
                tempFile.delete()
            }
        }

    fun startSpeaking(text: String, voiceName: String = defaultVoiceName): Int {
        val synthesizer = SpeechSynthesizer.createSynthesizer(context, null)
            ?: return -1
        configureSynthesizer(synthesizer, voiceName)
        return synthesizer.startSpeaking(text, object : SynthesizerListener {
            override fun onSpeakBegin() {}
            override fun onSpeakPaused() {}
            override fun onSpeakResumed() {}
            override fun onBufferProgress(percent: Int, beginPos: Int, endPos: Int, info: String?) {}
            override fun onSpeakProgress(percent: Int, beginPos: Int, endPos: Int) {}
            override fun onCompleted(error: SpeechError?) {
                synthesizer.destroy()
            }
            override fun onEvent(eventType: Int, arg1: Int, arg2: Int, obj: Bundle?) {}
        })
    }

    private fun configureSynthesizer(synthesizer: SpeechSynthesizer, voiceName: String) {
        synthesizer.setParameter(SpeechConstant.PARAMS, null)
        synthesizer.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_LOCAL)
        synthesizer.setParameter(ResourceUtil.TTS_RES_PATH, getResourcePath("tts"))
        synthesizer.setParameter(SpeechConstant.VOICE_NAME, voiceName)
        synthesizer.setParameter(SpeechConstant.SPEED, "50")
        synthesizer.setParameter(SpeechConstant.PITCH, "50")
        synthesizer.setParameter(SpeechConstant.VOLUME, "50")
        synthesizer.setParameter(SpeechConstant.KEY_REQUEST_FOCUS, "true")
    }

    // ---- 工具方法 ----

    private fun getResourcePath(type: String): String {
        val commonPath = ResourceUtil.generateResourcePath(
            context, ResourceUtil.RESOURCE_TYPE.assets, "$type/common.jet"
        )
        val voicePath = ResourceUtil.generateResourcePath(
            context, ResourceUtil.RESOURCE_TYPE.assets, "$type/${if (type == "iat") "sms_16k" else defaultVoiceName}.jet"
        )
        return "$commonPath;$voicePath"
    }

    private fun parseIatResult(json: String): String {
        val sb = StringBuilder()
        val result = JSONObject(json)
        val ws = result.optJSONArray("ws") ?: return ""
        for (i in 0 until ws.length()) {
            val cw = ws.getJSONObject(i).getJSONArray("cw")
            for (j in 0 until cw.length()) {
                sb.append(cw.getJSONObject(j).optString("w", ""))
            }
        }
        return sb.toString()
    }
}
```

- [ ] **Step 4: 创建Hilt DI模块**

`android/app/src/main/java/com/mowzi/app/speech/SpeechModule.kt`:

```kotlin
package com.mowzi.app.speech

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SpeechModule {

    @Provides
    @Singleton
    fun provideXfyunSpeechService(
        @ApplicationContext context: Context
    ): XfyunSpeechService {
        return XfyunSpeechService(
            context = context,
            appId = "1b20cd0f",
            engineType = "local",
            defaultVoiceName = "xiaoyan"
        )
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

```bash
cd android && ./gradlew test --tests "com.mowzi.app.speech.XfyunSpeechServiceTest"
```

Expected: PASS（3个测试全部通过）

- [ ] **Step 6: 提交**

```bash
git add android/app/src/main/java/com/mowzi/app/speech/ android/app/src/test/java/com/mowzi/app/speech/
git commit -m "feat: XfyunSpeechService封装 - STT语音听写 + TTS语音合成"
```

---

## Task 4: 修改ChatViewModel集成SDK

**Files:**
- Modify: `android/app/src/main/java/com/mowzi/app/ui/chat/ChatViewModel.kt`

- [ ] **Step 1: 注入XfyunSpeechService到ChatViewModel**

在 `ChatViewModel` 的构造函数中添加 `XfyunSpeechService` 依赖：

```kotlin
@HiltViewModel
class ChatViewModel @Inject constructor(
    // ... existing dependencies ...
    private val speechService: XfyunSpeechService,
    // ...
) : ViewModel() {
```

- [ ] **Step 2: 修改sendVoiceMessage使用本地STT**

替换原有的 `chatRepository.speechToText(pcmData)` 网络调用：

```kotlin
private fun sendVoiceMessage(pcmData: ByteArray) {
    viewModelScope.launch {
        streamingState = StreamingState.PROCESSING
        try {
            // 使用本地SDK进行语音识别
            val result = speechService.recognizeFromPcm(pcmData)
            if (result.text.isNotBlank() && result.confidence >= 0.3f) {
                sendTextMessage(result.text)
            } else {
                errorMessage = "没听清哦，再说一次吧？"
                streamingState = StreamingState.IDLE
            }
        } catch (e: Exception) {
            Log.e(TAG, "STT失败", e)
            errorMessage = "没听清哦，再说一次吧？"
            streamingState = StreamingState.IDLE
        }
    }
}
```

- [ ] **Step 3: 添加本地TTS句子合成逻辑**

在 `handleStreamChunk` 中，当收到完整句子时触发本地TTS：

```kotlin
// 在handleStreamChunk中处理sentence逻辑
private val sentenceBuffer = StringBuilder()
private val sentenceEndingRegex = Regex("[。！？…\\.\\!?]")

private fun handleTextChunk(content: String) {
    currentStreamingText += content
    sentenceBuffer.append(content)

    // 检查完整句子
    val match = sentenceEndingRegex.find(sentenceBuffer)
    if (match != null) {
        val endPos = match.range.last + 1
        val sentence = sentenceBuffer.substring(0, endPos).trim()
        sentenceBuffer.delete(0, endPos)

        if (sentence.isNotEmpty()) {
            // 本地TTS合成并播放
            synthesizeAndPlay(sentence)
        }
    }
}

private fun synthesizeAndPlay(text: String) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val pcmData = speechService.synthesize(text)
            if (pcmData != null) {
                audioPlayer.enqueuePcm(pcmData)
            }
        } catch (e: Exception) {
            Log.w(TAG, "TTS合成失败，跳过音频播放", e)
        }
    }
}
```

- [ ] **Step 4: 处理流结束时的剩余缓冲**

在收到 `text_done` 事件后，处理 `sentenceBuffer` 中剩余的文本：

```kotlin
// text_done处理中
val remaining = sentenceBuffer.toString().trim()
sentenceBuffer.clear()
if (remaining.isNotEmpty()) {
    synthesizeAndPlay(remaining)
}
```

- [ ] **Step 5: 验证编译通过**

```bash
cd android && ./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 提交**

```bash
git add android/app/src/main/java/com/mowzi/app/ui/chat/ChatViewModel.kt
git commit -m "feat: ChatViewModel集成讯飞SDK - 本地STT识别 + 本地TTS合成"
```

---

## Task 5: 修改AudioPlayer支持PCM播放

**Files:**
- Modify: `android/app/src/main/java/com/mowzi/app/audio/AudioPlayer.kt`

- [ ] **Step 1: 添加enqueuePcm方法**

在 `AudioPlayer.kt` 中添加PCM数据播放支持。讯飞本地TTS输出的PCM为16kHz 16bit mono：

```kotlin
fun enqueuePcm(pcmData: ByteArray) {
    // PCM -> WAV (添加WAV头部)
    val wavData = pcmToWav(pcmData, 16000, 16, 1)
    val tempFile = File.createTempFile("tts_", ".wav", cacheDir)
    tempFile.writeBytes(wavData)
    playQueue.add(tempFile)
    if (!isPlaying) playNext()
}

private fun pcmToWav(pcmData: ByteArray, sampleRate: Int, bitsPerSample: Int, channels: Int): ByteArray {
    val byteRate = sampleRate * channels * bitsPerSample / 8
    val blockAlign = channels * bitsPerSample / 8
    val dataSize = pcmData.size
    val header = ByteArray(44)

    // RIFF header
    header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte()
    header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
    writeInt(header, 4, 36 + dataSize)  // ChunkSize
    header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte()
    header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
    // fmt sub-chunk
    header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte()
    header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
    writeInt(header, 16, 16)           // SubChunk1Size
    writeShort(header, 20, 1)          // AudioFormat = PCM
    writeShort(header, 22, channels.toShort())
    writeInt(header, 24, sampleRate)
    writeInt(header, 28, byteRate)
    writeShort(header, 32, blockAlign.toShort())
    writeShort(header, 34, bitsPerSample.toShort())
    // data sub-chunk
    header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte()
    header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
    writeInt(header, 40, dataSize)

    return header + pcmData
}

private fun writeInt(buf: ByteArray, offset: Int, value: Int) {
    buf[offset] = (value and 0xFF).toByte()
    buf[offset + 1] = (value shr 8 and 0xFF).toByte()
    buf[offset + 2] = (value shr 16 and 0xFF).toByte()
    buf[offset + 3] = (value shr 24 and 0xFF).toByte()
}

private fun writeShort(buf: ByteArray, offset: Int, value: Short) {
    buf[offset] = (value.toInt() and 0xFF).toByte()
    buf[offset + 1] = (value.toInt() shr 8 and 0xFF).toByte()
}
```

- [ ] **Step 2: 验证编译通过**

```bash
cd android && ./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add android/app/src/main/java/com/mowzi/app/audio/AudioPlayer.kt
git commit -m "feat: AudioPlayer支持PCM播放 - 为讯飞本地TTS输出提供WAV封装"
```

---

## Task 6: 清理后端STT/TTS相关代码

**Files:**
- Modify: `android/app/src/main/java/com/mowzi/app/data/repository/ChatRepository.kt`
- Modify: `android/app/src/main/java/com/mowzi/app/data/remote/MowziApi.kt`
- Modify: `android/app/src/main/java/com/mowzi/app/data/remote/dto/ChatDto.kt`
- Modify: `android/app/src/main/java/com/mowzi/app/ui/chat/ChatScreen.kt`

- [ ] **Step 1: 从ChatRepository移除speechToText方法**

删除 `speechToText()` 方法和 `pcmToOpus()` 方法及相关OpusEncoder引用。`ChatRepository` 不再需要处理音频。

- [ ] **Step 2: 从MowziApi移除/chat/stt端点**

删除 `MowziApi.kt` 中的 `speechToText()` 接口定义。

- [ ] **Step 3: 从ChatScreen移除sentence_audio处理**

在 `ChatScreen.kt` 中确认不再处理 `sentence_audio` 类型的SSE事件（已在ChatViewModel中替换为本地TTS）。

- [ ] **Step 4: 删除OpusEncoder.kt**

```bash
rm android/app/src/main/java/com/mowzi/app/audio/OpusEncoder.kt
```

同时从 `build.gradle.kts` 中移除 `io.element.android:opusencoder` 依赖，从 `RepositoryModule.kt` 中移除相关provide方法。

- [ ] **Step 5: 验证编译通过**

```bash
cd android && ./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 提交**

```bash
git add -A android/app/src/main/java/com/mowzi/app/
git commit -m "refactor: 清理后端STT/TTS调用 - 移除OpusEncoder、speechToText网络调用"
```

---

## Task 7: 后端简化（移除/chat/stt端点）

**Files:**
- Modify: `backend/app/api/chat.py`
- Modify: `backend/app/schemas/chat.py`

- [ ] **Step 1: 从chat.py移除/chat/stt端点**

删除 `POST /api/v1/chat/stt` 端点处理函数。保留 `/chat/stream` 端点。

- [ ] **Step 2: 从chat.py移除xfyun_tts相关导入和调用**

删除 `sentence_audio` 相关的SSE事件生成代码。`/chat/stream` 只推送 `text_chunk` 和 `text_done` 事件。

- [ ] **Step 3: 清理schemas**

从 `backend/app/schemas/chat.py` 中移除 `SttResponse` 等不再使用的模型。

- [ ] **Step 4: 运行后端测试确认通过**

```bash
cd backend && python -m pytest tests/ -v
```

Expected: 所有现有测试通过（可能需要更新mock相关测试）

- [ ] **Step 5: 提交**

```bash
git add backend/app/api/chat.py backend/app/schemas/chat.py
git commit -m "refactor: 后端移除STT/TTS端点 - 语音处理已迁移到Android端"
```

---

## Task 8: 端到端验证

**Files:** 无新文件

- [ ] **Step 1: 启动后端服务**

```bash
cd backend && uvicorn app.main:app --reload --port 8000
```

- [ ] **Step 2: 在Android设备上运行应用**

```bash
cd android && ./gradlew installDebug && adb shell am start -n com.mowzi.app/.MainActivity
```

- [ ] **Step 3: 验证STT流程**

1. 点击麦克风按钮
2. 说一句话（如"你好"）
3. 确认Logcat中可见MSC SDK识别结果
4. 确认文字正确显示在聊天界面

- [ ] **Step 4: 验证TTS流程**

1. 发送一条消息
2. 等待LLM回复（SSE流式）
3. 确认每个完整句子都被本地TTS合成并播放
4. 确认Logcat中可见TTS合成日志

- [ ] **Step 5: 验证离线模式**

1. 关闭设备网络
2. 点击麦克风说话
3. 确认本地STT仍然可以工作（SDK已激活的情况下）
4. 确认本地TTS仍然可以播放
5. 确认LLM请求失败时显示友好提示

- [ ] **Step 6: 最终提交**

```bash
git add -A
git commit -m "feat: 讯飞MSC SDK集成完成 - 本地STT/TTS，后端简化"
```

---

## 自检清单

| 规格要求 | 对应Task | 状态 |
|----------|----------|------|
| 本地STT语音听写 | Task 3 (XfyunSpeechService) | ✅ |
| 本地TTS语音合成 | Task 3 (XfyunSpeechService) | ✅ |
| SDK初始化（Application） | Task 2 (MowziApp) | ✅ |
| SDK文件集成 | Task 1 (libs + jniLibs + assets) | ✅ |
| ChatViewModel适配 | Task 4 | ✅ |
| AudioPlayer PCM支持 | Task 5 | ✅ |
| 清理旧代码 | Task 6 (Android) + Task 7 (后端) | ✅ |
| E2E验证 | Task 8 | ✅ |
| 移除后端/chat/stt | Task 7 | ✅ |
| 移除sentence_audio SSE事件 | Task 7 | ✅ |
| 移除OpusEncoder | Task 6 | ✅ |
