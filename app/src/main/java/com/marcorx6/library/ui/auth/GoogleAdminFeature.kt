package com.marcorx6.library.ui

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn

/** Single feature boundary for Google account operations, verified admin session, and OAuth tokens. */
internal class GoogleAdminFeature(private val context: Context) {
    private val sessionState = AdminSessionState()

    val accountController = GoogleAccountController(context)
    val tokenProvider = GoogleTokenProvider(context, sessionState)

    var blogId: String
        get() = sessionState.blogId()
        set(value) = sessionState.setBlogId(value)

    var isAdminSession: Boolean
        get() = sessionState.isAuthorized(
            accountId = GoogleSignIn.getLastSignedInAccount(context)?.id
        )
        set(value) {
            if (value) {
                sessionState.grant(
                    accountId = GoogleSignIn.getLastSignedInAccount(context)?.id,
                    blogId = blogId
                )
            } else {
                sessionState.clear()
            }
        }

    fun driveToken(): String = tokenProvider.driveToken()
}
