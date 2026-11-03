package com.marcorx6.library.ui

import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException

internal class GoogleSignInResultHandler {
    fun handle(
        data: Intent?,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        try {
            GoogleSignIn.getSignedInAccountFromIntent(data)
                .getResult(ApiException::class.java)
            onSuccess()
        } catch (error: Exception) {
            onFailure(error.message ?: error.javaClass.simpleName)
        }
    }
}
