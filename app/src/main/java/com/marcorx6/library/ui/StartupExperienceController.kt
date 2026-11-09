package com.marcorx6.library.ui

import android.app.Activity
import android.app.Dialog
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

internal class StartupExperienceController(
    private val activity: Activity,
    private val dp: (Int) -> Int,
    private val makeText: (String, Float, Int, Boolean) -> TextView,
    private val showFirstRunSetup: () -> Unit,
    private val showProfile: () -> Unit,
    private val translate: (String) -> String
) {
    private val handler = Handler(Looper.getMainLooper())
    private var animator: ValueAnimator? = null
    private var completion: Runnable? = null
    private var dialog: Dialog? = null

    fun show() {
        if (!isActivityUsable() || dialog?.isShowing == true) return
        animator?.cancel()
        completion?.let(handler::removeCallbacks)
        val splash = Dialog(activity, android.R.style.Theme_Material_NoActionBar)
        dialog = splash
        val content = FrameLayout(activity).apply {
            setBackground(GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(
                Color.rgb(7, 8, 11), Color.rgb(15, 17, 22), Color.rgb(4, 5, 8)
            )))
        }
        val center = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(30), dp(24), dp(30), dp(24))
        }
        val logoHalo = FrameLayout(activity).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(25, 28, 34))
                setStroke(dp(3), Color.rgb(190, 198, 210))
            }
            elevation = dp(18).toFloat()
        }
        val logo = ImageView(activity).apply {
            setImageResource(com.marcorx6.library.R.drawable.marco_splash)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "MARCO"
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(8, 9, 12))
            }
            clipToOutline = true
            setPadding(dp(7), dp(7), dp(7), dp(7))
            alpha = 0f
            scaleX = 0.92f
            scaleY = 0.92f
        }
        logoHalo.addView(logo, FrameLayout.LayoutParams(-1, -1))
        center.addView(logoHalo, LinearLayout.LayoutParams(dp(230), dp(230)).apply { bottomMargin = dp(25) })
        center.addView(makeText("MARCO", 25f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            letterSpacing = 0.16f
        })
        center.addView(makeText("GAME LIBRARY", 10f, Color.rgb(190, 198, 210), true).apply {
            gravity = Gravity.CENTER
            letterSpacing = 0.28f
            setPadding(0, dp(9), 0, 0)
        })
        val loading = makeText(translate("جارٍ تجهيز عالم الألعاب..."), 11f, Color.rgb(170, 176, 186), false).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(34), 0, 0)
        }
        center.addView(loading)
        val progress = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            this.progress = 1
            progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(190, 198, 210))
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(57, 61, 70))
            progressDrawable = progressDrawable.mutate().apply {
                if (this is android.graphics.drawable.LayerDrawable) {
                    findDrawableByLayerId(android.R.id.background)?.let { it.setTint(Color.rgb(57, 61, 70)) }
                    findDrawableByLayerId(android.R.id.progress)?.let { it.setTint(Color.rgb(190, 198, 210)) }
                }
            }
        }
        center.addView(progress, LinearLayout.LayoutParams(dp(238), dp(7)).apply { topMargin = dp(18) })
        val percent = makeText("1%", 12f, Color.rgb(190, 198, 210), true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(9), 0, 0)
        }
        center.addView(percent)
        content.addView(center, FrameLayout.LayoutParams(-1, -1))
        splash.setContentView(content)
        splash.setCancelable(false)
        splash.setCanceledOnTouchOutside(false)
        splash.show()
        splash.window?.setBackgroundDrawableResource(android.R.color.transparent)
        splash.window?.setLayout(-1, -1)
        splash.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
            WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
                hide(WindowInsetsCompat.Type.statusBars())
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                window.isStatusBarContrastEnforced = false
                window.isNavigationBarContrastEnforced = false
            }
        }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val safe = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(0, safe.top, 0, safe.bottom)
            insets
        }
        androidx.core.view.ViewCompat.requestApplyInsets(content)
        logo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(700L)
            .setInterpolator(OvershootInterpolator(1.08f)).start()
        animator = ValueAnimator.ofInt(1, 100).apply {
            duration = 4500L
            addUpdateListener { valueAnimator ->
                if (isActivityUsable() && dialog === splash && splash.isShowing) {
                    val value = valueAnimator.animatedValue as Int
                    progress.progress = value
                    percent.text = "$value%"
                }
            }
            start()
        }
        completion = Runnable {
            if (isActivityUsable() && dialog === splash && splash.isShowing) {
                animator?.cancel()
                animator = null
                splash.dismiss()
                dialog = null
                val settings = activity.getSharedPreferences("app_settings", Activity.MODE_PRIVATE)
                if (!settings.getBoolean("setup_completed", false)) {
                    showFirstRunSetup()
                } else if (!settings.getBoolean("community_channels_prompted", true)) {
                    settings.edit().putBoolean("community_channels_prompted", true).apply()
                    CommunityChannelsController(activity, dp, makeText, translate).show()
                } else if (!settings.getBoolean("profile_prompted", false)) {
                    settings.edit().putBoolean("profile_prompted", true).apply()
                    showProfile()
                }
            }
            completion = null
        }.also { handler.postDelayed(it, 4600L) }
        splash.setOnDismissListener {
            animator?.cancel()
            animator = null
            completion?.let(handler::removeCallbacks)
            completion = null
            if (dialog === splash) dialog = null
        }
    }

    fun dismiss() {
        animator?.cancel()
        animator = null
        completion?.let(handler::removeCallbacks)
        completion = null
        dialog?.let { if (it.isShowing) it.dismiss() }
        dialog = null
    }

    private fun isActivityUsable(): Boolean =
        !activity.isFinishing && !activity.isDestroyed
}
