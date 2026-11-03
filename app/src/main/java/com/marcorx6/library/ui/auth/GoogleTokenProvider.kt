package com.marcorx6.library.ui

import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn

/** Centralizes scoped Google OAuth token retrieval for Blogger and Drive. */
internal class GoogleTokenProvider(
    context: Context,
    private val isAdminSession: () -> Boolean
) {
    private val appContext = context.applicationContext

    fun bloggerToken(): String {
        val account = GoogleSignIn.getLastSignedInAccount(appContext)
            ?: throw IllegalStateException("يرجى تسجيل الدخول بحساب Google")
        val androidAccount = account.account
            ?: throw IllegalStateException("بيانات حساب Google غير متاحة")
        return GoogleAuthUtil.getToken(
            appContext,
            androidAccount,
            "oauth2:https://www.googleapis.com/auth/blogger"
        )
    }

    fun driveToken(): String {
        val account = GoogleSignIn.getLastSignedInAccount(appContext)
            ?: throw IllegalStateException("يرجى تسجيل الدخول بحساب المسؤول")
        if (!isAdminSession()) {
            throw SecurityException("يلزم التحقق من صلاحيات Blogger قبل رفع الأغلفة")
        }
        val androidAccount = account.account
            ?: throw IllegalStateException("بيانات حساب Google غير متاحة")
        return GoogleAuthUtil.getToken(
            appContext,
            androidAccount,
            "oauth2:https://www.googleapis.com/auth/drive.file"
        )
    }
}
