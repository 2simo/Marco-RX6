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
    val error: String,
    val sourcePostUrl: String
)

internal class DownloadDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "marco_downloads.db", null, 2) {

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
                error TEXT NOT NULL DEFAULT '',
                source_post_url TEXT NOT NULL DEFAULT ''
            )"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("ALTER TABLE downloads ADD COLUMN source_post_url TEXT NOT NULL DEFAULT ''")
    }

    @Synchronized
    fun insert(id: Long, url: String, title: String, userAgent: String, cookie: String, referrer: String, mime: String, sourcePostUrl: String = "") {
        val values = ContentValues().apply {
            put("id", id); put("url", url); put("title", title)
            put("user_agent", userAgent); put("cookie", cookie); put("referrer", referrer)
            put("mime_type", mime); put("status", DownloadManager.STATUS_PENDING)
            put("source_post_url", sourcePostUrl)
        }
        writableDatabase.insertOrThrow("downloads", null, values)
    }

    @Synchronized
    fun findActiveDuplicate(url: String, sourcePostUrl: String): StoredDownload? {
        val activeStatuses = intArrayOf(
            DownloadManager.STATUS_PENDING,
            DownloadManager.STATUS_RUNNING,
            STATUS_SCHEDULED,
            DownloadManager.STATUS_PAUSED
        )
        val byUrl = readableDatabase.query(
            "downloads", null,
            "url=? AND status IN (?,?,?,?)",
            arrayOf(url, *activeStatuses.map(Int::toString).toTypedArray()),
            null, null, "id DESC", "1"
        ).use { if (it.moveToFirst()) it.toRecord() else null }
        if (byUrl != null) return byUrl
        if (sourcePostUrl.isBlank()) return null
        return readableDatabase.query(
            "downloads", null,
            "source_post_url=? AND status IN (?,?,?,?)",
            arrayOf(sourcePostUrl, *activeStatuses.map(Int::toString).toTypedArray()),
            null, null, "id DESC", "1"
        ).use { if (it.moveToFirst()) it.toRecord() else null }
    }


    /**
     * Atomically reserves free download slots before WorkManager is notified.
     * STATUS_SCHEDULED counts as an occupied slot, preventing concurrent enqueue
     * calls from exceeding the user's configured limit.
     */
    @Synchronized
    fun claimPending(maxConcurrent: Int): List<Long> {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val active = db.rawQuery(
                "SELECT COUNT(*) FROM downloads WHERE status IN (?,?)",
                arrayOf(DownloadManager.STATUS_RUNNING.toString(), STATUS_SCHEDULED.toString())
            ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
            val available = (maxConcurrent.coerceIn(1, 5) - active).coerceAtLeast(0)
            if (available == 0) {
                db.setTransactionSuccessful()
                return emptyList()
            }
            val ids = db.query(
                "downloads", arrayOf("id"), "status=?",
                arrayOf(DownloadManager.STATUS_PENDING.toString()),
                null, null, "id ASC", available.toString()
            ).use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getLong(0)) }
            }
            ids.forEach { id ->
                db.update(
                    "downloads",
                    ContentValues().apply { put("status", STATUS_SCHEDULED) },
                    "id=? AND status=?",
                    arrayOf(id.toString(), DownloadManager.STATUS_PENDING.toString())
                )
            }
            db.setTransactionSuccessful()
            return ids
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun markRunning(id: Long): Boolean {
        val changed = writableDatabase.update(
            "downloads",
            ContentValues().apply { put("status", DownloadManager.STATUS_RUNNING) },
            "id=? AND status IN (?,?,?)",
            arrayOf(
                id.toString(),
                DownloadManager.STATUS_PENDING.toString(),
                STATUS_SCHEDULED.toString(),
                DownloadManager.STATUS_RUNNING.toString()
            )
        )
        return changed > 0
    }

    @Synchronized
    fun nextId(): Long {
        val current = readableDatabase.rawQuery("SELECT MAX(id) FROM downloads", null).use {
            if (it.moveToFirst()) it.getLong(0) else 0L
        }
        return maxOf(System.currentTimeMillis(), current + 1L, 1L)
    }

    @Synchronized
    fun findFailedBySource(sourcePostUrl: String): StoredDownload? {
        if (sourcePostUrl.isBlank()) return null
        return readableDatabase.query("downloads", null, "source_post_url=? AND status=?", arrayOf(sourcePostUrl, DownloadManager.STATUS_FAILED.toString()), null, null, "id DESC", "1")
            .use { if (it.moveToFirst()) it.toRecord() else null }
    }

    @Synchronized
    fun refreshSource(id: Long, url: String, userAgent: String, cookie: String, referrer: String, mime: String) {
        writableDatabase.update("downloads", ContentValues().apply {
            put("url", url); put("user_agent", userAgent); put("cookie", cookie)
            put("referrer", referrer); put("mime_type", mime)
            put("status", DownloadManager.STATUS_PENDING); put("error", "")
            put("downloaded", 0L); put("total", -1L)
            put("etag", ""); put("last_modified", "")
            put("local_path", "")
        }, "id=?", arrayOf(id.toString()))
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
    fun pauseAllActive(): List<Long> {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val ids = db.query(
                "downloads", arrayOf("id"), "status IN (?,?,?)",
                arrayOf(
                    DownloadManager.STATUS_PENDING.toString(),
                    DownloadManager.STATUS_RUNNING.toString(),
                    STATUS_SCHEDULED.toString()
                ), null, null, "id ASC"
            ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getLong(0)) } }
            db.update(
                "downloads",
                ContentValues().apply { put("status", DownloadManager.STATUS_PAUSED) },
                "status IN (?,?,?)",
                arrayOf(
                    DownloadManager.STATUS_PENDING.toString(),
                    DownloadManager.STATUS_RUNNING.toString(),
                    STATUS_SCHEDULED.toString()
                )
            )
            db.setTransactionSuccessful()
            return ids
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun pausedOrFailedIds(): List<Long> =
        readableDatabase.query(
            "downloads", arrayOf("id"), "status IN (?,?)",
            arrayOf(DownloadManager.STATUS_PAUSED.toString(), DownloadManager.STATUS_FAILED.toString()),
            null, null, "id ASC"
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getLong(0)) } }

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
        error = getString(getColumnIndexOrThrow("error")),
        sourcePostUrl = getString(getColumnIndexOrThrow("source_post_url"))
    )

    companion object {
        const val STATUS_CANCELLED = 6
        const val STATUS_SCHEDULED = 7
        @Volatile private var instance: DownloadDatabase? = null
        fun get(context: Context): DownloadDatabase =
            instance ?: synchronized(this) {
                instance ?: DownloadDatabase(context).also { instance = it }
            }
    }
}
