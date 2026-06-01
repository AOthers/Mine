package com.wode.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wode.app.data.BackupRecord
import com.wode.app.service.BaiduPanService
import com.wode.app.service.FavoriteStore
import com.wode.app.service.RestoreLinkStore
import com.wode.app.service.TokenStore
import com.wode.app.ui.screens.AppListScreen
import com.wode.app.ui.screens.BackupRestoreHomeScreen
import com.wode.app.ui.screens.BackupScreen
import com.wode.app.ui.screens.FavoritesScreen
import com.wode.app.ui.screens.MineScreen
import com.wode.app.ui.screens.RestoreScreen
import com.wode.app.ui.screens.SettingsScreen
import com.wode.app.ui.screens.ToolboxHomeScreen
import com.wode.app.ui.screens.ToolboxHomeScreenContent
import com.wode.app.ui.theme.WodeTheme
import com.wode.app.viewmodel.AppListViewModel
import com.wode.app.viewmodel.BackupViewModel
import com.wode.app.viewmodel.InstallEvent

class MainActivity : ComponentActivity() {

    private lateinit var tokenStore: TokenStore
    private lateinit var panService: BaiduPanService
    private lateinit var restoreLinkStore: RestoreLinkStore
    private lateinit var favoriteStore: FavoriteStore

    private var currentScreen by mutableStateOf<Screen>(Screen.Main(MainTab.Tools))
    private var latestIntent by mutableStateOf<Intent?>(null)
    private var pendingAppKey: String? = null
    private var backupViewModelRef: BackupViewModel? = null
    private var isBackupRestoreFavorite by mutableStateOf(false)

