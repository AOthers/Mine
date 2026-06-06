package com.wode.app

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
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
import androidx.compose.runtime.Composable
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wode.app.data.BackupRecord
import com.wode.app.data.UpdateInfo
import com.wode.app.service.BaiduPanService
import com.wode.app.service.FavoriteStore
import com.wode.app.service.MovieSourceStore
import com.wode.app.service.RestoreLinkStore
import com.wode.app.service.TokenStore
import com.wode.app.service.UpdateService
import com.wode.app.ui.screens.AppListScreen
import com.wode.app.ui.screens.BackupRestoreHomeScreen
import com.wode.app.ui.screens.BackupScreen
import com.wode.app.ui.screens.FavoritesScreen
import com.wode.app.ui.screens.MineScreen
import com.wode.app.ui.screens.MovieWebScreen
import com.wode.app.ui.screens.MusicScreen
import com.wode.app.ui.screens.ReaderScreen
import com.wode.app.ui.screens.normalizeMovieUrl
import com.wode.app.ui.screens.RestoreScreen
import com.wode.app.ui.screens.SettingsScreen
import com.wode.app.ui.screens.ToolboxHomeScreen
import com.wode.app.ui.screens.ToolboxHomeScreenContent
import com.wode.app.ui.theme.WodeTheme
import com.wode.app.viewmodel.AppListViewModel
import com.wode.app.viewmodel.BackupViewModel
import com.wode.app.viewmodel.InstallEvent
import com.wode.app.viewmodel.MusicViewModel
import com.wode.app.viewmodel.ReaderEvent
import com.wode.app.viewmodel.ReaderViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException

class MainActivity : ComponentActivity() {

    private lateinit var tokenStore: TokenStore
    private lateinit var panService: BaiduPanService
    private lateinit var restoreLinkStore: RestoreLinkStore
    private lateinit var favoriteStore: FavoriteStore
    private lateinit var updateService: UpdateService
    private lateinit var movieSourceStore: MovieSourceStore

    private var currentScreen by mutableStateOf<Screen>(Screen.Main(MainTab.Tools))
    private var latestIntent by mutableStateOf<Intent?>(null)
    private var pendingAppKey: String? = null
    private var backupViewModelRef: BackupViewModel? = null
    private var musicViewModelRef: MusicViewModel? = null
    private var readerViewModelRef: ReaderViewModel? = null
    private var isBackupRestoreFavorite by mutableStateOf(false)
    private var isMoviesFavorite by mutableStateOf(false)
    private var isMusicFavorite by mutableStateOf(false)
    private var isReaderFavorite by mutableStateOf(false)
    private var pendingUpdate: UpdateInfo? = null
    private var isDownloadingUpdate by mutableStateOf(false)
    private var updateDownloadProgress by mutableStateOf(0f)
    private var updateDownloadJob: Job? = null
    private var updateCheckJob: Job? = null
    private var hasCheckedUpdateOnLaunch = false
    private var dismissedUpdateTagThisSession: String? = null
    private var updateDialog: AlertDialog? = null
    private var updateProgressBar: ProgressBar? = null
    private var updateProgressText: TextView? = null
    private var isActivityResumed = false
    private var pendingEnableSystemLibraryAfterPermission = false

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

