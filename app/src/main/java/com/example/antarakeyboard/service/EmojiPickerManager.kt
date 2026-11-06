package com.example.antarakeyboard.service

import android.annotation.SuppressLint
import androidx.annotation.StringRes
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.example.antarakeyboard.EmojiData
import com.example.antarakeyboard.R
import com.example.antarakeyboard.data.EmojiPickerStorage
import com.example.antarakeyboard.data.EmojiPickerStorage.EmojiButtonAction
import com.example.antarakeyboard.data.EmojiPickerStorage.TabsPosition
import com.example.antarakeyboard.data.EmojiPickerStorage.ButtonsSide
import com.example.antarakeyboard.extensions.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Emoji category definition
 */
enum class EmojiCategory(
    val icon: String,
    @param:StringRes val nameRes: Int,
    val emojisProvider: () -> List<String>
) {
    RECENT("🕐", R.string.emoji_cat_recent, { emptyList() }), // Populated dynamically
    SMILEYS("😀", R.string.emoji_cat_smileys, { EmojiData.smileys + EmojiData.hearts }),
    GESTURES("👋", R.string.emoji_cat_gestures, { EmojiData.gestures + EmojiData.body }),
    PEOPLE("👨", R.string.emoji_cat_people, { EmojiData.people + EmojiData.fantasy + EmojiData.activities }),
    ANIMALS("🐱", R.string.emoji_cat_animals, {
        EmojiData.mammals + EmojiData.birds + EmojiData.reptilesAndMythicalAnimals +
                EmojiData.seaAnimals + EmojiData.insectsAndSmallAnimals + EmojiData.plantsAndFlowers
    }),
    FOOD("🍔", R.string.emoji_cat_food, {
        EmojiData.fruits + EmojiData.vegetables + EmojiData.breadAndBreakfast +
                EmojiData.meatAndFastFood + EmojiData.cookedFood + EmojiData.sweets + EmojiData.drinks
    }),
    TRAVEL("🚗", R.string.emoji_cat_travel, { EmojiData.travelAndPlaces + EmojiData.transport }),
    ACTIVITIES("⚽", R.string.emoji_cat_activities, { EmojiData.celebrations + EmojiData.sports + EmojiData.games + EmojiData.artsAndCrafts }),
    OBJECTS("💡", R.string.emoji_cat_objects, {
        EmojiData.soundAndMusic + EmojiData.phonesAndComputers + EmojiData.lightsAndBooks +
                EmojiData.moneyAndMail + EmojiData.writingAndOffice + EmojiData.locksAndTools +
                EmojiData.scienceAndMedicine + EmojiData.household + EmojiData.clothing
    }),
    SYMBOLS("❤️", R.string.emoji_cat_symbols, {
        EmojiData.publicSymbols + EmojiData.arrows + EmojiData.religionAndZodiac +
                EmojiData.mediaControls + EmojiData.extraSymbols + EmojiData.keycaps +
                EmojiData.buttonSymbols + EmojiData.geometricShapes
    }),
    FLAGS("🏳️", R.string.emoji_cat_flags, { EmojiData.flags })
}

/**
 * Callback interface for emoji picker actions
 */
interface EmojiPickerCallback {
    fun onEmojiSelected(emoji: String)
    fun onBackspace()
    fun onSpace()
    fun onClose()
}

/**
 * Emoji picker panel, shown in place of the keyboard (inside overlayLayer, same size) with:
 * - Category tabs on the left
 * - Emoji grid in the center
 * - 3 action buttons on the right (configurable order)
 * - Lazy loading of categories for performance
 * - Recent emojis section
 */
