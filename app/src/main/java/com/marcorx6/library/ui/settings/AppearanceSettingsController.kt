package com.marcorx6.library.ui

import android.app.Activity
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
    private val showTypographySettings: () -> Unit,
    private val setContentView: (View) -> Unit,
    private val onBack: () -> Unit,
    private val translate: (String) -> String
) {
    fun show() {
        fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
        val dark = preferences.getBoolean("dark_mode", true)
        val bg = if (dark) Color.rgb(7, 17, 31) else Color.rgb(242, 247, 253)
        val surface = if (dark) Color.rgb(15, 35, 58) else Color.WHITE
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
            layoutDirection = if (translate("الإعدادات والمظهر") == "الإعدادات والمظهر") View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            setPadding(dp(20), dp(20), dp(20), dp(20))
            setBackgroundColor(bg)
        }
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val heading = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        heading.addView(TextView(activity).apply {
            text = translate(translate("MARCO RX6  /  PERSONALIZATION"))
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
        header.addView(TextView(activity).apply {
            text = "×"
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(muted)
            addGlassTouch(this)
            setOnClickListener { onBack() }
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        root.addView(header)
        root.addView(TextView(activity).apply {
            text = translate(translate("خصص تجربة MARCO RX6 بالطريقة التي تناسبك"))
            textSize = 12f
            setTextColor(muted)
            setPadding(0, dp(8), 0, dp(22))
        })
        val colorCard = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                setColor(if (dark) Color.argb(190, 15, 43, 70) else Color.argb(198, 255, 255, 255)); cornerRadius = dp(18).toFloat()
                setStroke(dp(1), if (dark) Color.rgb(42, 82, 126) else Color.rgb(198, 220, 244))
            }
        }
        colorCard.addView(TextView(activity).apply {
            text = translate(translate("اللون الرئيسي"))
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ink)
        })
        colorCard.addView(TextView(activity).apply {
            text = translate(translate("أزرق كهربائي  •  اللون الرسمي للتطبيق"))
            textSize = 12f
            setTextColor(muted)
            setPadding(0, dp(8), 0, dp(12))
        })
        colorCard.addView(TextView(activity).apply {
            text = translate(translate("●   أزرق"))
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
                setColor(if (dark) Color.argb(190, 15, 35, 58) else Color.argb(198, 255, 255, 255)); cornerRadius = dp(18).toFloat()
                setStroke(dp(1), if (dark) Color.rgb(42, 82, 126) else Color.rgb(198, 220, 244))
            }
        }
        animations.addView(TextView(activity).apply {
            text = translate(translate("حركات وانتقالات الواجهة"))
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ink)
        })
        val toggle = TextView(activity).apply {
            text = translate(if (preferences.getBoolean("animations", true)) translate("الحركات مفعّلة  ✓") else translate("الحركات متوقفة"))
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
                text = translate(if (enabled) translate("الحركات مفعّلة  ✓") else translate("الحركات متوقفة"))
                setTextColor(if (enabled) Color.WHITE else blue)
                background = GradientDrawable().apply {
                    setColor(if (enabled) Color.argb(218, 42, 145, 255) else if (dark) Color.argb(190, 15, 35, 58) else Color.argb(210, 255, 255, 255))
                    cornerRadius = dp(14).toFloat()
                    if (!enabled) setStroke(dp(1), blue)
                }
                Toast.makeText(activity, translate(if (enabled) translate("تم تفعيل انتقالات الواجهة") else translate("تم إيقاف انتقالات الواجهة")), Toast.LENGTH_SHORT).show()
            }
        }
        animations.addView(toggle, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        root.addView(animations)

        fun settingRow(title: String, description: String, action: () -> Unit) =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(14), dp(16), dp(14))
                background = GradientDrawable().apply {
                    setColor(if (dark) Color.argb(190, 15, 43, 70) else Color.argb(198, 255, 255, 255))
                    cornerRadius = dp(17).toFloat()
                    setStroke(dp(1), if (dark) Color.rgb(42, 82, 126) else Color.rgb(198, 220, 244))
                }
                isClickable = true
                isFocusable = true
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
            translate(translate("المظهر")),
            translate(if (dark) translate("الوضع الليلي مفعّل — اضغط للتبديل إلى الوضع النهاري")
            else translate("الوضع النهاري مفعّل — اضغط للتبديل إلى الوضع الليلي"))
        ) {
            preferences.edit().putBoolean("dark_mode", !dark).apply()
            recreateActivity()
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        root.addView(settingRow(translate(translate("اللغة")), translate(translate("العربية والإنجليزية والفرنسية والإسبانية")), showLanguagePicker),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        root.addView(settingRow(translate(translate("الإشعارات")), translate(translate("التحكم في تنبيهات المقالات الجديدة")), showNotificationSettings),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        root.addView(settingRow(translate(translate("الخط وحجم النص")), translate(translate("تخصيص عائلة الخط وحجم القراءة")), showTypographySettings),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        root.addView(TextView(activity).apply {
            text = translate(translate("MARCO RX6  •  BLUE EXPERIENCE"))
            textSize = 9f
            gravity = Gravity.CENTER
            setTextColor(muted)
            setPadding(0, dp(24), 0, dp(10))
        })
        val scroll = android.widget.ScrollView(activity).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            addView(root, android.widget.ScrollView.LayoutParams(-1, -2))
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
