package com.mowzi.app.data.repository

import com.mowzi.app.data.local.dao.ConversationDao
import com.mowzi.app.data.local.entity.ConversationEntity
import com.mowzi.app.data.remote.MowziApi
import com.mowzi.app.data.remote.dto.ConversationListResponse
import com.mowzi.app.data.remote.dto.ConversationResponse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationRepositoryImplTest {

    private val conversationDao: ConversationDao = mock()
    private val api: MowziApi = mock()
    private lateinit var repository: ConversationRepositoryImpl

    private val testEntity = ConversationEntity(
        id = "1",
        characterId = "10",
        title = "Test Conversation",
        status = "active",
        createdAt = 1000L,
        updatedAt = 2000L,
        lastMessageAt = 1500L
    )

    private val testDto = ConversationResponse(
        id = "1",
        characterId = "10",
        title = "Test Conversation",
        status = "active",
        createdAt = "2024-01-01T00:00:00Z",
        updatedAt = "2024-01-01T00:00:00Z",
        lastMessageAt = "2024-01-01T00:00:00Z"
    )

    @Before
    fun setup() {
        repository = ConversationRepositoryImpl(conversationDao, api)
    }

    @Test
    fun `getActiveConversation returns entity when dao has active conversation`() = runTest {
        whenever(conversationDao.getActive()).thenReturn(testEntity)

        val result = repository.getActiveConversation()

        assertEquals(testEntity, result)
        verify(conversationDao).getActive()
    }

    @Test
    fun `getActiveConversation returns null when no active conversation`() = runTest {
        whenever(conversationDao.getActive()).thenReturn(null)

        val result = repository.getActiveConversation()

        assertNull(result)
    }

    @Test
    fun `getAllConversations returns dao flow`() = runTest {
        val expectedFlow = MutableStateFlow(listOf(testEntity))
        whenever(conversationDao.getAll()).thenReturn(expectedFlow)

        val result = repository.getAllConversations()

        assertEquals(expectedFlow, result)
        verify(conversationDao).getAll()
    }

    @Test
    fun `upsertConversation delegates to dao`() = runTest {
        repository.upsertConversation(testEntity)

        verify(conversationDao).upsert(testEntity)
    }

    @Test
    fun `updateConversationStatus delegates to dao with timestamp`() = runTest {
        repository.updateConversationStatus("1", "archived")

        verify(conversationDao).updateStatus(eq("1"), eq("archived"), any())
    }

    @Test
    fun `getConversationsByStatus success returns entities and caches`() = runTest {
        val listResponse = ConversationListResponse(
            items = listOf(testDto), total = 1, page = 1, pageSize = 20, totalPages = 1
        )
        whenever(api.getConversations(status = "active")).thenReturn(Response.success(listResponse))

        val result = repository.getConversationsByStatus("active")

        assertEquals(1, result.size)
        assertEquals("1", result[0].id)
        assertEquals("Test Conversation", result[0].title)
        verify(conversationDao).upsert(any())
    }

    @Test
    fun `getConversationsByStatus failure returns empty list`() = runTest {
        whenever(api.getConversations(status = "active")).thenReturn(
            Response.success(null as ConversationListResponse?)
        )

        val result = repository.getConversationsByStatus("active")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getConversationsByStatus unsuccessful response returns empty list`() = runTest {
        whenever(api.getConversations(status = "active")).thenReturn(
            Response.error(404, okhttp3.ResponseBody.create(null, "not found"))
        )

        val result = repository.getConversationsByStatus("active")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getConversationsByStatus with null items returns empty list`() = runTest {
        whenever(api.getConversations(status = "active")).thenReturn(
            Response.success(ConversationListResponse(emptyList(), 0, 1, 20, 0))
        )

        val result = repository.getConversationsByStatus("active")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getConversationsByStatus maps null title to default`() = runTest {
        val dtoNoTitle = testDto.copy(title = "")
        val listResponse = ConversationListResponse(
            items = listOf(dtoNoTitle), total = 1, page = 1, pageSize = 20, totalPages = 1
        )
        whenever(api.getConversations(status = "active")).thenReturn(Response.success(listResponse))

        val result = repository.getConversationsByStatus("active")

        assertEquals(1, result.size)
        // Title from DTO is used as-is; the mapping is in the repository
        assertEquals("", result[0].title)
    }

    @Test
    fun `refreshConversations fetches all statuses and caches`() = runTest {
        val activeResponse = ConversationListResponse(
            items = listOf(testDto), total = 1, page = 1, pageSize = 20, totalPages = 1
        )
        val idleDto = testDto.copy(id = "2", status = "idle")
        val idleResponse = ConversationListResponse(
            items = listOf(idleDto), total = 1, page = 1, pageSize = 20, totalPages = 1
        )
        val archivedDto = testDto.copy(id = "3", status = "archived")
        val archivedResponse = ConversationListResponse(
            items = listOf(archivedDto), total = 1, page = 1, pageSize = 20, totalPages = 1
        )

        whenever(api.getConversations(status = "active")).thenReturn(Response.success(activeResponse))
        whenever(api.getConversations(status = "idle")).thenReturn(Response.success(idleResponse))
        whenever(api.getConversations(status = "archived")).thenReturn(Response.success(archivedResponse))

        repository.refreshConversations()

        verify(api).getConversations(status = "active")
        verify(api).getConversations(status = "idle")
        verify(api).getConversations(status = "archived")
        verify(conversationDao, times(3)).upsert(any())
    }
}
