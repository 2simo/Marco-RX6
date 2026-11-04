package com.marcorx6.library.ui

import android.app.Activity
import android.app.AlertDialog
import android.widget.Toast
import com.marcorx6.library.data.Game

internal class GameDeletionController(
    private val activity: Activity,
    private val isAdminSession: () -> Boolean,
    private val bloggerBlogId: () -> String,
    private val deletePost: (String) -> Unit,
    private val removeGame: (Game) -> Boolean,
    private val deleteCoverFileIfUnused: (String) -> Unit,
    private val renderGames: () -> Unit,
    private val executeIo: ((() -> Unit) -> Unit),
    private val postToUi: (() -> Unit) -> Unit,
    private val styleAlertDialog: (AlertDialog) -> Unit,
    private val translate: (String) -> String
) {
    fun delete(game: Game) {
        AlertDialog.Builder(activity)
            .setTitle(translate("حذف المقال"))
            .setMessage(translate("هل تريد حذف المقال") + " " + game.name + "؟")
            .setNegativeButton(translate("إلغاء"), null)
            .setPositiveButton(translate("حذف")) { _, _ ->
                if (!isAdminSession() || bloggerBlogId().isBlank() || game.bloggerPostId.isBlank()) {
                    Toast.makeText(
                        activity,
                        translate("يلزم تسجيل دخول المسؤول ومعرّف المقال في Blogger"),
                        Toast.LENGTH_LONG
                    ).show()
                    return@setPositiveButton
                }
                executeIo {
                    try {
                        deletePost(game.bloggerPostId)
                        postToUi {
                            if (removeGame(game)) {
                                deleteCoverFileIfUnused(game.coverLocalPath)
                            }
                            renderGames()
                            Toast.makeText(activity, translate("تم الحذف من Blogger"), Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        postToUi {
                            Toast.makeText(
                                activity,
                                e.message ?: translate("تعذر حذف المقال من Blogger"),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
            .create().apply {
                setOnShowListener {
                    styleAlertDialog(this)
                    getButton(AlertDialog.BUTTON_NEGATIVE).apply {
                        minHeight = (activity.resources.displayMetrics.density * 48).toInt()
                        contentDescription = translate("إلغاء حذف المقال")
                    }
                    getButton(AlertDialog.BUTTON_POSITIVE).apply {
                        minHeight = (activity.resources.displayMetrics.density * 48).toInt()
                        contentDescription = translate("تأكيد حذف المقال من Blogger")
                    }
                }
            }
            .show()
    }
}
