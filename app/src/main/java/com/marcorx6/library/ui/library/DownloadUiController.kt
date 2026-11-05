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
        class ProgressViews(
            val status: TextView,
            val details: TextView,
            val progress: android.widget.ProgressBar
        )
        val progressViews = mutableMapOf<Long, ProgressViews>()
        val lastSpeedSample = mutableMapOf<Long, Pair<Long, Long>>()
        var lastRenderedSnapshot: String? = null
        val handler = Handler(Looper.getMainLooper())
        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(list, LinearLayout.LayoutParams(-1, -2))
        }
        fun updateProgress(item: com.marcorx6.library.data.DownloadEntry, views: ProgressViews) {
            val statusLabel = when (item.status) {
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
            val previous = lastSpeedSample[item.id]
            val speed = if (item.status == DownloadManager.STATUS_RUNNING && previous != null && now > previous.second) {
                ((item.downloadedBytes - previous.first).coerceAtLeast(0L) * 1000L) /
                    (now - previous.second).coerceAtLeast(1L)
            } else 0L
            lastSpeedSample[item.id] = item.downloadedBytes to now

            views.status.text = if (item.totalBytes > 0L) "$statusLabel  •  $percent%" else statusLabel
            views.status.setTextColor(when (item.status) {
                DownloadManager.STATUS_SUCCESSFUL -> Color.rgb(36, 170, 112)
                DownloadManager.STATUS_FAILED -> Color.rgb(230, 85, 96)
                else -> primary
            })
            val downloaded = android.text.format.Formatter.formatFileSize(activity, item.downloadedBytes.coerceAtLeast(0L))
            val details = StringBuilder(downloaded)
            if (item.totalBytes > 0L) {
                val total = android.text.format.Formatter.formatFileSize(activity, item.totalBytes)
                details.append(" / ").append(total)
            } else {
                details.append(" / ").append(translate("الحجم غير معروف"))
            }
            if (item.status == DownloadManager.STATUS_RUNNING && speed > 0L) {
                details.append("  •  ")
                    .append(android.text.format.Formatter.formatFileSize(activity, speed))
                    .append("/s")
                if (item.totalBytes > item.downloadedBytes) {
                    val seconds = (item.totalBytes - item.downloadedBytes) / speed
                    val eta = when {
                        seconds < 60L -> seconds.toString() + "s"
                        seconds < 3600L -> (seconds / 60L).toString() + "m " + (seconds % 60L) + "s"
                        else -> (seconds / 3600L).toString() + "h " + ((seconds % 3600L) / 60L) + "m"
                    }
                    details.append("  •  ").append(translate("متبقي")).append(" ").append(eta)
                }
            }
            views.details.text = details.toString()
            views.progress.visibility = if (item.status == DownloadManager.STATUS_RUNNING && item.totalBytes > 0L) {
                View.VISIBLE
            } else {
                View.GONE
            }
            views.progress.progress = percent
        }

        fun render() {
            val entries = try { downloadRepository.getDownloads() } catch (error: Exception) {
                Toast.makeText(activity, translate("تعذر قراءة التنزيلات") + ": ${error.message}", Toast.LENGTH_LONG).show()
                emptyList()
            }
            val snapshot = entries.joinToString("|") { entry ->
                "${entry.id}:${entry.status}:${entry.title}"
            }
            if (snapshot == lastRenderedSnapshot) {
                entries.forEach { item -> progressViews[item.id]?.let { updateProgress(item, it) } }
                return
            }
            lastRenderedSnapshot = snapshot
            // Rebuild only when the queue structure or a row status changes.
            // Byte counters, speed and ETA update in-place without detaching cards.
            progressViews.clear()
            list.removeAllViews()
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
                card.addView(TextView(activity).apply {
                    text = item.title
                    textSize = 14f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(heading)
                })
                val statusView = TextView(activity).apply {
                    textSize = 11f
                    setPadding(0, dp(6), 0, 0)
                }
                card.addView(statusView)
                val detailsView = TextView(activity).apply {
                    textSize = 10f
                    setTextColor(muted)
                    setPadding(0, dp(4), 0, 0)
                }
                card.addView(detailsView)
                val progressView = android.widget.ProgressBar(
                    activity, null, android.R.attr.progressBarStyleHorizontal
                ).apply {
                    max = 100
                    progressTintList = android.content.res.ColorStateList.valueOf(primary)
                    progressBackgroundTintList = android.content.res.ColorStateList.valueOf(
                        if (dark) Color.argb(90, 110, 150, 190) else Color.argb(70, 70, 120, 180)
                    )
                }
                card.addView(progressView, LinearLayout.LayoutParams(-1, dp(7)).apply { topMargin = dp(10) })
                val rowProgressViews = ProgressViews(statusView, detailsView, progressView)
                progressViews[item.id] = rowProgressViews
                updateProgress(item, rowProgressViews)
                if (item.status == DownloadManager.STATUS_SUCCESSFUL || item.status == DownloadManager.STATUS_FAILED ||
                    item.status == DownloadManager.STATUS_RUNNING || item.status == DownloadManager.STATUS_PENDING ||
                    item.status == DownloadManager.STATUS_PAUSED) {
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
                    if (item.status == DownloadManager.STATUS_RUNNING || item.status == DownloadManager.STATUS_PENDING) {
                        row.addView(smallAction(translate("إيقاف مؤقت")) {
                            if (downloadRepository.pauseDownload(item.id)) {

                                lastSpeedSample.remove(item.id)
                                lastRenderedSnapshot = null
                                render()
                                Toast.makeText(activity, translate("تم إيقاف التنزيل مؤقتاً"), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(activity, translate("تعذر إيقاف التنزيل مؤقتاً"), Toast.LENGTH_SHORT).show()
                            }
                        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
                    } else if (item.status == DownloadManager.STATUS_PAUSED) {
                        row.addView(smallAction(translate("استكمال")) {
                            if (downloadRepository.resumeDownload(item.id)) {
                                lastRenderedSnapshot = null
                                render()
                                Toast.makeText(activity, translate("تم استكمال التنزيل"), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(activity, translate("تعذر استكمال التنزيل"), Toast.LENGTH_SHORT).show()
                            }
                        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
                    }
                    if (item.status == DownloadManager.STATUS_RUNNING || item.status == DownloadManager.STATUS_PENDING || item.status == DownloadManager.STATUS_PAUSED) {
                        row.addView(smallAction(translate("إلغاء التنزيل")) {
                            if (downloadRepository.deleteDownload(item.id)) {

                                lastSpeedSample.remove(item.id)
                                lastRenderedSnapshot = null
                                render()
                                Toast.makeText(activity, translate("تم إلغاء التنزيل"), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(activity, translate("تعذر إلغاء التنزيل"), Toast.LENGTH_SHORT).show()
                            }
                        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
                    }
                    if (item.status == DownloadManager.STATUS_SUCCESSFUL || item.status == DownloadManager.STATUS_FAILED) {
                    row.addView(smallAction(translate("حذف")) {
                        AlertDialog.Builder(activity)
                            .setMessage(translate("هل تريد حذف هذا التنزيل؟"))
                            .setNegativeButton(translate("إلغاء"), null)
                            .setPositiveButton(translate("حذف")) { _, _ ->
                                if (downloadRepository.deleteDownload(item.id)) {

                                    lastSpeedSample.remove(item.id)
                                    render()
                                    Toast.makeText(activity, translate("تم حذف التنزيل"), Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(activity, translate("تعذر حذف التنزيل"), Toast.LENGTH_SHORT).show()
                                }
                            }.show()
                    }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(6) })
                    }
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
