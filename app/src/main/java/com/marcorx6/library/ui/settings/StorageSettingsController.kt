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
    private val translate: (String) -> String,
    private val launchDownloadFolderPicker: () -> Unit,
    private val downloadFolderName: () -> String
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
        val bg = if (dark) Color.rgb(8, 9, 12) else Color.rgb(242, 244, 247)
        val ink = if (dark) Color.WHITE else Color.rgb(25, 29, 36)
        val muted = if (dark) Color.rgb(178, 184, 194) else Color.rgb(91, 98, 110)
        val appearance = AppearancePreferencesStore.appearance(activity)
        val selectedAccentId = appearance.getString(
            AccentPalette.PREFERENCE_KEY, AccentPalette.DEFAULT_ID
        ) ?: AccentPalette.DEFAULT_ID
        val silverAccent = AccentPalette.color(selectedAccentId)
        val accentText = AccentPalette.readableAccentText(selectedAccentId, dark)

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
            text = "MARCO  /  STORAGE"
            textSize = 9f
            letterSpacing = 0.12f
            setTextColor(accentText)
        })
        heading.addView(TextView(activity).apply {
            text = translate("التخزين")
            textSize = 25f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ink)
        })
        header.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(android.widget.ImageView(activity).apply {
            setImageResource(com.marcorx6.library.R.drawable.ic_action_back)
            imageTintList = android.content.res.ColorStateList.valueOf(accentText)
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            contentDescription = translate("رجوع")
            isClickable = true
            isFocusable = true
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
                setColor(if (dark) Color.rgb(25, 28, 34) else Color.WHITE)
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), if (dark) Color.rgb(130, 138, 151) else Color.rgb(160, 168, 181))
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

        val downloadLocationCard = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(17), dp(16), dp(17), dp(16))
            background = GradientDrawable().apply {
                setColor(if (dark) Color.rgb(25, 28, 34) else Color.WHITE)
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), if (dark) Color.rgb(130, 138, 151) else Color.rgb(160, 168, 181))
            }
        }
        downloadLocationCard.addView(TextView(activity).apply {
            text = translate("مجلد حفظ التنزيلات")
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ink)
        })
        downloadLocationCard.addView(TextView(activity).apply {
            text = downloadFolderName()
            textSize = 12f
            setTextColor(muted)
            setPadding(0, dp(7), 0, dp(12))
        })
        downloadLocationCard.addView(TextView(activity).apply {
            text = translate("اختيار أو تغيير المجلد")
            textSize = 13f
            gravity = Gravity.CENTER
            minHeight = dp(48)
            setTextColor(AccentPalette.foregroundForColor(silverAccent))
            background = GradientDrawable().apply {
                setColor(silverAccent)
                cornerRadius = dp(14).toFloat()
            }
            setOnClickListener { launchDownloadFolderPicker() }
        })
        root.addView(downloadLocationCard, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(14)
        })

        val cacheCard = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(17), dp(16), dp(17), dp(16))
            background = GradientDrawable().apply {
                setColor(if (dark) Color.rgb(25, 28, 34) else Color.WHITE)
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), if (dark) Color.rgb(130, 138, 151) else Color.rgb(160, 168, 181))
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
            setTextColor(Color.rgb(20, 23, 29))
            background = GradientDrawable().apply {
                setColor(silverAccent)
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

        total.text = translate("جارٍ الحساب...")
        details.text = translate("جارٍ حساب مساحة الملفات...")
        cacheValue.text = translate("جارٍ الحساب...")

        setContentView(scroll)
        ViewCompat.requestApplyInsets(scroll)

        // Recursive directory-size scans can be slow on devices with many downloaded files.
        // Keep filesystem traversal off the main thread so opening Settings stays responsive.
        Thread({
            val filesSize = directorySize(activity.filesDir)
            val cacheSize = directorySize(activity.cacheDir)
            val externalSize = directorySize(activity.getExternalFilesDir(null))
            val totalSize = directorySize(File(activity.applicationInfo.dataDir)) + externalSize
            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                total.text = formatBytes(totalSize)
                details.text = translate("الملفات المحفوظة") + ": " + formatBytes(filesSize) +
                    "\n" + translate("الملفات الخارجية") + ": " + formatBytes(externalSize)
                cacheValue.text = formatBytes(cacheSize) + "  •  " +
                    translate("يمكن حذفها بأمان عند الحاجة")
            }
        }, "marco-storage-usage").start()
    }
}

/** Tiny helper to avoid coupling the storage screen to the Activity's Toast styling. */
private object ToastMessage {
    fun show(activity: Activity, message: String) {
        android.widget.Toast.makeText(activity, message, android.widget.Toast.LENGTH_SHORT).show()
    }
}
