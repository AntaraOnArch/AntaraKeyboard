package com.example.antarakeyboard.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.ClipData
import android.content.ClipDescription
import android.view.DragEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.example.antarakeyboard.R
import com.example.antarakeyboard.SpecialChars
import com.example.antarakeyboard.data.ClassicLayouts
import com.example.antarakeyboard.data.EdgeSlotsStorage
import com.example.antarakeyboard.data.EmojiPickerStorage
import com.example.antarakeyboard.data.GlobalLongPressStorage
import com.example.antarakeyboard.data.KeyboardPrefs
import com.example.antarakeyboard.data.LayoutPresets
import com.example.antarakeyboard.extensions.dp
import com.example.antarakeyboard.model.KeyboardConfig

/**
 * Set layout popup: Keys (letters and numbers on one page) / Side buttons / Horizontal / Emoji
 * pages. Every page autosaves; the activity keeps the "<Section> saved" toast state behind [Host].
 */
class LayoutEditorDialog(private val activity: Activity, private val host: Host) {

    interface Host {
        fun markDirty(section: String)
        fun flushSavedToast()
    }

    companion object {
        // Sections of the dropdown, in its order (Help speed links open one directly)
        const val SECTION_KEYS = 0
        const val SECTION_SIDE = 1
        const val SECTION_HORIZONTAL = 2
        const val SECTION_EMOJI = 3
    }

    // Text built from data (names, key labels, color values), not from UI strings
    @SuppressLint("SetTextI18n")
    fun show(initialSection: Int = SECTION_KEYS) {
        val dialog = Dialog(activity)
        dialog.setContentView(R.layout.dialog_layout_editor)

        // Section dropdown
        val spinnerSection = dialog.findViewById<Spinner>(R.id.spinnerSection)
        val sectionOptions = listOf(
            R.string.layout_section_keys,
            R.string.layout_section_side,
            R.string.layout_section_horizontal,
            R.string.layout_section_emoji
        ).map { activity.getString(it) }
        // Classic layouts have no landscape island; the one with the number row has no side buttons
        val rowCount = KeyboardPrefs.getLayoutId(activity)
        val sections = listOf(SECTION_KEYS, SECTION_SIDE, SECTION_HORIZONTAL, SECTION_EMOJI).filter {
            when (it) {
                SECTION_SIDE -> EdgeSlotsStorage.slotCountForRowCount(rowCount) > 0
                SECTION_HORIZONTAL -> !ClassicLayouts.isClassic(rowCount)
                else -> true
            }
        }
        val sectionAdapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item, sections.map { sectionOptions[it] }).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerSection.adapter = sectionAdapter

        // Pages
        val pageLayout = dialog.findViewById<ScrollView>(R.id.pageLayout)
        val pageSideButtons = dialog.findViewById<LinearLayout>(R.id.pageSideButtons)
        val pageHorizontalCenter = dialog.findViewById<LinearLayout>(R.id.pageHorizontalCenter)
        val pageEmojiPicker = dialog.findViewById<ScrollView>(R.id.pageEmojiPicker)
        val emojiPickerContainer = dialog.findViewById<LinearLayout>(R.id.emojiPickerContainer)

        // Containers
        val layoutEditorContainer = dialog.findViewById<FrameLayout>(R.id.layoutEditorContainer)
        val numericEditorContainer = dialog.findViewById<FrameLayout>(R.id.numericEditorContainer)
        val horizontalEditorContainer = dialog.findViewById<FrameLayout>(R.id.horizontalEditorContainer)

        var currentPage = 0

        fun markDirty() {
            host.markDirty(sectionOptions[currentPage])
        }

        // Layout binders
        val layoutBinder = LayoutEditorBinder(
            context = activity,
            initial = KeyboardPrefs.loadAlphabetLayoutWithGlobalBinds(activity, rowCount),
            onSaved = { updated: KeyboardConfig ->
                KeyboardPrefs.saveAlphabetLayoutWithGlobalBinds(activity, rowCount, updated)
                markDirty()
            },
            showHeader = false
        )

