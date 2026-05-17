package com.mowzi.app.util

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenManager @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        private val DEVICE_TOKEN_KEY = stringPreferencesKey("device_token")
        private val PARENT_TOKEN_KEY = stringPreferencesKey("parent_token")
    }

    val deviceToken: Flow<String?> = dataStore.data.map { preferences ->
        preferences[DEVICE_TOKEN_KEY]
    }

    val parentToken: Flow<String?> = dataStore.data.map { preferences ->
        preferences[PARENT_TOKEN_KEY]
    }

    suspend fun getDeviceToken(): String? {
        return dataStore.data.first()[DEVICE_TOKEN_KEY]
    }

    suspend fun getParentToken(): String? {
        return dataStore.data.first()[PARENT_TOKEN_KEY]
    }

    suspend fun saveDeviceToken(token: String) {
        dataStore.edit { preferences ->
            preferences[DEVICE_TOKEN_KEY] = token
        }
    }

    suspend fun saveParentToken(token: String) {
        dataStore.edit { preferences ->
            preferences[PARENT_TOKEN_KEY] = token
        }
    }

    suspend fun clearDeviceToken() {
        dataStore.edit { preferences ->
            preferences.remove(DEVICE_TOKEN_KEY)
        }
    }

    suspend fun clearParentToken() {
        dataStore.edit { preferences ->
            preferences.remove(PARENT_TOKEN_KEY)
        }
    }

    suspend fun clearAllTokens() {
        dataStore.edit { preferences ->
            preferences.remove(DEVICE_TOKEN_KEY)
            preferences.remove(PARENT_TOKEN_KEY)
        }
    }
}