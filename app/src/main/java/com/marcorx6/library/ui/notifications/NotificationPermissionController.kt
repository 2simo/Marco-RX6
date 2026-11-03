package com.marcorx6.library.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build

internal class NotificationPermissionController(
    private val activity: Activity
) {
    fun requestIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_CODE)
        }
    }

    private companion object {
        const val REQUEST_CODE = 2002
    }
}
