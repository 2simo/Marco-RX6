package com.marcorx6.library.ui

import android.app.Dialog
import android.content.SharedPreferences
import androidx.activity.ComponentActivity

/** Owns first-run setup composition and the dialog lifecycle. */
internal class FirstRunSetupFeature(
    activity: ComponentActivity,
    settings: SharedPreferences,
    appearancePreferences: () -> SharedPreferences,
    dp: (Int) -> Int,
    rounded: (Int, Int) -> android.graphics.drawable.GradientDrawable,
    makeText: (String, Float, Int, Boolean) -> android.widget.TextView,
    currentLanguage: () -> String,
    isDarkMode: () -> Boolean,
    isNotificationsEnabled: () -> Boolean,
    setNotificationsEnabled: (Boolean) -> Unit,
    scheduleNotifications: () -> Unit,
    cancelNotifications: () -> Unit,
    requestNotificationPermission: () -> Unit,
    onPlatformSelected: (String) -> Unit,
    applyUnifiedSkin: (android.view.View) -> Unit,
    applyMotionPreferences: (android.view.View) -> Unit,
    recreateActivity: () -> Unit
) {
    private var setupDialog: Dialog? = null

    private val completionController = FirstRunSetupCompletionController(
        settings = settings,
        appearance = appearancePreferences(),
        setNotificationsEnabled = setNotificationsEnabled,
        scheduleNotifications = scheduleNotifications,
        cancelNotifications = cancelNotifications,
        requestNotificationPermission = requestNotificationPermission,
        onPlatformSelected = onPlatformSelected
    )

    private val viewFactory = FirstRunSetupViewFactory(
        activity = activity,
        dp = dp,
        makeText = makeText
    )

    private val controller = FirstRunSetupController(
        activity = activity,
        dp = dp,
        rounded = rounded,
        makeText = makeText,
        appearancePreferences = appearancePreferences,
        currentLanguage = currentLanguage,
        isDarkMode = isDarkMode,
        isNotificationsEnabled = isNotificationsEnabled,
        viewFactory = viewFactory,
        completionController = completionController,
        applyUnifiedSkin = applyUnifiedSkin,
        applyMotionPreferences = applyMotionPreferences,
        getSetupDialog = { setupDialog },
        setSetupDialog = { setupDialog = it },
        recreateActivity = recreateActivity
    )

    fun show() = controller.show()

    fun dismiss() {
        setupDialog?.let { if (it.isShowing) it.dismiss() }
        setupDialog = null
    }
}
