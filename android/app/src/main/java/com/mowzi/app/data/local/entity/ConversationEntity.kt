package com.mowzi.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey
    val id: String,
    val characterId: String,
    val title: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastMessageAt: Long
)