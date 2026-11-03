package com.marcorx6.library.data

import com.marcorx6.library.network.BloggerApi
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class BloggerPostRepositorySecurityTest {

    @Test
    fun deletePostDoesNotReachApiWhenAuthorizationIsMissing() {
        var apiCalls = 0
        val api = object : BloggerApi {
            override fun request(method: String, path: String, payload: JSONObject?): JSONObject {
                apiCalls++
                throw AssertionError("Blogger API must not be called without authorization")
            }
        }
        val repository = BloggerPostRepository(
            api = api,
            blogIdProvider = { "test-blog-id" },
            authorizationCheck = { false }
        )

        try {
            repository.deletePost("post-123")
            fail("Expected missing authorization to be rejected")
        } catch (_: SecurityException) {
            // Expected: the repository blocks the operation before reaching the API.
        }

        assertEquals(0, apiCalls)
    }

    @Test
    fun deletePostDoesNotReachApiWhenPostIdIsBlank() {
        var apiCalls = 0
        val api = object : BloggerApi {
            override fun request(method: String, path: String, payload: JSONObject?): JSONObject {
                apiCalls++
                throw AssertionError("Blogger API must not be called with a blank post ID")
            }
        }
        val repository = BloggerPostRepository(
            api = api,
            blogIdProvider = { "test-blog-id" },
            authorizationCheck = { true }
        )

        try {
            repository.deletePost(" ")
            fail("Expected blank post ID to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected: invalid identifiers are rejected before any network request.
        }

        assertEquals(0, apiCalls)
    }
}
