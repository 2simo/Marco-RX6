package com.marcorx6.library.ui

import java.util.concurrent.Executor

/**
 * Composition boundary for Blogger operations.
 *
 * Keeps API transport, post repository, and access verification together so Activity UI
 * code depends on one Blogger feature entry point rather than assembling its internals.
 */
internal class BloggerServices(
    tokenProvider: GoogleTokenProvider,
    blogIdProvider: () -> String,
    isAdminSession: () -> Boolean
) {
    val apiClient = com.marcorx6.library.network.BloggerApiClient {
        tokenProvider.bloggerToken()
    }

    val posts = com.marcorx6.library.data.BloggerPostRepository(
        api = apiClient,
        blogIdProvider = blogIdProvider,
        authorizationCheck = isAdminSession
    )

    fun createAccessController(
        executor: Executor,
        currentAccountId: () -> String?,
        currentBlogId: () -> String,
        setAdmin: (Boolean) -> Unit,
        postToUi: (() -> Unit) -> Unit,
        refreshUi: () -> Unit,
        onDenied: () -> Unit,
        onError: (String) -> Unit = {}
    ) = BloggerAccessController(
        executor = executor,
        checkAccess = apiClient::hasWriteAccess,
        currentAccountId = currentAccountId,
        currentBlogId = currentBlogId,
        setAdmin = setAdmin,
        postToUi = postToUi,
        refreshUi = refreshUi,
        onDenied = onDenied,
        onError = onError
    )
}
