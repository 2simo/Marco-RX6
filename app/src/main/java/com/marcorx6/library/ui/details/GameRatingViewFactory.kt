package com.marcorx6.library.ui.details

import android.app.Activity
import android.content.res.ColorStateList
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
    private val red: () -> Int,
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
        val primary = if (dark) Color.rgb(244, 247, 252) else Color.rgb(25, 29, 36)
        val accent = red()
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(12), dp(13), dp(12))
            background = GradientDrawable().apply {
                setColor(AccentPalette.surface(
                    if (dark) Color.rgb(25, 28, 34) else Color.rgb(242, 244, 247),
                    accent,
                    if (dark) 0.10f else 0.07f
                ))
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), if (dark)
                    AccentPalette.surface(Color.rgb(75, 80, 90), accent, 0.34f)
                else AccentPalette.surface(Color.rgb(211, 215, 222), accent, 0.30f))
            }
            elevation = dp(2).toFloat()
        }
        card.addView(makeText(translate("قيّم هذه اللعبة"), 16f, primary, true).apply {
            setPadding(0, 0, 0, dp(2))
        })
        val registered = isRegisteredUser()
        card.addView(makeText(
            translate(
                if (registered) "اختر تقييماً من نجمة إلى خمس نجوم"
                else "سجّل الدخول لتتمكن من تقييم اللعبة"
            ),
            12f,
            muted,
            false
        ).apply { setPadding(0, dp(5), 0, dp(7)) })

        val ratingBar = RatingBar(activity, null, android.R.attr.ratingBarStyle).apply {
            numStars = 5
            stepSize = 1f
            setIsIndicator(!registered)
            rating = 0f
            progressTintList = ColorStateList.valueOf(accent)
            secondaryProgressTintList = ColorStateList.valueOf(
                if (dark) Color.rgb(105, 112, 123) else Color.rgb(190, 196, 205)
            )
            progressBackgroundTintList = ColorStateList.valueOf(
                if (dark) Color.rgb(65, 70, 79) else Color.rgb(215, 219, 226)
            )
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
