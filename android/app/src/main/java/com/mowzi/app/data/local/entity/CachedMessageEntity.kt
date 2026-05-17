package com.mowzi.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cached_messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId")]
)
data class CachedMessageEntity(
    @PrimaryKey
    val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val audioLocalPath: String?,
    val timestamp: Long
)