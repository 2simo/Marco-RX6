package com.marcorx6.library.ui

import android.content.ContentResolver
import android.content.SharedPreferences
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.marcorx6.library.data.Game
import java.io.File
import java.util.concurrent.Executor

/**
 * Owns profile, first-run setup and startup experience composition and lifecycle.
 */
internal class UserExperienceRuntimeFeature(
    activity: ComponentActivity,
    contentResolver: ContentResolver,
    filesDir: File,
    executor: Executor,
    postToUi: (() -> Unit) -> Unit,
    settings: SharedPreferences,
    appearancePreferences: () -> SharedPreferences,
    games: () -> List<Game>,
    dp: (Int) -> Int,
    rounded: (Int, Int) -> GradientDrawable,
    makeText: (String, Float, Int, Boolean) -> TextView,
    makeInput: (String) -> EditText,
    translate: (String) -> String,
    currentLanguage: () -> String,
    isDarkMode: () -> Boolean,
    panel: () -> Int,
    red: () -> Int,
    white: () -> Int,
    gray: () -> Int,
    showLanguagePicker: () -> Unit,
    showNotificationSettings: () -> Unit,
    launchAvatarPicker: (Intent) -> Unit,
    styleAlertDialog: (android.app.AlertDialog) -> Unit,
    applyUnifiedSkin: (View) -> Unit,
    onAvatarSaved: () -> Unit,
    onAvatarError: (String) -> Unit,
    isNotificationsEnabled: () -> Boolean,
    setNotificationsEnabled: (Boolean) -> Unit,
    scheduleNotifications: () -> Unit,
    cancelNotifications: () -> Unit,
    requestNotificationPermission: () -> Unit,
    onPlatformSelected: (String) -> Unit,
    recreateActivity: () -> Unit,
    showFirstRunSetup: () -> Unit,
    showProfileForStartup: () -> Unit
) {
    private val profileFeature by lazy {
        ProfileFeature(
            activity = activity,
            contentResolver = contentResolver,
            filesDir = filesDir,
            executor = executor,
            postToUi = postToUi,
            preferences = ProfilePreferences.open(activity),
            games = games,
            dp = dp,
            rounded = rounded,
            makeText = makeText,
            makeInput = makeInput,
            tr = translate,
            isDarkMode = isDarkMode,
            panel = panel,
            red = red,
            white = white,
            gray = gray,
            showLanguagePicker = showLanguagePicker,
            showNotificationSettings = showNotificationSettings,
            chooseAvatar = ::chooseAvatar,
            styleAlertDialog = styleAlertDialog,
            applyUnifiedSkin = applyUnifiedSkin,
            onAvatarSaved = onAvatarSaved,
            onAvatarError = onAvatarError
        )
    }

    private val firstRunSetupFeature by lazy {
        FirstRunSetupFeature(
            activity = activity,
            settings = settings,
            appearancePreferences = appearancePreferences,
            dp = dp,
            rounded = rounded,
            makeText = makeText,
            currentLanguage = currentLanguage,
            isDarkMode = isDarkMode,
            isNotificationsEnabled = isNotificationsEnabled,
            setNotificationsEnabled = setNotificationsEnabled,
            scheduleNotifications = scheduleNotifications,
            cancelNotifications = cancelNotifications,
            requestNotificationPermission = requestNotificationPermission,
            onPlatformSelected = onPlatformSelected,
            applyUnifiedSkin = applyUnifiedSkin,
            recreateActivity = recreateActivity
        )
    }

    private val startupExperienceController by lazy {
        StartupExperienceController(
            activity = activity,
            dp = dp,
            makeText = makeText,
            showFirstRunSetup = showFirstRunSetup,
            showProfile = showProfileForStartup
        )
    }

    private fun chooseAvatar() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        launchAvatarPicker(intent)
    }

    val avatarController get() = profileFeature.avatarController

    fun showProfile() = profileFeature.show()
    fun showFirstRunSetup() = firstRunSetupFeature.show()
    fun showStartupExperience() = startupExperienceController.show()

    fun dismiss() {
        startupExperienceController.dismiss()
        firstRunSetupFeature.dismiss()
    }
}
