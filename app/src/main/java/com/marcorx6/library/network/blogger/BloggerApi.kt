package com.marcorx6.library.network

import org.json.JSONObject

/** Narrow transport contract consumed by Blogger data repositories. */
internal interface BloggerApi {
    fun request(method: String, path: String, payload: JSONObject? = null): JSONObject
}
