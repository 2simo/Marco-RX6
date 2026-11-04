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
    private val saveCloudProfile: (String) -> Unit,
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
                if (dark) intArrayOf(Color.rgb(25, 111, 205), Color.rgb(14, 53, 101), Color.rgb(8, 26, 48))
                else intArrayOf(Color.rgb(226, 241, 255), Color.rgb(241, 247, 255))).apply {
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), if (dark) Color.rgb(64, 145, 224) else Color.rgb(173, 207, 237))
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
        box.addView(makeText(tr("اسم العرض"), 11f, Color.rgb(75, 165, 255), true))
        box.addView(input, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(6); bottomMargin = dp(14) })
        val library = games()
        val favoriteCount = library.count { it.isFavorite }
        val stats = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        fun statCard(value: String, label: String) = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = rounded(panel(), 16)
            setPadding(dp(10), dp(14), dp(10), dp(14))
            addView(makeText(value, 23f, Color.rgb(75, 165, 255), true))
            addView(makeText(label, 10f, gray(), false).apply { setPadding(0, dp(3), 0, 0) })
        }
        stats.addView(statCard(library.size.toString(), "الألعاب"), LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
        stats.addView(statCard(favoriteCount.toString(), "المفضلة"), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(6) })
        box.addView(stats, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })

        fun directoryBytes(directory: File?): Long {
            if (directory == null || !directory.exists()) return 0L
            return try {
                directory.walkTopDown().filter { it.isFile }.sumOf { file ->
                    try { file.length().coerceAtLeast(0L) } catch (_: Exception) { 0L }
                }
            } catch (_: Exception) { 0L }
        }
        fun readableSize(bytes: Long): String {
            val mb = bytes.toDouble() / (1024.0 * 1024.0)
            return if (mb >= 1024.0) String.format(java.util.Locale.ROOT, "%.2f GB", mb / 1024.0)
            else String.format(java.util.Locale.ROOT, "%.1f MB", mb)
        }
        val cacheBytes = directoryBytes(activity.cacheDir)
        val appFilesBytes = directoryBytes(activity.filesDir)
        val storageCard = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(panel(), 16)
            setPadding(dp(14), dp(13), dp(14), dp(13))
            addView(makeText("▤", 22f, Color.rgb(75, 165, 255), true))
            val copy = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, 0, 0)
                addView(makeText("مساحة التخزين", 13f, white(), true))
                addView(makeText("ملفات التطبيق: ${readableSize(appFilesBytes)}  •  Cache: ${readableSize(cacheBytes)}", 10f, gray(), false).apply {
                    setPadding(0, dp(3), 0, 0)
                })
            }
            addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
            addView(makeText("›", 22f, gray(), true))
            setOnClickListener {
                AlertDialog.Builder(activity)
                    .setTitle("مساحة التخزين")
                    .setMessage(
                        "ملفات التطبيق: ${readableSize(appFilesBytes)}\\n" +
                        "الملفات المؤقتة (Cache): ${readableSize(cacheBytes)}\\n\\n" +
                        "هذه أرقام الملفات المحلية التي يديرها التطبيق. لا يتم حذف أي ملف من هنا."
                    )
                    .setPositiveButton("إعدادات التخزين") { _, _ ->
                        try {
                            val intent = android.content.Intent(
                                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                android.net.Uri.parse("package:${activity.packageName}")
                            )
                            activity.startActivity(intent)
                        } catch (_: Exception) {
                            Toast.makeText(activity, "تعذر فتح إعدادات التطبيق", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("إغلاق", null)
                    .show()
            }
        }
        box.addView(storageCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        fun settingButton(icon: String, label: String, detail: String, action: () -> Unit) = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(panel(), 15)
            setPadding(dp(14), dp(13), dp(14), dp(13))
            addView(makeText(icon, 20f, Color.rgb(75, 165, 255), true).apply { gravity = Gravity.CENTER })
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
                val nickname = input.text.toString().trim().ifBlank { "Player" }
                prefs.edit().putString("nickname", nickname).putBoolean("profile_created", true).apply()
                saveCloudProfile(nickname)
                Toast.makeText(activity, "تم حفظ الملف الشخصي", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }
        dialog.show()
        styleAlertDialog(dialog)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(
                android.view.WindowManager.LayoutParams.MATCH_PARENT,
                android.view.WindowManager.LayoutParams.MATCH_PARENT
            )
        }
        applyUnifiedSkin(box)
    }
}
