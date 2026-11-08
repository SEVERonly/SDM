package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import com.example.model.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class SettingsManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("sdm_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun loadSettings(): AppSettings {
        return AppSettings(
            speedLimitKbps = prefs.getInt(KEY_SPEED_LIMIT, 0),
            maxConcurrentDownloads = prefs.getInt(KEY_MAX_CONCURRENT, 3),
            defaultThreadCount = prefs.getInt(KEY_THREAD_COUNT, 8),
            customStoragePath = prefs.getString(KEY_STORAGE_PATH, "") ?: "",
            wifiOnly = prefs.getBoolean(KEY_WIFI_ONLY, false),
            autoInstallApk = prefs.getBoolean(KEY_AUTO_INSTALL, true),
            notifyOnComplete = prefs.getBoolean(KEY_NOTIFY_COMPLETE, true)
        )
    }

    fun updateSpeedLimit(kbps: Int) {
        prefs.edit().putInt(KEY_SPEED_LIMIT, kbps).apply()
        _settings.value = _settings.value.copy(speedLimitKbps = kbps)
    }

    fun updateMaxConcurrent(count: Int) {
        val clamped = count.coerceIn(1, 10)
        prefs.edit().putInt(KEY_MAX_CONCURRENT, clamped).apply()
        _settings.value = _settings.value.copy(maxConcurrentDownloads = clamped)
    }

    fun updateDefaultThreadCount(threads: Int) {
        val valid = if (threads in listOf(4, 8, 16, 32)) threads else 8
        prefs.edit().putInt(KEY_THREAD_COUNT, valid).apply()
        _settings.value = _settings.value.copy(defaultThreadCount = valid)
    }

    fun updateCustomStoragePath(path: String) {
        prefs.edit().putString(KEY_STORAGE_PATH, path).apply()
        _settings.value = _settings.value.copy(customStoragePath = path)
    }

    fun updateWifiOnly(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WIFI_ONLY, enabled).apply()
        _settings.value = _settings.value.copy(wifiOnly = enabled)
    }

    fun updateAutoInstallApk(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_INSTALL, enabled).apply()
        _settings.value = _settings.value.copy(autoInstallApk = enabled)
    }

    fun updateNotifyOnComplete(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFY_COMPLETE, enabled).apply()
        _settings.value = _settings.value.copy(notifyOnComplete = enabled)
    }

    /**
     * Resolves the target download directory:
     * If user custom storage path is valid, use it;
     * Otherwise automatically default to `/Internal Storage/Downloads/SDM_Downloads/`
     * or app external storage directory without failing or asking repeatedly.
     */
    fun resolveDownloadDirectory(): File {
        val custom = _settings.value.customStoragePath
        if (custom.isNotBlank()) {
            val customDir = File(custom)
            if (customDir.exists() || customDir.mkdirs()) {
                return customDir
            }
        }

        // Default path: Device Public Downloads / SDM_Downloads
        val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val sdmDir = File(publicDownloads, "SDM_Downloads")
        if (sdmDir.exists() || sdmDir.mkdirs()) {
            return sdmDir
        }

        // Safe fallback: App's external files directory
        val appSpecificDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "SDM_Downloads")
        if (!appSpecificDir.exists()) {
            appSpecificDir.mkdirs()
        }
        return appSpecificDir
    }

    fun getDefaultStoragePathDisplay(): String {
        return "/Internal Storage/Downloads/SDM_Downloads/"
    }

    companion object {
        private const val KEY_SPEED_LIMIT = "key_speed_limit"
        private const val KEY_MAX_CONCURRENT = "key_max_concurrent"
        private const val KEY_THREAD_COUNT = "key_thread_count"
        private const val KEY_STORAGE_PATH = "key_storage_path"
        private const val KEY_WIFI_ONLY = "key_wifi_only"
        private const val KEY_AUTO_INSTALL = "key_auto_install"
        private const val KEY_NOTIFY_COMPLETE = "key_notify_complete"

        @Volatile
        private var INSTANCE: SettingsManager? = null

        fun getInstance(context: Context): SettingsManager {
            return INSTANCE ?: synchronized(this) {
                val instance = SettingsManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
