package com.marcorx6.library.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri

/**
 * Routes Android activity results to the feature that owns each result.
 * Feature-specific behavior stays in the corresponding controller.
 */
internal class MainActivityResultController(
    private val signInRequestCode: Int,
    private val avatarRequestCode: Int,
    private val coverRequestCode: Int,
    private val onGoogleSignInResult: (Intent?) -> Unit,
    private val onAvatarSelected: (Uri) -> Unit,
    private val onCoverSelected: (Uri) -> Unit
) {
    fun handle(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            signInRequestCode -> onGoogleSignInResult(data)
            avatarRequestCode -> {
                if (resultCode == Activity.RESULT_OK) {
                    data?.data?.let(onAvatarSelected)
                }
            }
            coverRequestCode -> {
                if (resultCode == Activity.RESULT_OK) {
                    data?.data?.let(onCoverSelected)
                }
            }
        }
    }
}
