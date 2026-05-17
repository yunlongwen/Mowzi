package com.mowzi.app.ui.characters

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.mowzi.app.data.remote.dto.CharacterDto
import com.mowzi.app.ui.navigation.Route

@Composable
fun CharacterSelectScreen(
    navController: NavController,
    viewModel: CharacterSelectViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Log.d("wyl", "CharacterSelectScreen: composable entered, uiState isLoading=${uiState.isLoading}, errorMessage=${uiState.errorMessage}, characters=${uiState.characters.size}, selectedCharacterId=${uiState.selectedCharacterId}")

    // Navigate to chat when conversation is created
    LaunchedEffect(uiState.createdConversationId, uiState.selectedCharacterId) {
        Log.d("wyl", "CharacterSelectScreen: LaunchedEffect createdConversationId=${uiState.createdConversationId}, selectedCharacterId=${uiState.selectedCharacterId}")
        uiState.createdConversationId?.let { conversationId ->
            Log.d("wyl", "CharacterSelectScreen: navigating to chat with conversationId=$conversationId")
            val characterId = uiState.selectedCharacterId ?: return@let
            val character = uiState.characters.find { it.id == characterId }
            val characterName = character?.name ?: ""
            navController.navigate(Route.Chat.createRoute(conversationId, characterId, characterName))
            viewModel.clearConversationNavigation()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Log.d("wyl", "CharacterSelectScreen: entering when, isLoading=${uiState.isLoading}, errorMessage=${uiState.errorMessage}")
        when {
            uiState.isLoading -> {
                Log.d("wyl", "CharacterSelectScreen: branch - loading")
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            uiState.errorMessage != null -> {
                Log.d("wyl", "CharacterSelectScreen: branch - error, message=${uiState.errorMessage}")
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = uiState.errorMessage ?: "Unknown error",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            else -> {
                Log.d("wyl", "CharacterSelectScreen: branch - show characters, count=${uiState.characters.size}")
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(uiState.characters) { character ->
                        CharacterCard(
                            character = character,
                            isSelected = character.id == uiState.selectedCharacterId,
                            onClick = { viewModel.selectCharacter(character) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CharacterCard(
    character: CharacterDto,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.Transparent
    }

    Card(
        modifier = Modifier
            .clickable(onClick = onClick)
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = borderColor,
                shape = MaterialTheme.shapes.medium
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Avatar placeholder (circular)
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = character.name.firstOrNull()?.toString() ?: "?",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            // Character name
            Text(
                text = character.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 12.dp),
                textAlign = TextAlign.Center
            )

            // Description (single line, ellipsis)
            Text(
                text = character.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}