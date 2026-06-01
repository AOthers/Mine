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

class UpdateService(private val context: Context) {
    private val prefs = context.getSharedPreferences("wode_update", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun checkForUpdate(): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(LATEST_RELEASE_API)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Wode-App/${BuildConfig.VERSION_NAME}")
                .build()
            val response = client.newCall(request).execute()
            response.use {
                if (!it.isSuccessful) {
                    return@withContext Result.failure(Exception("检查更新失败：HTTP ${it.code}"))
                }
                val release = gson.fromJson(it.body?.string().orEmpty(), GitHubRelease::class.java)
                val asset = release.assets.firstOrNull { asset ->
                    asset.name.endsWith(".apk", ignoreCase = true)
                } ?: return@withContext Result.success(null)

                val latestVersion = release.tagName.trim().removePrefix("v").removePrefix("V")
                if (!isNewerVersion(latestVersion, BuildConfig.VERSION_NAME)) {
                    return@withContext Result.success(null)
                }
                if (prefs.getString(KEY_SKIPPED_TAG, "") == release.tagName) {
                    return@withContext Result.success(null)
                }

                Result.success(
                    UpdateInfo(
                        versionName = latestVersion,
                        tagName = release.tagName,
                        releaseName = release.name.ifBlank { release.tagName },
                        body = release.body,
                        apkName = asset.name,
                        apkDownloadUrl = asset.browserDownloadUrl,
                        htmlUrl = release.htmlUrl,
                    ),
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
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
        private const val KEY_SKIPPED_TAG = "skipped_tag"
    }
}
