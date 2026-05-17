package com.mowzi.app.util

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class TokenManagerTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var tokenManager: TokenManager
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        dataStore = PreferenceDataStoreFactory.createWithPath(
            produceFile = {
                val file = File(temporaryFolder.newFolder("datastore"), "test.preferences_pb")
                file.absolutePath.toPath()
            }
        )
        tokenManager = TokenManager(dataStore)
    }

    @Test
    fun `saveDeviceToken and getDeviceToken roundtrip`() = runTest(testDispatcher) {
        tokenManager.saveDeviceToken("device-abc-123")
        advanceUntilIdle()

        val result = tokenManager.getDeviceToken()
        assertEquals("device-abc-123", result)
    }

    @Test
    fun `getDeviceToken returns null when not set`() = runTest(testDispatcher) {
        val result = tokenManager.getDeviceToken()
        assertNull(result)
    }

    @Test
    fun `saveParentToken and getParentToken roundtrip`() = runTest(testDispatcher) {
        tokenManager.saveParentToken("parent-xyz-456")
        advanceUntilIdle()

        val result = tokenManager.getParentToken()
        assertEquals("parent-xyz-456", result)
    }

    @Test
    fun `getParentToken returns null when not set`() = runTest(testDispatcher) {
        val result = tokenManager.getParentToken()
        assertNull(result)
    }

    @Test
    fun `clearAllTokens removes both tokens`() = runTest(testDispatcher) {
        tokenManager.saveDeviceToken("device-token")
        tokenManager.saveParentToken("parent-token")
        advanceUntilIdle()

        tokenManager.clearAllTokens()
        advanceUntilIdle()

        assertNull(tokenManager.getDeviceToken())
        assertNull(tokenManager.getParentToken())
    }

    @Test
    fun `clearDeviceToken removes only device token`() = runTest(testDispatcher) {
        tokenManager.saveDeviceToken("device-token")
        tokenManager.saveParentToken("parent-token")
        advanceUntilIdle()

        tokenManager.clearDeviceToken()
        advanceUntilIdle()

        assertNull(tokenManager.getDeviceToken())
        assertEquals("parent-token", tokenManager.getParentToken())
    }

    @Test
    fun `clearParentToken removes only parent token`() = runTest(testDispatcher) {
        tokenManager.saveDeviceToken("device-token")
        tokenManager.saveParentToken("parent-token")
        advanceUntilIdle()

        tokenManager.clearParentToken()
        advanceUntilIdle()

        assertEquals("device-token", tokenManager.getDeviceToken())
        assertNull(tokenManager.getParentToken())
    }

    @Test
    fun `deviceToken flow emits saved value`() = runTest(testDispatcher) {
        tokenManager.saveDeviceToken("device-flow-test")
        advanceUntilIdle()

        val flowValue = tokenManager.deviceToken.first()
        assertEquals("device-flow-test", flowValue)
    }

    @Test
    fun `parentToken flow emits saved value`() = runTest(testDispatcher) {
        tokenManager.saveParentToken("parent-flow-test")
        advanceUntilIdle()

        val flowValue = tokenManager.parentToken.first()
        assertEquals("parent-flow-test", flowValue)
    }

    @Test
    fun `saveDeviceToken overwrites previous value`() = runTest(testDispatcher) {
        tokenManager.saveDeviceToken("old-token")
        advanceUntilIdle()

        tokenManager.saveDeviceToken("new-token")
        advanceUntilIdle()

        assertEquals("new-token", tokenManager.getDeviceToken())
    }
}
