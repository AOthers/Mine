package com.wode.app.service

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.wode.app.data.BaiduToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TokenStore(context: Context) {

    private val prefs: SharedPreferences = runCatching {
        val masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "wode_secure_settings",
            masterKey,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrElse {
        context.getSharedPreferences("wode_secure_settings_fallback", Context.MODE_PRIVATE)
    }

    private val _isAuthorized = MutableStateFlow(prefs.getBoolean(KEY_IS_AUTHORIZED, false))
    val isAuthorized: StateFlow<Boolean> = _isAuthorized.asStateFlow()

    fun saveToken(token: BaiduToken) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, token.accessToken)
            .putString(KEY_REFRESH_TOKEN, token.refreshToken)
            .putLong(KEY_EXPIRES_IN, token.expiresIn)
            .putLong(KEY_OBTAIN_TIME, token.obtainTime)
            .putBoolean(KEY_IS_AUTHORIZED, true)
            .apply()
        _isAuthorized.value = true
    }

    fun getToken(): BaiduToken? {
        val accessToken = prefs.getString(KEY_ACCESS_TOKEN, null) ?: return null
        return BaiduToken(
            accessToken = accessToken,
            refreshToken = prefs.getString(KEY_REFRESH_TOKEN, "") ?: "",
            expiresIn = prefs.getLong(KEY_EXPIRES_IN, 0),
            obtainTime = prefs.getLong(KEY_OBTAIN_TIME, 0),
        )
    }

    fun clearToken() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_EXPIRES_IN)
            .remove(KEY_OBTAIN_TIME)
            .putBoolean(KEY_IS_AUTHORIZED, false)
            .apply()
        _isAuthorized.value = false
    }

    fun saveCredentials(appKey: String, secretKey: String) {
        prefs.edit()
            .putString(KEY_APP_KEY, appKey.trim())
            .putString(KEY_SECRET_KEY, secretKey.trim())
            .apply()
    }

    fun getAppKey(): String = prefs.getString(KEY_APP_KEY, "") ?: ""

    fun getSecretKey(): String = prefs.getString(KEY_SECRET_KEY, "") ?: ""

    fun saveBackupPath(path: String) {
        prefs.edit()
            .putString(KEY_BACKUP_PATH, normalizePanPath(path))
            .apply()
    }

    fun getBackupPath(): String {
        return normalizePanPath(prefs.getString(KEY_BACKUP_PATH, "") ?: "")
    }

    fun saveRestorePath(path: String) {
        prefs.edit()
            .putString(KEY_RESTORE_PATH, normalizeLocalPath(path, DEFAULT_RESTORE_PATH))
            .remove(KEY_RESTORE_TREE_URI)
            .apply()
    }

    fun getRestorePath(): String {
        return normalizeLocalPath(prefs.getString(KEY_RESTORE_PATH, "") ?: "", DEFAULT_RESTORE_PATH)
    }

    fun saveRestoreTreeUri(uri: String) {
        prefs.edit()
            .putString(KEY_RESTORE_TREE_URI, uri)
            .apply()
    }

    fun getRestoreTreeUri(): String {
        return prefs.getString(KEY_RESTORE_TREE_URI, "") ?: ""
    }

    fun saveSameVersionStrategy(strategy: SameVersionStrategy) {
        prefs.edit()
            .putString(KEY_SAME_VERSION_STRATEGY, strategy.name)
            .apply()
    }

    fun getSameVersionStrategy(): SameVersionStrategy {
        val raw = prefs.getString(KEY_SAME_VERSION_STRATEGY, SameVersionStrategy.OVERWRITE.name)
        return SameVersionStrategy.entries.firstOrNull { it.name == raw } ?: SameVersionStrategy.OVERWRITE
    }

    private fun normalizePanPath(path: String): String {
        val trimmed = path.trim().replace("\\", "/").trimEnd('/')
        if (trimmed.isBlank() || trimmed == "/") return DEFAULT_BACKUP_PATH
        return if (trimmed.startsWith("/")) trimmed else "/$trimmed"
    }

    private fun normalizeLocalPath(path: String, defaultPath: String): String {
        val trimmed = path.trim().trimEnd('\\', '/')
        return trimmed.ifBlank { defaultPath }
    }

    companion object {
        private const val KEY_ACCESS_TOKEN = "baidu_access_token"
        private const val KEY_REFRESH_TOKEN = "baidu_refresh_token"
        private const val KEY_EXPIRES_IN = "baidu_expires_in"
        private const val KEY_OBTAIN_TIME = "baidu_obtain_time"
        private const val KEY_APP_KEY = "baidu_app_key"
        private const val KEY_SECRET_KEY = "baidu_secret_key"
        private const val KEY_IS_AUTHORIZED = "baidu_is_authorized"
        private const val KEY_BACKUP_PATH = "baidu_backup_path"
        private const val KEY_RESTORE_PATH = "restore_path"
        private const val KEY_RESTORE_TREE_URI = "restore_tree_uri"
        private const val KEY_SAME_VERSION_STRATEGY = "same_version_strategy"

        const val DEFAULT_BACKUP_PATH = "/apps/AppBackup"
        const val DEFAULT_RESTORE_PATH = "restored_apks"
    }

    enum class SameVersionStrategy {
        OVERWRITE,
        SAVE_AS_COPY,
    }
}
