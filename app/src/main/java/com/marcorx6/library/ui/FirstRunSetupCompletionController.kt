package com.marcorx6.library.ui

import android.content.SharedPreferences

internal class FirstRunSetupCompletionController(
    private val settings: SharedPreferences,
    private val appearance: SharedPreferences,
    private val setNotificationsEnabled: (Boolean) -> Unit,
    private val scheduleNotifications: () -> Unit,
    private val cancelNotifications: () -> Unit,
    private val requestNotificationPermission: () -> Unit,
    private val onPlatformSelected: (String) -> Unit
) {
    fun complete(
        languageCode: String,
        preferredPlatform: String,
        darkMode: Boolean,
        accentKey: String,
        notificationsEnabled: Boolean,
        onFinished: () -> Unit
    ) {
        settings.edit()
            .putString("language", languageCode)
            .putString("preferred_platform", preferredPlatform)
            .putBoolean("setup_completed", true)
            .putBoolean("profile_prompted", true)
            .apply()
        appearance.edit()
            .putBoolean("dark_mode", darkMode)
            .putString("accent", accentKey)
            .apply()
        onPlatformSelected(preferredPlatform)
        setNotificationsEnabled(notificationsEnabled)
        if (notificationsEnabled) {
            scheduleNotifications()
            requestNotificationPermission()
        } else {
            cancelNotifications()
        }
        onFinished()
    }
}
