package com.wode.app.service

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.wode.app.data.BaiduToken
import com.wode.app.data.BackupRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * 百度网盘 Open API 服务
 *
 * 上传流程（分片）:
 *   1. POST xpan/file?method=precreate  → 获取 uploadid
 *   2. POST d.pcs.baidu.com/.../superfile2?method=upload&partseq=N  → 逐个上传分片
 *   3. POST xpan/file?method=create  → 完成上传
 *
 * 参考: https://pan.baidu.com/union/doc/
 */
class BaiduPanService(private val context: Context) {

    companion object {
        private const val BASE_URL = "https://pan.baidu.com/rest/2.0"
        private const val OAUTH_AUTHORIZE = "https://openapi.baidu.com/oauth/2.0/authorize"
        private const val OAUTH_TOKEN = "https://openapi.baidu.com/oauth/2.0/token"

        // 分片大小：4MB
        private const val BLOCK_SIZE = 4 * 1024 * 1024L

        // 备份目录
        const val BACKUP_PATH = "/apps/AppBackup"
    }

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    // ==================== OAuth ====================

    fun getOAuthUrl(appKey: String): String {
        return Uri.parse(OAUTH_AUTHORIZE).buildUpon()
            .appendQueryParameter("client_id", appKey.trim())
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", "wode://baidu.oauth")
            .appendQueryParameter("scope", "basic,netdisk")
            .appendQueryParameter("display", "mobile")
            .build()
            .toString()
    }

    fun extractAuthCode(uri: Uri): String? = uri.getQueryParameter("code")

    fun extractAuthError(uri: Uri): String? {
        val error = uri.getQueryParameter("error") ?: return null
        val description = uri.getQueryParameter("error_description")
        return if (description.isNullOrBlank()) error else "$error - $description"
    }

