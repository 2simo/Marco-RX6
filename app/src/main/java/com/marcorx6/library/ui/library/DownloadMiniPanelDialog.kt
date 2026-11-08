package com.marcorx6.library.ui

import android.app.Activity
import android.app.Dialog
import android.app.DownloadManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import com.marcorx6.library.data.DownloadDatabase
import com.marcorx6.library.data.DownloadRepository

/** Compact live download list that can sit above the game page or the in-app browser. */
internal class DownloadMiniPanelDialog(
    private val activity: Activity,
    private val repository: DownloadRepository,
    private val translate: (String) -> String,
    private val isDarkMode: () -> Boolean
) {
    fun show() {
        if (activity.isFinishing || activity.isDestroyed) return
        fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()
        val dark = isDarkMode()
        val background = if (dark) Color.rgb(8, 9, 12) else Color.rgb(242, 244, 247)
        val surface = if (dark) Color.rgb(25, 28, 34) else Color.WHITE
        val heading = if (dark) Color.WHITE else Color.rgb(25, 29, 36)
        val muted = if (dark) Color.rgb(178, 184, 194) else Color.rgb(91, 98, 110)
        val accent = if (dark) Color.rgb(91, 157, 242) else Color.rgb(35, 91, 165)

        val dialog = Dialog(activity)
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(17), dp(15), dp(17), dp(12))
            this.background = GradientDrawable().apply {
                setColor(background)
                cornerRadii = floatArrayOf(dp(22).toFloat(), dp(22).toFloat(), dp(22).toFloat(), dp(22).toFloat(), 0f, 0f, 0f, 0f)
                setStroke(dp(1), if (dark) Color.rgb(70, 77, 89) else Color.rgb(205, 211, 220))
            }
        }
        val header = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val titleBox = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        titleBox.addView(TextView(activity).apply {
            text = translate("التنزيلات")
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setTextColor(heading)
        })
        titleBox.addView(TextView(activity).apply {
            text = translate("تستمر التنزيلات حتى عند إغلاق هذه اللوحة")
            textSize = 10f
            setTextColor(muted)
        })
        header.addView(titleBox, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(TextView(activity).apply {
            text = translate("إغلاق")
            textSize = 12f
            setTextColor(accent)
            gravity = Gravity.CENTER
            setPadding(dp(13), dp(9), dp(13), dp(9))
            this.background = GradientDrawable().apply {
                setColor(surface); cornerRadius = dp(12).toFloat()
                setStroke(dp(1), if (dark) Color.rgb(70, 77, 89) else Color.rgb(205, 211, 220))
            }
            setOnClickListener { dialog.dismiss() }
        })
        root.addView(header)

        val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(activity).apply {
            isVerticalScrollBarEnabled = false
            addView(list, LinearLayout.LayoutParams(-1, -2))
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, dp(245)).apply { topMargin = dp(12) })
        root.addView(TextView(activity).apply {
            text = translate("متابعة التصفح")
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(if (dark) Color.rgb(20, 23, 29) else Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(12), dp(14), dp(12))
            this.background = GradientDrawable().apply {
                setColor(if (dark) Color.rgb(91, 157, 242) else Color.rgb(35, 91, 165))
                cornerRadius = dp(14).toFloat()
            }
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        class Row(
            val card: LinearLayout,
            val title: TextView,
            val status: TextView,
            val details: TextView,
            val progress: ProgressBar,
            val actions: LinearLayout
        )
        val rows = linkedMapOf<Long, Row>()
        val speedSamples = mutableMapOf<Long, Pair<Long, Long>>()
        val handler = Handler(Looper.getMainLooper())

        fun action(label: String, onClick: () -> Unit): TextView = TextView(activity).apply {
            text = label
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(accent)
            setPadding(dp(10), dp(7), dp(10), dp(7))
            this.background = GradientDrawable().apply {
                setColor(surface); cornerRadius = dp(9).toFloat()
                setStroke(dp(1), if (dark) Color.rgb(70, 77, 89) else Color.rgb(205, 211, 220))
            }
            setOnClickListener { onClick() }
        }

        fun makeRow(name: String): Row {
            val card = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                this.background = GradientDrawable().apply {
                    setColor(surface); cornerRadius = dp(14).toFloat()
                    setStroke(dp(1), if (dark) Color.rgb(70, 77, 89) else Color.rgb(205, 211, 220))
                }
            }
            val nameView = TextView(activity).apply {
                text = name; textSize = 12f; setTypeface(null, Typeface.BOLD); setTextColor(heading)
                maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            }
            val statusView = TextView(activity).apply { textSize = 10f; setTextColor(accent); setPadding(0, dp(3), 0, 0) }
            val detailsView = TextView(activity).apply { textSize = 10f; setTextColor(muted); setPadding(0, dp(3), 0, 0) }
            val bar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progressTintList = android.content.res.ColorStateList.valueOf(accent)
                progressBackgroundTintList = android.content.res.ColorStateList.valueOf(if (dark) Color.rgb(70, 77, 89) else Color.rgb(205, 211, 220))
            }
            val actions = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
                setPadding(0, dp(7), 0, 0)
            }
            card.addView(nameView)
            card.addView(statusView)
            card.addView(detailsView)
            card.addView(bar, LinearLayout.LayoutParams(-1, dp(5)).apply { topMargin = dp(7) })
            card.addView(actions)
            return Row(card, nameView, statusView, detailsView, bar, actions)
        }

        fun render() {
            if (!dialog.isShowing || activity.isFinishing || activity.isDestroyed) return
            val entries = runCatching { repository.getDownloads().take(8) }.getOrDefault(emptyList())
            val ids = entries.map { it.id }.toSet()
            rows.keys.filter { it !in ids }.forEach { id ->
                rows.remove(id)?.let { list.removeView(it.card) }
                speedSamples.remove(id)
            }
            if (entries.isEmpty()) {
                if (list.childCount == 0) list.addView(TextView(activity).apply {
                    text = translate("لا توجد تنزيلات في القائمة")
                    textSize = 12f; setTextColor(muted); gravity = Gravity.CENTER
                    setPadding(0, dp(25), 0, dp(25))
                })
                return
            }
            entries.forEachIndexed { index, item ->
                val row = rows[item.id] ?: makeRow(item.title).also {
                    rows[item.id] = it
                    list.addView(it.card, index.coerceAtMost(list.childCount), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) })
                }
                if (list.indexOfChild(row.card) != index) {
                    list.removeView(row.card)
                    list.addView(row.card, index.coerceAtMost(list.childCount))
                }
                if (row.title.text.toString() != item.title) row.title.text = item.title
                val statusLabel = when (item.status) {
                    DownloadManager.STATUS_PENDING, DownloadDatabase.STATUS_SCHEDULED -> translate("في الانتظار")
                    DownloadManager.STATUS_RUNNING -> translate("قيد التنزيل")
                    DownloadManager.STATUS_PAUSED -> translate("متوقف مؤقتاً")
                    DownloadManager.STATUS_SUCCESSFUL -> translate("اكتمل")
                    DownloadManager.STATUS_FAILED -> translate("فشل")
                    else -> translate("غير معروف")
                }
                val percent = if (item.totalBytes > 0L) (item.downloadedBytes * 100L / item.totalBytes).coerceIn(0L, 100L).toInt() else 0
                val now = SystemClock.elapsedRealtime()
                val previous = speedSamples[item.id]
                val speed = if (item.status == DownloadManager.STATUS_RUNNING && previous != null && now > previous.second && item.downloadedBytes >= previous.first) {
                    ((item.downloadedBytes - previous.first) * 1000L) / (now - previous.second).coerceAtLeast(1L)
                } else 0L
                if (item.status == DownloadManager.STATUS_RUNNING) speedSamples[item.id] = item.downloadedBytes to now else speedSamples.remove(item.id)
                row.status.text = if (item.totalBytes > 0L) "$statusLabel • $percent%" else statusLabel
                val downloaded = android.text.format.Formatter.formatFileSize(activity, item.downloadedBytes.coerceAtLeast(0L))
                val total = if (item.totalBytes > 0L) android.text.format.Formatter.formatFileSize(activity, item.totalBytes) else translate("حجم غير معروف")
                val speedText = if (item.status == DownloadManager.STATUS_RUNNING) " • " + android.text.format.Formatter.formatFileSize(activity, speed) + "/s" else ""
                val etaText = if (speed > 0L && item.totalBytes > item.downloadedBytes) {
                    val seconds = ((item.totalBytes - item.downloadedBytes) / speed).coerceAtLeast(0L)
                    val eta = if (seconds >= 3600L) "${seconds / 3600L}h ${(seconds % 3600L) / 60L}m"
                        else if (seconds >= 60L) "${seconds / 60L}m ${seconds % 60L}s" else "${seconds}s"
                    " • " + translate("متبقي") + " " + eta
                } else ""
                row.details.text = "$downloaded / $total$speedText$etaText"
                row.progress.isIndeterminate = item.status == DownloadManager.STATUS_RUNNING && item.totalBytes <= 0L
                if (!row.progress.isIndeterminate) row.progress.progress = percent
                row.progress.visibility = if (item.status == DownloadManager.STATUS_SUCCESSFUL || item.status == DownloadManager.STATUS_FAILED) View.GONE else View.VISIBLE
                row.actions.removeAllViews()
                if (item.status == DownloadManager.STATUS_PENDING || item.status == DownloadDatabase.STATUS_SCHEDULED || item.status == DownloadManager.STATUS_RUNNING) {
                    row.actions.addView(action(translate("إيقاف")) { repository.pauseDownload(item.id); render() })
                } else if (item.status == DownloadManager.STATUS_PAUSED || item.status == DownloadManager.STATUS_FAILED) {
                    row.actions.addView(action(translate("استكمال")) { repository.resumeDownload(item.id); render() })
                }
                if (item.status != DownloadManager.STATUS_SUCCESSFUL) {
                    row.actions.addView(action(translate("إلغاء")) { repository.deleteDownload(item.id); render() })
                }
            }
        }

        val refresh = object : Runnable {
            override fun run() {
                if (dialog.isShowing) {
                    render()
                    handler.postDelayed(this, 900L)
                }
            }
        }
        dialog.setContentView(root)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnDismissListener { handler.removeCallbacks(refresh) }
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(-1, dp(370))
            setGravity(Gravity.BOTTOM)
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.18f)
        }
        render()
        handler.postDelayed(refresh, 900L)
    }
}
