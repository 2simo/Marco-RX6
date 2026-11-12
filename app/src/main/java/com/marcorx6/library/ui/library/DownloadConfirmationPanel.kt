package com.marcorx6.library.ui

import android.app.Activity
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.webkit.URLUtil
import android.net.Uri
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.marcorx6.library.data.DownloadStoragePreferences
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Download-add sheet shown directly over the in-app browser.
 * The actual transfer remains owned by DownloadRepository.
 */
internal class DownloadConfirmationPanel(
    private val activity: Activity,
    private val translate: (String) -> String,
    private val dark: Boolean
) {
    fun show(
        url: String,
        suggestedName: String,
        mimeType: String,
        onConfirm: (String) -> Unit
    ) {
        if (activity.isFinishing || activity.isDestroyed) return

        fun dp(value: Int): Int =
            (value * activity.resources.displayMetrics.density).toInt()

        val appearance = AppearancePreferencesStore.appearance(activity)
        val selectedAccentId = appearance.getString(
            AccentPalette.PREFERENCE_KEY, AccentPalette.DEFAULT_ID
        ) ?: AccentPalette.DEFAULT_ID
        val silver = AccentPalette.color(selectedAccentId)
        val accentText = if (selectedAccentId == "black_silver") {
            if (dark) Color.rgb(218, 225, 235) else Color.rgb(43, 49, 59)
        } else silver
        val background = AccentPalette.surface(
            if (dark) Color.rgb(13, 15, 19) else Color.rgb(247, 248, 250), silver, 0.045f
        )
        val surface = AccentPalette.surface(
            if (dark) Color.rgb(27, 30, 36) else Color.WHITE, silver, if (dark) 0.10f else 0.055f
        )
        val raised = AccentPalette.surface(
            if (dark) Color.rgb(36, 40, 47) else Color.rgb(239, 242, 246), silver, if (dark) 0.13f else 0.075f
        )
        val heading = if (dark) Color.WHITE else Color.rgb(25, 29, 36)
        val muted = if (dark) Color.rgb(157, 166, 179) else Color.rgb(103, 111, 124)
        val border = if (dark)
            AccentPalette.surface(Color.rgb(69, 77, 90), silver, 0.40f)
        else AccentPalette.surface(Color.rgb(211, 216, 224), silver, 0.34f)

        fun shape(fill: Int, stroke: Int = border, radius: Int = 15) =
            GradientDrawable().apply {
                setColor(fill)
                cornerRadius = dp(radius).toFloat()
                setStroke(dp(1), stroke)
            }

        fun label(value: String, size: Float, color: Int, bold: Boolean = false) =
            TextView(activity).apply {
                text = value
                textSize = size
                setTextColor(color)
                if (bold) setTypeface(null, Typeface.BOLD)
            }

        val fileName = suggestedName.ifBlank { URLUtil.guessFileName(url, null, mimeType) }
        val extension = fileName.substringAfterLast('.', "FILE")
            .uppercase().take(6).ifBlank { "FILE" }
        val host = runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault("")
        val storageName = runCatching {
            DownloadStoragePreferences(activity).displayName()
        }.getOrDefault("تخزين التطبيق")

        val dialog = Dialog(activity)
        val motionEnabled = activity.getSharedPreferences("app_settings", Activity.MODE_PRIVATE)
            .getBoolean("animations", true)
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(10), dp(18), dp(20))
            this.background = shape(background, border, 25)
            elevation = dp(20).toFloat()
            alpha = if (motionEnabled) 0f else 1f
            translationY = if (motionEnabled) dp(18).toFloat() else 0f
        }

        val scroll = ScrollView(activity).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            addView(root, android.widget.FrameLayout.LayoutParams(-1, -2))
        }

        val handleRow = LinearLayout(activity).apply {
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(13))
        }
        handleRow.addView(View(activity).apply {
            this.background = GradientDrawable().apply {
                setColor(if (dark) Color.rgb(91, 99, 112) else Color.rgb(190, 196, 205))
                cornerRadius = dp(3).toFloat()
            }
        }, LinearLayout.LayoutParams(dp(42), dp(4)))
        root.addView(handleRow, LinearLayout.LayoutParams(-1, -2))

        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val headingBox = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        headingBox.addView(label("DOWNLOAD MANAGER  /  MARCO RX6", 9f, silver, true))
        headingBox.addView(label(translate("إضافة إلى التنزيلات"), 21f, heading, true).apply {
            setPadding(0, dp(4), 0, 0)
        })
        header.addView(headingBox, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(TextView(activity).apply {
            text = "×"
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(muted)
            this.background = shape(raised)
            isClickable = true
            isFocusable = true
            contentDescription = translate("إغلاق")
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(dp(42), dp(42)))
        root.addView(header)

        val fileCard = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(13), dp(14), dp(13), dp(14))
            this.background = shape(surface)
        }
        fileCard.addView(TextView(activity).apply {
            text = extension
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(AccentPalette.foregroundForColor(silver))
            this.background = GradientDrawable().apply {
                setColor(silver)
                cornerRadius = dp(12).toFloat()
            }
        }, LinearLayout.LayoutParams(dp(54), dp(50)))
        val fileInfo = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        fileInfo.addView(label(fileName, 13f, heading, true).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        })
        fileInfo.addView(label(host, 10f, muted).apply {
            setPadding(0, dp(5), 0, 0)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        fileCard.addView(fileInfo, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(fileCard, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(16)
        })

        root.addView(label(translate("رابط التنزيل"), 12f, muted, true).apply {
            setPadding(dp(2), dp(17), dp(2), dp(7))
        })
        val urlCard = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(8), dp(8))
            this.background = shape(raised, Color.TRANSPARENT)
        }
        urlCard.addView(label(url, 10f, muted).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.MIDDLE
            setTextIsSelectable(true)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        urlCard.addView(TextView(activity).apply {
            text = translate("نسخ")
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(heading)
            setPadding(dp(12), dp(9), dp(12), dp(9))
            this.background = shape(surface)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText("download_url", url))
                text = translate("تم النسخ")
                postDelayed({ text = translate("نسخ") }, 1200L)
            }
        }, LinearLayout.LayoutParams(-2, dp(38)).apply { marginStart = dp(8) })
        root.addView(urlCard, LinearLayout.LayoutParams(-1, -2))

        root.addView(label(translate("اسم الملف"), 12f, muted, true).apply {
            setPadding(dp(2), dp(16), dp(2), dp(7))
        })
        val nameInput = EditText(activity).apply {
            setSingleLine(true)
            textSize = 13f
            setTextColor(heading)
            setHintTextColor(muted)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setText(fileName)
            setSelection(text.length)
            setPadding(dp(13), 0, dp(13), 0)
            this.background = shape(surface)
            imeOptions = EditorInfo.IME_ACTION_DONE
            setSelectAllOnFocus(true)
        }
        root.addView(nameInput, LinearLayout.LayoutParams(-1, dp(50)))

        val details = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            this.background = shape(raised, Color.TRANSPARENT)
        }
        fun detailLine(title: String, value: String, last: Boolean = false) {
            val line = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, if (details.childCount == 0) 0 else dp(10), 0, if (last) 0 else dp(10))
            }
            line.addView(label(title, 11f, muted), LinearLayout.LayoutParams(0, -2, 1f))
            line.addView(label(value, 11f, heading, true).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.END
            }, LinearLayout.LayoutParams(0, -2, 1.35f))
            details.addView(line)
        }
        detailLine(translate("نوع الملف"), mimeType.substringBefore(';').ifBlank { extension })
        detailLine(translate("مجلد الحفظ"), storageName)
        detailLine(translate("طريقة التنزيل"), translate("في الخلفية • قابل للاستئناف"), true)
        root.addView(details, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(14)
        })

        root.addView(label(
            translate("يمكنك متابعة السرعة والتقدم والتحكم في التنزيل من مدير التنزيلات."),
            10f,
            muted
        ).apply {
            setPadding(dp(2), dp(12), dp(2), dp(3))
        })

        val actions = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(15), 0, 0)
        }
        val cancel = TextView(activity).apply {
            text = translate("إلغاء")
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(accentText)
            this.background = shape(surface)
            isClickable = true
            isFocusable = true
            setOnClickListener { dialog.dismiss() }
        }
        actions.addView(cancel, LinearLayout.LayoutParams(0, dp(50), 0.85f).apply {
            marginEnd = dp(9)
        })
        val start = TextView(activity).apply {
            text = translate("بدء التنزيل")
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(AccentPalette.foregroundForColor(silver))
            setCompoundDrawablesRelativeWithIntrinsicBounds(com.marcorx6.library.R.drawable.ic_action_download, 0, 0, 0)
            compoundDrawablePadding = dp(7)
            compoundDrawableTintList = android.content.res.ColorStateList.valueOf(AccentPalette.foregroundForColor(silver))
            this.background = GradientDrawable().apply {
                setColor(silver)
                cornerRadius = dp(15).toFloat()
            }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                val name = nameInput.text.toString().trim()
                if (name.isBlank()) {
                    nameInput.error = translate("أدخل اسم الملف")
                    return@setOnClickListener
                }
                dialog.dismiss()
                onConfirm(name)
            }
        }
        actions.addView(start, LinearLayout.LayoutParams(0, dp(50), 1.5f))
        root.addView(actions)

        dialog.setContentView(scroll)
        dialog.setCanceledOnTouchOutside(true)
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(-1, WindowManager.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = 0.55f }
            navigationBarColor = background
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            SystemBarPolicy.apply(this, dark)
        }
        if (motionEnabled) {
            root.animate().cancel()
            root.animate().alpha(1f).translationY(0f).setDuration(190L)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        }
        var lifecycleObserver: DefaultLifecycleObserver? = null
        val lifecycleOwner = activity as? LifecycleOwner
        dialog.setOnDismissListener {
            lifecycleObserver?.let { lifecycleOwner?.lifecycle?.removeObserver(it) }
        }
        val observer = object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                if (dialog.isShowing) dialog.dismiss()
                owner.lifecycle.removeObserver(this)
            }
        }
        lifecycleObserver = observer
        lifecycleOwner?.lifecycle?.addObserver(observer)
    }
}
