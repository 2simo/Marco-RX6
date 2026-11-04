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

    fun refresh(blogId: String, accountId: String?, hasAccount: Boolean) {
        val version = ++requestVersion
        if (!hasAccount || accountId == null || blogId.isBlank()) {
            setAdmin(false)
            return
        }

        setAdmin(false)
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
