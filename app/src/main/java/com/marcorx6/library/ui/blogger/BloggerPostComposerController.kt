package com.marcorx6.library.ui

import android.app.AlertDialog
import android.view.Gravity
import android.view.View
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
    private val isDarkMode: () -> Boolean,
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
    private val setContentView: (View) -> Unit,
    private val onBack: () -> Unit,
    private val translate: (String) -> String
) {
    private data class LinkFields(val label: EditText, val url: EditText, val row: LinearLayout)

    fun show(existing: Game? = null) {
        if (!isAdminSession()) {
            Toast.makeText(activity, translate("يجب تسجيل الدخول بصلاحية المسؤول للنشر."), Toast.LENGTH_SHORT).show()
            return
        }
        if (existing != null) setPendingCoverLocalPath(existing.coverLocalPath)

        // Follow the app's saved appearance preference, not the device's system theme.
        // The app supports an in-app dark/light choice that can differ from uiMode.
        val isNight = isDarkMode()
        val studioTextColor = white()
        fun studioSurface(base: Int, amount: Float) =
            AccentPalette.surface(base, red(), amount)
        val studioBorder = studioSurface(
            if (isNight) android.graphics.Color.rgb(130, 138, 151)
            else android.graphics.Color.rgb(160, 168, 181),
            0.48f
        )
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                if (isNight) intArrayOf(
                    studioSurface(android.graphics.Color.rgb(8, 9, 12), 0.20f),
                    studioSurface(android.graphics.Color.rgb(17, 19, 24), 0.24f)
                ) else intArrayOf(
                    studioSurface(android.graphics.Color.WHITE, 0.10f),
                    studioSurface(android.graphics.Color.rgb(242, 244, 247), 0.16f)
                )
            ).apply {
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), studioBorder)
            }
        }
        fun section(text: String) = makeText(text, 13f, studioTextColor, true).apply {
            setPadding(0, dp(12), 0, dp(7))
        }
        fun addField(target: LinearLayout, view: View) {
            target.addView(view, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
        }
        fun makeAction(text: String, primary: Boolean = false) = Button(activity).apply {
            this.text = translate(text)
            isAllCaps = false
            if (primary) tag = "marco_primary_action"
            minHeight = dp(48)
            setTextColor(if (primary) accentForeground() else studioTextColor)
            background = if (primary) {
                val accent = red()
                val darkerAccent = android.graphics.Color.rgb(
                    (android.graphics.Color.red(accent) * 0.72f).toInt(),
                    (android.graphics.Color.green(accent) * 0.72f).toInt(),
                    (android.graphics.Color.blue(accent) * 0.72f).toInt()
                )
                android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(accent, darkerAccent)
                ).apply { cornerRadius = dp(13).toFloat() }
            }
            else rounded(panel(), 13)
        }

        val intro = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                if (isNight) intArrayOf(
                    studioSurface(android.graphics.Color.rgb(65, 70, 80), 0.25f),
                    studioSurface(android.graphics.Color.rgb(28, 31, 38), 0.20f)
                ) else intArrayOf(
                    studioSurface(android.graphics.Color.rgb(239, 241, 245), 0.14f),
                    studioSurface(android.graphics.Color.rgb(220, 224, 231), 0.18f)
                )
            ).apply {
                cornerRadius = dp(17).toFloat()
                setStroke(dp(1), studioBorder)
            }
        }
        intro.addView(makeText(
            translate(if (existing == null) "استوديو النشر • MARCO" else "استوديو التحرير • MARCO"),
            18f, studioTextColor, true
        ))
        intro.addView(makeText(
            translate("أدخل معلومات اللعبة وروابطها، ثم راجع شكل المقال قبل النشر."),
            12f, gray(), false
        ).apply { setPadding(0, dp(5), 0, 0) })
        box.addView(intro, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })

        val form = formFieldsFactory.create()
        val title = form.title
        val coverUrl = form.coverUrl
        val size = form.packageSize
        val version = form.version
        val format = form.fileFormat
        val language = form.language
        val password = form.password
        val rights = form.rights
        val coverPreview = form.coverPreview.apply {
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                if (isNight) intArrayOf(
                    studioSurface(android.graphics.Color.rgb(39, 42, 50), 0.22f),
                    studioSurface(android.graphics.Color.rgb(17, 19, 24), 0.18f)
                ) else intArrayOf(
                    studioSurface(android.graphics.Color.rgb(239, 241, 245), 0.13f),
                    studioSurface(android.graphics.Color.rgb(220, 224, 231), 0.17f)
                )
            ).apply {
                cornerRadius = dp(15).toFloat()
                setStroke(dp(1), studioBorder)
            }
        }
        setCoverFields(coverUrl, coverPreview)

        if (existing != null) {
            title.setText(existing.name)
            coverUrl.setText(existing.coverUrl)
            size.setText(existing.packageSize)
            version.setText(existing.version)
            format.setText(existing.fileFormat.ifBlank { "PKG" })
            language.setText(existing.language.ifBlank { "الإنجليزية" })
            password.setText(existing.password)
            rights.setText(existing.rights)
        } else {
            format.setText("PKG")
            language.setText("الإنجليزية")
            if (pendingCoverPublicUrl().isNotBlank()) coverUrl.setText(pendingCoverPublicUrl())
        }

        val labelsUi = labelsController.create(existing?.platform, existing == null)
        val selectedLabels = labelsUi.selectedLabels
        box.addView(section("01 • " + translate("صورة الغلاف")))
        box.addView(coverPreview, LinearLayout.LayoutParams(-1, dp(178)).apply {
            bottomMargin = dp(9)
        })
        loadCover(coverPreview, pendingCoverLocalPath(), existing?.coverUrl ?: "")
        addField(box, makeAction("اختيار صورة الغلاف من الهاتف", primary = true).apply {
            contentDescription = translate("اختيار صورة غلاف المقال من الهاتف")
            setOnClickListener { chooseCover() }
        })
        addField(box, coverUrl)

        box.addView(section("02 • " + translate("العنوان وأقسام المقال")))
        addField(box, title)
        addField(box, labelsUi.button)

        box.addView(section("03 • " + translate("معلومات اللعبة")))
        addField(box, size)
        addField(box, version)
        addField(box, format)
        addField(box, language)
        addField(box, password)
        addField(box, rights)

        box.addView(section("04 • " + translate("روابط التحميل المباشرة")))
        val linksContainer = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val linkRows = mutableListOf<LinkFields>()

        fun addLinkRow(initialLabel: String = "", initialUrl: String = "") {
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                    if (isNight) intArrayOf(
                        studioSurface(android.graphics.Color.rgb(35, 38, 45), 0.22f),
                        studioSurface(android.graphics.Color.rgb(15, 17, 22), 0.18f)
                    ) else intArrayOf(
                        studioSurface(android.graphics.Color.WHITE, 0.12f),
                        studioSurface(android.graphics.Color.rgb(230, 234, 240), 0.17f)
                    )
                ).apply {
                    cornerRadius = dp(14).toFloat()
                    setStroke(dp(1), studioBorder)
                }
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
            row.addView(makeText(translate("رابط تحميل"), 12f, studioTextColor, true))
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


        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            setPadding(0, dp(3), 0, dp(8))
            addView(box)
        }
        val baseTop = dp(10)
        val baseBottom = dp(12)
        val page = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), baseTop, dp(14), baseBottom)
            setBackgroundColor(studioSurface(bg(), if (isNight) 0.08f else 0.06f))
        }
        val toolbar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(10))
        }
        val back = ImageView(activity).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            contentDescription = translate("الرجوع من استوديو النشر")
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(panel(), 14)
            setOnClickListener { onBack() }
        }
        toolbar.addView(back, LinearLayout.LayoutParams(dp(48), dp(48)))
        toolbar.addView(makeText(translate(if (existing == null) "استوديو النشر" else "تحرير المقال"), 19f, studioTextColor, true),
            LinearLayout.LayoutParams(0, -2, 1f))
        page.addView(toolbar, LinearLayout.LayoutParams(-1, -2))
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val actions = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, 0)
        }
        val closeButton = makeAction("إغلاق").apply {
            setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_menu_revert, 0, 0, 0)
            setOnClickListener { onBack() }
        }
        val previewButton = makeAction("معاينة").apply {
            setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_menu_view, 0, 0, 0)
        }
        val publishButton = makeAction(if (existing == null) "نشر المقال" else "حفظ التعديلات", primary = true).apply {
            setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_menu_save, 0, 0, 0)
        }
        actions.addView(closeButton, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(6) })
        actions.addView(previewButton, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(6) })
        actions.addView(publishButton, LinearLayout.LayoutParams(0, dp(52), 1.25f))
        page.addView(actions, LinearLayout.LayoutParams(-1, -2))

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

        previewButton.setOnClickListener {
                    val linksJson = collectLinks() ?: return@setOnClickListener
                    val firstUrl = try { JSONArray(linksJson).optJSONObject(0)?.optString("url").orEmpty() } catch (_: Exception) { "" }
                    if (firstUrl.isBlank()) {
                        Toast.makeText(activity, translate("أضف رابط تحميل واحداً على الأقل قبل المعاينة"), Toast.LENGTH_LONG).show()
                        return@setOnClickListener
                    }
                    val web = previewFactory.create(
                        title = title.text.toString(),
                        labels = selectedLabels.joinToString("، "),
                        genre = "",
                        releaseYear = "",
                        body = "",
                        coverUrl = coverUrl.text.toString(),
                        relatedLink = firstUrl,
                        packageSize = size.text.toString(),
                        version = version.text.toString(),
                        fileFormat = format.text.toString(),
                        language = language.text.toString(),
                        password = password.text.toString(),
                        rights = rights.text.toString(),
                        downloadLinksJson = linksJson
                    ).apply {
                        contentDescription = translate("معاينة كاملة لمقال Blogger قبل النشر")
                        layoutParams = LinearLayout.LayoutParams(-1, dp(520))
                    }
                    val previewDialog = AlertDialog.Builder(activity)
                        .setTitle(translate("معاينة قبل النشر"))
                        .setView(web)
                        .setPositiveButton(translate("إغلاق"), null)
                        .create()
                    previewDialog.show()
                    styleAlertDialog(previewDialog)
                    previewDialog.getButton(AlertDialog.BUTTON_POSITIVE).minHeight = dp(48)
                }
        publishButton.setOnClickListener {
                    val postTitle = title.text.toString().trim()
                    val coverLink = coverUrl.text.toString().trim()
                    val linksJson = collectLinks() ?: return@setOnClickListener
                    val firstUrl = try { JSONArray(linksJson).optJSONObject(0)?.optString("url").orEmpty() } catch (_: Exception) { "" }
                    if (firstUrl.isBlank()) {
                        Toast.makeText(activity, translate("أضف رابط تحميل واحداً على الأقل قبل النشر"), Toast.LENGTH_LONG).show()
                        return@setOnClickListener
                    }
                    val pendingLocalPath = pendingCoverLocalPath()
                    val validationFailure = BloggerPostInputValidator.validate(
                        coverUploadInProgress = coverUploadInProgress(),
                        pendingCoverLocalPath = pendingLocalPath,
                        selectedLabels = selectedLabels,
                        title = postTitle,
                        coverUrl = coverLink,
                        relatedLink = firstUrl,
                        content = ""
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
                        description = "",
                        coverLink = coverLink,
                        relatedLink = firstUrl,
                        genre = "",
                        releaseYear = "",
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
                    publishButton.isEnabled = false
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
                            Toast.makeText(activity, translate("تم نشر المقال على Blogger"), Toast.LENGTH_LONG).show()
                            onBack()
                        },
                        onError = { message ->
                            publishButton.isEnabled = true
                            Toast.makeText(activity, translate(message), Toast.LENGTH_LONG).show()
                        }
                    )
        }

        var imeWasVisible = false
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(page) { view, insets ->
            val safeTop = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.statusBars() or
                    androidx.core.view.WindowInsetsCompat.Type.displayCutout()
            ).top
            val navigationBottom = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.navigationBars()
            ).bottom
            val gestureBottom = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.mandatorySystemGestures()
            ).bottom
            // The IME inset is essential on edge-to-edge Android versions where
            // adjustResize alone does not move a custom full-screen root above the keyboard.
            val imeBottom = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.ime()
            ).bottom
            val imeVisible = insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())
            val safeBottom = maxOf(navigationBottom, gestureBottom, imeBottom)

            view.setPadding(dp(14), baseTop + safeTop, dp(14), baseBottom + safeBottom)

            // Keep the editor viewport as large as possible while typing. The
            // fixed toolbar/actions are restored as soon as the keyboard closes.
            if (imeVisible != imeWasVisible) {
                imeWasVisible = imeVisible
                toolbar.visibility = if (imeVisible) View.GONE else View.VISIBLE
                actions.visibility = if (imeVisible) View.GONE else View.VISIBLE
                if (imeVisible) {
                    view.post {
                        val focusedInput = view.findFocus() as? EditText
                        if (focusedInput != null && focusedInput.isShown && focusedInput.hasFocus()) {
                            focusedInput.requestRectangleOnScreen(
                                android.graphics.Rect(0, 0, focusedInput.width, focusedInput.height),
                                true
                            )
                        }
                    }
                }
            }
            insets
        }
        setContentView(page)
        androidx.core.view.ViewCompat.requestApplyInsets(page)
        applyUnifiedSkin(page)
        // MainActivity.showRootScreen owns the single entrance transition for this page.
    }
}
