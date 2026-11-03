package com.marcorx6.library.ui

import android.content.Context

/**
 * Owns Google account/session operations and the Blogger service composition that depends on them.
 */
internal class GoogleAccountRuntimeFeature(context: Context) {
    private val googleAdminFeature = GoogleAdminFeature(context)

    val accountController get() = googleAdminFeature.accountController
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

    fun driveToken(): String = googleAdminFeature.driveToken()
}
