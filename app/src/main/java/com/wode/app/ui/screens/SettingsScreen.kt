package com.wode.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
) {
    val isAuthorized by viewModel.isAuthorized.collectAsState()

    var appKey by remember { mutableStateOf(tokenStore.getAppKey()) }
    var secretKey by remember { mutableStateOf(tokenStore.getSecretKey()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // ==== 百度网盘 ====
            Text(
                text = "百度网盘",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp),
            )

            // 授权状态卡片
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
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
                        tint = if (isAuthorized) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isAuthorized) "已授权" else "未授权",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = if (isAuthorized) "百度网盘已连接" else "请登录百度网盘以使用备份功能",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (isAuthorized) {
                        TextButton(
                            onClick = { viewModel.logout() },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) {
                            Text("登出")
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // API 密钥配置
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "API 密钥配置",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "在百度网盘开放平台 (pan.baidu.com/union) 创建应用后，获取 AppKey 和 SecretKey。OAuth 回调地址须设为: wode://baidu.oauth",
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

            Spacer(Modifier.height(16.dp))

            // 备份目录信息
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "备份目录",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "APK 文件将备份到百度网盘中的：\n${BaiduPanService.BACKUP_PATH}/",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // ==== 关于 ====
            Text(
                text = "关于",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "我的", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "版本 1.0.0",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "手机应用备份工具箱",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
