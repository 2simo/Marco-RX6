package com.marcorx6.library.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.marcorx6.library.data.Game
import com.marcorx6.library.ui.details.GameRatingViewFactory

internal class GameDetailsController(
    private val activity: Activity,
    private val games: () -> List<Game>,
    private val bg: () -> Int,
    private val dp: (Int) -> Int,
    private val topBarFactory: GameDetailsTopBarFactory,
    private val identityViewFactory: GameDetailsIdentityViewFactory,
    private val ratingViewFactory: GameRatingViewFactory,
    private val recordViewOnce: (Game) -> Unit,
    private val similarGamesViewFactory: GameSimilarGamesViewFactory,
    private val downloadLinksViewFactory: GameDownloadLinksViewFactory,
    private val isAdmin: () -> Boolean,
    private val showGameDialog: (Game) -> Unit,
    private val deleteGame: (Game) -> Unit,
    private val applyMotionPreferences: (android.view.View) -> Unit,
    private val applyUnifiedSkin: (android.view.View) -> Unit,
    private val translate: (String) -> String,
    private val isDarkMode: () -> Boolean
) {
    fun show(game: Game) {
        // Keep view recording intact; its counter is presented on library cards, not here.
        recordViewOnce(game)
        val reader = android.app.Dialog(activity)
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg())
        }

        root.addView(topBarFactory.create(game) { reader.dismiss() })

        val contentScroll = ScrollView(activity).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = true
            overScrollMode = android.view.View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(18))
        }
        contentScroll.addView(content, android.widget.FrameLayout.LayoutParams(-1, -2))
        root.addView(contentScroll, LinearLayout.LayoutParams(-1, 0, 1f))

        // Game identity and information, followed by the original download actions.
        content.addView(identityViewFactory.create(game), LinearLayout.LayoutParams(-1, -2))

        // Preserve game-management actions for administrators, using the same
        // neutral black/silver visual language as the redesigned details page.
        if (isAdmin()) {
            val adminActions = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(8), dp(10), dp(8))
                background = GradientDrawable().apply {
                    setColor(if (isDarkMode()) Color.rgb(25, 28, 34) else Color.rgb(242, 244, 247))
                    cornerRadius = dp(16).toFloat()
                    setStroke(dp(1), if (isDarkMode()) Color.rgb(75, 80, 90) else Color.rgb(190, 196, 205))
                }
            }
            fun adminButton(label: String, action: () -> Unit): TextView =
                TextView(activity).apply {
                    text = translate(label)
                    textSize = 14f
                    gravity = Gravity.CENTER
                    setTextColor(if (isDarkMode()) Color.rgb(244, 247, 252) else Color.rgb(25, 29, 36))
                    setTypeface(null, Typeface.BOLD)
                    setPadding(dp(12), dp(12), dp(12), dp(12))
                    background = GradientDrawable().apply {
                        setColor(if (isDarkMode()) Color.rgb(43, 47, 55) else Color.WHITE)
                        cornerRadius = dp(12).toFloat()
                        setStroke(dp(1), if (isDarkMode()) Color.rgb(105, 112, 123) else Color.rgb(190, 196, 205))
                    }
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { action() }
                }
            adminActions.addView(
                adminButton("تعديل") { reader.dismiss(); showGameDialog(game) },
                LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(5) }
            )
            adminActions.addView(
                adminButton("حذف") { reader.dismiss(); deleteGame(game) },
                LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(5) }
            )
            content.addView(adminActions, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(10)
            })
        }


        val sourceUrl = game.sourcePostUrl.ifBlank { game.url }
        val downloadButtons = downloadLinksViewFactory.createFromHtml(
            game.bloggerHtml, game.name, sourceUrl
        ) ?: downloadLinksViewFactory.create(game.downloadLinks, game.name, sourceUrl)
        downloadButtons?.let { buttons ->
            content.addView(buttons, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(10)
            })
        }

        // Keep user ratings and related games available in the details page.
        content.addView(ratingViewFactory.create(game), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(10)
        })
        similarGamesViewFactory.create(game, games()) { selected ->
            reader.dismiss()
            show(selected)
        }?.let { similar ->
            content.addView(similar, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(10)
            })
        }

        applyMotionPreferences(root)
        applyUnifiedSkin(root)
        reader.setContentView(root)
        reader.window?.let { window ->
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(bg()))
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.statusBarColor = bg()
            window.navigationBarColor = bg()
            window.setDimAmount(0f)
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                window.isNavigationBarContrastEnforced = false
                window.isStatusBarContrastEnforced = false
            }
        }
        reader.show()
        reader.window?.let { window ->
            window.setLayout(
                android.view.WindowManager.LayoutParams.MATCH_PARENT,
                android.view.WindowManager.LayoutParams.MATCH_PARENT
            )
            val lightSystemBars = !isDarkMode()
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (lightSystemBars) {
                android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                    android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            } else {
                0
            }
        }
    }
}
