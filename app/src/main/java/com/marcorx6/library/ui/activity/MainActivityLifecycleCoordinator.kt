package com.marcorx6.library.ui.activity

import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Coordinates Activity lifecycle with the independently owned runtime features.
 */
internal class MainActivityLifecycleCoordinator(
    private val activity: ComponentActivity,
    private val initializePreferredPlatform: () -> Unit,
    private val applySystemBarAppearance: () -> Unit,
    private val createNotificationChannel: () -> Unit,
    private val loadGames: () -> Unit,
    private val restoreAdminSession: () -> Unit,
    private val createInterface: () -> Unit,
    private val showStartupExperience: () -> Unit,
    private val setupCompleted: () -> Boolean,
    private val notificationsEnabled: () -> Boolean,
    private val scheduleNotifications: () -> Unit,
    private val requestNotificationPermission: () -> Unit,
    private val syncFromBlog: () -> Unit,
    private val dismissUserExperience: () -> Unit,
    private val disposeLibrary: () -> Unit,
    private val shutdownServices: () -> Unit,
    private val closeCoverRuntime: () -> Unit
) {
    fun onCreate() {
        initializePreferredPlatform()
        applySystemBarAppearance()
        createNotificationChannel()
        loadGames()
        restoreAdminSession()
        createInterface()
        showStartupExperience()

        // Background prompts and sync stay away from the full-screen first-run setup.
        // They resume on the next Activity creation after setup has been completed.
        if (setupCompleted()) {
            if (notificationsEnabled()) {
                scheduleNotifications()
                requestNotificationPermission()
            }
            syncFromBlog()
        }
    }

    fun onResume() {
        applySystemBarAppearance()
        WindowInsetsControllerCompat(activity.window, activity.window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
        ViewCompat.requestApplyInsets(activity.window.decorView)
    }

    fun onDestroy() {
        dismissUserExperience()
        disposeLibrary()
        shutdownServices()
        closeCoverRuntime()
    }
}
