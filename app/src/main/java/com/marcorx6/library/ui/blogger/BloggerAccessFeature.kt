package com.marcorx6.library.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import java.util.concurrent.Executor

/** Owns the current-account Blogger permission check and its UI refresh behavior. */
internal class BloggerAccessFeature(
    context: Context,
    services: BloggerServices,
    executor: Executor,
    private val currentAccount: () -> GoogleSignInAccount?,
    private val currentBlogId: () -> String,
    setAdmin: (Boolean) -> Unit,
    postToUi: (() -> Unit) -> Unit,
    refreshUi: () -> Unit
) {
    private val controller = services.createAccessController(
        executor = executor,
        currentAccountId = { currentAccount()?.id },
        currentBlogId = currentBlogId,
        setAdmin = setAdmin,
        postToUi = postToUi,
        refreshUi = refreshUi,
        onDenied = {
            Toast.makeText(
                context,
                "هذا الحساب لا يملك صلاحية النشر أو التعديل في مدونة Blogger",
                Toast.LENGTH_LONG
            ).show()
        },
        onError = { message ->
            val title = "تعذر التحقق من صلاحيات Blogger"
            val detailsView = TextView(context).apply {
                text = message
                textIsSelectable = true
                setTextIsSelectable(true)
                setPadding(40, 24, 40, 24)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            val scrollView = ScrollView(context).apply {
                addView(detailsView)
            }
            AlertDialog.Builder(context)
                .setTitle(title)
                .setView(scrollView)
                .setPositiveButton("نسخ الخطأ") { _, _ ->
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Blogger API error", message))
                    Toast.makeText(context, "تم نسخ تفاصيل الخطأ", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("إغلاق", null)
                .show()
        }
    )

    fun refresh() {
        val account = currentAccount()
        controller.refresh(
            blogId = currentBlogId(),
            accountId = account?.id,
            hasAccount = account?.account != null
        )
    }
}
