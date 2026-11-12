package com.marcorx6.library.ui

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

internal class FirstRunSetupController(
    private val activity: Activity,
    private val dp: (Int) -> Int,
    private val rounded: (Int, Int) -> GradientDrawable,
    private val makeText: (String, Float, Int, Boolean) -> android.widget.TextView,
    private val appearancePreferences: () -> android.content.SharedPreferences,
    private val currentLanguage: () -> String,
    private val isDarkMode: () -> Boolean,
    private val isNotificationsEnabled: () -> Boolean,
    private val launchDownloadFolderPicker: () -> Unit,
    private val downloadFolderName: () -> String,
    private val viewFactory: FirstRunSetupViewFactory,
    private val completionController: FirstRunSetupCompletionController,
    private val applyUnifiedSkin: (View) -> Unit,
    private val applyMotionPreferences: (View) -> Unit,
    private val getSetupDialog: () -> Dialog?,
    private val setSetupDialog: (Dialog?) -> Unit,
    private val recreateActivity: () -> Unit
) {
    fun show() {
        val settings = activity.getSharedPreferences("app_settings", Activity.MODE_PRIVATE)
        val motionEnabled = settings.getBoolean("animations", true)
        val appearance = appearancePreferences()
        var step = 0
        var preferredPlatform = settings.getString("preferred_platform", "PS3")
            ?.takeIf { it in listOf("PS2", "PS3", "PS4") } ?: "PS3"
        var languageCode = currentLanguage()
        var darkMode = isDarkMode()
        var accentKey = appearance.getString(AccentPalette.PREFERENCE_KEY, AccentPalette.DEFAULT_ID)
            ?.takeIf { key -> AccentPalette.options.any { it.id == key } } ?: AccentPalette.DEFAULT_ID
        fun accentShade(color: Int): Int = Color.rgb(
            (Color.red(color) * 0.72f).toInt(),
            (Color.green(color) * 0.72f).toInt(),
            (Color.blue(color) * 0.72f).toInt()
        )
        var activeAccent = AccentPalette.color(accentKey)
        var notificationsEnabled = isNotificationsEnabled()

        val steps = listOf(
            "منصة المكتبة", "لغة التطبيق", "المظهر", "لون الواجهة", "الإشعارات", "مكان التنزيلات", "جاهز للانطلاق"
        )
        val dialog = Dialog(activity, android.R.style.Theme_Material_NoActionBar)
        setSetupDialog(dialog)
        dialog.setOnDismissListener { if (getSetupDialog() === dialog) setSetupDialog(null) }
        fun setupBackground(dark: Boolean) = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            if (dark) intArrayOf(
                AccentPalette.surface(Color.rgb(7, 8, 11), activeAccent, 0.22f),
                AccentPalette.surface(Color.rgb(16, 18, 23), activeAccent, 0.18f),
                AccentPalette.surface(Color.rgb(5, 6, 9), activeAccent, 0.16f)
            ) else intArrayOf(
                AccentPalette.surface(Color.rgb(250, 251, 253), activeAccent, 0.12f),
                AccentPalette.surface(Color.rgb(232, 236, 242), activeAccent, 0.14f),
                AccentPalette.surface(Color.rgb(245, 247, 250), activeAccent, 0.10f)
            )
        ).apply { cornerRadius = dp(24).toFloat() }
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(18))
            background = setupBackground(darkMode)
        }
        val top = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brand = makeText("MARCO", 13f, Color.WHITE, true).apply { letterSpacing = 0.12f }
        val stepCount = makeText("1 / ${steps.size}", 11f, activeAccent, true)
        top.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(stepCount)
        root.addView(top)

        val progressTrack = FrameLayout(activity).apply {
            background = rounded(if (darkMode) Color.rgb(57, 61, 70) else Color.rgb(210, 216, 225), 8)
            clipToOutline = true
        }
        val progressFill = View(activity).apply {
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(activeAccent, accentShade(activeAccent))).apply {
                cornerRadius = dp(8).toFloat()
            }
        }
        progressTrack.addView(progressFill, FrameLayout.LayoutParams(0, dp(5), Gravity.START))
        root.addView(progressTrack, LinearLayout.LayoutParams(-1, dp(5)).apply {
            topMargin = dp(18)
            bottomMargin = dp(28)
        })

        val content = FrameLayout(activity).apply { layoutParams = LinearLayout.LayoutParams(-1, 0, 1f) }
        root.addView(content)
        val footer = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        lateinit var renderStep: (Int, Boolean) -> Unit
        // Keep the palette list at the user's current scroll position when its cards are rebuilt.
        var colorScrollPosition = 0
        var colorScrollView: ScrollView? = null
        val back = Button(activity).apply { setOnClickListener { renderStep(step - 1, true) } }
        val next = Button(activity).apply { tag = "marco_primary_action" }
        footer.addView(back, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) })
        footer.addView(next, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(8) })
        root.addView(footer, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })



        renderStep = { target: Int, animate: Boolean ->
            step = target.coerceIn(0, steps.lastIndex)
            activeAccent = AccentPalette.color(accentKey)
            val tr: (String) -> String = { value -> LanguageTranslator.translate(value, languageCode) }
            root.background = setupBackground(darkMode)
            progressTrack.background = rounded(if (darkMode) Color.rgb(57, 61, 70) else Color.rgb(210, 216, 225), 8)
            root.layoutDirection = if (languageCode == "ar") View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            dialog.window?.let { SystemBarPolicy.apply(it, darkMode = darkMode) }
            back.setTextColor(if (darkMode) Color.rgb(225, 230, 237) else Color.rgb(35, 41, 51))
            back.background = rounded(if (darkMode) Color.rgb(35, 39, 46) else Color.WHITE, 14)
            next.setTextColor(AccentPalette.foreground(accentKey))
            next.background = rounded(activeAccent, 14)
            progressFill.background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(activeAccent, accentShade(activeAccent))
            ).apply { cornerRadius = dp(8).toFloat() }
            stepCount.setTextColor(AccentPalette.readableAccentText(accentKey, darkMode))
            stepCount.text = "${step + 1} / ${steps.size}"
            progressTrack.post {
                val params = progressFill.layoutParams as FrameLayout.LayoutParams
                params.width = (progressTrack.width * ((step + 1f) / steps.size)).toInt().coerceAtLeast(dp(4))
                progressFill.layoutParams = params
            }
            back.text = tr("السابق")
            back.isEnabled = step > 0
            back.alpha = if (step > 0) 1f else 0.35f
            next.text = tr(if (step == steps.lastIndex) "ابدأ الآن" else "التالي")
            content.animate().cancel()
            content.removeAllViews()
            val page = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.TOP
                setPadding(0, dp(8), 0, dp(8))
            }
            page.addView(makeText(tr("خطوة ${step + 1}"), 10f, activeAccent, true))
            page.addView(makeText(tr(steps[step]), 27f, if (darkMode) Color.WHITE else Color.rgb(25, 29, 36), true).apply {
                setPadding(0, dp(8), 0, dp(8))
            })
            val description = when (step) {
                0 -> "اختر المنصة التي تريد أن تبدأ بها مكتبتك. يمكنك تغييرها في أي وقت."
                1 -> "اختر اللغة التي تناسبك لعرض واجهة MARCO."
                2 -> "اختر المظهر الذي يريح عينيك أثناء التصفح."
                3 -> "اختر اللون الذي سيظهر في الأزرار والعناصر التفاعلية."
                4 -> "هل ترغب في تلقي إشعار عند نشر مقالات جديدة في المدونة؟"
                5 -> "اختر المجلد الذي تريد حفظ الملفات التي تنزّلها فيه. هذه الخطوة اختيارية ويمكنك تخطيها."
                else -> "تم إعداد مكتبتك. اختياراتك محفوظة ويمكنك تغييرها لاحقاً من الإعدادات."
            }
            page.addView(makeText(tr(description), 13f, if (darkMode) Color.rgb(170, 176, 186) else Color.rgb(91, 98, 110), false).apply {
                setPadding(0, 0, 0, dp(24))
            })

            val choices = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            fun addChoice(label: String, detail: String = "", selectedNow: Boolean, action: () -> Unit) {
                val card = viewFactory.optionCard(label, selectedNow, detail, darkMode, activeAccent, AccentPalette.foreground(accentKey))
                choices.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(11) })
                card.setOnClickListener {
                    action()
                    renderStep(step, false)
                }
                if (animate && motionEnabled) {
                    card.alpha = 0f
                    card.translationY = dp(14).toFloat()
                    card.animate().alpha(1f).translationY(0f).setStartDelay((choices.childCount - 1) * 65L)
                        .setDuration(300L).start()
                }
            }

            when (step) {
                0 -> listOf("PS2", "PS3", "PS4").forEach { platform ->
                    val description = when (platform) {
                        "PS2" -> "PlayStation 2"
                        "PS3" -> "PlayStation 3"
                        "PS4" -> "PlayStation 4"
                        else -> platform
                    }
                    addChoice(platform, description, preferredPlatform == platform) {
                        preferredPlatform = platform
                    }
                }
                1 -> listOf("ar" to "العربية", "en" to "English", "fr" to "Français", "es" to "Español")
                    .forEach { (code, label) -> addChoice(tr(label), "", languageCode == code) { languageCode = code } }
                2 -> listOf(true to "الوضع الليلي", false to "الوضع النهاري")
                    .forEach { (dark, label) -> addChoice(tr(label), "", darkMode == dark) { darkMode = dark } }
                3 -> {
                    AccentPalette.options.forEach { option ->
                        val selected = accentKey == option.id
                        val card = viewFactory.optionCard(
                            AccentPalette.localizedLabel(option.id, languageCode),
                            selected,
                            tr("اللون الرئيسي"),
                            darkMode,
                            option.color,
                            AccentPalette.foreground(option.id)
                        )
                        val dot = View(activity).apply {
                            background = rounded(option.color, 20)
                        }
                        card.addView(dot, LinearLayout.LayoutParams(dp(28), dp(28)).apply {
                            topMargin = dp(9)
                        })
                        card.setOnClickListener {
                            colorScrollPosition = colorScrollView?.scrollY ?: colorScrollPosition
                            accentKey = option.id
                            renderStep(step, false)
                        }
                        choices.addView(card, LinearLayout.LayoutParams(-1, -2).apply {
                            bottomMargin = dp(9)
                        })
                        if (animate && motionEnabled) viewFactory.animateIn(card)
                    }
                }
                4 -> {
                    addChoice(tr("تفعيل الإشعارات"), tr("تنبيه عند وصول مقال جديد"), notificationsEnabled) { notificationsEnabled = true }
                    addChoice(tr("إيقاف الإشعارات"), tr("يمكنك تفعيلها لاحقاً من الإعدادات"), !notificationsEnabled) { notificationsEnabled = false }
                }
                5 -> {
                    val folderCard = viewFactory.optionCard(
                        tr("اختيار مجلد التنزيلات"),
                        true,
                        tr("اختر مجلداً لحفظ التنزيلات فيه."),
                        darkMode,
                        activeAccent,
                        AccentPalette.foreground(accentKey)
                    )
                    folderCard.setOnClickListener { launchDownloadFolderPicker() }
                    choices.addView(folderCard, LinearLayout.LayoutParams(-1, -2).apply {
                        bottomMargin = dp(10)
                    })
                    if (animate && motionEnabled) viewFactory.animateIn(folderCard)
                    choices.addView(makeText(
                        tr("يمكنك تخطي هذه الخطوة والمتابعة بالتخزين الافتراضي."),
                        11f,
                        if (darkMode) Color.rgb(170, 176, 186) else Color.rgb(91, 98, 110),
                        false
                    ).apply {
                        setPadding(dp(4), dp(12), dp(4), dp(4))
                    }, LinearLayout.LayoutParams(-1, -2))
                }
                6 -> {
                    val readyCard = viewFactory.optionCard(
                        tr("مكتبتك جاهزة!"),
                        true,
                        tr("تم حفظ المنصة واللغة والمظهر واللون والإشعارات ومكان التنزيلات.") + "\n" + tr("المجلد الحالي:") + " " + downloadFolderName(),
                        darkMode,
                        activeAccent,
                        AccentPalette.foreground(accentKey)
                    )
                    choices.addView(readyCard, LinearLayout.LayoutParams(-1, -2))
                    if (animate && motionEnabled) viewFactory.animateIn(readyCard)
                }
            }
            val scroll = ScrollView(activity).apply {
                isFillViewport = true
                overScrollMode = View.OVER_SCROLL_NEVER
                addView(choices)
            }
            page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
            if (step == 3) {
                colorScrollView = scroll
                scroll.post { scroll.scrollTo(0, colorScrollPosition) }
            } else {
                colorScrollView = null
                colorScrollPosition = 0
            }
            content.addView(page, FrameLayout.LayoutParams(-1, -1))
            // This setup has a temporary, not-yet-saved theme selection.
            // Do not run the global skin pass, which reads the previously saved theme.
            applyMotionPreferences(page)
            if (animate && motionEnabled) viewFactory.animateIn(page)
            next.setOnClickListener {
                if (step < steps.lastIndex) {
                    val old = step
                    if (motionEnabled) {
                        content.animate().cancel()
                        content.animate().alpha(0f).translationX(-dp(28).toFloat()).setDuration(150L)
                            .withEndAction {
                                renderStep(old + 1, true)
                                content.translationX = dp(28).toFloat()
                                content.animate().alpha(1f).translationX(0f).setDuration(230L).start()
                            }
                            .start()
                    } else {
                        content.animate().cancel()
                        content.alpha = 1f
                        content.translationX = 0f
                        renderStep(old + 1, false)
                    }
                } else {
                    completionController.complete(
                        languageCode = languageCode,
                        preferredPlatform = preferredPlatform,
                        darkMode = darkMode,
                        accentKey = accentKey,
                        notificationsEnabled = notificationsEnabled
                    ) {
                        dialog.dismiss()
                        CommunityChannelsController(
                            activity = activity,
                            dp = dp,
                            makeText = makeText,
                            translate = { value -> LanguageTranslator.translate(value, languageCode) },
                            isDarkMode = isDarkMode,
                            red = { AccentPalette.color(accentKey) }
                        ).show {
                            settings.edit().putBoolean("community_channels_prompted", true).apply()
                            recreateActivity()
                        }
                    }
                }
            }
        }

        dialog.setContentView(root)
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialog.show()
        // The onboarding dialog is a separate Window, so explicitly skin its
        // complete content tree with the same selected palette as app screens.
        applyUnifiedSkin(root)
        dialog.window?.let { window ->
            window.setBackgroundDrawableResource(android.R.color.transparent)
            window.setLayout(-1, -1)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
            SystemBarPolicy.apply(window, darkMode = darkMode)
        }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safe = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(dp(22), dp(24) + safe.top, dp(22), dp(18) + safe.bottom)
            insets
        }
        androidx.core.view.ViewCompat.requestApplyInsets(root)
        // The setup's local theme choice is authoritative until it is saved.
        renderStep(0, true)
        dialog.window?.decorView?.let(applyMotionPreferences)
    }

}
