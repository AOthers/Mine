package com.wode.app.service

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.wode.app.BuildConfig
import com.wode.app.data.UpdateInfo
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class UpdateService(private val context: Context) {
    private val prefs = context.getSharedPreferences("wode_update", Context.MODE_PRIVATE)
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

                val releaseJson = response.body?.string().orEmpty()
                val release = parseGitHubRelease(releaseJson)
                    ?: parseGitHubReleaseWithoutAsset(releaseJson)?.let { releaseWithoutAsset ->
                        resolveReleaseAsset(releaseWithoutAsset)?.let { asset ->
                            releaseWithoutAsset.withAsset(asset)
                        }
                    }
                    ?: return@withContext checkForUpdateFromReleasePage(ignoreSkipped)

                Result.success(release.toUpdateInfoOrNull(ignoreSkipped))
            }
        } catch (e: CancellationException) {
            throw e
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
            val page = response.latestReleasePage()
            val tagName = page.tagName
                ?: return@use Result.failure(Exception("检查更新失败：无法识别最新版本"))
            val latestVersion = tagName.trim().removePrefix("v").removePrefix("V")
            if (!isNewerVersion(latestVersion, BuildConfig.VERSION_NAME)) {
                return@use Result.success(null)
            }
            if (!ignoreSkipped && prefs.getString(KEY_SKIPPED_TAG, "") == tagName) {
                return@use Result.success(null)
            }

            val apkDownloadUrl = page.apkDownloadUrl ?: findApkDownloadUrlFromExpandedAssets(tagName)
                ?: return@use Result.failure(Exception("检查更新失败：最新 Release 没有 APK 文件"))
            val apkName = apkDownloadUrl.substringAfterLast('/').ifBlank { "Mine-$tagName.apk" }
            Result.success(
                UpdateInfo(
                    versionName = latestVersion,
                    tagName = tagName,
                    releaseName = tagName,
                    body = "",
                    apkName = apkName,
                    apkDownloadUrl = apkDownloadUrl,
                    htmlUrl = "$RELEASE_TAG_BASE/$tagName",
                ),
            )
        }
    }

    suspend fun downloadApk(
        updateInfo: UpdateInfo,
        onProgress: (Float) -> Unit = {},
    ): Result<File> = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "apk_download").also { it.mkdirs() }
        val file = File(dir, updateInfo.apkName.ifBlank { "wode-${updateInfo.tagName}.apk" })
        try {
            val request = Request.Builder()
                .url(updateInfo.apkDownloadUrl)
                .header("User-Agent", "Wode-App/${BuildConfig.VERSION_NAME}")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("下载更新失败：HTTP ${response.code}"))
                }
                val body = response.body ?: return@withContext Result.failure(Exception("下载更新失败：响应为空"))
                val totalBytes = body.contentLength()
                var downloadedBytes = 0L
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                file.outputStream().use { output ->
                    body.byteStream().use { input ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            if (totalBytes > 0) {
                                onProgress((downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
                onProgress(1f)
            }
            Result.success(file)
        } catch (e: CancellationException) {
            file.delete()
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun skipUpdate(tagName: String) {
        prefs.edit().putString(KEY_SKIPPED_TAG, tagName).apply()
    }

    fun clearSkippedUpdate() {
        prefs.edit().remove(KEY_SKIPPED_TAG).apply()
    }

    private fun GitHubReleaseInfo.toUpdateInfoOrNull(ignoreSkipped: Boolean): UpdateInfo? {
        val latestVersion = tagName.trim().removePrefix("v").removePrefix("V")
        if (!isNewerVersion(latestVersion, BuildConfig.VERSION_NAME)) return null
        if (!ignoreSkipped && prefs.getString(KEY_SKIPPED_TAG, "") == tagName) return null
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

    private fun Response.latestReleasePage(): ReleasePageInfo {
        val urlSegments = request.url.pathSegments
        val tagIndex = urlSegments.indexOf("tag")
        val bodyText = body?.string().orEmpty()
        val htmlTag = TAG_LINK_REGEX.find(bodyText)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
        val apkPath = APK_LINK_REGEX.find(bodyText)?.groupValues?.getOrNull(1)
        val apkUrl = apkPath?.toAbsoluteGitHubUrl()
        val redirectTag = if (tagIndex >= 0 && tagIndex + 1 < urlSegments.size) {
            urlSegments[tagIndex + 1].takeIf { it.isNotBlank() }
        } else {
            null
        }
        return ReleasePageInfo(tagName = redirectTag ?: htmlTag, apkDownloadUrl = apkUrl)
    }

    private fun resolveReleaseAsset(release: GitHubReleaseInfo): ApkAssetInfo? {
        if (release.assetsUrl.isBlank()) return null
        val request = Request.Builder()
            .url(release.assetsUrl)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Wode-App/${BuildConfig.VERSION_NAME}")
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                parseGitHubAssets(response.body?.string().orEmpty()).firstOrNull()
            }
        }.getOrNull()
    }

    private fun findApkDownloadUrlFromExpandedAssets(tagName: String): String? {
        val request = Request.Builder()
            .url("$RELEASE_EXPANDED_ASSETS_BASE/$tagName")
            .header("User-Agent", "Wode-App/${BuildConfig.VERSION_NAME}")
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                APK_LINK_REGEX.find(response.body?.string().orEmpty())
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toAbsoluteGitHubUrl()
            }
        }.getOrNull()
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

    internal data class GitHubReleaseInfo(
        val tagName: String,
        val name: String,
        val body: String,
        val htmlUrl: String,
        val apkName: String = "",
        val apkDownloadUrl: String = "",
        val assetsUrl: String = "",
    ) {
        fun withAsset(asset: ApkAssetInfo): GitHubReleaseInfo {
            return copy(apkName = asset.name, apkDownloadUrl = asset.downloadUrl)
        }
    }

    internal data class ApkAssetInfo(
        val name: String,
        val downloadUrl: String,
    )

    private data class ReleasePageInfo(
        val tagName: String?,
        val apkDownloadUrl: String?,
    )

    companion object {
        private const val LATEST_RELEASE_API = "https://api.github.com/repos/AOthers/Mine/releases/latest"
        private const val LATEST_RELEASE_PAGE = "https://github.com/AOthers/Mine/releases/latest"
        private const val RELEASE_DOWNLOAD_BASE = "https://github.com/AOthers/Mine/releases/download"
        private const val RELEASE_TAG_BASE = "https://github.com/AOthers/Mine/releases/tag"
        private const val RELEASE_EXPANDED_ASSETS_BASE = "https://github.com/AOthers/Mine/releases/expanded_assets"
        private const val KEY_SKIPPED_TAG = "skipped_tag"
        private val TAG_LINK_REGEX = Regex("""/AOthers/Mine/releases/tag/([^"'<>]+)""")
        private val APK_LINK_REGEX = Regex("""((?:https://github\.com)?/AOthers/Mine/releases/download/[^"'<>]+/[^"'<>]+\.apk)""")

        internal fun parseGitHubRelease(json: String): GitHubReleaseInfo? {
            val release = parseGitHubReleaseWithoutAsset(json) ?: return null
            val apkAsset = parseGitHubAssetsFromRelease(json).firstOrNull() ?: return null
            return release.withAsset(apkAsset)
        }

        internal fun parseGitHubReleaseWithoutAsset(json: String): GitHubReleaseInfo? {
            val root = runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull() ?: return null
            return GitHubReleaseInfo(
                tagName = root.stringOrEmpty("tag_name"),
                name = root.stringOrEmpty("name"),
                body = root.stringOrEmpty("body"),
                htmlUrl = root.stringOrEmpty("html_url"),
                assetsUrl = root.stringOrEmpty("assets_url"),
            )
        }

        internal fun parseGitHubAssets(json: String): List<ApkAssetInfo> {
            val array = runCatching { JsonParser.parseString(json).asJsonArray }.getOrNull() ?: return emptyList()
            return array
                .asSequence()
                .mapNotNull { it.asJsonObjectOrNull() }
                .mapNotNull(::parseApkAsset)
                .toList()
        }

        private fun parseGitHubAssetsFromRelease(json: String): List<ApkAssetInfo> {
            val root = runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull() ?: return emptyList()
            return root.getAsJsonArrayOrNull("assets")
                ?.asSequence()
                ?.mapNotNull { it.asJsonObjectOrNull() }
                ?.mapNotNull(::parseApkAsset)
                ?.toList()
                .orEmpty()
        }

        private fun parseApkAsset(asset: JsonObject): ApkAssetInfo? {
            val name = asset.stringOrEmpty("name")
            val downloadUrl = asset.stringOrEmpty("browser_download_url")
            return if (name.endsWith(".apk", ignoreCase = true) && downloadUrl.isNotBlank()) {
                ApkAssetInfo(name, downloadUrl)
            } else {
                null
            }
        }

        private fun JsonObject.getAsJsonArrayOrNull(name: String) =
            get(name)?.takeIf { it.isJsonArray }?.asJsonArray

        private fun com.google.gson.JsonElement.asJsonObjectOrNull() =
            takeIf { it.isJsonObject }?.asJsonObject

        private fun JsonObject.stringOrEmpty(name: String): String =
            get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString.orEmpty()

        private fun String.toAbsoluteGitHubUrl(): String {
            return if (startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)) {
                this
            } else {
                "https://github.com$this"
            }
        }
    }
}
