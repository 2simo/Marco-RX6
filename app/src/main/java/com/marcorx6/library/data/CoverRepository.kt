package com.marcorx6.library.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Loads and caches cover bitmaps independently of UI widgets. */
internal class CoverRepository(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val worker: ExecutorService = Executors.newFixedThreadPool(3)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val bitmapCache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt().coerceAtLeast(1024)
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.byteCount / 1024).coerceAtLeast(1)
    }

    // Multiple visible rows can request the same cover at once. Share one decode/download
    // operation and fan its result out to every waiting ImageView callback.
    private val inFlightLock = Any()
    private val inFlightCallbacks = mutableMapOf<String, MutableList<(Bitmap?) -> Unit>>()

    fun stableCacheName(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** Returns a stable request key and delivers the decoded bitmap on the main thread. */
    fun load(localPath: String, imageUrl: String, onLoaded: (Bitmap?) -> Unit): String {
        val localFile = localPath.takeIf { it.isNotBlank() && File(it).isFile }?.let(::File)
        val bundledAsset = if (imageUrl.isNotBlank()) {
            "offline_images/${stableCacheName(imageUrl)}.webp"
        } else ""
        // Avoid opening APK assets on the caller thread; asset lookup belongs to the worker.
        val key = when {
            localFile != null -> "file:${localFile.absolutePath}|url:$imageUrl"
            bundledAsset.isNotBlank() -> "asset:$bundledAsset|url:$imageUrl"
            else -> "url:$imageUrl"
        }
        if (localFile == null && bundledAsset.isBlank() && imageUrl.isBlank()) {
            mainHandler.post { onLoaded(null) }
            return key
        }
        bitmapCache.get(key)?.let { cached ->
            mainHandler.post { onLoaded(cached) }
            return key
        }

        val shouldStartLoad = synchronized(inFlightLock) {
            val waiting = inFlightCallbacks[key]
            if (waiting != null) {
                waiting.add(onLoaded)
                false
            } else {
                inFlightCallbacks[key] = mutableListOf(onLoaded)
                true
            }
        }
        if (!shouldStartLoad) return key

        worker.execute {
            val bitmap = try {
                var decoded = localFile?.let { file ->
                    try { decodeFile(file) } catch (_: Exception) { null }
                }
                if (decoded == null && bundledAsset.isNotBlank()) {
                    decoded = try { decodeAsset(bundledAsset) } catch (_: Exception) { null }
                }
                if (decoded == null && imageUrl.isNotBlank()) {
                    val folder = File(appContext.cacheDir, "cover_cache").apply { mkdirs() }
                    val cached = File(folder, stableCacheName(imageUrl) + ".img")
                    val fresh = cached.isFile &&
                        System.currentTimeMillis() - cached.lastModified() < 7L * 24L * 60L * 60L * 1000L
                    if (fresh) decoded = try { decodeFile(cached) } catch (_: Exception) { null }
                    if (decoded == null) {
                        cached.delete()
                        downloadToCache(imageUrl, cached)
                        decoded = decodeFile(cached)
                    }
                }
                decoded?.also { bitmapCache.put(key, it) }
            } catch (_: Exception) {
                null
            }
            mainHandler.post {
                val callbacks = synchronized(inFlightLock) {
                    inFlightCallbacks.remove(key)?.toList().orEmpty()
                }
                callbacks.forEach { callback -> callback(bitmap) }
            }
        }
        return key
    }

    private fun decodeFile(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > 900 || bounds.outHeight / sample > 1100) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private fun decodeAsset(assetPath: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        appContext.assets.open(assetPath).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > 900 || bounds.outHeight / sample > 1100) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return appContext.assets.open(assetPath).use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun trimDiskCache(folder: File) {
        val files = folder.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") }
            ?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        val limit = 64L * 1024L * 1024L
        for (file in files) {
            if (total <= limit) break
            val length = file.length()
            if (file.delete()) total -= length
        }
    }

    private fun downloadToCache(imageUrl: String, destination: File) {
        val connection = URL(imageUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 8000
        connection.readTimeout = 12000
        connection.instanceFollowRedirects = true
        connection.useCaches = true
        connection.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
        )
        connection.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
        val temporary = File(destination.parentFile, destination.name + ".tmp")
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${connection.responseCode}")
            }
            if (connection.contentLength > 12 * 1024 * 1024) {
                throw IllegalStateException("Cover too large")
            }
            connection.inputStream.use { input ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > 12L * 1024L * 1024L) {
                            throw IllegalStateException("Cover too large")
                        }
                        output.write(buffer, 0, count)
                    }
                }
            }
            if (destination.exists()) destination.delete()
            if (!temporary.renameTo(destination)) {
                throw IllegalStateException("Cache write failed")
            }
            trimDiskCache(destination.parentFile ?: appContext.cacheDir)
        } finally {
            temporary.delete()
            connection.disconnect()
        }
    }

    override fun close() {
        worker.shutdownNow()
    }
}
