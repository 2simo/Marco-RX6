package com.marcorx6.library.ui

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** One-time welcome screen linking new users to the official MARCO communities. */
internal class CommunityChannelsController(
    private val activity: Activity,
    private val dp: (Int) -> Int,
    private val makeText: (String, Float, Int, Boolean) -> TextView,
    private val translate: (String) -> String,
    private val isDarkMode: () -> Boolean,
    private val red: () -> Int
) {
    fun show(onContinue: () -> Unit = {}) {
        if (activity.isFinishing || activity.isDestroyed) return
        val dark = isDarkMode()
        val dialog = Dialog(activity, android.R.style.Theme_Material_NoActionBar)
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(28), dp(24), dp(24))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                if (dark) intArrayOf(Color.rgb(7, 8, 11), Color.rgb(15, 17, 22), Color.rgb(7, 8, 11))
                else intArrayOf(Color.rgb(250, 251, 253), Color.rgb(232, 236, 242), Color.rgb(245, 247, 250))
            )
        }
        root.addView(makeText("MARCO", 12f, red(), true).apply {
            gravity = Gravity.CENTER
            letterSpacing = 0.16f
        })
        root.addView(makeText(translate("مرحباً بك في MARCO"), 25f, if (dark) Color.WHITE else Color.rgb(25, 29, 36), true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, dp(8))
        })
        root.addView(makeText(
            translate("تابع قنواتنا الرسمية لتصلك أخبار التطبيق والتحديثات الجديدة."),
            13f, if (dark) Color.rgb(178, 184, 194) else Color.rgb(91, 98, 110), false
        ).apply {
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(28))
        })
        fun channelButton(label: String, detail: String, url: String) {
            val button = Button(activity).apply {
                text = "$label\n$detail"
                isAllCaps = false
                textSize = 14f
                setTextColor(AccentPalette.foregroundForColor(red()))
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    setColor(red())
                    cornerRadius = dp(16).toFloat()
                    setStroke(dp(1), Color.argb(100, 145, 153, 166))
                }
                minHeight = dp(62)
                setOnClickListener {
                    try {
                        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (_: Exception) {
                        Toast.makeText(activity, translate("تعذر فتح الرابط"), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            root.addView(button, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        }
        channelButton("Telegram", "MARCO RX1", TELEGRAM_URL)
        channelButton("YouTube", "MARCO RX7", YOUTUBE_URL)
        val continueButton = Button(activity).apply {
            text = translate("متابعة إلى التطبيق")
            isAllCaps = false
            textSize = 14f
            setTextColor(AccentPalette.foregroundForColor(red()))
            background = GradientDrawable().apply {
                setColor(red())
                cornerRadius = dp(16).toFloat()
            }
            minHeight = dp(52)
            setOnClickListener { dialog.dismiss(); onContinue() }
        }
        root.addView(continueButton, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        dialog.setContentView(root)
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(-1, -1)
        dialog.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
            SystemBarPolicy.apply(window, darkMode = dark)
        }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safe = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(dp(24), dp(28) + safe.top, dp(24), dp(24) + safe.bottom)
            insets
        }
        androidx.core.view.ViewCompat.requestApplyInsets(root)
    }

    companion object {
        const val TELEGRAM_URL = "https://t.me/MARCO_RX1"
        const val YOUTUBE_URL = "https://youtube.com/@marco_rx7?si=udXm_y-QFIZIzZ6a"
    }
}
