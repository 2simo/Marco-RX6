package com.marcorx6.library.ui

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope

/** Builds the existing Google sign-in client with the app's required API scopes. */
internal object GoogleSignInFactory {
    fun create(context: Context) = GoogleSignIn.getClient(
        context,
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(
                Scope("https://www.googleapis.com/auth/blogger"),
                Scope("https://www.googleapis.com/auth/drive.file")
            )
            .build()
    )
}
