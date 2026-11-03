package com.marcorx6.library.network

/**
 * Central URL policy for resources that must never be fetched over cleartext HTTP.
 */
internal object SecureUrlPolicy {
    fun toHttpsUrl(rawUrl: String): String? {
        val value = rawUrl.trim()
        return when {
            value.startsWith("https://", ignoreCase = true) -> value
            value.startsWith("http://", ignoreCase = true) -> "https://" + value.substringAfter("://")
            else -> null
        }
    }

    fun upgradeToHttps(rawUrl: String): String {
        return toHttpsUrl(rawUrl) ?: rawUrl.trim()
    }
}
