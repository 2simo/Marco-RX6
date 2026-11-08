package com.marcorx6.library.data

import android.content.Context

/** User-selected cap for simultaneous game file transfers. */
internal class DownloadConcurrencyPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "download_settings",
        Context.MODE_PRIVATE
    )

    fun maxConcurrent(): Int = preferences.getInt(KEY_MAX_CONCURRENT, DEFAULT_MAX_CONCURRENT).coerceIn(1, 5)

    fun setMaxConcurrent(value: Int) {
        preferences.edit().putInt(KEY_MAX_CONCURRENT, value.coerceIn(1, 5)).apply()
    }

    fun segmentConnections(): Int = preferences.getInt(KEY_SEGMENT_CONNECTIONS, DEFAULT_SEGMENT_CONNECTIONS).coerceIn(1, 4)

    fun setSegmentConnections(value: Int) {
        preferences.edit().putInt(KEY_SEGMENT_CONNECTIONS, value.coerceIn(1, 4)).apply()
    }

    fun speedLimitBytesPerSecond(): Long = preferences.getLong(KEY_SPEED_LIMIT, 0L).coerceAtLeast(0L)

    fun setSpeedLimitBytesPerSecond(value: Long) {
        preferences.edit().putLong(KEY_SPEED_LIMIT, value.coerceAtLeast(0L)).apply()
    }

    fun wifiOnly(): Boolean = preferences.getBoolean(KEY_WIFI_ONLY, false)

    fun setWifiOnly(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_WIFI_ONLY, enabled).apply()
    }

    companion object {
        private const val KEY_MAX_CONCURRENT = "max_concurrent_downloads"
        private const val KEY_WIFI_ONLY = "wifi_only_downloads"
        private const val KEY_SEGMENT_CONNECTIONS = "segment_connections"
        private const val KEY_SPEED_LIMIT = "download_speed_limit_bytes_per_second"
        const val DEFAULT_SEGMENT_CONNECTIONS = 4
        const val DEFAULT_MAX_CONCURRENT = 2
    }
}
