package com.mowzi.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.mowzi.app.data.local.AppDatabase
import com.mowzi.app.data.local.dao.ConversationDao
import com.mowzi.app.data.local.dao.MessageDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mowzi_preferences")

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "mowzi_database"
        ).build()
    }

    @Provides
    @Singleton
    fun provideConversationDao(database: AppDatabase): ConversationDao {
        return database.conversationDao()
    }

    @Provides
    @Singleton
    fun provideMessageDao(database: AppDatabase): MessageDao {
        return database.messageDao()
    }

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.dataStore
    }
}