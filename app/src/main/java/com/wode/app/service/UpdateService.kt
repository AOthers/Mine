package com.wode.app.service

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.wode.app.BuildConfig
import com.wode.app.data.UpdateInfo
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class UpdateService(private val context: Context) {
    private val prefs = context.getSharedPreferences("wode_update", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun checkForUpdate(ignoreSkipped: Boolean = false): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(LATEST_RELEASE_API)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Wode-App/${BuildConfig.VERSION_NAME}")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    if (response.code == 403) {
                        return@withContext checkForUpdateFromReleasePage(ignoreSkipped)
                    }
                    return@withContext Result.failure(Exception("检查更新失败：HTTP ${response.code}"))
                }

                val release = gson.fromJson(response.body?.string().orEmpty(), GitHubRelease::class.java)
                val asset = release.assets.firstOrNull { asset ->
                    asset.name.endsWith(".apk", ignoreCase = true)
                } ?: return@withContext checkForUpdateFromReleasePage(ignoreSkipped)

                Result.success(
                    release.toUpdateInfoOrNull(
                        ignoreSkipped = ignoreSkipped,
                        apkName = asset.name,
                        apkDownloadUrl = asset.browserDownloadUrl,
                    ),
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun checkForUpdateFromReleasePage(ignoreSkipped: Boolean): Result<UpdateInfo?> {
        val request = Request.Builder()
            .url(LATEST_RELEASE_PAGE)
            .header("User-Agent", "Wode-App/${BuildConfig.VERSION_NAME}")
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return@use Result.failure(Exception("检查更新失败：HTTP ${response.code}"))
            }
            val tagName = response.latestReleaseTag()
                ?: return@use Result.failure(Exception("检查更新失败：无法识别最新版本"))
            val latestVersion = tagName.trim().removePrefix("v").removePrefix("V")
            if (!isNewerVersion(latestVersion, BuildConfig.VERSION_NAME)) {
                return@use Result.success(null)
            }
            if (!ignoreSkipped && prefs.getString(KEY_SKIPPED_TAG, "") == tagName) {
                return@use Result.success(null)
            }

            val apkName = "Mine-$tagName.apk"
            Result.success(
                UpdateInfo(
                    versionName = latestVersion,
                    tagName = tagName,
                    releaseName = tagName,
                    body = "",
                    apkName = apkName,
                    apkDownloadUrl = "$RELEASE_DOWNLOAD_BASE/$tagName/$apkName",
                    htmlUrl = "$RELEASE_TAG_BASE/$tagName",
                ),
            )
        }
    }

    suspend fun downloadApk(updateInfo: UpdateInfo): Result<File> = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, "apk_download").also { it.mkdirs() }
            val file = File(dir, updateInfo.apkName.ifBlank { "wode-${updateInfo.tagName}.apk" })
            val request = Request.Builder()
                .url(updateInfo.apkDownloadUrl)
                .header("User-Agent", "Wode-App/${BuildConfig.VERSION_NAME}")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("下载更新失败：HTTP ${response.code}"))
                }
                val body = response.body ?: return@withContext Result.failure(Exception("下载更新失败：响应为空"))
                file.outputStream().use { output ->
                    body.byteStream().use { input -> input.copyTo(output) }
                }
            }
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun skipUpdate(tagName: String) {
        prefs.edit().putString(KEY_SKIPPED_TAG, tagName).apply()
    }

    private fun GitHubRelease.toUpdateInfoOrNull(
        ignoreSkipped: Boolean,
        apkName: String,
        apkDownloadUrl: String,
    ): UpdateInfo? {
        val latestVersion = tagName.trim().removePrefix("v").removePrefix("V")
        if (!isNewerVersion(latestVersion, BuildConfig.VERSION_NAME)) {
            return null
        }
        if (!ignoreSkipped && prefs.getString(KEY_SKIPPED_TAG, "") == tagName) {
            return null
        }
        return UpdateInfo(
            versionName = latestVersion,
            tagName = tagName,
            releaseName = name.ifBlank { tagName },
            body = body,
            apkName = apkName,
            apkDownloadUrl = apkDownloadUrl,
            htmlUrl = htmlUrl,
        )
    }

    private fun Response.latestReleaseTag(): String? {
        val urlSegments = request.url.pathSegments
        val tagIndex = urlSegments.indexOf("tag")
        if (tagIndex >= 0 && tagIndex + 1 < urlSegments.size) {
            return urlSegments[tagIndex + 1].takeIf { it.isNotBlank() }
        }
        return body?.string()
            ?.let { html -> TAG_LINK_REGEX.find(html)?.groupValues?.getOrNull(1) }
            ?.takeIf { it.isNotBlank() }
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = latest.split(".", "-", "_").map { it.toIntOrNull() ?: 0 }
        val currentParts = current.split(".", "-", "_").map { it.toIntOrNull() ?: 0 }
        val maxSize = maxOf(latestParts.size, currentParts.size)
        for (index in 0 until maxSize) {
            val latestPart = latestParts.getOrElse(index) { 0 }
            val currentPart = currentParts.getOrElse(index) { 0 }
            if (latestPart != currentPart) return latestPart > currentPart
        }
        return false
    }

    private data class GitHubRelease(
        @SerializedName("tag_name") val tagName: String = "",
        val name: String = "",
        val body: String = "",
        @SerializedName("html_url") val htmlUrl: String = "",
        val assets: List<GitHubAsset> = emptyList(),
    )

    private data class GitHubAsset(
        val name: String = "",
        @SerializedName("browser_download_url") val browserDownloadUrl: String = "",
    )

    companion object {
        private const val LATEST_RELEASE_API = "https://api.github.com/repos/AOthers/Mine/releases/latest"
        private const val LATEST_RELEASE_PAGE = "https://github.com/AOthers/Mine/releases/latest"
        private const val RELEASE_DOWNLOAD_BASE = "https://github.com/AOthers/Mine/releases/download"
        private const val RELEASE_TAG_BASE = "https://github.com/AOthers/Mine/releases/tag"
        private const val KEY_SKIPPED_TAG = "skipped_tag"
        private val TAG_LINK_REGEX = Regex("""/AOthers/Mine/releases/tag/([^"'<>]+)""")
    }
}
