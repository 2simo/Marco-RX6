package com.marcorx6.library.ui

import android.app.Activity
import android.graphics.drawable.Drawable
import android.widget.TextView
import android.app.AlertDialog
import com.marcorx6.library.data.Game

internal class GameWatchlistViewFactory(
    private val activity: Activity,
    private val games: MutableList<Game>,
    private val dp: (Int) -> Int,
    private val rounded: (Int, Int) -> Drawable,
    private val white: () -> Int,
    private val panel: () -> Int,
    private val saveGames: () -> Unit,
    private val renderGames: () -> Unit,
    private val styleAlertDialog: (AlertDialog) -> Unit,
    private val translate: (String) -> String
) {
    fun create(game: Game): TextView {
        val watchlistNames = arrayOf(translate("بدون حالة"), translate("أريد لعبها"), translate("ألعبها حالياً"), translate("أنهيتها"))
        val watchlistKeys = arrayOf("", "WANT_TO_PLAY", "PLAYING", "COMPLETED")
        fun watchlistLabel(status: String) = when (status) {
            "WANT_TO_PLAY" -> translate("أريد لعبها")
            "PLAYING" -> translate("ألعبها حالياً")
            "COMPLETED" -> translate("أنهيتها")
            else -> translate("إضافة إلى قائمة المتابعة")
        }

        return TextView(activity).apply {
            text = "＋  " + watchlistLabel(game.playStatus)
            textSize = 11f
            setTextColor(white())
            gravity = android.view.Gravity.CENTER
            background = rounded(panel(), 11)
            setPadding(dp(11), dp(9), dp(11), dp(9))
            setOnClickListener {
                val current = watchlistKeys.indexOf(game.playStatus).coerceAtLeast(0)
                AlertDialog.Builder(activity)
                    .setTitle(translate("قائمة متابعة الألعاب"))
                    .setSingleChoiceItems(watchlistNames, current) { dialog, which ->
                        val index = games.indexOfFirst { it.name == game.name && it.url == game.url }
                        if (index >= 0) {
                            games[index] = games[index].copy(playStatus = watchlistKeys[which])
                            saveGames()
                            text = "＋  " + watchlistLabel(watchlistKeys[which])
                            renderGames()
                        }
                        dialog.dismiss()
                    }
                    .setNegativeButton(translate("إغلاق"), null)
                    .show().also(styleAlertDialog)
            }
        }
    }
}
