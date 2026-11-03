package com.marcorx6.library.ui

import android.app.Activity
import android.app.AlertDialog
import com.marcorx6.library.data.GameLibraryStore
import com.marcorx6.library.data.Game

/** Presents platform correction and delegates persistence to GameLibraryStore. */
internal class PlatformCorrectionController(
    private val activity: Activity,
    private val store: GameLibraryStore,
    private val onChanged: () -> Unit,
    private val styleDialog: (AlertDialog) -> Unit
) {
    fun show(game: Game) {
        val choices = arrayOf("PS2", "PS3", "PS4")
        AlertDialog.Builder(activity)
            .setTitle("اختر المنصة")
            .setItems(choices) { _, which ->
                if (store.correctPlatform(game.sourcePostUrl, choices[which])) onChanged()
            }
            .setNegativeButton("إلغاء", null)
            .show().also(styleDialog)
    }
}
