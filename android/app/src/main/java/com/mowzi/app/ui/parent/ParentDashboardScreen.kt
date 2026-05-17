package com.mowzi.app.ui.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mowzi.app.data.remote.dto.ParentConversationDto
import com.mowzi.app.data.remote.dto.ParentMessageDto
import com.mowzi.app.data.remote.dto.ParentUsageItem

/**
 * Parent dashboard screen with tabs for different sections.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentDashboardScreen(
    viewModel: ParentDashboardViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.loadSettings()
        viewModel.loadUsage("daily")
        viewModel.loadUsage("weekly")
        viewModel.loadConversations()
    }

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            snackbarHostState.showSnackbar("设置已保存")
            viewModel.clearSaveSuccess()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("家长控制面板") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Tab row
                TabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TabItem(
                        title = "使用限制",
                        icon = Icons.Default.AccessTime,
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 }
                    )
                    TabItem(
                        title = "对话历史",
                        icon = Icons.Default.Chat,
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 }
                    )
                    TabItem(
                        title = "API配置",
                        icon = Icons.Default.Settings,
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 }
                    )
                }

                // Tab content
                when (selectedTab) {
                    0 -> UsageLimitTab(
                        settings = uiState.settings,
                        dailyUsage = uiState.dailyUsage,
                        weeklyUsage = uiState.weeklyUsage,
                        onDailyLimitChange = viewModel::updateDailyLimit,
                        onSessionLimitChange = viewModel::updateSessionLimit,
                        onBlockedHoursStartChange = viewModel::updateBlockedHoursStart,
                        onBlockedHoursEndChange = viewModel::updateBlockedHoursEnd,
                        onSave = viewModel::saveSettings,
                        isSaving = uiState.isSaving
                    )
                    1 -> ConversationHistoryTab(
                        conversations = uiState.conversations,
                        selectedConversation = uiState.selectedConversation,
                        selectedConversationMessages = uiState.selectedConversationMessages,
                        onSelectConversation = viewModel::selectConversation,
                        onBackFromConversation = viewModel::clearSelectedConversation
                    )
                    2 -> ApiConfigTab(
                        settings = uiState.settings,
                        onLlMUrlChange = viewModel::updateLlMUrl,
                        onLlMModelChange = viewModel::updateLlMModel,
                        onSave = viewModel::saveSettings,
                        isSaving = uiState.isSaving
                    )
                }
            }
        }
    }
}

@Composable
private fun TabItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TabRow(
    selectedTabIndex: Int,
    modifier: Modifier = Modifier
) {
    // Using a simple row instead of TabRow to avoid version issues
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // Tabs are rendered by parent composable passing selectedTabIndex as state
    }
}

@Composable
private fun UsageLimitTab(
    settings: com.mowzi.app.data.remote.dto.ParentSettingsResponse,
    dailyUsage: List<ParentUsageItem>,
    weeklyUsage: List<ParentUsageItem>,
    onDailyLimitChange: (Int) -> Unit,
    onSessionLimitChange: (Int) -> Unit,
    onBlockedHoursStartChange: (String?) -> Unit,
    onBlockedHoursEndChange: (String?) -> Unit,
    onSave: () -> Unit,
    isSaving: Boolean
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Daily usage chart
        UsageChartCard(
            title = "今日使用时长",
            usageItems = dailyUsage.take(7),
            maxValue = settings.dailyLimitMin.toFloat()
        )

        // Weekly usage chart
        UsageChartCard(
            title = "本周使用时长",
            usageItems = weeklyUsage.take(7),
            maxValue = (settings.dailyLimitMin * 7).toFloat()
        )

        // Daily limit slider
        SettingsSliderCard(
            title = "每日时长限制",
            value = settings.dailyLimitMin,
            valueRange = 15..120,
            unit = "分钟",
            onValueChange = onDailyLimitChange
        )

        // Session limit slider
        SettingsSliderCard(
            title = "单次会话时长限制",
            value = settings.sessionLimitMin,
            valueRange = 10..60,
            unit = "分钟",
            onValueChange = onSessionLimitChange
        )

        // Blocked hours
        BlockedHoursCard(
            blockedHoursStart = settings.blockedHoursStart,
            blockedHoursEnd = settings.blockedHoursEnd,
            onStartChange = onBlockedHoursStartChange,
            onEndChange = onBlockedHoursEndChange
        )

        // Save button
        Button(
            onClick = onSave,
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("保存设置")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun UsageChartCard(
    title: String,
    usageItems: List<ParentUsageItem>,
    maxValue: Float
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (usageItems.isEmpty()) {
                Text(
                    text = "暂无数据",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    usageItems.forEach { item ->
                        val heightFraction = if (maxValue > 0) {
                            (item.minutes.toFloat() / maxValue).coerceIn(0f, 1f)
                        } else 0f

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(24.dp)
                                    .height((80 * heightFraction).coerceAtLeast(4f).dp)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = item.date.takeLast(2),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSliderCard(
    title: String,
    value: Int,
    valueRange: IntRange,
    unit: String,
    onValueChange: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "$value $unit",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Slider(
                value = value.toFloat(),
                onValueChange = { onValueChange(it.toInt()) },
                valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
                steps = (valueRange.last - valueRange.first) / 5
            )
        }
    }
}

@Composable
private fun BlockedHoursCard(
    blockedHoursStart: String?,
    blockedHoursEnd: String?,
    onStartChange: (String?) -> Unit,
    onEndChange: (String?) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "禁用时段",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = blockedHoursStart ?: "",
                    onValueChange = { onStartChange(it.ifEmpty { null }) },
                    label = { Text("开始时间") },
                    placeholder = { Text("21:00") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )

                Text(
                    text = "至",
                    style = MaterialTheme.typography.bodyLarge
                )

                OutlinedTextField(
                    value = blockedHoursEnd ?: "",
                    onValueChange = { onEndChange(it.ifEmpty { null }) },
                    label = { Text("结束时间") },
                    placeholder = { Text("07:00") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "设置后，孩子在此时段内无法使用应用",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ConversationHistoryTab(
    conversations: List<ParentConversationDto>,
    selectedConversation: ParentConversationDto?,
    selectedConversationMessages: List<ParentMessageDto>,
    onSelectConversation: (ParentConversationDto) -> Unit,
    onBackFromConversation: () -> Unit
) {
    if (selectedConversation != null) {
        ConversationDetailView(
            conversation = selectedConversation,
            messages = selectedConversationMessages,
            onBack = onBackFromConversation
        )
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (conversations.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无对话记录",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(conversations) { conversation ->
                    ConversationItem(
                        conversation = conversation,
                        onClick = { onSelectConversation(conversation) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationItem(
    conversation: ParentConversationDto,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = conversation.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = conversation.characterName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${conversation.messageCount} 条消息",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatTimestamp(conversation.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ConversationDetailView(
    conversation: ParentConversationDto,
    messages: List<ParentMessageDto>,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Column {
                Text(
                    text = conversation.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = conversation.characterName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        HorizontalDivider()

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages) { message ->
                MessageItem(message = message)
            }
        }
    }
}

@Composable
private fun MessageItem(message: ParentMessageDto) {
    val isUser = message.role == "user"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isUser) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .padding(12.dp)
        ) {
            Column {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatTimestamp(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun ApiConfigTab(
    settings: com.mowzi.app.data.remote.dto.ParentSettingsResponse,
    onLlMUrlChange: (String?) -> Unit,
    onLlMModelChange: (String?) -> Unit,
    onSave: () -> Unit,
    isSaving: Boolean
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // LLM Configuration
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "LLM 配置",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = settings.llmApiUrl ?: "",
                    onValueChange = { onLlMUrlChange(it.ifEmpty { null }) },
                    label = { Text("API URL") },
                    placeholder = { Text("https://api.openai.com/v1") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = settings.llmModel ?: "",
                    onValueChange = { onLlMModelChange(it.ifEmpty { null }) },
                    label = { Text("模型名称") },
                    placeholder = { Text("gpt-4o-mini") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }

        // Xfyun Configuration (read-only display)
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "讯飞配置",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "App ID",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = settings.xfyunAppId ?: "未配置",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Text(
                    text = "API密钥和Secret仅在服务端配置，不可在此查看",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Save button
        Button(
            onClick = onSave,
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("保存配置")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val date = java.util.Date(timestamp)
    val format = java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.getDefault())
    return format.format(date)
}