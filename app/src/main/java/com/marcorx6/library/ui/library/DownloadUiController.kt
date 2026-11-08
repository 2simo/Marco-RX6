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
    private val onBack: () -> Unit,
    private val openGameSource: (String) -> Unit
) {
    fun enqueueGameDownload(
        link: String,
        title: String,
        userAgent: String = "",
        contentDisposition: String = "",
        hintedMimeType: String = "",
        referrer: String = "",
        sourcePostUrl: String = ""
    ) {
        val secureLink = SecureUrlPolicy.toHttpsUrl(link)
        if (secureLink == null) {
            Toast.makeText(activity, translate("رابط التنزيل غير آمن أو غير صالح؛ يجب استخدام HTTPS"), Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(activity, translate("جارٍ التحقق من رابط الملف…"), Toast.LENGTH_SHORT).show()
        downloadRepository.enqueue(
            secureLink, title, userAgent, contentDisposition, hintedMimeType, referrer, sourcePostUrl
        ) { result ->
            activity.runOnUiThread {
                when (result) {
                    is DownloadStartResult.Enqueued -> {
                        Toast.makeText(activity, translate("تم التحقق من الملف وإضافته إلى مدير التنزيلات"), Toast.LENGTH_LONG).show()
                        DownloadMiniPanelDialog(activity, downloadRepository, translate, isDarkMode).show()
                    }
                    is DownloadStartResult.AlreadyQueued -> {
                        Toast.makeText(activity, translate("هذا التنزيل موجود بالفعل في قائمة التنزيلات") + ": " + result.title, Toast.LENGTH_LONG).show()
                        DownloadMiniPanelDialog(activity, downloadRepository, translate, isDarkMode).show()
                    }
                    is DownloadStartResult.OpenInBrowser -> {
                        Toast.makeText(activity, translate("هذا رابط صفحة تحميل؛ افتح الصفحة لإكمال الخطوات واختيار الملف المباشر"), Toast.LENGTH_LONG).show()
                        DownloadPageDialog(activity, translate).show(result.finalUrl, title) { url, gameTitle, userAgent, disposition, mimeType, pageUrl ->
                            enqueueGameDownload(url, gameTitle, userAgent, disposition, mimeType, pageUrl, sourcePostUrl)
                        }
                    }
                    is DownloadStartResult.RefreshSource -> {
                        Toast.makeText(activity, translate("تعذر استخدام الرابط القديم؛ جارٍ تحديث معلومات اللعبة من المصدر"), Toast.LENGTH_LONG).show()
                        openGameSource(result.sourcePostUrl)
                    }
                    is DownloadStartResult.Failed ->
                        Toast.makeText(activity, translate(result.message), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun showDownloadManager() {
        fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()
        val dark = isDarkMode()
        val background = if (dark) Color.rgb(8, 9, 12) else Color.rgb(242, 244, 247)
        val surface = if (dark) Color.rgb(25, 28, 34) else Color.WHITE
        val primary = Color.rgb(91, 157, 242)
        val heading = if (dark) Color.WHITE else Color.rgb(25, 29, 36)
        val muted = if (dark) Color.rgb(178, 184, 194) else Color.rgb(91, 98, 110)
        fun addGlassTouch(view: View) {
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                view.foreground = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(
                        if (dark) Color.argb(82, 91, 157, 242) else Color.argb(55, 140, 148, 160)
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
            text = "MARCO  /  ${translate("التنزيلات").uppercase()}"
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
        val concurrencyButton = TextView(activity).apply {
            text = "×${downloadRepository.maxConcurrentDownloads()}"
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(primary)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), dp(10))
            this.background = GradientDrawable().apply {
                setColor(surface)
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), if (dark) Color.rgb(78, 85, 98) else Color.rgb(210, 216, 225))
            }
            setOnClickListener {
                val options = arrayOf("1", "2", "3", "4", "5")
                val selected = downloadRepository.maxConcurrentDownloads() - 1
                val picker = AlertDialog.Builder(activity)
                    .setTitle(translate("عدد التنزيلات المتزامنة"))
                    .setSingleChoiceItems(options, selected) { dialog, which ->
                        val value = which + 1
                        downloadRepository.setMaxConcurrentDownloads(value)
                        text = "×$value"
                        Toast.makeText(activity, translate("تم تحديث عدد التنزيلات المتزامنة"), Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                    .setNegativeButton(translate("إلغاء"), null)
                    .create()
                picker.show()
                styleDialog(picker)
            }
        }
        header.addView(concurrencyButton, LinearLayout.LayoutParams(-2, dp(44)).apply { marginEnd = dp(7) })
        val wifiOnlyButton = TextView(activity).apply {
            text = if (downloadRepository.wifiOnlyDownloads()) "Wi-Fi" else "ALL"
            textSize = 10f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(primary)
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(10), dp(10), dp(10))
            this.background = GradientDrawable().apply {
                setColor(surface)
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), if (dark) Color.rgb(78, 85, 98) else Color.rgb(210, 216, 225))
            }
            contentDescription = translate("التنزيل عبر Wi-Fi فقط")
            setOnClickListener {
                val enabled = !downloadRepository.wifiOnlyDownloads()
                downloadRepository.setWifiOnlyDownloads(enabled)
                text = if (enabled) "Wi-Fi" else "ALL"
                Toast.makeText(activity, translate(if (enabled) "التنزيل عبر Wi-Fi فقط" else "التنزيل عبر جميع الشبكات"), Toast.LENGTH_SHORT).show()
            }
        }
        header.addView(wifiOnlyButton, LinearLayout.LayoutParams(-2, dp(44)).apply { marginEnd = dp(7) })
        header.addView(android.widget.ImageView(activity).apply {
            setImageResource(com.marcorx6.library.R.drawable.ic_action_back)
            imageTintList = android.content.res.ColorStateList.valueOf(primary)
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(13), dp(13), dp(13), dp(13))
            minimumWidth = dp(48)
            minimumHeight = dp(48)
            contentDescription = translate("رجوع")
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
        var render: (() -> Unit)? = null
        var selectedFilter = "all"
        val filterButtons = linkedMapOf<String, TextView>()
        fun filterButton(key: String, label: String): TextView = TextView(activity).apply {
            text = translate(label)
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(9), dp(12), dp(9))
            isClickable = true
            isFocusable = true
            addGlassTouch(this)
            setOnClickListener {
                selectedFilter = key
                filterButtons.forEach { (filterKey, button) ->
                    val active = filterKey == selectedFilter
                    button.setTextColor(if (active) Color.rgb(20, 23, 29) else primary)
                    (button.background as? GradientDrawable)?.setColor(
                        if (active) primary else surface
                    )
                }
                render?.invoke()
            }
            this.background = GradientDrawable().apply {
                cornerRadius = dp(13).toFloat()
                setColor(surface)
                setStroke(dp(1), if (dark) Color.rgb(78, 85, 98) else Color.rgb(210, 216, 225))
            }
            setTextColor(primary)
        }
        val filterRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        listOf("all" to "الكل", "active" to "النشطة", "completed" to "المكتملة", "failed" to "الفاشلة").forEach { (key, label) ->
            val button = filterButton(key, label)
            filterButtons[key] = button
            filterRow.addView(button, LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                if (key != "all") marginStart = dp(5)
            })
        }
        filterButtons["all"]?.apply {
            setTextColor(Color.rgb(20, 23, 29))
            (background as? GradientDrawable)?.setColor(primary)
        }
        val bulkRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(9), 0, dp(10))
        }
        fun bulkButton(label: String, onClick: () -> Unit) = TextView(activity).apply {
            text = translate(label)
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setTextColor(primary)
            this.background = GradientDrawable().apply {
                setColor(surface)
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), if (dark) Color.rgb(78, 85, 98) else Color.rgb(210, 216, 225))
            }
            isClickable = true
            isFocusable = true
            addGlassTouch(this)
            setOnClickListener { onClick() }
        }
        bulkRow.addView(bulkButton("إيقاف الكل") {
            val count = downloadRepository.pauseAllDownloads()
            Toast.makeText(activity, translate("تم إيقاف") + " $count " + translate("تنزيلات مؤقتاً"), Toast.LENGTH_SHORT).show()
            render?.invoke()
        }, LinearLayout.LayoutParams(0, dp(42), 1f))
        bulkRow.addView(bulkButton("استكمال الكل") {
            val count = downloadRepository.resumeAllDownloads()
            Toast.makeText(activity, translate("تم استكمال") + " $count " + translate("تنزيلات"), Toast.LENGTH_SHORT).show()
            render?.invoke()
        }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginStart = dp(8) })
        val maintenanceRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(10))
        }
        maintenanceRow.addView(bulkButton("إعادة محاولة الفاشلة") {
            val count = downloadRepository.retryAllFailed()
            Toast.makeText(activity, translate("إعادة محاولة") + " $count " + translate("تنزيلات"), Toast.LENGTH_SHORT).show()
            render?.invoke()
        }, LinearLayout.LayoutParams(0, dp(40), 1f))
        maintenanceRow.addView(bulkButton("مسح المكتملة") {
            AlertDialog.Builder(activity)
                .setMessage(translate("إزالة جميع التنزيلات المكتملة من قائمة التنزيلات؟"))
                .setNegativeButton(translate("إلغاء"), null)
                .setPositiveButton(translate("حذف")) { _, _ ->
                    val count = downloadRepository.clearCompletedDownloads()
                    Toast.makeText(activity, translate("تم حذف") + " $count " + translate("ملفات مكتملة"), Toast.LENGTH_SHORT).show()
                    render?.invoke()
                }.show()
        }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginStart = dp(8) })
        root.addView(filterRow)
        root.addView(bulkRow)
        root.addView(maintenanceRow)

        val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        class RowViews(
            val card: LinearLayout,
            val title: TextView,
            val status: TextView,
            val details: TextView,
            val progress: android.widget.ProgressBar,
            val actions: LinearLayout,
            var actionState: String = ""
        )
        val rows = linkedMapOf<Long, RowViews>()
        val lastSpeedSample = mutableMapOf<Long, Pair<Long, Long>>()
        val handler = Handler(Looper.getMainLooper())

        // Poll more frequently only while a transfer is active. An idle download
        // screen does not need to query the persistent queue several times per second.
        var refreshIntervalMillis = 5_000L
        val emptyState = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(42), dp(24), dp(42))
            this.background = GradientDrawable().apply {
                setColors(if (dark)
                    intArrayOf(Color.argb(220, 35, 38, 45), Color.argb(208, 15, 17, 22))
                else intArrayOf(Color.argb(232, 255, 255, 255), Color.argb(218, 222, 226, 232)))
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), if (dark) Color.argb(165, 130, 138, 151) else Color.argb(175, 160, 168, 181))
            }
            addView(android.widget.ImageView(activity).apply {
                setImageResource(com.marcorx6.library.R.drawable.ic_action_download)
                imageTintList = android.content.res.ColorStateList.valueOf(muted)
                scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                contentDescription = translate("التنزيلات")
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
            addView(TextView(activity).apply {
                text = translate("لا توجد تنزيلات حالياً."); textSize = 17f
                setTypeface(null, android.graphics.Typeface.BOLD); setTextColor(heading)
                gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(6))
            })
            addView(TextView(activity).apply {
                text = translate("اختر لعبة، ثم أضف رابط التحميل من صفحة التفاصيل ليظهر هنا.")
                textSize = 12f; setTextColor(muted); gravity = Gravity.CENTER
            })
        }
        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(list, LinearLayout.LayoutParams(-1, -2))
        }

        fun updateProgress(item: com.marcorx6.library.data.DownloadEntry, views: RowViews) {
            val statusLabel = when (item.status) {
                DownloadManager.STATUS_PENDING, com.marcorx6.library.data.DownloadDatabase.STATUS_SCHEDULED -> translate("في الانتظار")
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
            val speed = if (item.status == DownloadManager.STATUS_RUNNING && previous != null &&
                now > previous.second && item.downloadedBytes >= previous.first) {
                ((item.downloadedBytes - previous.first) * 1000L) / (now - previous.second).coerceAtLeast(1L)
            } else 0L
            if (item.status == DownloadManager.STATUS_RUNNING) {
                lastSpeedSample[item.id] = item.downloadedBytes to now
            } else {
                lastSpeedSample.remove(item.id)
            }
            if (views.title.text.toString() != item.title) views.title.text = item.title
            val statusText = if (item.totalBytes > 0L) "$statusLabel  •  $percent%" else statusLabel
            if (views.status.text.toString() != statusText) views.status.text = statusText
            val statusColor = when (item.status) {
                DownloadManager.STATUS_SUCCESSFUL -> Color.rgb(36, 170, 112)
                DownloadManager.STATUS_FAILED -> Color.rgb(230, 85, 96)
                else -> primary
            }
            if (views.status.currentTextColor != statusColor) views.status.setTextColor(statusColor)
            val downloaded = android.text.format.Formatter.formatFileSize(activity, item.downloadedBytes.coerceAtLeast(0L))
            val details = StringBuilder(downloaded)
            if (item.totalBytes > 0L) {
                details.append(" / ").append(android.text.format.Formatter.formatFileSize(activity, item.totalBytes))
            } else details.append(" / ").append(translate("الحجم غير معروف"))
            if (item.status == DownloadManager.STATUS_RUNNING) {
                details.append("  •  ")
                    .append(android.text.format.Formatter.formatFileSize(activity, speed))
                    .append("/s")
                if (speed > 0L && item.totalBytes > item.downloadedBytes) {
                    val remainingBytes = (item.totalBytes - item.downloadedBytes).coerceAtLeast(0L)
                    val seconds = kotlin.math.ceil(remainingBytes.toDouble() / speed.toDouble())
                        .toLong().coerceAtLeast(0L)
                    val eta = when {
                        seconds < 60L -> "${seconds}s"
                        seconds < 3600L -> "${seconds / 60L}m ${seconds % 60L}s"
                        else -> "${seconds / 3600L}h ${(seconds % 3600L) / 60L}m"
                    }
                    details.append("  •  ").append(translate("متبقي")).append(" ").append(eta)
                }
            }
            if (item.status == DownloadManager.STATUS_FAILED) {
                details.append("\n").append(translate("تعذر إكمال التنزيل. يمكنك إعادة المحاولة أو تحديث رابط اللعبة."))
            }
            val detailsText = details.toString()
            if (views.details.text.toString() != detailsText) views.details.text = detailsText
            // Avoid invalidating the progress widget when the polled value did not change.
            val hasProgress = item.status == DownloadManager.STATUS_RUNNING ||
                item.status == DownloadManager.STATUS_PENDING || item.status == com.marcorx6.library.data.DownloadDatabase.STATUS_SCHEDULED || item.status == DownloadManager.STATUS_PAUSED
            val progressVisibility = if (hasProgress) View.VISIBLE else View.GONE
            if (views.progress.visibility != progressVisibility) views.progress.visibility = progressVisibility
            val indeterminate = item.status == DownloadManager.STATUS_RUNNING && item.totalBytes <= 0L
            if (views.progress.isIndeterminate != indeterminate) views.progress.isIndeterminate = indeterminate
            if (!indeterminate && views.progress.progress != percent) views.progress.progress = percent
        }

        fun smallAction(label: String, filled: Boolean = false, onClick: () -> Unit) =
            TextView(activity).apply {
                text = label; textSize = 11f; gravity = Gravity.CENTER
                minimumHeight = dp(42); setPadding(dp(13), dp(9), dp(13), dp(9))
                setTextColor(if (filled) Color.rgb(20, 23, 29) else primary)
                this.background = GradientDrawable().apply {
                    setColor(if (filled) Color.argb(225, 91, 157, 242)
                        else if (dark) Color.argb(190, 35, 38, 45) else Color.WHITE)
                    cornerRadius = dp(12).toFloat(); setStroke(dp(1), primary)
                }
                isClickable = true; isFocusable = true; addGlassTouch(this)
                setOnClickListener { onClick() }
            }

        fun updateActions(item: com.marcorx6.library.data.DownloadEntry, views: RowViews) {
            val signature = "${item.status}:${item.id}:${item.priority}"
            if (views.actionState == signature) return
            views.actionState = signature
            views.actions.removeAllViews()
            fun addAction(view: View, start: Boolean = false) {
                views.actions.addView(view, LinearLayout.LayoutParams(0, -2, 1f).apply {
                    if (start) marginStart = dp(6) else marginEnd = dp(6)
                })
            }
            if (item.status == DownloadManager.STATUS_PENDING ||
                item.status == com.marcorx6.library.data.DownloadDatabase.STATUS_SCHEDULED ||
                item.status == DownloadManager.STATUS_PAUSED) {
                addAction(smallAction(translate("الأولوية: ") + when (item.priority) {
                    2 -> translate("عالية")
                    1 -> translate("متوسطة")
                    else -> translate("عادية")
                }) {
                    val choices = arrayOf(translate("عادية"), translate("متوسطة"), translate("عالية"))
                    AlertDialog.Builder(activity)
                        .setTitle(translate("أولوية التنزيل"))
                        .setSingleChoiceItems(choices, item.priority.coerceIn(0, 2)) { dialog, selected ->
                            downloadRepository.setDownloadPriority(item.id, selected)
                            views.actionState = ""
                            render?.invoke()
                            dialog.dismiss()
                        }
                        .setNegativeButton(translate("إلغاء"), null)
                        .show().also(styleDialog)
                })
            }
            if (item.status == DownloadManager.STATUS_SUCCESSFUL) {
                addAction(smallAction(translate("فتح الملف"), true) {
                    try {
                        val uri = downloadRepository.openDownloadedFile(item.id)
                            ?: throw IllegalStateException("File URI unavailable")
                        activity.startActivity(Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, item.mimeType?.takeIf { it.isNotBlank() } ?: "*/*")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        })
                    } catch (_: Exception) {
                        Toast.makeText(activity, translate("تعذر فتح الملف؛ تحقق من وجود تطبيق مناسب لفتحه"), Toast.LENGTH_LONG).show()
                    }
                })
            }
            if (item.status == DownloadManager.STATUS_RUNNING || item.status == DownloadManager.STATUS_PENDING || item.status == com.marcorx6.library.data.DownloadDatabase.STATUS_SCHEDULED) {
                addAction(smallAction(translate("إيقاف مؤقت")) {
                    if (downloadRepository.pauseDownload(item.id)) {
                        lastSpeedSample.remove(item.id); render?.invoke()
                        Toast.makeText(activity, translate("تم إيقاف التنزيل مؤقتاً"), Toast.LENGTH_SHORT).show()
                    } else Toast.makeText(activity, translate("تعذر إيقاف التنزيل مؤقتاً"), Toast.LENGTH_SHORT).show()
                })
            } else if (item.status == DownloadManager.STATUS_PAUSED) {
                addAction(smallAction(translate("استكمال")) {
                    if (downloadRepository.resumeDownload(item.id)) {
                        lastSpeedSample.remove(item.id); render?.invoke()
                        Toast.makeText(activity, translate("تم استكمال التنزيل"), Toast.LENGTH_SHORT).show()
                    } else Toast.makeText(activity, translate("تعذر استكمال التنزيل"), Toast.LENGTH_SHORT).show()
                })
            } else if (item.status == DownloadManager.STATUS_FAILED) {
                addAction(smallAction(translate("إعادة المحاولة"), true) {
                    if (downloadRepository.resumeDownload(item.id)) {
                        lastSpeedSample.remove(item.id); render?.invoke()
                        Toast.makeText(activity, translate("بدأت إعادة محاولة التنزيل"), Toast.LENGTH_SHORT).show()
                    } else Toast.makeText(activity, translate("تعذرت إعادة محاولة التنزيل"), Toast.LENGTH_SHORT).show()
                })
            }
            if (item.status == DownloadManager.STATUS_RUNNING || item.status == DownloadManager.STATUS_PENDING ||
                item.status == com.marcorx6.library.data.DownloadDatabase.STATUS_SCHEDULED || item.status == DownloadManager.STATUS_PAUSED) {
                addAction(smallAction(translate("إلغاء التنزيل")) {
                    if (downloadRepository.deleteDownload(item.id)) {
                        lastSpeedSample.remove(item.id); render?.invoke()
                        Toast.makeText(activity, translate("تم إلغاء التنزيل"), Toast.LENGTH_SHORT).show()
                    } else Toast.makeText(activity, translate("تعذر إلغاء التنزيل"), Toast.LENGTH_SHORT).show()
                }, start = true)
            }
            if (item.status == DownloadManager.STATUS_FAILED && item.sourcePostUrl.isNotBlank()) {
                addAction(smallAction(translate("تحديث رابط التحميل"), true) {
                    openGameSource(item.sourcePostUrl)
                })
            }
            if (item.status == DownloadManager.STATUS_SUCCESSFUL || item.status == DownloadManager.STATUS_FAILED) {
                addAction(smallAction(translate("حذف")) {
                    AlertDialog.Builder(activity)
                        .setMessage(translate("هل تريد حذف هذا التنزيل؟"))
                        .setNegativeButton(translate("إلغاء"), null)
                        .setPositiveButton(translate("حذف")) { _, _ ->
                            if (downloadRepository.deleteDownload(item.id)) {
                                lastSpeedSample.remove(item.id); render?.invoke()
                                Toast.makeText(activity, translate("تم حذف التنزيل"), Toast.LENGTH_SHORT).show()
                            } else Toast.makeText(activity, translate("تعذر حذف التنزيل"), Toast.LENGTH_SHORT).show()
                        }.show()
                }, start = true)
            }
        }

        fun createRow(item: com.marcorx6.library.data.DownloadEntry): RowViews {
            val card = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(15), dp(14), dp(15), dp(14))
                this.background = GradientDrawable().apply {
                    setColors(if (dark)
                        intArrayOf(Color.argb(221, 35, 38, 45), Color.argb(209, 15, 17, 22))
                    else intArrayOf(Color.argb(232, 255, 255, 255), Color.argb(218, 222, 226, 232)))
                    cornerRadius = dp(17).toFloat()
                    setStroke(dp(1), if (dark) Color.argb(165, 130, 138, 151) else Color.argb(175, 160, 168, 181))
                }
            }
            val titleView = TextView(activity).apply {
                textSize = 14f; setTypeface(null, android.graphics.Typeface.BOLD); setTextColor(heading)
            }
            val statusView = TextView(activity).apply { textSize = 11f; setPadding(0, dp(6), 0, 0) }
            val detailsView = TextView(activity).apply {
                textSize = 10f; setTextColor(muted); setPadding(0, dp(4), 0, 0)
            }
            val progressView = android.widget.ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progressTintList = android.content.res.ColorStateList.valueOf(primary)
                progressBackgroundTintList = android.content.res.ColorStateList.valueOf(
                    if (dark) Color.argb(90, 130, 138, 151) else Color.argb(70, 160, 168, 181))
            }
            val actions = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(10), 0, 0)
            }
            card.addView(titleView)
            card.addView(statusView)
            card.addView(detailsView)
            card.addView(progressView, LinearLayout.LayoutParams(-1, dp(7)).apply { topMargin = dp(10) })
            card.addView(actions)
            return RowViews(card, titleView, statusView, detailsView, progressView, actions)
        }

        render = renderBlock@{
            if (activity.isFinishing || activity.isDestroyed) return@renderBlock
            val allEntries = try { downloadRepository.getDownloads() } catch (error: Exception) {
                Toast.makeText(activity, translate("تعذر قراءة التنزيلات") + (error.message?.takeIf { it.isNotBlank() }?.let { ": ${translate(it)}" } ?: ""), Toast.LENGTH_LONG).show()
                return@renderBlock
            }
            refreshIntervalMillis = if (allEntries.any {
                it.status == DownloadManager.STATUS_RUNNING ||
                    it.status == DownloadManager.STATUS_PENDING ||
                    it.status == com.marcorx6.library.data.DownloadDatabase.STATUS_SCHEDULED
            }) 1_200L else 5_000L
            val activeStatuses = setOf(
                DownloadManager.STATUS_PENDING,
                DownloadManager.STATUS_RUNNING,
                DownloadManager.STATUS_PAUSED,
                com.marcorx6.library.data.DownloadDatabase.STATUS_SCHEDULED
            )
            val visibleEntries = when (selectedFilter) {
                "active" -> allEntries.filter { it.status in activeStatuses }
                "completed" -> allEntries.filter { it.status == DownloadManager.STATUS_SUCCESSFUL }
                "failed" -> allEntries.filter { it.status == DownloadManager.STATUS_FAILED }
                else -> allEntries
            }
            val currentIds = visibleEntries.map { it.id }.toSet()
            val removedIds = rows.keys.filter { it !in currentIds }
            removedIds.forEach { id ->
                rows.remove(id)?.let { list.removeView(it.card) }
                lastSpeedSample.remove(id)
            }
            emptyState.visibility = if (visibleEntries.isEmpty()) View.VISIBLE else View.GONE
            if (emptyState.parent == null) list.addView(emptyState, LinearLayout.LayoutParams(-1, -2))
            visibleEntries.forEachIndexed { index, item ->
                val row = rows[item.id] ?: createRow(item).also { created ->
                    rows[item.id] = created
                    val insertAt = index.coerceAtMost(list.childCount)
                    list.addView(created.card, insertAt, LinearLayout.LayoutParams(-1, -2).apply {
                        bottomMargin = dp(10)
                    })
                }
                updateProgress(item, row)
                updateActions(item, row)
                val desiredIndex = index + if (emptyState.visibility == View.VISIBLE) 1 else 0
                if (row.card.parent === list && list.indexOfChild(row.card) != desiredIndex) {
                    row.card.animate().cancel()
                    list.removeView(row.card)
                    list.addView(row.card, desiredIndex.coerceAtMost(list.childCount))
                }
            }
        }
        render?.invoke()
        val refreshTask = object : Runnable {
            override fun run() {
                if (!activity.isFinishing && !activity.isDestroyed && root.isAttachedToWindow) {
                    render?.invoke()
                    handler.postDelayed(this, refreshIntervalMillis)
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
        root.layoutTransition = null
        list.layoutTransition = null
        emptyState.layoutTransition = null
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
        root.layoutTransition = null
        list.layoutTransition = null
        rows.values.forEach { row ->
            row.card.layoutTransition = null
            row.actions.layoutTransition = null
        }
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
