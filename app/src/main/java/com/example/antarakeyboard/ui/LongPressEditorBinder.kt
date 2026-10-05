package com.example.antarakeyboard.ui

import com.example.antarakeyboard.R
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.example.antarakeyboard.SpecialChars
import com.example.antarakeyboard.data.KeyboardPrefs
import com.example.antarakeyboard.data.LongPressPresets
import com.example.antarakeyboard.extensions.dp
import com.example.antarakeyboard.model.KeyConfig
import com.example.antarakeyboard.model.KeyboardConfig
import com.example.antarakeyboard.model.KeyShape
import com.example.antarakeyboard.service.EmojiCategory

class LongPressEditorBinder(
    private val context: Context,
    initial: KeyboardConfig,
    private val titleText: String,
    private val lockedLabels: Set<String> = setOf("⇧", "⌫", "↵", "123", "ABC", "abc", " "),
    private val onChanged: (KeyboardConfig) -> Unit = {}
) {
    private val cfg: KeyboardConfig = deepCopy(initial)

    private var keyboardContainer: LinearLayout? = null

    private data class PickerCategory(
        val icon: String,
        val name: String,
        val items: () -> List<String>
    )

    private val pickerCategories: List<PickerCategory> by lazy {
        listOf(
            PickerCategory("á", context.getString(R.string.lp_cat_diacritics)) { SpecialChars.DIACRITICS },
            PickerCategory("ł", context.getString(R.string.lp_cat_latin)) { LongPressPresets.allLatinLetters() },
            PickerCategory("ж", context.getString(R.string.lp_cat_cyrillic)) { LongPressPresets.allCyrillicLetters() },
            PickerCategory("€", context.getString(R.string.lp_cat_currency)) { SpecialChars.CURRENCY },
            PickerCategory("@", context.getString(R.string.lp_cat_symbols)) { (SpecialChars.SYMBOLS + extraLayoutSymbols()).distinct() },
            PickerCategory("( )", context.getString(R.string.lp_cat_brackets)) { SpecialChars.BRACKETS },
            PickerCategory("…", context.getString(R.string.lp_cat_punctuation)) { SpecialChars.PUNCTUATION },
            PickerCategory("±", context.getString(R.string.lp_cat_math)) { SpecialChars.MATH },
            PickerCategory("→", context.getString(R.string.lp_cat_arrows)) { SpecialChars.ARROWS }
        ) + EmojiCategory.entries
            .filter { it != EmojiCategory.RECENT }
            .map { PickerCategory(it.icon, context.getString(it.nameRes), it.emojisProvider) }
    }

    /** Symbols used in the default numeric / horizontal layouts that SpecialChars doesn't list. */
    private fun extraLayoutSymbols(): List<String> {
        val controls = setOf("⇧", "⌫", "↵", "ABC", "abc", "123", "😊")
        val known = SpecialChars.ALL.toSet()
        return listOf(
            defaultKeyboardLayout, defaultFourRowKeyboardLayout, defaultThreeRowKeyboardLayoutQwertz,
            defaultNumericLayout, defaultFourRowNumericLayout, defaultThreeRowNumericLayout,
            defaultHorizontalCenterLayout
        )
            .flatMap { layout -> layout.rows.flatMap { it.keys } }
            .flatMap { listOf(it.label) + it.longPressBindings }
            .filter { s ->
                s.isNotBlank() && !isMarker(s) && s !in controls && s !in known &&
                        s.none { it.isLetterOrDigit() }
            }
            .distinct()
    }

    fun bindInto(container: ViewGroup) {
        container.removeAllViews()

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14.dp(context), 14.dp(context), 14.dp(context), 10.dp(context))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        root.addView(TextView(context).apply {
            text = titleText
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, 10.dp(context))
        })

        val scroll = ScrollView(context).apply {
            isFillViewport = true
        }

        keyboardContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        scroll.addView(
            keyboardContainer,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        // Height follows content; weight lets it shrink (and scroll) when the screen is too short
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        container.addView(root)
        buildKeyboardUI()
    }

    fun getUpdatedConfig(): KeyboardConfig = cfg

    /** Editable keys that currently have at least one long-press binding (layout order). */
    fun boundKeys(): List<KeyConfig> =
        cfg.rows.flatMap { it.keys }
            .filter { key ->
                key.label !in lockedLabels && key.label.isNotBlank() &&
                        key.longPressBindings.any { !isMarker(it) }
            }

    /** Visible (non-marker) bindings of a key. */
    fun visibleBindings(key: KeyConfig): List<String> =
        key.longPressBindings.filterNot { isMarker(it) }

    /** Opens the character picker for [key]; [onClosed] runs after it is dismissed. */
    fun openPicker(key: KeyConfig, onClosed: () -> Unit = {}) {
        showLongPressPickerForKey(key, onClosed)
    }

    private fun buildKeyboardUI() {
        val userShape = KeyboardPrefs.getShape(context)
        val container = keyboardContainer ?: return

        // Same layout as the Set layout editor
        EditorKeyboardRenderer(context).render(container, cfg) { key, _, _ ->
            createKeyView(key, userShape)
        }
    }

    private fun createKeyView(key: KeyConfig, userShape: KeyShape): View {
        val locked = key.label in lockedLabels
        val empty = key.label.isBlank() && key.label != " "
        val boundCount = key.longPressBindings.count { !isMarker(it) }

        val wrapper = FrameLayout(context)

        // Same look as the Set layout editor keys
        val keyView = KeyView(context).apply {
            text = if (key.label.isBlank()) "" else key.label
            gravity = Gravity.CENTER
            textSize = 16f
            includeFontPadding = false
            isAllCaps = false
            shape = userShape
            isSpecial = (key.label == "↵")
            setTextColor(0xFFFFFFFF.toInt())
            customBgColor = 0xFF111111.toInt()

            alpha = when {
                locked -> 0.55f
                empty -> 0.30f
                else -> 1f
            }

            setOnClickListener {
                if (!locked && !empty) {
                    showLongPressPickerForKey(key)
                }
            }
        }

        wrapper.addView(
            keyView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        // Badge with number of bound characters
        if (!locked && boundCount > 0) {
            wrapper.addView(
                TextView(context).apply {
                    text = boundCount.toString()
                    textSize = 10f
                    gravity = Gravity.CENTER
                    setTextColor(0xFFFFFFFF.toInt())
                    setBackgroundColor(0x66000000)
                    setPadding(4.dp(context), 1.dp(context), 4.dp(context), 1.dp(context))
                },
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.END
                ).apply {
                    topMargin = 2.dp(context)
                    marginEnd = 2.dp(context)
                }
            )
        }

        return wrapper
    }

    /* ───────── LONG PRESS PICKER (emoji-picker style) ───────── */

    private fun isMarker(s: String) = s.startsWith("__") && s.endsWith("__")

    private fun showLongPressPickerForKey(key: KeyConfig, onClosed: () -> Unit = {}) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val selectedBg = GradientDrawable().apply {
            cornerRadius = 8.dp(context).toFloat()
            setColor(0xFF4A5A8A.toInt())
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(PopupColors.POPUP_BG)
            setPadding(8.dp(context), 8.dp(context), 8.dp(context), 8.dp(context))
        }

        // Header: title + close
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8.dp(context), 0, 0, 4.dp(context))
        }
        header.addView(TextView(context).apply {
            text = context.getString(R.string.lp_editor_title, key.label)
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(PopupColors.TEXT_PRIMARY)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(TextView(context).apply {
            text = "✕"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(PopupColors.TEXT_PRIMARY)
            setPadding(12.dp(context), 4.dp(context), 12.dp(context), 4.dp(context))
            setOnClickListener { dialog.dismiss() }
        })
        root.addView(header)

        // Currently bound characters (tap to remove)
        val boundScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(PopupColors.CONTAINER_BG)
        }
        val boundRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(6.dp(context), 4.dp(context), 6.dp(context), 4.dp(context))
        }
        boundScroll.addView(boundRow)
        root.addView(boundScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 44.dp(context)
        ).apply { bottomMargin = 6.dp(context) })

        // Category tabs
        val tabsScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(PopupColors.CONTAINER_BG)
        }
        val tabsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8.dp(context), 2.dp(context), 8.dp(context), 2.dp(context))
        }
        tabsScroll.addView(tabsRow)
        root.addView(tabsScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 40.dp(context)
        ))

        val categoryTitle = TextView(context).apply {
            textSize = 12f
            setTextColor(PopupColors.TEXT_SECONDARY)
            setPadding(8.dp(context), 6.dp(context), 8.dp(context), 4.dp(context))
        }
        root.addView(categoryTitle)

        // Grid
        val gridScroll = ScrollView(context).apply { isVerticalScrollBarEnabled = true }
        val grid = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        gridScroll.addView(grid)
        root.addView(gridScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (context.resources.displayMetrics.heightPixels * 0.42f).toInt()
        ))

        val dialogW = (context.resources.displayMetrics.widthPixels * 0.92f).toInt()
        val columns = 8
        val cellSize = (dialogW - 16.dp(context)) / columns

        val cellViews = mutableMapOf<String, MutableList<TextView>>()
        val tabViews = mutableMapOf<PickerCategory, TextView>()

        fun styleCell(cell: TextView, item: String) {
            cell.background = if (item in key.longPressBindings) selectedBg.constantState?.newDrawable() else null
        }

        lateinit var refreshBound: () -> Unit

        fun toggle(item: String) {
            if (item in key.longPressBindings) {
                key.longPressBindings.remove(item)
            } else {
                key.longPressBindings.add(item)
            }
            cellViews[item]?.forEach { styleCell(it, item) }
            refreshBound()
            // Autosave
            onChanged(cfg)
        }

        refreshBound = {
            boundRow.removeAllViews()
            val bound = key.longPressBindings.filterNot { isMarker(it) }
            if (bound.isEmpty()) {
                boundRow.addView(TextView(context).apply {
                    text = context.getString(R.string.lp_nothing_bound)
                    textSize = 13f
                    setTextColor(PopupColors.TEXT_HINT)
                    setPadding(6.dp(context), 0, 6.dp(context), 0)
                })
            } else {
                bound.forEach { item ->
                    boundRow.addView(TextView(context).apply {
                        text = item
                        textSize = 18f
                        gravity = Gravity.CENTER
                        setTextColor(PopupColors.TEXT_PRIMARY)
                        background = GradientDrawable().apply {
                            cornerRadius = 8.dp(context).toFloat()
                            setColor(PopupColors.BUTTON_BG)
                        }
                        setPadding(10.dp(context), 2.dp(context), 10.dp(context), 2.dp(context))
                        setOnClickListener { toggle(item) }
                    }, LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, 34.dp(context)
                    ).apply { marginEnd = 4.dp(context) })
                }
            }
        }

        fun showCategory(category: PickerCategory) {
            tabViews.forEach { (c, v) -> v.alpha = if (c == category) 1f else 0.5f }
            categoryTitle.text = category.name

            grid.removeAllViews()
            cellViews.clear()

            category.items().chunked(columns).forEach { rowItems ->
                val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                rowItems.forEach { item ->
                    val cell = TextView(context).apply {
                        text = item
                        textSize = 22f
                        gravity = Gravity.CENTER
                        setTextColor(PopupColors.TEXT_PRIMARY)
                        setOnClickListener { toggle(item) }
                    }
                    styleCell(cell, item)
                    cellViews.getOrPut(item) { mutableListOf() }.add(cell)
                    row.addView(cell, LinearLayout.LayoutParams(cellSize, cellSize))
                }
                grid.addView(row)
            }
            gridScroll.scrollTo(0, 0)
        }

        pickerCategories.forEach { category ->
            val tab = TextView(context).apply {
                text = category.icon
                textSize = 18f
                gravity = Gravity.CENTER
                setTextColor(PopupColors.TEXT_PRIMARY)
                setPadding(10.dp(context), 4.dp(context), 10.dp(context), 4.dp(context))
                setOnClickListener { showCategory(category) }
            }
            tabViews[category] = tab
            tabsRow.addView(tab, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }

        refreshBound()
        showCategory(pickerCategories.first())

        dialog.setContentView(root)
        dialog.setOnDismissListener {
            buildKeyboardUI()
            onClosed()
        }
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(dialogW, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun deepCopy(src: KeyboardConfig): KeyboardConfig {
        return KeyboardConfig(
            rows = src.rows.map { row ->
                row.copy(
                    keys = row.keys.map {
                        it.copy(longPressBindings = it.longPressBindings.toMutableList())
                    }.toMutableList()
                )
            }.toMutableList(),
            specialLeft = src.specialLeft.map {
                it.copy(longPressBindings = it.longPressBindings.toMutableList())
            }.toMutableList(),
            specialRight = src.specialRight.map {
                it.copy(longPressBindings = it.longPressBindings.toMutableList())
            }.toMutableList()
        )
    }
}
