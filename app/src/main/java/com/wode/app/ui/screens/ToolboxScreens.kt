package com.wode.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HomeRepairService
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wode.app.BuildConfig
import com.wode.app.MainActivity.MainTab

@Composable
fun ToolboxHomeScreen(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    toolsContent: @Composable () -> Unit,
    favoritesContent: @Composable () -> Unit,
    mineContent: @Composable () -> Unit,
) {
    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = selectedTab == MainTab.Tools,
                    onClick = { onTabSelected(MainTab.Tools) },
                    icon = { Icon(Icons.Default.HomeRepairService, contentDescription = null) },
                    label = { Text("功能") },
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.Favorites,
                    onClick = { onTabSelected(MainTab.Favorites) },
                    icon = { Icon(Icons.Default.Favorite, contentDescription = null) },
                    label = { Text("收藏") },
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.Mine,
                    onClick = { onTabSelected(MainTab.Mine) },
                    icon = { Icon(Icons.Default.Person, contentDescription = null) },
                    label = { Text("我的") },
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            when (selectedTab) {
                MainTab.Tools -> toolsContent()
                MainTab.Favorites -> favoritesContent()
                MainTab.Mine -> mineContent()
            }
        }
    }
}

@Composable
fun ToolboxHomeScreenContent(
    isBaiduAuthorized: Boolean,
    backupCount: Int,
    isBackupRestoreFavorite: Boolean,
    isMoviesFavorite: Boolean,
    onOpenBackupRestore: () -> Unit,
    onOpenMovies: () -> Unit,
    onSetBackupRestoreFavorite: (Boolean) -> Unit,
    onSetMoviesFavorite: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
    ) {
        Text(
            text = "我的工具箱",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "把常用的小工具整理在这里",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        Spacer(Modifier.height(20.dp))

        BackupRestoreToolCard(
            backupCount = backupCount,
            isAuthorized = isBaiduAuthorized,
            isFavorite = isBackupRestoreFavorite,
            onClick = onOpenBackupRestore,
            onSetFavorite = onSetBackupRestoreFavorite,
        )
        Spacer(Modifier.height(14.dp))
        MoviesToolCard(
            isFavorite = isMoviesFavorite,
            onClick = onOpenMovies,
            onSetFavorite = onSetMoviesFavorite,
        )
    }
}

@Composable
fun FavoritesScreen(
    isBackupRestoreFavorite: Boolean,
    isMoviesFavorite: Boolean,
    backupCount: Int,
    isBaiduAuthorized: Boolean,
    onOpenBackupRestore: () -> Unit,
    onOpenMovies: () -> Unit,
    onSetBackupRestoreFavorite: (Boolean) -> Unit,
    onSetMoviesFavorite: (Boolean) -> Unit,
) {
    if (isBackupRestoreFavorite || isMoviesFavorite) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
        ) {
            Text(
                text = "收藏",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(20.dp))
            if (isBackupRestoreFavorite) {
                BackupRestoreToolCard(
                    backupCount = backupCount,
                    isAuthorized = isBaiduAuthorized,
                    isFavorite = true,
                    onClick = onOpenBackupRestore,
                    onSetFavorite = onSetBackupRestoreFavorite,
                )
            }
            if (isBackupRestoreFavorite && isMoviesFavorite) {
                Spacer(Modifier.height(14.dp))
            }
            if (isMoviesFavorite) {
                MoviesToolCard(
                    isFavorite = true,
                    onClick = onOpenMovies,
                    onSetFavorite = onSetMoviesFavorite,
                )
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("还没有收藏", style = MaterialTheme.typography.titleMedium)
                Text(
                    "长按功能卡片，可以把常用功能放到这里",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun MineScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
    ) {
        Text(
            text = "我的",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "个人设置和应用信息",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(20.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("我的", style = MaterialTheme.typography.titleMedium)
                Text("版本 ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("一个逐步扩展的手机工具箱", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MoviesToolCard(
    isFavorite: Boolean,
    onClick: () -> Unit,
    onSetFavorite: (Boolean) -> Unit,
) {
    var showFavoriteDialog by remember { mutableStateOf(false) }

    ToolCard(
        title = "影视",
        subtitle = "内嵌影视站点浏览",
        status = if (isFavorite) "已收藏" else "WebView",
        icon = Icons.Default.Movie,
        onClick = onClick,
        onLongClick = { showFavoriteDialog = true },
    )

    if (showFavoriteDialog) {
        AlertDialog(
            onDismissRequest = { showFavoriteDialog = false },
            title = { Text(if (isFavorite) "取消收藏" else "收藏功能") },
            text = { Text(if (isFavorite) "是否取消收藏“影视”？" else "是否收藏“影视”？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSetFavorite(!isFavorite)
                        showFavoriteDialog = false
                    },
                ) {
                    Text(if (isFavorite) "取消收藏" else "收藏")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFavoriteDialog = false }) {
                    Text("关闭")
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BackupRestoreToolCard(
    backupCount: Int,
    isAuthorized: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onSetFavorite: (Boolean) -> Unit,
) {
    var showFavoriteDialog by remember { mutableStateOf(false) }

    ToolCard(
        title = "备份与恢复",
        subtitle = if (backupCount > 0) "已备份 $backupCount 个安装包" else "提取安装包，保存到百度网盘",
        status = if (isFavorite) "已收藏" else if (isAuthorized) "网盘已授权" else "需要配置网盘",
        icon = Icons.Default.SettingsBackupRestore,
        onClick = onClick,
        onLongClick = { showFavoriteDialog = true },
    )

    if (showFavoriteDialog) {
        AlertDialog(
            onDismissRequest = { showFavoriteDialog = false },
            title = { Text(if (isFavorite) "取消收藏" else "收藏功能") },
            text = { Text(if (isFavorite) "是否取消收藏“备份与恢复”？" else "是否收藏“备份与恢复”？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSetFavorite(!isFavorite)
                        showFavoriteDialog = false
                    },
                ) {
                    Text(if (isFavorite) "取消收藏" else "收藏")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFavoriteDialog = false }) {
                    Text("关闭")
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreHomeScreen(
    isBaiduAuthorized: Boolean,
    backupCount: Int,
    onBack: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("备份与恢复") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            StatusCard(
                isAuthorized = isBaiduAuthorized,
                backupCount = backupCount,
                onOpenSettings = onOpenSettings,
            )
            ActionCard(
                title = "备份应用",
                subtitle = "显示手机所有应用，选择后提取安装包并上传",
                icon = Icons.Default.Backup,
                onClick = onBackup,
            )
            ActionCard(
                title = "恢复应用",
                subtitle = "查看已备份的软件，选择链接下载或百度网盘恢复",
                icon = Icons.Default.Restore,
                onClick = onRestore,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ToolCard(
    title: String,
    subtitle: String,
    status: String,
    icon: ImageVector,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun StatusCard(
    isAuthorized: Boolean,
    backupCount: Int,
    onOpenSettings: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.CloudDone,
                contentDescription = null,
                tint = if (isAuthorized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = if (isAuthorized) "百度网盘已连接" else "百度网盘未授权", style = MaterialTheme.typography.titleMedium)
                Text("当前已读取 $backupCount 个备份文件", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onOpenSettings) {
                Text(if (isAuthorized) "管理" else "去配置")
            }
        }
    }
}

@Composable
private fun ActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
