package com.mowzi.app.sse

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader

/**
 * SSE client for parsing Server-Sent Events using OkHttp.
 */
class SSEClient(private val okHttpClient: OkHttpClient) {

    fun connect(request: Request): Flow<SSEEvent> = callbackFlow {
        val call = okHttpClient.newCall(request)
        val response = call.execute()

        if (!response.isSuccessful) {
            close()
            return@callbackFlow
        }

        val reader: BufferedReader? = response.body?.byteStream()?.bufferedReader()

        var currentEvent = ""
        var currentData = ""

        reader?.useLines { lines ->
            for (line in lines) {
                when {
                    line.startsWith("event: ") -> {
                        currentEvent = line.removePrefix("event: ").trim()
                    }
                    line.startsWith("data: ") -> {
                        currentData = line.removePrefix("data: ").trim()
                    }
                    line.isBlank() -> {
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

/**
 * Represents a parsed SSE event.
 */
data class SSEEvent(val event: String, val data: String)