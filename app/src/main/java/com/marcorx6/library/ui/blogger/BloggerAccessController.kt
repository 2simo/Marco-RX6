package com.marcorx6.library.ui

import java.util.concurrent.Executor

internal class BloggerAccessController(
    private val executor: Executor,
    private val checkAccess: (String) -> Boolean,
    private val currentAccountId: () -> String?,
    private val currentBlogId: () -> String,
    private val setAdmin: (Boolean) -> Unit,
    private val postToUi: (() -> Unit) -> Unit,
    private val refreshUi: () -> Unit,
    private val onDenied: () -> Unit,
    private val onError: (String) -> Unit = {}
) {
    // Prevent an older asynchronous access check from overriding a newer session/blog state.
    private var requestVersion = 0L
    private var verifiedAccountId: String? = null
    private var verifiedBlogId: String? = null

    fun refresh(blogId: String, accountId: String?, hasAccount: Boolean) {
        val version = ++requestVersion
        if (!hasAccount || accountId == null || blogId.isBlank()) {
            verifiedAccountId = null
            verifiedBlogId = null
            setAdmin(false)
            postToUi {
                if (version == requestVersion) {
                    refreshUi()
                    // The access check can run during app startup before the first Blogger
                    // sync has resolved the blog ID. Treat that as a pending state, not an
                    // authentication error; BlogSyncController refreshes access after sync.
                }
            }
            return
        }

        // Keep an existing grant while rechecking the same account/blog pair.
        // A changed identity must not inherit the previous administrator state.
        if (verifiedAccountId != accountId || verifiedBlogId != blogId) setAdmin(false)
        verifiedAccountId = accountId
        verifiedBlogId = blogId
        executor.execute {
            val result = try {
                Result.success(checkAccess(blogId))
            } catch (error: Exception) {
                Result.failure(error)
            }
            postToUi {
                if (
                    version == requestVersion &&
                    currentAccountId() == accountId &&
                    currentBlogId() == blogId
                ) {
                    val allowed = result.getOrDefault(false)
                    setAdmin(allowed)
                    refreshUi()
                    result.exceptionOrNull()?.let { error ->
                        onError(error.message ?: error.javaClass.simpleName)
                    } ?: if (!allowed) {
                        onDenied()
                    } else {
                        Unit
                    }
                }
            }
        }
    }
}
