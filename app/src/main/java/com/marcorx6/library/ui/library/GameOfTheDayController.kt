package com.marcorx6.library.ui

import android.app.Activity
import android.app.AlertDialog
import android.graphics.drawable.Drawable
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.marcorx6.library.data.Game
import java.util.Calendar

internal class GameOfTheDayController(
    private val activity: Activity,
    private val games: () -> List<Game>,
    private val dp: (Int) -> Int,
    private val rounded: (Int, Int) -> Drawable,
    private val panel: () -> Int,
    private val white: () -> Int,
    private val red: () -> Int,
    private val gray: () -> Int,
    private val makeText: (String, Float, Int, Boolean) -> TextView,
    private val loadCover: (ImageView, String, String) -> Unit,
    private val showDetails: (Game) -> Unit,
    private val openGameLink: (String) -> Unit,
    private val styleAlertDialog: (AlertDialog) -> Unit,
    private val translate: (String) -> String
) {
    fun show() {
        val library = games()
        if (library.isEmpty()) {
            Toast.makeText(activity, translate("لا توجد ألعاب متاحة لاختيار لعبة اليوم"), Toast.LENGTH_SHORT).show()
            return
        }
        val game = GameOfDaySelector.select(library, Calendar.getInstance()) ?: return
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(6), dp(18), dp(4))
        }
        val cover = ImageView(activity).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(panel(), 14)
            contentDescription = game.name
        }
        if (game.coverUrl.isNotBlank() || game.coverLocalPath.isNotBlank()) {
            loadCover(cover, game.coverLocalPath, game.coverUrl)
        } else {
            cover.setImageResource(android.R.drawable.ic_menu_gallery)
            cover.scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        box.addView(cover, LinearLayout.LayoutParams(-1, dp(190)))
        box.addView(makeText(game.name, 18f, white(), true).apply {
            setPadding(0, dp(12), 0, dp(5))
        })
        val platformLabel = game.platform + if (game.genre.isNotBlank()) " • ${game.genre}" else ""
        box.addView(makeText(platformLabel, 11f, android.graphics.Color.rgb(105, 190, 255), true))
        val summary = game.description.ifBlank {
            translate("اكتشف تفاصيل هذه اللعبة وروابطها داخل مكتبة MARCO RX6.")
        }
        box.addView(makeText(summary, 12f, gray(), false).apply {
            setPadding(0, dp(8), 0, 0)
            maxLines = 5
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        AlertDialog.Builder(activity)
            .setTitle("✧ " + translate("لعبة اليوم"))
            .setView(box)
            .setPositiveButton(translate("تفاصيل اللعبة")) { _, _ -> showDetails(game) }
            .setNeutralButton(translate("فتح الرابط")) { _, _ -> openGameLink(game.url) }
            .setNegativeButton(translate("إغلاق"), null)
            .create().apply { setOnShowListener { styleAlertDialog(this) } }
            .show()
    }
}
