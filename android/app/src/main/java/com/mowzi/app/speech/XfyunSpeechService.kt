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
import kotlin.coroutines.resume

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
                    Log.d(TAG, "STT最终结果: $resultBuffer")
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

        try {
            val buf = ByteArray(1280)
            audioFlow.collect { shorts ->
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
        recognizer.setParameter("sample_rate", "16000")
    }

    // ---- TTS (语音合成) ----

    suspend fun synthesize(text: String, voiceName: String = defaultVoiceName): ByteArray? =
        suspendCancellableCoroutine { continuation ->
            val synthesizer = SpeechSynthesizer.createSynthesizer(context, null)
                ?: throw IllegalStateException("SpeechSynthesizer创建失败")

            configureSynthesizer(synthesizer, voiceName)

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
            override fun onCompleted(error: SpeechError?) { synthesizer.destroy() }
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
            context, ResourceUtil.RESOURCE_TYPE.assets,
            "$type/${if (type == "iat") "sms_16k" else defaultVoiceName}.jet"
        )
        return "$commonPath;$voicePath"
    }

    fun parseIatResult(json: String): String {
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
