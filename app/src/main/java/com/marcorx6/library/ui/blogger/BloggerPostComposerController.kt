package com.marcorx6.library.ui

import android.app.AlertDialog
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.ComponentActivity
import android.view.View
import com.marcorx6.library.data.Game

/**
 * Owns the Blogger post editor UI and coordinates validation, preview and save.
 * Activity state and persistence remain supplied through narrow callbacks.
 */
internal class BloggerPostComposerController(
    private val activity: ComponentActivity,
    private val isAdminSession: () -> Boolean,
    private val dp: (Int) -> Int,
    private val bg: () -> Int,
    private val panel: () -> Int,
    private val white: () -> Int,
    private val gray: () -> Int,
    private val red: () -> Int,
    private val makeText: (String, Float, Int, Boolean) -> android.widget.TextView,
    private val rounded: (Int, Int) -> android.graphics.drawable.Drawable,
    private val accentForeground: () -> Int,
    private val labelsController: GamePostLabelsController,
    private val formFieldsFactory: BloggerPostFormFieldsFactory,
    private val setCoverFields: (EditText, ImageView) -> Unit,
    private val pendingCoverLocalPath: () -> String,
    private val setPendingCoverLocalPath: (String) -> Unit,
    private val pendingCoverPublicUrl: () -> String,
    private val setPendingCoverPublicUrl: (String) -> Unit,
    private val coverUploadInProgress: () -> Boolean,
    private val loadCover: (ImageView, String, String) -> Unit,
    private val chooseCover: () -> Unit,
    private val previewFactory: BloggerArticlePreviewFactory,
    private val styleAlertDialog: (AlertDialog) -> Unit,
    private val postFactory: BloggerGamePostFactory,
    private val saveController: BloggerPostSaveController,
    private val upsertGame: (Game, Game?) -> Unit,
    private val deleteCoverFileIfUnused: (String) -> Unit,
    private val resetCoverUploadFailed: () -> Unit,
    private val saveGames: () -> Unit,
    private val renderGames: () -> Unit,
    private val applyUnifiedSkin: (View) -> Unit,
    private val translate: (String) -> String
) {
    fun show(existing: Game? = null) {
        if (!isAdminSession()) {
            Toast.makeText(activity, translate("يجب تسجيل الدخول بصلاحية المسؤول للنشر."), Toast.LENGTH_SHORT).show()
            return
        }
        if (existing != null) setPendingCoverLocalPath(existing.coverLocalPath)

        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(16))
            background = rounded(bg(), 18)
        }
        val intro = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = rounded(if (android.os.Build.VERSION.SDK_INT >= 23) android.graphics.Color.argb(205, 15, 43, 70) else panel(), 15)
        }
        intro.addView(makeText(
            translate(if (existing == null) "استوديو النشر • MARCO RX6" else "استوديو التحرير • MARCO RX6"),
            17f, white(), true
        ))
        intro.addView(makeText(
            translate("جهّز الغلاف والتفاصيل والمحتوى، ثم عاين المقال قبل إرساله إلى Blogger."),
            11f, gray(), false
        ).apply { setPadding(0, dp(5), 0, 0) })
        box.addView(intro, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        val labelsUi = labelsController.create(
            initialLabel = existing?.platform,
            isNewPost = existing == null
        )
        val selectedLabels = labelsUi.selectedLabels
        val formFields = formFieldsFactory.create()
        val title = formFields.title
        val body = formFields.body
        val coverUrl = formFields.coverUrl
        val link = formFields.relatedLink
        val genre = formFields.genre
        val releaseYear = formFields.releaseYear
        val preview = formFields.coverPreview
        setCoverFields(coverUrl, preview)

        if (existing != null) {
            title.setText(existing.name)
            if (existing.platform.isNotBlank()) selectedLabels.add(existing.platform)
            body.setText(existing.description)
            coverUrl.setText(existing.coverUrl)
            link.setText(existing.url.takeIf { it != existing.sourcePostUrl } ?: "")
            genre.setText(existing.genre)
            releaseYear.setText(existing.releaseYear)
        } else {
            body.setText("")
            if (pendingCoverPublicUrl().isNotBlank()) coverUrl.setText(pendingCoverPublicUrl())
        }

        box.addView(makeText("02 • " + translate("صورة الغلاف"), 12f, red(), true).apply {
            setPadding(0, dp(8), 0, dp(6))
        })
        box.addView(preview, LinearLayout.LayoutParams(-1, dp(165)).apply {
            bottomMargin = dp(8)
        })
        loadCover(preview, pendingCoverLocalPath(), existing?.coverUrl ?: "")
        box.addView(Button(activity).apply {
            text = translate("اختيار صورة الغلاف من الهاتف")
            tag = "marco_primary_action"
            setTextColor(accentForeground())
            background = rounded(((red() and 0x00FFFFFF) or (222 shl 24)), 12)
            setOnClickListener { chooseCover() }
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        box.addView(makeText(translate("أقسام المقال • Labels من المدونة"), 10f, red(), true))
        box.addView(labelsUi.button)
        box.addView(genre)
        box.addView(releaseYear)
        box.addView(title)
        box.addView(makeText("03 • " + translate("محتوى المقال"), 12f, red(), true).apply {
            setPadding(0, dp(12), 0, dp(5))
        })
        box.addView(body, LinearLayout.LayoutParams(-1, dp(190)))
        box.addView(coverUrl)
        box.addView(link)

        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            setPadding(0, dp(4), 0, dp(8))
            addView(box)
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle(translate(if (existing == null) "إنشاء مقال جديد" else "تعديل المقال"))
            .setView(scroll)
            .setNegativeButton(translate("إغلاق"), null)
            .setNeutralButton(translate("معاينة"), null)
            .setPositiveButton(translate(if (existing == null) "نشر المقال" else "حفظ التعديلات"), null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                val web = previewFactory.create(
                    title = title.text.toString(),
                    labels = selectedLabels.joinToString("، "),
                    genre = genre.text.toString(),
                    releaseYear = releaseYear.text.toString(),
                    body = body.text.toString(),
                    coverUrl = coverUrl.text.toString(),
                    relatedLink = link.text.toString()
                )
                AlertDialog.Builder(activity)
                    .setTitle(translate("معاينة قبل النشر"))
                    .setView(web)
                    .setPositiveButton(translate("إغلاق"), null)
                    .show()
                    .also(styleAlertDialog)
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val postTitle = title.text.toString().trim()
                val coverLink = coverUrl.text.toString().trim()
                val relatedLink = link.text.toString().trim()
                val contentText = body.text.toString().trim()
                val pendingLocalPath = pendingCoverLocalPath()
                val validationFailure = BloggerPostInputValidator.validate(
                    coverUploadInProgress = coverUploadInProgress(),
                    pendingCoverLocalPath = pendingLocalPath,
                    selectedLabels = selectedLabels,
                    title = postTitle,
                    coverUrl = coverLink,
                    relatedLink = relatedLink,
                    content = contentText
                )
                if (validationFailure != null) {
                    when (validationFailure.field) {
                        BloggerPostInputValidator.Field.TITLE -> title.error = translate(validationFailure.message)
                        BloggerPostInputValidator.Field.COVER_URL -> coverUrl.error = translate(validationFailure.message)
                        BloggerPostInputValidator.Field.RELATED_LINK -> link.error = translate(validationFailure.message)
                        BloggerPostInputValidator.Field.BODY -> body.error = translate(validationFailure.message)
                        null -> Toast.makeText(
                            activity,
                            translate(validationFailure.message),
                            if (validationFailure.longMessage) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@setOnClickListener
                }
                val post = postFactory.create(
                    existing = existing,
                    title = postTitle,
                    labels = selectedLabels.toList(),
                    description = contentText,
                    coverLink = coverLink,
                    relatedLink = relatedLink,
                    genre = genre.text.toString(),
                    releaseYear = releaseYear.text.toString(),
                    pendingCoverLocalPath = pendingLocalPath,
                    pendingCoverPublicUrl = pendingCoverPublicUrl()
                )
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                saveController.save(
                    post = post,
                    labels = selectedLabels.toList(),
                    existing = existing,
                    onSuccess = { saved ->
                        upsertGame(saved, existing)
                        deleteCoverFileIfUnused(existing?.coverLocalPath.orEmpty())
                        setPendingCoverLocalPath("")
                        setPendingCoverPublicUrl("")
                        resetCoverUploadFailed()
                        saveGames()
                        renderGames()
                        dialog.dismiss()
                        Toast.makeText(activity, translate("تم نشر المقال على Blogger"), Toast.LENGTH_LONG).show()
                    },
                    onError = { message ->
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                        Toast.makeText(activity, translate(message), Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
        dialog.show()
        styleAlertDialog(dialog)
        applyUnifiedSkin(box)
        dialog.window?.setLayout(
            -1,
            (activity.resources.displayMetrics.heightPixels * 0.88f).toInt()
        )
    }
}
