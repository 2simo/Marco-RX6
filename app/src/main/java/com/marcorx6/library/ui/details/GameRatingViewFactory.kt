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

/** Lets a registered user rate the game; engagement summaries are shown on library cards only. */
internal class GameRatingViewFactory(
    private val activity: Activity,
    private val dp: (Int) -> Int,
    private val makeText: (String, Float, Int, Boolean) -> TextView,
    private val isDarkMode: () -> Boolean,
    private val white: () -> Int,
    private val gray: () -> Int,
    private val translate: (String) -> String,
    private val isRegisteredUser: () -> Boolean,
    private val loadMyRating: (Game, (Int?) -> Unit) -> Unit,
    private val submitRating: (Game, Int, (Boolean, String?) -> Unit) -> Unit
) {
    fun create(game: Game): LinearLayout {
        val dark = isDarkMode()
        val muted = if (dark) Color.rgb(178, 184, 194) else Color.rgb(91, 98, 110)
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = GradientDrawable().apply {
                setColor(if (dark) Color.rgb(35, 38, 45) else Color.rgb(242, 244, 247))
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), if (dark) Color.rgb(130, 138, 151) else Color.rgb(160, 168, 181))
            }
        }
        card.addView(makeText(translate("قيّم هذه اللعبة"), 15f, white(), true))
        val registered = isRegisteredUser()
        card.addView(makeText(
            translate(
                if (registered) "اختر تقييماً من نجمة إلى خمس نجوم"
                else "سجّل الدخول لتتمكن من تقييم اللعبة"
            ),
            12f,
            muted,
            true
        ).apply { setPadding(0, dp(8), 0, dp(5)) })

        val ratingBar = RatingBar(activity, null, android.R.attr.ratingBarStyle).apply {
            numStars = 5
            stepSize = 1f
            setIsIndicator(!registered)
            rating = 0f
            contentDescription = translate("قيّم هذه اللعبة من نجمة إلى خمس نجوم")
        }
        card.addView(ratingBar, LinearLayout.LayoutParams(-2, -2).apply {
            gravity = Gravity.START
        })

        if (registered) {
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
                                Toast.makeText(
                                    activity,
                                    translate("تم حفظ تقييمك"),
                                    Toast.LENGTH_SHORT
                                ).show()
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
        return card
    }
}
