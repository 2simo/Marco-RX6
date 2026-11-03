package com.marcorx6.library.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.Executor

class BloggerAccessControllerTest {

    private val directExecutor = Executor { command -> command.run() }

    @Test
    fun grantsAdminOnlyAfterAccessCheckSucceedsForCurrentAccount() {
        var admin = false
        var refreshed = 0
        var denied = 0
        val controller = BloggerAccessController(
            executor = directExecutor,
            checkAccess = { it == "blog-1" },
            currentAccountId = { "account-1" },
            setAdmin = { admin = it },
            postToUi = { it() },
            refreshUi = { refreshed++ },
            onDenied = { denied++ }
        )

        controller.refresh("blog-1", "account-1", true)

        assertEquals(true, admin)
        assertEquals(1, refreshed)
        assertEquals(0, denied)
    }

    @Test
    fun rejectsAccessResultWhenAccountChangesDuringCheck() {
        var admin = true
        var refreshed = 0
        var denied = 0
        var currentAccount = "account-2"
        var uiAction: (() -> Unit)? = null
        val controller = BloggerAccessController(
            executor = directExecutor,
            checkAccess = { true },
            currentAccountId = { currentAccount },
            setAdmin = { admin = it },
            postToUi = { action -> uiAction = action },
            refreshUi = { refreshed++ },
            onDenied = { denied++ }
        )

        controller.refresh("blog-1", "account-1", true)
        currentAccount = "account-2"
        uiAction?.invoke()

        assertEquals(false, admin)
        assertEquals(0, refreshed)
        assertEquals(0, denied)
    }

    @Test
    fun ignoresOlderCheckWhenBlogChangesForSameAccount() {
        var admin = false
        val queuedUiActions = mutableListOf<() -> Unit>()
        val controller = BloggerAccessController(
            executor = directExecutor,
            checkAccess = { it == "blog-1" },
            currentAccountId = { "account-1" },
            setAdmin = { admin = it },
            postToUi = { action -> queuedUiActions.add(action) },
            refreshUi = {},
            onDenied = {}
        )

        controller.refresh("blog-1", "account-1", true)
        controller.refresh("blog-2", "account-1", true)

        queuedUiActions[0].invoke()
        assertEquals(false, admin)

        queuedUiActions[1].invoke()
        assertEquals(false, admin)
    }

    @Test
    fun clearsAdminWhenAccountOrBlogIsMissing() {
        var admin = true
        var checks = 0
        val controller = BloggerAccessController(
            executor = directExecutor,
            checkAccess = { checks++; true },
            currentAccountId = { "account-1" },
            setAdmin = { admin = it },
            postToUi = { it() },
            refreshUi = {},
            onDenied = {}
        )

        controller.refresh("blog-1", null, false)

        assertEquals(false, admin)
        assertEquals(0, checks)
    }
}
