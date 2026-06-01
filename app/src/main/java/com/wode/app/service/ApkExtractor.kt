package com.wode.app.service

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.wode.app.data.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

class ApkExtractor(private val context: Context) {

    private val pm: PackageManager = context.packageManager

    /**
     * 获取所有已安装的用户应用列表（排除系统应用）
     */
    suspend fun getInstalledApps(includeSystem: Boolean = false): List<AppInfo> =
        withContext(Dispatchers.IO) {
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)

            packages
                .filter { app ->
                    // 排除自身
                    app.packageName != context.packageName &&
                    (includeSystem || (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0)
                }
                .mapNotNull { appInfo ->
                    try {
                        val sourceDir = appInfo.sourceDir
                        val apkFile = File(sourceDir)
                        if (!apkFile.exists() || !apkFile.canRead()) return@mapNotNull null

                        AppInfo(
                            packageName = appInfo.packageName,
                            appName = pm.getApplicationLabel(appInfo).toString(),
                            versionName = try {
                                pm.getPackageInfo(appInfo.packageName, 0).versionName ?: "?"
                            } catch (_: Exception) { "?" },
                            versionCode = try {
                                val vc = pm.getPackageInfo(appInfo.packageName, 0).versionCode
                                if (android.os.Build.VERSION.SDK_INT >= 28)
                                    vc.toLong()
                                else
                                    vc.toLong()
                            } catch (_: Exception) { 0L },
                            sourceDir = sourceDir,
                            apkSize = apkFile.length(),
                            isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                        ).also {
                            // 加载图标
                            it.icon = pm.getApplicationIcon(appInfo)
                        }
                    } catch (_: Exception) {
                        null
                    }
                }
                .sortedBy { it.appName.lowercase() }
        }

    /**
     * 将 APK 复制到缓存目录，返回副本文件
     * 使用缓存目录避免占用外部存储
     */
    suspend fun copyApkToCache(appInfo: AppInfo): File? = withContext(Dispatchers.IO) {
        try {
            val source = File(appInfo.sourceDir)
            if (!source.exists()) return@withContext null

            val cacheDir = File(context.cacheDir, "apk_backup")
            if (!cacheDir.exists()) cacheDir.mkdirs()

            val dest = File(cacheDir, "${appInfo.packageName}_${appInfo.versionName}.apk")

            // 如果已存在且大小一致则跳过复制
            if (dest.exists() && dest.length() == source.length()) {
                return@withContext dest
            }

            source.inputStream().use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            dest
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 计算文件的 MD5
     */
    suspend fun computeMd5(file: File): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("MD5")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * 清理缓存中的 APK 副本
     */
    fun clearCache() {
        val cacheDir = File(context.cacheDir, "apk_backup")
        if (cacheDir.exists()) {
            cacheDir.listFiles()?.forEach { it.delete() }
        }
    }
}
