package com.marcorx6.library.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.app.DownloadManager

internal data class StoredDownload(
    val id: Long,
    val url: String,
    val title: String,
    val userAgent: String,
    val cookie: String,
    val referrer: String,
    val mimeType: String,
    val status: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val etag: String,
    val lastModified: String,
    val localPath: String,
    val error: String
)

internal class DownloadDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "marco_downloads.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE downloads (
                id INTEGER PRIMARY KEY,
                url TEXT NOT NULL,
                title TEXT NOT NULL,
                user_agent TEXT NOT NULL DEFAULT '',
                cookie TEXT NOT NULL DEFAULT '',
                referrer TEXT NOT NULL DEFAULT '',
                mime_type TEXT NOT NULL DEFAULT '',
                status INTEGER NOT NULL,
                downloaded INTEGER NOT NULL DEFAULT 0,
                total INTEGER NOT NULL DEFAULT -1,
                etag TEXT NOT NULL DEFAULT '',
                last_modified TEXT NOT NULL DEFAULT '',
                local_path TEXT NOT NULL DEFAULT '',
                error TEXT NOT NULL DEFAULT ''
            )"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    @Synchronized
    fun insert(id: Long, url: String, title: String, userAgent: String, cookie: String, referrer: String, mime: String) {
        val values = ContentValues().apply {
            put("id", id); put("url", url); put("title", title)
            put("user_agent", userAgent); put("cookie", cookie); put("referrer", referrer)
            put("mime_type", mime); put("status", DownloadManager.STATUS_PENDING)
        }
        writableDatabase.insertOrThrow("downloads", null, values)
    }

    @Synchronized
    fun get(id: Long): StoredDownload? =
        readableDatabase.query("downloads", null, "id=?", arrayOf(id.toString()), null, null, null)
            .use { if (it.moveToFirst()) it.toRecord() else null }

    @Synchronized
    fun all(): List<StoredDownload> =
        readableDatabase.query("downloads", null, "status<>?", arrayOf(STATUS_CANCELLED.toString()), null, null, "id DESC")
            .use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toRecord()) } }

    @Synchronized
    fun status(id: Long, status: Int, error: String = "") {
        writableDatabase.update("downloads", ContentValues().apply {
            put("status", status); put("error", error)
        }, "id=?", arrayOf(id.toString()))
    }

    @Synchronized
    fun renameTitle(id: Long, title: String) {
        writableDatabase.update("downloads", ContentValues().apply { put("title", title) }, "id=?", arrayOf(id.toString()))
    }

    @Synchronized
    fun progress(id: Long, downloaded: Long, total: Long, etag: String, modified: String) {
        writableDatabase.update("downloads", ContentValues().apply {
            put("downloaded", downloaded); put("total", total)
            put("etag", etag); put("last_modified", modified)
        }, "id=?", arrayOf(id.toString()))
    }

    @Synchronized
    fun complete(id: Long, path: String, mime: String) {
        writableDatabase.update("downloads", ContentValues().apply {
            put("local_path", path); put("mime_type", mime)
            put("status", DownloadManager.STATUS_SUCCESSFUL); put("error", "")
        }, "id=?", arrayOf(id.toString()))
    }

    @Synchronized
    fun cancel(id: Long) {
        writableDatabase.update("downloads", ContentValues().apply {
            put("status", STATUS_CANCELLED)
        }, "id=?", arrayOf(id.toString()))
    }

    @Synchronized
    fun delete(id: Long) {
        writableDatabase.delete("downloads", "id=?", arrayOf(id.toString()))
    }

    private fun android.database.Cursor.toRecord() = StoredDownload(
        id = getLong(getColumnIndexOrThrow("id")),
        url = getString(getColumnIndexOrThrow("url")),
        title = getString(getColumnIndexOrThrow("title")),
        userAgent = getString(getColumnIndexOrThrow("user_agent")),
        cookie = getString(getColumnIndexOrThrow("cookie")),
        referrer = getString(getColumnIndexOrThrow("referrer")),
        mimeType = getString(getColumnIndexOrThrow("mime_type")),
        status = getInt(getColumnIndexOrThrow("status")),
        downloadedBytes = getLong(getColumnIndexOrThrow("downloaded")),
        totalBytes = getLong(getColumnIndexOrThrow("total")),
        etag = getString(getColumnIndexOrThrow("etag")),
        lastModified = getString(getColumnIndexOrThrow("last_modified")),
        localPath = getString(getColumnIndexOrThrow("local_path")),
        error = getString(getColumnIndexOrThrow("error"))
    )

    companion object {
        const val STATUS_CANCELLED = 6
        @Volatile private var instance: DownloadDatabase? = null
        fun get(context: Context): DownloadDatabase =
            instance ?: synchronized(this) {
                instance ?: DownloadDatabase(context).also { instance = it }
            }
    }
}
