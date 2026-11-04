package com.marcorx6.library.ui

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.marcorx6.library.data.DownloadRepository
import com.marcorx6.library.data.DownloadStartResult
import com.marcorx6.library.network.SecureUrlPolicy

/**
 * Owns download-related presentation and external-link navigation.
 * Actual URL inspection and DownloadManager operations remain in DownloadRepository.
 */
internal class DownloadUiController(
    private val activity: Activity,
    private val downloadRepository: DownloadRepository,
    private val styleDialog: (AlertDialog) -> Unit,
    private val isDarkMode: () -> Boolean,
    private val setContentView: (View) -> Unit,
    private val onBack: () -> Unit
) {
    fun enqueueGameDownload(
        link: String,
        title: String,
        userAgent: String = "",
        contentDisposition: String = "",
        hintedMimeType: String = "",
        referrer: String = ""
    ) {
        val secureLink = SecureUrlPolicy.toHttpsUrl(link)
        if (secureLink == null) {
            Toast.makeText(activity, "رابط التنزيل غير آمن أو غير صالح؛ يجب استخدام HTTPS", Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(activity, "جارٍ التحقق من رابط الملف…", Toast.LENGTH_SHORT).show()
        downloadRepository.enqueue(
            secureLink, title, userAgent, contentDisposition, hintedMimeType, referrer
        ) { result ->
            activity.runOnUiThread {
                when (result) {
                    is DownloadStartResult.Enqueued ->
                        Toast.makeText(activity, "تم التحقق من الملف وإضافته إلى مدير التنزيلات", Toast.LENGTH_LONG).show()
                    is DownloadStartResult.OpenInBrowser -> {
                        Toast.makeText(activity, "هذا رابط صفحة أو تعذّر التأكد أنه ملف مباشر؛ سيتم فتحه في المتصفح", Toast.LENGTH_LONG).show()
                        openGameLink(result.finalUrl)
                    }
                    is DownloadStartResult.Failed ->
                        Toast.makeText(activity, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun showDownloadManager() {
        fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()
        val dark = isDarkMode()
        val background = if (dark) Color.rgb(7, 17, 31) else Color.rgb(242, 247, 253)
        val surface = if (dark) Color.rgb(15, 35, 58) else Color.WHITE
        val primary = Color.rgb(42, 145, 255)
        val heading = if (dark) Color.WHITE else Color.rgb(22, 43, 65)
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
            setBackgroundColor(background)
            setPadding(dp(18), dp(18), dp(18), dp(12))
        }
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleBox = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        titleBox.addView(TextView(activity).apply {
            text = "MARCO RX6  /  DOWNLOADS"
            textSize = 9f
            letterSpacing = 0.12f
            setTextColor(primary)
        })
        titleBox.addView(TextView(activity).apply {
            text = "التنزيلات"
            textSize = 25f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(heading)
            setPadding(0, dp(4), 0, 0)
        })
        header.addView(titleBox, LinearLayout.LayoutParams(0, -2, 1f))
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
            text = "تابع حالة ملفات الألعاب التي أضفتها إلى التنزيلات"
            textSize = 12f
            setTextColor(muted)
            setPadding(0, dp(7), 0, dp(18))
        })
        val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(list, ScrollView.LayoutParams(-1, -2))
        }
        fun render() {
            list.removeAllViews()
            val entries = try { downloadRepository.getDownloads() } catch (error: Exception) {
                Toast.makeText(activity, "تعذر قراءة التنزيلات: ${error.message}", Toast.LENGTH_LONG).show()
                emptyList()
            }
            if (entries.isEmpty()) {
                val empty = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(dp(24), dp(42), dp(24), dp(42))
                    background = GradientDrawable().apply {
                        setColor(surface); cornerRadius = dp(22).toFloat()
                        setStroke(dp(1), if (dark) Color.rgb(42, 82, 126) else Color.rgb(198, 220, 244))
                    }
                }
                empty.addView(TextView(activity).apply {
                    text = "↓"
                    textSize = 42f
                    gravity = Gravity.CENTER
                    setTextColor(primary)
                })
                empty.addView(TextView(activity).apply {
                    text = "ما كايناش تنزيلات دابا"
                    textSize = 17f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(heading)
                    gravity = Gravity.CENTER
                    setPadding(0, dp(10), 0, dp(6))
                })
                empty.addView(TextView(activity).apply {
                    text = "اختار لعبة، ومن صفحة التفاصيل أضف رابط التحميل باش يبان هنا."
                    textSize = 12f
                    setTextColor(muted)
                    gravity = Gravity.CENTER
                })
                list.addView(empty, LinearLayout.LayoutParams(-1, -2))
            } else entries.forEach { item ->
                val status = when (item.status) {
                    DownloadManager.STATUS_PENDING -> "في الانتظار"
                    DownloadManager.STATUS_RUNNING -> "قيد التنزيل"
                    DownloadManager.STATUS_PAUSED -> "متوقف مؤقتاً"
                    DownloadManager.STATUS_SUCCESSFUL -> "اكتمل"
                    DownloadManager.STATUS_FAILED -> "فشل"
                    else -> "غير معروف"
                }
                val percent = if (item.totalBytes > 0L)
                    (item.downloadedBytes * 100L / item.totalBytes).coerceIn(0L, 100L).toInt() else 0
                val card = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(15), dp(14), dp(15), dp(14))
                    background = GradientDrawable().apply {
                        setColor(surface); cornerRadius = dp(17).toFloat()
                        setStroke(dp(1), if (dark) Color.rgb(42, 82, 126) else Color.rgb(198, 220, 244))
                    }
                }
                card.addView(TextView(activity).apply {
                    text = item.title
                    textSize = 14f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(heading)
                })
                card.addView(TextView(activity).apply {
                    text = if (item.totalBytes > 0L) "$status  •  $percent%" else status
                    textSize = 11f
                    setTextColor(when (item.status) {
                        DownloadManager.STATUS_SUCCESSFUL -> Color.rgb(36, 170, 112)
                        DownloadManager.STATUS_FAILED -> Color.rgb(230, 85, 96)
                        else -> primary
                    })
                    setPadding(0, dp(6), 0, 0)
                })
                if (item.totalBytes > 0L) {
                    val downloadedMb = item.downloadedBytes.coerceAtLeast(0L) / (1024L * 1024L)
                    val totalMb = item.totalBytes.coerceAtLeast(0L) / (1024L * 1024L)
                    card.addView(TextView(activity).apply {
                        text = "$downloadedMb MB / $totalMb MB"
                        textSize = 10f
                        setTextColor(muted)
                        setPadding(0, dp(4), 0, 0)
                    })
                }
                if (item.totalBytes > 0L && item.status == DownloadManager.STATUS_RUNNING) {
                    val progress = android.widget.ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
                        max = 100; this.progress = percent
                        progressTintList = android.content.res.ColorStateList.valueOf(primary)
                    }
                    card.addView(progress, LinearLayout.LayoutParams(-1, dp(6)).apply { topMargin = dp(10) })
                }
                list.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
            }
        }
        render()
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val actions = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, 0)
        }
        fun action(label: String, filled: Boolean, onClick: () -> Unit) = TextView(activity).apply {
            text = label
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(if (filled) Color.WHITE else primary)
            background = GradientDrawable().apply {
                setColor(if (filled) Color.argb(224, 42, 145, 255)
                    else if (dark) Color.argb(205, 15, 43, 70) else Color.argb(220, 255, 255, 255))
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), if (filled) Color.argb(175, 119, 194, 255) else primary)
            }
            setPadding(dp(14), dp(13), dp(14), dp(13))
            addGlassTouch(this)
            setOnClickListener { onClick() }
        }
        actions.addView(action("تحديث", false) { render() }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(7) })
        actions.addView(action("تنزيلات النظام", true) {
            try { activity.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) }
            catch (_: Exception) { Toast.makeText(activity, "تعذر فتح مدير تنزيلات النظام", Toast.LENGTH_SHORT).show() }
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(7) })
        root.addView(actions)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safe = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars() or
                    androidx.core.view.WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(dp(18), dp(18) + safe.top, dp(18), dp(12) + safe.bottom)
            insets
        }
        setContentView(root)
        activity.window?.apply {
            statusBarColor = Color.TRANSPARENT
            navigationBarColor = Color.TRANSPARENT
            androidx.core.view.WindowInsetsControllerCompat(this, decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
                show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
        androidx.core.view.ViewCompat.requestApplyInsets(root)
    }

    fun openGameLink(link: String) {
        if (link.isBlank()) {
            Toast.makeText(activity, "لا يوجد رابط متاح", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val secureLink = SecureUrlPolicy.upgradeToHttps(link)
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(secureLink)))
        } catch (_: Exception) {
            Toast.makeText(activity, "تعذر فتح هذا الرابط", Toast.LENGTH_SHORT).show()
        }
    }
}