    suspend fun exchangeToken(
        appKey: String, secretKey: String, authCode: String
    ): Result<BaiduToken> = withContext(Dispatchers.IO) {
        try {
            val body = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", authCode)
                .add("client_id", appKey.trim())
                .add("client_secret", secretKey.trim())
                .add("redirect_uri", "wode://baidu.oauth")
                .build()

            val json = postForJson(OAUTH_TOKEN, body, OAuthResponse::class.java)
            if (json.error != null) {
                Result.failure(Exception("OAuth 失败: ${json.error} - ${json.errorDescription}"))
            } else {
                Result.success(BaiduToken(
                    accessToken = json.accessToken!!,
                    refreshToken = json.refreshToken!!,
                    expiresIn = json.expiresIn!!,
                    obtainTime = System.currentTimeMillis(),
                ))
            }
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun refreshToken(
        appKey: String, secretKey: String, refreshToken: String
    ): Result<BaiduToken> = withContext(Dispatchers.IO) {
        try {
            val body = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .add("client_id", appKey.trim())
                .add("client_secret", secretKey.trim())
                .build()

            val json = postForJson(OAUTH_TOKEN, body, OAuthResponse::class.java)
            if (json.error != null) {
                Result.failure(Exception("Token 刷新失败: ${json.error}"))
            } else {
                Result.success(BaiduToken(
                    accessToken = json.accessToken!!,
                    refreshToken = json.refreshToken!!,
                    expiresIn = json.expiresIn!!,
                    obtainTime = System.currentTimeMillis(),
                ))
            }
        } catch (e: Exception) { Result.failure(e) }
    }

    // ==================== 文件上传（分片） ====================

    /**
     * 上传 APK 文件到百度网盘（分片上传）
     */
    suspend fun uploadFile(
        token: String,
        file: File,
        remotePath: String,
        onProgress: ((Long, Long) -> Unit)? = null
    ): Result<BackupRecord> = withContext(Dispatchers.IO) {
        try {
            val fileSize = file.length()
            val fileMd5 = computeFileMd5(file)
            val blockCount = ((fileSize + BLOCK_SIZE - 1) / BLOCK_SIZE).toInt()
            val blockMd5s = computeBlockMd5s(file, blockCount)

            // Step 1: precreate — 获取 uploadid
            val uploadId = preCreate(token, remotePath, fileSize, blockMd5s)
                .getOrElse { return@withContext Result.failure(it) }

            // Step 2: 逐个上传分片到 PCS
            var uploadedBytes = 0L
            RandomAccessFile(file, "r").use { raf ->
                for (i in 0 until blockCount) {
                    val offset = i * BLOCK_SIZE
                    val size = minOf(BLOCK_SIZE, fileSize - offset)
                    val block = ByteArray(size.toInt())
                    raf.seek(offset)
                    raf.readFully(block)

                    uploadBlockToPcs(token, remotePath, uploadId, i, block)

                    uploadedBytes += size
                    onProgress?.invoke(uploadedBytes, fileSize)
                }
            }

            // Step 3: create — 完成上传
            createFile(token, remotePath, fileSize, uploadId, blockMd5s)

            Result.success(BackupRecord(
                packageName = "", appName = "", versionName = "",
                fsId = 0, path = remotePath, size = fileSize,
                uploadTime = System.currentTimeMillis(), md5 = fileMd5,
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Step 1: precreate ---
    private fun preCreate(
        token: String, path: String, size: Long, blockMd5s: List<String>
    ): Result<String> {
        val blockListJson = gson.toJson(blockMd5s)

        val body = FormBody.Builder()
            .add("access_token", token)
            .add("path", path)
            .add("size", size.toString())
            .add("isdir", "0")
            .add("autoinit", "1")
            .add("block_list", blockListJson)
            .build()

        val url = "$BASE_URL/xpan/file?method=precreate"
        val json = postForJson(url, body, PreCreateResponse::class.java)

        if (json.errno != 0) {
            return Result.failure(Exception("precreate 失败: errno=${json.errno}"))
        }
        val uploadId = json.uploadid
            ?: return Result.failure(Exception("precreate 失败: 无 uploadid"))
        return Result.success(uploadId)
    }

    // --- Step 2: 上传单个分片到 PCS ---
    private fun uploadBlockToPcs(
        token: String, path: String, uploadId: String, partSeq: Int, data: ByteArray
    ) {
        val blockBody = data.toRequestBody("application/octet-stream".toMediaType())
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", "block-$partSeq", blockBody)
            .build()

        val url = HttpUrl.Builder()
            .scheme("https")
            .host("d.pcs.baidu.com")
            .addPathSegment("rest")
            .addPathSegment("2.0")
            .addPathSegment("pcs")
            .addPathSegment("superfile2")
            .addQueryParameter("method", "upload")
            .addQueryParameter("access_token", token)
            .addQueryParameter("type", "tmpfile")
            .addQueryParameter("path", path)
            .addQueryParameter("uploadid", uploadId)
            .addQueryParameter("partseq", partSeq.toString())
            .build()

        val request = Request.Builder().url(url).post(requestBody).build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: "{}"

        if (!response.isSuccessful) {
            throw Exception("分片 $partSeq 上传失败: HTTP ${response.code}, body=$body")
        }

        // 解析 PCS 返回，百度可能 HTTP 200 但返回错误码
        val pcsResp = gson.fromJson(body, PcsUploadResponse::class.java)
        if (pcsResp.error_code != null && pcsResp.error_code != 0) {
            throw Exception("分片 $partSeq 上传失败: errno=${pcsResp.error_code}, msg=${pcsResp.error_msg}")
        }
        // 校验返回的 MD5
        if (pcsResp.md5 != null) {
            val localMd5 = MessageDigest.getInstance("MD5")
                .digest(data).joinToString("") { "%02x".format(it) }
            if (!pcsResp.md5.equals(localMd5, ignoreCase = true)) {
                throw Exception("分片 $partSeq MD5 不匹配: 本地=$localMd5, 服务端=${pcsResp.md5}")
            }
        }
    }

    // --- Step 3: create ---
    private fun createFile(
        token: String, path: String, size: Long, uploadId: String, blockMd5s: List<String>
    ) {
        val blockListJson = gson.toJson(blockMd5s)

        val body = FormBody.Builder()
            .add("access_token", token)
            .add("path", path)
            .add("size", size.toString())
            .add("isdir", "0")
            .add("uploadid", uploadId)
            .add("block_list", blockListJson)
            .add("rtype", "3")
            .build()

        val url = "$BASE_URL/xpan/file?method=create"
        val json = postForJson(url, body, BaseResponse::class.java)

        if (json.errno != 0) {
            throw Exception("create 失败: errno=${json.errno}")
        }
    }

    // ==================== 文件列表 / 下载 ====================

    suspend fun listBackupFiles(token: String): Result<List<RemoteFileInfo>> =
        withContext(Dispatchers.IO) {
            try {
                ensureDir(token, BACKUP_PATH)
                val url = HttpUrl.Builder()
                    .scheme("https")
                    .host("pan.baidu.com")
                    .addPathSegment("rest")
                    .addPathSegment("2.0")
                    .addPathSegment("xpan")
                    .addPathSegment("file")
                    .addQueryParameter("method", "list")
                    .addQueryParameter("access_token", token)
                    .addQueryParameter("dir", BACKUP_PATH)
                    .addQueryParameter("limit", "1000")
                    .build()
                val json = getForJson(url.toString(), ListResponse::class.java)
                if (json.errno != 0) {
                    return@withContext Result.failure(Exception("获取文件列表失败: errno=${json.errno}"))
                }
                Result.success(json.list ?: emptyList())
            } catch (e: Exception) { Result.failure(e) }
        }

    suspend fun getDownloadUrl(token: String, fsId: Long): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val url = HttpUrl.Builder()
                    .scheme("https")
                    .host("pan.baidu.com")
                    .addPathSegment("rest")
                    .addPathSegment("2.0")
                    .addPathSegment("xpan")
                    .addPathSegment("file")
                    .addQueryParameter("method", "filemetas")
                    .addQueryParameter("access_token", token)
                    .addQueryParameter("fsids", "[$fsId]")
                    .addQueryParameter("dlink", "1")
                    .build()
                val json = getForJson(url.toString(), FileMetaResponse::class.java)
                if (json.errno != 0 || json.list.isNullOrEmpty()) {
                    return@withContext Result.failure(Exception("获取下载链接失败"))
                }
                val dlink = json.list[0].dlink
                if (dlink.isNullOrBlank()) {
                    return@withContext Result.failure(Exception("下载链接为空"))
                }
                Result.success(dlink)
            } catch (e: Exception) { Result.failure(e) }
        }

    /**
     * 下载文件到本地
     */
    suspend fun downloadFile(token: String, dlink: String, destFile: File): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                // dlink 是百度返回的预签名 URL，安全地拼接 access_token
                val downloadUrl = dlink.toHttpUrlOrNull()?.newBuilder()
                    ?.addQueryParameter("access_token", token)
                    ?.build()
                    ?: return@withContext Result.failure(Exception("下载链接格式无效"))
                val request = Request.Builder()
                    .url(downloadUrl)
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("下载失败: HTTP ${response.code}"))
                }

                destFile.parentFile?.mkdirs()
                response.body?.byteStream()?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Result.success(destFile)
            } catch (e: Exception) { Result.failure(e) }
        }

