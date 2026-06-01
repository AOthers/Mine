package com.wode.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wode.app.service.BaiduPanService
import com.wode.app.service.TokenStore
import com.wode.app.ui.screens.AppListScreen
import com.wode.app.ui.screens.BackupScreen
import com.wode.app.ui.screens.SettingsScreen
import com.wode.app.ui.theme.WodeTheme
import com.wode.app.viewmodel.AppListViewModel
import com.wode.app.viewmodel.BackupViewModel
import com.wode.app.viewmodel.InstallEvent
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var tokenStore: TokenStore
    private lateinit var panService: BaiduPanService

    private var currentScreen by mutableStateOf<Screen>(Screen.AppList)
    private var latestIntent by mutableStateOf<Intent?>(null)
    private var pendingAppKey: String? = null

    sealed class Screen {
        object AppList : Screen()
        object Backup : Screen()
        object Settings : Screen()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tokenStore = TokenStore(this)
        panService = BaiduPanService(this)

        latestIntent = intent

        setContent {
            WodeTheme {
                val appListViewModel: AppListViewModel = viewModel()
                val backupViewModel: BackupViewModel = viewModel()

                LaunchedEffect(latestIntent) {
                    handleOAuthCallback(latestIntent, backupViewModel)
                }

                // 收集安装事件
                LaunchedEffect(Unit) {
                    backupViewModel.installEvents.collect { event ->
                        when (event) {
                            is InstallEvent.Toast ->
                                Toast.makeText(this@MainActivity, event.message, Toast.LENGTH_SHORT).show()
                            is InstallEvent.InstallApk ->
                                installApk(event.file)
                        }
                    }
                }

                when (currentScreen) {
                    is Screen.AppList -> {
                        AppListScreen(
                            viewModel = appListViewModel,
                            onStartBackup = { apps ->
                                backupViewModel.startBackup(apps)
                                currentScreen = Screen.Backup
                            },
                            onNavigateSettings = { currentScreen = Screen.Settings },
                        )
                    }
                    is Screen.Backup -> {
                        val uiState by backupViewModel.uiState.collectAsState()
                        BackupScreen(
                            uiState = uiState,
                            onCancel = { backupViewModel.cancelBackup() },
                            onBack = {
                                appListViewModel.loadApps()
                                currentScreen = Screen.AppList
                            },
                        )
                    }
                    is Screen.Settings -> {
                        SettingsScreen(
                            viewModel = backupViewModel,
                            tokenStore = tokenStore,
                            onBack = { currentScreen = Screen.AppList },
                            onStartOAuth = { appKey ->
                                pendingAppKey = appKey
                                startOAuth(appKey)
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        latestIntent = intent
    }

    // ==================== OAuth ====================

    private fun startOAuth(appKey: String) {
        val oauthUrl = panService.getOAuthUrl(appKey)
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(oauthUrl)))
    }

    private suspend fun handleOAuthCallback(intent: Intent?, backupViewModel: BackupViewModel) {
        val uri = intent?.data ?: return
        if (uri.scheme != "wode" || uri.host != "baidu.oauth") return

        panService.extractAuthError(uri)?.let { error ->
            Toast.makeText(this, "授权失败：$error", Toast.LENGTH_LONG).show()
            return
        }

        val authCode = panService.extractAuthCode(uri) ?: run {
            Toast.makeText(this, "授权失败：未获取到授权码", Toast.LENGTH_SHORT).show()
            return
        }

        val appKey = (pendingAppKey ?: tokenStore.getAppKey()).trim()
        if (appKey.isBlank()) {
            Toast.makeText(this, "请先在设置中配置 AppKey", Toast.LENGTH_SHORT).show()
            return
        }

        exchangeToken(backupViewModel, appKey, authCode)
    }

    private suspend fun exchangeToken(backupViewModel: BackupViewModel, appKey: String, authCode: String) {
        val secretKey = tokenStore.getSecretKey().trim()
        if (secretKey.isBlank()) {
            Toast.makeText(this@MainActivity, "请先在设置中配置 SecretKey", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this@MainActivity, "正在授权...", Toast.LENGTH_SHORT).show()
        backupViewModel.handleOAuthCallback(appKey, secretKey, authCode)
            .onSuccess { Toast.makeText(this@MainActivity, "百度网盘授权成功！", Toast.LENGTH_SHORT).show() }
            .onFailure { Toast.makeText(this@MainActivity, "授权失败: ${it.message}", Toast.LENGTH_LONG).show() }
    }

    // ==================== 安装 APK ====================

    private fun installApk(file: java.io.File) {
        val uri = FileProvider.getUriForFile(
            this,
            "$packageName.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "无法启动安装: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
