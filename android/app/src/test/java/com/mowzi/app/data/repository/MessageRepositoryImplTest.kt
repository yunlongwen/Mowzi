package com.mowzi.app.data.repository

import com.mowzi.app.data.local.dao.MessageDao
import com.mowzi.app.data.local.entity.CachedMessageEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class MessageRepositoryImplTest {

    private val messageDao: MessageDao = mock()
    private lateinit var repository: MessageRepositoryImpl

    private val testMessage = CachedMessageEntity(
        id = "msg1",
        conversationId = "conv1",
        role = "user",
        content = "Hello",
        audioLocalPath = null,
        timestamp = 1000L
    )

    @Before
    fun setup() {
        repository = MessageRepositoryImpl(messageDao)
    }

    @Test
    fun `getMessagesByConversation returns dao flow`() {
        val expectedFlow = MutableStateFlow(listOf(testMessage))
        whenever(messageDao.getByConversation("conv1")).thenReturn(expectedFlow)

        val result = repository.getMessagesByConversation("conv1")

        assertEquals(expectedFlow, result)
        verify(messageDao).getByConversation("conv1")
    }

    @Test
    fun `getMessagesByConversation with empty result returns empty flow`() {
        val emptyFlow = MutableStateFlow(emptyList<CachedMessageEntity>())
        whenever(messageDao.getByConversation("conv1")).thenReturn(emptyFlow)

        val result = repository.getMessagesByConversation("conv1")

        assertTrue(result === emptyFlow)
    }

    @Test
    fun `insertMessage delegates to dao`() = runTest {
        repository.insertMessage(testMessage)

        verify(messageDao).insert(testMessage)
    }

    @Test
    fun `insertMessages delegates to dao`() = runTest {
        val messages = listOf(testMessage, testMessage.copy(id = "msg2"))

        repository.insertMessages(messages)

        verify(messageDao).insertAll(messages)
    }

    @Test
    fun `upsertMessage delegates to dao`() = runTest {
        repository.upsertMessage(testMessage)

        verify(messageDao).upsert(testMessage)
    }

    @Test
    fun `insertMessage called with correct parameters`() = runTest {
        val message = CachedMessageEntity(
            id = "msg99",
            conversationId = "conv99",
            role = "assistant",
            content = "Hi there",
            audioLocalPath = "/path/audio.wav",
            timestamp = 2000L
        )

        repository.insertMessage(message)

        verify(messageDao).insert(argThat {
            id == "msg99" && conversationId == "conv99" && role == "assistant"
        })
    }
}
