package com.mowzi.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class CharactersResponse(
    val characters: List<CharacterDto>
)

@Serializable
data class CharacterDto(
    val id: String,
    val name: String,
    val avatarUrl: String,
    val description: String
)