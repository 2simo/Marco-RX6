package com.marcorx6.library.ui

import android.content.Context
import android.widget.Toast
import com.marcorx6.library.data.Game
import java.util.concurrent.Executor

internal class BlogSyncController(
    private val context: Context,
    private val executor: Executor,
    private val isSyncing: () -> Boolean,
    private val setSyncing: (Boolean) -> Unit,
    private val fetch: () -> Pair<String, List<Game>>,
    private val postToUi: (() -> Unit) -> Unit,
    private val merge: (List<Game>) -> Pair<Int, Int>,
    private val setBlogId: (String) -> Unit,
    private val refreshAccess: () -> Unit,
    private val renderGames: () -> Unit,
    private val translate: (String) -> String
) {
    fun sync(showResult: Boolean) {
        if (isSyncing()) {
            if (showResult) Toast.makeText(context, translate("المزامنة جارية بالفعل"), Toast.LENGTH_SHORT).show()
            return
        }
        setSyncing(true)
        if (showResult) Toast.makeText(context, translate("جارٍ الاتصال بمدونة MARCO RX6..."), Toast.LENGTH_SHORT).show()
        executor.execute {
            try {
                val (blogId, importedGames) = fetch()
                postToUi {
                    setBlogId(blogId)
                    val result = merge(importedGames)
                    setSyncing(false)
                    refreshAccess()
                    renderGames()
                    if (showResult) {
                        Toast.makeText(
                            context,
                            "${translate("اكتملت المزامنة")}: ${translate("تمت إضافة")} ${result.first} ${translate("وتحديث")} ${result.second}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (_: Exception) {
                postToUi {
                    setSyncing(false)
                    if (showResult) {
                        Toast.makeText(
                            context,
                            translate("تعذرت المزامنة. تحقق من اتصال الإنترنت وحاول مجدداً."),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }
}
