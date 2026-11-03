package com.marcorx6.library.ui

import androidx.activity.ComponentActivity
import android.widget.TextView

/** Composes the side menu with the app's feature entry points. */
internal class SideMenuFeature(
    activity: ComponentActivity,
    dp: (Int) -> Int,
    makeText: (String, Float, Int, Boolean) -> TextView,
    translate: (String) -> String,
    isDark: () -> Boolean,
    language: () -> String,
    gold: Int,
    ink: Int,
    surface: Int,
    white: Int,
    gray: Int,
    syncing: () -> Boolean,
    isAdmin: () -> Boolean,
    sync: () -> Unit,
    showDownloads: () -> Unit,
    showNotifications: () -> Unit,
    showProfile: () -> Unit,
    signOut: () -> Unit,
    signIn: () -> Unit,
    showGameDialog: () -> Unit,
    showGameOfDay: () -> Unit,
    showLanguage: () -> Unit,
    showAppearance: () -> Unit,
    showTypography: () -> Unit,
    openLink: (String) -> Unit,
    applyMotion: (android.view.View) -> Unit,
    applySkin: (android.view.View) -> Unit
) {
    private val controller = SideMenuController(
        activity = activity,
        dp = dp,
        makeText = makeText,
        tr = translate,
        isDark = isDark,
        language = language,
        gold = gold,
        ink = ink,
        surface = surface,
        white = white,
        gray = gray,
        syncing = syncing,
        isAdmin = isAdmin,
        sync = sync,
        showDownloads = showDownloads,
        showNotifications = showNotifications,
        showProfile = showProfile,
        signOut = signOut,
        signIn = signIn,
        showGameDialog = showGameDialog,
        showGameOfDay = showGameOfDay,
        showLanguage = showLanguage,
        showAppearance = showAppearance,
        showTypography = showTypography,
        openLink = openLink,
        applyMotion = applyMotion,
        applySkin = applySkin
    )

    fun show() = controller.show()
}
