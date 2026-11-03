package com.marcorx6.library.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AdminSessionStateTest {

    @Test
    fun grantsAccessOnlyForVerifiedAccountAndBlogPair() {
        val state = AdminSessionState()
        state.grant("account-1", "blog-1")

        assertEquals(true, state.isAuthorized("account-1", "blog-1"))
        assertEquals(false, state.isAuthorized("account-2", "blog-1"))
        assertEquals(false, state.isAuthorized("account-1", "blog-2"))
        assertEquals(false, state.isAuthorized(null, "blog-1"))
    }

    @Test
    fun refusesIncompleteGrant() {
        val state = AdminSessionState()
        state.grant("account-1", "blog-1")
        state.grant("account-1", "")

        assertEquals(false, state.isAuthorized("account-1", "blog-1"))
    }

    @Test
    fun clearRevokesPreviouslyGrantedSession() {
        val state = AdminSessionState()
        state.grant("account-1", "blog-1")
        state.clear()

        assertEquals(false, state.isAuthorized("account-1", "blog-1"))
    }
}
