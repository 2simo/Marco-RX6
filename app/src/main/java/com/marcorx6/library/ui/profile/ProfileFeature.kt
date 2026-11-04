package com.marcorx6.library.ui

import android.app.Activity
import android.content.ContentResolver
import android.content.SharedPreferences
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.widget.EditText
import android.widget.TextView
import com.marcorx6.library.data.Game
import com.marcorx6.library.data.ProfileAvatarRepository
import java.io.File
import java.util.concurrent.Executor

/**
 * Composition root for profile UI and avatar persistence.
 * Activity owns only the feature entry point and the avatar picker launch.
 */
internal class ProfileFeature(
    activity: Activity,
    contentResolver: ContentResolver,
    filesDir: File,
    executor: Executor,
    postToUi: (() -> Unit) -> Unit,
    preferences: SharedPreferences,
    games: () -> List<Game>,
    dp: (Int) -> Int,
    rounded: (Int, Int) -> GradientDrawable,
    makeText: (String, Float, Int, Boolean) -> TextView,
    makeInput: (String) -> EditText,
    tr: (String) -> String,
    isDarkMode: () -> Boolean,
    panel: () -> Int,
    red: () -> Int,
    white: () -> Int,
    gray: () -> Int,
    showLanguagePicker: () -> Unit,
    showNotificationSettings: () -> Unit,
    showAccount: () -> Unit,
    saveCloudProfile: (String) -> Unit,
    chooseAvatar: () -> Unit,
    styleAlertDialog: (android.app.AlertDialog) -> Unit,
    applyUnifiedSkin: (android.view.View) -> Unit,
    onAvatarSaved: () -> Unit,
    onAvatarError: (String) -> Unit,
    setContentView: (android.view.View) -> Unit,
    onBack: () -> Unit
) {
    private val avatarRepository = ProfileAvatarRepository(contentResolver, filesDir)
    private var activeDialog: android.app.AlertDialog? = null

    val avatarController = ProfileAvatarController(
        executor = executor,
        saveAvatar = { uri: Uri -> avatarRepository.save(uri).absolutePath },
        persistPath = { path -> preferences.edit().putString("avatar_path", path).apply() },
        postToUi = postToUi,
        onSaved = onAvatarSaved,
        onError = onAvatarError
    )

    private val controller = ProfileController(
        activity = activity,
        preferences = preferences,
        games = games,
        dp = dp,
        rounded = rounded,
        makeText = makeText,
        makeInput = makeInput,
        tr = tr,
        isDarkMode = isDarkMode,
        panel = panel,
        red = red,
        white = white,
        gray = gray,
        showLanguagePicker = showLanguagePicker,
        showNotificationSettings = showNotificationSettings,
        showAccount = showAccount,
        saveCloudProfile = saveCloudProfile,
        chooseAvatar = chooseAvatar,
        getActiveDialog = { activeDialog },
        setActiveDialog = { activeDialog = it },
        styleAlertDialog = styleAlertDialog,
        applyUnifiedSkin = applyUnifiedSkin,
        setContentView = setContentView,
        onBack = onBack
    )

    fun show() = controller.show()
}
