package com.marcorx6.library.ui

import android.app.Activity
import android.app.AlertDialog
import android.graphics.drawable.Drawable
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.marcorx6.library.data.Game
import com.marcorx6.library.ui.settings.LocalContentTranslator
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
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
    private val translate: (String) -> String,
    private val targetLanguage: () -> String
) {
    private val contentTranslatorDelegate = lazy { LocalContentTranslator(activity) }
    private val contentTranslator by contentTranslatorDelegate

    init {
        (activity as? LifecycleOwner)?.lifecycle?.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                if (contentTranslatorDelegate.isInitialized()) contentTranslator.close()
                owner.lifecycle.removeObserver(this)
            }
        })
    }
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
        val genreView = makeText(platformLabel, 11f, android.graphics.Color.rgb(76, 145, 235), true)
        box.addView(genreView)
        val summary = game.description.ifBlank {
            translate("اكتشف تفاصيل هذه اللعبة وروابطها داخل مكتبة MARCO.")
        }
        val summaryView = makeText(summary, 12f, gray(), false).apply {
            setPadding(0, dp(8), 0, 0)
            maxLines = 5
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        box.addView(summaryView)
        // Translate only the short description shown in this preview, never Blogger's
        // full article, game title, platform identifier, or stored Game fields.
        val target = targetLanguage()
        if (target in setOf("ar", "en", "fr", "es")) {
            if (game.genre.isNotBlank()) {
                contentTranslator.translateAutomatically(
                    game.genre, target,
                    onTranslated = { value ->
                        activity.runOnUiThread {
                            if (!activity.isFinishing && !activity.isDestroyed &&
                                genreView.isAttachedToWindow && value.isNotBlank()
                            ) genreView.text = game.platform + " • " + value
                        }
                    },
                    onError = { /* Keep original genre. */ }
                )
            }
            if (game.description.length in 3..500) {
                contentTranslator.translateAutomatically(
                    game.description, target,
                    onTranslated = { value ->
                        activity.runOnUiThread {
                            if (!activity.isFinishing && !activity.isDestroyed &&
                                summaryView.isAttachedToWindow && value.isNotBlank()
                            ) summaryView.text = value
                        }
                    },
                    onError = { /* Keep original summary. */ }
                )
            }
        }
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