    private val chooseRestoreFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            backupViewModelRef?.saveRestoreTreeUri(uri)
            Toast.makeText(this, "恢复文件夹已更新", Toast.LENGTH_SHORT).show()
        }
    }

    sealed class Screen {
        data class Main(val tab: MainTab) : Screen()
        object BackupRestoreHome : Screen()
        object BackupRestoreSettings : Screen()
        object AppList : Screen()
        object BackupProgress : Screen()
        object RestoreList : Screen()
    }

    enum class MainTab { Tools, Favorites, Mine }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tokenStore = TokenStore(this)
        panService = BaiduPanService(this)
        restoreLinkStore = RestoreLinkStore(this)
        favoriteStore = FavoriteStore(this)
        isBackupRestoreFavorite = favoriteStore.isFavorite(FavoriteStore.TOOL_BACKUP_RESTORE)
        latestIntent = intent
        requestNotificationPermissionIfNeeded()

        setContent {
            WodeTheme {
                val appListViewModel: AppListViewModel = viewModel()
                val backupViewModel: BackupViewModel = viewModel()
                backupViewModelRef = backupViewModel

                val isAuthorized by backupViewModel.isAuthorized.collectAsState()
                val backupRecords by backupViewModel.backupRecords.collectAsState()
                val restoredApks by backupViewModel.restoredApks.collectAsState()
                val installedApps by appListViewModel.apps.collectAsState()

                BackHandler(enabled = shouldHandleSystemBack()) {
                    handleSystemBack(appListViewModel, backupViewModel)
                }

                LaunchedEffect(latestIntent) {
                    handleOAuthCallback(latestIntent, backupViewModel)
                }

                LaunchedEffect(Unit) {
                    backupViewModel.installEvents.collect { event ->
                        when (event) {
                            is InstallEvent.Toast ->
                                Toast.makeText(this@MainActivity, event.message, Toast.LENGTH_SHORT).show()
                            is InstallEvent.InstallApk -> installApk(event.file)
                            is InstallEvent.InstallUri -> installApk(event.uri)
                        }
                    }
                }

                when (val screen = currentScreen) {
                    is Screen.Main -> {
                        ToolboxHomeScreen(
                            selectedTab = screen.tab,
                            onTabSelected = { currentScreen = Screen.Main(it) },
                            toolsContent = {
                                ToolboxHomeScreenContent(
                                    isBaiduAuthorized = isAuthorized,
                                    backupCount = backupRecords.size,
                                    isBackupRestoreFavorite = isBackupRestoreFavorite,
                                    onOpenBackupRestore = {
                                        backupViewModel.loadBackupRecords()
                                        currentScreen = Screen.BackupRestoreHome
                                    },
                                    onSetBackupRestoreFavorite = ::updateBackupRestoreFavorite,
                                )
                            },
                            favoritesContent = {
                                FavoritesScreen(
                                    isBackupRestoreFavorite = isBackupRestoreFavorite,
                                    backupCount = backupRecords.size,
                                    isBaiduAuthorized = isAuthorized,
                                    onOpenBackupRestore = {
                                        backupViewModel.loadBackupRecords()
                                        currentScreen = Screen.BackupRestoreHome
                                    },
                                    onSetBackupRestoreFavorite = ::updateBackupRestoreFavorite,
                                )
                            },
                            mineContent = { MineScreen() },
                        )
                    }

                    Screen.BackupRestoreHome -> {
                        BackupRestoreHomeScreen(
                            isBaiduAuthorized = isAuthorized,
                            backupCount = backupRecords.size,
                            onBack = { currentScreen = Screen.Main(MainTab.Tools) },
                            onBackup = { currentScreen = Screen.AppList },
                            onRestore = {
                                backupViewModel.loadBackupRecords()
                                backupViewModel.loadRestoredApks()
                                currentScreen = Screen.RestoreList
                            },
                            onOpenSettings = { currentScreen = Screen.BackupRestoreSettings },
                        )
                    }

                    Screen.BackupRestoreSettings -> {
                        SettingsScreen(
                            viewModel = backupViewModel,
                            tokenStore = tokenStore,
                            onBack = { currentScreen = Screen.BackupRestoreHome },
                            onStartOAuth = { appKey ->
                                pendingAppKey = appKey
                                startOAuth(appKey)
                            },
                            onChooseRestoreFolder = {
                                chooseRestoreFolderLauncher.launch(null)
                            },
                            onPathSaved = { message ->
                                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                            },
                        )
                    }

                    Screen.AppList -> {
                        AppListScreen(
                            viewModel = appListViewModel,
                            onStartBackup = { apps ->
                                backupViewModel.startBackup(apps)
                                currentScreen = Screen.BackupProgress
                            },
                            onBack = { currentScreen = Screen.BackupRestoreHome },
                        )
                    }

                    Screen.BackupProgress -> {
                        val uiState by backupViewModel.uiState.collectAsState()
                        BackupScreen(
                            uiState = uiState,
                            onCancel = { backupViewModel.cancelBackup() },
                            onBack = {
                                appListViewModel.loadApps()
                                backupViewModel.loadBackupRecords()
                                currentScreen = Screen.BackupRestoreHome
                            },
                        )
                    }

                    Screen.RestoreList -> {
                        RestoreScreen(
                            records = backupRecords,
                            restoredApks = restoredApks,
                            installedAppsByPackage = installedApps.associateBy { it.packageName },
                            getLink = restoreLinkStore::getLink,
                            onSaveLink = restoreLinkStore::saveLink,
                            onOpenLink = ::openDownloadLink,
                            onDownloadFromBaidu = { record: BackupRecord ->
                                backupViewModel.downloadAndInstall(record)
                            },
                            onRefresh = { backupViewModel.loadBackupRecords() },
                            onRefreshRestoredApks = { backupViewModel.loadRestoredApks() },
                            onDeleteRestoredApk = { backupViewModel.deleteRestoredApk(it) },
                            onBack = { currentScreen = Screen.BackupRestoreHome },
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

    private fun shouldHandleSystemBack(): Boolean {
        return currentScreen !is Screen.Main || (currentScreen as? Screen.Main)?.tab != MainTab.Tools
    }

    private fun handleSystemBack(appListViewModel: AppListViewModel, backupViewModel: BackupViewModel) {
        when (val screen = currentScreen) {
            is Screen.Main -> {
                if (screen.tab != MainTab.Tools) currentScreen = Screen.Main(MainTab.Tools)
            }
            Screen.BackupRestoreHome -> currentScreen = Screen.Main(MainTab.Tools)
            Screen.BackupRestoreSettings -> currentScreen = Screen.BackupRestoreHome
            Screen.AppList -> currentScreen = Screen.BackupRestoreHome
            Screen.RestoreList -> currentScreen = Screen.BackupRestoreHome
            Screen.BackupProgress -> {
                appListViewModel.loadApps()
                backupViewModel.loadBackupRecords()
                currentScreen = Screen.BackupRestoreHome
            }
        }
    }

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
            Toast.makeText(this, "请先在备份与恢复设置中配置 AppKey", Toast.LENGTH_SHORT).show()
            return
        }

        exchangeToken(backupViewModel, appKey, authCode)
    }

    private suspend fun exchangeToken(backupViewModel: BackupViewModel, appKey: String, authCode: String) {
        val secretKey = tokenStore.getSecretKey().trim()
        if (secretKey.isBlank()) {
            Toast.makeText(this, "请先在备份与恢复设置中配置 SecretKey", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "正在授权...", Toast.LENGTH_SHORT).show()
        backupViewModel.handleOAuthCallback(appKey, secretKey, authCode)
            .onSuccess {
                Toast.makeText(this, "百度网盘授权成功", Toast.LENGTH_SHORT).show()
                backupViewModel.loadBackupRecords()
                currentScreen = Screen.BackupRestoreHome
            }
            .onFailure {
                Toast.makeText(this, "授权失败：${it.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun openDownloadLink(link: String) {
        val url = link.trim()
        if (url.isBlank()) {
            Toast.makeText(this, "请先填写下载链接", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            Toast.makeText(this, "无法打开链接：${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun installApk(file: java.io.File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        installApk(uri)
    }

    private fun installApk(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "无法启动安装：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            REQUEST_POST_NOTIFICATIONS,
        )
    }

    private fun updateBackupRestoreFavorite(favorite: Boolean) {
        favoriteStore.setFavorite(FavoriteStore.TOOL_BACKUP_RESTORE, favorite)
        isBackupRestoreFavorite = favorite
        Toast.makeText(this, if (favorite) "已收藏" else "已取消收藏", Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val REQUEST_POST_NOTIFICATIONS = 2001
    }
}
