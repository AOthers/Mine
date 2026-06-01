package com.wode.app.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wode.app.data.AppInfo
import com.wode.app.data.BackupRecord
import com.wode.app.viewmodel.RestoredApk
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class RestoreAppGroup(
    val packageName: String,
    val displayName: String,
    val records: List<BackupRecord>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestoreScreen(
    records: List<BackupRecord>,
    restoredApks: List<RestoredApk>,
    installedAppsByPackage: Map<String, AppInfo>,
    getLink: (String) -> String,
    onSaveLink: (String, String) -> Unit,
    onOpenLink: (String) -> Unit,
    onDownloadFromBaidu: (BackupRecord) -> Unit,
    onRefresh: () -> Unit,
    onRefreshRestoredApks: () -> Unit,
    onDeleteRestoredApk: (RestoredApk) -> Unit,
    onBack: () -> Unit,
) {
    var selectedGroup by remember { mutableStateOf<RestoreAppGroup?>(null) }
    var showDownloads by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val groups = remember(records, installedAppsByPackage, query) {
        val keyword = query.trim()
        records
            .groupBy { it.packageName }
            .map { (packageName, groupRecords) ->
                val sortedRecords = groupRecords.sortedByDescending { it.uploadTime }
                RestoreAppGroup(
                    packageName = packageName,
                    displayName = sortedRecords.first().displayName(installedAppsByPackage),
                    records = sortedRecords,
                )
            }
            .filter { group ->
                keyword.isBlank() ||
                    group.displayName.contains(keyword, ignoreCase = true) ||
                    group.packageName.contains(keyword, ignoreCase = true) ||
                    group.records.any { it.versionName.contains(keyword, ignoreCase = true) }
            }
            .sortedBy { it.displayName.lowercase(Locale.getDefault()) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("恢复应用") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            onRefreshRestoredApks()
                            showDownloads = true
                        },
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "已下载安装包")
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
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
                .background(MaterialTheme.colorScheme.background),
        ) {
            SearchBar(
                query = query,
                onQueryChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (groups.isEmpty()) {
                EmptyRestoreState(onRefresh = onRefresh)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    items(groups, key = { it.packageName }) { group ->
                        RestoreAppCard(
                            group = group,
                            app = installedAppsByPackage[group.packageName],
                            onClick = { selectedGroup = group },
                        )
                    }
                }
            }
        }
    }

    selectedGroup?.let { group ->
        RestoreVersionsDialog(
            group = group,
            initialLink = getLink(group.packageName),
            onDismiss = { selectedGroup = null },
            onSaveLink = { link -> onSaveLink(group.packageName, link) },
            onOpenLink = onOpenLink,
            onDownloadFromBaidu = onDownloadFromBaidu,
        )
    }

    if (showDownloads) {
        DownloadedApksDialog(
            apks = restoredApks,
            onDismiss = { showDownloads = false },
            onDelete = onDeleteRestoredApk,
        )
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text("搜索应用、包名或版本") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "搜索") },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "清除")
                }
            }
        },
        singleLine = true,
    )
}

@Composable
private fun RestoreAppCard(group: RestoreAppGroup, app: AppInfo?, onClick: () -> Unit) {
    val latest = group.records.first()
    val iconBitmap = remember(app?.packageName, app?.icon) { app?.icon?.toBitmap() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap.asImageBitmap(),
                        contentDescription = "${group.displayName} 图标",
                        modifier = Modifier.padding(4.dp),
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(group.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = group.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${group.records.size} 个版本 · 最新 ${latest.versionLabel} · ${latest.formattedTime}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RestoreVersionsDialog(
    group: RestoreAppGroup,
    initialLink: String,
    onDismiss: () -> Unit,
    onSaveLink: (String) -> Unit,
    onOpenLink: (String) -> Unit,
    onDownloadFromBaidu: (BackupRecord) -> Unit,
) {
    var link by remember(group.packageName) { mutableStateOf(initialLink) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(group.displayName) },
        text = {
            Column {
                Text(group.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it },
                    label = { Text("官方或其他下载链接") },
                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        onSaveLink(link)
                        onOpenLink(link)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("打开链接")
                }
                Spacer(Modifier.height(12.dp))
                Text("选择网盘版本", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                group.records.forEach { record ->
                    VersionRow(
                        record = record,
                        onClick = {
                            onDismiss()
                            onDownloadFromBaidu(record)
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSaveLink(link)
                    onDismiss()
                },
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun DownloadedApksDialog(
    apks: List<RestoredApk>,
    onDismiss: () -> Unit,
    onDelete: (RestoredApk) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("已下载安装包") },
        text = {
            if (apks.isEmpty()) {
                Text("还没有恢复下载的安装包", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(360.dp)) {
                    items(apks, key = { it.uri ?: it.path }) { apk ->
                        DownloadedApkRow(apk = apk, onDelete = { onDelete(apk) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
    )
}

@Composable
private fun DownloadedApkRow(apk: RestoredApk, onDelete: () -> Unit) {
    val iconBitmap = remember(apk.path, apk.uri, apk.icon) { apk.icon?.toBitmap() }

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap.asImageBitmap(),
                    contentDescription = "${apk.appName} 图标",
                    modifier = Modifier.size(34.dp),
                )
            } else {
                Icon(
                    Icons.Default.InsertDriveFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(30.dp),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
            ) {
                Text(apk.appName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = apk.packageName.ifBlank { "未知包名" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "版本 ${apk.versionName.ifBlank { "未知" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${apk.formattedSize} · ${apk.formattedTime}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = apk.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun VersionRow(record: BackupRecord, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("版本 ${record.versionLabel}", style = MaterialTheme.typography.bodyLarge)
            Text("${record.formattedSize} · ${record.formattedTime}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyRestoreState(onRefresh: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("还没有读取到备份", style = MaterialTheme.typography.titleMedium)
            Text("确认百度网盘已授权后，点击刷新重新读取", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRefresh) {
                Text("刷新")
            }
        }
    }
}

private fun Drawable.toBitmap(): Bitmap {
    if (this is BitmapDrawable) return bitmap
    val width = intrinsicWidth.coerceAtLeast(1)
    val height = intrinsicHeight.coerceAtLeast(1)
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas = Canvas(bitmap)
        setBounds(0, 0, width, height)
        draw(canvas)
    }
}

private fun BackupRecord.displayName(installedAppsByPackage: Map<String, AppInfo>): String {
    return appName.takeUnless { it.looksLikePackageOrApkName() }
        ?: installedAppsByPackage[packageName]?.appName
        ?: path.substringAfterLast("/").substringBeforeLast(".apk")
}

private fun String.looksLikePackageOrApkName(): Boolean {
    if (isBlank()) return true
    return endsWith(".apk", ignoreCase = true) ||
        matches(Regex("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+(_.*)?"))
}

private val BackupRecord.versionLabel: String
    get() = versionName.ifBlank { "未知" }

private val BackupRecord.formattedSize: String
    get() = when {
        size < 1024 -> "${size}B"
        size < 1024 * 1024 -> "${size / 1024}KB"
        else -> "%.1fMB".format(size / (1024.0 * 1024.0))
    }

private val BackupRecord.formattedTime: String
    get() = if (uploadTime > 0) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(uploadTime))
    } else {
        "未知时间"
    }

private val RestoredApk.formattedTime: String
    get() = if (modifiedTime > 0) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(modifiedTime))
    } else {
        "未知时间"
    }
