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

    companion object {
        private const val KEY_MAX_CONCURRENT = "max_concurrent_downloads"
        const val DEFAULT_MAX_CONCURRENT = 2
    }
}
