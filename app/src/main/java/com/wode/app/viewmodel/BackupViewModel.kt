package com.wode.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wode.app.data.AppInfo
import com.wode.app.data.BaiduToken
import com.wode.app.data.BackupRecord
import com.wode.app.service.ApkExtractor
import com.wode.app.service.BaiduPanService
import com.wode.app.service.TokenStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

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

/** 需要 Activity 处理的事件 */
sealed class InstallEvent {
    data class InstallApk(val file: File) : InstallEvent()
    data class Toast(val message: String) : InstallEvent()
}

class BackupViewModel(application: Application) : AndroidViewModel(application) {

    private val extractor = ApkExtractor(application)
    private val panService = BaiduPanService(application)
    private val tokenStore = TokenStore(application)

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    val isAuthorized: StateFlow<Boolean> = tokenStore.isAuthorized

    private val _backupRecords = MutableStateFlow<List<BackupRecord>>(emptyList())
    val backupRecords: StateFlow<List<BackupRecord>> = _backupRecords.asStateFlow()

    private val _installEvents = MutableSharedFlow<InstallEvent>()
    val installEvents: SharedFlow<InstallEvent> = _installEvents.asSharedFlow()

    private var backupJob: Job? = null

    // ==================== 备份 ====================

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
            phase = BackupUiState.Phase.CANCELLED, isRunning = false,
        )
    }

    private suspend fun runBackup(apps: List<AppInfo>) {
        val token = getValidToken() ?: run {
            updateAllToFailed("请先登录百度网盘"); return
        }

        for (app in apps) {
            try {
                updateItemStatus(app.packageName, BackupItemState.Status.EXTRACTING)
                val apkFile = extractor.copyApkToCache(app)
                    ?: throw Exception("无法提取 APK")

                updateItemStatus(app.packageName, BackupItemState.Status.UPLOADING)
                val remotePath = "${BaiduPanService.BACKUP_PATH}/${app.packageName}_${app.versionName}.apk"

                val result = panService.uploadFile(token, apkFile, remotePath) { uploaded, total ->
                    val p = if (total > 0) uploaded.toFloat() / total else 0f
                    updateItemProgress(app.packageName, p.coerceIn(0f, 1f))
                }

                if (result.isSuccess) {
                    updateItemStatus(app.packageName, BackupItemState.Status.COMPLETED, 1f)
                    _uiState.value = _uiState.value.copy(
                        completedCount = _uiState.value.completedCount + 1,
                    )
                    _backupRecords.value = _backupRecords.value + result.getOrThrow().copy(
                        packageName = app.packageName, appName = app.appName,
                        versionName = app.versionName,
                    )
                } else {
                    throw result.exceptionOrNull() ?: Exception("上传失败")
                }
                apkFile.delete()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                updateItemStatus(app.packageName, BackupItemState.Status.FAILED,
                    error = e.message ?: "未知错误")
            }
        }
        _uiState.value = _uiState.value.copy(
            phase = BackupUiState.Phase.COMPLETED, isRunning = false,
        )
    }

    // ==================== 网盘授权 ====================

    suspend fun handleOAuthCallback(appKey: String, secretKey: String, authCode: String): Result<BaiduToken> {
        val result = panService.exchangeToken(appKey, secretKey, authCode)
        if (result.isSuccess) tokenStore.saveToken(result.getOrThrow())
        return result
    }

    fun logout() { tokenStore.clearToken() }

    // ==================== 备份记录 ====================

    fun loadBackupRecords() {
        viewModelScope.launch {
            val token = getValidToken() ?: return@launch
            panService.listBackupFiles(token).onSuccess { files ->
                _backupRecords.value = files.map { f ->
                    BackupRecord(
                        packageName = extractPackageName(f.path ?: ""),
                        appName = f.serverFilename ?: "", versionName = "",
                        fsId = f.fsId, path = f.path ?: "", size = f.size ?: 0,
                        uploadTime = (f.serverMtime ?: 0) * 1000, md5 = f.md5 ?: "",
                    )
                }
            }
        }
    }

    // ==================== 下载 & 安装 ====================

    /**
     * 下载网盘中的 APK 并触发安装
     */
    fun downloadAndInstall(fsId: Long) {
        viewModelScope.launch {
            val token = getValidToken() ?: run {
                _installEvents.emit(InstallEvent.Toast("请先登录百度网盘"))
                return@launch
            }

            // 1. 获取下载链接
            val dlink = panService.getDownloadUrl(token, fsId).getOrElse {
                _installEvents.emit(InstallEvent.Toast("获取下载链接失败: ${it.message}"))
                return@launch
            }

            // 2. 下载到缓存目录
            val downloadDir = File(getApplication<Application>().cacheDir, "apk_download")
            val destFile = File(downloadDir, "restore_${fsId}.apk")

            _installEvents.emit(InstallEvent.Toast("正在下载..."))
            panService.downloadFile(token, dlink, destFile).onSuccess { file ->
                _installEvents.emit(InstallEvent.InstallApk(file))
            }.onFailure {
                _installEvents.emit(InstallEvent.Toast("下载失败: ${it.message}"))
            }
        }
    }

    // ==================== 内部辅助 ====================

    private suspend fun getValidToken(): String? {
        val token = tokenStore.getToken() ?: return null
        if (token.isExpired) {
            val refreshed = panService.refreshToken(
                tokenStore.getAppKey(), tokenStore.getSecretKey(), token.refreshToken
            )
            if (refreshed.isSuccess) {
                tokenStore.saveToken(refreshed.getOrThrow())
                return refreshed.getOrThrow().accessToken
            } else {
                tokenStore.clearToken()
                return null
            }
        }
        return token.accessToken
    }

    private fun updateItemStatus(pkg: String, status: BackupItemState.Status,
                                  progress: Float = 0f, error: String? = null) {
        _uiState.value = _uiState.value.copy(items = _uiState.value.items.map {
            if (it.packageName == pkg) it.copy(status = status, progress = progress, error = error) else it
        })
    }

    private fun updateItemProgress(pkg: String, progress: Float) {
        _uiState.value = _uiState.value.copy(items = _uiState.value.items.map {
            if (it.packageName == pkg) it.copy(progress = progress, status = BackupItemState.Status.UPLOADING) else it
        })
    }

    private fun updateAllToFailed(error: String) {
        _uiState.value = _uiState.value.copy(
            items = _uiState.value.items.map { it.copy(status = BackupItemState.Status.FAILED, error = error) },
            phase = BackupUiState.Phase.COMPLETED, isRunning = false,
        )
    }

    private fun extractPackageName(path: String): String {
        val filename = path.substringAfterLast("/")
        val idx = filename.lastIndexOf('_')
        return if (idx > 0) filename.substring(0, idx) else filename.substringBeforeLast(".apk")
    }

    override fun onCleared() {
        super.onCleared()
        extractor.clearCache()
    }
}
