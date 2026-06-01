package com.wode.app.service

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.wode.app.data.BaiduToken
import com.wode.app.data.BackupRecord
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

class BaiduPanService(private val context: Context) {

    companion object {
        private const val BASE_URL = "https://pan.baidu.com/rest/2.0"
        private const val OAUTH_AUTHORIZE = "https://openapi.baidu.com/oauth/2.0/authorize"
        private const val OAUTH_TOKEN = "https://openapi.baidu.com/oauth/2.0/token"
        private const val BLOCK_SIZE = 4 * 1024 * 1024L

        const val DEFAULT_BACKUP_PATH = TokenStore.DEFAULT_BACKUP_PATH
    }

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

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
        appKey: String,
        secretKey: String,
        authCode: String,
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
                Result.failure(Exception("OAuth 失败：${json.error} - ${json.errorDescription}"))
            } else {
                Result.success(
                    BaiduToken(
                        accessToken = json.accessToken!!,
                        refreshToken = json.refreshToken!!,
                        expiresIn = json.expiresIn!!,
                        obtainTime = System.currentTimeMillis(),
                    ),
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshToken(
        appKey: String,
        secretKey: String,
        refreshToken: String,
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
                Result.failure(Exception("Token 刷新失败：${json.error}"))
            } else {
                Result.success(
                    BaiduToken(
                        accessToken = json.accessToken!!,
                        refreshToken = json.refreshToken!!,
                        expiresIn = json.expiresIn!!,
                        obtainTime = System.currentTimeMillis(),
                    ),
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadFile(
        token: String,
        file: File,
        remotePath: String,
        overwrite: Boolean,
        onProgress: ((Long, Long) -> Unit)? = null,
    ): Result<BackupRecord> = withContext(Dispatchers.IO) {
        try {
            val fileSize = file.length()
            val fileMd5 = computeFileMd5(file)
            val blockCount = ((fileSize + BLOCK_SIZE - 1) / BLOCK_SIZE).toInt()
            val blockMd5s = computeBlockMd5s(file, blockCount)

            val uploadId = preCreate(token, remotePath, fileSize, blockMd5s, overwrite)
                .getOrElse { return@withContext Result.failure(it) }

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

            val createdFile = createFile(token, remotePath, fileSize, uploadId, blockMd5s, overwrite)

            Result.success(
                BackupRecord(
                    packageName = "",
                    appName = "",
                    versionName = "",
                    fsId = createdFile.fsId ?: 0,
                    path = createdFile.path ?: remotePath,
                    size = fileSize,
                    uploadTime = System.currentTimeMillis(),
                    md5 = fileMd5,
                ),
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun preCreate(
        token: String,
        path: String,
        size: Long,
        blockMd5s: List<String>,
        overwrite: Boolean,
    ): Result<String> {
        val body = FormBody.Builder()
            .add("access_token", token)
            .add("path", path)
            .add("size", size.toString())
            .add("isdir", "0")
            .add("autoinit", "1")
            .add("rtype", if (overwrite) "3" else "0")
            .add("block_list", gson.toJson(blockMd5s))
            .build()

        val json = postForJson("$BASE_URL/xpan/file?method=precreate", body, PreCreateResponse::class.java)
        if (json.errno != 0) {
            return Result.failure(Exception("precreate 失败：errno=${json.errno}"))
        }
        val uploadId = json.uploadid
            ?: return Result.failure(Exception("precreate 失败：没有 uploadid"))
        return Result.success(uploadId)
    }

    private fun uploadBlockToPcs(
        token: String,
        path: String,
        uploadId: String,
        partSeq: Int,
        data: ByteArray,
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

        val response = client.newCall(Request.Builder().url(url).post(requestBody).build()).execute()
        val body = response.body?.string() ?: "{}"
        if (!response.isSuccessful) {
            throw Exception("分片 $partSeq 上传失败：HTTP ${response.code}, body=$body")
        }

        val pcsResp = gson.fromJson(body, PcsUploadResponse::class.java)
        if (pcsResp.errorCode != null && pcsResp.errorCode != 0) {
            throw Exception("分片 $partSeq 上传失败：errno=${pcsResp.errorCode}, msg=${pcsResp.errorMsg}")
        }
        if (pcsResp.md5 != null) {
            val localMd5 = MessageDigest.getInstance("MD5")
                .digest(data)
                .joinToString("") { "%02x".format(it) }
            if (!pcsResp.md5.equals(localMd5, ignoreCase = true)) {
                throw Exception("分片 $partSeq MD5 不匹配：本地=$localMd5, 服务端=${pcsResp.md5}")
            }
        }
    }

    private fun createFile(
        token: String,
        path: String,
        size: Long,
        uploadId: String,
        blockMd5s: List<String>,
        overwrite: Boolean,
    ): CreateResponse {
        val body = FormBody.Builder()
            .add("access_token", token)
            .add("path", path)
            .add("size", size.toString())
            .add("isdir", "0")
            .add("uploadid", uploadId)
            .add("block_list", gson.toJson(blockMd5s))
            .add("rtype", if (overwrite) "3" else "0")
            .build()

        val json = postForJson("$BASE_URL/xpan/file?method=create", body, CreateResponse::class.java)
        if (json.errno != 0) {
            throw Exception("create 失败：errno=${json.errno}")
        }
        return json
    }

    suspend fun listBackupFiles(token: String, backupPath: String): Result<List<RemoteFileInfo>> =
        withContext(Dispatchers.IO) {
            try {
                ensureDir(token, backupPath)
                val url = HttpUrl.Builder()
                    .scheme("https")
                    .host("pan.baidu.com")
                    .addPathSegment("rest")
                    .addPathSegment("2.0")
                    .addPathSegment("xpan")
                    .addPathSegment("file")
                    .addQueryParameter("method", "list")
                    .addQueryParameter("access_token", token)
                    .addQueryParameter("dir", backupPath)
                    .addQueryParameter("limit", "1000")
                    .build()
                val json = getForJson(url.toString(), ListResponse::class.java)
                if (json.errno != 0) {
                    return@withContext Result.failure(Exception("获取文件列表失败：errno=${json.errno}"))
                }
                Result.success(json.list ?: emptyList())
            } catch (e: Exception) {
                Result.failure(e)
            }
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
                    .addPathSegment("multimedia")
                    .addQueryParameter("method", "filemetas")
                    .addQueryParameter("access_token", token)
                    .addQueryParameter("fsids", "[$fsId]")
                    .addQueryParameter("thumb", "0")
                    .addQueryParameter("dlink", "1")
                    .addQueryParameter("extra", "0")
                    .build()
                val json = getForJson(url.toString(), FileMetaResponse::class.java)
                if (json.errno != 0) {
                    val message = json.errmsg ?: "errno=${json.errno}"
                    return@withContext Result.failure(Exception("filemetas 失败：$message"))
                }
                if (json.list.isNullOrEmpty()) {
                    return@withContext Result.failure(Exception("filemetas 未返回文件信息，请刷新列表后重试"))
                }
                val meta = json.list[0]
                val dlink = meta.dlink
                if (dlink.isNullOrBlank()) {
                    return@withContext Result.failure(Exception("下载链接为空：${meta.filename ?: meta.serverFilename ?: fsId}"))
                }
                Result.success(dlink)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun downloadFile(
        token: String,
        dlink: String,
        target: RestoreTarget,
        onProgress: ((Long, Long) -> Unit)? = null,
    ): Result<RestoreTarget> =
        withContext(Dispatchers.IO) {
            try {
                val downloadUrl = dlink.toHttpUrlOrNull()?.newBuilder()
                    ?.addQueryParameter("access_token", token)
                    ?.build()
                    ?: return@withContext Result.failure(Exception("下载链接格式无效"))
                val request = Request.Builder()
                    .url(downloadUrl)
                    .header("User-Agent", "pan.baidu.com")
                    .header("Referer", "https://pan.baidu.com/")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("下载失败：HTTP ${response.code}"))
                }

                val totalBytes = response.body?.contentLength()?.takeIf { it > 0 } ?: 0L
                response.body?.byteStream()?.use { input ->
                    target.output.use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var downloadedBytes = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            onProgress?.invoke(downloadedBytes, totalBytes)
                        }
                    }
                } ?: return@withContext Result.failure(Exception("下载失败：响应为空"))
                Result.success(target)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun ensureDir(token: String, dirPath: String) {
        val body = FormBody.Builder()
            .add("method", "create")
            .add("access_token", token)
            .add("path", dirPath)
            .add("isdir", "1")
            .add("size", "0")
            .add("rtype", "0")
            .build()
        runCatching {
            client.newCall(Request.Builder().url("$BASE_URL/xpan/file").post(body).build()).execute().close()
        }
    }

    private fun computeFileMd5(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
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
                raf.seek(offset)
                raf.readFully(block)
                digest.update(block)
                md5s.add(digest.digest().joinToString("") { "%02x".format(it) })
            }
        }
        return md5s
    }

    private inline fun <reified T> postForJson(url: String, body: RequestBody, clazz: Class<T>): T {
        val request = Request.Builder().url(url).post(body).build()
        val response = client.newCall(request).execute()
        return response.use {
            gson.fromJson(it.body?.string(), clazz)
        }
    }

    private inline fun <reified T> getForJson(url: String, clazz: Class<T>): T {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "pan.baidu.com")
            .get()
            .build()
        val response = client.newCall(request).execute()
        return response.use {
            gson.fromJson(it.body?.string(), clazz)
        }
    }

    data class OAuthResponse(
        val error: String?,
        @SerializedName("error_description") val errorDescription: String?,
        @SerializedName("access_token") val accessToken: String?,
        @SerializedName("refresh_token") val refreshToken: String?,
        @SerializedName("expires_in") val expiresIn: Long?,
    )

    data class CreateResponse(
        val errno: Int,
        @SerializedName("fs_id") val fsId: Long?,
        val path: String?,
    )

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
        val errmsg: String?,
        val list: List<FileMeta>?,
    )

    data class FileMeta(
        @SerializedName("fs_id") val fsId: Long,
        @SerializedName("server_filename") val serverFilename: String?,
        val filename: String?,
        val dlink: String?,
    )

    data class PcsUploadResponse(
        val md5: String?,
        @SerializedName("error_code") val errorCode: Int?,
        @SerializedName("error_msg") val errorMsg: String?,
    )
}
