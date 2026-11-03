package com.marcorx6.library.ui

import android.content.Context
import android.widget.Toast
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import java.util.concurrent.Executor

/** Owns the current-account Blogger permission check and its UI refresh behavior. */
internal class BloggerAccessFeature(
    context: Context,
    services: BloggerServices,
    executor: Executor,
    currentAccount: () -> GoogleSignInAccount?,
    currentBlogId: () -> String,
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