        val numericLocked = setOf("⇧", "⌫", "↵", "ABC", "abc", " ", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9")
        lateinit var numericBinder: LayoutEditorBinder
        numericBinder = LayoutEditorBinder(
            context = activity,
            initial = KeyboardPrefs.loadNumericLayoutWithGlobalBinds(activity, rowCount),
            onSaved = { updated: KeyboardConfig ->
                KeyboardPrefs.saveNumericLayoutWithGlobalBinds(activity, rowCount, updated)
                markDirty()
            },
            lockedLabels = numericLocked,
            onEmptyKeyClick = { key ->
                showSpecialCharPicker { picked ->
                    key.label = picked
                    key.longPressBindings.remove("__USER_EMPTY__")
                    // The symbol brings its own binds (shared by all row counts); otherwise the
                    // save would store it as "no binds" and wipe them
                    key.longPressBindings.addAll(
                        GlobalLongPressStorage.loadNumericBinds(activity).getOrElse(picked) { emptyList() }
                    )
                    numericBinder.bindInto(numericEditorContainer)
                    numericBinder.saveExternally()
                }
            },
            allowClearKeys = true,
            showHeader = false
        )

        lateinit var horizontalBinder: LayoutEditorBinder
        horizontalBinder = LayoutEditorBinder(
            context = activity,
            initial = KeyboardPrefs.loadHorizontalCenterLayoutForRowCount(activity, rowCount),
            onSaved = { updated: KeyboardConfig ->
                KeyboardPrefs.saveHorizontalCenterLayoutForRowCount(activity, rowCount, updated)
                markDirty()
            },
            lockedLabels = emptySet(),
            onEmptyKeyClick = { key ->
                showSpecialCharPicker { picked ->
                    key.label = picked
                    key.longPressBindings.remove("__USER_EMPTY__")
                    horizontalBinder.bindInto(horizontalEditorContainer)
                    horizontalBinder.saveExternally()
                }
            },
            allowClearKeys = true,
            showHeader = false
        )

        layoutBinder.bindInto(layoutEditorContainer)
        setupLayoutPresets(dialog, rowCount, layoutBinder)
        numericBinder.bindInto(numericEditorContainer)
        horizontalBinder.bindInto(horizontalEditorContainer)

        SideButtonsPage(activity).setup(pageSideButtons) { markDirty() }

        // Emoji picker settings
        val emojiButtonOrder = EmojiPickerStorage.getButtonOrder(activity).toMutableList()
        var emojiTabsPosition = EmojiPickerStorage.getTabsPosition(activity)
        var emojiButtonsSide = EmojiPickerStorage.getButtonsSide(activity)

        fun setupEmojiPickerPage() {
            emojiPickerContainer.removeAllViews()

            // Tabs position
            val tabsLabel = TextView(activity).apply {
                text = activity.getString(R.string.emoji_tabs_position)
                textSize = 16f
                setPadding(0, 0, 0, 8.dp(this))
            }
            emojiPickerContainer.addView(tabsLabel)

            val tabsSpinner = Spinner(activity)
            val tabsOptions = EmojiPickerStorage.TabsPosition.entries.map { activity.getString(it.labelRes) }
            tabsSpinner.adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item, tabsOptions).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            tabsSpinner.setSelection(EmojiPickerStorage.TabsPosition.entries.indexOf(emojiTabsPosition))
            tabsSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val picked = EmojiPickerStorage.TabsPosition.entries[position]
                    if (picked == emojiTabsPosition) return
                    emojiTabsPosition = picked
                    EmojiPickerStorage.setTabsPosition(activity, emojiTabsPosition)
                    markDirty()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
            emojiPickerContainer.addView(tabsSpinner, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16.dp(activity) })

            // Buttons side
            val sideLabel = TextView(activity).apply {
                text = activity.getString(R.string.emoji_buttons_side)
                textSize = 16f
                setPadding(0, 0, 0, 8.dp(this))
            }
            emojiPickerContainer.addView(sideLabel)

