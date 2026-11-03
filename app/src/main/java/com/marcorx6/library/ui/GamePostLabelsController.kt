package com.marcorx6.library.ui

import android.app.Activity
import android.graphics.drawable.Drawable
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

internal data class GamePostLabelsUi(
    val button: Button,
    val selectedLabels: MutableSet<String>
)

internal class GamePostLabelsController(
    private val activity: Activity,
    private val white: () -> Int,
    private val panel: () -> Int,
    private val rounded: (Int, Int) -> Drawable,
    private val fetchLabels: () -> List<String>,
    private val executeIo: (() -> Unit) -> Unit,
    private val postToUi: (() -> Unit) -> Unit,
    private val styleAlertDialog: (AlertDialog) -> Unit
) {
    fun create(initialLabel: String?, isNewPost: Boolean): GamePostLabelsUi {
        val button = Button(activity).apply {
            text = "تحميل تسميات المدونة..."
            setTextColor(white())
            background = rounded(panel(), 12)
        }
        val selectedLabels = linkedSetOf<String>()
        val availableLabels = mutableListOf<String>()

        fun refreshButton() {
            button.text = if (selectedLabels.isEmpty()) {
                "اختيار Labels من المدونة"
            } else {
                "الأقسام (${selectedLabels.size}): " + selectedLabels.joinToString("، ")
            }
        }

        if (!initialLabel.isNullOrBlank()) selectedLabels.add(initialLabel)

        button.setOnClickListener {
            if (availableLabels.isEmpty()) {
                Toast.makeText(activity, "لم يتم تحميل تسميات المدونة بعد", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val checked = BooleanArray(availableLabels.size) { availableLabels[it] in selectedLabels }
            AlertDialog.Builder(activity)
                .setTitle("اختر أقسام المقال من المدونة")
                .setMultiChoiceItems(availableLabels.toTypedArray(), checked) { _, which, isChecked ->
                    if (isChecked) selectedLabels.add(availableLabels[which])
                    else selectedLabels.remove(availableLabels[which])
                    refreshButton()
                }
                .setPositiveButton("تم", null)
                .setNeutralButton("تحديث التسميات") { _, _ ->
                    executeIo {
                        try {
                            val latest = fetchLabels()
                            postToUi {
                                availableLabels.clear()
                                availableLabels.addAll(latest)
                                selectedLabels.retainAll(availableLabels.toSet())
                                refreshButton()
                                Toast.makeText(activity, "تم تحديث ${latest.size} تسمية من المدونة", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            postToUi {
                                Toast.makeText(activity, e.message ?: "تعذر تحديث التسميات", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
                .show()
                .also(styleAlertDialog)
        }

        executeIo {
            try {
                val labels = fetchLabels()
                postToUi {
                    availableLabels.clear()
                    availableLabels.addAll(labels)
                    if (isNewPost && selectedLabels.isEmpty() && labels.isNotEmpty()) {
                        selectedLabels.add(labels.first())
                    }
                    refreshButton()
                    button.isEnabled = labels.isNotEmpty()
                    if (labels.isEmpty()) button.text = "لا توجد Labels في المقالات المنشورة"
                }
            } catch (_: Exception) {
                postToUi {
                    button.text = "تعذر تحميل Labels • حاول التحديث"
                    button.isEnabled = true
                }
            }
        }

        return GamePostLabelsUi(button, selectedLabels)
    }
}
