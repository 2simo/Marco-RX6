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
    private val accentForeground: () -> Int
) {

    private fun glassRipple(): android.graphics.drawable.Drawable =
        android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(
                if (isDarkMode()) Color.argb(78, 190, 198, 210) else Color.argb(58, 145, 153, 165)
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
        if (android.os.Build.VERSION.SDK_INT >= 23 && view.foreground == null) {
            view.foreground = glassRipple()
        } else if (view.background != null && view.background !is android.graphics.drawable.RippleDrawable &&
            view.foreground == null) {
            val base = view.background.constantState?.newDrawable()?.mutate() ?: view.background.mutate()
            view.background = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(
                    if (isDarkMode()) Color.argb(78, 190, 198, 210) else Color.argb(58, 145, 153, 165)
                ),
                base,
                android.graphics.drawable.ColorDrawable(Color.WHITE)
            )
        }
    }

    fun stylePrimaryButton(button: Button, primary: Boolean = false) {
        button.background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            if (primary) intArrayOf(Color.argb(238, 220, 224, 231), Color.argb(220, 150, 160, 174))
            else if (isDarkMode()) intArrayOf(Color.argb(205, 34, 37, 44), Color.argb(190, 15, 17, 22))
            else intArrayOf(Color.argb(218, 255, 255, 255), Color.argb(205, 225, 228, 234))).apply {
            cornerRadius = dp(16).toFloat()
            setStroke(dp(1), if (primary) Color.argb(190, 150, 160, 174)
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
    private fun themedNeutralColor(color: Int, background: Boolean): Int? {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        // Include blue-tinted charcoal/silver neutrals used by legacy account and message cards,
        // while leaving genuinely colored accents and artwork untouched.
        if (hsv[1] > 0.32f) return null
        val value = hsv[2]
        val replacement = if (background) {
            if (isDarkMode()) {
                when {
                    value >= 0.88f -> panel()
                    value <= 0.10f -> bg()
                    value <= 0.25f -> panel()
                    else -> return null
                }
            } else {
                when {
                    value <= 0.07f -> bg()
                    value <= 0.90f -> panel()
                    value >= 0.97f -> Color.WHITE
                    else -> return null
                }
            }
        } else {
            if (isDarkMode()) {
                // Replace legacy dark-gray labels too, not only near-black text.
                // This is important for views created before the current palette.
                when {
                    value <= 0.55f -> white()
                    else -> return null
                }
            } else {
                // Silver/white labels from old dark-only screens become readable
                // charcoal labels on the light surface.
                when {
                    value >= 0.45f -> white()
                    else -> return null
                }
            }
        }
        return Color.argb(Color.alpha(color), Color.red(replacement),
            Color.green(replacement), Color.blue(replacement))
    }

    private fun rethemeBackground(drawable: android.graphics.drawable.Drawable?) {
        when (drawable) {
            is android.graphics.drawable.ColorDrawable -> {
                themedNeutralColor(drawable.color, true)?.let(drawable::setColor)
            }
            is GradientDrawable -> {
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    // Modern Android exposes gradient stops; retint neutral legacy
                    // black/white gradients as well as single-colour card fills.
                    val stops = drawable.colors
                    if (stops != null) {
                        val themed = stops.map { color ->
                            themedNeutralColor(color, true) ?: color
                        }.toIntArray()
                        if (!stops.contentEquals(themed)) drawable.colors = themed
                    } else {
                        // Most account/message cards use a solid GradientDrawable,
                        // whose colors array is null even on current Android.
                        drawable.color?.defaultColor?.let { current ->
                            themedNeutralColor(current, true)?.let(drawable::setColor)
                        }
                    }
                } else if (android.os.Build.VERSION.SDK_INT >= 24) {
                    val current = drawable.color?.defaultColor
                    if (current != null) themedNeutralColor(current, true)?.let(drawable::setColor)
                }
            }
            is android.graphics.drawable.RippleDrawable -> {
                if (drawable.numberOfLayers > 0) rethemeBackground(drawable.getDrawable(0))
            }
        }
    }

    fun applyUnifiedSkin(view: View) {
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
        if (view !is Button && view !is EditText && view !is CheckBox && view !is RadioButton) {
            if (view is TextView) {
                themedNeutralColor(view.currentTextColor, false)?.let(view::setTextColor)
            }
            rethemeBackground(view.background)
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
            is Button -> stylePrimaryButton(view, view.tag == "marco_primary_action")
            is SeekBar -> {
                view.progressTintList = android.content.res.ColorStateList.valueOf(red())
                view.thumbTintList = android.content.res.ColorStateList.valueOf(red())
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
            if (isDarkMode()) intArrayOf(Color.rgb(25, 28, 34), Color.rgb(8, 9, 12))
            else intArrayOf(Color.WHITE, Color.rgb(242, 244, 247))).apply {
            cornerRadius = dp(24).toFloat()
            setStroke(dp(1), if (isDarkMode()) Color.rgb(130, 138, 151) else Color.rgb(211, 215, 222))
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
                intArrayOf(Color.rgb(35, 38, 45), Color.rgb(15, 17, 22))
            } else {
                intArrayOf(Color.WHITE, Color.rgb(225, 228, 234))
            }
            val normalStroke = if (isDarkMode()) Color.rgb(130, 138, 151) else Color.rgb(160, 168, 181)
            val focusedStroke = Color.rgb(190, 198, 210)
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
