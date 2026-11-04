package com.marcorx6.library.ui

import android.app.Activity
import android.widget.TextView
import com.marcorx6.library.data.Game

internal class GameFavoriteToggleFactory(
    private val activity: Activity,
    private val games: MutableList<Game>,
    private val dp: (Int) -> Int,
    private val white: () -> Int,
    private val red: () -> Int,
    private val saveGames: () -> Unit,
    private val renderGames: () -> Unit
) {
    fun create(game: Game): TextView {
        return TextView(activity).apply {
            text = if (game.isFavorite) "★" else "☆"
            contentDescription = game.name + ", " + if (game.isFavorite) "إزالة من المفضلة" else "إضافة إلى المفضلة"
            textSize = 25f
            gravity = android.view.Gravity.CENTER
            setTextColor(if (game.isFavorite) android.graphics.Color.rgb(92, 183, 255) else white())
            if (android.os.Build.VERSION.SDK_INT >= 23) foreground = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(android.graphics.Color.argb(82, 83, 177, 255)),
                null,
                android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE)
            )
            setPadding(dp(8), dp(4), dp(8), dp(4))
            minWidth = dp(48)
            minHeight = dp(48)
            setOnClickListener {
                val index = games.indexOfFirst { it.name == game.name && it.url == game.url }
                if (index >= 0) {
                    games[index] = games[index].copy(isFavorite = !games[index].isFavorite)
                    saveGames()
                    text = if (games[index].isFavorite) "★" else "☆"
                    contentDescription = games[index].name + ", " + if (games[index].isFavorite) "إزالة من المفضلة" else "إضافة إلى المفضلة"
                    setTextColor(if (games[index].isFavorite) android.graphics.Color.rgb(92, 183, 255) else white())
                    renderGames()
                }
            }
        }
    }
}
