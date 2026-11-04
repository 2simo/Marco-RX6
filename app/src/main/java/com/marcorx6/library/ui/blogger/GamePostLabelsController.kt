package com.marcorx6.library.ui

import android.app.Activity
import android.graphics.drawable.Drawable
import android.widget.Button
import android.widget.Toast
import android.app.AlertDialog

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
    private val styleAlertDialog: (AlertDialog) -> Unit,
    private val translate: (String) -> String
) {
    fun create(initialLabel: String?, isNewPost: Boolean): GamePostLabelsUi {
        val button = Button(activity).apply {
            text = translate("تحميل تسميات المدونة...")
            setTextColor(white())
            background = rounded(panel(), 12)
            minHeight = (activity.resources.displayMetrics.density * 52).toInt()
            contentDescription = translate("اختيار أقسام المقال من المدونة")
            isAllCaps = false
        }
        val selectedLabels = linkedSetOf<String>()
        val availableLabels = mutableListOf<String>()

        fun refreshButton() {
            button.text = if (selectedLabels.isEmpty()) {
                translate("اختيار Labels من المدونة")
            } else {
                translate("الأقسام") + " (${selectedLabels.size}): " + selectedLabels.joinToString("، ")
            }
            button.contentDescription = if (selectedLabels.isEmpty()) {
                translate("اختيار أقسام المقال من المدونة")
            } else {
                translate("الأقسام المحددة") + ": " + selectedLabels.joinToString("، ")
            }
        }

        if (!initialLabel.isNullOrBlank()) selectedLabels.add(initialLabel)

        button.setOnClickListener {
            if (availableLabels.isEmpty()) {
                Toast.makeText(activity, translate("لم يتم تحميل تسميات المدونة بعد"), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val checked = BooleanArray(availableLabels.size) { availableLabels[it] in selectedLabels }
            AlertDialog.Builder(activity)
                .setTitle(translate("اختر أقسام المقال من المدونة"))
                .setMultiChoiceItems(availableLabels.toTypedArray(), checked) { _, which, isChecked ->
                    if (isChecked) selectedLabels.add(availableLabels[which])
                    else selectedLabels.remove(availableLabels[which])
                    refreshButton()
                }
                .setPositiveButton(translate("تم"), null)
                .setNeutralButton(translate("تحديث التسميات")) { _, _ ->
                    executeIo {
                        try {
                            val latest = fetchLabels()
                            postToUi {
                                availableLabels.clear()
                                availableLabels.addAll(latest)
                                selectedLabels.retainAll(availableLabels.toSet())
                                refreshButton()
                                Toast.makeText(activity, translate("تم تحديث التسميات من المدونة") + " (${latest.size})", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            postToUi {
                                Toast.makeText(activity, e.message ?: translate("تعذر تحديث التسميات"), Toast.LENGTH_LONG).show()
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
                    if (labels.isEmpty()) {
                        button.text = translate("لا توجد Labels في المقالات المنشورة")
                        button.contentDescription = button.text
                    }
                }
            } catch (_: Exception) {
                postToUi {
                    button.text = translate("تعذر تحميل Labels • حاول التحديث")
                    button.contentDescription = button.text
                    button.isEnabled = true
                }
            }
        }

        return GamePostLabelsUi(button, selectedLabels)
    }
}
