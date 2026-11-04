package com.marcorx6.library.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.marcorx6.library.data.Game
import java.io.File

internal class ProfileController(
    private val activity: Activity,
    private val preferences: SharedPreferences,
    private val games: () -> List<Game>,
    private val dp: (Int) -> Int,
    private val rounded: (Int, Int) -> GradientDrawable,
    private val makeText: (String, Float, Int, Boolean) -> TextView,
    private val makeInput: (String) -> EditText,
    private val tr: (String) -> String,
    private val isDarkMode: () -> Boolean,
    private val panel: () -> Int,
    private val red: () -> Int,
    private val white: () -> Int,
    private val gray: () -> Int,
    private val showLanguagePicker: () -> Unit,
    private val showNotificationSettings: () -> Unit,
    private val showAccount: () -> Unit,
    private val chooseAvatar: () -> Unit,
    private val getActiveDialog: () -> AlertDialog?,
    private val setActiveDialog: (AlertDialog?) -> Unit,
    private val styleAlertDialog: (AlertDialog) -> Unit,
    private val applyUnifiedSkin: (android.view.View) -> Unit
) {
    fun show() {
        val prefs = preferences
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(10), dp(18), dp(8))
        }
        val dark = isDarkMode()
        val hero = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                if (dark) intArrayOf(Color.rgb(19, 91, 158), Color.rgb(12, 39, 67), Color.rgb(8, 26, 43))
                else intArrayOf(Color.rgb(226, 241, 255), Color.rgb(241, 247, 255))).apply {
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), if (dark) Color.rgb(65, 133, 194) else Color.rgb(173, 207, 237))
            }
        }
        val avatar = ImageView(activity).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(if (dark) Color.rgb(36, 65, 91) else Color.WHITE, 48)
            clipToOutline = true
            val path = prefs.getString("avatar_path", "").orEmpty()
            if (path.isNotBlank() && File(path).exists()) {
                android.graphics.BitmapFactory.decodeFile(path)?.let { setImageBitmap(it) }
            } else {
                setImageResource(android.R.drawable.ic_menu_camera)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
            }
        }
        hero.addView(avatar, LinearLayout.LayoutParams(dp(78), dp(78)))
        val identity = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
        }
        identity.addView(makeText("PLAYER PROFILE", 9f, if (dark) Color.rgb(151, 207, 255) else Color.rgb(34, 119, 190), true).apply { letterSpacing = 0.12f })
        identity.addView(makeText(prefs.getString("nickname", "Player") ?: "Player", 19f, if (dark) Color.WHITE else Color.rgb(22, 43, 65), true).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(5), 0, dp(3))
        })
        identity.addView(makeText("MARCO RX6  •  GAMER", 9f, if (dark) Color.rgb(191, 211, 230) else Color.rgb(79, 105, 129), true))
        hero.addView(identity, LinearLayout.LayoutParams(0, -2, 1f))
        hero.setOnClickListener {
            getActiveDialog()?.dismiss()
            chooseAvatar()
        }
        box.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        box.addView(makeText("اضغط على البطاقة لتغيير الصورة الشخصية", 10f, gray(), false).apply {
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(12))
        })
        val input = makeInput(tr("اسم العرض")).apply {
            setSingleLine(true)
            setText(prefs.getString("nickname", "Player") ?: "Player")
        }
        box.addView(makeText(tr("اسم العرض"), 11f, red(), true))
        box.addView(input, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(6); bottomMargin = dp(14) })
        val library = games()
        val favoriteCount = library.count { it.isFavorite }
        val stats = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        fun statCard(value: String, label: String) = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = rounded(panel(), 16)
            setPadding(dp(10), dp(14), dp(10), dp(14))
            addView(makeText(value, 23f, red(), true))
            addView(makeText(label, 10f, gray(), false).apply { setPadding(0, dp(3), 0, 0) })
        }
        stats.addView(statCard(library.size.toString(), "الألعاب"), LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
        stats.addView(statCard(favoriteCount.toString(), "المفضلة"), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(6) })
        box.addView(stats, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
        fun settingButton(icon: String, label: String, detail: String, action: () -> Unit) = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(panel(), 15)
            setPadding(dp(14), dp(13), dp(14), dp(13))
            addView(makeText(icon, 20f, red(), true).apply { gravity = Gravity.CENTER })
            val copy = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, 0, 0)
                addView(makeText(label, 13f, white(), true))
                addView(makeText(detail, 10f, gray(), false).apply { setPadding(0, dp(3), 0, 0) })
            }
            addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
            addView(makeText("‹", 22f, gray(), true))
            setOnClickListener { action() }
        }
        box.addView(settingButton("♙", "الحساب", "إنشاء حساب أو تسجيل الدخول • الاستخدام كزائر متاح") { showAccount() }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        box.addView(settingButton("文", "اللغة", "العربية • English • Français • Español") { showLanguagePicker() }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        box.addView(settingButton("♧", "الإشعارات", "التحكم في تنبيهات المقالات الجديدة") { showNotificationSettings() })
        val dialog = AlertDialog.Builder(activity)
            .setTitle(if (prefs.getBoolean("profile_created", false)) tr("ملفي الشخصي") else "إنشاء ملف اللاعب")
            .setView(ScrollView(activity).apply { isFillViewport = true; addView(box) })
            .setPositiveButton(tr("حفظ"), null)
            .setNegativeButton(tr("إغلاق"), null)
            .create()
        setActiveDialog(dialog)
        dialog.setOnDismissListener { if (getActiveDialog() === dialog) setActiveDialog(null) }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                prefs.edit().putString("nickname", input.text.toString().trim().ifBlank { "Player" }).putBoolean("profile_created", true).apply()
                Toast.makeText(activity, "تم حفظ الملف الشخصي", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }
        dialog.show()
        styleAlertDialog(dialog)
        applyUnifiedSkin(box)
    }
}
