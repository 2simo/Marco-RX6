package com.marcorx6.library.ui.activity

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri

/**
 * Registers and routes the three Activity Result contracts used by the app.
 * Registration is eager so launchers are registered before the Activity reaches STARTED.
 */
internal class ActivityResultRuntimeFeature(
    activity: ComponentActivity,
    signInRequestCode: Int,
    avatarRequestCode: Int,
    coverRequestCode: Int,
    onGoogleSignInResult: (Intent?) -> Unit,
    onAvatarSelected: (Uri) -> Unit,
    onCoverSelected: (Uri) -> Unit
) {
    private val controller = MainActivityResultController(
        signInRequestCode = signInRequestCode,
        avatarRequestCode = avatarRequestCode,
        coverRequestCode = coverRequestCode,
        onGoogleSignInResult = onGoogleSignInResult,
        onAvatarSelected = onAvatarSelected,
        onCoverSelected = onCoverSelected
    )

    private val googleSignInLauncher =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            controller.handle(signInRequestCode, result.resultCode, result.data)
        }

    private val coverPickerLauncher =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            controller.handle(coverRequestCode, result.resultCode, result.data)
        }

    private val profileAvatarLauncher =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            controller.handle(avatarRequestCode, result.resultCode, result.data)
        }

    fun launchGoogleSignIn(intent: Intent) = googleSignInLauncher.launch(intent)
    fun launchCoverPicker(intent: Intent) = coverPickerLauncher.launch(intent)
    fun launchProfileAvatar(intent: Intent) = profileAvatarLauncher.launch(intent)
}
