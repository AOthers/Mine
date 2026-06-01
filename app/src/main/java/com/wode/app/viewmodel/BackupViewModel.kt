package com.wode.app.viewmodel

import android.app.Application
import android.content.pm.PackageManager
import android.content.pm.PackageInfo
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wode.app.data.AppInfo
import com.wode.app.data.BaiduToken
import com.wode.app.data.BackupRecord
import com.wode.app.service.ApkExtractor
import com.wode.app.service.BaiduPanService
import com.wode.app.service.ProgressNotifier
import com.wode.app.service.RestoreTarget
import com.wode.app.service.TokenStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BackupItemState(
    val packageName: String,
    val appName: String,
    val status: Status = Status.PENDING,
    val progress: Float = 0f,
    val error: String? = null,
) {
    enum class Status { PENDING, EXTRACTING, UPLOADING, COMPLETED, FAILED }
}

data class BackupUiState(
    val items: List<BackupItemState> = emptyList(),
    val phase: Phase = Phase.IDLE,
    val isRunning: Boolean = false,
    val completedCount: Int = 0,
    val totalCount: Int = 0,
) {
    enum class Phase { IDLE, RUNNING, COMPLETED, CANCELLED }
}

data class RestoredApk(
    val name: String,
    val appName: String,
    val versionName: String,
    val packageName: String,
    val path: String,
    val uri: String?,
    val size: Long,
    val modifiedTime: Long,
    val icon: Drawable? = null,
) {
    val formattedSize: String
        get() = when {
            size < 1024 -> "${size}B"
            size < 1024 * 1024 -> "${size / 1024}KB"
            else -> "%.1fMB".format(size / (1024.0 * 1024.0))
        }
}

sealed class InstallEvent {
    data class InstallApk(val file: File) : InstallEvent()
    data class InstallUri(val uri: Uri) : InstallEvent()
    data class Toast(val message: String) : InstallEvent()
}

private data class ParsedBackupName(
    val appName: String,
    val packageName: String,
    val versionName: String,
)

private data class ApkMetadata(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val icon: Drawable?,
)

class BackupViewModel(application: Application) : AndroidViewModel(application) {

    private val extractor = ApkExtractor(application)
    private val panService = BaiduPanService(application)
    private val tokenStore = TokenStore(application)
    private val notifier = ProgressNotifier(application)

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    val isAuthorized: StateFlow<Boolean> = tokenStore.isAuthorized

    private val _backupRecords = MutableStateFlow<List<BackupRecord>>(emptyList())
    val backupRecords: StateFlow<List<BackupRecord>> = _backupRecords.asStateFlow()

    private val _restoredApks = MutableStateFlow<List<RestoredApk>>(emptyList())
    val restoredApks: StateFlow<List<RestoredApk>> = _restoredApks.asStateFlow()

    private val _installEvents = MutableSharedFlow<InstallEvent>()
    val installEvents: SharedFlow<InstallEvent> = _installEvents.asSharedFlow()

    private var backupJob: Job? = null

    fun startBackup(apps: List<AppInfo>) {
        if (_uiState.value.isRunning) return
        _uiState.value = BackupUiState(
            items = apps.map { BackupItemState(it.packageName, it.appName) },
            phase = BackupUiState.Phase.RUNNING,
            isRunning = true,
            totalCount = apps.size,
        )
        backupJob = viewModelScope.launch { runBackup(apps) }
    }

    fun cancelBackup() {
        backupJob?.cancel()
        _uiState.value = _uiState.value.copy(
            phase = BackupUiState.Phase.CANCELLED,
            isRunning = false,
        )
        notifier.showComplete(ProgressNotifier.BACKUP_ID, "备份已取消", "备份任务已停止")
    }

