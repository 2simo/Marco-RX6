package com.marcorx6.library.network

import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Handles Google Drive cover uploads and verifies the resulting public image URL. */
internal class GoogleDriveClient : DriveCoverUploader {

    override fun uploadCover(file: File, accessToken: String): String {
        require(accessToken.isNotBlank()) { "رمز الوصول إلى Google Drive غير متاح" }
        if (!file.exists() || file.length() == 0L) {
            throw IllegalStateException("ملف الصورة فارغ أو غير موجود")
        }
        if (file.length() > MAX_UPLOAD_BYTES) {
            throw IllegalStateException("حجم صورة الغلاف يتجاوز 10 ميغابايت")
        }

        val mime = detectImageMimeType(file)
        val extension = when (mime) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            else -> "jpg"
        }
        val boundary = "MarcoRX6" + System.currentTimeMillis()
        val metadata = JSONObject()
            .put("name", "MARCO_RX6_" + file.nameWithoutExtension + "." + extension)
        val metadataPart = (
            "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n" +
                metadata.toString() +
                "\r\n--$boundary\r\nContent-Type: $mime\r\n\r\n"
            ).toByteArray(Charsets.UTF_8)
        val closingPart = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)
        val multipartLength = metadataPart.size.toLong() + file.length() + closingPart.size

        val upload = URL(UPLOAD_URL).openConnection() as HttpURLConnection
        val uploadBody: String
        val fileId: String
        try {
            upload.requestMethod = "POST"
            upload.connectTimeout = 30_000
            upload.readTimeout = 30_000
            upload.doOutput = true
            upload.setFixedLengthStreamingMode(multipartLength)
            upload.setRequestProperty("Authorization", "Bearer $accessToken")
            upload.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
            BufferedOutputStream(upload.outputStream).use { output ->
                output.write(metadataPart)
                file.inputStream().use { input -> input.copyTo(output) }
                output.write(closingPart)
            }
            val code = upload.responseCode
            uploadBody = readResponse(upload, code)
            if (code !in 200..299) {
                throw IllegalStateException("فشل رفع الصورة إلى Google Drive ($code): $uploadBody")
            }
            fileId = JSONObject(uploadBody).optString("id")
            if (fileId.isBlank()) {
                throw IllegalStateException("لم يرجع Google Drive معرّف الصورة")
            }
        } finally {
            upload.disconnect()
        }

        val permission = URL("https://www.googleapis.com/drive/v3/files/$fileId/permissions")
            .openConnection() as HttpURLConnection
        try {
            permission.requestMethod = "POST"
            permission.connectTimeout = 20_000
            permission.readTimeout = 20_000
            permission.doOutput = true
            permission.setRequestProperty("Authorization", "Bearer $accessToken")
            permission.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            permission.outputStream.use {
                it.write(
                    JSONObject().put("type", "anyone").put("role", "reader")
                        .toString().toByteArray(Charsets.UTF_8)
                )
            }
            val code = permission.responseCode
            val body = readResponse(permission, code)
            if (code !in 200..299) {
                throw IllegalStateException(
                    "تم رفع الصورة لكن تعذر جعلها متاحة للموقع ($code): $body"
                )
            }
        } finally {
            permission.disconnect()
        }

        val publicImageUrl = "https://lh3.googleusercontent.com/d/$fileId"
        verifyPublicImageUrl(publicImageUrl)
        return publicImageUrl
    }

    private fun detectImageMimeType(file: File): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val detected = bounds.outMimeType?.lowercase(Locale.ROOT).orEmpty()
        return when (detected) {
            "image/png", "image/webp", "image/gif", "image/jpeg" -> detected
            else -> when (file.extension.lowercase(Locale.ROOT)) {
                "png" -> "image/png"
                "webp" -> "image/webp"
                "gif" -> "image/gif"
                else -> "image/jpeg"
            }
        }
    }

    private fun verifyPublicImageUrl(imageUrl: String) {
        var lastFailure = "رابط الصورة لا يعيد ملف صورة مباشراً"
        repeat(3) { attempt ->
            val connection = URL(imageUrl).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 12_000
                connection.readTimeout = 15_000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("Range", "bytes=0-31")
                connection.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36"
                )
                val code = connection.responseCode
                val mime = connection.contentType.orEmpty()
                    .substringBefore(";").lowercase(Locale.ROOT)
                if (code in 200..299 && mime.startsWith("image/")) return
                lastFailure =
                    "تعذر التحقق من رابط الصورة (HTTP $code، النوع: ${mime.ifBlank { "غير معروف" }})"
            } catch (e: Exception) {
                lastFailure = e.message ?: lastFailure
            } finally {
                connection.disconnect()
            }
            if (attempt < 2) Thread.sleep(900L * (attempt + 1))
        }
        throw IllegalStateException(lastFailure)
    }

    private fun readResponse(connection: HttpURLConnection, code: Int): String {
        return (if (code in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
    }

    private companion object {
        const val MAX_UPLOAD_BYTES = 10L * 1024L * 1024L
        const val UPLOAD_URL =
            "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id"
    }
}
