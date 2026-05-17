package com.mowzi.app.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class XfyunSpeechServiceTest {

    @Test
    fun `sentence ending regex matches Chinese punctuation`() {
        val regex = Regex("[。！？…\\.!?]")
        assertEquals(true, regex.containsMatchIn("你好！"))
        assertEquals(true, regex.containsMatchIn("是吗？"))
        assertEquals(true, regex.containsMatchIn("好的。"))
        assertEquals(false, regex.containsMatchIn("你好"))
    }

    @Test
    fun `sentence ending regex finds correct position`() {
        val regex = Regex("[。！？…\\.!?]")
        val text = "今天天气真好！"
        val match = regex.find(text)
        assertEquals(6, match!!.range.first)
        assertEquals(6, match.range.last)
    }

    @Test
    fun `sentence ending regex matches multiple sentences`() {
        val regex = Regex("[。！？…\\.!?]")
        assertEquals(true, regex.containsMatchIn("你好！我很好。"))
        assertEquals(true, regex.containsMatchIn("Hello! How are you?"))
    }
}
