package com.marcorx6.library.data

import android.content.ContentResolver
import android.net.Uri

/**
 * Handles reading and writing backup documents selected through Android's document picker.
 * Backup format validation and mapping remain centralized in BackupCodec.
 */
internal class BackupFileRepository(
    private val contentResolver: ContentResolver
) {
    fun write(uri: Uri, payload: String) {
        contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use {
            it.write(payload)
        } ?: throw IllegalStateException("تعذر إنشاء ملف النسخة الاحتياطية")
    }

    fun read(uri: Uri): BackupSnapshot {
        val raw = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
            it.readText()
        } ?: throw IllegalStateException("تعذر قراءة الملف")
        return BackupCodec.decode(raw)
    }
}
