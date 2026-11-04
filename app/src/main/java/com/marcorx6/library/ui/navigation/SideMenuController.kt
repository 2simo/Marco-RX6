package com.marcorx6.library.ui

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

internal class SideMenuController(
    private val activity: Activity,
    private val dp: (Int) -> Int,
    private val makeText: (String, Float, Int, Boolean) -> TextView,
    private val tr: (String) -> String,
    private val isDark: () -> Boolean,
    private val language: () -> String,
    private val gold: Int,
    private val ink: Int,
    private val surface: Int,
    private val white: Int,
    private val gray: Int,
    private val syncing: () -> Boolean,
    private val isAdmin: () -> Boolean,
    private val sync: () -> Unit,
    private val showDownloads: () -> Unit,
    private val showNotifications: () -> Unit,
    private val showProfile: () -> Unit,
    private val signOut: () -> Unit,
    private val signIn: () -> Unit,
    private val showGameDialog: () -> Unit,
    private val showGameOfDay: () -> Unit,
    private val showLanguage: () -> Unit,
    private val showAppearance: () -> Unit,
    private val showTypography: () -> Unit,
    private val openLink: (String) -> Unit,
    private val applyMotion: (View) -> Unit,
    private val applySkin: (View) -> Unit
) {
    fun show() {
        val drawer = Dialog(activity)
        val panel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = if (language() == "ar") View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            setPadding(dp(20), dp(24), dp(20), dp(18))
            setBackground(GradientDrawable().apply {
                setColor(ink)
                cornerRadii = floatArrayOf(0f, 0f, dp(24).toFloat(), dp(24).toFloat(), dp(24).toFloat(), dp(24).toFloat(), 0f, 0f)
                setStroke(dp(1), Color.rgb(47, 117, 188))
            })
        }
        val top = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val brand = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(makeText("MARCO", 10f, Color.rgb(92, 174, 255), true))
        brand.addView(makeText("RX6", 27f, white, true))
        top.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(TextView(activity).apply {
            text = "×"; textSize = 30f; setTextColor(if (isDark()) Color.LTGRAY else Color.rgb(29, 58, 91)); gravity = Gravity.CENTER
            setPadding(dp(12), 0, dp(12), 0); setOnClickListener { drawer.dismiss() }
        })
        panel.addView(top)
        panel.addView(makeText("القائمة الرئيسية", 10f, gray, true).apply { setPadding(0, dp(24), 0, dp(12)) })
        fun menuItem(icon: String, label: String, detail: String = "", action: () -> Unit) {
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    if (isDark()) intArrayOf(Color.rgb(21, 48, 80), Color.rgb(10, 24, 43)) else intArrayOf(Color.WHITE, Color.rgb(237, 243, 252))).apply {
                    cornerRadius = dp(16).toFloat()
                    setStroke(dp(1), if (isDark()) Color.rgb(48, 119, 190) else Color.rgb(207, 220, 239))
                }
                elevation = dp(1).toFloat()
                setPadding(dp(14), dp(14), dp(14), dp(14))
                isClickable = true; isFocusable = true
                setOnClickListener { drawer.dismiss(); action() }
            }
            val iconView = TextView(activity).apply {
                text = icon; textSize = 17f; gravity = Gravity.CENTER
                setTextColor(if (isDark()) Color.WHITE else Color.rgb(25, 91, 157))
                background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    if (isDark()) intArrayOf(Color.rgb(38, 133, 230), Color.rgb(15, 70, 135)) else intArrayOf(Color.rgb(220, 239, 255), Color.rgb(195, 222, 250))).apply {
                    cornerRadius = dp(12).toFloat()
                    setStroke(dp(1), if (isDark()) Color.rgb(77, 157, 235) else Color.rgb(165, 204, 239))
                }
                setPadding(dp(11), dp(9), dp(11), dp(9)); minWidth = dp(42)
            }
            val labels = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            labels.addView(makeText(label, 14f, if (isDark()) white else Color.rgb(27, 43, 63), true))
            if (detail.isNotBlank()) labels.addView(makeText(detail, 10f, gray, false).apply { setPadding(0, dp(4), 0, 0) })
            row.addView(iconView, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(12) })
            row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
            panel.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
        }
        menuItem("⟳", tr("⟳  مزامنة"), if (syncing()) "المزامنة قيد التنفيذ" else "تحديث المقالات من المدونة", sync)
        menuItem("⬇", "مدير التنزيلات", "متابعة التنزيلات وحالاتها", showDownloads)
        menuItem("♧", tr("♧  التنبيهات"), "التحكم في إشعارات المقالات الجديدة", showNotifications)
        menuItem("♙", tr("حسابي"), tr("ملفي الشخصي"), showProfile)
        menuItem("♛", if (isAdmin()) tr("● مسؤول") else tr("دخول المسؤول  ↗"),
            if (isAdmin()) "تسجيل الخروج من حساب المسؤول" else "إدارة ونشر مقالات المدونة") {
            if (isAdmin()) signOut() else signIn()
        }
        if (isAdmin()) menuItem("＋", tr("＋  نشر"), "إضافة مقال جديد إلى المدونة", showGameDialog)
        menuItem("✧", "لعبة اليوم", "اكتشف اختيار MARCO RX6 اليومي", showGameOfDay)
        menuItem("文", tr("إعدادات اللغة"), tr("اختر لغة التطبيق"), showLanguage)
        menuItem("✦", "ألوان وحركات الواجهة", "تخصيص اللون الرئيسي وتأثيرات الحركة", showAppearance)
        menuItem("Aa", "الخط وحجم النص", "تخصيص قراءة وواجهة التطبيق", showTypography)
        menuItem("▶", "قناة YouTube", "@marco_rx7") { openLink("https://youtube.com/@marco_rx7") }
        menuItem("➤", "قناة Telegram", "@MARCO_RX1") { openLink("https://t.me/MARCO_RX1") }
        menuItem("ⓘ", "سياسة الخصوصية", "طريقة جمع واستخدام وحماية البيانات") {
            openLink("https://2simo.github.io/Marco-RX6/privacy-policy.html")
        }
        menuItem("⌫", "طلب حذف الحساب والبيانات", "طلب الحذف حتى إلا ما قدرتش تدخل للتطبيق") {
            openLink("https://2simo.github.io/Marco-RX6/delete-account.html")
        }
        panel.addView(View(activity), LinearLayout.LayoutParams(1, dp(12)))
        panel.addView(makeText("MARCO RX6  •  PREMIUM", 9f, if (isDark()) gray else Color.rgb(82, 108, 139), true).apply { gravity = Gravity.CENTER; setPadding(0, dp(16), 0, dp(4)) })
        val menuScroll = ScrollView(activity).apply {
            isFillViewport = true; isVerticalScrollBarEnabled = false
            addView(panel, ViewGroup.LayoutParams(-1, -2))
        }
        ViewCompat.setOnApplyWindowInsetsListener(menuScroll) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(0, safe.top, 0, safe.bottom); insets
        }
        applyMotion(menuScroll); applySkin(menuScroll)
        drawer.setContentView(menuScroll)
        val width = minOf(dp(360), activity.resources.displayMetrics.widthPixels - dp(24))
        drawer.window?.let { window ->
            window.setBackgroundDrawableResource(android.R.color.transparent)
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            val lp = window.attributes
            lp.width = width; lp.height = -1
            lp.gravity = if (language() == "ar") Gravity.RIGHT else Gravity.LEFT
            lp.dimAmount = 0.62f; window.attributes = lp
        }
        drawer.show()
        drawer.window?.setLayout(width, -1)
        menuScroll.requestApplyInsets()
    }
}
