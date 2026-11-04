package com.example.antarakeyboard.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isEmpty
import com.example.antarakeyboard.R
import com.example.antarakeyboard.data.EdgePos
import com.example.antarakeyboard.data.KeyboardPrefs
import com.example.antarakeyboard.data.LastCustomColors
import com.example.antarakeyboard.data.PrefsManager
import com.example.antarakeyboard.data.SavedLayoutStorage
import com.example.antarakeyboard.extensions.dp
import com.example.antarakeyboard.model.EdgeActionType
import com.example.antarakeyboard.model.EdgeSlot
import com.example.antarakeyboard.model.KeyShape
import com.example.antarakeyboard.model.KeyboardConfig
import com.example.antarakeyboard.service.EdgeOverlayManager
import com.example.antarakeyboard.service.KeyScale
import com.google.gson.reflect.TypeToken

/**
 * Saved layouts (Theme → Custom, Set layout's saved-layouts dropdown): the list, the
 * Preview / Restore / Rename / Delete menu and the preview with editable colors.
 */
class SavedLayoutsDialog(private val activity: Activity, private val host: Host) {

    interface Host {
        fun rowsLabel(rows: Int): String
        fun shapeLabel(shapeName: String): String
        /** Restores [savedLayout] ([withColors]: also its colors) and refreshes the main screen. */
        fun restoreSavedLayout(savedLayout: SavedLayoutStorage.SavedLayout, withColors: Boolean)
        /** Colors of a saved layout were applied (makes Custom the active theme). */
        fun onColorsApplied()
    }

    /** What a restore brings back; an entry without colors only offers [LAYOUT]. */
    private enum class RestorePart(val label: Int, val message: Int) {
        ALL(R.string.restore_layout_and_colors, R.string.restore_layout_message),
        LAYOUT(R.string.restore_layout_only, R.string.restore_layout_only_message),
        COLORS(R.string.colors_only, R.string.restore_colors_only_message)
    }

    /**
     * Preview / Restore / Rename / Delete menu for one saved layout. [onRestored] gets true
     * when colors were applied (Custom becomes the active theme), false for the layout only.
     */
    fun showSavedLayoutActions(
        savedLayout: SavedLayoutStorage.SavedLayout,
        onListChanged: () -> Unit,
        onRestored: (colorsApplied: Boolean) -> Unit,
        onClosed: () -> Unit = {}
    ) {
        AlertDialog.Builder(activity)
            .setTitle(savedLayout.name)
            .setItems(arrayOf(
                R.string.action_preview, R.string.action_restore, R.string.action_rename, R.string.action_delete
            ).map { activity.getString(it) }.toTypedArray()) { _, which ->
                when (which) {
                    0 -> showSavedLayoutPreview(savedLayout, onApplied = onRestored)
                    1 -> {
                        // Layout and colors can be restored separately
                        if (savedLayout.includesColors) {
                            val parts = RestorePart.entries
                            AlertDialog.Builder(activity)
                                .setTitle(R.string.restore_layout_title)
                                .setItems(parts.map { activity.getString(it.label) }.toTypedArray()) { _, part ->
                                    confirmRestore(savedLayout, parts[part], onRestored)
                                }
                                .setNegativeButton(R.string.action_cancel, null)
                                .show()
                        } else {
                            confirmRestore(savedLayout, RestorePart.LAYOUT, onRestored)
                        }
                    }
                    2 -> {
                        val input = android.widget.EditText(activity).apply { setText(savedLayout.name); selectAll() }
                        AlertDialog.Builder(activity).setTitle(R.string.action_rename).setView(input)
                            .setPositiveButton(R.string.action_save) { _, _ ->
                                SavedLayoutStorage.renameLayout(activity, savedLayout.id, input.text.toString().trim())
                                onListChanged()
                            }.setNegativeButton(R.string.action_cancel, null).show()
                    }
                    3 -> {
                        AlertDialog.Builder(activity).setTitle(R.string.delete_confirm)
                            .setPositiveButton(R.string.action_delete) { _, _ ->
                                SavedLayoutStorage.deleteLayout(activity, savedLayout.id)
                                onListChanged()
                            }.setNegativeButton(R.string.action_cancel, null).show()
                    }
                }
                onClosed()
            }
            .setOnCancelListener { onClosed() }
            .show()
    }

