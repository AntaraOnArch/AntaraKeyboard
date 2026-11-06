package com.example.antarakeyboard.ui

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isNotEmpty
import com.example.antarakeyboard.R
import com.example.antarakeyboard.data.KeyboardPrefs
import com.example.antarakeyboard.data.PrefsManager
import com.example.antarakeyboard.extensions.dp
import com.example.antarakeyboard.extensions.themeColor

/**
 * Colors popup: section dropdown like Set layout / Bind long press. Every change is saved
 * immediately. [markDirty] gets the changed section's toast name and [flushSavedToast] shows
 * one "<Section> saved" toast (on section switch and close; the activity also calls it when
 * the app is left). [onDismiss] runs after the popup closes.
 */
class ColorsDialog(
    private val activity: Activity,
    private val markDirty: (section: String) -> Unit,
    private val flushSavedToast: () -> Unit,
    private val onDismiss: () -> Unit
) {

    // Section name shown in the dropdown → name used in the "<name> saved" toast
    private val colorSections = listOf(
        R.string.colors_section_space to R.string.colors_saved_space,
        R.string.colors_section_enter to R.string.colors_saved_enter,
        R.string.colors_section_side to R.string.colors_saved_side,
        R.string.colors_section_keys to R.string.colors_saved_keys,
        R.string.colors_section_background to R.string.colors_section_background,
        R.string.colors_section_theme_defaults to R.string.colors_section_theme_defaults
    )

    fun show() {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_colors)

        val spinnerSection = dialog.findViewById<Spinner>(R.id.spinnerColorsSection)
        val page = dialog.findViewById<LinearLayout>(R.id.colorsPageContainer)
        spinnerSection.adapter = ArrayAdapter(
            activity,
            android.R.layout.simple_spinner_item,
            colorSections.map { activity.getString(it.first) }
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        var currentSection = -1

        fun showSection(index: Int) {
            currentSection = index
            page.removeAllViews()
            val onChanged = { markDirty(activity.getString(colorSections[index].second)) }
            when (index) {
                0 -> buildSpaceColorsPage(page, onChanged)
                1 -> buildEnterColorsPage(page, onChanged)
                2 -> buildSideButtonsColorsPage(page, onChanged)
                3 -> buildKeysColorsPage(page, onChanged)
                4 -> buildBackgroundColorsPage(page, onChanged)
                5 -> buildThemeDefaultsPage(page, onChanged)
            }
        }

        spinnerSection.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == currentSection) return
                flushSavedToast()
                showSection(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        dialog.setOnDismissListener {
            flushSavedToast()
            onDismiss()
        }

        showSection(0)
        dialog.show()
        dialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.92f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    /** Button filled with [color] (label kept readable); tapping it opens the color picker. */
    private fun colorButton(label: String, color: Int, onPicked: (Int) -> Unit): Button {
        return Button(activity).apply {
            text = label
            isAllCaps = false
            setSwatch(this, color)
            setOnClickListener {
                activity.showAdvancedColorPicker(label, tag as? Int ?: color) { picked ->
                    setSwatch(this, picked)
                    onPicked(picked)
                }
            }
        }
    }

    private fun setSwatch(button: Button, color: Int) {
        button.tag = color
        button.setBackgroundColor(color)
        val opaque = androidx.core.graphics.ColorUtils.setAlphaComponent(color, 0xFF)
        val light = Color.alpha(color) < 0x80 ||
            androidx.core.graphics.ColorUtils.calculateLuminance(opaque) > 0.5
        button.setTextColor(if (light) Color.BLACK else Color.WHITE)
    }

    private fun LinearLayout.addSpaced(view: View) {
        addView(
            view,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { if (this@addSpaced.isNotEmpty()) topMargin = 8.dp(activity) }
        )
    }

    private fun buildSpaceColorsPage(page: LinearLayout, onChanged: () -> Unit) {
        var useTheme = KeyboardPrefs.getSpaceUseTheme(activity)
        var linked = KeyboardPrefs.isSpaceLinked(activity)
        // Start from what is drawn now, so switching off "use theme" keeps the look
        val (drawn1, drawn2) = KeyboardPrefs.resolveSpaceColors(activity, PrefsManager.isDarkMode(activity))
        var c1 = drawn1
        var c2 = drawn2

        fun save() {
            if (linked) c2 = c1
            KeyboardPrefs.setSpaceColors(activity, c1, c2, linked)
            onChanged()
        }

        lateinit var b2: Button
        val b1 = colorButton(activity.getString(R.string.color_space_1), c1) { picked ->
            c1 = picked
            if (linked) setSwatch(b2, picked)
            save()
        }
        b2 = colorButton(activity.getString(R.string.color_space_2), c2) { picked ->
            c2 = picked
            if (linked) {
                c1 = picked
                setSwatch(b1, picked)
            }
            save()
        }

        val cb = CheckBox(activity).apply {
            text = activity.getString(R.string.color_space_linked)
            isChecked = linked
            isEnabled = !useTheme
            setOnCheckedChangeListener { _, isChecked ->
                linked = isChecked
                if (linked) setSwatch(b2, c1)
                save()
            }
        }

        fun updateEnabled() {
            cb.isEnabled = !useTheme
            b1.isEnabled = !useTheme
            b2.isEnabled = !useTheme
        }

        val cbTheme = CheckBox(activity).apply {
            text = activity.getString(R.string.color_use_theme)
            isChecked = useTheme
            setOnCheckedChangeListener { _, isChecked ->
                useTheme = isChecked
                if (isChecked) KeyboardPrefs.setSpaceUseTheme(activity, true) else save()
                updateEnabled()
                onChanged()
            }
        }

        page.addSpaced(cbTheme)
        page.addSpaced(cb)
        page.addSpaced(b1)
        page.addSpaced(b2)
        updateEnabled()
    }

    private fun buildEnterColorsPage(page: LinearLayout, onChanged: () -> Unit) {
        var bg = KeyboardPrefs.getEnterBg(activity)
        var icon = KeyboardPrefs.getEnterIcon(activity)

        page.addSpaced(colorButton(activity.getString(R.string.color_background), bg) { picked ->
            bg = picked
            KeyboardPrefs.setEnterColors(activity, bg, icon)
            onChanged()
        })
        page.addSpaced(colorButton(activity.getString(R.string.color_icon), icon) { picked ->
            icon = picked
            KeyboardPrefs.setEnterColors(activity, bg, icon)
            onChanged()
        })
    }

    private fun buildSideButtonsColorsPage(page: LinearLayout, onChanged: () -> Unit) {
        var useThemeBg = KeyboardPrefs.getSideButtonsUseThemeBg(activity)
        // Theme look = no background + theme icon color; start from it so turning the theme off keeps the look
        var bg = if (useThemeBg) Color.TRANSPARENT else KeyboardPrefs.getSideButtonsBg(activity)
        var textColor = if (useThemeBg) {
            val isDark = PrefsManager.isDarkMode(activity)
            KeyboardPrefs.themeSideIconColor(
                activity, isDark, activity.themeColor(R.attr.edgeIconText, if (isDark) Color.WHITE else Color.BLACK)
            )
        } else {
            KeyboardPrefs.getSideButtonsTextColor(activity)
        }

        fun save() {
            KeyboardPrefs.setSideButtonsColors(activity, bg, textColor, useThemeBg)
            onChanged()
        }

        // Last visible background, brought back when "No background" is turned off
        var lastBg = if (Color.alpha(bg) == 0) DEFAULT_SIDE_BG else bg

        val bgBtn = colorButton(activity.getString(R.string.color_background), lastBg) { picked ->
            bg = picked; lastBg = picked; save()
        }
        val textBtn = colorButton(activity.getString(R.string.color_icon), textColor) { picked -> textColor = picked; save() }

        // Custom icon color with no background at all (only the icon on the keyboard background)
        val noBgCb = CheckBox(activity).apply {
            text = activity.getString(R.string.color_no_background)
            isChecked = Color.alpha(bg) == 0
            setOnCheckedChangeListener { _, isChecked ->
                bg = if (isChecked) Color.TRANSPARENT else lastBg
                bgBtn.isEnabled = !useThemeBg && !isChecked
                save()
            }
        }

        fun updateEnabled() {
            noBgCb.isEnabled = !useThemeBg
            bgBtn.isEnabled = !useThemeBg && !noBgCb.isChecked
            textBtn.isEnabled = !useThemeBg
        }
        updateEnabled()

        val cb = CheckBox(activity).apply {
            text = activity.getString(R.string.color_use_theme)
            isChecked = useThemeBg
            setOnCheckedChangeListener { _, isChecked ->
                useThemeBg = isChecked
                updateEnabled()
                save()
            }
        }

        page.addSpaced(cb)
        page.addSpaced(noBgCb)
        page.addSpaced(bgBtn)
        page.addSpaced(textBtn)
    }

    private fun buildKeysColorsPage(page: LinearLayout, onChanged: () -> Unit) {
        val themeColors = KeyboardPrefs.getThemeDefaultsForMode(activity, PrefsManager.isDarkMode(activity))
        var useTheme = KeyboardPrefs.getKeysUseTheme(activity)
        var allSame = KeyboardPrefs.getKeysAllSameColor(activity)
        // While the theme is used, start from the theme colors, so turning it off keeps the look
        var bg = if (useTheme) themeColors.keyFill else KeyboardPrefs.getKeysBg(activity)
        var textColor = if (useTheme) themeColors.keyText else KeyboardPrefs.getKeysTextColor(activity)

        fun save() {
            KeyboardPrefs.setKeysColors(activity, bg, textColor, allSame, useTheme = useTheme)
            onChanged()
        }

        // "All keys same color": one background + text color
        val allSameContainer = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        allSameContainer.addSpaced(colorButton(activity.getString(R.string.color_background), bg) { picked -> bg = picked; save() })
        allSameContainer.addSpaced(colorButton(activity.getString(R.string.color_text), textColor) { picked -> textColor = picked; save() })

        // Per-key colors: tap a key to edit it
        val individualContainer = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val grid = GridLayout(activity).apply { columnCount = 5 }
        val rowCount = KeyboardPrefs.getRowCount(activity)
        val layout = KeyboardPrefs.loadAlphabetLayoutForRowCount(activity, rowCount)
        val labels = layout.rows.asSequence().flatMap { it.keys }
            .map { it.label }
            .filter { it.isNotBlank() && it !in setOf(" ", "↵", "⇧", "⌫", "😊") }
            .map { if (it.length == 1 && it[0].isLetter()) it.lowercase() else it }
            .distinct()

        labels.forEach { keyLabel ->
            val btn = Button(activity).apply {
                text = keyLabel
                isAllCaps = false
                minWidth = 0
                minimumWidth = 0
            }
            fun refresh() {
                val colors = KeyboardPrefs.getKeyIndividualColors(activity, keyLabel)
                setSwatch(btn, colors?.first ?: themeColors.keyFill)
                btn.setTextColor(colors?.second ?: themeColors.keyText)
            }
            refresh()
            btn.setOnClickListener {
                showIndividualKeyColorPopup(keyLabel) {
                    refresh()
                    onChanged()
                }
            }
            grid.addView(btn, GridLayout.LayoutParams().apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(2.dp(activity), 2.dp(activity), 2.dp(activity), 2.dp(activity))
            })
        }
        individualContainer.addSpaced(grid)

        fun updateVisibility() {
            allSameContainer.visibility = if (!useTheme && allSame) View.VISIBLE else View.GONE
            individualContainer.visibility = if (!useTheme && !allSame) View.VISIBLE else View.GONE
        }

        val cbAllSame = CheckBox(activity).apply {
            text = activity.getString(R.string.color_all_keys_same)
            isChecked = allSame
            isEnabled = !useTheme
            setOnCheckedChangeListener { _, isChecked ->
                allSame = isChecked
                updateVisibility()
                save()
            }
        }

        val cbTheme = CheckBox(activity).apply {
            text = activity.getString(R.string.color_use_theme)
            isChecked = useTheme
            setOnCheckedChangeListener { _, isChecked ->
                useTheme = isChecked
                cbAllSame.isEnabled = !isChecked
                updateVisibility()
                save()
            }
        }

        page.addSpaced(cbTheme)
        page.addSpaced(cbAllSame)
        page.addSpaced(allSameContainer)
        page.addSpaced(individualContainer)
        updateVisibility()
    }

    /** Small popup (same style as the others) for one key's background + text color. Autosaves. */
    private fun showIndividualKeyColorPopup(keyLabel: String, onChanged: () -> Unit) {
        val themeColors = KeyboardPrefs.getThemeDefaultsForMode(activity, PrefsManager.isDarkMode(activity))
        val current = KeyboardPrefs.getKeyIndividualColors(activity, keyLabel)
        var bg = current?.first ?: themeColors.keyFill
        var textColor = current?.second ?: themeColors.keyText

        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(this), 16.dp(this), 16.dp(this), 16.dp(this))
            setBackgroundColor(activity.getColor(R.color.main_app_bg))
        }

        val preview = TextView(activity).apply {
            text = keyLabel
            textSize = 24f
            gravity = Gravity.CENTER
            setBackgroundColor(bg)
            setTextColor(textColor)
            minHeight = 64.dp(activity)
        }

        fun save() {
            KeyboardPrefs.setKeyIndividualColors(activity, keyLabel, bg, textColor)
            preview.setBackgroundColor(bg)
            preview.setTextColor(textColor)
            onChanged()
        }

        root.addSpaced(preview)
        root.addSpaced(colorButton(activity.getString(R.string.color_background), bg) { picked -> bg = picked; save() })
        root.addSpaced(colorButton(activity.getString(R.string.color_text), textColor) { picked -> textColor = picked; save() })
        root.addSpaced(Button(activity).apply {
            text = activity.getString(R.string.color_key_use_default)
            isAllCaps = false
            setOnClickListener {
                KeyboardPrefs.clearKeyIndividualColors(activity, keyLabel)
                onChanged()
                dialog.dismiss()
            }
        })

        dialog.setContentView(root)
        dialog.show()
        dialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.85f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun buildBackgroundColorsPage(page: LinearLayout, onChanged: () -> Unit) {
        val modes = listOf(
            KeyboardPrefs.BackgroundMode.THEME to activity.getString(R.string.bg_mode_theme),
            KeyboardPrefs.BackgroundMode.DARK to activity.getString(R.string.bg_mode_dark),
            KeyboardPrefs.BackgroundMode.LIGHT to activity.getString(R.string.bg_mode_light),
            KeyboardPrefs.BackgroundMode.CUSTOM to activity.getString(R.string.bg_mode_custom)
            // Transparent is chosen in the Theme dropdown
        )
        val currentMode = KeyboardPrefs.getBackgroundMode(activity)

        val customBtn = colorButton(activity.getString(R.string.bg_mode_custom), KeyboardPrefs.getBackgroundColor(activity)) { picked ->
            KeyboardPrefs.setBackgroundColor(activity, picked, useTheme = false)
            onChanged()
        }
        customBtn.isEnabled = currentMode == KeyboardPrefs.BackgroundMode.CUSTOM

        val group = android.widget.RadioGroup(activity).apply {
            orientation = android.widget.RadioGroup.VERTICAL
        }
        modes.forEach { (mode, label) ->
            group.addView(android.widget.RadioButton(activity).apply {
                id = View.generateViewId()
                text = label
                tag = mode
                isChecked = mode == currentMode
            })
        }
        group.setOnCheckedChangeListener { g, checkedId ->
            val mode = g.findViewById<View>(checkedId)?.tag as? KeyboardPrefs.BackgroundMode
                ?: return@setOnCheckedChangeListener
            if (mode == KeyboardPrefs.BackgroundMode.CUSTOM &&
                KeyboardPrefs.getBackgroundMode(activity) != KeyboardPrefs.BackgroundMode.CUSTOM
            ) {
                // Switching to Custom starts from the background shown now, not an old stored color
                val shown = KeyboardPrefs.resolveKeyboardBackground(activity, PrefsManager.isDarkMode(activity))
                KeyboardPrefs.setBackgroundColor(activity, shown, useTheme = false)
                setSwatch(customBtn, shown)
            } else {
                KeyboardPrefs.setBackgroundMode(activity, mode)
            }
            customBtn.isEnabled = mode == KeyboardPrefs.BackgroundMode.CUSTOM
            onChanged()
        }

        page.addSpaced(group)
        page.addSpaced(customBtn)
    }

    private fun buildThemeDefaultsPage(page: LinearLayout, onChanged: () -> Unit) {
        var lightKeyFill = KeyboardPrefs.getThemeLightKeyFill(activity)
        var lightKeyText = KeyboardPrefs.getThemeLightKeyText(activity)
        var lightSpaceFill = KeyboardPrefs.getThemeLightSpaceFill(activity)
        var lightKeyboardBg = KeyboardPrefs.getThemeLightKeyboardBg(activity)

        var darkKeyFill = KeyboardPrefs.getThemeDarkKeyFill(activity)
        var darkKeyText = KeyboardPrefs.getThemeDarkKeyText(activity)
        var darkSpaceFill = KeyboardPrefs.getThemeDarkSpaceFill(activity)
        var darkKeyboardBg = KeyboardPrefs.getThemeDarkKeyboardBg(activity)

        fun saveLight() {
            KeyboardPrefs.setThemeLightDefaults(activity, lightKeyFill, lightKeyText, lightSpaceFill, lightKeyboardBg)
            onChanged()
        }

        fun saveDark() {
            KeyboardPrefs.setThemeDarkDefaults(activity, darkKeyFill, darkKeyText, darkSpaceFill, darkKeyboardBg)
            onChanged()
        }

        fun header(text: String) = TextView(activity).apply {
            this.text = text
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        page.addSpaced(header(activity.getString(R.string.theme_defaults_light)))
        page.addSpaced(colorButton(activity.getString(R.string.color_key_fill), lightKeyFill) { lightKeyFill = it; saveLight() })
        page.addSpaced(colorButton(activity.getString(R.string.color_key_text), lightKeyText) { lightKeyText = it; saveLight() })
        page.addSpaced(colorButton(activity.getString(R.string.color_space_fill), lightSpaceFill) { lightSpaceFill = it; saveLight() })
        page.addSpaced(colorButton(activity.getString(R.string.color_background), lightKeyboardBg) { lightKeyboardBg = it; saveLight() })

        page.addSpaced(header(activity.getString(R.string.theme_defaults_dark)))
        page.addSpaced(colorButton(activity.getString(R.string.color_key_fill), darkKeyFill) { darkKeyFill = it; saveDark() })
        page.addSpaced(colorButton(activity.getString(R.string.color_key_text), darkKeyText) { darkKeyText = it; saveDark() })
        page.addSpaced(colorButton(activity.getString(R.string.color_space_fill), darkSpaceFill) { darkSpaceFill = it; saveDark() })
        page.addSpaced(colorButton(activity.getString(R.string.color_background), darkKeyboardBg) { darkKeyboardBg = it; saveDark() })

        page.addSpaced(Button(activity).apply {
            text = activity.getString(R.string.reset_factory)
            isAllCaps = false
            setOnClickListener {
                AlertDialog.Builder(activity)
                    .setTitle(R.string.reset_factory)
                    .setMessage(R.string.reset_factory_message)
                    .setNegativeButton(R.string.action_cancel, null)
                    .setPositiveButton(R.string.action_reset) { _, _ ->
                        KeyboardPrefs.resetThemeDefaultsToFactory(activity)
                        page.removeAllViews()
                        buildThemeDefaultsPage(page, onChanged)
                        onChanged()
                    }
                    .show()
            }
        })
    }

    private companion object {
        /** Side button background offered when "No background" is turned off and none was set. */
        const val DEFAULT_SIDE_BG = 0xFF3A3A3A.toInt()
    }
}
