package com.marcorx6.library.ui

import android.app.Activity
import android.content.SharedPreferences
import android.graphics.drawable.GradientDrawable
import android.widget.EditText
import android.widget.TextView
import com.marcorx6.library.data.Game

/**
 * Profile is now the unified account entry point.
 *
 * Visitors are never given a local/default profile. The account screen renders
 * sign-up/sign-in actions for visitors and the authenticated account actions
 * for signed-in users.
 */
internal class ProfileController(
    private val activity: Activity,
    private val preferences: SharedPreferences,
    private val games: () -> List<Game>,
    private val dp: (Int) -> Int,
    private val rounded: (Int, Int) -> GradientDrawable,
    private val makeText: (String, Float, Int, Boolean) -> TextView,
    private val makeInput: (String) -> EditText,
    private val tr: (String) -> String,
    private val isDarkMode: () -> Boolean,
    private val panel: () -> Int,
    private val red: () -> Int,
    private val white: () -> Int,
    private val gray: () -> Int,
    private val showLanguagePicker: () -> Unit,
    private val showNotificationSettings: () -> Unit,
    private val showAccount: () -> Unit,
    private val saveCloudProfile: (String) -> Unit,
    private val chooseAvatar: () -> Unit,
    private val getActiveDialog: () -> android.app.AlertDialog?,
    private val setActiveDialog: (android.app.AlertDialog?) -> Unit,
    private val styleAlertDialog: (android.app.AlertDialog) -> Unit,
    private val applyUnifiedSkin: (android.view.View) -> Unit,
    private val setContentView: (android.view.View) -> Unit,
    private val onBack: () -> Unit
) {
    fun show() {
        // No guest avatar, nickname, or editable profile is created or displayed.
        showAccount()
    }
}
