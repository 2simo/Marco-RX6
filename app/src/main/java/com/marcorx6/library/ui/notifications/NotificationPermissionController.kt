package com.marcorx6.library.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Requests Android 13+ notification permission through the Activity Result API.
 * The launcher is registered when the notification feature is created during Activity.onCreate.
 */
internal class NotificationPermissionController(
    private val activity: ComponentActivity
) {
    private val permissionLauncher =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // The scheduler preference remains user-controlled; the system permission is independent.
        }

    fun requestIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