class EmojiPickerManager(
    private val context: Context,
    private val overlayLayerProvider: () -> FrameLayout,
    private val scope: CoroutineScope,
    private val callback: EmojiPickerCallback,
    /** Resolved from the active theme / Colors settings each time the picker opens. */
    private val colorsProvider: () -> Colors
) {
    data class Colors(
        val background: Int,
        val panel: Int,
        val button: Int,
        val text: Int,
        val hint: Int
    )

    private var colors = Colors(0xFF1E1E1E.toInt(), 0xFF2A2A2A.toInt(), 0xFF3A3A3A.toInt(), Color.WHITE, 0x88FFFFFF.toInt())
    private var loadJob: Job? = null

    private var panel: View? = null
    private var columns = MIN_COLUMNS
    private var currentCategory: EmojiCategory = EmojiCategory.SMILEYS
    private var categoryTabs: Map<EmojiCategory, View> = emptyMap()
    private var emojiContainer: LinearLayout? = null
    private var loadedCategories: MutableSet<EmojiCategory> = mutableSetOf()

    fun show() {
        hide()
        colors = colorsProvider()

        val overlayLayer = overlayLayerProvider()

        // Read layout settings
        val tabsPosition = EmojiPickerStorage.getTabsPosition(context)
        val buttonsSide = EmojiPickerStorage.getButtonsSide(context)

        val categoryTabSize = 36.dp(context)
        val actionButtonSize = 44.dp(context)

        val isTabsHorizontal = tabsPosition == TabsPosition.TOP || tabsPosition == TabsPosition.BOTTOM

        // Used emojis open first once there are enough of them to be worth it
        currentCategory = if (EmojiPickerStorage.getRecentEmojis(context).size >= RECENT_FIRST_MIN) {
            EmojiCategory.RECENT
        } else {
            EmojiCategory.SMILEYS
        }

        // Columns follow the grid's width: more emojis per row in landscape
        val overlayW = overlayLayer.width.takeIf { it > 0 } ?: context.resources.displayMetrics.widthPixels
        val gridW = overlayW - actionButtonSize - (if (isTabsHorizontal) 0 else categoryTabSize) - 8.dp(context)
        columns = (gridW / EMOJI_CELL_MIN_W.dp(context)).coerceAtLeast(MIN_COLUMNS)

        // Build layout based on settings
        val root = if (isTabsHorizontal) {
            buildHorizontalTabsLayout(categoryTabSize, actionButtonSize, tabsPosition, buttonsSide)
        } else {
            buildVerticalTabsLayout(categoryTabSize, actionButtonSize, tabsPosition, buttonsSide)
        }.apply {
            tag = PANEL_TAG
            // Takes every touch, so nothing reaches the keys or the background swipe below
            isClickable = true
            // Drawn (and touched) above the keys and side buttons, without a shadow
            translationZ = PANEL_Z.dp(context).toFloat()
            outlineProvider = null
        }

        // Replaces the keyboard in place: as large as the keyboard (side and top padding included,
        // the bottom keeps the navigation inset), nothing reaches above it over the text
        overlayLayer.addView(root, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ).apply {
            leftMargin = -overlayLayer.paddingLeft
            rightMargin = -overlayLayer.paddingRight
            topMargin = -overlayLayer.paddingTop
        })
        panel = root

        // Load initial category
        loadCategory(currentCategory)
    }

    /**
     * Build layout with tabs on LEFT or RIGHT (vertical tabs)
     * Structure: tabs?, buttons?, grid, buttons?, tabs?
     */
    private fun buildVerticalTabsLayout(
        tabSize: Int,
        buttonSize: Int,
        tabsPosition: TabsPosition,
        buttonsSide: ButtonsSide
    ): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(colors.background)
        }

        val tabsContainer = createCategoryTabsVertical()
        val actionsContainer = createActionButtonsVertical()
        val gridContainer = createEmojiGridContainer()

        // Order depends on settings
        val leftViews = mutableListOf<Pair<View, Int>>() // View to width
        val rightViews = mutableListOf<Pair<View, Int>>()

        // Place tabs
        if (tabsPosition == TabsPosition.LEFT) {
            leftViews.add(tabsContainer to tabSize)
        } else {
            rightViews.add(tabsContainer to tabSize)
        }

        // Place buttons
        if (buttonsSide == ButtonsSide.LEFT) {
            leftViews.add(actionsContainer to buttonSize)
        } else {
            rightViews.add(actionsContainer to buttonSize)
        }

        // Add left views
        leftViews.forEach { (view, width) ->
            root.addView(view, LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT))
        }

        // Add grid (center, flexible)
        root.addView(gridContainer, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))

        // Add right views
        rightViews.forEach { (view, width) ->
            root.addView(view, LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT))
        }

        return root
    }

    /**
     * Build layout with tabs on TOP or BOTTOM (horizontal tabs)
     * Structure: vertical tabs?, content row, tabs?
     * Content row: buttons?, grid, buttons?
     */
    private fun buildHorizontalTabsLayout(
        tabSize: Int,
        buttonSize: Int,
        tabsPosition: TabsPosition,
        buttonsSide: ButtonsSide
    ): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colors.background)
        }

        val tabsContainer = createCategoryTabsHorizontal()
        val actionsContainer = createActionButtonsVertical()
        val gridContainer = createEmojiGridContainer()

        // Content row (horizontal: buttons + grid + buttons)
        val contentRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        if (buttonsSide == ButtonsSide.LEFT) {
            contentRow.addView(actionsContainer, LinearLayout.LayoutParams(buttonSize, ViewGroup.LayoutParams.MATCH_PARENT))
        }

        contentRow.addView(gridContainer, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))

        if (buttonsSide == ButtonsSide.RIGHT) {
            contentRow.addView(actionsContainer, LinearLayout.LayoutParams(buttonSize, ViewGroup.LayoutParams.MATCH_PARENT))
        }

        // Add to root based on tabs position
        if (tabsPosition == TabsPosition.TOP) {
            root.addView(tabsContainer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, tabSize))
            root.addView(contentRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        } else {
            root.addView(contentRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            root.addView(tabsContainer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, tabSize))
        }

        return root
    }

    val isShowing: Boolean get() = panel != null

    fun hide() {
        val view = panel ?: return
        panel = null
        (view.parent as? ViewGroup)?.removeView(view)
        loadJob?.cancel()
        loadJob = null
        loadedCategories.clear()
        emojiContainer = null
    }

    /**
     * Create vertical category tabs (for LEFT/RIGHT position)
     */
    private fun createCategoryTabsVertical(): View {
        val scroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            setBackgroundColor(colors.panel)
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(2.dp(context), 8.dp(context), 2.dp(context), 8.dp(context))
        }

        val tabsMap = mutableMapOf<EmojiCategory, View>()

        EmojiCategory.entries.forEach { category ->
            val tab = TextView(context).apply {
                text = category.icon
                textSize = 18f
                gravity = Gravity.CENTER
                setPadding(4.dp(context), 10.dp(context), 4.dp(context), 10.dp(context))
                alpha = if (category == currentCategory) 1f else 0.5f

                setOnClickListener {
                    selectCategory(category)
                }
            }

            tabsMap[category] = tab
            container.addView(tab, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }

        categoryTabs = tabsMap
        scroll.addView(container)
        return scroll
    }

    /**
     * Create horizontal category tabs (for TOP/BOTTOM position)
     */
    private fun createCategoryTabsHorizontal(): View {
        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(colors.panel)
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8.dp(context), 2.dp(context), 8.dp(context), 2.dp(context))
        }

        val tabsMap = mutableMapOf<EmojiCategory, View>()

        EmojiCategory.entries.forEach { category ->
            val tab = TextView(context).apply {
                text = category.icon
                textSize = 18f
                gravity = Gravity.CENTER
                setPadding(10.dp(context), 4.dp(context), 10.dp(context), 4.dp(context))
                alpha = if (category == currentCategory) 1f else 0.5f

                setOnClickListener {
                    selectCategory(category)
                }
            }

            tabsMap[category] = tab
            container.addView(tab, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }

        categoryTabs = tabsMap
        scroll.addView(container)
        return scroll
    }

    // The touch listener only blocks the parent from intercepting and returns false
    @SuppressLint("ClickableViewAccessibility")
    private fun createEmojiGridContainer(): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(4.dp(context), 4.dp(context), 4.dp(context), 4.dp(context))
        }

        // Category title
        val titleView = TextView(context).apply {
            tag = "category_title"
            textSize = 12f
            setTextColor(colors.hint)
            setPadding(8.dp(context), 4.dp(context), 8.dp(context), 4.dp(context))
        }
        container.addView(titleView)

        // Scrollable emoji grid
        val scroll = ScrollView(context).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = true

            // Block swipe gestures when scrolling
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN,
                    MotionEvent.ACTION_MOVE -> {
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                }
                false
            }
        }

        val emojiGrid = LinearLayout(context).apply {
            tag = "emoji_grid"
            orientation = LinearLayout.VERTICAL
        }

        scroll.addView(emojiGrid)
        container.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        emojiContainer = container.findViewWithTag("emoji_grid")

        return container
    }

    /**
     * Create vertical action buttons (stacked top to bottom)
     */
    private fun createActionButtonsVertical(): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(colors.panel)
            setPadding(4.dp(context), 8.dp(context), 4.dp(context), 8.dp(context))
        }

        val buttonOrder = EmojiPickerStorage.getButtonOrder(context)

        // The three buttons share the panel's height
        buttonOrder.forEach { action ->
            val btn = createActionButton(action)
            container.addView(btn, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                bottomMargin = 4.dp(context)
            })
        }

        return container
    }

    // The touch listener only adds visual feedback and returns false; clicks still go through
    // onClick (and performClick), so calling performClick here would fire twice
    @SuppressLint("ClickableViewAccessibility")
    private fun createActionButton(action: EmojiButtonAction): View {
        return TextView(context).apply {
            text = EmojiPickerStorage.getButtonLabel(action)
            textSize = when (action) {
                EmojiButtonAction.CLOSE -> 20f
                EmojiButtonAction.BACKSPACE -> 18f
                EmojiButtonAction.SPACE -> 16f
            }
            gravity = Gravity.CENTER
            setTextColor(colors.text)
            setBackgroundColor(colors.button)

            setOnClickListener {
                when (action) {
                    EmojiButtonAction.CLOSE -> callback.onClose()
                    EmojiButtonAction.BACKSPACE -> callback.onBackspace()
                    EmojiButtonAction.SPACE -> callback.onSpace()
                }
            }

            // Visual feedback
            setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        v.alpha = 0.7f
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.alpha = 1f
                    }
                }
                false
            }
        }
    }

    private fun selectCategory(category: EmojiCategory) {
        if (category == currentCategory && loadedCategories.contains(category)) return

        // Update tab highlighting
        categoryTabs[currentCategory]?.alpha = 0.5f
        categoryTabs[category]?.alpha = 1f

        currentCategory = category
        loadCategory(category)
    }

    private fun loadCategory(category: EmojiCategory) {
        val container = emojiContainer ?: return
        val parent = container.parent?.parent as? LinearLayout ?: return
        val titleView = parent.findViewWithTag<TextView>("category_title")

        // Update title
        titleView?.text = context.getString(category.nameRes)

        // Clear existing emojis
        container.removeAllViews()

        // Show loading indicator
        val loadingView = TextView(context).apply {
            text = context.getString(R.string.emoji_loading)
            textSize = 14f
            setTextColor(colors.hint)
            gravity = Gravity.CENTER
            setPadding(20.dp(context), 20.dp(context), 20.dp(context), 20.dp(context))
        }
        container.addView(loadingView)

        // Load emojis asynchronously; a newer category selection cancels this load
        loadJob?.cancel()
        loadJob = scope.launch {
            val emojis = withContext(Dispatchers.Default) {
                if (category == EmojiCategory.RECENT) {
                    EmojiPickerStorage.getRecentEmojis(context)
                } else {
                    category.emojisProvider()
                }
            }

            withContext(Dispatchers.Main) {
                container.removeAllViews()

                if (emojis.isEmpty()) {
                    val emptyView = TextView(context).apply {
                        text = if (category == EmojiCategory.RECENT) {
                            context.getString(R.string.emoji_no_recent)
                        } else {
                            context.getString(R.string.emoji_empty_category)
                        }
                        textSize = 14f
                        setTextColor(colors.hint)
                        gravity = Gravity.CENTER
                        setPadding(20.dp(context), 40.dp(context), 20.dp(context), 40.dp(context))
                    }
                    container.addView(emptyView)
                    return@withContext
                }

                // Emoji rows; the column count was set from the panel's width in show()
                val chunked = emojis.chunked(columns)

                chunked.forEach { rowEmojis ->
                    val row = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_HORIZONTAL
                    }

                    rowEmojis.forEach { emoji ->
                        val emojiView = createEmojiButton(emoji)
                        row.addView(emojiView, LinearLayout.LayoutParams(
                            0,
                            48.dp(context),
                            1f
                        ))
                    }

                    // Fill remaining slots if row is incomplete
                    repeat(columns - rowEmojis.size) {
                        val spacer = View(context)
                        row.addView(spacer, LinearLayout.LayoutParams(0, 48.dp(context), 1f))
                    }

                    container.addView(row)
                }

                loadedCategories.add(category)
            }
        }
    }

    // The touch listener only adds visual feedback and returns false; clicks still go through
    // onClick (and performClick), so calling performClick here would fire twice
    @SuppressLint("ClickableViewAccessibility")
    private fun createEmojiButton(emoji: String): View {
        return TextView(context).apply {
            text = emoji
            textSize = 24f
            gravity = Gravity.CENTER
            includeFontPadding = false

            setOnClickListener {
                // Save to recent
                EmojiPickerStorage.addRecentEmoji(context, emoji)

                // Notify callback
                callback.onEmojiSelected(emoji)
            }

            setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        v.scaleX = 1.2f
                        v.scaleY = 1.2f
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.scaleX = 1f
                        v.scaleY = 1f
                    }
                }
                false
            }
        }
    }

    private companion object {
        const val PANEL_TAG = "emoji_panel"
        /** Recent opens first from this many used emojis on. */
        const val RECENT_FIRST_MIN = 10
        const val MIN_COLUMNS = 5
        /** Narrowest emoji cell in dp; decides how many emojis fit in a row. */
        const val EMOJI_CELL_MIN_W = 64
        const val PANEL_Z = 16
    }
}
