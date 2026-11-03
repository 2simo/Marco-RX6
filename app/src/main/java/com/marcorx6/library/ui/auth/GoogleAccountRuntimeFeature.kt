package com.marcorx6.library.ui.auth

import com.marcorx6.library.ui.*

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount

/**
 * Owns Google account/session operations and the Blogger service composition that depends on them.
 */
internal class GoogleAccountRuntimeFeature(private val context: Context) {
    private val googleAdminFeature = GoogleAdminFeature(context)

    val tokenProvider get() = googleAdminFeature.tokenProvider

    var blogId: String
        get() = googleAdminFeature.blogId
        set(value) { googleAdminFeature.blogId = value }

    var isAdminSession: Boolean
        get() = googleAdminFeature.isAdminSession
        set(value) { googleAdminFeature.isAdminSession = value }

    val bloggerServices by lazy {
        BloggerServices(
            tokenProvider = tokenProvider,
            blogIdProvider = { blogId },
            isAdminSession = { isAdminSession }
        )
    }

    fun currentAccount(): GoogleSignInAccount? = GoogleSignIn.getLastSignedInAccount(context)

    fun signInIntent(): Intent = googleAdminFeature.accountController.signInIntent()

    fun signOut(onSignedOut: () -> Unit) =
        googleAdminFeature.accountController.signOut(onSignedOut)

    fun handleSignInResult(
        data: Intent?,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) = googleAdminFeature.accountController.handleSignInResult(
        data = data,
        onSuccess = onSuccess,
        onFailure = onFailure
    )

    fun driveToken(): String = googleAdminFeature.driveToken()
}
