package com.marcorx6.library.ui

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.webkit.URLUtil
import android.net.Uri

/**
 * A focused, 1DM-inspired download sheet shown directly over the in-app browser.
 * It gathers the filename before handing the real transfer to DownloadRepository.
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

        val background = if (dark) Color.rgb(12, 14, 18) else Color.rgb(248, 249, 251)
        val surface = if (dark) Color.rgb(27, 30, 37) else Color.WHITE
        val raised = if (dark) Color.rgb(36, 40, 48) else Color.rgb(239, 242, 246)
        val silver = if (dark) Color.rgb(205, 212, 222) else Color.rgb(89, 99, 114)
        val heading = if (dark) Color.WHITE else Color.rgb(25, 29, 36)
        val muted = if (dark) Color.rgb(160, 168, 181) else Color.rgb(103, 111, 124)
        val border = if (dark) Color.rgb(75, 82, 95) else Color.rgb(211, 216, 224)

        fun panelShape(fill: Int, stroke: Int = border, radius: Int = 16) =
            GradientDrawable().apply {
                setColor(fill)
                cornerRadius = dp(radius).toFloat()
                setStroke(dp(1), stroke)
            }

        fun label(text: String, size: Float, color: Int, bold: Boolean = false) =
            TextView(activity).apply {
                this.text = text
                textSize = size
                setTextColor(color)
                if (bold) setTypeface(null, Typeface.BOLD)
            }

        val dialog = Dialog(activity)
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(22))
            setBackground(panelShape(background, border, 25))
            elevation = dp(18).toFloat()
        }

        val handleRow = LinearLayout(activity).apply {
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(14))
        }
        handleRow.addView(View(activity).apply {
            background = GradientDrawable().apply {
                setColor(if (dark) Color.rgb(91, 98, 110) else Color.rgb(190, 196, 205))
                cornerRadius = dp(3).toFloat()
            }
        }, LinearLayout.LayoutParams(dp(42), dp(4)))
        root.addView(handleRow, LinearLayout.LayoutParams(-1, -2))

        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val headingBox = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        headingBox.addView(label("MARCO RX6  /  DOWNLOAD", 9f, silver, true))
        headingBox.addView(label(translate("إضافة إلى التنزيلات"), 21f, heading, true).apply {
            setPadding(0, dp(4), 0, 0)
        })
        header.addView(headingBox, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(TextView(activity).apply {
            text = "×"
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(muted)
            background = panelShape(raised)
            isClickable = true
            isFocusable = true
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(dp(42), dp(42)))
        root.addView(header)

        val fileCard = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(13), dp(14), dp(13), dp(14))
            background = panelShape(surface)
        }
        val extension = URLUtil.guessFileName(url, null, mimeType)
            .substringAfterLast('.', "FILE").uppercase().take(5).ifBlank { "FILE" }
        fileCard.addView(TextView(activity).apply {
            text = extension
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(if (dark) Color.rgb(22, 25, 31) else Color.WHITE)
            background = GradientDrawable().apply {
                setColor(silver)
                cornerRadius = dp(12).toFloat()
            }
        }, LinearLayout.LayoutParams(dp(54), dp(48)))
        val fileInfo = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        fileInfo.addView(label(translate("ملف جاهز للتنزيل"), 13f, heading, true))
        fileInfo.addView(label(
            Uri.parse(url).host.orEmpty(),
            10f,
            muted
        ).apply { setPadding(0, dp(5), 0, 0) })
        fileCard.addView(fileInfo, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(fileCard, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(18)
        })

        root.addView(label(translate("اسم الملف"), 12f, muted, true).apply {
            setPadding(dp(2), dp(18), dp(2), dp(7))
        })
        val nameInput = EditText(activity).apply {
            setSingleLine(true)
            textSize = 13f
            setTextColor(heading)
            setHintTextColor(muted)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setText(suggestedName.ifBlank { URLUtil.guessFileName(url, null, mimeType) })
            setSelection(text.length)
            setPadding(dp(13), 0, dp(13), 0)
            background = panelShape(surface)
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
        }
        root.addView(nameInput, LinearLayout.LayoutParams(-1, dp(50)))

        val details = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = panelShape(raised, Color.TRANSPARENT)
        }
        fun detailLine(title: String, value: String) {
            val line = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            line.addView(label(title, 11f, muted), LinearLayout.LayoutParams(0, -2, 1f))
            line.addView(label(value, 11f, heading, true).apply {
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                gravity = Gravity.END
            }, LinearLayout.LayoutParams(0, -2, 1.4f))
            details.addView(line)
        }
        detailLine(translate("نوع الملف"), mimeType.ifBlank { extension })
        detailLine(translate("مكان الحفظ"), "Download/MARCO RX6")
        detailLine(translate("طريقة التنزيل"), translate("في الخلفية"))
        root.addView(details, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(14)
        })

        root.addView(label(
            translate("سيستمر التنزيل في الخلفية ويمكنك متابعة السرعة والتقدم من مدير التنزيلات."),
            10f,
            muted
        ).apply {
            setPadding(dp(2), dp(12), dp(2), dp(4))
        })

        val actions = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(16), 0, 0)
        }
        val cancel = TextView(activity).apply {
            text = translate("إلغاء")
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(silver)
            background = panelShape(surface)
            isClickable = true
            isFocusable = true
            setOnClickListener { dialog.dismiss() }
        }
        actions.addView(cancel, LinearLayout.LayoutParams(0, dp(50), 0.85f).apply {
            marginEnd = dp(9)
        })
        val start = TextView(activity).apply {
            text = translate("بدء التنزيل  ↓")
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(if (dark) Color.rgb(19, 22, 28) else Color.WHITE)
            background = GradientDrawable().apply {
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

        dialog.setContentView(root)
        dialog.setCanceledOnTouchOutside(true)
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(-1, WindowManager.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = 0.52f }
            navigationBarColor = background
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }
}
