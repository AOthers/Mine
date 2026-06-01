package com.wode.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wode.app.service.BaiduPanService
import com.wode.app.service.TokenStore
import com.wode.app.viewmodel.BackupViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: BackupViewModel,
    tokenStore: TokenStore,
    onBack: () -> Unit,
    onStartOAuth: (String) -> Unit,
    onChooseRestoreFolder: () -> Unit,
    onPathSaved: (String) -> Unit,
) {
    val isAuthorized by viewModel.isAuthorized.collectAsState()
    val restoreDisplayPath by viewModel.restoreDisplayPath.collectAsState()

    var appKey by remember { mutableStateOf(tokenStore.getAppKey()) }
    var secretKey by remember { mutableStateOf(tokenStore.getSecretKey()) }
    var backupPath by remember { mutableStateOf(tokenStore.getBackupPath()) }
    var sameVersionStrategy by remember { mutableStateOf(tokenStore.getSameVersionStrategy()) }
    var showApiTutorial by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("备份与恢复设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { showApiTutorial = true }) {
                        Text("教程")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "百度网盘",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (isAuthorized) Icons.Default.Cloud else Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = if (isAuthorized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp),
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 14.dp),
                    ) {
                        Text(
                            text = if (isAuthorized) "已授权" else "未授权",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = if (isAuthorized) "百度网盘已连接，可用于备份和恢复" else "配置密钥并授权后才能使用网盘能力",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (isAuthorized) {
                        TextButton(
                            onClick = { viewModel.logout() },
                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) {
                            Text("退出")
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("API 密钥配置", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "在百度网盘开放平台创建应用后，填写 AppKey 和 SecretKey。OAuth 回调地址设置为 wode://baidu.oauth。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = appKey,
                        onValueChange = { appKey = it },
                        label = { Text("AppKey") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = secretKey,
                        onValueChange = { secretKey = it },
                        label = { Text("SecretKey") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                appKey = appKey.trim()
                                secretKey = secretKey.trim()
                                tokenStore.saveCredentials(appKey, secretKey)
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("保存")
                        }
                        Button(
                            onClick = {
                                appKey = appKey.trim()
                                secretKey = secretKey.trim()
                                tokenStore.saveCredentials(appKey, secretKey)
                                if (appKey.isNotBlank()) onStartOAuth(appKey)
                            },
                            modifier = Modifier.weight(1f),
                            enabled = appKey.isNotBlank(),
                        ) {
                            Text("保存并授权")
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("网盘备份目录", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "APK 会备份到百度网盘中的这个目录。留空会恢复默认目录：${BaiduPanService.DEFAULT_BACKUP_PATH}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = backupPath,
                        onValueChange = { backupPath = it },
                        label = { Text("百度网盘保存路径") },
                        placeholder = { Text(BaiduPanService.DEFAULT_BACKUP_PATH) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            tokenStore.saveBackupPath(backupPath)
                            backupPath = tokenStore.getBackupPath()
                            viewModel.loadBackupRecords()
                            onPathSaved("网盘备份路径已更新：$backupPath")
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("保存网盘路径")
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("恢复安装包保存位置", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "下载的恢复安装包会保存到这里。默认位置是应用自己的 Android/data 目录，也可以选择其他文件夹。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "当前保存位置：\n$restoreDisplayPath",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            onChooseRestoreFolder()
                            viewModel.refreshRestoreDisplayPath()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("选择保存文件夹")
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("同版本备份", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "版本不同会自动保留多个版本；版本相同时按这里的规则处理。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    StrategyRow(
                        title = "覆盖",
                        subtitle = "同一应用同一版本再次备份时覆盖原文件",
                        selected = sameVersionStrategy == TokenStore.SameVersionStrategy.OVERWRITE,
                        onClick = {
                            sameVersionStrategy = TokenStore.SameVersionStrategy.OVERWRITE
                            tokenStore.saveSameVersionStrategy(sameVersionStrategy)
                        },
                    )
                    StrategyRow(
                        title = "另存",
                        subtitle = "同一应用同一版本再次备份时保留一份带时间的新文件",
                        selected = sameVersionStrategy == TokenStore.SameVersionStrategy.SAVE_AS_COPY,
                        onClick = {
                            sameVersionStrategy = TokenStore.SameVersionStrategy.SAVE_AS_COPY
                            tokenStore.saveSameVersionStrategy(sameVersionStrategy)
                        },
                    )
                }
            }
        }
    }

    if (showApiTutorial) {
        ApiKeyTutorialDialog(onDismiss = { showApiTutorial = false })
    }
}

@Composable
private fun StrategyRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ApiKeyTutorialDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("获取 API 密钥教程") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("1. 打开百度网盘开放平台，登录你的百度账号。")
                Text("2. 创建一个应用，应用类型选择适合个人使用的网盘应用。")
                Text("3. 在应用配置里找到 AppKey 和 SecretKey，并复制到本页面。")
                Text("4. OAuth 回调地址填写：wode://baidu.oauth")
                Text("5. 回到本页面，点击“保存并授权”，浏览器授权成功后会自动回到应用。")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("知道了")
            }
        },
    )
}
