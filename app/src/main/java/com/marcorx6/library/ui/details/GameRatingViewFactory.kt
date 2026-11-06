package com.marcorx6.library.ui.details

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import com.marcorx6.library.data.Game
import java.util.Locale

/** Public engagement summary plus an editable rating for authenticated, non-anonymous users. */
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
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = GradientDrawable().apply {
                setColor(if (dark) Color.rgb(18, 36, 57) else Color.rgb(245, 249, 255))
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), if (dark) Color.rgb(62, 116, 166) else Color.rgb(198, 220, 245))
            }
        }
        card.addView(makeText(translate("تقييمات اللاعبين"), 15f, white(), true))

        val metrics = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(10))
        }
        fun metric(iconRes: Int, label: String, tint: Int): Pair<LinearLayout, TextView> {
            val chip = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = GradientDrawable().apply {
                    setColor(if (dark) Color.rgb(10, 27, 45) else Color.rgb(232, 242, 254))
                    cornerRadius = dp(11).toFloat()
                    setStroke(dp(1), if (dark) Color.rgb(48, 86, 125) else Color.rgb(207, 225, 246))
                }
                setPadding(dp(9), dp(8), dp(9), dp(8))
            }
            val icon = ImageView(activity).apply {
                setImageResource(iconRes)
                imageTintList = android.content.res.ColorStateList.valueOf(tint)
                contentDescription = label
            }
            chip.addView(icon, LinearLayout.LayoutParams(dp(17), dp(17)))
            val value = makeText("—", 12f, white(), true).apply {
                setPadding(dp(7), 0, 0, 0)
            }
            chip.addView(value)
            return chip to value
        }
        val (ratingMetric, ratingValue) = metric(
            com.marcorx6.library.R.drawable.ic_engagement_rating,
            translate("متوسط التقييم"), Color.rgb(255, 190, 55)
        )
        val (viewsMetric, viewsValue) = metric(
            com.marcorx6.library.R.drawable.ic_engagement_views,
            translate("المشاهدات"), Color.rgb(72, 174, 255)
        )
        metrics.addView(ratingMetric, LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginEnd = dp(6)
        })
        metrics.addView(viewsMetric, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(metrics)

        val ratingCount = makeText(translate("لا توجد تقييمات بعد"), 11f, muted, false)
        card.addView(ratingCount)

        val registered = isRegisteredUser()
        card.addView(makeText(
            translate(if (registered) "قيّم اللعبة" else "سجّل الدخول لتتمكن من تقييم اللعبة"),
            12f, muted, true
        ).apply { setPadding(0, dp(12), 0, dp(4)) })

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
                                Toast.makeText(activity, translate("تم حفظ تقييمك"), Toast.LENGTH_SHORT).show()
                                refreshSummary(game, ratingValue, viewsValue, ratingCount)
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

        refreshSummary(game, ratingValue, viewsValue, ratingCount)
        return card
    }

    private fun refreshSummary(
        game: Game,
        ratingValue: TextView,
        viewsValue: TextView,
        ratingCount: TextView
    ) {
        loadSummary(game) { views, average, count ->
            activity.runOnUiThread {
                ratingValue.text = if (count == 0L) "—" else String.format(Locale.US, "%.1f/5", average)
                viewsValue.text = compactCount(views)
                ratingCount.text = if (count == 0L) {
                    translate("لا توجد تقييمات بعد")
                } else {
                    count.toString() + " " + translate("تقييم")
                }
            }
        }
    }

    private fun compactCount(value: Long): String = when {
        value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
        value >= 1_000 -> String.format(Locale.US, "%.1fK", value / 1_000.0)
        else -> value.toString()
    }
}