    private suspend fun runBackup(apps: List<AppInfo>) {
        val token = getValidToken() ?: run {
            updateAllToFailed("请先登录百度网盘")
            return
        }
        val backupPath = tokenStore.getBackupPath()
        val sameVersionStrategy = tokenStore.getSameVersionStrategy()

        notifier.showProgress(ProgressNotifier.BACKUP_ID, "正在备份应用", "准备备份 ${apps.size} 个应用", 0)

        for (app in apps) {
            try {
                updateItemStatus(app.packageName, BackupItemState.Status.EXTRACTING)
                val apkFile = extractor.copyApkToCache(app) ?: throw Exception("无法提取 APK")

                updateItemStatus(app.packageName, BackupItemState.Status.UPLOADING)
                val baseFilename = buildBackupFilename(app)
                val overwrite = sameVersionStrategy == TokenStore.SameVersionStrategy.OVERWRITE
                val filename = if (overwrite) baseFilename else appendCopySuffix(baseFilename)
                val remotePath = "$backupPath/$filename"

                val result = panService.uploadFile(token, apkFile, remotePath, overwrite) { uploaded, total ->
                    val progress = if (total > 0) uploaded.toFloat() / total else 0f
                    updateItemProgress(app.packageName, progress.coerceIn(0f, 1f))
                    notifier.showProgress(
                        ProgressNotifier.BACKUP_ID,
                        "正在备份应用",
                        "${app.appName} ${(progress * 100).toInt()}%",
                        (progress * 100).toInt(),
                    )
                }

                if (result.isSuccess) {
                    updateItemStatus(app.packageName, BackupItemState.Status.COMPLETED, 1f)
                    _uiState.value = _uiState.value.copy(
                        completedCount = _uiState.value.completedCount + 1,
                    )
                    _backupRecords.value = _backupRecords.value + result.getOrThrow().copy(
                        packageName = app.packageName,
                        appName = app.appName,
                        versionName = app.versionName,
                    )
                } else {
                    throw result.exceptionOrNull() ?: Exception("上传失败")
                }
                apkFile.delete()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                updateItemStatus(app.packageName, BackupItemState.Status.FAILED, error = e.message ?: "未知错误")
            }
        }

        _uiState.value = _uiState.value.copy(
            phase = BackupUiState.Phase.COMPLETED,
            isRunning = false,
        )
        notifier.showComplete(
            ProgressNotifier.BACKUP_ID,
            "备份完成",
            "已完成 ${_uiState.value.completedCount}/${apps.size} 个应用",
        )
    }

    suspend fun handleOAuthCallback(appKey: String, secretKey: String, authCode: String): Result<BaiduToken> {
        val result = panService.exchangeToken(appKey, secretKey, authCode)
        if (result.isSuccess) tokenStore.saveToken(result.getOrThrow())
        return result
    }

    fun logout() {
        tokenStore.clearToken()
    }

    fun saveRestoreTreeUri(uri: Uri) {
        tokenStore.saveRestoreTreeUri(uri.toString())
        loadRestoredApks()
    }

    fun loadBackupRecords() {
        viewModelScope.launch {
            val token = getValidToken() ?: return@launch
            panService.listBackupFiles(token, tokenStore.getBackupPath()).onSuccess { files ->
                _backupRecords.value = files
                    .filter { it.isDir != 1 }
                    .map { file ->
                        val parsed = parseBackupName(file.path ?: "")
                        BackupRecord(
                            packageName = parsed.packageName,
                            appName = parsed.appName,
                            versionName = parsed.versionName,
                            fsId = file.fsId,
                            path = file.path ?: "",
                            size = file.size ?: 0,
                            uploadTime = (file.serverMtime ?: 0) * 1000,
                            md5 = file.md5 ?: "",
                        )
                    }
            }
        }
    }

    fun downloadAndInstall(record: BackupRecord) {
        viewModelScope.launch {
            if (record.fsId <= 0) {
                _installEvents.emit(InstallEvent.Toast("备份文件信息不完整，请刷新列表后重试"))
                return@launch
            }

            val token = getValidToken() ?: run {
                _installEvents.emit(InstallEvent.Toast("请先登录百度网盘"))
                return@launch
            }

            val dlink = panService.getDownloadUrl(token, record.fsId).getOrElse {
                _installEvents.emit(InstallEvent.Toast("获取下载链接失败：${it.message ?: "未知错误"}"))
                return@launch
            }

            val filename = buildRestoreFilename(record)
            _installEvents.emit(InstallEvent.Toast("正在下载..."))
            notifier.showProgress(ProgressNotifier.RESTORE_ID, "正在恢复应用", "正在下载 APK", 0, indeterminate = true)

            val target = createRestoreTarget(filename).getOrElse {
                _installEvents.emit(InstallEvent.Toast("创建保存文件失败：${it.message}"))
                return@launch
            }

            panService.downloadFile(token, dlink, target) { downloaded, total ->
                val progress = if (total > 0) (downloaded * 100 / total).toInt() else 0
                notifier.showProgress(
                    ProgressNotifier.RESTORE_ID,
                    "正在恢复应用",
                    if (total > 0) "下载中 $progress%" else "正在下载 APK",
                    progress,
                    indeterminate = total <= 0,
                )
            }.onSuccess { saved ->
                notifier.showComplete(
                    ProgressNotifier.RESTORE_ID,
                    "恢复下载完成",
                    "安装包已保存到 ${saved.displayPath}",
                )
                loadRestoredApks()
                saved.installUri?.let {
                    _installEvents.emit(InstallEvent.InstallUri(it))
                } ?: saved.file?.let {
                    _installEvents.emit(InstallEvent.InstallApk(it))
                }
            }.onFailure {
                notifier.showComplete(ProgressNotifier.RESTORE_ID, "恢复下载失败", it.message ?: "未知错误")
                _installEvents.emit(InstallEvent.Toast("下载失败：${it.message}"))
            }
        }
    }

