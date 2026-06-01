package com.wode.app.data

/**
 * 备份记录 — 对应百度网盘中的一条已备份 APK
 */
data class BackupRecord(
    /** 应用包名 */
    val packageName: String,
    /** 应用名称 */
    val appName: String,
    /** 版本名 */
    val versionName: String,
    /** 百度网盘文件唯一标识 (fs_id) */
    val fsId: Long,
    /** 网盘中文件路径 */
    val path: String,
    /** 文件大小 */
    val size: Long,
    /** 上传时间戳 */
    val uploadTime: Long,
    /** MD5 校验值 */
    val md5: String,
)

/**
 * 百度网盘 OAuth Token
 */
data class BaiduToken(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,       // 秒
    val obtainTime: Long,      // 获取时的时间戳（毫秒）
) {
    /** 是否已过期（提前 1 小时刷新） */
    val isExpired: Boolean
        get() = System.currentTimeMillis() > obtainTime + (expiresIn - 3600) * 1000
}
