package com.marcorx6.library.ui

import android.app.AlertDialog
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.marcorx6.library.data.Game
import org.json.JSONArray
import org.json.JSONObject

/**
 * Full-screen admin publishing studio. Blogger/Drive operations remain delegated
 * to the existing controllers so this UI change does not replace either backend.
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
    private data class LinkFields(val label: EditText, val url: EditText, val row: LinearLayout)

    fun show(existing: Game? = null) {
        if (!isAdminSession()) {
            Toast.makeText(activity, translate("يجب تسجيل الدخول بصلاحية المسؤول للنشر."), Toast.LENGTH_SHORT).show()
            return
        }
        if (existing != null) setPendingCoverLocalPath(existing.coverLocalPath)

        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                if (android.os.Build.VERSION.SDK_INT >= 23)
                    intArrayOf(android.graphics.Color.rgb(9, 31, 54), android.graphics.Color.rgb(6, 17, 32))
                else intArrayOf(bg(), panel())
            ).apply {
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), android.graphics.Color.argb(115, 74, 153, 222))
            }
        }
        fun section(text: String) = makeText(text, 13f, white(), true).apply {
            setPadding(0, dp(12), 0, dp(7))
        }
        fun addField(target: LinearLayout, view: View) {
            target.addView(view, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
        }
        fun makeAction(text: String, primary: Boolean = false) = Button(activity).apply {
            this.text = translate(text)
            isAllCaps = false
            minHeight = dp(48)
            setTextColor(if (primary) accentForeground() else white())
            background = if (primary) android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(android.graphics.Color.rgb(37, 132, 222), android.graphics.Color.rgb(15, 73, 145))
            ).apply { cornerRadius = dp(13).toFloat() }
            else rounded(panel(), 13)
        }

        val intro = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(android.graphics.Color.argb(225, 25, 91, 151), android.graphics.Color.argb(205, 11, 39, 69))
            ).apply {
                cornerRadius = dp(17).toFloat()
                setStroke(dp(1), android.graphics.Color.argb(130, 103, 177, 239))
            }
        }
        intro.addView(makeText(
            translate(if (existing == null) "استوديو النشر • MARCO RX6" else "استوديو التحرير • MARCO RX6"),
            18f, white(), true
        ))
        intro.addView(makeText(
            translate("أدخل معلومات اللعبة وروابطها، ثم راجع شكل المقال قبل النشر."),
            12f, gray(), false
        ).apply { setPadding(0, dp(5), 0, 0) })
        box.addView(intro, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })

        val form = formFieldsFactory.create()
        val title = form.title
        val body = form.body
        val coverUrl = form.coverUrl
        val genre = form.genre
        val releaseYear = form.releaseYear
        val size = form.packageSize
        val version = form.version
        val format = form.fileFormat
        val language = form.language
        val password = form.password
        val rights = form.rights
        val coverPreview = form.coverPreview
        setCoverFields(coverUrl, coverPreview)

        if (existing != null) {
            title.setText(existing.name)
            body.setText(existing.description)
            coverUrl.setText(existing.coverUrl)
            genre.setText(existing.genre)
            releaseYear.setText(existing.releaseYear)
            size.setText(existing.packageSize)
            version.setText(existing.version)
            format.setText(existing.fileFormat.ifBlank { "PKG" })
            language.setText(existing.language.ifBlank { "الإنجليزية" })
            password.setText(existing.password)
            rights.setText(existing.rights)
        } else {
            format.setText("PKG")
            language.setText("الإنجليزية")
            body.setText("")
            if (pendingCoverPublicUrl().isNotBlank()) coverUrl.setText(pendingCoverPublicUrl())
        }

        val labelsUi = labelsController.create(existing?.platform, existing == null)
        val selectedLabels = labelsUi.selectedLabels
        box.addView(section("01 • " + translate("العنوان وأقسام المقال")))
        addField(box, title)
        addField(box, labelsUi.button)
        addField(box, genre)
        addField(box, releaseYear)

        box.addView(section("02 • " + translate("معلومات اللعبة")))
        addField(box, size)
        addField(box, version)
        addField(box, format)
        addField(box, language)
        addField(box, password)
        addField(box, rights)

        box.addView(section("03 • " + translate("صورة الغلاف")))
        box.addView(coverPreview, LinearLayout.LayoutParams(-1, dp(178)).apply {
            bottomMargin = dp(9)
        })
        loadCover(coverPreview, pendingCoverLocalPath(), existing?.coverUrl ?: "")
        addField(box, makeAction("اختيار صورة الغلاف من الهاتف", primary = true).apply {
            contentDescription = translate("اختيار صورة غلاف المقال من الهاتف")
            setOnClickListener { chooseCover() }
        })
        addField(box, coverUrl)

        box.addView(section("04 • " + translate("روابط التحميل المباشرة")))
        val linksContainer = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val linkRows = mutableListOf<LinkFields>()

        fun addLinkRow(initialLabel: String = "", initialUrl: String = "") {
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = rounded(panel(), 14)
            }
            val label = EditText(activity).apply {
                hint = translate("اسم الرابط (مثال: Google Drive)")
                setSingleLine(true)
                minHeight = dp(52)
                setText(initialLabel)
                setPadding(dp(12), dp(10), dp(12), dp(10))
            }
            val url = EditText(activity).apply {
                hint = translate("رابط التحميل HTTPS")
                setSingleLine(true)
                minHeight = dp(52)
                inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_URI
                setText(initialUrl)
                setPadding(dp(12), dp(10), dp(12), dp(10))
            }
            row.addView(makeText(translate("رابط تحميل"), 12f, white(), true))
            addField(row, label)
            addField(row, url)
            val remove = makeAction("إزالة هذا الرابط").apply {
                setTextColor(android.graphics.Color.rgb(255, 130, 145))
                setOnClickListener {
                    linkRows.removeAll { it.row === row }
                    linksContainer.removeView(row)
                }
            }
            row.addView(remove, LinearLayout.LayoutParams(-1, -2))
            linkRows += LinkFields(label, url, row)
            linksContainer.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(9)
            })
        }

        val savedLinks = try { JSONArray(existing?.downloadLinks.orEmpty()) } catch (_: Exception) { JSONArray() }
        if (savedLinks.length() > 0) {
            for (i in 0 until savedLinks.length()) {
                val item = savedLinks.optJSONObject(i) ?: continue
                addLinkRow(item.optString("label"), item.optString("url"))
            }
        } else {
            val oldUrl = existing?.url.orEmpty().takeIf { it != existing?.sourcePostUrl }.orEmpty()
            if (oldUrl.isNotBlank()) addLinkRow("تحميل اللعبة", oldUrl)
            else addLinkRow()
        }
        box.addView(linksContainer, LinearLayout.LayoutParams(-1, -2))
        addField(box, makeAction("إضافة رابط جديد", primary = true).apply {
            setOnClickListener { addLinkRow() }
        })

        box.addView(section("05 • " + translate("وصف اللعبة")))
        addField(box, body)

        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            setPadding(0, dp(3), 0, dp(8))
            addView(box)
        }
        val dialog = AlertDialog.Builder(activity)
            .setView(scroll)
            .setNegativeButton(translate("إغلاق"), null)
            .setNeutralButton(translate("معاينة"), null)
            .setPositiveButton(translate(if (existing == null) "نشر المقال" else "حفظ التعديلات"), null)
            .create()
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        fun collectLinks(): String? {
            val result = JSONArray()
            for ((index, item) in linkRows.withIndex()) {
                val label = item.label.text.toString().trim()
                val url = item.url.text.toString().trim()
                if (label.isBlank() && url.isBlank()) continue
                if (label.isBlank() || url.isBlank()) {
                    Toast.makeText(activity, translate("أدخل اسم الرابط وعنوانه معاً") + " • " + (index + 1), Toast.LENGTH_LONG).show()
                    return null
                }
                if (!url.startsWith("https://", ignoreCase = true)) {
                    item.url.error = translate("يجب أن يبدأ الرابط بـ HTTPS")
                    return null
                }
                result.put(JSONObject().put("label", label).put("url", url))
            }
            return result.toString()
        }

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).apply {
                minHeight = dp(48)
                contentDescription = translate("إغلاق استوديو النشر")
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).apply {
                minHeight = dp(48)
                contentDescription = translate("معاينة المقال قبل النشر")
                setOnClickListener {
                    val linksJson = collectLinks() ?: return@setOnClickListener
                    val firstUrl = try { JSONArray(linksJson).optJSONObject(0)?.optString("url").orEmpty() } catch (_: Exception) { "" }
                    val web = previewFactory.create(
                        title = title.text.toString(),
                        labels = selectedLabels.joinToString("، "),
                        genre = genre.text.toString(),
                        releaseYear = releaseYear.text.toString(),
                        body = body.text.toString(),
                        coverUrl = coverUrl.text.toString(),
                        relatedLink = firstUrl,
                        packageSize = size.text.toString(),
                        version = version.text.toString(),
                        fileFormat = format.text.toString(),
                        language = language.text.toString(),
                        password = password.text.toString(),
                        rights = rights.text.toString(),
                        downloadLinksJson = linksJson
                    ).apply { contentDescription = translate("معاينة كاملة لمقال Blogger قبل النشر") }
                    val previewDialog = AlertDialog.Builder(activity)
                        .setTitle(translate("معاينة قبل النشر"))
                        .setView(web)
                        .setPositiveButton(translate("إغلاق"), null)
                        .create()
                    previewDialog.show()
                    styleAlertDialog(previewDialog)
                    previewDialog.getButton(AlertDialog.BUTTON_POSITIVE).minHeight = dp(48)
                }
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).apply {
                minHeight = dp(52)
                contentDescription = translate(if (existing == null) "نشر المقال على Blogger" else "حفظ تعديلات المقال")
                setOnClickListener {
                    val postTitle = title.text.toString().trim()
                    val coverLink = coverUrl.text.toString().trim()
                    val linksJson = collectLinks() ?: return@setOnClickListener
                    val firstUrl = try { JSONArray(linksJson).optJSONObject(0)?.optString("url").orEmpty() } catch (_: Exception) { "" }
                    val pendingLocalPath = pendingCoverLocalPath()
                    val validationFailure = BloggerPostInputValidator.validate(
                        coverUploadInProgress = coverUploadInProgress(),
                        pendingCoverLocalPath = pendingLocalPath,
                        selectedLabels = selectedLabels,
                        title = postTitle,
                        coverUrl = coverLink,
                        relatedLink = firstUrl,
                        content = body.text.toString().trim()
                    )
                    if (validationFailure != null) {
                        when (validationFailure.field) {
                            BloggerPostInputValidator.Field.TITLE -> title.error = translate(validationFailure.message)
                            BloggerPostInputValidator.Field.COVER_URL -> coverUrl.error = translate(validationFailure.message)
                            BloggerPostInputValidator.Field.RELATED_LINK -> Toast.makeText(activity, translate(validationFailure.message), Toast.LENGTH_LONG).show()
                            BloggerPostInputValidator.Field.BODY, null -> Toast.makeText(
                                activity, translate(validationFailure.message),
                                if (validationFailure.longMessage) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                            ).show()
                        }
                        return@setOnClickListener
                    }
                    val post = postFactory.create(
                        existing = existing,
                        title = postTitle,
                        labels = selectedLabels.toList(),
                        description = body.text.toString().trim(),
                        coverLink = coverLink,
                        relatedLink = firstUrl,
                        genre = genre.text.toString(),
                        releaseYear = releaseYear.text.toString(),
                        pendingCoverLocalPath = pendingLocalPath,
                        pendingCoverPublicUrl = pendingCoverPublicUrl(),
                        packageSize = size.text.toString(),
                        version = version.text.toString(),
                        fileFormat = format.text.toString(),
                        language = language.text.toString(),
                        password = password.text.toString(),
                        rights = rights.text.toString(),
                        downloadLinksJson = linksJson
                    )
                    isEnabled = false
                    saveController.save(
                        post = post,
                        labels = selectedLabels.toList(),
                        existing = existing,
                        onSuccess = { saved ->
                            upsertGame(saved, existing)
                            // Only clean up an old local cover after the replacement is committed.
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
        }

        dialog.show()
        styleAlertDialog(dialog)
        applyUnifiedSkin(box)
        box.alpha = 0f
        box.translationY = dp(14).toFloat()
        box.animate().alpha(1f).translationY(0f).setDuration(280L)
            .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(-1, -1)
            setGravity(Gravity.FILL)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }
}
