package com.wode.app.service

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.wode.app.data.BaiduToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 加密本地配置存储 — Token、AppKey 等敏感信息
 *
 * 使用 Jetpack Security EncryptedSharedPreferences，
 * 密钥存储在 Android Keystore 中（硬件级保护）。
 */
class TokenStore(context: Context) {

    private val masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    private val prefs = EncryptedSharedPreferences.create(
        "wode_secure_settings",
        masterKey,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    // --- 授权状态（非敏感，用 StateFlow 暴露给 UI） ---
    private val _isAuthorized = MutableStateFlow(prefs.getBoolean(KEY_IS_AUTHORIZED, false))
    val isAuthorized: StateFlow<Boolean> = _isAuthorized.asStateFlow()

    // ==================== Token ====================

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

    // ==================== AppKey / SecretKey ====================

    fun saveCredentials(appKey: String, secretKey: String) {
        prefs.edit()
            .putString(KEY_APP_KEY, appKey.trim())
            .putString(KEY_SECRET_KEY, secretKey.trim())
            .apply()
    }

    fun getAppKey(): String = prefs.getString(KEY_APP_KEY, "") ?: ""

    fun getSecretKey(): String = prefs.getString(KEY_SECRET_KEY, "") ?: ""

    // ==================== Keys ====================

    companion object {
        private const val KEY_ACCESS_TOKEN = "baidu_access_token"
        private const val KEY_REFRESH_TOKEN = "baidu_refresh_token"
        private const val KEY_EXPIRES_IN = "baidu_expires_in"
        private const val KEY_OBTAIN_TIME = "baidu_obtain_time"
        private const val KEY_APP_KEY = "baidu_app_key"
        private const val KEY_SECRET_KEY = "baidu_secret_key"
        private const val KEY_IS_AUTHORIZED = "baidu_is_authorized"
    }
}
