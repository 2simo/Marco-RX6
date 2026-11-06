package com.marcorx6.library.ui.details

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import com.marcorx6.library.data.Game
import java.util.Locale

/** Displays public score and lets authenticated, non-anonymous users submit one editable rating. */
internal class GameRatingViewFactory(
    private val activity: Activity,
    private val dp: (Int) -> Int,
    private val makeText: (String, Float, Int, Boolean) -> TextView,
    private val isDarkMode: () -> Boolean,
    private val white: () -> Int,
    private val gray: () -> Int,
    private val translate: (String) -> String,
    private val isRegisteredUser: () -> Boolean,
    private val loadSummary: (Game, (Long, Double, Long) -> Unit) -> Unit,
    private val loadMyRating: (Game, (Int?) -> Unit) -> Unit,
    private val submitRating: (Game, Int, (Boolean, String?) -> Unit) -> Unit
) {
    fun create(game: Game): LinearLayout {
        val dark = isDarkMode()
        val muted = if (dark) Color.rgb(170, 190, 214) else Color.rgb(91, 108, 130)
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                setColor(if (dark) Color.rgb(18, 36, 57) else Color.rgb(245, 249, 255))
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), if (dark) Color.rgb(62, 116, 166) else Color.rgb(198, 220, 245))
            }
        }
        card.addView(makeText(translate("تقييمات اللاعبين"), 15f, white(), true))
        val summary = makeText(translate("جارٍ تحميل التقييمات…"), 12f, muted, false).apply {
            setPadding(0, dp(5), 0, dp(4))
        }
        card.addView(summary)

        val registered = isRegisteredUser()
        val ratingBar = RatingBar(activity, null, android.R.attr.ratingBarStyle).apply {
            numStars = 5
            stepSize = 1f
            isIndicator = !registered
            rating = 0f
            contentDescription = translate("قيّم هذه اللعبة من نجمة إلى خمس نجوم")
        }
        card.addView(ratingBar, LinearLayout.LayoutParams(-2, -2).apply {
            gravity = Gravity.START
        })
        if (!registered) {
            card.addView(makeText(
                translate("سجّل الدخول لتتمكن من تقييم اللعبة"), 11f, muted, false
            ).apply { setPadding(0, dp(4), 0, 0) })
        } else {
            loadMyRating(game) { previous ->
                if (previous != null) ratingBar.rating = previous.toFloat()
            }
            ratingBar.setOnRatingBarChangeListener { _, value, fromUser ->
                if (fromUser && value >= 1f) {
                    ratingBar.isEnabled = false
                    submitRating(game, value.toInt()) { success, error ->
                        activity.runOnUiThread {
                            ratingBar.isEnabled = true
                            if (success) {
                                Toast.makeText(activity, translate("تم حفظ تقييمك"), Toast.LENGTH_SHORT).show()
                                refreshSummary(game, summary)
                            } else {
                                Toast.makeText(
                                    activity,
                                    error ?: translate("تعذر حفظ التقييم"),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }
            }
        }
        refreshSummary(game, summary)
        return card
    }

    private fun refreshSummary(game: Game, target: TextView) {
        loadSummary(game) { views, average, count ->
            activity.runOnUiThread {
                target.text = if (count == 0L) {
                    translate("لا توجد تقييمات بعد")
                } else {
                    String.format(
                        Locale.getDefault(),
                        "%s: %.1f/5  •  %d %s",
                        translate("متوسط التقييم"),
                        average,
                        count,
                        translate("تقييم")
                    )
                }
            }
        }
    }
}
