package com.marcorx6.library.network

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Blogger API transport and server-side authorization lookup.
 * Blog write access is verified against Blogger's BlogUserInfo resource.
 */
internal class BloggerApiClient(
    private val tokenProvider: () -> String
) : BloggerApi {
    override fun request(method: String, path: String, payload: JSONObject?): JSONObject {
        val connection = URL("https://www.googleapis.com/blogger/v3/$path")
            .openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 20_000
            connection.readTimeout = 25_000
            connection.setRequestProperty("Authorization", "Bearer ${tokenProvider()}")
            connection.setRequestProperty("Accept", "application/json")
            if (payload != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use {
                    it.write(payload.toString().toByteArray(Charsets.UTF_8))
                }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = try {
                    JSONObject(body).optJSONObject("error")?.optString("message") ?: body
                } catch (_: Exception) {
                    body
                }
                throw IllegalStateException("خطأ في Blogger API ($code): $message")
            }
            return if (body.isBlank()) JSONObject() else JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    fun hasWriteAccess(blogId: String): Boolean {
        if (blogId.isBlank()) return false
        val response = request("GET", "users/self/blogs/$blogId")
        val userInfo = response.optJSONObject("blog_user_info") ?: return false
        val role = userInfo.optString("role").uppercase(Locale.ROOT)
        return userInfo.optBoolean("hasAdminAccess") || role == "ADMIN" || role == "AUTHOR"
    }
}
