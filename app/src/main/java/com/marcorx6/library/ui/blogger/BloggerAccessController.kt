package com.marcorx6.library.ui

import java.util.concurrent.Executor

internal class BloggerAccessController(
    private val executor: Executor,
    private val checkAccess: (String) -> Boolean,
    private val currentAccountId: () -> String?,
    private val setAdmin: (Boolean) -> Unit,
    private val postToUi: (() -> Unit) -> Unit,
    private val refreshUi: () -> Unit,
    private val onDenied: () -> Unit
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
            val allowed = try {
                checkAccess(blogId)
            } catch (_: Exception) {
                false
            }
            postToUi {
                if (version == requestVersion && currentAccountId() == accountId) {
                    setAdmin(allowed)
                    refreshUi()
                    if (!allowed) onDenied()
                }
            }
        }
    }
}
