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
    private var introPulseAnimator: ValueAnimator? = null

    fun show(onStartupFinished: () -> Unit) {
        if (!isActivityUsable() || dialog?.isShowing == true || introDialog?.isShowing == true) {
            onStartupFinished()
            return
        }
        val intro = Dialog(activity, android.R.style.Theme_Material_NoActionBar)
        introDialog = intro
        val introContent = FrameLayout(activity).apply {
            setBackgroundColor(Color.BLACK)
        }
        val artwork = ImageView(activity).apply {
            setImageResource(com.marcorx6.library.R.drawable.marco_silver_wolf_intro)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.BLACK)
            contentDescription = "Silver wolf"
            alpha = 0f
            scaleX = 0.84f
            scaleY = 0.84f
        }
        introContent.addView(
            artwork,
            FrameLayout.LayoutParams(-1, -1).apply {
                leftMargin = dp(12)
                rightMargin = dp(12)
                bottomMargin = dp(66)
            }
        )
        val brandName = makeText("MARCO", 30f, Color.rgb(218, 222, 230), true).apply {
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            letterSpacing = 0.28f
            maxLines = 1
            alpha = 0f
            translationY = dp(16).toFloat()
            setShadowLayer(dp(14).toFloat(), 0f, dp(2).toFloat(), Color.rgb(115, 122, 134))
        }
        introContent.addView(
            brandName,
            FrameLayout.LayoutParams(-1, dp(62), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                leftMargin = dp(24)
                rightMargin = dp(24)
                bottomMargin = dp(28)
            }
        )
        intro.setContentView(introContent)
        intro.setCancelable(false)
        intro.setCanceledOnTouchOutside(false)
        intro.show()
        intro.window?.setBackgroundDrawableResource(android.R.color.black)
        intro.window?.setLayout(-1, -1)
        val introMotionEnabled = activity.getSharedPreferences("app_settings", Activity.MODE_PRIVATE)
            .getBoolean("animations", true)
        if (introMotionEnabled) {
            artwork.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(1050L)
                .setInterpolator(android.view.animation.DecelerateInterpolator(1.5f))
                .start()
            brandName.animate().alpha(1f).translationY(0f)
                .setStartDelay(520L)
                .setDuration(720L)
                .setInterpolator(OvershootInterpolator(0.85f))
                .start()
            introPulseAnimator = ValueAnimator.ofFloat(1f, 1.025f).apply {
                duration = 1500L
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                startDelay = 1050L
                addUpdateListener { pulse ->
                    if (introDialog === intro && intro.isShowing) {
                        val scale = pulse.animatedValue as Float
                        artwork.scaleX = scale
                        artwork.scaleY = scale
                    }
                }
                start()
            }
        } else {
            artwork.alpha = 1f
            artwork.scaleX = 1f
            artwork.scaleY = 1f
            brandName.alpha = 1f
            brandName.translationY = 0f
        }
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
        }.also { handler.postDelayed(it, 2900L) }
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
        val electricBlue = silver
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
            // Fill the entire edge-to-edge screen. CENTER_CROP removes the
            // top/bottom letterboxing without distorting the original artwork.
            scaleType = ImageView.ScaleType.CENTER_CROP
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
            setPadding(dp(30), dp(10), dp(30), dp(24))
            setBackgroundColor(Color.TRANSPARENT)
        }
        val signature = makeText("MARCO  /  SYSTEM STARTUP", 10f, silver, true).apply {
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            letterSpacing = 0.20f
            alpha = 0.90f
        }
        footer.addView(signature, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(9)
        })
        val statusRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val status = makeText("تهيئة واجهة التطبيق", 11f, silver, true).apply {
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            maxLines = 1
            alpha = 0.96f
        }
        statusRow.addView(status, LinearLayout.LayoutParams(0, -2, 1f))
        val percent = makeText("1%", 10f, electricBlue, true).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            letterSpacing = 0.04f
        }
        statusRow.addView(percent, LinearLayout.LayoutParams(-2, -2).apply {
            marginStart = dp(12)
        })
        footer.addView(statusRow, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(9)
        })
        val progress = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            this.progress = 1
            progressTintList = android.content.res.ColorStateList.valueOf(electricBlue)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(48, 52, 60))
            progressDrawable = progressDrawable.mutate().apply {
                if (this is android.graphics.drawable.LayerDrawable) {
                    findDrawableByLayerId(android.R.id.background)?.let { it.setTint(Color.rgb(48, 52, 60)) }
                    findDrawableByLayerId(android.R.id.progress)?.let { it.setTint(electricBlue) }
                }
            }
        }
        footer.addView(progress, LinearLayout.LayoutParams(-1, dp(3)))
        content.addView(
            footer,
            FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                bottomMargin = dp(42)
            }
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
        // Keep the artwork edge-to-edge; do not inset the image below system bars.
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
                    val message = when {
                        value < 18 -> "تهيئة واجهة التطبيق"
                        value < 36 -> "تجهيز مكتبتك"
                        value < 54 -> "تحميل عناصر البداية"
                        value < 72 -> "ضبط المظهر والتفضيلات"
                        value < 90 -> "إتمام التجهيزات"
                        else -> "لحظات وننطلق"
                    }
                    if (status.text.toString() != message) {
                        status.animate().alpha(0f).setDuration(110L).withEndAction {
                            status.text = message
                            status.animate().alpha(0.96f).setDuration(180L).start()
                        }.start()
                    }
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