    private val chooseMusicFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            musicViewModelRef?.addFolder(uri, hasAudioPermission())
            Toast.makeText(this, "音乐文件夹已添加", Toast.LENGTH_SHORT).show()
        }
    }

    private val chooseReaderFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            readerViewModelRef?.addFile(uri)
        }
    }

    private val chooseReaderFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            readerViewModelRef?.addFolder(uri)
        }
    }

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            if (pendingEnableSystemLibraryAfterPermission) {
                pendingEnableSystemLibraryAfterPermission = false
                musicViewModelRef?.enableSystemLibrary(true)
            } else {
                musicViewModelRef?.loadLibrary(true)
            }
        } else {
            pendingEnableSystemLibraryAfterPermission = false
            musicViewModelRef?.loadLibrary(false)
        }
    }

    sealed class Screen {
        data class Main(val tab: MainTab) : Screen()
        object BackupRestoreHome : Screen()
        object BackupRestoreSettings : Screen()
        object AppList : Screen()
        object BackupProgress : Screen()
        object RestoreList : Screen()
        object MovieWeb : Screen()
        object Music : Screen()
        object Reader : Screen()
    }

    enum class MainTab { Tools, Favorites, Mine }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tokenStore = TokenStore(this)
        panService = BaiduPanService(this)
        restoreLinkStore = RestoreLinkStore(this)
        favoriteStore = FavoriteStore(this)
        updateService = UpdateService(this)
        movieSourceStore = MovieSourceStore(this)
        isBackupRestoreFavorite = favoriteStore.isFavorite(FavoriteStore.TOOL_BACKUP_RESTORE)
        isMoviesFavorite = favoriteStore.isFavorite(FavoriteStore.TOOL_MOVIES)
        isMusicFavorite = favoriteStore.isFavorite(FavoriteStore.TOOL_MUSIC)
        isReaderFavorite = favoriteStore.isFavorite(FavoriteStore.TOOL_READER)
        latestIntent = intent
        requestNotificationPermissionIfNeeded()

        setContent {
            WodeTheme {
                val appListViewModel: AppListViewModel = viewModel()
                val backupViewModel: BackupViewModel = viewModel()
                val musicViewModel: MusicViewModel = viewModel()
                val readerViewModel: ReaderViewModel = viewModel()
                backupViewModelRef = backupViewModel
                musicViewModelRef = musicViewModel
                readerViewModelRef = readerViewModel

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

                LaunchedEffect(Unit) {
                    readerViewModel.events.collect { event ->
                        when (event) {
                            is ReaderEvent.Toast ->
                                Toast.makeText(this@MainActivity, event.message, Toast.LENGTH_SHORT).show()
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
                                    isMoviesFavorite = isMoviesFavorite,
                                    isMusicFavorite = isMusicFavorite,
                                    isReaderFavorite = isReaderFavorite,
                                    onOpenBackupRestore = {
                                        appListViewModel.loadApps()
                                        backupViewModel.loadBackupRecords()
                                        currentScreen = Screen.BackupRestoreHome
                                    },
                                    onOpenMovies = {
                                        currentScreen = Screen.MovieWeb
                                    },
                                    onOpenMusic = {
                                        musicViewModel.ensureLibraryLoaded(hasAudioPermission())
                                        currentScreen = Screen.Music
                                    },
                                    onOpenReader = {
                                        currentScreen = Screen.Reader
                                    },
                                    onSetBackupRestoreFavorite = ::updateBackupRestoreFavorite,
                                    onSetMoviesFavorite = ::updateMoviesFavorite,
                                    onSetMusicFavorite = ::updateMusicFavorite,
                                    onSetReaderFavorite = ::updateReaderFavorite,
                                )
                            },
                            favoritesContent = {
                                FavoritesScreen(
                                    isBackupRestoreFavorite = isBackupRestoreFavorite,
                                    isMoviesFavorite = isMoviesFavorite,
                                    isMusicFavorite = isMusicFavorite,
                                    isReaderFavorite = isReaderFavorite,
                                    backupCount = backupRecords.size,
                                    isBaiduAuthorized = isAuthorized,
                                    onOpenBackupRestore = {
                                        appListViewModel.loadApps()
                                        backupViewModel.loadBackupRecords()
                                        currentScreen = Screen.BackupRestoreHome
                                    },
                                    onOpenMovies = {
                                        currentScreen = Screen.MovieWeb
                                    },
                                    onOpenMusic = {
                                        musicViewModel.ensureLibraryLoaded(hasAudioPermission())
                                        currentScreen = Screen.Music
                                    },
                                    onOpenReader = {
                                        currentScreen = Screen.Reader
                                    },
                                    onSetBackupRestoreFavorite = ::updateBackupRestoreFavorite,
                                    onSetMoviesFavorite = ::updateMoviesFavorite,
                                    onSetMusicFavorite = ::updateMusicFavorite,
                                    onSetReaderFavorite = ::updateReaderFavorite,
                                )
                            },
                            mineContent = { MineScreen(onCheckUpdate = ::checkUpdateManually) },
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

                    Screen.MovieWeb -> {
                        MovieWebScreen(
                            movieSourceStore = movieSourceStore,
                            onBack = { currentScreen = Screen.Main(MainTab.Tools) },
                            onOpenExternal = ::openMovieLink,
                        )
                    }

                    Screen.Music -> {
                        MusicScreen(
                            viewModel = musicViewModel,
                            onBack = { currentScreen = Screen.Main(MainTab.Tools) },
                            onRefresh = { musicViewModel.loadLibrary(hasAudioPermission()) },
                            onRequestPermission = ::enableSystemMusicDirectly,
                            onAddFolder = { chooseMusicFolderLauncher.launch(null) },
                        )
                    }

                    Screen.Reader -> {
                        ReaderScreen(
                            viewModel = readerViewModel,
                            onBack = { currentScreen = Screen.Main(MainTab.Tools) },
                            onAddFile = { chooseReaderFileLauncher.launch(arrayOf("*/*")) },
                            onAddFolder = { chooseReaderFolderLauncher.launch(null) },
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

    override fun onResume() {
        super.onResume()
        isActivityResumed = true
        showPendingUpdateWhenReady()
    }

    override fun onPause() {
        isActivityResumed = false
        super.onPause()
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
            Screen.MovieWeb -> currentScreen = Screen.Main(MainTab.Tools)
            Screen.Music -> currentScreen = Screen.Main(MainTab.Tools)
            Screen.Reader -> currentScreen = Screen.Main(MainTab.Tools)
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

    private fun openMovieLink(link: String) {
        val uri = normalizeMovieUrl(link)
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        }.onFailure {
            Toast.makeText(this, "无法打开链接：${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun installApk(file: java.io.File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        installApk(uri)
    }

    private fun checkUpdateOnLaunch() {
        if (hasCheckedUpdateOnLaunch) return
        hasCheckedUpdateOnLaunch = true
        updateCheckJob = lifecycleScope.launch {
            updateService.clearSkippedUpdate()
            updateService.checkForUpdate(ignoreSkipped = true)
                .onSuccess { update ->
                    if (update != null) showUpdateIfAllowed(update)
                }
        }
    }

    private fun showUpdateIfAllowed(update: UpdateInfo, force: Boolean = false) {
        if (!force && dismissedUpdateTagThisSession == update.tagName) return
        if (isDownloadingUpdate) return
        pendingUpdate = update
        showPendingUpdateWhenReady()
    }

    private fun showPendingUpdateWhenReady() {
        pendingUpdate ?: return
        if (!isActivityResumed || isFinishing || isDestroyed) return
        if (isDownloadingUpdate) return
        if (updateDialog?.isShowing == true) return
        window.decorView.post {
            val latest = pendingUpdate ?: return@post
            if (isActivityResumed && !isFinishing && !isDestroyed && updateDialog?.isShowing != true) {
                showNativeUpdateDialog(latest)
            }
        }
    }

    private fun showNativeUpdateDialog(update: UpdateInfo) {
        if (!isActivityResumed || isFinishing || isDestroyed) return
        updateDialog?.let { oldDialog ->
            updateDialog = null
            oldDialog.dismiss()
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 20, 48, 0)
        }
        content.addView(TextView(this).apply {
            text = buildString {
                append(update.releaseName)
                if (update.body.isNotBlank()) {
                    append("\n\n")
                    append(update.body.take(300))
                }
            }
        })
        updateProgressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            visibility = View.GONE
        }
        updateProgressText = TextView(this).apply {
            text = "下载中 0%"
            visibility = View.GONE
        }
        content.addView(updateProgressBar)
        content.addView(updateProgressText)

        val dialog = AlertDialog.Builder(this)
            .setTitle("发现新版本 ${update.versionName}")
            .setView(content)
            .setPositiveButton("更新", null)
            .setNegativeButton("取消", null)
            .setNeutralButton("跳过这次更新", null)
            .create()
        updateDialog = dialog
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                startUpdateDownload(update)
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                cancelUpdateDialog()
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                if (!isDownloadingUpdate) {
                    updateService.skipUpdate(update.tagName)
                    dismissedUpdateTagThisSession = update.tagName
                    pendingUpdate = null
                    dialog.dismiss()
                }
            }
        }
        dialog.setOnDismissListener {
            if (updateDialog === dialog) {
                updateDialog = null
                updateProgressBar = null
                updateProgressText = null
            }
            schedulePendingUpdateDialogReshow()
        }
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialog.show()
        verifyUpdateDialogStillShowing(dialog)
    }

    private fun schedulePendingUpdateDialogReshow() {
        if (pendingUpdate == null || isDownloadingUpdate || !isActivityResumed || isFinishing || isDestroyed) return
        window.decorView.postDelayed({
            showPendingUpdateWhenReady()
        }, 350L)
    }

    private fun verifyUpdateDialogStillShowing(dialog: AlertDialog) {
        window.decorView.postDelayed({
            if (pendingUpdate == null || isDownloadingUpdate || !isActivityResumed || isFinishing || isDestroyed) return@postDelayed
            if (updateDialog === dialog && dialog.isShowing) return@postDelayed
            if (updateDialog === dialog) {
                updateDialog = null
                updateProgressBar = null
                updateProgressText = null
            }
            schedulePendingUpdateDialogReshow()
        }, 500L)
    }

    private fun startUpdateDownload(update: UpdateInfo) {
        if (isDownloadingUpdate) return
        isDownloadingUpdate = true
        updateDownloadProgress = 0f
        updateDialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = false
        updateDialog?.getButton(AlertDialog.BUTTON_NEUTRAL)?.isEnabled = false
        updateDialog?.getButton(AlertDialog.BUTTON_NEGATIVE)?.text = "取消下载"
        updateProgressBar?.visibility = View.VISIBLE
        updateProgressText?.visibility = View.VISIBLE
        Toast.makeText(this, "正在下载更新...", Toast.LENGTH_SHORT).show()
        updateDownloadJob = lifecycleScope.launch {
            try {
                updateService.downloadApk(update) { progress ->
                    updateDownloadProgress = progress
                    runOnUiThread { updateNativeDownloadProgress(progress) }
                }
                    .onSuccess { file ->
                        pendingUpdate = null
                        updateDialog?.dismiss()
                        installApk(file)
                    }
                    .onFailure {
                        Toast.makeText(this@MainActivity, "下载更新失败：${it.message}", Toast.LENGTH_LONG).show()
                    }
            } catch (_: CancellationException) {
                Toast.makeText(this@MainActivity, "已取消下载", Toast.LENGTH_SHORT).show()
            } finally {
                isDownloadingUpdate = false
                updateDownloadProgress = 0f
                updateDownloadJob = null
                runOnUiThread { resetNativeUpdateDialogButtons() }
            }
        }
    }

    private fun updateNativeDownloadProgress(progress: Float) {
        val percent = (progress.coerceIn(0f, 1f) * 100).toInt()
        updateProgressBar?.progress = percent
        updateProgressText?.text = "下载中 $percent%"
    }

    private fun resetNativeUpdateDialogButtons() {
        updateDialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = true
        updateDialog?.getButton(AlertDialog.BUTTON_NEUTRAL)?.isEnabled = true
        updateDialog?.getButton(AlertDialog.BUTTON_NEGATIVE)?.text = "取消"
        updateProgressBar?.visibility = View.GONE
        updateProgressText?.visibility = View.GONE
    }

    private fun cancelUpdateDialog() {
        if (isDownloadingUpdate) {
            updateDownloadJob?.cancel()
        } else {
            pendingUpdate?.let { dismissedUpdateTagThisSession = it.tagName }
            pendingUpdate = null
            updateDownloadProgress = 0f
            updateDownloadJob = null
            updateDialog?.dismiss()
        }
    }

    override fun onDestroy() {
        updateCheckJob?.cancel()
        updateDownloadJob?.cancel()
        updateDialog?.dismiss()
        super.onDestroy()
    }

    private fun checkUpdateManually() {
        Toast.makeText(this, "正在检查更新...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            updateService.checkForUpdate(ignoreSkipped = true)
                .onSuccess { update ->
                    if (update == null) {
                        Toast.makeText(this@MainActivity, "已是最新版本", Toast.LENGTH_SHORT).show()
                    } else {
                        updateService.clearSkippedUpdate()
                        dismissedUpdateTagThisSession = null
                        showUpdateIfAllowed(update, force = true)
                    }
                }
                .onFailure {
                    Toast.makeText(this@MainActivity, "检查更新失败：${it.message}", Toast.LENGTH_LONG).show()
                }
        }
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

    private fun hasAudioPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun enableSystemMusicDirectly() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (hasAudioPermission()) {
            musicViewModelRef?.enableSystemLibrary(true)
        } else {
            pendingEnableSystemLibraryAfterPermission = true
            audioPermissionLauncher.launch(permission)
        }
    }

    private fun updateBackupRestoreFavorite(favorite: Boolean) {
        favoriteStore.setFavorite(FavoriteStore.TOOL_BACKUP_RESTORE, favorite)
        isBackupRestoreFavorite = favorite
        Toast.makeText(this, if (favorite) "已收藏" else "已取消收藏", Toast.LENGTH_SHORT).show()
    }

    private fun updateMoviesFavorite(favorite: Boolean) {
        favoriteStore.setFavorite(FavoriteStore.TOOL_MOVIES, favorite)
        isMoviesFavorite = favorite
        Toast.makeText(this, if (favorite) "已收藏" else "已取消收藏", Toast.LENGTH_SHORT).show()
    }

    private fun updateMusicFavorite(favorite: Boolean) {
        favoriteStore.setFavorite(FavoriteStore.TOOL_MUSIC, favorite)
        isMusicFavorite = favorite
        Toast.makeText(this, if (favorite) "已收藏" else "已取消收藏", Toast.LENGTH_SHORT).show()
    }

    private fun updateReaderFavorite(favorite: Boolean) {
        favoriteStore.setFavorite(FavoriteStore.TOOL_READER, favorite)
        isReaderFavorite = favorite
        Toast.makeText(this, if (favorite) "已收藏" else "已取消收藏", Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val REQUEST_POST_NOTIFICATIONS = 2001
    }
}
