package com.wode.app.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

private const val MOVIE_HOME_URL = "https://www.hhkan0.com/"

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
    onBack: () -> Unit,
    onOpenExternal: (String) -> Unit,
) {
    val webViewHolder = remember { MovieWebViewHolder() }
    val currentOpenExternal by rememberUpdatedState(onOpenExternal)
    var currentUrl by remember { mutableStateOf(MOVIE_HOME_URL) }
    var isLoading by remember { mutableStateOf(true) }
    var progress by remember { mutableFloatStateOf(0f) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var canGoBack by remember { mutableStateOf(false) }

    val navigationState = remember(canGoBack, webViewHolder) {
        MovieWebNavigationState(
            canGoBack = canGoBack,
            goBack = { webViewHolder.webView?.goBack() },
        )
    }

    BackHandler {
        if (!navigationState.consumeBack()) {
            onBack()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewHolder.webView?.stopLoading()
            webViewHolder.webView?.destroy()
            webViewHolder.webView = null
        }
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
                            webViewHolder.webView?.loadUrl(MOVIE_HOME_URL)
                        },
                    ) {
                        Icon(Icons.Default.Home, contentDescription = "主页")
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
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                isLoading = true
                                progress = 0.25f
                                errorMessage = null
                                currentUrl = url ?: MOVIE_HOME_URL
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
                        loadUrl(MOVIE_HOME_URL)
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

        }
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

fun normalizeMovieUrl(url: String): Uri {
    return Uri.parse(url.ifBlank { MOVIE_HOME_URL })
}
