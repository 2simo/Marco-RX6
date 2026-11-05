package com.marcorx6.library.ui

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import java.util.Locale

/** Shows app storage usage and only offers clearing disposable cache files. */
internal class StorageSettingsController(
    private val activity: Activity,
    private val setContentView: (View) -> Unit,
    private val onBack: () -> Unit,
    private val translate: (String) -> String
) {
    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()

    private fun directorySize(file: File?): Long {
        if (file == null || !file.exists()) return 0L
        if (file.isFile) return file.length()
        return file.listFiles()?.sumOf { directorySize(it) } ?: 0L
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB")
        var value = bytes.toDouble()
        var index = -1
        do {
            value /= 1024.0
            index++
        } while (value >= 1024.0 && index < units.lastIndex)
        return String.format(Locale.getDefault(), "%.1f %s", value, units[index])
    }

    fun show() {
        val dark = activity.getSharedPreferences("appearance_settings", Activity.MODE_PRIVATE)
            .getBoolean("dark_mode", true)
        val bg = if (dark) Color.rgb(7, 17, 31) else Color.rgb(242, 247, 253)
        val ink = if (dark) Color.WHITE else Color.rgb(22, 43, 65)
        val muted = if (dark) Color.rgb(164, 184, 207) else Color.rgb(91, 112, 137)
        val blue = Color.rgb(42, 145, 255)

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(22))
            setBackgroundColor(bg)
        }
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val heading = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        heading.addView(TextView(activity).apply {
            text = "MARCO RX6  /  STORAGE"
            textSize = 9f
            letterSpacing = 0.12f
            setTextColor(blue)
        })
        heading.addView(TextView(activity).apply {
            text = translate("التخزين")
            textSize = 25f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ink)
        })
        header.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(TextView(activity).apply {
            text = "×"
            contentDescription = translate("رجوع")
            textSize = 30f
            gravity = Gravity.CENTER
            minWidth = dp(48)
            minHeight = dp(48)
            setTextColor(muted)
            setOnClickListener { onBack() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(header)
        root.addView(TextView(activity).apply {
            text = translate("مساحة التطبيق والملفات المؤقتة")
            textSize = 12f
            setTextColor(muted)
            setPadding(0, dp(8), 0, dp(18))
        })

        val usageCard = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(17), dp(17), dp(17), dp(17))
            background = GradientDrawable().apply {
                setColor(if (dark) Color.rgb(15, 39, 64) else Color.WHITE)
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), if (dark) Color.rgb(53, 105, 158) else Color.rgb(175, 207, 239))
            }
        }
        val total = TextView(activity).apply {
            textSize = 25f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ink)
        }
        usageCard.addView(TextView(activity).apply {
            text = translate("إجمالي ملفات التطبيق")
            textSize = 13f
            setTextColor(muted)
        })
        usageCard.addView(total.apply { setPadding(0, dp(6), 0, dp(14)) })
        val details = TextView(activity).apply {
            textSize = 12f
            setLineSpacing(dp(7).toFloat(), 1f)
            setTextColor(muted)
        }
        usageCard.addView(details)
        root.addView(usageCard, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(14)
        })

        val cacheCard = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(17), dp(16), dp(17), dp(16))
            background = GradientDrawable().apply {
                setColor(if (dark) Color.rgb(15, 39, 64) else Color.WHITE)
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), if (dark) Color.rgb(53, 105, 158) else Color.rgb(175, 207, 239))
            }
        }
        cacheCard.addView(TextView(activity).apply {
            text = translate("ذاكرة التخزين المؤقت")
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ink)
        })
        val cacheValue = TextView(activity).apply {
            textSize = 12f
            setTextColor(muted)
            setPadding(0, dp(6), 0, dp(13))
        }
        cacheCard.addView(cacheValue)
        val clear = TextView(activity).apply {
            text = translate("مسح الملفات المؤقتة")
            textSize = 13f
            gravity = Gravity.CENTER
            minHeight = dp(48)
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(blue)
                cornerRadius = dp(14).toFloat()
            }
            setOnClickListener {
                AlertDialog.Builder(activity)
                    .setTitle(translate("مسح الملفات المؤقتة؟"))
                    .setMessage(translate("سيتم حذف الملفات المؤقتة فقط. لن يتم حذف الألعاب أو المفضلة أو التنزيلات أو الحسابات أو أغلفة الألعاب المحفوظة."))
                    .setNegativeButton(translate("إلغاء"), null)
                    .setPositiveButton(translate("مسح")) { _, _ ->
                        val cache = activity.cacheDir
                        cache.listFiles()?.forEach { it.deleteRecursively() }
                        cache.mkdirs()
                        ToastMessage.show(activity, translate("تم مسح الملفات المؤقتة"))
                        show()
                    }
                    .show()
            }
        }
        cacheCard.addView(clear)
        root.addView(cacheCard)

        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(root, android.view.ViewGroup.LayoutParams(-1, -2))
        }
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(0, safe.top, 0, safe.bottom)
            insets
        }

        val filesSize = directorySize(activity.filesDir)
        val cacheSize = directorySize(activity.cacheDir)
        val externalSize = directorySize(activity.getExternalFilesDir(null))
        val totalSize = directorySize(File(activity.applicationInfo.dataDir)) + externalSize
        total.text = formatBytes(totalSize)
        details.text = translate("الملفات المحفوظة") + ": " + formatBytes(filesSize) +
            "\\n" + translate("الملفات الخارجية") + ": " + formatBytes(externalSize)
        cacheValue.text = formatBytes(cacheSize) + "  •  " +
            translate("يمكن حذفها بأمان عند الحاجة")

        setContentView(scroll)
        ViewCompat.requestApplyInsets(scroll)
    }
}

/** Tiny helper to avoid coupling the storage screen to the Activity's Toast styling. */
private object ToastMessage {
    fun show(activity: Activity, message: String) {
        android.widget.Toast.makeText(activity, message, android.widget.Toast.LENGTH_SHORT).show()
    }
}
