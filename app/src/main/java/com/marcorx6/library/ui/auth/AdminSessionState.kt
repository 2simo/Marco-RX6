package com.marcorx6.library.ui

/**
 * Single in-memory authority for the currently verified Blogger administrator session.
 *
 * The grant is bound to the Google account and active Blogger blog. Changing the blog
 * or account invalidates access without depending on any UI component.
 */
internal class AdminSessionState {
    private var activeBlogId: String = ""
    private var authorizedAccountId: String? = null
    private var authorizedBlogId: String? = null

    @Synchronized
    fun blogId(): String = activeBlogId

    @Synchronized
    fun setBlogId(value: String) {
        if (activeBlogId != value) {
            activeBlogId = value
            clear()
        }
    }

    @Synchronized
    fun grant(accountId: String?, blogId: String?) {
        if (accountId.isNullOrBlank() || blogId.isNullOrBlank() || blogId != activeBlogId) {
            clear()
            return
        }
        authorizedAccountId = accountId
        authorizedBlogId = blogId
    }

    @Synchronized
    fun isAuthorized(accountId: String?): Boolean =
        !accountId.isNullOrBlank() &&
            authorizedAccountId == accountId &&
            authorizedBlogId == activeBlogId &&
            activeBlogId.isNotBlank()

    @Synchronized
    fun clear() {
        authorizedAccountId = null
        authorizedBlogId = null
    }
}
