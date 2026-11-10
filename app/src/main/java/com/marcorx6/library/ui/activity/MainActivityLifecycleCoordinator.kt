package com.marcorx6.library.ui.activity

import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat

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
    private val disposeAccount: () -> Unit,
    private val dismissUserExperience: () -> Unit,
    private val disposeLibrary: () -> Unit,
    private val shutdownServices: () -> Unit,
    private val closeCoverRuntime: () -> Unit
) {
    private var created = false
    private var destroyed = false

    fun onCreate() {
        if (created || destroyed) return
        created = true
        initializePreferredPlatform()
        applySystemBarAppearance()
        createNotificationChannel()
        loadGames()
        restoreAdminSession()
        // Put the splash on screen before composing the full library view. The launch
        // window already uses the same dark background, so neither the system launch
        // window nor the Activity's first layout is exposed as a blank intermediate page.
        showStartupExperience()
        createInterface()

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
        if (!created || destroyed) return
        applySystemBarAppearance()
        ViewCompat.requestApplyInsets(activity.window.decorView)
    }

    fun onDestroy() {
        if (destroyed) return
        destroyed = true
        disposeAccount()
        dismissUserExperience()
        disposeLibrary()
        shutdownServices()
        closeCoverRuntime()
    }
}
