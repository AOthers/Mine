package com.wode.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wode.app.viewmodel.BackupItemState
import com.wode.app.viewmodel.BackupUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    uiState: BackupUiState,
    onCancel: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("备份进度") },
                navigationIcon = {
                    if (!uiState.isRunning) {
                        TextButton(onClick = onBack) {
                            Text("返回")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (uiState.isRunning) {
                BottomAppBar {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        OutlinedButton(onClick = onCancel) {
                            Text("取消备份")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            // 总进度条
            if (uiState.totalCount > 0) {
                val overallProgress = if (uiState.totalCount > 0)
                    uiState.completedCount.toFloat() / uiState.totalCount
                else 0f

                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = when (uiState.phase) {
                                BackupUiState.Phase.IDLE -> "准备中..."
                                BackupUiState.Phase.RUNNING -> "正在备份..."
                                BackupUiState.Phase.COMPLETED -> "备份完成"
                                BackupUiState.Phase.CANCELLED -> "已取消"
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "${uiState.completedCount}/${uiState.totalCount}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = overallProgress,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Divider()

            // 文件列表
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(uiState.items) { item ->
                    BackupItemRow(item)
                }

                if (uiState.items.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "暂无备份任务",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupItemRow(item: BackupItemState) {
    val animatedProgress by animateFloatAsState(
        targetValue = item.progress,
        label = "progress",
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 状态图标
            Icon(
                imageVector = when (item.status) {
                    BackupItemState.Status.PENDING -> Icons.Default.HourglassEmpty
                    BackupItemState.Status.EXTRACTING -> Icons.Default.Upload
                    BackupItemState.Status.UPLOADING -> Icons.Default.Upload
                    BackupItemState.Status.COMPLETED -> Icons.Default.CheckCircle
                    BackupItemState.Status.FAILED -> Icons.Default.Error
                },
                contentDescription = null,
                tint = when (item.status) {
                    BackupItemState.Status.COMPLETED -> MaterialTheme.colorScheme.primary
                    BackupItemState.Status.FAILED -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(24.dp),
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.appName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                when (item.status) {
                    BackupItemState.Status.UPLOADING -> {
                        LinearProgressIndicator(
                            progress = animatedProgress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                        )
                    }
                    BackupItemState.Status.FAILED -> {
                        item.error?.let { error ->
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    else -> {}
                }
            }
        }
    }
    Divider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
}
