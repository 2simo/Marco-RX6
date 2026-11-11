package com.marcorx6.library.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.app.AlertDialog
import android.widget.EditText
import android.content.SharedPreferences
import android.view.View
import android.widget.Toast
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

internal class AppearanceSettingsController(
    private val activity: Activity,
    private val preferences: SharedPreferences,
    private val currentLanguage: () -> String,
    private val applyMotionPreferences: (View) -> Unit,
    private val recreateActivity: () -> Unit,
    private val showLanguagePicker: () -> Unit,
    private val showNotificationSettings: () -> Unit,
    private val launchDownloadFolderPicker: () -> Unit,
    private val downloadFolderName: () -> String,
    private val adminSessionConnected: () -> Boolean,
    private val onAdminSessionAction: () -> Unit,
    private val showTypographySettings: () -> Unit,
    private val setContentView: (View) -> Unit,
    private val onBack: () -> Unit,
    private val translate: (String) -> String
) {
    fun show() {
        fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
        val dark = preferences.getBoolean("dark_mode", true)
        val selectedAccentId = preferences.getString(AccentPalette.PREFERENCE_KEY, AccentPalette.DEFAULT_ID)
            ?.takeIf { id -> AccentPalette.options.any { it.id == id } } ?: AccentPalette.DEFAULT_ID
        val silverAccent = AccentPalette.color(selectedAccentId)
        val accentText = AccentPalette.readableAccentText(selectedAccentId, dark)
        val bg = if (dark) AccentPalette.surface(Color.rgb(8, 9, 12), silverAccent, 0.20f, selectedAccentId)
            else AccentPalette.surface(Color.rgb(245, 247, 250), silverAccent, 0.10f, selectedAccentId)
        val panelSurface = if (dark) AccentPalette.surface(Color.rgb(25, 28, 34), silverAccent, 0.30f, selectedAccentId)
            else AccentPalette.surface(Color.rgb(224, 229, 237), silverAccent, 0.20f, selectedAccentId)
        val ink = if (dark) Color.WHITE else Color.rgb(25, 29, 36)
        val muted = if (dark) Color.rgb(178, 184, 194) else Color.rgb(91, 98, 110)
        fun addGlassTouch(view: View) {
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                view.foreground = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(
                        Color.argb(if (dark) 72 else 55, Color.red(silverAccent), Color.green(silverAccent), Color.blue(silverAccent))
                    ),
                    null,
                    android.graphics.drawable.ColorDrawable(Color.WHITE)
                )
            }
        }
        fun sectionLabel(value: String) = TextView(activity).apply {
            text = translate(value)
            textSize = 10f
            letterSpacing = 0.10f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(accentText)
            setPadding(dp(3), dp(18), dp(3), dp(8))
        }
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val rtl = currentLanguage() == "ar"
            layoutDirection = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            textDirection = if (rtl) View.TEXT_DIRECTION_RTL else View.TEXT_DIRECTION_LTR
            setPadding(dp(20), dp(20), dp(20), dp(20))
            setBackgroundColor(bg)
        }
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val heading = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        heading.addView(TextView(activity).apply {
            text = translate("MARCO  /  PERSONALIZATION")
            textSize = 9f
            letterSpacing = 0.12f
            setTextColor(silverAccent)
        })
        heading.addView(TextView(activity).apply {
            text = translate("الإعدادات والمظهر")
            textSize = 25f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ink)
            setPadding(0, dp(5), 0, 0)
        })
        header.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(android.widget.ImageView(activity).apply {
            setImageResource(com.marcorx6.library.R.drawable.ic_action_back)
            imageTintList = android.content.res.ColorStateList.valueOf(accentText)
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            contentDescription = translate("رجوع")
            minimumWidth = dp(48)
            minimumHeight = dp(48)
            addGlassTouch(this)
            setOnClickListener { onBack() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(header)
        root.addView(TextView(activity).apply {
            text = translate("خصص تجربة MARCO بالطريقة التي تناسبك")
            textSize = 12f
            setTextColor(muted)
            setPadding(0, dp(8), 0, dp(22))
        })
        root.addView(sectionLabel("المظهر والحركة"), LinearLayout.LayoutParams(-1, -2))
        val colorCard = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                setColors(if (selectedAccentId == "black_silver") intArrayOf(
                    Color.rgb(7, 8, 11),
                    Color.rgb(82, 91, 106),
                    Color.rgb(197, 206, 219)
                ) else if (dark) intArrayOf(
                    AccentPalette.surface(Color.rgb(35, 38, 45), silverAccent, 0.28f, selectedAccentId),
                    AccentPalette.surface(Color.rgb(15, 17, 22), silverAccent, 0.22f, selectedAccentId)
                ) else intArrayOf(
                    AccentPalette.surface(Color.WHITE, silverAccent, 0.16f, selectedAccentId),
                    panelSurface
                ))
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), Color.argb(175, Color.red(silverAccent), Color.green(silverAccent), Color.blue(silverAccent)))
            }
        }
        val paletteCardInk = if (selectedAccentId == "black_silver") Color.WHITE else ink
        val paletteCardMuted = if (selectedAccentId == "black_silver") Color.rgb(225, 230, 239) else muted
        colorCard.addView(TextView(activity).apply {
            text = translate("اللون الرئيسي")
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(paletteCardInk)
        })
        colorCard.addView(TextView(activity).apply {
            text = translate("غيّر اللون الأساسي لواجهات التطبيق والبطاقات والأزرار والأيقونات والعناصر التفاعلية")
            textSize = 12f
            setTextColor(paletteCardMuted)
            setPadding(0, dp(8), 0, dp(12))
        })
        colorCard.addView(TextView(activity).apply {
            text = translate("اختر اللون الأساسي للتطبيق")
            textSize = 12f
            setTextColor(paletteCardMuted)
            setPadding(0, dp(8), 0, dp(12))
        })
        val paletteRows = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        AccentPalette.options.chunked(4).forEach { rowOptions ->
            val paletteRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            rowOptions.forEach { option ->
                val isSelected = option.id == selectedAccentId
                val swatch = TextView(activity).apply {
                    // Preserve each option's own preview color during global theming.
                    tag = "marco_palette_swatch"
                    text = if (isSelected) "✓" else ""
                    textSize = 19f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    gravity = Gravity.CENTER
                    setTextColor(AccentPalette.foreground(option.id))
                    contentDescription = translate(option.label)
                    background = GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        if (option.id == "black_silver") intArrayOf(
                            Color.rgb(7, 8, 11),
                            Color.rgb(82, 91, 106),
                            Color.rgb(197, 206, 219)
                        ) else intArrayOf(option.color, option.color)
                    ).apply {
                        cornerRadius = dp(15).toFloat()
                        setStroke(dp(if (isSelected) 3 else 1),
                            if (isSelected) AccentPalette.foreground(option.id)
                            else if (dark) Color.argb(110, 190, 198, 210) else Color.argb(130, 105, 113, 126))
                    }
                    isClickable = true
                    isFocusable = true
                    minimumHeight = dp(44)
                    setOnClickListener {
                        if (preferences.getString(AccentPalette.PREFERENCE_KEY, AccentPalette.DEFAULT_ID) != option.id) {
                            preferences.edit().putString(AccentPalette.PREFERENCE_KEY, option.id).apply()
                            // Rebuild this live settings page immediately. setContentView routes
                            // through MainActivity.showRootScreen, which skins the whole Activity
                            // tree using the newly saved palette; no app restart is needed.
                            show()
                        }
                    }
                }
                val item = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(dp(5), dp(4), dp(5), dp(4))
                    addView(swatch, LinearLayout.LayoutParams(dp(48), dp(44)))
                    addView(TextView(activity).apply {
                        text = translate(option.label)
                        textSize = 10f
                        gravity = Gravity.CENTER
                        setTextColor(if (isSelected) accentText else muted)
                        setPadding(0, dp(5), 0, 0)
                        maxLines = 1
                    }, LinearLayout.LayoutParams(-1, -2))
                }
                paletteRow.addView(item, LinearLayout.LayoutParams(0, -2, 1f))
            }
            repeat(4 - rowOptions.size) {
                paletteRow.addView(View(activity), LinearLayout.LayoutParams(0, dp(1), 1f))
            }
            paletteRows.addView(paletteRow, LinearLayout.LayoutParams(-1, -2))
        }
        colorCard.addView(paletteRows, LinearLayout.LayoutParams(-1, -2))
        root.addView(colorCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        val animations = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                setColors(if (dark) intArrayOf(Color.argb(225, 35, 38, 45), Color.argb(215, 15, 17, 22)) else intArrayOf(Color.argb(232, 255, 255, 255), Color.argb(216, 222, 226, 232)))
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), if (dark) Color.argb(165, 130, 138, 151) else Color.argb(175, 160, 168, 181))
            }
        }
        animations.addView(TextView(activity).apply {
            text = translate("حركات وانتقالات الواجهة")
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ink)
        })
        val toggle = TextView(activity).apply {
            contentDescription = translate("تبديل حركات وانتقالات الواجهة")
            minimumHeight = dp(48)
            text = translate(if (preferences.getBoolean("animations", true)) "الحركات مفعّلة  ✓" else "الحركات متوقفة").replace("✓", "").trim()
            if (preferences.getBoolean("animations", true)) {
                setCompoundDrawablesRelativeWithIntrinsicBounds(com.marcorx6.library.R.drawable.ic_action_check, 0, 0, 0)
                compoundDrawablePadding = dp(6)
            }
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(if (preferences.getBoolean("animations", true)) AccentPalette.foreground(selectedAccentId) else accentText)
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = GradientDrawable().apply {
                setColor(if (preferences.getBoolean("animations", true)) Color.argb(218, Color.red(silverAccent), Color.green(silverAccent), Color.blue(silverAccent)) else if (dark) Color.argb(190, 25, 28, 34) else Color.argb(210, 255, 255, 255))
                cornerRadius = dp(14).toFloat()
                if (!preferences.getBoolean("animations", true)) setStroke(dp(1), silverAccent)
            }
            addGlassTouch(this)
            setOnClickListener {
                val enabled = !preferences.getBoolean("animations", true)
                preferences.edit().putBoolean("animations", enabled).apply()
                applyMotionPreferences(activity.window.decorView)
                text = translate(if (enabled) "الحركات مفعّلة  ✓" else "الحركات متوقفة").replace("✓", "").trim()
                setCompoundDrawablesRelativeWithIntrinsicBounds(if (enabled) com.marcorx6.library.R.drawable.ic_action_check else 0, 0, 0, 0)
                contentDescription = translate(if (enabled) "إيقاف حركات وانتقالات الواجهة" else "تفعيل حركات وانتقالات الواجهة")
                setTextColor(if (enabled) AccentPalette.foreground(selectedAccentId) else silverAccent)
                background = GradientDrawable().apply {
                    setColor(if (enabled) Color.argb(218, Color.red(silverAccent), Color.green(silverAccent), Color.blue(silverAccent)) else if (dark) Color.argb(190, 25, 28, 34) else Color.argb(210, 255, 255, 255))
                    cornerRadius = dp(14).toFloat()
                    if (!enabled) setStroke(dp(1), silverAccent)
                }
                Toast.makeText(activity, translate(if (enabled) "تم تفعيل انتقالات الواجهة" else "تم إيقاف انتقالات الواجهة"), Toast.LENGTH_SHORT).show()
            }
        }
        animations.addView(toggle, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        root.addView(animations)

        fun settingRow(title: String, description: String, action: () -> Unit) =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(14), dp(16), dp(14))
                background = GradientDrawable().apply {
                    setColors(if (dark) intArrayOf(Color.argb(220, 35, 38, 45), Color.argb(207, 15, 17, 22)) else intArrayOf(Color.argb(232, 255, 255, 255), Color.argb(216, 222, 226, 232)))
                    cornerRadius = dp(18).toFloat()
                    setStroke(dp(1), if (dark) Color.argb(165, 130, 138, 151) else Color.argb(175, 160, 168, 181))
                }
                isClickable = true
                isFocusable = true
                contentDescription = "$title. $description"
                elevation = dp(2).toFloat()
                addView(TextView(activity).apply {
                    text = title
                    textSize = 15f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(ink)
                })
                addView(TextView(activity).apply {
                    text = description
                    textSize = 11f
                    setTextColor(muted)
                    setPadding(0, dp(5), 0, 0)
                })
                addGlassTouch(this)
                setOnClickListener { action() }
            }
        root.addView(settingRow(
            translate("المظهر"),
            translate(if (dark) "الوضع الليلي مفعّل — اضغط للتبديل إلى الوضع النهاري"
            else "الوضع النهاري مفعّل — اضغط للتبديل إلى الوضع الليلي")
        ) {
            preferences.edit().putBoolean("dark_mode", !dark).apply()
            recreateActivity()
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        root.addView(sectionLabel("الخصوصية والتنزيلات"), LinearLayout.LayoutParams(-1, -2))
        val adBlockerEnabled = preferences.getBoolean("download_page_ad_blocker", true)
        root.addView(settingRow(
            translate("حجب الإعلانات في صفحة التحميل"),
            translate(if (adBlockerEnabled) "مفعّل — حجب الإعلانات المعروفة داخل صفحة التحميل الذكية" else "متوقف — السماح بمحتوى الإعلانات داخل صفحة التحميل")
        ) {
            val enabled = !preferences.getBoolean("download_page_ad_blocker", true)
            preferences.edit().putBoolean("download_page_ad_blocker", enabled).apply()
            Toast.makeText(activity, translate(if (enabled) "تم تفعيل حجب الإعلانات" else "تم إيقاف حجب الإعلانات"), Toast.LENGTH_SHORT).show()
            show()
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        root.addView(sectionLabel("اللغة والمجتمع"), LinearLayout.LayoutParams(-1, -2))
        root.addView(settingRow(translate("اللغة"), translate("العربية والإنجليزية والفرنسية والإسبانية"), showLanguagePicker),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        root.addView(settingRow(
            translate("قناة Telegram الرسمية"),
            translate("تابع أخبار وإعلانات MARCO على Telegram")
        ) {
            runCatching {
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(CommunityChannelsController.TELEGRAM_URL)))
            }.onFailure {
                Toast.makeText(activity, translate("تعذر فتح الرابط"), Toast.LENGTH_SHORT).show()
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        root.addView(settingRow(
            translate("قناة YouTube الرسمية"),
            translate("شاهد فيديوهات وشروحات MARCO على YouTube")
        ) {
            runCatching {
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(CommunityChannelsController.YOUTUBE_URL)))
            }.onFailure {
                Toast.makeText(activity, translate("تعذر فتح الرابط"), Toast.LENGTH_SHORT).show()
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        root.addView(settingRow(
            translate("التحقق من تحديث التطبيق"),
            translate("تحقق من وجود إصدار أحدث من MARCO على GitHub")
        ) {
            AppUpdateChecker(activity, translate).check()
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        root.addView(settingRow(
            translate("استثناء مواقع من حجب الإعلانات"),
            translate("أضف النطاقات المسموح بها مفصولة بفاصلة، مثلاً example.com"),
        ) {
            val input = EditText(activity).apply {
                setSingleLine(false)
                minLines = 2
                setText(preferences.getString("download_page_ad_allowlist", "").orEmpty())
                hint = "example.com, files.example.org"
                setPadding(dp(14), dp(12), dp(14), dp(12))
            }
            AlertDialog.Builder(activity)
                .setTitle(translate("المواقع المستثناة"))
                .setMessage(translate("لن يتم حجب الإعلانات أو الموارد من هذه النطاقات داخل صفحة التحميل."))
                .setView(input)
                .setNegativeButton(translate("إلغاء"), null)
                .setPositiveButton(translate("حفظ")) { _, _ ->
                    val domains = input.text.toString().split(',', ';')
                        .map { it.trim().lowercase() }
                        .filter { it.matches(Regex("[a-z0-9.-]+")) && !it.startsWith(".") && !it.endsWith(".") }
                        .distinct()
                    preferences.edit().putString("download_page_ad_allowlist", domains.joinToString(",")).apply()
                    Toast.makeText(activity, translate("تم حفظ استثناءات المواقع"), Toast.LENGTH_SHORT).show()
                    show()
                }
                .show()
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        root.addView(sectionLabel("إشعارات التطبيق"), LinearLayout.LayoutParams(-1, -2))
        root.addView(settingRow(translate("الإشعارات"), translate("التحكم في تنبيهات المقالات الجديدة"), showNotificationSettings),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        root.addView(sectionLabel("الحساب والصلاحيات"), LinearLayout.LayoutParams(-1, -2))
        root.addView(settingRow(
            translate(if (adminSessionConnected()) "فصل حساب المشرف" else "ربط حساب المشرف (اختياري)"),
            translate(if (adminSessionConnected()) "حساب المشرف مربوط؛ صلاحيات Blogger للنشر وDrive لرفع الأغلفة مفعّلة" else "تسجيل Google العادي لا يفعّل النشر. اربط هنا حساب Blogger المشرف نفسه لتفعيل النشر ورفع الأغلفة"),
            onAdminSessionAction
        ), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        root.addView(sectionLabel("التخصيص والتخزين"), LinearLayout.LayoutParams(-1, -2))
        root.addView(settingRow(translate("الخط وحجم النص"), translate("تخصيص عائلة الخط وحجم القراءة"), showTypographySettings),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        root.addView(settingRow(translate("التخزين"), translate("عرض مساحة التطبيق والملفات المؤقتة وإدارة ذاكرة التخزين المؤقت")) {
            StorageSettingsController(
                activity, setContentView, { show() }, translate,
                launchDownloadFolderPicker, downloadFolderName
            ).show()
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        root.addView(sectionLabel("التشخيص وسجل الأخطاء"), LinearLayout.LayoutParams(-1, -2))
        root.addView(settingRow(
            translate("تصدير سجل التشخيص"),
            translate("احفظ سجل الأعطال والأحداث محلياً وشاركه عند الحاجة؛ لا يتم إرساله تلقائياً")
        ) {
            runCatching {
                val exportFile = com.marcorx6.library.AppDiagnostics.createExport(activity)
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    activity,
                    "${activity.packageName}.files",
                    exportFile
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                activity.startActivity(Intent.createChooser(shareIntent, translate("مشاركة سجل التشخيص")))
            }.onFailure {
                Toast.makeText(activity, translate("تعذر إنشاء سجل التشخيص"), Toast.LENGTH_LONG).show()
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        root.addView(TextView(activity).apply {
            text = "MARCO  •  BLACK / SILVER EXPERIENCE"
            textSize = 9f
            gravity = Gravity.CENTER
            setTextColor(muted)
            setPadding(0, dp(24), 0, dp(10))
        })
        applyMotionPreferences(root)
        // The enclosing screen transition is applied once by MainActivity.showRootScreen.
        val scroll = android.widget.ScrollView(activity).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            addView(root, LinearLayout.LayoutParams(-1, -2))
        }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val safe = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars() or
                    androidx.core.view.WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(0, safe.top, 0, safe.bottom)
            insets
        }
        setContentView(scroll)
        activity.window.statusBarColor = Color.TRANSPARENT
        activity.window.navigationBarColor = Color.TRANSPARENT
        SystemBarPolicy.apply(activity.window, dark)
        androidx.core.view.ViewCompat.requestApplyInsets(scroll)
    }
}
