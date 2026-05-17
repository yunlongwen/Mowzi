package com.mowzi.app.ui.onboarding

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun WelcomeScreen(
    viewModel: WelcomeViewModel = hiltViewModel(),
    onRegistered: () -> Unit,
    onHasActiveConversation: (String) -> Unit,
    onGoToCharacterSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSettings by remember { mutableStateOf(false) }

    Log.d("wyl", "WelcomeScreen: uiState changed - hasToken=${uiState.hasToken}, isCheckingActive=${uiState.isCheckingActive}, activeConversationId=${uiState.activeConversationId}, registered=${uiState.registered}, errorMessage=${uiState.errorMessage}")

    LaunchedEffect(uiState.registered) {
        Log.d("wyl", "WelcomeScreen: LaunchedEffect uiState.registered=${uiState.registered}")
        if (uiState.registered) {
            Log.d("wyl", "WelcomeScreen: navigating to character select (registered)")
            onGoToCharacterSelect()
        }
    }

    LaunchedEffect(uiState.activeConversationId) {
        Log.d("wyl", "WelcomeScreen: LaunchedEffect uiState.activeConversationId=${uiState.activeConversationId}")
        uiState.activeConversationId?.let { convId ->
            Log.d("wyl", "WelcomeScreen: navigating to chat with conversationId=$convId")
            onHasActiveConversation(convId)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Log.d("wyl", "WelcomeScreen: entering when, hasToken=${uiState.hasToken}, isCheckingActive=${uiState.isCheckingActive}")
        when {
            uiState.hasToken == null || uiState.isCheckingActive -> {
                Log.d("wyl", "WelcomeScreen: branch - loading (hasToken=null or isCheckingActive=true)")
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
            }

            uiState.hasToken == true && uiState.activeConversationId == null -> {
                Log.d("wyl", "WelcomeScreen: branch - hasToken=true, no activeConversationId, navigate to character select")
                LaunchedEffect(Unit) { onGoToCharacterSelect() }
            }

            uiState.hasToken == true -> {
                Log.d("wyl", "WelcomeScreen: branch - hasToken=true, has activeConversationId=${uiState.activeConversationId}")
                LaunchedEffect(Unit) {
                    uiState.activeConversationId?.let { onHasActiveConversation(it) }
                        ?: onGoToCharacterSelect()
                }
            }

            else -> {
                Log.d("wyl", "WelcomeScreen: branch - else (no token), show welcome content, errorMessage=${uiState.errorMessage}")
                if (showSettings) {
                    ServerSettingsContent(
                        currentUrl = uiState.serverUrl,
                        onSave = { url ->
                            viewModel.updateServerUrl(url)
                            showSettings = false
                        },
                        onBack = { showSettings = false }
                    )
                } else {
                    WelcomeContent(
                        childName = uiState.childName,
                        isRegistering = uiState.isRegistering,
                        errorMessage = uiState.errorMessage,
                        onNameChange = viewModel::onChildNameChanged,
                        onRegister = viewModel::registerDevice,
                        onShowSettings = { showSettings = true }
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerSettingsContent(
    currentUrl: String,
    onSave: (String) -> Unit,
    onBack: () -> Unit
) {
    var url by remember { mutableStateOf(currentUrl) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "服务器设置",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("服务器地址") },
            placeholder = { Text("http://10.0.2.2:8000") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "模拟器请填: http://10.0.2.2:8000\n真机请填电脑局域网IP",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { onSave(url.trimEndSuffix("/")) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("保存")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("返回")
        }
    }
}

private fun String.trimEndSuffix(suffix: String): String {
    return if (endsWith(suffix)) dropLast(suffix.length) else this
}

@Composable
private fun WelcomeContent(
    childName: String,
    isRegistering: Boolean,
    errorMessage: String?,
    onNameChange: (String) -> Unit,
    onRegister: () -> Unit,
    onShowSettings: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Settings button in top-right
        IconButton(
            onClick = onShowSettings,
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "服务器设置",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Pets,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "你好呀！",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "我是毛仔，你的AI好朋友！\n告诉我你的名字吧",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = childName,
                onValueChange = onNameChange,
                label = { Text("你的名字") },
                placeholder = { Text("输入你的名字") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onRegister() })
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Button(
                onClick = onRegister,
                enabled = !isRegistering && childName.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isRegistering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("开始冒险！")
                }
            }
        }
    }
}