    fun getRestorePath(): String {
        val treeUri = tokenStore.getRestoreTreeUri()
        if (treeUri.isNotBlank()) return treeUri
        return resolveRestoreDirectory().absolutePath
    }

    fun loadRestoredApks() {
        viewModelScope.launch {
            _restoredApks.value = withContext(Dispatchers.IO) {
                val treeUri = tokenStore.getRestoreTreeUri()
                if (treeUri.isNotBlank()) {
                    listSafRestoredApks(Uri.parse(treeUri))
                } else {
                    listFileRestoredApks(resolveRestoreDirectory())
                }
            }
        }
    }

    fun deleteRestoredApk(apk: RestoredApk) {
        viewModelScope.launch(Dispatchers.IO) {
            apk.uri?.let { uri ->
                DocumentFile.fromSingleUri(getApplication(), Uri.parse(uri))?.delete()
            } ?: File(apk.path).delete()
            loadRestoredApks()
        }
    }

    private suspend fun getValidToken(): String? {
        val token = tokenStore.getToken() ?: return null
        if (token.isExpired) {
            val refreshed = panService.refreshToken(tokenStore.getAppKey(), tokenStore.getSecretKey(), token.refreshToken)
            if (refreshed.isSuccess) {
                tokenStore.saveToken(refreshed.getOrThrow())
                return refreshed.getOrThrow().accessToken
            }
            tokenStore.clearToken()
            return null
        }
        return token.accessToken
    }

    private fun createRestoreTarget(filename: String): Result<RestoreTarget> {
        val treeUri = tokenStore.getRestoreTreeUri()
        if (treeUri.isNotBlank()) {
            val dir = DocumentFile.fromTreeUri(getApplication(), Uri.parse(treeUri))
                ?: return Result.failure(Exception("无法打开已选择的文件夹"))
            val file = dir.createFile("application/vnd.android.package-archive", filename)
                ?: return Result.failure(Exception("无法创建 APK 文件"))
            return Result.success(
                RestoreTarget(
                    output = getApplication<Application>().contentResolver.openOutputStream(file.uri)
                        ?: return Result.failure(Exception("无法写入 APK 文件")),
                    displayPath = file.name ?: filename,
                    installUri = file.uri,
                    file = null,
                ),
            )
        }

        val file = File(resolveRestoreDirectory(), filename)
        file.parentFile?.mkdirs()
        return Result.success(
            RestoreTarget(
                output = file.outputStream(),
                displayPath = file.absolutePath,
                installUri = null,
                file = file,
            ),
        )
    }

