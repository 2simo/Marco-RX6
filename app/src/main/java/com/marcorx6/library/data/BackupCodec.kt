package com.marcorx6.library.data

import org.json.JSONArray
import org.json.JSONObject

internal data class BackupAppearance(
    val darkMode: Boolean,
    val accent: String,
    val animations: Boolean
)

internal data class BackupSnapshot(
    val games: List<Game>,
    val appearance: BackupAppearance?
)

/**
 * Converts the stable MARCO RX6 backup format to and from the app data model.
 * Device-local cover paths are intentionally excluded from exported backups.
 */
internal object BackupCodec {
    private const val FORMAT = "MARCO_RX6_BACKUP"
    private const val VERSION = 1

    fun encode(
        games: List<Game>,
        darkMode: Boolean,
        accent: String,
        animations: Boolean
    ): String {
        val payload = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("createdAt", System.currentTimeMillis())
            .put("games", JSONArray().also { array ->
                games.forEach { game ->
                    array.put(
                        JSONObject()
                            .put("name", game.name)
                            .put("platform", game.platform)
                            .put("description", game.description)
                            .put("url", game.url)
                            .put("coverUrl", game.coverUrl)
                            .put("coverLocalPath", "")
                            .put("sourcePostUrl", game.sourcePostUrl)
                            .put("downloadLinks", game.downloadLinks)
                            .put("blogImported", game.blogImported)
                            .put("platformManuallyCorrected", game.platformManuallyCorrected)
                            .put("isFavorite", game.isFavorite)
                            .put("bloggerPostId", game.bloggerPostId)
                            .put("bloggerHtml", game.bloggerHtml)
                            .put("playStatus", game.playStatus)
                            .put("genre", game.genre)
                            .put("releaseYear", game.releaseYear)
                    )
                }
            })
            .put(
                "appearance",
                JSONObject()
                    .put("darkMode", darkMode)
                    .put("accent", accent)
                    .put("animations", animations)
            )
        return payload.toString(2)
    }

    fun decode(raw: String): BackupSnapshot {
        val backup = JSONObject(raw)
        require(backup.optString("format") == FORMAT) { "ملف النسخة الاحتياطية غير صالح" }
        require(backup.optInt("version", 0) == VERSION) { "إصدار النسخة الاحتياطية غير مدعوم" }
        val array = backup.optJSONArray("games") ?: JSONArray()
        val restored = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val name = item.optString("name")
                if (name.isBlank()) continue
                add(
                    Game(
                        name,
                        item.optString("platform", "BLOG"),
                        item.optString("description"),
                        item.optString("url"),
                        item.optString("coverUrl"),
                        "",
                        item.optString("sourcePostUrl"),
                        item.optString("downloadLinks"),
                        item.optBoolean("blogImported"),
                        item.optBoolean("platformManuallyCorrected"),
                        item.optBoolean("isFavorite"),
                        item.optString("bloggerPostId"),
                        item.optString("bloggerHtml"),
                        item.optString("playStatus", ""),
                        item.optString("genre", ""),
                        item.optString("releaseYear", "")
                    )
                )
            }
        }
        val appearanceJson = backup.optJSONObject("appearance")
        val appearance = appearanceJson?.let {
            BackupAppearance(
                darkMode = it.optBoolean("darkMode", true),
                accent = it.optString("accent", "violet"),
                animations = it.optBoolean("animations", true)
            )
        }
        return BackupSnapshot(restored, appearance)
    }
}
