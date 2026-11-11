package com.marcorx6.library.ui

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.util.WeakHashMap

/**
 * Context-bound appearance and formatting helpers extracted from MainActivity.
 *
 * Values that depend on the current Activity theme are supplied lazily so
 * existing appearance preferences and runtime theme changes keep their behavior.
 */
internal class AppearanceUiFactory(
    private val activity: Activity,
    private val dp: (Int) -> Int,
    private val isDarkMode: () -> Boolean,
    private val bg: () -> Int,
    private val panel: () -> Int,
    private val red: () -> Int,
    private val white: () -> Int,
    private val gray: () -> Int,
    private val accentForeground: () -> Int,
    private val selectedAccentId: () -> String
) {

    // Cached once: applyUnifiedSkin traverses large view trees and these exact
    // legacy tokens are checked for every text, icon, gradient and outline.
    private val lightLegacyAccentTokens = setOf(
        Color.rgb(190, 198, 210),
        Color.rgb(205, 211, 220),
        Color.rgb(205, 212, 222),
        Color.rgb(210, 216, 225),
        Color.rgb(220, 224, 231)
    )
    private val darkLegacyAccentTokens = setOf(
        Color.rgb(105, 113, 126),
        Color.rgb(104, 113, 126),
        Color.rgb(105, 116, 132),
        Color.rgb(75, 83, 96),
        Color.rgb(65, 72, 84),
        Color.rgb(65, 73, 86)
    )

    private val paletteAccentTokens = AccentPalette.options.map { it.color and 0x00FFFFFF }.toSet()
    private val paletteDarkAccentTokens = AccentPalette.options.map { option ->
        Color.rgb(
            (Color.red(option.color) * 0.72f).toInt(),
            (Color.green(option.color) * 0.72f).toInt(),
            (Color.blue(option.color) * 0.72f).toInt()
        ) and 0x00FFFFFF
    }.toSet()

    // Surfaces produced by AccentPalette.surface() are deliberately tinted and
    // therefore are not grayscale. Remember every supported source blend so an
    // already-open screen can move from its previous accent to the next one.
    private val accentSurfaceTokens: Map<Int, Pair<Int, Float>> = run {
        val bases = listOf(
            Color.rgb(8, 9, 12) to 0.20f,
            Color.rgb(245, 247, 250) to 0.10f,
            Color.rgb(25, 28, 34) to 0.30f,
            Color.rgb(224, 229, 237) to 0.20f,
            Color.rgb(35, 38, 45) to 0.28f,
            Color.rgb(15, 17, 22) to 0.22f,
            Color.WHITE to 0.16f,
            Color.rgb(68, 76, 91) to 0.24f,
            Color.rgb(30, 35, 45) to 0.18f,
            Color.rgb(12, 15, 21) to 0.12f,
            Color.rgb(250, 251, 253) to 0.10f,
            Color.rgb(220, 225, 232) to 0.14f,
            Color.rgb(185, 193, 204) to 0.16f
        )
        AccentPalette.options.flatMap { option ->
            bases.map { (base, amount) ->
                (AccentPalette.surface(base, option.color, amount, option.id) and 0x00FFFFFF) to (base to amount)
            }
        }.toMap()
    }

    // Keep the unthemed source values. Without these snapshots, the first palette
    // pass permanently turns a neutral surface into a saturated color; the next
    // pass then mistakes that previous accent for branded artwork and leaves it.
    // Weak keys let detached RecyclerView/dialog rows be collected normally.
    private val originalBackgrounds = WeakHashMap<View, android.graphics.drawable.Drawable.ConstantState>()
    private val originalTextColors = WeakHashMap<TextView, android.content.res.ColorStateList>()
    private val originalImageTints = WeakHashMap<ImageView, android.content.res.ColorStateList>()
    private val originalCompoundTints = WeakHashMap<TextView, android.content.res.ColorStateList>()

    private fun restoreOriginalStyle(view: View) {
        if (view !is EditText && view.background != null) {
            val saved = originalBackgrounds[view]
            if (saved == null) {
                view.background.constantState?.let { originalBackgrounds[view] = it }
            } else {
                view.background = saved.newDrawable(activity.resources).mutate()
            }
        }
        if (view is TextView) {
            val savedText = originalTextColors[view]
            if (savedText == null) originalTextColors[view] = view.textColors
            else view.setTextColor(savedText)
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                val tint = view.compoundDrawableTintList
                if (tint != null) {
                    val savedTint = originalCompoundTints[view]
                    if (savedTint == null) originalCompoundTints[view] = tint
                    else view.compoundDrawableTintList = savedTint
                }
            }
        }
        if (view is ImageView) {
            val tint = view.imageTintList
            if (tint != null) {
                val savedTint = originalImageTints[view]
                if (savedTint == null) originalImageTints[view] = tint
                else view.imageTintList = savedTint
            }
        }
    }

    private fun glassRipple(): android.graphics.drawable.Drawable =
        android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(
                Color.argb(if (isDarkMode()) 78 else 58, Color.red(red()), Color.green(red()), Color.blue(red()))
            ),
            null,
            android.graphics.drawable.ColorDrawable(Color.WHITE)
        )

    private fun installGlassInteraction(view: View) {
        if (!view.isClickable && !view.isLongClickable) return
        // Keep each screen's existing shape and color, but soften clickable
        // gradient surfaces so the whole app shares the translucent glass language.
        if (view !is EditText && view.background is GradientDrawable) {
            val base = view.background.constantState?.newDrawable()?.mutate() as? GradientDrawable
                ?: (view.background.mutate() as? GradientDrawable)
            base?.alpha = if (view.tag == "marco_primary_action") 224 else if (isDarkMode()) 198 else 210
            if (base != null) view.background = base
        }
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            val accentRipple = android.content.res.ColorStateList.valueOf(
                Color.argb(if (isDarkMode()) 78 else 58, Color.red(red()), Color.green(red()), Color.blue(red()))
            )
            val existingForeground = view.foreground
            if (existingForeground is android.graphics.drawable.RippleDrawable) {
                existingForeground.setColor(accentRipple)
            } else if (existingForeground == null) {
                view.foreground = glassRipple()
            }
        } else if (view.background != null && view.background !is android.graphics.drawable.RippleDrawable &&
            view.foreground == null) {
            val base = view.background.constantState?.newDrawable()?.mutate() ?: view.background.mutate()
            view.background = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(
                    Color.argb(if (isDarkMode()) 78 else 58, Color.red(red()), Color.green(red()), Color.blue(red()))
                ),
                base,
                android.graphics.drawable.ColorDrawable(Color.WHITE)
            )
        }
    }

    fun stylePrimaryButton(button: Button, primary: Boolean = false) {
        button.background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            if (primary) intArrayOf(
                Color.argb(238, Color.red(red()), Color.green(red()), Color.blue(red())),
                Color.argb(220, (Color.red(red()) * 0.72f).toInt(), (Color.green(red()) * 0.72f).toInt(), (Color.blue(red()) * 0.72f).toInt())
            )
            else if (isDarkMode()) intArrayOf(Color.argb(205, 34, 37, 44), Color.argb(190, 15, 17, 22))
            else intArrayOf(Color.argb(218, 255, 255, 255), Color.argb(205, 225, 228, 234))).apply {
            cornerRadius = dp(16).toFloat()
            setStroke(dp(1), if (primary) Color.argb(190, Color.red(red()), Color.green(red()), Color.blue(red()))
                else if (isDarkMode()) Color.argb(165, 130, 138, 151) else Color.argb(175, 160, 168, 181))
        }
        button.setTextColor(if (primary) accentForeground() else white())
        button.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        button.textSize = 13.5f
        button.isAllCaps = false
        button.letterSpacing = 0.01f
        button.minHeight = dp(48)
        button.minWidth = dp(48)
        button.setPadding(dp(16), dp(10), dp(16), dp(10))
        button.stateListAnimator = null
        installGlassInteraction(button)
    }

    /**
     * Recolors only neutral grayscale surfaces. Accent-colored controls, artwork,
     * and custom illustrations are intentionally left untouched.
     */
    /**
     * Legacy pages contain several historical shades of MARCO silver for primary
     * actions (including separate light/dark variants). Resolve those exact tokens
     * centrally so older screens inherit the selected accent without recoloring
     * ordinary neutral text, borders, or black/silver surfaces.
     */
    private fun legacyAccentReplacement(color: Int): Int? {
        val rgb = color and 0x00FFFFFF
        val previousSurface = accentSurfaceTokens[rgb]
        if (previousSurface != null) {
            val replacement = AccentPalette.surface(previousSurface.first, red(), previousSurface.second, selectedAccentId())
            return Color.argb(Color.alpha(color), Color.red(replacement), Color.green(replacement), Color.blue(replacement))
        }
        val isDarkAccent = rgb in darkLegacyAccentTokens || rgb in paletteDarkAccentTokens
        val isAccent = isDarkAccent || rgb in lightLegacyAccentTokens || rgb in paletteAccentTokens
        if (!isAccent) return null
        val accent = red()
        val factor = if (isDarkAccent) 0.72f else 1f
        return Color.argb(
            Color.alpha(color),
            (Color.red(accent) * factor).toInt().coerceIn(0, 255),
            (Color.green(accent) * factor).toInt().coerceIn(0, 255),
            (Color.blue(accent) * factor).toInt().coerceIn(0, 255)
        )
    }

    private fun themedNeutralColor(color: Int, background: Boolean): Int? {
        legacyAccentReplacement(color)?.let { return it }
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        // Keep photographs, game artwork and intentionally colored illustrations intact.
        // Neutral surfaces, however, must all inherit a visible tint from the selected
        // accent instead of only changing the few legacy silver tokens we knew in advance.
        if (hsv[1] > 0.32f) return null
        val value = hsv[2]
        val replacement = if (background) {
            val amount = if (isDarkMode()) {
                when {
                    value <= 0.10f -> 0.22f
                    value >= 0.88f -> 0.15f
                    else -> 0.20f
                }
            } else {
                when {
                    value <= 0.08f -> 0.13f
                    value >= 0.90f -> 0.10f
                    else -> 0.15f
                }
            }
            AccentPalette.surface(color, red(), amount, selectedAccentId())
        } else {
            if (isDarkMode()) {
                when {
                    value <= 0.55f -> white()
                    else -> return null
                }
            } else {
                when {
                    value >= 0.45f -> white()
                    else -> return null
                }
            }
        }
        return Color.argb(Color.alpha(color), Color.red(replacement),
            Color.green(replacement), Color.blue(replacement))
    }

    private fun rethemeBackground(drawable: android.graphics.drawable.Drawable?): Boolean {
        when (drawable) {
            is android.graphics.drawable.ColorDrawable -> {
                val accent = legacyAccentReplacement(drawable.color)
                if (accent != null) {
                    drawable.setColor(accent)
                    return true
                }
                themedNeutralColor(drawable.color, true)?.let(drawable::setColor)
            }
            is GradientDrawable -> {
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    // Resolve known legacy accent stops before neutral surface colors.
                    val stops = drawable.colors
                    if (stops != null) {
                        var containsAccent = false
                        val themed = stops.map { color ->
                            val accent = legacyAccentReplacement(color)
                            if (accent != null) {
                                containsAccent = true
                                accent
                            } else themedNeutralColor(color, true) ?: color
                        }.toIntArray()
                        if (!stops.contentEquals(themed)) drawable.colors = themed
                        return containsAccent
                    } else {
                        drawable.color?.defaultColor?.let { current ->
                            val accent = legacyAccentReplacement(current)
                            if (accent != null) {
                                drawable.setColor(accent)
                                return true
                            }
                            themedNeutralColor(current, true)?.let(drawable::setColor)
                        }
                    }
                } else if (android.os.Build.VERSION.SDK_INT >= 24) {
                    val current = drawable.color?.defaultColor
                    if (current != null) {
                        val accent = legacyAccentReplacement(current)
                        if (accent != null) {
                            drawable.setColor(accent)
                            return true
                        }
                        themedNeutralColor(current, true)?.let(drawable::setColor)
                    }
                }
            }
            is android.graphics.drawable.RippleDrawable -> {
                var changed = false
                for (index in 0 until drawable.numberOfLayers) {
                    if (rethemeBackground(drawable.getDrawable(index))) changed = true
                }
                return changed
            }
            is android.graphics.drawable.LayerDrawable -> {
                var changed = false
                for (index in 0 until drawable.numberOfLayers) {
                    if (rethemeBackground(drawable.getDrawable(index))) changed = true
                }
                return changed
            }
            is android.graphics.drawable.InsetDrawable -> {
                return rethemeBackground(drawable.drawable)
            }
            is android.graphics.drawable.ScaleDrawable -> {
                return rethemeBackground(drawable.drawable)
            }
            is android.graphics.drawable.ClipDrawable -> {
                return rethemeBackground(drawable.drawable)
            }
        }
        return false
    }

    fun applyUnifiedSkin(view: View) {
        // Palette swatches and the shared top bar are authored from explicit palette
        // values. Do not reinterpret their saturated colors as legacy silver tokens.
        // The top bar owns its children too, so skipping its subtree preserves its
        // already-correct icon tints and accent surfaces during a global skin pass.
        if (view.tag == "marco_palette_swatch" || view.tag == "marco_primary_topbar") return
        // Always start from the original drawable/tint/text values so a second,
        // third or later color selection replaces the previous palette cleanly.
        restoreOriginalStyle(view)
        installGlassInteraction(view)
        // Re-theme old hard-coded black/white text and neutral card fills on every
        // newly rendered page. This catches legacy screens that do not use palette
        // callbacks yet, while preserving colored labels and branded controls.
        if (view is ImageView) {
            // Recolor neutral navigation/profile glyphs so light pages never retain
            // low-contrast silver icons. Untinted artwork and colored icons are preserved.
            view.imageTintList?.defaultColor?.let { current ->
                themedNeutralColor(current, false)?.let { replacement ->
                    view.imageTintList = android.content.res.ColorStateList.valueOf(replacement)
                }
            }
        }
        if (view is TextView && android.os.Build.VERSION.SDK_INT >= 23) {
            // Search, publish and navigation actions frequently tint compound icons
            // through TextView rather than using a standalone ImageView.
            view.compoundDrawableTintList?.defaultColor?.let { current ->
                themedNeutralColor(current, false)?.let { replacement ->
                    view.compoundDrawableTintList = android.content.res.ColorStateList.valueOf(replacement)
                }
            }
        }
        if (view !is Button && view !is EditText && view !is CheckBox && view !is RadioButton) {
            if (view is TextView) {
                themedNeutralColor(view.currentTextColor, false)?.let(view::setTextColor)
            }
            val hasAccentSurface = rethemeBackground(view.background)
            // Text placed on a legacy silver primary button must use a foreground
            // chosen for the currently selected accent, not the old fixed dark ink.
            if (hasAccentSurface && view is TextView && view !is EditText) {
                view.setTextColor(accentForeground())
            }
        }
        if (view is TextView && view !is EditText) {
            val compoundIcons = view.compoundDrawablesRelative
            if (compoundIcons.any { it != null }) {
                // Use one predictable icon footprint and spacing for compound
                // icon/label controls so glyphs cannot collide with their text.
                compoundIcons.filterNotNull().forEach { icon ->
                    icon.setBounds(0, 0, dp(18), dp(18))
                }
                view.compoundDrawablePadding = maxOf(view.compoundDrawablePadding, dp(6))
            }
            if (view.isClickable || view.isLongClickable) {
                view.minimumHeight = maxOf(view.minimumHeight, dp(44))
            }
        }
        // Every screen opened through MainActivity receives the same neutral
        // black/silver base when its root has no explicit surface of its own.
        if (view.parent === activity.findViewById<ViewGroup>(android.R.id.content) &&
            view.background == null
        ) {
            view.setBackgroundColor(bg())
        }
        when (view) {
            is EditText -> styleInput(view)
            // CompoundButton subclasses Button, so handle them first to avoid
            // applying the generic button background to checkboxes and radio buttons.
            is CheckBox -> {
                view.setTextColor(white())
                view.buttonTintList = android.content.res.ColorStateList.valueOf(red())
                view.minHeight = dp(48)
            }
            is RadioButton -> {
                view.setTextColor(white())
                view.buttonTintList = android.content.res.ColorStateList.valueOf(red())
                view.minHeight = dp(48)
            }
            is Button -> {
                val legacyPrimary = rethemeBackground(view.background)
                stylePrimaryButton(view, view.tag == "marco_primary_action" || legacyPrimary)
            }
            is android.widget.RatingBar -> {
                view.progressTintList = android.content.res.ColorStateList.valueOf(red())
                view.secondaryProgressTintList = android.content.res.ColorStateList.valueOf(
                    Color.argb(95, Color.red(red()), Color.green(red()), Color.blue(red()))
                )
            }
            is SeekBar -> {
                view.progressTintList = android.content.res.ColorStateList.valueOf(red())
                view.thumbTintList = android.content.res.ColorStateList.valueOf(red())
            }
            is android.widget.ProgressBar -> {
                view.progressTintList = android.content.res.ColorStateList.valueOf(red())
                view.indeterminateTintList = android.content.res.ColorStateList.valueOf(red())
            }
            is Spinner -> {
                view.backgroundTintList = android.content.res.ColorStateList.valueOf(gray())
                view.minimumHeight = dp(48)
            }
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) applyUnifiedSkin(view.getChildAt(i))
        }
    }

    fun styleAlertDialog(dialog: AlertDialog) {
        dialog.window?.setBackgroundDrawable(GradientDrawable(GradientDrawable.Orientation.TL_BR,
            if (isDarkMode()) intArrayOf(
                AccentPalette.surface(Color.rgb(25, 28, 34), red(), 0.22f, selectedAccentId()),
                AccentPalette.surface(Color.rgb(8, 9, 12), red(), 0.16f, selectedAccentId())
            ) else intArrayOf(
                AccentPalette.surface(Color.WHITE, red(), 0.10f, selectedAccentId()),
                AccentPalette.surface(Color.rgb(242, 244, 247), red(), 0.14f, selectedAccentId())
            )).apply {
            cornerRadius = dp(24).toFloat()
            setStroke(dp(1), AccentPalette.surface(
                if (isDarkMode()) Color.rgb(130, 138, 151) else Color.rgb(180, 188, 200),
                red(),
                0.48f,
                selectedAccentId()
            ))
        })
        dialog.window?.let { window ->
            // Dialogs are separate Windows: apply the same immersive/IME policy
            // instead of clearing fullscreen flags and relying on a one-time hide.
            WindowCompat.setDecorFitsSystemWindows(window, true)
            window.navigationBarColor = bg()
            SystemBarPolicy.apply(window, isDarkMode())
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.attributes = window.attributes.apply { dimAmount = 0.66f }
            KeyboardLayoutController.install(window, window.decorView)
        }
        dialog.findViewById<TextView>(activity.resources.getIdentifier("alertTitle", "id", "android"))?.apply {
            setTextColor(white())
            textSize = 18f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        dialog.findViewById<View>(android.R.id.message)?.let {
            (it as? TextView)?.apply {
                setTextColor(gray())
                textSize = 14f
                setLineSpacing(dp(3).toFloat(), 1f)
            }
        }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.let { stylePrimaryButton(it, true) }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.let { stylePrimaryButton(it) }
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.let { stylePrimaryButton(it) }
        dialog.listView?.let { list ->
            list.setBackgroundColor(if (isDarkMode()) Color.rgb(17, 19, 24) else Color.WHITE)
            list.divider = android.graphics.drawable.ColorDrawable(if (isDarkMode()) Color.rgb(57, 61, 70) else Color.rgb(211, 215, 222))
            list.dividerHeight = dp(1)
            list.post {
                for (index in 0 until list.childCount) {
                    val row = list.getChildAt(index)
                    if (row is ViewGroup) {
                        for (childIndex in 0 until row.childCount) {
                            (row.getChildAt(childIndex) as? TextView)?.setTextColor(white())
                        }
                    } else {
                        (row as? TextView)?.setTextColor(white())
                    }
                }
            }
        }
        dialog.window?.decorView?.let { applyUnifiedSkin(it) }
    }

    fun themedSpinner(items: List<String>, selectedIndex: Int = 0): Spinner {
        val spinner = Spinner(activity)
        val adapter = object : ArrayAdapter<String>(activity, android.R.layout.simple_spinner_item, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                return (super.getView(position, convertView, parent) as TextView).apply {
                    setTextColor(white())
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                }
            }
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                return (super.getDropDownView(position, convertView, parent) as TextView).apply {
                    setTextColor(white())
                    setBackgroundColor(panel())
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                }
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.setSelection(selectedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)))
        spinner.backgroundTintList = android.content.res.ColorStateList.valueOf(gray())
        return spinner
    }

    private fun inputBackground(): android.graphics.drawable.StateListDrawable =
        android.graphics.drawable.StateListDrawable().apply {
            val fill = if (isDarkMode()) {
                intArrayOf(
                    AccentPalette.surface(Color.rgb(35, 38, 45), red(), 0.24f, selectedAccentId()),
                    AccentPalette.surface(Color.rgb(15, 17, 22), red(), 0.19f, selectedAccentId())
                )
            } else {
                intArrayOf(
                    AccentPalette.surface(Color.WHITE, red(), 0.13f, selectedAccentId()),
                    AccentPalette.surface(Color.rgb(225, 228, 234), red(), 0.17f, selectedAccentId())
                )
            }
            val normalStroke = AccentPalette.surface(
                if (isDarkMode()) Color.rgb(130, 138, 151) else Color.rgb(160, 168, 181),
                red(),
                0.48f,
                selectedAccentId()
            )
            val focusedStroke = red()
            addState(intArrayOf(android.R.attr.state_focused), GradientDrawable(
                GradientDrawable.Orientation.TL_BR, fill
            ).apply {
                cornerRadius = dp(16).toFloat()
                setStroke(dp(2), focusedStroke)
            })
            addState(intArrayOf(), GradientDrawable(
                GradientDrawable.Orientation.TL_BR, fill
            ).apply {
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), normalStroke)
            })
        }

    private fun styleInput(input: EditText) {
        input.background = inputBackground()
        input.setPadding(dp(14), dp(12), dp(14), dp(12))
        input.minHeight = dp(54)
        input.textSize = 14f
        input.setTextColor(white())
        input.setHintTextColor(gray())
        input.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }

    fun makeInput(hintText: String): EditText =
        EditText(activity).apply {
            hint = hintText
            styleInput(this)
        }
}
