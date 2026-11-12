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
    private val translate: (String) -> String,
    private val isDarkMode: () -> Boolean,
    private val red: () -> Int
) {
    private val handler = Handler(Looper.getMainLooper())
    private var animator: ValueAnimator? = null
    private var completion: Runnable? = null
    private var dialog: Dialog? = null
    private var introDialog: Dialog? = null
    private var introCompletion: Runnable? = null

    fun show(onStartupFinished: () -> Unit) {
        if (!isActivityUsable() || dialog?.isShowing == true || introDialog?.isShowing == true) {
            onStartupFinished()
            return
        }
        val intro = Dialog(activity, android.R.style.Theme_Material_NoActionBar)
        introDialog = intro
        val artwork = ImageView(activity).apply {
            setImageResource(com.marcorx6.library.R.drawable.marco_dragon_intro)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.BLACK)
            contentDescription = "MARCO RX6"
        }
        intro.setContentView(artwork)
        intro.setCancelable(false)
        intro.setCanceledOnTouchOutside(false)
        intro.show()
        intro.window?.setBackgroundDrawableResource(android.R.color.black)
        intro.window?.setLayout(-1, -1)
        intro.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = Color.BLACK
            window.navigationBarColor = Color.BLACK
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                window.isStatusBarContrastEnforced = false
                window.isNavigationBarContrastEnforced = false
            }
            WindowInsetsControllerCompat(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        introCompletion = Runnable {
            if (isActivityUsable() && introDialog === intro && intro.isShowing) {
                introDialog = null
                introCompletion = null
                showLoading(intro, onStartupFinished)
            }
        }.also { handler.postDelayed(it, 1700L) }
        intro.setOnDismissListener {
            introCompletion?.let(handler::removeCallbacks)
            introCompletion = null
            if (introDialog === intro) introDialog = null
        }
    }

    private fun showLoading(existingWindow: Dialog? = null, onStartupFinished: () -> Unit) {
        if (!isActivityUsable() || (dialog?.isShowing == true && dialog !== existingWindow)) return
        animator?.cancel()
        completion?.let(handler::removeCallbacks)
        val silver = Color.rgb(190, 196, 207)
        // Reuse the intro window so the Activity/library is never exposed between
        // the dragon intro and the loading splash.
        val splash = existingWindow ?: Dialog(activity, android.R.style.Theme_Material_NoActionBar)
        dialog = splash
        val content = FrameLayout(activity).apply {
            setBackground(GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(3, 4, 6), Color.rgb(13, 15, 19), Color.BLACK)))
        }
        val logo = ImageView(activity).apply {
            setImageResource(com.marcorx6.library.R.drawable.marco_splash)
            // Let the complete splash artwork use the full available screen area.
            // FIT_CENTER preserves the artwork without stretching or cropping it.
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "MARCO RX6"
            setBackgroundColor(Color.BLACK)
            alpha = 0f
        }
        content.addView(logo, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        // Keep the splash artwork unobstructed: loading feedback stays in a
        // compact footer instead of placing large branding text over the image.
        val footer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(42), dp(8), dp(42), dp(22))
            setBackgroundColor(Color.TRANSPARENT)
        }
        val progress = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            this.progress = 1
            progressTintList = android.content.res.ColorStateList.valueOf(silver)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(48, 52, 60))
            progressDrawable = progressDrawable.mutate().apply {
                if (this is android.graphics.drawable.LayerDrawable) {
                    findDrawableByLayerId(android.R.id.background)?.let { it.setTint(Color.rgb(48, 52, 60)) }
                    findDrawableByLayerId(android.R.id.progress)?.let { it.setTint(silver) }
                }
            }
        }
        footer.addView(progress, LinearLayout.LayoutParams(-1, dp(3)))
        val percent = makeText("1%", 9f, silver, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(5), 0, 0)
        }
        footer.addView(percent)
        content.addView(
            footer,
            FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        )
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
            SystemBarPolicy.apply(window, darkMode = true)
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
        val motionEnabled = activity.getSharedPreferences("app_settings", Activity.MODE_PRIVATE)
            .getBoolean("animations", true)
        if (motionEnabled) {
            logo.animate().alpha(1f).setDuration(700L)
                .setInterpolator(OvershootInterpolator(1.08f)).start()
        } else {
            logo.alpha = 1f
        }
        // Keep numeric loading progress active: it reports startup work rather than
        // decorative motion and remains useful when animations are disabled.
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
                // The Activity library is composed only after the complete startup
                // sequence. This prevents it from appearing behind/between splash windows.
                onStartupFinished()
                val settings = activity.getSharedPreferences("app_settings", Activity.MODE_PRIVATE)
                if (!settings.getBoolean("setup_completed", false)) {
                    showFirstRunSetup()
                } else if (!settings.getBoolean("community_channels_prompted", true)) {
                    settings.edit().putBoolean("community_channels_prompted", true).apply()
                    CommunityChannelsController(activity, dp, makeText, translate, isDarkMode, red).show()
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
        introCompletion?.let(handler::removeCallbacks)
        introCompletion = null
        introDialog?.let { if (it.isShowing) it.dismiss() }
        introDialog = null
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