    // ==================== 辅助方法 ====================

    private fun ensureDir(token: String, dirPath: String) {
        val body = FormBody.Builder()
            .add("method", "create")
            .add("access_token", token)
            .add("path", dirPath)
            .add("isdir", "1")
            .add("size", "0")
            .add("rtype", "0")
            .build()
        try { client.newCall(Request.Builder().url("$BASE_URL/xpan/file").post(body).build()).execute() }
        catch (_: Exception) {}
    }

    private fun computeFileMd5(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        FileInputStream(file).use { fis ->
            val buf = ByteArray(8192); var n: Int
            while (fis.read(buf).also { n = it } != -1) digest.update(buf, 0, n)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun computeBlockMd5s(file: File, blockCount: Int): List<String> {
        val md5s = mutableListOf<String>()
        RandomAccessFile(file, "r").use { raf ->
            val digest = MessageDigest.getInstance("MD5")
            for (i in 0 until blockCount) {
                digest.reset()
                val offset = i * BLOCK_SIZE
                val size = minOf(BLOCK_SIZE, file.length() - offset)
                val block = ByteArray(size.toInt())
                raf.seek(offset); raf.readFully(block)
                digest.update(block)
                md5s.add(digest.digest().joinToString("") { "%02x".format(it) })
            }
        }
        return md5s
    }

    private inline fun <reified T> postForJson(url: String, body: RequestBody, clazz: Class<T>): T {
        val request = Request.Builder().url(url).post(body).build()
        val response = client.newCall(request).execute()
        return gson.fromJson(response.body?.string(), clazz)
    }

    private inline fun <reified T> getForJson(url: String, clazz: Class<T>): T {
        val request = Request.Builder().url(url).get().build()
        val response = client.newCall(request).execute()
        return gson.fromJson(response.body?.string(), clazz)
    }

    // ==================== API 响应数据类 ====================

    data class OAuthResponse(
        val error: String?,
        @SerializedName("error_description") val errorDescription: String?,
        @SerializedName("access_token") val accessToken: String?,
        @SerializedName("refresh_token") val refreshToken: String?,
        @SerializedName("expires_in") val expiresIn: Long?,
    )

    data class BaseResponse(val errno: Int)

    data class PreCreateResponse(
        val errno: Int,
        val uploadid: String?,
    )

    data class ListResponse(
        val errno: Int,
        val list: List<RemoteFileInfo>?,
    )

    data class RemoteFileInfo(
        @SerializedName("fs_id") val fsId: Long,
        val path: String?,
        @SerializedName("server_filename") val serverFilename: String?,
        val size: Long?,
        @SerializedName("server_mtime") val serverMtime: Long?,
        val md5: String?,
        @SerializedName("isdir") val isDir: Int?,
    )

    data class FileMetaResponse(
        val errno: Int,
        val list: List<FileMeta>?,
    )

    data class FileMeta(
        @SerializedName("fs_id") val fsId: Long,
        val dlink: String?,
    )

    /** PCS superfile2 分片上传响应 */
    data class PcsUploadResponse(
        val md5: String?,
        val error_code: Int?,
        val error_msg: String?,
    )
}
