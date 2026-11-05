package com.marcorx6.library.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.marcorx6.library.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Checks the latest stable GitHub Release and sends users to its official release page. */
internal class AppUpdateChecker(
    private val activity: Activity,
    private val translate: (String) -> String,
    private val styleDialog: (AlertDialog) -> Unit = {}
) {
    fun check() {
        Toast.makeText(activity, translate("جارٍ التحقق من وجود تحديث..."), Toast.LENGTH_SHORT).show()
        Thread {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(RELEASE_API).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 15000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "MARCO-RX6-Android")
                    setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                }
                val code = connection.responseCode
                if (code !in 200..299) throw IllegalStateException("GitHub HTTP $code")
                val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                if (release.optBoolean("draft", false) || release.optBoolean("prerelease", false)) {
                    throw IllegalStateException("No stable release available")
                }
                val tag = release.optString("tag_name").trim()
                val latestVersion = tag.removePrefix("v").removePrefix("V")
                val releaseUrl = release.optString("html_url").ifBlank { RELEASE_PAGE }
                val notes = release.optString("body").trim().take(MAX_NOTES_LENGTH)
                if (latestVersion.isBlank() || !releaseUrl.startsWith("https://github.com/")) {
                    throw IllegalStateException("Invalid release metadata")
                }
                val currentVersion = BuildConfig.VERSION_NAME
                val newer = compareVersions(latestVersion, currentVersion) > 0
                activity.runOnUiThread {
                    if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                    if (!newer) {
                        Toast.makeText(activity, translate("أنت تستعمل أحدث إصدار") + " (" + currentVersion + ")", Toast.LENGTH_LONG).show()
                    } else {
                        val message = buildString {
                            append(translate("الإصدار الحالي")).append(": ").append(currentVersion)
                            append("\n").append(translate("الإصدار الجديد")).append(": ").append(latestVersion)
                            if (notes.isNotBlank()) append("\n\n").append(translate("التغييرات")).append(":\n").append(notes)
                        }
                        AlertDialog.Builder(activity)
                            .setTitle(translate("يتوفر تحديث جديد"))
                            .setMessage(message)
                            .setNegativeButton(translate("إغلاق"), null)
                            .setPositiveButton(translate("فتح صفحة التحديث")) { _, _ ->
                                runCatching {
                                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl)))
                                }.onFailure {
                                    Toast.makeText(activity, translate("تعذر فتح الرابط"), Toast.LENGTH_SHORT).show()
                                }
                            }
                            .show().also(styleDialog)
                    }
                }
            } catch (_: Exception) {
                activity.runOnUiThread {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        Toast.makeText(activity, translate("تعذر التحقق من التحديث. تحقق من اتصال الإنترنت."), Toast.LENGTH_LONG).show()
                    }
                }
            } finally {
                connection?.disconnect()
            }
        }.start()
    }

    private fun compareVersions(first: String, second: String): Int {
        val a = Regex("""\d+""").findAll(first).mapNotNull { it.value.toIntOrNull() }.toList()
        val b = Regex("""\d+""").findAll(second).mapNotNull { it.value.toIntOrNull() }.toList()
        if (a.isEmpty() || b.isEmpty()) return 0
        for (index in 0 until maxOf(a.size, b.size)) {
            val left = a.getOrElse(index) { 0 }
            val right = b.getOrElse(index) { 0 }
            if (left != right) return left.compareTo(right)
        }
        return 0
    }

    companion object {
        private const val RELEASE_API = "https://api.github.com/repos/2simo/Marco-RX6/releases/latest"
        private const val RELEASE_PAGE = "https://github.com/2simo/Marco-RX6/releases"
        private const val MAX_NOTES_LENGTH = 3000
    }
}
