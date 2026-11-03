package com.marcorx6.library.ui

/**
 * Single in-memory authority for the currently verified Blogger administrator session.
 *
 * A grant is bound to both the Google account and the Blogger blog that were checked.
 * Switching either identity invalidates the grant automatically.
 */
internal class AdminSessionState {
    private var authorizedAccountId: String? = null
    private var authorizedBlogId: String? = null

    @Synchronized
    fun grant(accountId: String?, blogId: String?) {
        if (accountId.isNullOrBlank() || blogId.isNullOrBlank()) {
            clear()
            return
        }
        authorizedAccountId = accountId
        authorizedBlogId = blogId
    }

    @Synchronized
    fun isAuthorized(accountId: String?, blogId: String?): Boolean =
        !accountId.isNullOrBlank() &&
            !blogId.isNullOrBlank() &&
            authorizedAccountId == accountId &&
            authorizedBlogId == blogId

    @Synchronized
    fun clear() {
        authorizedAccountId = null
        authorizedBlogId = null
    }
}
