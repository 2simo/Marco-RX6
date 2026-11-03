package com.marcorx6.library.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AdminSessionStateTest {

    @Test
    fun grantsAccessOnlyForVerifiedAccountAndActiveBlog() {
        val state = AdminSessionState()
        state.setBlogId("blog-1")
        state.grant("account-1", "blog-1")

        assertEquals(true, state.isAuthorized("account-1"))
        assertEquals(false, state.isAuthorized("account-2"))
        assertEquals(false, state.isAuthorized(null))
    }

    @Test
    fun refusesGrantForDifferentOrMissingBlog() {
        val state = AdminSessionState()
        state.setBlogId("blog-1")
        state.grant("account-1", "blog-2")

        assertEquals(false, state.isAuthorized("account-1"))
    }

    @Test
    fun changingBlogAutomaticallyRevokesPreviousGrant() {
        val state = AdminSessionState()
        state.setBlogId("blog-1")
        state.grant("account-1", "blog-1")
        state.setBlogId("blog-2")

        assertEquals("blog-2", state.blogId())
        assertEquals(false, state.isAuthorized("account-1"))
    }

    @Test
    fun clearRevokesPreviouslyGrantedSession() {
        val state = AdminSessionState()
        state.setBlogId("blog-1")
        state.grant("account-1", "blog-1")
        state.clear()

        assertEquals(false, state.isAuthorized("account-1"))
    }
}
