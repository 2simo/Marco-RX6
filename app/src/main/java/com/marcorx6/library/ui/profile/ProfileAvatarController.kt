package com.marcorx6.library.ui

import android.net.Uri
import java.util.concurrent.Executor

internal class ProfileAvatarController(
    private val executor: Executor,
    private val saveAvatar: (Uri) -> String,
    private val persistPath: (String) -> Unit,
    private val postToUi: (() -> Unit) -> Unit,
    private val onSaved: () -> Unit,
    private val onError: (String) -> Unit
) {
    fun handle(uri: Uri) {
        executor.execute {
            try {
                val path = saveAvatar(uri)
                persistPath(path)
                postToUi(onSaved)
            } catch (error: Exception) {
                postToUi { onError(error.message ?: error.javaClass.simpleName) }
            }
        }
    }
}