    private fun listFileRestoredApks(dir: File): List<RestoredApk> {
        return dir.listFiles()
            ?.filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }
            ?.map { file ->
                val parsed = parseBackupName(file.name)
                val metadata = loadApkMetadata(file.absolutePath)
                RestoredApk(
                    name = file.name,
                    appName = metadata?.appName ?: parsed.appName.ifBlank { file.nameWithoutExtension },
                    versionName = metadata?.versionName ?: parsed.versionName,
                    packageName = metadata?.packageName ?: parsed.packageName,
                    path = file.absolutePath,
                    uri = null,
                    size = file.length(),
                    modifiedTime = file.lastModified(),
                    icon = metadata?.icon,
                )
            }
            ?.sortedByDescending { it.modifiedTime }
            ?: emptyList()
    }

    private fun listSafRestoredApks(treeUri: Uri): List<RestoredApk> {
        val dir = DocumentFile.fromTreeUri(getApplication(), treeUri) ?: return emptyList()
        return dir.listFiles()
            .filter { it.isFile && it.name?.endsWith(".apk", ignoreCase = true) == true }
            .map { file ->
                val name = file.name ?: "unknown.apk"
                val parsed = parseBackupName(name)
                val metadata = loadApkMetadata(file.uri)
                RestoredApk(
                    name = name,
                    appName = metadata?.appName ?: parsed.appName.ifBlank { name.substringBeforeLast(".apk") },
                    versionName = metadata?.versionName ?: parsed.versionName,
                    packageName = metadata?.packageName ?: parsed.packageName,
                    path = file.uri.toString(),
                    uri = file.uri.toString(),
                    size = file.length(),
                    modifiedTime = file.lastModified(),
                    icon = metadata?.icon,
                )
            }
            .sortedByDescending { it.modifiedTime }
    }

    private fun loadApkMetadata(path: String): ApkMetadata? {
        val packageManager = getApplication<Application>().packageManager
        val info = packageManager.getPackageArchiveInfo(path, 0) ?: return null
        val appInfo = info.applicationInfo ?: return null
        appInfo.sourceDir = path
        appInfo.publicSourceDir = path
        return ApkMetadata(
            appName = appInfo.loadLabel(packageManager)?.toString().orEmpty(),
            packageName = info.packageName.orEmpty(),
            versionName = info.versionName.orEmpty(),
            icon = appInfo.loadIcon(packageManager),
        )
    }

    private fun loadApkMetadata(uri: Uri): ApkMetadata? {
        val cacheFile = File(getApplication<Application>().cacheDir, "icon_${uri.hashCode()}.apk")
        return runCatching {
            getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                cacheFile.outputStream().use { output -> input.copyTo(output) }
            }
            loadApkMetadata(cacheFile.absolutePath)
        }.getOrNull()
    }

    private fun buildRestoreFilename(record: BackupRecord): String {
        return record.path.substringAfterLast("/").ifBlank {
            "${sanitizeFilenamePart(record.appName)}_${record.packageName}_${sanitizeFilenamePart(record.versionName)}.apk"
        }
    }

    private fun updateItemStatus(packageName: String, status: BackupItemState.Status, progress: Float = 0f, error: String? = null) {
        _uiState.value = _uiState.value.copy(
            items = _uiState.value.items.map {
                if (it.packageName == packageName) it.copy(status = status, progress = progress, error = error) else it
            },
        )
    }

    private fun updateItemProgress(packageName: String, progress: Float) {
        _uiState.value = _uiState.value.copy(
            items = _uiState.value.items.map {
                if (it.packageName == packageName) it.copy(progress = progress, status = BackupItemState.Status.UPLOADING) else it
            },
        )
    }

    private fun updateAllToFailed(error: String) {
        _uiState.value = _uiState.value.copy(
            items = _uiState.value.items.map { it.copy(status = BackupItemState.Status.FAILED, error = error) },
            phase = BackupUiState.Phase.COMPLETED,
            isRunning = false,
        )
    }

    private fun buildBackupFilename(app: AppInfo): String {
        return "${sanitizeFilenamePart(app.appName)}_${app.packageName}_${sanitizeFilenamePart(app.versionName)}.apk"
    }

    private fun appendCopySuffix(filename: String): String {
        val base = filename.substringBeforeLast(".apk")
        val timestamp = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(Date())
        return "${base}_copy-$timestamp.apk"
    }

    private fun sanitizeFilenamePart(value: String): String {
        return value.trim()
            .ifBlank { "unknown" }
            .replace(Regex("[\\\\/:*?\"<>|\\r\\n]+"), "-")
            .replace(Regex("\\s+"), " ")
    }

    private fun parseBackupName(path: String): ParsedBackupName {
        val filename = path.substringAfterLast("/").substringBeforeLast("?")
        val nameWithoutExt = filename.substringBeforeLast(".apk")
        val withoutCopy = nameWithoutExt.replace(Regex("_copy-\\d{14}$"), "")
        val parts = withoutCopy.split("_")
        return if (parts.size >= 3) {
            ParsedBackupName(
                appName = parts.dropLast(2).joinToString("_"),
                packageName = parts[parts.size - 2],
                versionName = parts.last(),
            )
        } else {
            val index = filename.lastIndexOf('_')
            val packageName = if (index > 0) filename.substring(0, index) else nameWithoutExt
            ParsedBackupName(appName = "", packageName = packageName, versionName = "")
        }
    }

    private fun resolveRestoreDirectory(): File {
        val rawPath = tokenStore.getRestorePath()
        val dir = File(rawPath)
        return if (dir.isAbsolute) dir else File(getApplication<Application>().getExternalFilesDir(null), rawPath)
            .also { it.mkdirs() }
    }

    override fun onCleared() {
        super.onCleared()
        extractor.clearCache()
    }
}
