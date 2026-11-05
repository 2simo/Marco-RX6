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
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
    private val translate: (String) -> String,
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
            Toast.makeText(activity, translate("رابط التنزيل غير آمن أو غير صالح؛ يجب استخدام HTTPS"), Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(activity, translate("جارٍ التحقق من رابط الملف…"), Toast.LENGTH_SHORT).show()
        downloadRepository.enqueue(
            secureLink, title, userAgent, contentDisposition, hintedMimeType, referrer
        ) { result ->
            activity.runOnUiThread {
                when (result) {
                    is DownloadStartResult.Enqueued ->
                        Toast.makeText(activity, translate("تم التحقق من الملف وإضافته إلى مدير التنزيلات"), Toast.LENGTH_LONG).show()
                    is DownloadStartResult.OpenInBrowser -> {
                        Toast.makeText(activity, translate("هذا رابط صفحة تحميل؛ افتح الصفحة لإكمال الخطوات واختيار الملف المباشر"), Toast.LENGTH_LONG).show()
                        DownloadPageDialog(activity, translate).show(result.finalUrl, title) { url, gameTitle, userAgent, disposition, mimeType, pageUrl ->
                            enqueueGameDownload(url, gameTitle, userAgent, disposition, mimeType, pageUrl)
                        }
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
            text = translate("التنزيلات")
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
            minWidth = dp(48)
            minHeight = dp(48)
            contentDescription = translate("إغلاق صفحة التنزيلات")
            isClickable = true
            isFocusable = true
            addGlassTouch(this)
            setOnClickListener { onBack() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(header)
        root.addView(TextView(activity).apply {
            text = translate("تابع حالة ملفات الألعاب التي أضفتها إلى التنزيلات")
            textSize = 12f
            setTextColor(muted)
            setPadding(0, dp(7), 0, dp(18))
        })
        val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val lastProgress = mutableMapOf<Long, Int>()
        val lastSpeedSample = mutableMapOf<Long, Pair<Long, Long>>()
        var lastRenderedSnapshot: String? = null
        val handler = Handler(Looper.getMainLooper())
        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(list, LinearLayout.LayoutParams(-1, -2))
        }
        fun render() {
            list.removeAllViews()
            val entries = try { downloadRepository.getDownloads() } catch (error: Exception) {
                Toast.makeText(activity, translate("تعذر قراءة التنزيلات") + ": ${error.message}", Toast.LENGTH_LONG).show()
                emptyList()
            }
            val snapshot = entries.joinToString("|") { entry ->
                val visiblePercent = if (entry.totalBytes > 0L)
                    (entry.downloadedBytes * 100L / entry.totalBytes).coerceIn(0L, 100L) else -1L
                "${entry.id}:${entry.status}:$visiblePercent"
            }
            if (snapshot == lastRenderedSnapshot) return
            lastRenderedSnapshot = snapshot
            if (entries.isEmpty()) {
                val empty = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(dp(24), dp(42), dp(24), dp(42))
                    this.background = GradientDrawable().apply {
                        setColors(if (dark)
                            intArrayOf(Color.argb(220, 23, 67, 108), Color.argb(208, 8, 27, 47))
                        else intArrayOf(Color.argb(232, 255, 255, 255), Color.argb(218, 224, 240, 255)))
                        cornerRadius = dp(22).toFloat()
                        setStroke(dp(1), if (dark) Color.argb(165, 80, 151, 215) else Color.argb(175, 153, 195, 236))
                    }
                }
                empty.addView(TextView(activity).apply {
                    text = "↓"
                    textSize = 42f
                    gravity = Gravity.CENTER
                    setTextColor(primary)
                })
                empty.addView(TextView(activity).apply {
                    text = translate("ما كايناش تنزيلات دابا")
                    textSize = 17f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(heading)
                    gravity = Gravity.CENTER
                    setPadding(0, dp(10), 0, dp(6))
                })
                empty.addView(TextView(activity).apply {
                    text = translate("اختار لعبة، ومن صفحة التفاصيل أضف رابط التحميل باش يبان هنا.")
                    textSize = 12f
                    setTextColor(muted)
                    gravity = Gravity.CENTER
                })
                list.addView(empty, LinearLayout.LayoutParams(-1, -2))
            } else entries.forEach { item ->
                val status = when (item.status) {
                    DownloadManager.STATUS_PENDING -> translate("في الانتظار")
                    DownloadManager.STATUS_RUNNING -> translate("قيد التنزيل")
                    DownloadManager.STATUS_PAUSED -> translate("متوقف مؤقتاً")
                    DownloadManager.STATUS_SUCCESSFUL -> translate("اكتمل")
                    DownloadManager.STATUS_FAILED -> translate("فشل")
                    else -> translate("غير معروف")
                }
                val percent = if (item.totalBytes > 0L)
                    (item.downloadedBytes * 100L / item.totalBytes).coerceIn(0L, 100L).toInt() else 0
                val now = SystemClock.elapsedRealtime()
                val previousSample = lastSpeedSample[item.id]
                val speedBytesPerSecond = if (previousSample != null && now > previousSample.second) {
                    ((item.downloadedBytes - previousSample.first).coerceAtLeast(0L) * 1000L) /
                        (now - previousSample.second).coerceAtLeast(1L)
                } else 0L
                lastSpeedSample[item.id] = item.downloadedBytes to now
                val card = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(15), dp(14), dp(15), dp(14))
                    this.background = GradientDrawable().apply {
                        setColors(if (dark)
                            intArrayOf(Color.argb(221, 22, 65, 105), Color.argb(209, 8, 27, 47))
                        else intArrayOf(Color.argb(232, 255, 255, 255), Color.argb(218, 224, 240, 255)))
                        cornerRadius = dp(17).toFloat()
                        setStroke(dp(1), if (dark) Color.argb(165, 80, 151, 215) else Color.argb(175, 153, 195, 236))
                    }
                }
                // Download rows are informational; keep them non-clickable so they
                // are not announced as interactive controls without an action.
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
                    val downloadedLabel = android.text.format.Formatter.formatFileSize(activity, item.downloadedBytes.coerceAtLeast(0L))
                    val totalLabel = android.text.format.Formatter.formatFileSize(activity, item.totalBytes.coerceAtLeast(0L))
                    val speedLabel = if (item.status == DownloadManager.STATUS_RUNNING && speedBytesPerSecond > 0L)
                        "  •  ${android.text.format.Formatter.formatFileSize(activity, speedBytesPerSecond)}/s" else ""
                    card.addView(TextView(activity).apply {
                        text = "$downloadedLabel / $totalLabel$speedLabel"
                        textSize = 10f
                        setTextColor(muted)
                        setPadding(0, dp(4), 0, 0)
                    })
                }
                if (item.totalBytes > 0L && item.status == DownloadManager.STATUS_RUNNING) {
                    val oldPercent = lastProgress[item.id] ?: percent
                    val progress = android.widget.ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
                        max = 100
                        this.progress = oldPercent.coerceIn(0, 100)
                        progressTintList = android.content.res.ColorStateList.valueOf(primary)
                        progressBackgroundTintList = android.content.res.ColorStateList.valueOf(
                            if (dark) Color.argb(90, 110, 150, 190) else Color.argb(70, 70, 120, 180)
                        )
                    }
                    card.addView(progress, LinearLayout.LayoutParams(-1, dp(7)).apply { topMargin = dp(10) })
                    // Update only when the visible percentage changes. Recreating and
                    // animating every row on each polling tick caused a brief screen flash.
                    progress.progress = percent
                    lastProgress[item.id] = percent
                }
                if (item.status == DownloadManager.STATUS_SUCCESSFUL || item.status == DownloadManager.STATUS_FAILED) {
                    val row = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(0, dp(10), 0, 0)
                    }
                    fun smallAction(label: String, filled: Boolean = false, onClick: () -> Unit) = TextView(activity).apply {
                        text = label
                        textSize = 11f
                        gravity = Gravity.CENTER
                        minHeight = dp(42)
                        setPadding(dp(13), dp(9), dp(13), dp(9))
                        setTextColor(if (filled) Color.WHITE else primary)
                        this.background = GradientDrawable().apply {
                            setColor(if (filled) Color.argb(225, 42, 145, 255)
                                else if (dark) Color.argb(190, 15, 43, 70) else Color.WHITE)
                            cornerRadius = dp(12).toFloat()
                            setStroke(dp(1), primary)
                        }
                        isClickable = true
                        isFocusable = true
                        addGlassTouch(this)
                        setOnClickListener { onClick() }
                    }
                    if (item.status == DownloadManager.STATUS_SUCCESSFUL) {
                        row.addView(smallAction(translate("فتح الملف"), true) {
                            try {
                                val fileUri = downloadRepository.openDownloadedFile(item.id)
                                    ?: throw IllegalStateException("File URI unavailable")
                                val openIntent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(fileUri, item.mimeType?.takeIf { it.isNotBlank() } ?: "*/*")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                activity.startActivity(openIntent)
                            } catch (_: Exception) {
                                Toast.makeText(activity, translate("تعذر فتح الملف؛ تحقق من وجود تطبيق مناسب لفتحه"), Toast.LENGTH_LONG).show()
                            }
                        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
                    }
                    row.addView(smallAction(translate("حذف")) {
                        AlertDialog.Builder(activity)
                            .setMessage(translate("هل تريد حذف هذا التنزيل؟"))
                            .setNegativeButton(translate("إلغاء"), null)
                            .setPositiveButton(translate("حذف")) { _, _ ->
                                if (downloadRepository.deleteDownload(item.id)) {
                                    lastProgress.remove(item.id)
                                    lastSpeedSample.remove(item.id)
                                    render()
                                    Toast.makeText(activity, translate("تم حذف التنزيل"), Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(activity, translate("تعذر حذف التنزيل"), Toast.LENGTH_SHORT).show()
                                }
                            }.show()
                    }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(6) })
                    card.addView(row)
                }
                list.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
            }
        }
        render()
        val refreshTask = object : Runnable {
            override fun run() {
                if (!activity.isFinishing && !activity.isDestroyed && root.isAttachedToWindow) {
                    render()
                    handler.postDelayed(this, 900L)
                }
            }
        }
        root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                handler.removeCallbacks(refreshTask)
                handler.post(refreshTask)
            }
            override fun onViewDetachedFromWindow(view: View) {
                handler.removeCallbacks(refreshTask)
            }
        })
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
            this.background = GradientDrawable().apply {
                setColor(if (filled) Color.argb(224, 42, 145, 255)
                    else if (dark) Color.argb(205, 15, 43, 70) else Color.argb(220, 255, 255, 255))
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), if (filled) Color.argb(175, 119, 194, 255) else primary)
            }
            minWidth = dp(48)
            minHeight = dp(48)
            isClickable = true
            isFocusable = true
            contentDescription = label
            setPadding(dp(14), dp(13), dp(14), dp(13))
            addGlassTouch(this)
            setOnClickListener { onClick() }
        }
        actions.addView(action(translate("تحديث"), false) { render() }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(7) })
        actions.addView(action(translate("تنزيلات النظام"), true) {
            try { activity.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) }
            catch (_: Exception) { Toast.makeText(activity, translate("تعذر فتح مدير تنزيلات النظام"), Toast.LENGTH_SHORT).show() }
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
            Toast.makeText(activity, translate("لا يوجد رابط متاح"), Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val secureLink = SecureUrlPolicy.upgradeToHttps(link)
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(secureLink)))
        } catch (_: Exception) {
            Toast.makeText(activity, translate("تعذر فتح هذا الرابط"), Toast.LENGTH_SHORT).show()
        }
    }
}
