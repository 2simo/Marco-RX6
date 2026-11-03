package com.marcorx6.library.ui

import android.content.Context
import android.content.Intent

/** Owns Google account client operations and sign-in result validation. */
internal class GoogleAccountController(context: Context) {
    private val client = GoogleSignInFactory.create(context.applicationContext)
    private val resultHandler = GoogleSignInResultHandler()

    fun signInIntent(): Intent = client.signInIntent

    fun signOut(onComplete: () -> Unit) {
        client.signOut().addOnCompleteListener { onComplete() }
    }

    fun handleSignInResult(
        data: Intent?,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) = resultHandler.handle(data, onSuccess, onFailure)
}
