package com.marcorx6.library

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Local-only diagnostic log. Keeps a small rolling history and never uploads data.
 * Call log() with operational events; uncaught crashes are captured automatically.
 */
object AppDiagnostics {
    private const val MAX_FILE_BYTES = 512 * 1024L
    private const val MAX_LOG_FILES = 3
    private const val LOG_NAME = "marco-rx6.log"
    private val lock = Any()
    private val fileLock = Any()
    private val currentScreen = AtomicReference("Application")
    private val lastAction = AtomicReference("none")
    private val droppedEntries = java.util.concurrent.atomic.AtomicLong(0L)
    private val writer = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue<Runnable>(1024),
        { task -> Thread(task, "marco-diagnostics-writer").apply { isDaemon = true } },
        { task, executor ->
            if (!executor.isShutdown) {
                executor.queue.poll()
                droppedEntries.incrementAndGet()
                executor.queue.offer(task)
            } else droppedEntries.incrementAndGet()
        }
    )
    @Volatile private var initialized = false
    @Volatile private var appContext: Context? = null
    @Volatile private var previousHandler: Thread.UncaughtExceptionHandler? = null

    fun install(context: Context) {
        if (initialized) return
        synchronized(lock) {
            if (initialized) return
            appContext = context.applicationContext
            previousHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, error ->
                runCatching { flush(1800L) }
                log("FATAL", "UncaughtException", "Uncaught exception on ${thread.name}", error)
                runCatching { flush(1800L) }
                val previous = previousHandler
                if (previous != null) previous.uncaughtException(thread, error)
                else {
                    android.os.Process.killProcess(android.os.Process.myPid())
                    kotlin.system.exitProcess(10)
                }
            }
            initialized = true
        }
        val packageInfo = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        log(
            "INFO",
            "Application",
            "Diagnostic logging initialized appVersion=${packageInfo?.versionName ?: "unknown"} " +
                "versionCode=${if (android.os.Build.VERSION.SDK_INT >= 28) packageInfo?.longVersionCode ?: -1L else packageInfo?.versionCode ?: -1} " +
                "device=${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} " +
                "android=${android.os.Build.VERSION.RELEASE} sdk=${android.os.Build.VERSION.SDK_INT} " +
                "targetSdk=${context.applicationInfo.targetSdkVersion} pid=${android.os.Process.myPid()}"
        )
    }

    /**
     * Enqueue ordinary diagnostics off the main thread. Fatal entries are written
     * synchronously after draining earlier events so the last crash is not lost.
     */
    fun log(level: String, tag: String, message: String, error: Throwable? = null) {
        val context = appContext ?: return
        val details = if (error == null) "" else {
            val writer = StringWriter()
            error.printStackTrace(PrintWriter(writer))
            "\n" + writer.toString().take(18000)
        }
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val safeTag = sanitize(tag).take(80)
        val dropped = droppedEntries.getAndSet(0L)
        val contextSnapshot = "screen=${sanitize(currentScreen.get()).take(100)} " +
            "action=${sanitize(lastAction.get()).take(140)} thread=${Thread.currentThread().name}" +
            if (dropped > 0L) " droppedLogEntries=$dropped" else ""
        val safeMessage = sanitize("$contextSnapshot ${message + details}").take(20000)
        val line = "$stamp [$level] [$safeTag] $safeMessage\n"
        if (level.equals("FATAL", ignoreCase = true)) {
            writeLine(context, line)
            return
        }
        runCatching { writer.execute { writeLine(context, line) } }
            .onFailure { droppedEntries.incrementAndGet() }
    }

    /** Records the semantic screen name instead of only a generic View class. */
    fun setScreen(name: String) {
        val normalized = sanitize(name).take(100).ifBlank { "Unknown" }
        currentScreen.set(normalized)
        log("INFO", "SCREEN", "Screen changed")
    }

    /** Breadcrumb for a user action; do not pass text fields, credentials or message bodies. */
    fun recordAction(action: String) {
        val normalized = sanitize(action).take(140).ifBlank { "Unknown action" }
        lastAction.set(normalized)
        log("DEBUG", "USER_ACTION", "Action started")
    }

    private fun writeLine(context: Context, line: String) {
        synchronized(fileLock) {
            runCatching {
                val directory = File(context.filesDir, "diagnostics")
                if (!directory.exists()) directory.mkdirs()
                val current = File(directory, LOG_NAME)
                val bytes = line.toByteArray(Charsets.UTF_8)
                if (current.exists() && current.length() + bytes.size > MAX_FILE_BYTES) {
                    rotate(directory, current)
                }
                FileOutputStream(current, true).use { it.write(bytes) }
            }
        }
    }

    /** Waits until all previously queued log events have reached disk. */
    fun flush(timeoutMs: Long = 1500L): Boolean {
        if (writer.isShutdown) return false
        val latch = CountDownLatch(1)
        return try {
            writer.execute { latch.countDown() }
            latch.await(timeoutMs.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
            false
        }
    }

    private fun sanitize(value: String): String {
        var result = value
        result = result.replace(
            Regex("(?i)(access[_-]?token|refresh[_-]?token|id[_-]?token|authorization|password|passwd|api[_-]?key)\\s*[\"']?\\s*[:=]\\s*[\"']?[^\\s,\"';}]+"),
            "$1=[REDACTED]"
        )
        result = result.replace(
            Regex("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", RegexOption.IGNORE_CASE),
            "[EMAIL]"
        )
        result = result.replace(
            Regex("(?i)\\b(uid|userId|accountId)\\s*[\"']?\\s*[:=]\\s*[\"']?[^\\s,\"';}]+"),
            "$1=[REDACTED]"
        )
        result = result.replace(
            Regex("(?i)([?&](?:token|key|auth|signature)=)[^&\\s]+"),
            "$1[REDACTED]"
        )
        result = result.replace(Regex("(?i)\\bBearer\\s+[^\\s,;]+"), "Bearer [REDACTED]")
        return result
    }

    private fun rotate(directory: File, current: File) {
        File(directory, "$LOG_NAME.${MAX_LOG_FILES - 1}").delete()
        for (index in MAX_LOG_FILES - 2 downTo 1) {
            val source = File(directory, "$LOG_NAME.$index")
            if (source.exists()) source.renameTo(File(directory, "$LOG_NAME.${index + 1}"))
        }
        if (current.exists()) current.renameTo(File(directory, "$LOG_NAME.1"))
    }

    fun createExport(context: Context): File {
        log("INFO", "Diagnostics", "User requested a diagnostic export")
        flush(2500L)
        val source = File(context.filesDir, "diagnostics")
        val outputDirectory = File(context.cacheDir, "diagnostic_exports")
        if (!outputDirectory.exists()) outputDirectory.mkdirs()
        val output = File(outputDirectory, "MARCO_RX6_diagnostics.zip")
        synchronized(fileLock) {
            ZipOutputStream(FileOutputStream(output)).use { zip ->
                source.listFiles()
                    ?.filter { it.isFile && (it.name == LOG_NAME || it.name.startsWith("$LOG_NAME.")) }
                    ?.sortedByDescending { it.lastModified() }
                    ?.forEach { file ->
                        file.inputStream().use { input ->
                            zip.putNextEntry(ZipEntry(file.name))
                            input.copyTo(zip)
                            zip.closeEntry()
                        }
                    }
            }
        }
        log(
            "INFO",
            "Diagnostics",
            "Diagnostic export created bytes=${output.length()} entries=${source.listFiles()?.count { it.isFile && (it.name == LOG_NAME || it.name.startsWith("$LOG_NAME.")) } ?: 0}"
        )
        return output
    }
}
