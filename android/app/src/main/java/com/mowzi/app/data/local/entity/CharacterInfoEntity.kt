package com.mowzi.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "character_info")
data class CharacterInfoEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val avatarUrl: String,
    val description: String
)