    private fun confirmRestore(
        savedLayout: SavedLayoutStorage.SavedLayout,
        part: RestorePart,
        onRestored: (colorsApplied: Boolean) -> Unit
    ) {
        AlertDialog.Builder(activity)
            .setTitle(savedLayout.name)
            .setMessage(part.message)
            .setPositiveButton(R.string.action_restore) { _, _ ->
                when (part) {
                    RestorePart.ALL -> host.restoreSavedLayout(savedLayout, withColors = true)
                    RestorePart.LAYOUT -> host.restoreSavedLayout(savedLayout, withColors = false)
                    RestorePart.COLORS -> applyColors(savedLayout)
                }
                onRestored(part != RestorePart.LAYOUT)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun applyColors(savedLayout: SavedLayoutStorage.SavedLayout) {
        SavedLayoutStorage.applyColors(activity, savedLayout)
        host.onColorsApplied()
        Toast.makeText(activity, R.string.colors_applied, Toast.LENGTH_SHORT).show()
    }

    /**
     * Popup opened from Theme → Custom: lists all saved layouts (and the last custom colors).
     * [onDone] gets true if something was applied, false if the popup was just closed.
     */
    // Text built from data (names, key labels, color values), not from UI strings
    @SuppressLint("SetTextI18n")
    fun showSavedLayoutsPopup(onDone: (applied: Boolean) -> Unit) {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        var applied = false

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(this), 16.dp(this), 16.dp(this), 16.dp(this))
            setBackgroundColor(activity.getColor(R.color.main_app_bg))
        }
        root.addView(TextView(activity).apply {
            text = activity.getString(R.string.saved_layouts_title)
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 12.dp(activity))
        })

