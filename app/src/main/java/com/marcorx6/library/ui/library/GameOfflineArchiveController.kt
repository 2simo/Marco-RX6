package com.marcorx6.library.ui

import android.app.Activity
import android.net.Uri
import android.webkit.WebView
import android.widget.Toast
import java.io.File

internal class GameOfflineArchiveController(
    private val activity: Activity
) {
    fun buttonLabel(archiveFile: File): String =
        if (archiveFile.isFile) "قراءة دون إنترنت" else "حفظ دون إنترنت"

    fun openOrSave(
        webView: WebView,
        archiveFile: File,
        postToUi: (() -> Unit) -> Unit
    ) {
        if (archiveFile.isFile) {
            // Local-file access is enabled only for an explicitly opened saved archive.
            webView.settings.allowFileAccess = true
            webView.loadUrl(Uri.fromFile(archiveFile).toString())
            Toast.makeText(activity, "تم فتح النسخة المحفوظة", Toast.LENGTH_SHORT).show()
        } else {
            webView.saveWebArchive(archiveFile.absolutePath, false) { savedPath ->
                postToUi {
                    if (!savedPath.isNullOrBlank() && File(savedPath).isFile) {
                        Toast.makeText(activity, "تم حفظ المقال للقراءة دون اتصال", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(activity, "تعذر حفظ المقال. انتظر اكتمال تحميل الصور ثم حاول مجدداً.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
}
