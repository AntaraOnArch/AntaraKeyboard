package com.example.antarakeyboard.service

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import com.example.antarakeyboard.EmojiData
import com.example.antarakeyboard.data.EmojiPickerStorage
import com.example.antarakeyboard.data.EmojiPickerStorage.EmojiButtonAction
import com.example.antarakeyboard.extensions.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Emoji category definition
 */
enum class EmojiCategory(
    val icon: String,
    val displayName: String,
    val emojisProvider: () -> List<String>
) {
    RECENT("🕐", "Recent", { emptyList() }), // Populated dynamically
    SMILEYS("😀", "Smileys & People", { EmojiData.smileys + EmojiData.hearts }),
    GESTURES("👋", "Gestures & Body", { EmojiData.gestures + EmojiData.body }),
    PEOPLE("👨", "People", { EmojiData.people + EmojiData.fantasy + EmojiData.activities }),
    ANIMALS("🐱", "Animals & Nature", {
        EmojiData.mammals + EmojiData.birds + EmojiData.reptilesAndMythicalAnimals +
                EmojiData.seaAnimals + EmojiData.insectsAndSmallAnimals + EmojiData.plantsAndFlowers
    }),
    FOOD("🍔", "Food & Drink", {
        EmojiData.fruits + EmojiData.vegetables + EmojiData.breadAndBreakfast +
                EmojiData.meatAndFastFood + EmojiData.cookedFood + EmojiData.sweets + EmojiData.drinks
    }),
    TRAVEL("🚗", "Travel & Places", { EmojiData.travelAndPlaces + EmojiData.transport }),
    ACTIVITIES("⚽", "Activities", { EmojiData.celebrations + EmojiData.sports + EmojiData.games + EmojiData.artsAndCrafts }),
    OBJECTS("💡", "Objects", {
        EmojiData.soundAndMusic + EmojiData.phonesAndComputers + EmojiData.lightsAndBooks +
                EmojiData.moneyAndMail + EmojiData.writingAndOffice + EmojiData.locksAndTools +
                EmojiData.scienceAndMedicine + EmojiData.household + EmojiData.clothing
    }),
    SYMBOLS("❤️", "Symbols", {
        EmojiData.publicSymbols + EmojiData.arrows + EmojiData.religionAndZodiac +
                EmojiData.mediaControls + EmojiData.extraSymbols + EmojiData.keycaps +
                EmojiData.buttonSymbols + EmojiData.geometricShapes
    }),
    FLAGS("🏳️", "Flags", { EmojiData.flags })
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
 * Manages the emoji picker popup with:
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
    private val callback: EmojiPickerCallback
) {
    private var popup: PopupWindow? = null
    private var currentCategory: EmojiCategory = EmojiCategory.SMILEYS
    private var categoryTabs: Map<EmojiCategory, View> = emptyMap()
    private var emojiContainer: LinearLayout? = null
    private var loadedCategories: MutableSet<EmojiCategory> = mutableSetOf()

    val isShowing: Boolean
        get() = popup != null

    fun show(keyboardHeight: Int) {
        hide()

        val overlayLayer = overlayLayerProvider()
        val screenWidth = context.resources.displayMetrics.widthPixels

        // Dimensions
        val popupWidth = (screenWidth * 0.85f).toInt().coerceAtLeast(280.dp(context))
        val popupHeight = (keyboardHeight * 0.88f).toInt()

        val categoryTabWidth = 36.dp(context)
        val actionButtonWidth = 44.dp(context)

        // Root layout: horizontal [tabs | grid | buttons]
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFF1E1E1E.toInt())
            clipChildren = false
            clipToPadding = false
        }

        // 1. Category tabs (left side, vertical)
        val tabsContainer = createCategoryTabs(categoryTabWidth, popupHeight)
        root.addView(tabsContainer, LinearLayout.LayoutParams(
            categoryTabWidth,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        // 2. Emoji grid (center)
        val gridContainer = createEmojiGridContainer()
        root.addView(gridContainer, LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.MATCH_PARENT,
            1f
        ))

        // 3. Action buttons (right side, vertical)
        val actionsContainer = createActionButtons(actionButtonWidth, popupHeight)
        root.addView(actionsContainer, LinearLayout.LayoutParams(
            actionButtonWidth,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        // Create popup
        popup = PopupWindow(
            root,
            popupWidth,
            popupHeight,
            false // Not focusable to allow keyboard interaction
        ).apply {
            isOutsideTouchable = true
            isFocusable = false
            elevation = 12.dp(context).toFloat()
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setOnDismissListener {
                popup = null
                loadedCategories.clear()
            }
        }

        // Position: center horizontally, align with keyboard
        val x = (overlayLayer.width - popupWidth) / 2
        val y = (overlayLayer.height - popupHeight - 4.dp(context)).coerceAtLeast(0)

        popup?.showAtLocation(overlayLayer, Gravity.NO_GRAVITY, x, y)

        // Load initial category
        loadCategory(currentCategory)
    }

    fun hide() {
        popup?.dismiss()
        popup = null
    }

    private fun createCategoryTabs(width: Int, height: Int): View {
        val scroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            setBackgroundColor(0xFF2A2A2A.toInt())
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

    private fun createEmojiGridContainer(): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(4.dp(context), 4.dp(context), 4.dp(context), 4.dp(context))
        }

        // Category title
        val titleView = TextView(context).apply {
            tag = "category_title"
            textSize = 12f
            setTextColor(0xAAFFFFFF.toInt())
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

    private fun createActionButtons(width: Int, height: Int): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(0xFF2A2A2A.toInt())
            setPadding(4.dp(context), 8.dp(context), 4.dp(context), 8.dp(context))
        }

        val buttonOrder = EmojiPickerStorage.getButtonOrder(context)
        val buttonHeight = (height - 24.dp(context)) / 3

        buttonOrder.forEach { action ->
            val btn = createActionButton(action, width - 8.dp(context), buttonHeight)
            container.addView(btn, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                buttonHeight
            ).apply {
                bottomMargin = 4.dp(context)
            })
        }

        return container
    }

    private fun createActionButton(action: EmojiButtonAction, width: Int, height: Int): View {
        return TextView(context).apply {
            text = EmojiPickerStorage.getButtonLabel(action)
            textSize = when (action) {
                EmojiButtonAction.CLOSE -> 20f
                EmojiButtonAction.BACKSPACE -> 18f
                EmojiButtonAction.SPACE -> 16f
            }
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(0xFF3A3A3A.toInt())

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
        titleView?.text = category.displayName

        // Clear existing emojis
        container.removeAllViews()

        // Show loading indicator
        val loadingView = TextView(context).apply {
            text = "Loading..."
            textSize = 14f
            setTextColor(0x88FFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(20.dp(context), 20.dp(context), 20.dp(context), 20.dp(context))
        }
        container.addView(loadingView)

        // Load emojis asynchronously
        scope.launch {
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
                            "No recent emojis"
                        } else {
                            "No emojis in this category"
                        }
                        textSize = 14f
                        setTextColor(0x88FFFFFF.toInt())
                        gravity = Gravity.CENTER
                        setPadding(20.dp(context), 40.dp(context), 20.dp(context), 40.dp(context))
                    }
                    container.addView(emptyView)
                    return@withContext
                }

                // Create emoji rows (5 per row)
                val columns = 5
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
}
