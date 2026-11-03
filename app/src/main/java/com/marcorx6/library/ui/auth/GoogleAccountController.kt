package com.marcorx6.library.ui

import android.content.Context
import android.content.Intent

/** Owns the configured Google Sign-In client, separate from Activity UI and result handling. */
internal class GoogleAccountController(context: Context) {
    private val client = GoogleSignInFactory.create(context.applicationContext)

    fun signInIntent(): Intent = client.signInIntent

    fun signOut(onComplete: () -> Unit) {
        client.signOut().addOnCompleteListener { onComplete() }
    }
}
