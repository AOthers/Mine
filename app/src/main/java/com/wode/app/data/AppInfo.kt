package com.wode.app.data

import android.graphics.drawable.Drawable
import android.os.Parcelable
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

/**
 * 已安装应用的信息
 */
@Parcelize
data class AppInfo(
    /** 应用包名，唯一标识 */
    val packageName: String,
    /** 应用名称（用户可见） */
    val appName: String,
    /** 版本名 */
    val versionName: String,
    /** 版本号 */
    val versionCode: Long,
    /** APK 源文件路径 (ApplicationInfo.sourceDir) */
    val sourceDir: String,
    /** APK 文件大小（字节） */
    val apkSize: Long,
    /** 是否为系统应用 */
    val isSystemApp: Boolean,
) : Parcelable {

    /** 图标 — 不参与序列化，运行时加载 */
    @IgnoredOnParcel
    var icon: Drawable? = null

    /** 格式化大小 */
    val formattedSize: String
        get() = when {
            apkSize < 1024 -> "${apkSize}B"
            apkSize < 1024 * 1024 -> "${apkSize / 1024}KB"
            else -> "%.1fMB".format(apkSize / (1024.0 * 1024.0))
        }
}
