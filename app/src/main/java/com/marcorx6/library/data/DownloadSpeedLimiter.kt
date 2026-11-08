package com.marcorx6.library.data

import java.util.concurrent.TimeUnit

/**
 * Aggregate byte-rate limiter shared by all connections of one download.
 * A zero value disables throttling.
 */
internal class DownloadSpeedLimiter(private val bytesPerSecond: Long) {
    private var nextAvailableNanos = 0L

    @Synchronized
    fun acquire(bytes: Int) {
        if (bytesPerSecond <= 0L || bytes <= 0) return
        val now = System.nanoTime()
        val scheduled = maxOf(now, nextAvailableNanos)
        val duration = (bytes.toLong() * 1_000_000_000L / bytesPerSecond).coerceAtLeast(1L)
        nextAvailableNanos = scheduled + duration
        val waitNanos = scheduled - now
        if (waitNanos > 0L) TimeUnit.NANOSECONDS.sleep(waitNanos)
    }
}
