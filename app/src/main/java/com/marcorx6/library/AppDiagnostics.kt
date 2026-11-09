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

/**
 * Local-only diagnostic log. Keeps a small rolling history and never uploads data.
 * Call log() with operational events; uncaught crashes are captured automatically.
 */
object AppDiagnostics {
    private const val MAX_FILE_BYTES = 512 * 1024L
    private const val MAX_LOG_FILES = 3
    private const val LOG_NAME = "marco-rx6.log"
    private val lock = Any()
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
                log("FATAL", "UncaughtException", "Uncaught exception on ${thread.name}", error)
                val previous = previousHandler
                if (previous != null) previous.uncaughtException(thread, error)
                else {
                    android.os.Process.killProcess(android.os.Process.myPid())
                    kotlin.system.exitProcess(10)
                }
            }
            initialized = true
        }
        log("INFO", "Application", "Diagnostic logging initialized")
    }

    fun log(level: String, tag: String, message: String, error: Throwable? = null) {
        val context = appContext ?: return
        synchronized(lock) {
            runCatching {
                val directory = File(context.filesDir, "diagnostics")
                if (!directory.exists()) directory.mkdirs()
                val current = File(directory, LOG_NAME)
                val details = if (error == null) "" else {
                    val writer = StringWriter()
                    error.printStackTrace(PrintWriter(writer))
                    "\n" + writer.toString().take(18000)
                }
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
                val safeTag = sanitize(tag).take(80)
                val safeMessage = sanitize(message + details).take(20000)
                val line = "$stamp [$level] [$safeTag] $safeMessage\n"
                val bytes = line.toByteArray(Charsets.UTF_8)
                if (current.exists() && current.length() + bytes.size > MAX_FILE_BYTES) rotate(directory, current)
                FileOutputStream(current, true).use { it.write(bytes) }
            }
        }
    }

    private fun sanitize(value: String): String {
        var result = value
        result = result.replace(
            Regex("(?i)(access[_-]?token|refresh[_-]?token|id[_-]?token|authorization|password|passwd|api[_-]?key)\\s*[:=]\\s*[^\\s,;]+"),
            "$1=[REDACTED]"
        )
        result = result.replace(
            Regex("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", RegexOption.IGNORE_CASE),
            "[EMAIL]"
        )
        return result
    }

    private fun rotate(directory: File, current: File) {
        File(directory, "$LOG_NAME.$MAX_LOG_FILES").delete()
        for (index in MAX_LOG_FILES - 1 downTo 1) {
            val source = File(directory, "$LOG_NAME.$index")
            if (source.exists()) source.renameTo(File(directory, "$LOG_NAME.${index + 1}"))
        }
        if (current.exists()) current.renameTo(File(directory, "$LOG_NAME.1"))
    }

    fun createExport(context: Context): File {
        log("INFO", "Diagnostics", "User requested a diagnostic export")
        val source = File(context.filesDir, "diagnostics")
        val outputDirectory = File(context.cacheDir, "diagnostic_exports")
        if (!outputDirectory.exists()) outputDirectory.mkdirs()
        val output = File(outputDirectory, "MARCO_RX6_diagnostics.zip")
        synchronized(lock) {
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
        return output
    }
}