            val sideSpinner = Spinner(activity)
            val sideOptions = EmojiPickerStorage.ButtonsSide.entries.map { activity.getString(it.labelRes) }
            sideSpinner.adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item, sideOptions).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            sideSpinner.setSelection(EmojiPickerStorage.ButtonsSide.entries.indexOf(emojiButtonsSide))
            sideSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val picked = EmojiPickerStorage.ButtonsSide.entries[position]
                    if (picked == emojiButtonsSide) return
                    emojiButtonsSide = picked
                    EmojiPickerStorage.setButtonsSide(activity, emojiButtonsSide)
                    markDirty()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
            emojiPickerContainer.addView(sideSpinner, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16.dp(activity) })

            // Button order
            val orderLabel = TextView(activity).apply {
                text = activity.getString(R.string.emoji_button_order)
                textSize = 16f
                setPadding(0, 0, 0, 8.dp(this))
            }
            emojiPickerContainer.addView(orderLabel)

            val orderContainer = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
            }
            emojiPickerContainer.addView(orderContainer)

            fun rebuildOrder() {
                orderContainer.removeAllViews()

                emojiButtonOrder.forEachIndexed { index, action ->
                    val btn = Button(activity).apply {
                        text = "${index + 1}. ${EmojiPickerStorage.getButtonLabel(action)} ${activity.getString(EmojiPickerStorage.getButtonDisplayName(action))}"
                        isAllCaps = false
                        tag = index

                        setOnLongClickListener { v ->
                            val data = ClipData.newPlainText("fromIndex", index.toString())
                            val shadow = View.DragShadowBuilder(v)
                            v.startDragAndDrop(data, shadow, null, 0)
                            true
                        }

                        setOnDragListener { v, e ->
                            when (e.action) {
                                DragEvent.ACTION_DRAG_STARTED -> {
                                    e.clipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true
                                }

                                DragEvent.ACTION_DRAG_ENTERED -> {
                                    v.alpha = 0.65f
                                    true
                                }

                                DragEvent.ACTION_DRAG_EXITED -> {
                                    v.alpha = 1f
                                    true
                                }

                                DragEvent.ACTION_DROP -> {
                                    v.alpha = 1f

                                    val from = e.clipData?.getItemAt(0)?.text?.toString()?.toIntOrNull()
                                        ?: return@setOnDragListener true
                                    val to = (v.tag as? Int) ?: return@setOnDragListener true
                                    if (from == to) return@setOnDragListener true

                                    // Move dragged button to the drop position
                                    val moved = emojiButtonOrder.removeAt(from)
                                    emojiButtonOrder.add(to, moved)

                                    // Autosave
                                    EmojiPickerStorage.saveButtonOrder(activity, emojiButtonOrder)
                                    markDirty()

                                    // Rebuild after the drag finishes dispatching
                                    v.post { rebuildOrder() }
                                    true
                                }

                                DragEvent.ACTION_DRAG_ENDED -> {
                                    v.alpha = 1f
                                    true
                                }

                                else -> true
                            }
                        }
                    }
                    orderContainer.addView(btn, LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = 4.dp(activity) })
                }
            }

            rebuildOrder()
        }

        // Show page function
        fun showPage(page: Int) {
            currentPage = page
            pageLayout.visibility = if (page == 0) View.VISIBLE else View.GONE
            pageSideButtons.visibility = if (page == 1) View.VISIBLE else View.GONE
            pageHorizontalCenter.visibility = if (page == 2) View.VISIBLE else View.GONE
            pageEmojiPicker.visibility = if (page == 3) View.VISIBLE else View.GONE

            if (page == 3) setupEmojiPickerPage()
        }

        val firstSection = initialSection.takeIf { it in sections } ?: SECTION_KEYS
        spinnerSection.setSelection(sections.indexOf(firstSection), false)
        spinnerSection.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                // The first callback reports the section already shown
                val section = sections[position]
                if (section == currentPage) return
                host.flushSavedToast()
                showPage(section)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        dialog.setOnDismissListener {
            host.flushSavedToast()
        }

        dialog.show()
        dialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.94f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        showPage(firstSection)
    }

    /**
     * "Preset" dropdown above the letters: "Custom layout" + every [LayoutPresets] layout. Picking a
     * preset relabels the default layout of this row count; "Custom layout" brings back the last
     * hand-made arrangement. Manual swaps switch the dropdown to "Custom layout".
     */
    private fun setupLayoutPresets(dialog: Dialog, rowCount: Int, binder: LayoutEditorBinder) {
        val spinner = dialog.findViewById<Spinner>(R.id.spinnerLayoutPreset)
        val defaultLayout = KeyboardPrefs.defaultAlphabetLayoutForRowCount(rowCount)
        val options = listOf(activity.getString(R.string.layout_preset_custom)) + LayoutPresets.ALL.map { it.name }
        spinner.adapter = ArrayAdapter(activity, R.layout.main_spinner_item, options).apply {
            setDropDownViewResource(R.layout.main_spinner_dropdown_item)
        }

        fun detectedPosition(): Int =
            LayoutPresets.detect(binder.currentLayout(), defaultLayout)?.let { LayoutPresets.ALL.indexOf(it) + 1 } ?: 0

        // Last hand-made arrangement, restored by "Custom layout"
        var lastCustom: KeyboardConfig? = if (detectedPosition() == 0) copyLayout(binder.currentLayout()) else null
        var shown = detectedPosition()
        spinner.setSelection(shown, false)

        binder.onLayoutChanged = {
            val pos = detectedPosition()
            if (pos == 0) lastCustom = copyLayout(binder.currentLayout())
            if (pos != shown) {
                shown = pos
                spinner.setSelection(pos, false)
            }
        }

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == shown) return
                shown = position
                if (position == 0) {
                    lastCustom?.let { binder.replaceLayout(it) }
                } else {
                    binder.replaceLayout(
                        KeyboardPrefs.alphabetLayoutWithPreset(activity, rowCount, LayoutPresets.ALL[position - 1])
                    )
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun copyLayout(layout: KeyboardConfig) = layout.copy(
        rows = layout.rows.map { row ->
            row.copy(keys = row.keys.map { it.copy(longPressBindings = it.longPressBindings.toMutableList()) }.toMutableList())
        }.toMutableList()
    )

    private fun showSpecialCharPicker(onPicked: (String) -> Unit) {
        val all = mutableListOf("∅")
        all.addAll(SpecialChars.ALL)

        AlertDialog.Builder(activity)
            .setTitle(R.string.pick_character)
            .setItems(all.toTypedArray()) { _, which ->
                val picked = all[which]
                onPicked(if (picked == "∅") "" else picked)
            }
            .show()
    }
}
