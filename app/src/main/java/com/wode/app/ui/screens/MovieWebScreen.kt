package com.wode.app.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.wode.app.data.MovieSource
import com.wode.app.service.MovieSourceStore

class MovieWebNavigationState(
    private val canGoBack: Boolean,
    private val goBack: () -> Unit,
) {
    fun consumeBack(): Boolean {
        if (!canGoBack) return false
        goBack()
        return true
    }
}

private class MovieWebViewHolder {
    var webView: WebView? = null
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MovieWebScreen(
    movieSourceStore: MovieSourceStore,
    onBack: () -> Unit,
    onOpenExternal: (String) -> Unit,
) {
    val webViewHolder = remember { MovieWebViewHolder() }
    val context = LocalContext.current
    val activity = context as? Activity
    val currentOpenExternal by rememberUpdatedState(onOpenExternal)
    val movieSources by movieSourceStore.sources.collectAsState()
    val currentSource by movieSourceStore.currentSource.collectAsState()
    var currentUrl by remember { mutableStateOf(currentSource.url) }
    var isLoading by remember { mutableStateOf(true) }
    var progress by remember { mutableFloatStateOf(0f) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var showSourceDialog by remember { mutableStateOf(false) }
    var fullScreenView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    val navigationState = remember(canGoBack, webViewHolder) {
        MovieWebNavigationState(
            canGoBack = canGoBack,
            goBack = { webViewHolder.webView?.goBack() },
        )
    }

    BackHandler {
        if (fullScreenView != null) {
            customViewCallback?.onCustomViewHidden()
        } else if (!navigationState.consumeBack()) {
            onBack()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            customViewCallback?.onCustomViewHidden()
            webViewHolder.webView?.stopLoading()
            webViewHolder.webView?.destroy()
            webViewHolder.webView = null
        }
    }

    DisposableEffect(fullScreenView) {
        val window = activity?.window
        if (fullScreenView != null) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            window?.decorView?.systemUiVisibility =
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            window?.decorView?.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
        onDispose {
            if (fullScreenView != null) {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                window?.decorView?.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            }
        }
    }

    LaunchedEffect(currentSource.url) {
        errorMessage = null
        currentUrl = currentSource.url
        webViewHolder.webView?.loadUrl(currentSource.url)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("影视") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            errorMessage = null
                            currentUrl = currentSource.url
                            webViewHolder.webView?.loadUrl(currentSource.url)
                        },
                    ) {
                        Icon(Icons.Default.Home, contentDescription = "主页")
                    }
                    IconButton(onClick = { showSourceDialog = true }) {
                        Icon(Icons.Default.Link, contentDescription = "来源")
                    }
                    IconButton(
                        onClick = {
                            errorMessage = null
                            webViewHolder.webView?.reload()
                        },
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                    IconButton(onClick = { onOpenExternal(currentUrl) }) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = "用浏览器打开")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        webViewHolder.webView = this
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadsImagesAutomatically = true
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        webChromeClient = object : WebChromeClient() {
                            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                                if (view == null) return
                                if (fullScreenView != null) {
                                    callback?.onCustomViewHidden()
                                    return
                                }
                                fullScreenView = view
                                customViewCallback = callback
                            }

                            override fun onHideCustomView() {
                                fullScreenView = null
                                customViewCallback = null
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                isLoading = true
                                progress = 0.25f
                                errorMessage = null
                                currentUrl = url ?: currentSource.url
                                canGoBack = view.canGoBack()
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                isLoading = false
                                progress = 1f
                                currentUrl = url ?: currentUrl
                                canGoBack = view.canGoBack()
                            }

                            override fun onReceivedError(
                                view: WebView,
                                request: WebResourceRequest,
                                error: WebResourceError,
                            ) {
                                if (request.isForMainFrame) {
                                    isLoading = false
                                    errorMessage = error.description?.toString() ?: "页面加载失败"
                                }
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest,
                            ): Boolean {
                                val uri = request.url ?: return false
                                return if (uri.scheme == "http" || uri.scheme == "https") {
                                    currentUrl = uri.toString()
                                    false
                                } else {
                                    currentOpenExternal(uri.toString())
                                    true
                                }
                            }
                        }
                        loadUrl(currentSource.url)
                    }
                },
                update = { view ->
                    currentUrl = view.url ?: currentUrl
                    canGoBack = view.canGoBack()
                },
            )

            if (isLoading) {
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(),
                )
            }

            errorMessage?.let { message ->
                MovieWebError(
                    message = message,
                    onRetry = {
                        errorMessage = null
                        webViewHolder.webView?.reload()
                    },
                    onOpenExternal = { onOpenExternal(currentUrl) },
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            fullScreenView?.let { view ->
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(androidx.compose.ui.graphics.Color.Black),
                    factory = {
                        (view.parent as? ViewGroup)?.removeView(view)
                        view
                    },
                )
            }
        }
    }

    if (showSourceDialog) {
        MovieSourceDialog(
            sources = movieSources,
            currentSource = currentSource,
            onSelectSource = movieSourceStore::selectSource,
            onAddSource = { name, url -> movieSourceStore.addSource(name, url) },
            onDeleteSource = movieSourceStore::deleteSource,
            onRestoreDefaults = movieSourceStore::restoreDefaults,
            onDismiss = { showSourceDialog = false },
        )
    }
}

@Composable
private fun MovieWebError(
    message: String,
    onRetry: () -> Unit,
    onOpenExternal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("页面加载失败", style = MaterialTheme.typography.titleMedium)
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onRetry) {
                Text("重试")
            }
            Button(onClick = onOpenExternal) {
                Text("用浏览器打开")
            }
        }
    }
}

@Composable
private fun MovieSourceDialog(
    sources: List<MovieSource>,
    currentSource: MovieSource,
    onSelectSource: (String) -> Unit,
    onAddSource: (String, String) -> Unit,
    onDeleteSource: (String) -> Unit,
    onRestoreDefaults: () -> Unit,
    onDismiss: () -> Unit,
) {
    var sourceName by remember { mutableStateOf("") }
    var sourceUrl by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("影视来源") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                sources.forEach { source ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectSource(source.id) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = source.id == currentSource.id,
                            onClick = { onSelectSource(source.id) },
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(source.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = source.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (source.id != MovieSourceStore.DEFAULT_SOURCE_ID) {
                            IconButton(onClick = { onDeleteSource(source.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "删除")
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = sourceName,
                    onValueChange = { sourceName = it },
                    label = { Text("名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = sourceUrl,
                    onValueChange = { sourceUrl = it },
                    label = { Text("网址") },
                    placeholder = { Text(MovieSourceStore.DEFAULT_SOURCE_URL) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onAddSource(sourceName, sourceUrl)
                    sourceName = ""
                    sourceUrl = ""
                },
                enabled = sourceUrl.isNotBlank(),
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onRestoreDefaults) {
                Text("恢复默认")
            }
            TextButton(onClick = onDismiss) {
                Text("完成")
            }
        },
    )
}

fun normalizeMovieUrl(url: String): Uri {
    return Uri.parse(MovieSourceStore.normalizeUrl(url))
}
