package com.mowzi.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.mowzi.app.data.local.dao.ConversationDao
import com.mowzi.app.data.local.dao.MessageDao
import com.mowzi.app.data.local.entity.CharacterInfoEntity
import com.mowzi.app.data.local.entity.CachedMessageEntity
import com.mowzi.app.data.local.entity.ConversationEntity

@Database(
    entities = [
        ConversationEntity::class,
        CachedMessageEntity::class,
        CharacterInfoEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
}