        val scroll = ScrollView(activity)
        val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ))

        fun addEntry(title: String, subtitle: String, onClick: () -> Unit) {
            list.addView(Button(activity).apply {
                text = "$title\n$subtitle"
                isAllCaps = false
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setOnClickListener { onClick() }
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 4.dp(activity) })
        }

        fun rebuildList() {
            list.removeAllViews()

            if (LastCustomColors.exists(activity)) {
                addEntry(activity.getString(R.string.last_custom_colors), activity.getString(R.string.colors_only)) {
                    LastCustomColors.apply(activity)
                    Toast.makeText(activity, R.string.custom_colors_applied, Toast.LENGTH_SHORT).show()
                    applied = true
                    dialog.dismiss()
                }
            }

            val savedLayouts = SavedLayoutStorage.getSavedLayouts(activity)
            savedLayouts.forEach { saved ->
                addEntry(
                    saved.name,
                    activity.getString(R.string.saved_layout_subtitle, host.rowsLabel(saved.rowCount), host.shapeLabel(saved.keyShape), SavedLayoutStorage.formatTimestamp(saved.timestamp))
                ) {
                    showSavedLayoutActions(
                        savedLayout = saved,
                        onListChanged = { rebuildList() },
                        onRestored = { colorsApplied ->
                            // Layout only: the theme stays as it was
                            applied = colorsApplied
                            dialog.dismiss()
                        }
                    )
                }
            }

            if (list.isEmpty()) {
                list.addView(TextView(activity).apply {
                    text = activity.getString(R.string.no_saved_layouts)
                    alpha = 0.75f
                    setPadding(0, 8.dp(activity), 0, 8.dp(activity))
                })
            }
        }
        rebuildList()

        dialog.setContentView(root)
        dialog.setOnDismissListener { onDone(applied) }
        dialog.show()
        dialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.92f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    /**
     * [onApplied] runs after a restore or applied colors (true = colors were applied, which
     * makes Custom the active theme). An entry without colors is drawn in the current colors
     * and only offers restoring its layout.
     */
    private fun showSavedLayoutPreview(
        savedLayout: SavedLayoutStorage.SavedLayout,
        onApplied: (colorsApplied: Boolean) -> Unit
    ) {
        val gson = com.google.gson.Gson()

        val hasColors = savedLayout.includesColors

        // Mutable color values; a layout without colors is shown in the colors drawn now
        val isDark = PrefsManager.isDarkMode(activity)
        val (drawnKeyFill, drawnKeyText) = KeyboardPrefs.resolveKeyColors(activity, isDark)
        var currentKeyFill = if (hasColors) savedLayout.keyFill else drawnKeyFill
        var currentKeyText = if (hasColors) savedLayout.keyText else drawnKeyText
        var currentSpaceBg = if (hasColors) savedLayout.space1Bg else KeyboardPrefs.resolveSpaceColors(activity, isDark).first
        var currentEnterBg = if (hasColors) savedLayout.enterBg else KeyboardPrefs.getEnterBg(activity)
        val currentEnterIcon = if (hasColors) savedLayout.enterIcon else KeyboardPrefs.getEnterIcon(activity)
        var currentBgColor = if (hasColors) savedLayout.backgroundColor else KeyboardPrefs.resolveKeyboardBackground(activity, isDark)

        // Parse the saved layout JSON
        val alphabetLayout: KeyboardConfig = gson.fromJson(
            savedLayout.alphabetLayoutJson,
            KeyboardConfig::class.java
        )

        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(this), 16.dp(this), 16.dp(this), 16.dp(this))
        }

        // Title
        val titleText = TextView(activity).apply {
            text = savedLayout.name
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 8.dp(this))
        }
        root.addView(titleText)

        // Transparent background can't be seen in the preview, so say it explicitly
        if (hasColors && !savedLayout.backgroundUseTheme && Color.alpha(savedLayout.backgroundColor) == 0) {
            root.addView(TextView(activity).apply {
                text = activity.getString(R.string.transparent_background)
                textSize = 13f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, 0, 0, 8.dp(activity))
            })
        }

        // Info section
        val infoText = TextView(activity).apply {
            text = activity.getString(R.string.preview_info, host.shapeLabel(savedLayout.keyShape), savedLayout.rowCount, SavedLayoutStorage.formatTimestamp(savedLayout.timestamp))
            textSize = 12f
            setPadding(0, 0, 0, 12.dp(this))
        }
        root.addView(infoText)

        // Side button positions (absent in layouts saved before they existed)
        if (savedLayout.sideOffsetLeftDp != null) {
            fun offset(dp: Int?) = SideOffsetsDialog.formatOffset(dp ?: 0)
            root.addView(TextView(activity).apply {
                text = activity.getString(
                    R.string.preview_side_offsets,
                    offset(savedLayout.sideOffsetLeftDp),
                    offset(savedLayout.sideOffsetRightDp),
                    offset(savedLayout.sideOffsetLeftLandscapeDp),
                    offset(savedLayout.sideOffsetRightLandscapeDp)
                )
                textSize = 12f
                setPadding(0, 0, 0, 12.dp(activity))
            })
        }

        // Colors section (a layout without colors says so instead)
        val colorsLabel = TextView(activity).apply {
            text = activity.getString(if (hasColors) R.string.preview_colors_label else R.string.saved_without_colors)
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            if (!hasColors) setPadding(0, 0, 0, 12.dp(activity))
        }
        root.addView(colorsLabel)

        val colorsRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8.dp(this), 0, 12.dp(this))
        }

        // Preview container reference for updates
        lateinit var previewContainer: LinearLayout

        // Side buttons of the saved layout: colors as saved (or the current ones for a layout
        // without colors); "use theme" draws them without fill in the key text color
        val edgeSlots: List<EdgeSlot> = runCatching<List<EdgeSlot>> {
            gson.fromJson(savedLayout.edgeSlotsJson, object : TypeToken<List<EdgeSlot>>() {}.type)
        }.getOrNull().orEmpty()
        val sideBindings = EdgeOverlayManager.edgeBindings(savedLayout.rowCount, edgeSlots)
        val sideKeyRows = EdgeOverlayManager.sideButtonKeyRows(savedLayout.rowCount)
        val sideUseTheme = if (hasColors) savedLayout.sideUseTheme else KeyboardPrefs.getSideButtonsUseThemeBg(activity)
        val sideBg = if (hasColors) savedLayout.sideBg else KeyboardPrefs.getSideButtonsBg(activity)
        val sideText = if (hasColors) savedLayout.sideText else KeyboardPrefs.getSideButtonsTextColor(activity)

        // Preview: real keys with the saved shape, row count, key height, row spacing, colors,
        // side buttons and their position sliders (portrait values)
        fun rebuildPreview() {
            previewContainer.removeAllViews()
            previewContainer.setBackgroundColor(currentBgColor)

            val savedShape = runCatching { KeyShape.valueOf(savedLayout.keyShape) }.getOrDefault(KeyShape.HEX)
            // Same per-layout rules as the keyboard (3-row hexagons are tall, 3-row triangles taller)
            val shape = if (savedLayout.rowCount == 3 && savedShape == KeyShape.HEX) KeyShape.HEX_TALL else savedShape
            val stretch = KeyScale.clamp(savedLayout.keyScale ?: KeyScale.DEFAULT) *
                (if (savedLayout.rowCount == 3 && savedShape == KeyShape.TRIANGLE) KeyScale.THREE_ROW_TRIANGLE_DEFAULT else 1f)
            val rowSpacing = KeyScale.clampRowSpacing(savedLayout.rowSpacingDp ?: 0).dp(activity)

            val rows = alphabetLayout.rows.map { row -> row.keys.filter { it.label.isNotEmpty() } }
            val maxKeys = rows.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1
            val isHex = shape == KeyShape.HEX || shape == KeyShape.HEX_TALL
            // Like the keyboard: only 3- and 4-row hexagon layouts shift every second row;
            // 5 rows interlock by their different key counts
            val honeycombFactor = when {
                !isHex -> 0f
                savedLayout.rowCount == 3 -> 0.42f
                savedLayout.rowCount == 4 -> 0.5f
                else -> 0f
            }
            // Room for a side button on both ends of every row (keys stay centered)
            val sideFactor = 0.72f
            val previewPadding = 16.dp(activity)
            val availW = (activity.resources.displayMetrics.widthPixels * 0.95f).toInt() -
                32.dp(activity) - previewPadding
            val keyW = (availW / (maxKeys + honeycombFactor + 2 * sideFactor)).toInt()
            val sideW = (keyW * sideFactor).toInt()
            val shiftPx = (keyW * honeycombFactor).toInt()
            val baseH = if (shape == KeyShape.HEX_TALL) (keyW * 1.6f).toInt() else keyW
            // Honeycomb rows interlock by the hexagon tips (a quarter of the drawn height)
            val rowOverlap = when {
                isHex -> (baseH * 0.96f * stretch * 0.25f).toInt() - 1.dp(activity)
                shape == KeyShape.TRIANGLE -> (baseH * 0.15f * stretch).toInt()
                else -> -(2.dp(activity))
            }

            fun sideButton(rowIndex: Int, side: EdgePos.Side): View {
                val visualIndex = sideKeyRows.indexOf(rowIndex)
                val slot = sideBindings.firstOrNull { it.visualIndex == visualIndex && it.side == side }?.slot
                val offsetDp = when (side) {
                    EdgePos.Side.LEFT -> savedLayout.sideOffsetLeftDp
                    EdgePos.Side.RIGHT -> savedLayout.sideOffsetRightDp
                } ?: 0
                return TextView(activity).apply {
                    if (slot != null) {
                        text = EdgeOverlayManager.sideButtonLabel(slot)
                        textSize = if (slot.type == EdgeActionType.SHIFT) 18f else 12f
                        gravity = Gravity.CENTER
                        includeFontPadding = false
                        setTextColor(if (sideUseTheme) currentKeyText else sideText)
                        if (!sideUseTheme) setBackgroundColor(sideBg)
                        translationX = offsetDp.dp(activity).toFloat()
                    }
                    layoutParams = LinearLayout.LayoutParams(sideW, baseH)
                }
            }

            rows.forEachIndexed { rowIndex, keys ->
                val shifted = honeycombFactor > 0f && rowIndex % 2 == 1
                val keysLayout = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    clipChildren = false
                    clipToPadding = false
                    // Honeycomb: every second row half a key to the right of the others
                    if (honeycombFactor > 0f) {
                        if (shifted) setPadding(shiftPx, 0, 0, 0) else setPadding(0, 0, shiftPx, 0)
                    }
                }

                keys.forEachIndexed { keyIndex, key ->
                    val (fill, textColor) = when (key.label) {
                        " " -> currentSpaceBg to currentKeyText
                        "↵" -> currentEnterBg to currentEnterIcon
                        else -> currentKeyFill to currentKeyText
                    }
                    keysLayout.addView(KeyView(activity).apply {
                        text = if (key.label == " ") "" else key.label
                        isAllCaps = false
                        gravity = Gravity.CENTER
                        includeFontPadding = false
                        textSize = 11f
                        this.shape = shape
                        forceSquare = shape != KeyShape.TRIANGLE && shape != KeyShape.HEX_TALL
                        heightStretch = stretch
                        triangleFlipped = (rowIndex + keyIndex) % 2 == 1
                        customBgColor = fill
                        setTextColor(textColor)
                    }, LinearLayout.LayoutParams(keyW, baseH))
                }

                val rowLayout = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    clipChildren = false
                    clipToPadding = false
                    addView(sideButton(rowIndex, EdgePos.Side.LEFT))
                    addView(keysLayout, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(sideButton(rowIndex, EdgePos.Side.RIGHT))
                }

                previewContainer.addView(rowLayout, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { if (rowIndex > 0) topMargin = -rowOverlap + rowSpacing })
            }
        }

        // Clickable color swatches
        fun addColorSwatch(color: Int, label: String, onColorChanged: (Int) -> Unit): View {
            val container = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                isClickable = true
                isFocusable = true
            }
            val swatch = View(activity).apply {
                setBackgroundColor(color)
                layoutParams = LinearLayout.LayoutParams(40.dp(this), 40.dp(this))
            }
            val text = TextView(activity).apply {
                this.text = label
                textSize = 11f
                gravity = Gravity.CENTER
            }
            container.addView(swatch)
            container.addView(text)

            var swatchColor = color
            container.setOnClickListener {
                activity.showAdvancedColorPicker(label, swatchColor) { picked ->
                    swatchColor = picked
                    swatch.setBackgroundColor(picked)
                    onColorChanged(picked)
                    rebuildPreview()
                }
            }

            colorsRow.addView(container)
            return swatch
        }

        addColorSwatch(currentKeyFill, activity.getString(R.string.swatch_keys)) { currentKeyFill = it }
        addColorSwatch(currentKeyText, activity.getString(R.string.swatch_text)) { currentKeyText = it }
        addColorSwatch(currentSpaceBg, activity.getString(R.string.swatch_space)) { currentSpaceBg = it }
        addColorSwatch(currentEnterBg, activity.getString(R.string.swatch_enter)) { currentEnterBg = it }
        addColorSwatch(currentBgColor, activity.getString(R.string.swatch_bg)) { currentBgColor = it }

        if (hasColors) root.addView(colorsRow)

        // Layout preview section
        val layoutLabel = TextView(activity).apply {
            text = activity.getString(R.string.preview_layout_label)
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 8.dp(this))
        }
        root.addView(layoutLabel)

        // Build preview container
        previewContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(currentBgColor)
            setPadding(8.dp(this), 8.dp(this), 8.dp(this), 8.dp(this))
            clipChildren = false
            clipToPadding = false
        }
        rebuildPreview()
        root.addView(previewContainer)

        // Buttons
        val buttonsRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            // Same height for all buttons, even when one label takes two lines
            isBaselineAligned = false
            gravity = Gravity.END
            setPadding(0, 16.dp(this), 0, 0)
        }

        // Saved layout with the colors edited in this preview
        fun editedLayout() = savedLayout.copy(
            keyFill = currentKeyFill,
            keyText = currentKeyText,
            space1Bg = currentSpaceBg,
            space2Bg = currentSpaceBg,
            enterBg = currentEnterBg,
            enterIcon = currentEnterIcon,
            backgroundColor = currentBgColor,
            // A color picked here is a concrete color, not "use theme"
            backgroundUseTheme = savedLayout.backgroundUseTheme && currentBgColor == savedLayout.backgroundColor
        )

        // Equal widths; a long label wraps onto two whole lines instead of breaking a word
        fun buttonParams() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            .apply { marginStart = 4.dp(activity) }

        val applyColorsBtn = Button(activity).apply {
            text = activity.getString(R.string.apply_colors)
            isAllCaps = false
            setOnClickListener {
                // Keep the edits in the saved entry, then apply its colors to the keyboard
                SavedLayoutStorage.updateLayoutColors(
                    activity,
                    savedLayout.id,
                    currentKeyFill,
                    currentKeyText,
                    currentSpaceBg,
                    currentEnterBg,
                    currentEnterIcon,
                    currentBgColor
                )
                applyColors(editedLayout())
                dialog.dismiss()
                onApplied(true)
            }
        }

        val restoreBtn = Button(activity).apply {
            text = activity.getString(R.string.restore_layout_button)
            isAllCaps = false
            setOnClickListener {
                host.restoreSavedLayout(editedLayout(), withColors = hasColors)
                dialog.dismiss()
                onApplied(hasColors)
            }
        }

        val closeBtn = Button(activity).apply {
            text = activity.getString(R.string.action_close)
            isAllCaps = false
            setOnClickListener { dialog.dismiss() }
        }

        if (hasColors) buttonsRow.addView(applyColorsBtn, buttonParams())
        buttonsRow.addView(restoreBtn, buttonParams())
        buttonsRow.addView(closeBtn, buttonParams())
        root.addView(buttonsRow)

        val scrollView = ScrollView(activity).apply {
            addView(root)
        }

        dialog.setContentView(scrollView)
        dialog.show()
        dialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.95f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}
