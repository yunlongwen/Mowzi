package com.mowzi.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mowzi.app.data.local.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations WHERE status = 'active' LIMIT 1")
    suspend fun getActive(): ConversationEntity?

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<ConversationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(conversation: ConversationEntity)

    @Query("UPDATE conversations SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, updatedAt: Long)
}