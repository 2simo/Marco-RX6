package com.marcorx6.library.ui

import android.app.Activity
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
        val bg = if (dark) Color.rgb(7, 17, 31) else Color.rgb(242, 247, 253)
        val blue = Color.rgb(42, 145, 255)
        val ink = if (dark) Color.WHITE else Color.rgb(22, 43, 65)
        val muted = if (dark) Color.rgb(164, 184, 207) else Color.rgb(91, 112, 137)
        fun addGlassTouch(view: View) {
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                view.foreground = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(
                        if (dark) Color.argb(82, 76, 169, 255) else Color.argb(55, 35, 126, 224)
                    ),
                    null,
                    android.graphics.drawable.ColorDrawable(Color.WHITE)
                )
            }
        }
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = if (translate("العربية") == "العربية") View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            setPadding(dp(20), dp(20), dp(20), dp(20))
            setBackgroundColor(bg)
        }
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val heading = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        heading.addView(TextView(activity).apply {
            text = "MARCO RX6  /  PERSONALIZATION"
            textSize = 9f
            letterSpacing = 0.12f
            setTextColor(blue)
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
            imageTintList = android.content.res.ColorStateList.valueOf(blue)
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            contentDescription = translate("رجوع")
            minimumWidth = dp(48)
            minimumHeight = dp(48)
            addGlassTouch(this)
            setOnClickListener { onBack() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(header)
        root.addView(TextView(activity).apply {
            text = translate("خصص تجربة MARCO RX6 بالطريقة التي تناسبك")
            textSize = 12f
            setTextColor(muted)
            setPadding(0, dp(8), 0, dp(22))
        })
        val colorCard = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                setColors(if (dark) intArrayOf(Color.argb(222, 23, 68, 110), Color.argb(208, 8, 27, 47)) else intArrayOf(Color.argb(232, 255, 255, 255), Color.argb(216, 224, 240, 255)))
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), if (dark) Color.argb(165, 80, 151, 215) else Color.argb(175, 153, 195, 236))
            }
        }
        colorCard.addView(TextView(activity).apply {
            text = translate("اللون الرئيسي")
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ink)
        })
        colorCard.addView(TextView(activity).apply {
            text = translate("أزرق كهربائي  •  اللون الرسمي للتطبيق")
            textSize = 12f
            setTextColor(muted)
            setPadding(0, dp(8), 0, dp(12))
        })
        colorCard.addView(TextView(activity).apply {
            text = translate("●   أزرق")
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(15), dp(14), dp(15), dp(14))
            background = GradientDrawable().apply {
                setColor(blue); cornerRadius = dp(14).toFloat()
            }
        })
        root.addView(colorCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        val animations = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                setColors(if (dark) intArrayOf(Color.argb(222, 23, 68, 110), Color.argb(208, 8, 27, 47)) else intArrayOf(Color.argb(232, 255, 255, 255), Color.argb(216, 224, 240, 255)))
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), if (dark) Color.argb(165, 80, 151, 215) else Color.argb(175, 153, 195, 236))
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
            text = translate(if (preferences.getBoolean("animations", true)) "الحركات مفعّلة  ✓" else "الحركات متوقفة")
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(if (preferences.getBoolean("animations", true)) Color.WHITE else blue)
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = GradientDrawable().apply {
                setColor(if (preferences.getBoolean("animations", true)) Color.argb(218, 42, 145, 255) else if (dark) Color.argb(190, 15, 35, 58) else Color.argb(210, 255, 255, 255))
                cornerRadius = dp(14).toFloat()
                if (!preferences.getBoolean("animations", true)) setStroke(dp(1), blue)
            }
            addGlassTouch(this)
            setOnClickListener {
                val enabled = !preferences.getBoolean("animations", true)
                preferences.edit().putBoolean("animations", enabled).apply()
                applyMotionPreferences(activity.window.decorView)
                text = translate(if (enabled) "الحركات مفعّلة  ✓" else "الحركات متوقفة")
                contentDescription = translate(if (enabled) "إيقاف حركات وانتقالات الواجهة" else "تفعيل حركات وانتقالات الواجهة")
                setTextColor(if (enabled) Color.WHITE else blue)
                background = GradientDrawable().apply {
                    setColor(if (enabled) Color.argb(218, 42, 145, 255) else if (dark) Color.argb(190, 15, 35, 58) else Color.argb(210, 255, 255, 255))
                    cornerRadius = dp(14).toFloat()
                    if (!enabled) setStroke(dp(1), blue)
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
                    setColors(if (dark) intArrayOf(Color.argb(220, 21, 63, 102), Color.argb(207, 8, 27, 47)) else intArrayOf(Color.argb(232, 255, 255, 255), Color.argb(216, 224, 240, 255)))
                    cornerRadius = dp(18).toFloat()
                    setStroke(dp(1), if (dark) Color.argb(165, 80, 151, 215) else Color.argb(175, 153, 195, 236))
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
        root.addView(settingRow(translate("اللغة"), translate("العربية والإنجليزية والفرنسية والإسبانية"), showLanguagePicker),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
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
        root.addView(settingRow(translate("الإشعارات"), translate("التحكم في تنبيهات المقالات الجديدة"), showNotificationSettings),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        root.addView(settingRow(
            translate(if (adminSessionConnected()) "فصل حساب المشرف" else "ربط حساب المشرف (اختياري)"),
            translate(if (adminSessionConnected()) "حساب المشرف مربوط؛ صلاحيات Blogger للنشر وDrive لرفع الأغلفة مفعّلة" else "تسجيل Google العادي لا يفعّل النشر. اربط هنا حساب Blogger المشرف نفسه لتفعيل النشر ورفع الأغلفة"),
            onAdminSessionAction
        ), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        root.addView(settingRow(translate("الخط وحجم النص"), translate("تخصيص عائلة الخط وحجم القراءة"), showTypographySettings),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        root.addView(settingRow(translate("التخزين"), translate("عرض مساحة التطبيق والملفات المؤقتة وإدارة ذاكرة التخزين المؤقت")) {
            StorageSettingsController(
                activity, setContentView, { show() }, translate,
                launchDownloadFolderPicker, downloadFolderName
            ).show()
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        root.addView(TextView(activity).apply {
            text = "MARCO RX6  •  BLUE EXPERIENCE"
            textSize = 9f
            gravity = Gravity.CENTER
            setTextColor(muted)
            setPadding(0, dp(24), 0, dp(10))
        })
        applyMotionPreferences(root)
        if (preferences.getBoolean("animations", true)) {
            root.alpha = 0f
            root.translationY = dp(10).toFloat()
            root.animate().alpha(1f).translationY(0f).setDuration(260L).start()
        }
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
        androidx.core.view.WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
            show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
        androidx.core.view.ViewCompat.requestApplyInsets(scroll)
    }
}
