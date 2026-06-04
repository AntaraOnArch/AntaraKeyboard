package com.example.antarakeyboard.service

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.antarakeyboard.EmojiData
import com.example.antarakeyboard.R
import com.example.antarakeyboard.data.EdgePos
import com.example.antarakeyboard.data.EdgeSlotsStorage
import com.example.antarakeyboard.data.KeyboardPrefs
import com.example.antarakeyboard.model.EdgeActionType
import com.example.antarakeyboard.model.EdgeSlot
import com.example.antarakeyboard.model.KeyConfig
import com.example.antarakeyboard.model.KeyShape
import com.example.antarakeyboard.model.KeyboardConfig
import com.example.antarakeyboard.service.input.KeyInputController
import com.example.antarakeyboard.ui.KeyView
import com.example.antarakeyboard.ui.defaultFourRowKeyboardLayout
import com.example.antarakeyboard.ui.defaultFourRowNumericLayout
import com.example.antarakeyboard.ui.defaultKeyboardLayout
import com.example.antarakeyboard.ui.defaultNumericLayout
import com.example.antarakeyboard.ui.defaultThreeRowKeyboardLayoutQwertz
import com.example.antarakeyboard.ui.defaultThreeRowNumericLayout
import kotlin.math.max
import kotlin.math.roundToInt


class MyKeyboardService : InputMethodService() {

    /* ───────── STATE ───────── */

    private var isShifted = false
    private var isDrawing = false
    private var lastBottomInsetPx: Int = 0

    private var currentKeyboardConfig: KeyboardConfig = defaultKeyboardLayout
    private var currentShape: KeyShape = KeyShape.HEX
    private var activeShape: KeyShape = KeyShape.HEX

    private val mainHandler = Handler(Looper.getMainLooper())
    private val swipeEditHandler = Handler(Looper.getMainLooper())
    private val backspaceHoldHandler = Handler(Looper.getMainLooper())

    private var longPressPopup: PopupWindow? = null

    private lateinit var rootView: View
    private lateinit var keyboardContainer: LinearLayout
    private lateinit var overlayLayer: FrameLayout
    private lateinit var themedCtx: Context

    private val myDefaultNumericConfig: KeyboardConfig
        get() = when (KeyboardPrefs.getRowCount(this)) {
            3 -> defaultThreeRowNumericLayout
            4 -> defaultFourRowNumericLayout
            5 -> KeyboardPrefs.loadNumericLayout(this)
            else -> defaultThreeRowNumericLayout
        }

    private val OVERLAP_RATIO = 0.18f

    private var lastIsDark: Boolean? = null
    private var targetKeyboardHeightPx: Int = 0

    lateinit var inputController: KeyInputController
    private var landscapeSpaceIndex = 0

    private var lpRects: List<android.graphics.Rect> = emptyList()
    private var lpChars: List<String> = emptyList()
    private var lpSelectedIndex: Int = 0
    private var lpPreviewTv: TextView? = null
    private var lpGrid: GridLayout? = null

    private var alphabetLayoutLower: KeyboardConfig? = null
    private var alphabetLayoutUpper: KeyboardConfig? = null

    private var deleteRepeatRunnable: Runnable? = null
    private var restoreRepeatRunnable: Runnable? = null
    private var backspaceHoldRunnable: Runnable? = null
    private var backspaceStartHoldRunnable: Runnable? = null

    private var deleteRepeatMs: Long = 120L
    private var restoreRepeatMs: Long = 120L
    private var backspaceHoldMs: Long = 90L

    private var lastDeletedText: String = ""
    private val currentDeleteBatch = StringBuilder()
    private var restoreProgressIndex: Int = 0

    private var isDeleteGestureActive = false
    private var isRestoreGestureActive = false
    private var isBackspaceHoldActive = false

    private val LIVE_REPLACE = false
    private var lpHasLiveInserted = false
    private val EDGE_GHOST_MARKER = "__EDGE_GHOST__"
    private val USER_EMPTY_MARKER = "__USER_EMPTY__"
    private var emojiPopup: PopupWindow? = null
    /* ───────── LIFECYCLE ───────── */

    override fun onCreateInputView(): View {
        val isDark = getSharedPreferences("theme_prefs", MODE_PRIVATE)
            .getBoolean("dark_mode", true)

        val themeRes = if (isDark) {
            R.style.Theme_AntaraKeyboard_Dark
        } else {
            R.style.Theme_AntaraKeyboard_Light
        }

        themedCtx = ContextThemeWrapper(this, themeRes)
        lastIsDark = isDark

        rootView = layoutInflater.cloneInContext(themedCtx)
            .inflate(R.layout.keyboard_view, null)

        overlayLayer = rootView.findViewById(R.id.keyboardRoot)
        keyboardContainer = rootView.findViewById(R.id.keyboardContainer)

        lastBottomInsetPx = 0

        val bg = keyboardBgColor(themedCtx)
        window?.window?.setBackgroundDrawable(ColorDrawable(bg))
        rootView.setBackgroundColor(bg)
        overlayLayer.setBackgroundColor(bg)
        keyboardContainer.setBackgroundColor(bg)

        overlayLayer.clipChildren = false
        overlayLayer.clipToPadding = false
        keyboardContainer.clipChildren = false
        keyboardContainer.clipToPadding = false

        rootView.isFocusable = false
        rootView.isFocusableInTouchMode = false
        rootView.isLongClickable = false

        overlayLayer.isFocusable = false
        overlayLayer.isFocusableInTouchMode = false
        overlayLayer.isLongClickable = false
        overlayLayer.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS

        keyboardContainer.isFocusable = false
        keyboardContainer.isFocusableInTouchMode = false
        keyboardContainer.isLongClickable = false
        keyboardContainer.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS

        keyboardContainer.gravity = Gravity.CENTER_HORIZONTAL

        keyboardContainer.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        )

        inputController = KeyInputController(this)

        val basePadL = overlayLayer.paddingLeft
        val basePadT = overlayLayer.paddingTop
        val basePadR = overlayLayer.paddingRight
        val basePadB = overlayLayer.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(overlayLayer) { v, insets ->
            val navInset = insets.getInsetsIgnoringVisibility(
                WindowInsetsCompat.Type.navigationBars()
            ).bottom

            val tappableInset = insets.getInsetsIgnoringVisibility(
                WindowInsetsCompat.Type.tappableElement()
            ).bottom

            val gestureInset = insets.getInsetsIgnoringVisibility(
                WindowInsetsCompat.Type.systemGestures()
            ).bottom

            val bottomInset = maxOf(navInset, tappableInset, gestureInset)
            val insetChanged = lastBottomInsetPx != bottomInset

            lastBottomInsetPx = bottomInset
            v.setPadding(basePadL, basePadT, basePadR, basePadB + bottomInset)

            v.post {
                syncOverlayHeightToContent()
                if (insetChanged) {
                    redrawKeyboard()
                } else {
                    overlayLayer.requestLayout()
                }
            }

            insets
        }

        ViewCompat.requestApplyInsets(overlayLayer)

        targetKeyboardHeightPx = computeTargetKeyboardHeight()
        currentShape = KeyboardPrefs.getShape(this)

        val baseCfg = activeAlphabetBaseLayout()
        alphabetLayoutLower = baseCfg
        alphabetLayoutUpper = makeUppercaseConfig(baseCfg)

        val activeAlphabet = if (isShifted) alphabetLayoutUpper else alphabetLayoutLower
        currentKeyboardConfig = applyEdgeKeys(activeAlphabet ?: baseCfg)

        overlayLayer.post { redrawKeyboard() }
        return rootView
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        setExtractViewShown(false)

        isShifted = false

        val isDarkNow = getSharedPreferences("theme_prefs", MODE_PRIVATE)
            .getBoolean("dark_mode", true)

        if (lastIsDark != null && lastIsDark != isDarkNow) {
            lastIsDark = isDarkNow
            recreateInputView()
            return
        }
        lastIsDark = isDarkNow

        currentShape = KeyboardPrefs.getShape(this)

        val baseCfg = activeAlphabetBaseLayout()
        currentKeyboardConfig = applyEdgeKeys(baseCfg)

        val hasLetters = baseCfg.rows.any { row ->
            row.keys.any { k -> k.label.length == 1 && k.label[0].isLetter() }
        }

        if (hasLetters) {
            alphabetLayoutLower = baseCfg
            alphabetLayoutUpper = makeUppercaseConfig(baseCfg)
            currentKeyboardConfig = applyEdgeKeys(alphabetLayoutLower ?: baseCfg)
        }

        targetKeyboardHeightPx = computeTargetKeyboardHeight()
        overlayLayer.post { redrawKeyboard() }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        resetTransientState()
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        resetTransientState()
    }

    override fun onEvaluateFullscreenMode() = false
    override fun onCreateExtractTextView(): View? = null

    private fun resetTransientState() {
        isShifted = false

        stopSwipeDelete()
        stopSwipeRestore()
        stopBackspaceHold()
        hideEmojiPopup()

        currentDeleteBatch.clear()
        isDeleteGestureActive = false
        isRestoreGestureActive = false
        isBackspaceHoldActive = false
        restoreProgressIndex = 0

        hideLongPressPopup()
    }

    private fun recreateInputView() {
        setInputView(onCreateInputView())
    }

    /* ───────── HELPERS ───────── */

    private fun sideButtonTuning(
        isLandscapeMode: Boolean,
        rowCount: Int,
        visualIndex: Int,
        side: EdgePos.Side,
        slotType: EdgeActionType
    ): SideButtonTuning {
        return if (isLandscapeMode) {
            landscapeSideButtonTuning(rowCount, visualIndex, side, slotType)
        } else {
            portraitSideButtonTuning(rowCount, visualIndex, side, slotType)
        }
    }

    private fun portraitSideButtonTuning(
        rowCount: Int,
        visualIndex: Int,
        side: EdgePos.Side,
        slotType: EdgeActionType
    ): SideButtonTuning {
        return when (rowCount) {
            5 -> when (side) {
                EdgePos.Side.LEFT -> when (visualIndex) {
                    0 -> SideButtonTuning(x = -18, y = -5, widthScale = 0.48f, heightScale = 0.58f, iconX = 0, iconY = 0)// više lijevo / prema rubu
                    1 -> SideButtonTuning(x = -18, y = -3, widthScale = 0.48f, heightScale = 0.58f, iconX = 0, iconY = 0) // manje lijevo / više unutra
                    2 -> SideButtonTuning(x = -18, y = -1, widthScale = 0.48f, heightScale = 0.58f, iconX = 0, iconY = 0)
                    else -> SideButtonTuning()
                }

                EdgePos.Side.RIGHT -> when (visualIndex) {
                    0 -> SideButtonTuning(x = 2, y = -2, widthScale = 0.48f, heightScale = 0.58f, iconX = 0, iconY = 0)
                    1 -> SideButtonTuning(x = 2, y = -1, widthScale = 0.48f, heightScale = 0.58f, iconX = 0, iconY = 0)
                    2 -> SideButtonTuning(x = 2, y = -1, widthScale = 0.48f, heightScale = 0.58f, iconX = 0, iconY = 0)
                    else -> SideButtonTuning()
                }
            }

            4 -> when (side) {
                EdgePos.Side.LEFT -> when (visualIndex) {
                    0 -> SideButtonTuning(x = -15, y = 7, widthScale = 0.48f, heightScale = 0.48f, iconX = 0, iconY = 0)
                    1 -> SideButtonTuning(x = -15, y = 7, widthScale = 0.48f, heightScale = 0.48f, iconX = 0, iconY = 0)
                    2 -> SideButtonTuning(x = -15, y = 7, widthScale = 0.48f, heightScale = 0.48f, iconX = 0, iconY = 0)
                    3 -> SideButtonTuning(x = -15, y = 7, widthScale = 0.48f, heightScale = 0.48f, iconX = 0, iconY = 0)
                    else -> SideButtonTuning()
                }

                EdgePos.Side.RIGHT -> when (visualIndex) {
                    0 -> SideButtonTuning(x = -6, y = 7, widthScale = 0.48f, heightScale = 0.48f, iconX = 0, iconY = 0)
                    1 -> SideButtonTuning(x = -6, y = 7, widthScale = 0.48f, heightScale = 0.48f, iconX = 0, iconY = 0)
                    2 -> SideButtonTuning(x = -6, y = 7, widthScale = 0.48f, heightScale = 0.48f, iconX = 0, iconY = 0)
                    3 -> SideButtonTuning(x = -6, y = 7, widthScale = 0.48f, heightScale = 0.48f, iconX = 0, iconY = 0)
                    else -> SideButtonTuning()
                }
            }

            3 -> when (side) {
                EdgePos.Side.LEFT -> when (visualIndex) {
                    1 -> SideButtonTuning(x = -12, y = 0, widthScale = 0.2f, heightScale = 0.4f, iconX = 0, iconY = 0)
                    else -> SideButtonTuning()
                }

                EdgePos.Side.RIGHT -> when (visualIndex) {
                    0 -> SideButtonTuning(x = -4, y = 0, widthScale = 0.2f, heightScale = 0.4f, iconX = 0, iconY = 0)
                    2 -> SideButtonTuning(x = -4, y = 0, widthScale = 0.2f, heightScale = 0.4f, iconX = 0, iconY = 0)
                    else -> SideButtonTuning()
                }
            }

            else -> SideButtonTuning()
        }.let { base ->
            if (slotType == EdgeActionType.SHIFT) {
                base.copy(
                    iconTextSizeSp = when (rowCount) {
                        5 -> 21f
                        4 -> 18f
                        3 -> 18f
                        else -> 18f
                    },
                    iconY = base.iconY - 3
                )
            } else {
                base
            }
        }
    }

    private fun landscapeSideButtonTuning(
        rowCount: Int,
        visualIndex: Int,
        side: EdgePos.Side,
        slotType: EdgeActionType
    ): SideButtonTuning {
        return when (rowCount) {
            5 -> when (side) {
                EdgePos.Side.LEFT -> when (visualIndex) {
                    0 -> SideButtonTuning(x = -14, y = -5, widthScale = 0.55f, heightScale = 0.55f, iconX = 0, iconY = 0)
                    1 -> SideButtonTuning(x = -14, y = -5, widthScale = 0.55f, heightScale = 0.55f, iconX = 0, iconY = 0)
                    2 -> SideButtonTuning(x = -14, y = -4, widthScale = 0.55f, heightScale = 0.55f, iconX = 0, iconY = 0)
                    else -> SideButtonTuning(widthScale = 0.55f, heightScale = 0.55f)
                }

                EdgePos.Side.RIGHT -> when (visualIndex) {
                    0 -> SideButtonTuning(x = 17, y = -5, widthScale = 0.55f, heightScale = 0.55f, iconX = 0, iconY = 0)
                    1 -> SideButtonTuning(x = 17, y = -5, widthScale = 0.55f, heightScale = 0.55f, iconX = 0, iconY = 0)
                    2 -> SideButtonTuning(x = 17, y = -4, widthScale = 0.55f, heightScale = 0.55f, iconX = 0, iconY = 0)
                    else -> SideButtonTuning(widthScale = 0.55f, heightScale = 0.55f)
                }
            }

            4 -> when (side) {
                EdgePos.Side.LEFT -> when (visualIndex) {
                    0 -> SideButtonTuning(x = -18, y = -5, widthScale = 0.55f, heightScale = 0.55f, iconX = 0, iconY = 0)
                    1 -> SideButtonTuning(x = -18, y = -5, widthScale = 0.55f, heightScale = 0.55f, iconX = 0, iconY = 0)
                    2 -> SideButtonTuning(x = -18, y = -5, widthScale = 0.55f, heightScale = 0.55f, iconX = 0, iconY = 0)
                    3 -> SideButtonTuning(x = -18, y = -5, widthScale = 0.55f, heightScale = 0.55f, iconX = 0, iconY = 0)
                    else -> SideButtonTuning(widthScale = 0.55f, heightScale = 0.55f)
                }

                EdgePos.Side.RIGHT -> when (visualIndex) {
                    0 -> SideButtonTuning(x = 18, y = -5, widthScale = 0.55f, heightScale = 0.55f, iconX = 0, iconY = 0)
                    1 -> SideButtonTuning(x = 18, y = -5, widthScale = 0.55f, heightScale = 0.55f, iconX = 0, iconY = 0)
                    2 -> SideButtonTuning(x = 18, y = -5, widthScale = 0.55f, heightScale = 0.55f, iconX = 0, iconY = 0)
                    3 -> SideButtonTuning(x = 18, y = -5, widthScale = 0.55f, heightScale = 0.55f, iconX = 0, iconY = 0)
                    else -> SideButtonTuning(widthScale = 0.55f, heightScale = 0.55f)
                }
            }

            3 -> when (side) {
                EdgePos.Side.LEFT -> when (visualIndex) {
                   // 0 -> SideButtonTuning(x = -6, y = -6, widthScale = 0.2f, heightScale = 0.4f, iconX = 0, iconY = 0)
                    1 -> SideButtonTuning(x = -17, y = -6, widthScale = 0.8f, heightScale = 0.4f, iconX = 5, iconY = 0)
                    //2 -> SideButtonTuning(x = -6, y = -6, widthScale = 0.2f, heightScale = 0.4f, iconX = 0, iconY = 0)
                    else -> SideButtonTuning()
                }

                EdgePos.Side.RIGHT -> when (visualIndex) {
                    0 -> SideButtonTuning(x = 22, y = -8, widthScale = 0.8f, heightScale = 0.4f, iconX = -3, iconY = 0)
                    //1 -> SideButtonTuning(x = 22, y = -6, widthScale = 0.8f, heightScale = 0.4f, iconX = -3, iconY = 0)
                    2 -> SideButtonTuning(x = 22, y = -2, widthScale = 0.8f, heightScale = 0.4f, iconX = -3, iconY = 0)
                    else -> SideButtonTuning()
                }
            }

            else -> SideButtonTuning()
        }.let { base ->
            if (slotType == EdgeActionType.SHIFT) {
                base.copy(
                    iconTextSizeSp = 17f,
                    iconY = base.iconY - 2
                )
            } else {
                base.copy(
                    iconTextSizeSp = 13.5f
                )
            }
        }
    }
    private fun landscapeShape(): KeyShape {
        val savedRowCount = KeyboardPrefs.getRowCount(this)
        return if (
            isLandscape() &&
            savedRowCount == 3 &&
            currentShape == KeyShape.HEX
        ) {
            KeyShape.HEX_TALL
        } else {
            currentShape
        }
    }

    private fun effectiveShape(): KeyShape {
        val savedRowCount = KeyboardPrefs.getRowCount(this)
        return if (
            savedRowCount == 3 &&
            currentShape == KeyShape.HEX
        ) {
            KeyShape.HEX_TALL
        } else {
            currentShape
        }
    }
    private data class EdgeBinding(
        val visualIndex: Int,
        val side: EdgePos.Side,
        val slot: EdgeSlot
    )
    private data class SideButtonTuning(
        val x: Int = 0,
        val y: Int = 0,
        val widthScale: Float = 1f,
        val heightScale: Float = 1f,
        val iconX: Int = 0,
        val iconY: Int = 0,
        val iconTextSizeSp: Float? = null
    )
    private fun threeRowEdgeBindings(): List<EdgeBinding> {
        val slots = EdgeSlotsStorage.load(this)
        val result = mutableListOf<EdgeBinding>()

        // 3-row pravilo:
        // row 1 -> DESNO
        // row 2 -> LIJEVO
        // row 3 -> DESNO

        slots.getOrNull(0)?.takeIf { it.type != EdgeActionType.NONE }?.let {
            result += EdgeBinding(
                visualIndex = 0,
                side = EdgePos.Side.RIGHT,
                slot = it.copy(side = EdgePos.Side.RIGHT)
            )
        }

        slots.getOrNull(1)?.takeIf { it.type != EdgeActionType.NONE }?.let {
            result += EdgeBinding(
                visualIndex = 1,
                side = EdgePos.Side.LEFT,
                slot = it.copy(side = EdgePos.Side.LEFT)
            )
        }

        slots.getOrNull(2)?.takeIf { it.type != EdgeActionType.NONE }?.let {
            result += EdgeBinding(
                visualIndex = 2,
                side = EdgePos.Side.RIGHT,
                slot = it.copy(side = EdgePos.Side.RIGHT)
            )
        }

        return result
    }

    private fun fourRowEdgeBindings(): List<EdgeBinding> {
        val slots = EdgeSlotsStorage.load(this)
        val result = mutableListOf<EdgeBinding>()

        // 4-row pravilo:
        // row 1 -> desno
        // row 2 -> lijevo
        // row 3 -> desno
        // row 4 -> lijevo

        val slot0 = slots.getOrNull(0)
        val slot1 = slots.getOrNull(1)
        val slot2 = slots.getOrNull(2)
        val slot3 = slots.getOrNull(3)

        if (slot0 != null && slot0.type != EdgeActionType.NONE) {
            result += EdgeBinding(
                visualIndex = 0,
                side = EdgePos.Side.RIGHT,
                slot = slot0.copy(side = EdgePos.Side.RIGHT)
            )
        }

        if (slot1 != null && slot1.type != EdgeActionType.NONE) {
            result += EdgeBinding(
                visualIndex = 1,
                side = EdgePos.Side.LEFT,
                slot = slot1.copy(side = EdgePos.Side.LEFT)
            )
        }

        if (slot2 != null && slot2.type != EdgeActionType.NONE) {
            result += EdgeBinding(
                visualIndex = 2,
                side = EdgePos.Side.RIGHT,
                slot = slot2.copy(side = EdgePos.Side.RIGHT)
            )
        }

        if (slot3 != null && slot3.type != EdgeActionType.NONE) {
            result += EdgeBinding(
                visualIndex = 3,
                side = EdgePos.Side.LEFT,
                slot = slot3.copy(side = EdgePos.Side.LEFT)
            )
        }

        return result
    }

    private fun activeEdgeBindings(totalRows: Int): List<EdgeBinding> {
        return when (totalRows) {
            3 -> threeRowEdgeBindings()
            4 -> fourRowEdgeBindings()
            else -> {
                EdgeSlotsStorage.load(this)
                    .filter { it.type != EdgeActionType.NONE }
                    .map { slot ->
                        EdgeBinding(
                            visualIndex = (slot.index / 2).coerceIn(0, 2),
                            side = slot.side,
                            slot = slot
                        )
                    }
            }
        }
    }

    private fun activeAlphabetBaseLayout(): KeyboardConfig {
        return when (KeyboardPrefs.getRowCount(this)) {
            3 -> defaultThreeRowKeyboardLayoutQwertz
            4 -> defaultFourRowKeyboardLayout
            5 -> KeyboardPrefs.loadLayout(this)
            else -> defaultThreeRowKeyboardLayoutQwertz
        }
    }


    private fun isAlphabetLayoutActive(): Boolean {
        return currentKeyboardConfig.rows.any { row ->
            row.keys.any { key ->
                key.label.length == 1 && key.label[0].isLetter()
            }
        }
    }

    private fun hideEmojiPopup() {
        emojiPopup?.dismiss()
        emojiPopup = null
    }
    private fun showEmojiPicker() {
        hideLongPressPopup()
        hideEmojiPopup()

        val popupWidth = (availableKeyboardWidthPx() * 0.92f).toInt()
        val popupHeight = (computeTargetKeyboardHeight() * 0.78f).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setBackgroundColor(0xFF1E1E1E.toInt())
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(this).apply {
            text = "Emoji"
            textSize = 16f
            setTextColor(Color.WHITE)
        }

        val closeBtn = TextView(this).apply {
            text = "✕"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(4), dp(10), dp(4))
            setOnClickListener {
                hideEmojiPopup()
            }
        }

        header.addView(
            title,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        header.addView(
            closeBtn,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val scroll = ScrollView(this).apply {
            isFillViewport = true
        }

        val grid = GridLayout(this).apply {
            columnCount = 5
            useDefaultMargins = false
            alignmentMode = GridLayout.ALIGN_BOUNDS
        }

        EmojiData.basic.forEach { emoji ->
            val btn = Button(this).apply {
                text = emoji
                isAllCaps = false
                textSize = 24f
                setPadding(0, 0, 0, 0)
                setOnClickListener {
                    currentInputConnection?.commitText(emoji, 1)
                }
            }

            val lp = GridLayout.LayoutParams().apply {
                width = 0
                height = dp(52)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }

            grid.addView(btn, lp)
        }

        scroll.addView(
            grid,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            header,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(8)
            }
        )

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val popup = PopupWindow(
            root,
            popupWidth,
            popupHeight,
            true
        ).apply {
            isOutsideTouchable = true
            isFocusable = true
            elevation = dp(10).toFloat()
            setBackgroundDrawable(ColorDrawable(0xCC000000.toInt()))
            setOnDismissListener {
                emojiPopup = null
            }
        }

        emojiPopup = popup

        val x = ((overlayLayer.width - popupWidth) / 2).coerceAtLeast(dp(8))
        val y = ((overlayLayer.height - popupHeight) / 2).coerceAtLeast(dp(8))

        popup.showAtLocation(overlayLayer, Gravity.NO_GRAVITY, x, y)
    }

    private fun landscapeKeyGapPx(): Int = when (landscapeShape()) {
        KeyShape.TRIANGLE -> dp(0)
        KeyShape.CIRCLE -> dp(2)
        KeyShape.CUBE -> dp(2)
        else -> dp(2)
    }


    private fun themeColor(ctx: Context, attr: Int, fallback: Int): Int {
        val tv = android.util.TypedValue()
        val th = ctx.theme
        return if (
            th.resolveAttribute(attr, tv, true) &&
            tv.type in android.util.TypedValue.TYPE_FIRST_COLOR_INT..android.util.TypedValue.TYPE_LAST_COLOR_INT
        ) {
            tv.data
        } else {
            fallback
        }
    }

    private fun keyboardBgColor(ctx: Context): Int {
        return themeColor(
            ctx,
            android.R.attr.windowBackground,
            themeColor(ctx, android.R.attr.colorBackground, Color.BLACK)
        )
    }

    private fun edgeIconTextColor(ctx: Context): Int =
        themeColor(ctx, R.attr.edgeIconText, 0xFFFFFFFF.toInt())

    private fun edgeIconActiveColor(ctx: Context): Int =
        themeColor(ctx, R.attr.edgeIconTextActive, edgeIconTextColor(ctx))

    private fun isPortrait() =
        resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    private fun isLandscape() =
        resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()

    private fun computeTargetKeyboardHeight(): Int {
        val screenH = resources.displayMetrics.heightPixels
        val savedRowCount = KeyboardPrefs.getRowCount(this)

        return if (isPortrait()) {
            (screenH * 0.36f).roundToInt().coerceAtLeast(dp(230))
        } else {
            val ratio = when (savedRowCount) {
                3 -> 0.28f
                4 -> 0.38f
                5 -> 0.52f
                else -> 0.36f
            }

            val minH = when (savedRowCount) {
                3 -> dp(120)
                4 -> dp(160)
                5 -> dp(175)
                else -> dp(120)
            }

            (screenH * ratio).roundToInt().coerceAtLeast(minH)
        }
    }

    private fun totalVisibleRows(): Int {
        var rows = currentKeyboardConfig.rows.size
        if (currentKeyboardConfig.specialLeft.isNotEmpty()) rows += 1
        if (currentKeyboardConfig.specialRight.isNotEmpty()) rows += 1
        return rows.coerceAtLeast(1)
    }

    private fun keyHeight(): Int {
        val rows = totalVisibleRows()

        val containerH = (targetKeyboardHeightPx + lastBottomInsetPx).takeIf { it > 0 }
            ?: computeTargetKeyboardHeight()

        val usableH = (containerH - overlayLayer.paddingTop - overlayLayer.paddingBottom)
            .coerceAtLeast(dp(120))

        val savedRowCount = KeyboardPrefs.getRowCount(this)
        val activeShape = currentShape
        val usableFactor = if (isLandscape()) 0.88f else 0.92f
        val usableForKeys = (usableH * usableFactor).toInt()

        if (!isLandscape()) {
            when (savedRowCount) {
                3 -> {
                    return when(activeShape) {
                        KeyShape.HEX,
                        KeyShape.HEX_TALL,
                        KeyShape.HEX_HALF_LEFT,
                        KeyShape.HEX_HALF_RIGHT -> (usableForKeys / 3.15f).toInt().coerceAtLeast(dp(52))

                        KeyShape.TRIANGLE -> (usableForKeys / 3.35f).toInt().coerceAtLeast(dp(48))
                        KeyShape.CIRCLE -> (usableForKeys / 3.30f).toInt().coerceAtLeast(dp(50))
                        KeyShape.CUBE -> (usableForKeys / 3.30f).toInt().coerceAtLeast(dp(50))
                    }
                }

                4 -> {
                    return when (activeShape) {
                        KeyShape.HEX,
                        KeyShape.HEX_TALL,
                        KeyShape.HEX_HALF_LEFT,
                        KeyShape.HEX_HALF_RIGHT -> (usableForKeys / 4.05f).toInt().coerceAtLeast(dp(44))

                        KeyShape.TRIANGLE -> (usableForKeys / 4.25f).toInt().coerceAtLeast(dp(42))
                        KeyShape.CIRCLE -> (usableForKeys / 4.20f).toInt().coerceAtLeast(dp(43))
                        KeyShape.CUBE -> (usableForKeys / 4.20f).toInt().coerceAtLeast(dp(43))
                    }
                }
            }
        }

        val denom = when (activeShape) {
            KeyShape.HEX,
            KeyShape.HEX_TALL,
            KeyShape.HEX_HALF_LEFT,
            KeyShape.HEX_HALF_RIGHT -> (rows - (rows - 1) * OVERLAP_RATIO).coerceAtLeast(1f)

            KeyShape.TRIANGLE -> rows.toFloat()
            KeyShape.CIRCLE -> rows.toFloat()
            KeyShape.CUBE -> rows.toFloat()
        }

        val baseH = (usableForKeys / denom).toInt().coerceAtLeast(
            if (isLandscape()) dp(28) else dp(36)
        )

        return when (activeShape){
            KeyShape.HEX,
            KeyShape.HEX_TALL,
            KeyShape.HEX_HALF_LEFT,
            KeyShape.HEX_HALF_RIGHT -> (baseH * 1.08f).toInt()

            KeyShape.TRIANGLE -> if (isLandscape()) {
                (baseH * 0.84f).toInt()
            } else {
                (baseH * 0.92f).toInt()
            }

            KeyShape.CIRCLE -> if (isLandscape()) {
                (baseH * 0.88f).toInt()
            } else {
                (baseH * 0.96f).toInt()
            }

            KeyShape.CUBE -> if (isLandscape()) {
                (baseH * 0.88f).toInt()
            } else {
                (baseH * 0.96f).toInt()
            }
        }
    }

    private fun availableKeyboardWidthPx(): Int {
        val w = overlayLayer.width
        val base = if (w > 0) w else resources.displayMetrics.widthPixels
        return (base - overlayLayer.paddingLeft - overlayLayer.paddingRight).coerceAtLeast(dp(200))
    }

    private fun syncOverlayHeightToContent() {
        if (!::overlayLayer.isInitialized || !::keyboardContainer.isInitialized) return

        val contentH = keyboardContainer.height
        if (contentH <= 0) return

        val extraBottomSafety = if (isLandscape()) dp(6) else dp(16)

        val desired = contentH +
                overlayLayer.paddingTop +
                overlayLayer.paddingBottom +
                extraBottomSafety

        val minTarget = if (isLandscape()) dp(110) else dp(180)

        val maxTarget = if (isLandscape()) {
            (computeTargetKeyboardHeight() + lastBottomInsetPx).coerceAtLeast(dp(120))
        } else {
            (computeTargetKeyboardHeight() + lastBottomInsetPx).coerceAtLeast(dp(230))
        }

        val newH = desired.coerceIn(minTarget, maxTarget)

        val lp = overlayLayer.layoutParams ?: ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            newH
        )

        if (lp.height != newH) {
            lp.height = newH
            overlayLayer.layoutParams = lp
            overlayLayer.minimumHeight = newH
            overlayLayer.requestLayout()
        }
    }


    /* ───────── DELETE / RESTORE LOGIC ───────── */

    private fun beginDeleteBatch() {
        currentDeleteBatch.clear()
    }

    private fun appendDeletedChar(ch: String) {
        currentDeleteBatch.insert(0, ch)
    }

    private fun finishDeleteBatch() {
        val result = currentDeleteBatch.toString()
        if (result.isNotEmpty()) {
            lastDeletedText = result
            restoreProgressIndex = 0
        }
    }

    private fun clearRestoreBuffer() {
        lastDeletedText = ""
        restoreProgressIndex = 0
    }

    private fun swipeRepeatDelay(absDx: Float): Long {
        return when {
            absDx > dp(170) -> 25L
            absDx > dp(140) -> 40L
            absDx > dp(110) -> 55L
            absDx > dp(80) -> 75L
            absDx > dp(60) -> 95L
            else -> 120L
        }
    }

    private fun deleteOneForSwipe() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(1, 0)?.toString().orEmpty()
        if (before.isEmpty()) return

        appendDeletedChar(before)
        ic.deleteSurroundingText(1, 0)
    }

    private fun restoreOneForSwipe() {
        val ic = currentInputConnection ?: return
        if (lastDeletedText.isEmpty()) return
        if (restoreProgressIndex >= lastDeletedText.length) return

        val ch = lastDeletedText[restoreProgressIndex].toString()
        ic.commitText(ch, 1)
        restoreProgressIndex++

        if (restoreProgressIndex >= lastDeletedText.length) {
            lastDeletedText = ""
            restoreProgressIndex = 0
        }
    }

    fun startSwipeDelete(absDx: Float) {
        stopSwipeRestore()
        deleteRepeatMs = swipeRepeatDelay(absDx)

        if (!isDeleteGestureActive) {
            isDeleteGestureActive = true
            beginDeleteBatch()
        }

        if (deleteRepeatRunnable != null) return

        deleteRepeatRunnable = object : Runnable {
            override fun run() {
                deleteOneForSwipe()
                swipeEditHandler.postDelayed(this, deleteRepeatMs)
            }
        }

        deleteOneForSwipe()
        swipeEditHandler.postDelayed(deleteRepeatRunnable!!, deleteRepeatMs)
    }

    fun updateSwipeDelete(absDx: Float) {
        deleteRepeatMs = swipeRepeatDelay(absDx)
    }

    fun stopSwipeDelete() {
        deleteRepeatRunnable?.let { swipeEditHandler.removeCallbacks(it) }
        deleteRepeatRunnable = null

        if (isDeleteGestureActive) {
            finishDeleteBatch()
            isDeleteGestureActive = false
        }
    }
    private fun restoreRepeatDelay(absDx: Float): Long {
        return when {
            absDx > dp(170) -> 14L
            absDx > dp(140) -> 24L
            absDx > dp(110) -> 36L
            absDx > dp(80) -> 50L
            absDx > dp(60) -> 68L
            else -> 90L
        }
    }
    fun startSwipeRestore(absDx: Float) {
        stopSwipeDelete()
        restoreRepeatMs = restoreRepeatDelay(absDx)

        if (!isRestoreGestureActive) {
            isRestoreGestureActive = true
        }

        if (restoreRepeatRunnable != null) return

        restoreRepeatRunnable = object : Runnable {
            override fun run() {
                restoreOneForSwipe()
                swipeEditHandler.postDelayed(this, restoreRepeatMs)
            }
        }

        restoreOneForSwipe()
        swipeEditHandler.postDelayed(restoreRepeatRunnable!!, restoreRepeatMs)
    }

    fun updateSwipeRestore(absDx: Float) {
        restoreRepeatMs = restoreRepeatDelay(absDx)
    }



    fun stopSwipeRestore() {
        restoreRepeatRunnable?.let { swipeEditHandler.removeCallbacks(it) }
        restoreRepeatRunnable = null
        isRestoreGestureActive = false
    }

    fun startBackspaceHold() {
        cancelPendingBackspaceHold()

        if (!isBackspaceHoldActive) {
            isBackspaceHoldActive = true
            beginDeleteBatch()
        }

        if (backspaceHoldRunnable != null) return

        backspaceHoldRunnable = object : Runnable {
            override fun run() {
                deleteOneForSwipe()
                backspaceHoldHandler.postDelayed(this, backspaceHoldMs)
            }
        }

        // prvi delete odmah kad hold stvarno krene
        deleteOneForSwipe()
        backspaceHoldHandler.postDelayed(backspaceHoldRunnable!!, backspaceHoldMs)
    }
    fun commitExactText(text: String) {
        clearRestoreBuffer()
        currentInputConnection?.commitText(text, 1)
    }
    fun isBackspaceHoldRunning(): Boolean {
        return isBackspaceHoldActive
    }

    fun stopBackspaceHold() {
        cancelPendingBackspaceHold()

        backspaceHoldRunnable?.let { backspaceHoldHandler.removeCallbacks(it) }
        backspaceHoldRunnable = null

        if (isBackspaceHoldActive) {
            finishDeleteBatch()
            isBackspaceHoldActive = false
        }
    }

    /* ───────── INPUT API for Controller ───────── */

    fun scheduleBackspaceHold() {
        cancelPendingBackspaceHold()

        backspaceStartHoldRunnable = Runnable {
            startBackspaceHold()
        }

        backspaceHoldHandler.postDelayed(
            backspaceStartHoldRunnable!!,
            ViewConfiguration.getLongPressTimeout().toLong()
        )
    }

    fun cancelPendingBackspaceHold() {
        backspaceStartHoldRunnable?.let { backspaceHoldHandler.removeCallbacks(it) }
        backspaceStartHoldRunnable = null
    }
    fun backspaceOnce() {
        beginDeleteBatch()
        deleteOneForSwipe()
        finishDeleteBatch()
    }

    fun sendEnter() {
        currentInputConnection?.sendKeyEvent(
            KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)
        )
    }

    fun toggleShift() {
        isShifted = !isShifted

        val isNumeric = currentKeyboardConfig.rows.any { row ->
            row.keys.any { it.label == "123" || it.label.equals("abc", true) }
        }

        if (!isNumeric) {
            val lower = alphabetLayoutLower
            val upper = alphabetLayoutUpper
            if (lower != null && upper != null) {
                currentKeyboardConfig = applyEdgeKeys(if (isShifted) upper else lower)
            }
        }

        redrawKeyboard()
    }

    fun commitText(text: String) {
        if (text != "123" && text != "ABC" && text != "abc") {
            clearRestoreBuffer()
        }

        when (text) {
            "123" -> {
                currentKeyboardConfig = applyEdgeKeys(myDefaultNumericConfig)
                redrawKeyboard()
                return
            }

            "ABC", "abc" -> {
                val baseCfg = activeAlphabetBaseLayout()
                alphabetLayoutLower = baseCfg
                alphabetLayoutUpper = makeUppercaseConfig(baseCfg)
                currentKeyboardConfig = applyEdgeKeys(
                    if (isShifted) alphabetLayoutUpper ?: baseCfg
                    else alphabetLayoutLower ?: baseCfg
                )
                redrawKeyboard()
                return
            }
        }

        val out = if (text.length == 1 && text[0].isLetter()) {
            if (isShifted) text.uppercase() else text.lowercase()
        } else {
            text
        }

        currentInputConnection?.commitText(out, 1)
    }



    /* ───────── EDGE KEYS ───────── */

    private fun applyEdgeKeys(cfg: KeyboardConfig): KeyboardConfig {
        val copy = cfg.copy(
            rows = cfg.rows.map { row ->
                row.copy(
                    keys = row.keys.map { key ->
                        key.copy(longPressBindings = key.longPressBindings.toMutableList())
                    }.toMutableList()
                )
            }.toMutableList(),
            specialLeft = cfg.specialLeft.map {
                it.copy(longPressBindings = it.longPressBindings.toMutableList())
            }.toMutableList(),
            specialRight = cfg.specialRight.map {
                it.copy(longPressBindings = it.longPressBindings.toMutableList())
            }.toMutableList()
        )

        val slots = EdgeSlotsStorage.load(this).filter { it.type != EdgeActionType.NONE }
        if (slots.isEmpty()) return copy

        val labelsToHideFromMainLayout = buildSet<String> {
            slots.forEach { s ->
                when (s.type) {
                    EdgeActionType.SHIFT -> add("⇧")
                    EdgeActionType.BACKSPACE -> add("⌫")

                    // ENTER ostaje i u glavnom layoutu i kao side button
                    EdgeActionType.ENTER -> Unit

                    EdgeActionType.SPACE -> Unit
                    EdgeActionType.CHAR -> Unit
                    EdgeActionType.EMOJI_PICKER -> Unit
                    EdgeActionType.NONE -> Unit
                }
            }
        }

        fun replaceWithGhostPlaceholder(list: MutableList<KeyConfig>) {
            for (i in list.indices) {
                val key = list[i]
                if (key.label in labelsToHideFromMainLayout) {
                    list[i] = key.copy(
                        label = "",
                        longPressBindings = mutableListOf(EDGE_GHOST_MARKER)
                    )
                }
            }
        }

        copy.rows.forEach { replaceWithGhostPlaceholder(it.keys) }
        replaceWithGhostPlaceholder(copy.specialLeft)
        replaceWithGhostPlaceholder(copy.specialRight)

        return copy
    }

    /* ───────── LONG PRESS POPUP ───────── */

    private fun showLongPressPopup(anchor: View, chars: List<String>) {
        if (chars.isEmpty()) return

        hideLongPressPopup()

        lpChars = chars
        lpSelectedIndex = 0
        lpHasLiveInserted = false

        val maxW = (overlayLayer.width.takeIf { it > 0 }
            ?: resources.displayMetrics.widthPixels) - dp(16)

        val cols = minOf(7, chars.size)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setBackgroundColor(0xFFFFFFFF.toInt())
            layoutParams = ViewGroup.LayoutParams(maxW, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val preview = TextView(this).apply {
            text = chars.first()
            textSize = 26f
            setTextColor(0xFF000000.toInt())
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(0, 0, 0, dp(6))
        }

        lpPreviewTv = preview
        root.addView(
            preview,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val grid = GridLayout(this).apply {
            columnCount = cols
            useDefaultMargins = false
            alignmentMode = GridLayout.ALIGN_BOUNDS
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        lpGrid = grid

        val popupKeyH = (keyHeight() * 0.62f).toInt().coerceIn(dp(28), dp(70))
        val popupTextSize = if (isPortrait()) 16f else 14f


        chars.forEachIndexed { idx, ch ->
            val kv = KeyView(themedCtx).apply {
                tag = idx
                text = ch
                isAllCaps = false
                shape = currentShape
                gravity = Gravity.CENTER
                isClickable = false
                isFocusable = false
                textSize = popupTextSize
                setTextColor(themeColor(this@MyKeyboardService, R.attr.keyText, Color.WHITE))
                includeFontPadding = false
                setPadding(0, 0, 0, 0)
            }

            val lp = GridLayout.LayoutParams().apply {
                rowSpec = GridLayout.spec(idx / cols)
                columnSpec = GridLayout.spec(idx % cols, 1f)
                width = 0
                height = popupKeyH
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }

            grid.addView(kv, lp)
        }

        root.addView(grid)

        val pw = PopupWindow(
            root,
            maxW,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            false
        ).apply {
            isOutsideTouchable = false
            isFocusable = false
            isClippingEnabled = true
            elevation = dp(10).toFloat()
            setOnDismissListener {
                longPressPopup = null
                lpPreviewTv = null
                lpGrid = null
                lpChars = emptyList()
                lpHasLiveInserted = false
            }
        }

        root.measure(
            View.MeasureSpec.makeMeasureSpec(maxW, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )

        val anchorLoc = IntArray(2)
        val rootLoc = IntArray(2)
        anchor.getLocationOnScreen(anchorLoc)
        overlayLayer.getLocationOnScreen(rootLoc)

        val popupW = maxW
        val popupH = root.measuredHeight

        val desiredX = anchorLoc[0] - rootLoc[0] + anchor.width / 2 - popupW / 2
        val desiredY = anchorLoc[1] - rootLoc[1] - popupH - dp(10)

        val x = desiredX.coerceIn(dp(8), overlayLayer.width - popupW - dp(8))
        val y = desiredY.coerceIn(dp(8), overlayLayer.height - popupH - dp(8))

        pw.showAtLocation(overlayLayer, Gravity.NO_GRAVITY, x, y)
        longPressPopup = pw

        updateLongPressHighlight()

        root.post {
            val rects = MutableList(lpChars.size) { android.graphics.Rect() }
            val g = lpGrid ?: return@post

            for (i in 0 until g.childCount) {
                val child = g.getChildAt(i)
                val idx = (child.tag as? Int) ?: continue
                val loc = IntArray(2)
                child.getLocationOnScreen(loc)

                rects[idx] = android.graphics.Rect(
                    loc[0],
                    loc[1],
                    loc[0] + child.width,
                    loc[1] + child.height
                )
            }

            lpRects = rects
        }

        if (LIVE_REPLACE) {
            commitLiveSelected()
        }
    }

    private fun updateLongPressHighlight() {
        val grid = lpGrid ?: return

        val fillActive = themeColor(this, R.attr.enterFill, 0xFF2E55E7.toInt())
        val textActive = themeColor(this, R.attr.enterText, 0xFFFFFFFF.toInt())
        val textNormal = themeColor(this, R.attr.keyText, 0xFFFFFFFF.toInt())

        for (i in 0 until grid.childCount) {
            val child = grid.getChildAt(i)
            val idx = (child.tag as? Int) ?: continue

            if (child is KeyView) {
                if (idx == lpSelectedIndex) {
                    child.customBgColor = fillActive
                    child.setTextColor(textActive)
                    child.alpha = 1f
                } else {
                    child.customBgColor = null
                    child.setTextColor(textNormal)
                    child.alpha = 0.65f
                }
            } else {
                child.alpha = if (idx == lpSelectedIndex) 1f else 0.65f
            }

            child.scaleX = if (idx == lpSelectedIndex) 1.06f else 1f
            child.scaleY = if (idx == lpSelectedIndex) 1.06f else 1f
        }

        lpPreviewTv?.text = lpChars.getOrNull(lpSelectedIndex) ?: ""
    }

    private fun moveLpSelection(dx: Int, dy: Int) {
        if (lpChars.isEmpty()) return

        val cols = lpGrid?.columnCount ?: 7
        val total = lpChars.size
        val rows = (total + cols - 1) / cols

        val curRow = lpSelectedIndex / cols
        val curCol = lpSelectedIndex % cols

        var newRow = (curRow + dy).coerceIn(0, rows - 1)
        var newCol = (curCol + dx).coerceIn(0, cols - 1)
        var newIndex = newRow * cols + newCol

        if (newIndex >= total) {
            while (newIndex >= total && newCol > 0) {
                newCol--
                newIndex = newRow * cols + newCol
            }
            if (newIndex >= total) newIndex = total - 1
        }

        if (newIndex != lpSelectedIndex) {
            lpSelectedIndex = newIndex
            updateLongPressHighlight()

            if (LIVE_REPLACE) {
                replaceLiveSelected()
            }
        }
    }

    private fun commitLiveSelected() {
        val ch = lpChars.getOrNull(lpSelectedIndex) ?: return
        currentInputConnection?.commitText(ch, 1)
        lpHasLiveInserted = true
    }

    private fun replaceLiveSelected() {
        if (lpHasLiveInserted) {
            currentInputConnection?.deleteSurroundingText(1, 0)
        }
        commitLiveSelected()
    }

    private fun hideLongPressPopup() {
        longPressPopup?.dismiss()
        longPressPopup = null
    }

    /* ───────── LAYOUT ───────── */

    private data class RowSizing(
        val keyW: Int,
        val keyH: Int,
        val gapPx: Int,
        val outerPadPx: Int,
        val overlapPx: Int,
        val triOverlapX: Int,
        val triOverlapY: Int
    )

    private fun computeRowSizing(count: Int, availW: Int): RowSizing {
        val savedRowCount = KeyboardPrefs.getRowCount(this)
        val layoutShape = currentShape

        val gap = when (layoutShape) {
            KeyShape.HEX,
            KeyShape.HEX_TALL,
            KeyShape.HEX_HALF_LEFT,
            KeyShape.HEX_HALF_RIGHT -> {
                when {
                    isLandscape() -> dp(0)
                    savedRowCount == 3 -> dp(0)
                    savedRowCount == 4 -> dp(0)
                    else -> dp(1)
                }
            }

            KeyShape.TRIANGLE -> dp(0)
            KeyShape.CIRCLE -> if (isLandscape()) dp(2) else dp(4)
            KeyShape.CUBE -> if (isLandscape()) dp(2) else dp(4)
        }

        val effectiveAvailW = when (layoutShape) {
            KeyShape.HEX,
            KeyShape.HEX_TALL,
            KeyShape.HEX_HALF_LEFT,
            KeyShape.HEX_HALF_RIGHT -> {
                when {
                    isLandscape() -> {
                        if (savedRowCount == 3) {
                            availW
                        } else {
                            (availW * 1.22f).toInt()
                        }
                    }

                    savedRowCount == 3 -> (availW * 0.95f).toInt()
                    savedRowCount == 4 -> (availW * 0.94f).toInt()
                    else -> availW
                }
            }

            KeyShape.TRIANGLE -> if (isLandscape()) (availW * 0.92f).toInt() else (availW * 0.78f).toInt()
            KeyShape.CIRCLE -> if (isLandscape()) availW else (availW * 0.92f).toInt()
            KeyShape.CUBE -> if (isLandscape()) availW else (availW * 0.92f).toInt()
        }

        val targetColumns = when {
            layoutShape == KeyShape.HEX && isLandscape() && savedRowCount == 3 -> 11f
            layoutShape == KeyShape.HEX && isLandscape() -> 7f
            savedRowCount == 3 -> 8.8f
            layoutShape == KeyShape.HEX && savedRowCount == 4 -> 8.9f
            layoutShape == KeyShape.HEX -> 7f
            else -> max(1, count).toFloat()
        }

        val minKeyWidth = when {
            isLandscape() && savedRowCount == 3 -> dp(24)
            isLandscape() -> dp(40)
            savedRowCount == 3 -> dp(20)
            savedRowCount == 4 -> dp(24)
            else -> dp(36)
        }

        val baseKeyW = ((effectiveAvailW - (targetColumns - 1) * gap) / targetColumns)
            .toInt()
            .coerceAtLeast(minKeyWidth)

        val keyW = when {
            layoutShape == KeyShape.HEX && isLandscape() && savedRowCount == 3 -> baseKeyW

            layoutShape == KeyShape.HEX && isLandscape() && count <= 6 -> {
                (baseKeyW * 1.06f).toInt()
            }

            layoutShape == KeyShape.HEX && isLandscape() && count >= 7 -> {
                baseKeyW
            }

            savedRowCount == 3 -> {
                ((effectiveAvailW - (count - 1) * gap) / count.toFloat())
                    .toInt()
                    .coerceAtLeast(dp(24))
            }

            savedRowCount == 4 -> {
                ((effectiveAvailW - (count - 1) * gap) / count.toFloat())
                    .toInt()
                    .coerceAtLeast(dp(22))
            }

            count == 7 -> {
                ((effectiveAvailW - (count - 1) * gap) / count.toFloat())
                    .toInt()
                    .coerceAtLeast(dp(36))
            }

            count == 6 -> baseKeyW

            else -> {
                ((effectiveAvailW - (count - 1) * gap) / max(1, count).toFloat())
                    .toInt()
                    .coerceAtLeast(dp(36))
            }
        }

        val used = count * keyW + (count - 1) * gap

        val outer = when {
            isLandscape() && layoutShape == KeyShape.HEX -> {
                ((availW - used) / 2).coerceAtLeast(dp(4))
            }

            isLandscape() -> {
                ((availW - used) / 2).coerceAtLeast(dp(6))
            }

            savedRowCount == 3 -> {
                ((availW - used) / 2).coerceAtLeast(0)
            }

            savedRowCount == 4 -> {
                ((availW - used) / 2).coerceAtLeast(0)
            }

            else -> {
                ((availW - used) / 2).coerceAtLeast(0)
            }
        }

        val rawKeyH = keyHeight()

        val keyH = if (
            !isLandscape() &&
            savedRowCount == 3 &&
            (
                    currentShape == KeyShape.HEX ||
                            currentShape == KeyShape.HEX_HALF_LEFT ||
                            currentShape == KeyShape.HEX_HALF_RIGHT
                    )
        ) {
            // 3-row portrait: izduženi hex gumbi, +60% visine
            (keyW * 1.90f).toInt().coerceAtLeast(dp(42))
        } else {
            rawKeyH
        }

        val overlap = when (currentShape) {
            KeyShape.HEX,
            KeyShape.HEX_TALL,
            KeyShape.HEX_HALF_LEFT,
            KeyShape.HEX_HALF_RIGHT -> {
                val ratio = when {
                    isLandscape() -> OVERLAP_RATIO * 0.6f
                    savedRowCount == 3 -> OVERLAP_RATIO * 1.58f
                    savedRowCount == 4 -> OVERLAP_RATIO * 1.00f
                    else -> OVERLAP_RATIO
                }
                (keyH * ratio).toInt()
            }

            else -> 0
        }

        return RowSizing(
            keyW = keyW,
            keyH = keyH,
            gapPx = gap,
            outerPadPx = outer,
            overlapPx = overlap,
            triOverlapX = 0,
            triOverlapY = 0
        )
    }

    private fun edgeRowIndices(totalRows: Int): List<Int> {
        if (totalRows <= 0) return emptyList()

        return when (totalRows) {
            3 -> listOf(0, 1, 2)
            4 -> listOf(0, 1, 2, 3)
            5 -> listOf(0, 2, 4)
            else -> listOf(0, totalRows / 2, totalRows - 1).distinct()
        }
    }

    private fun redrawKeyboard() {
        if (!::keyboardContainer.isInitialized) return
        if (!::overlayLayer.isInitialized) return
        if (isDrawing) return

        isDrawing = true

        mainHandler.post {
            keyboardContainer.removeAllViews()
            if (isLandscape()) {
                buildLandscapeLayout()

                keyboardContainer.post {
                    syncOverlayHeightToContent()

                    val toRemove = mutableListOf<View>()
                    for (i in 0 until overlayLayer.childCount) {
                        val v = overlayLayer.getChildAt(i)
                        val tag = v.tag?.toString() ?: continue
                        if (
                            tag.startsWith("edge_slot_") ||
                            tag.startsWith("edge_icon_") ||
                            tag.startsWith("landscape_side_btn_") ||
                            tag.startsWith("landscape_side_bg_")
                        ) {
                            toRemove.add(v)
                        }
                    }
                    toRemove.forEach { overlayLayer.removeView(it) }

                    drawLandscapeSideSlots()

                    isDrawing = false
                }
                return@post
            }


            var spaceIndex = 0

            fun buildRow(keys: List<KeyConfig>, containerRowIndex: Int) {
                val rowKeys = keys.filterNot { key ->
                    key.longPressBindings.contains(EDGE_GHOST_MARKER)
                }

                if (rowKeys.isEmpty()) return

                val availW = availableKeyboardWidthPx()
                val sizing = computeRowSizing(rowKeys.size, availW)

                val savedRowCount = KeyboardPrefs.getRowCount(this@MyKeyboardService)
                val layoutShape = currentShape

                val vPad = when (layoutShape) {
                    KeyShape.TRIANGLE -> 0

                    KeyShape.HEX,
                    KeyShape.HEX_TALL,
                    KeyShape.HEX_HALF_LEFT,
                    KeyShape.HEX_HALF_RIGHT -> {
                        when {
                            isLandscape() -> 0
                            savedRowCount == 3 -> dp(3)
                            else -> dp(1)
                        }
                    }

                    else -> if (isLandscape()) dp(1) else dp(2)
                }

                val shouldHoneycomb =
                    !isLandscape() &&
                            (layoutShape == KeyShape.HEX ||
                                    layoutShape == KeyShape.HEX_TALL ||
                                    layoutShape == KeyShape.HEX_HALF_LEFT ||
                                    layoutShape == KeyShape.HEX_HALF_RIGHT) &&
                            (savedRowCount == 3 || savedRowCount == 4)

                val honeycombShift = when {
                    !shouldHoneycomb -> 0
                    savedRowCount == 3 -> (sizing.keyW * 0.42f).toInt()
                    else -> (sizing.keyW * 0.50f).toInt()
                }

                val isShiftedRow = shouldHoneycomb && (containerRowIndex % 2 == 1)

                val leftPad = when {
                    savedRowCount == 4 && isShiftedRow ->
                        (sizing.outerPadPx + honeycombShift - dp(6)).coerceAtLeast(0)

                    savedRowCount == 4 ->
                        (sizing.outerPadPx - dp(6)).coerceAtLeast(0)

                    savedRowCount == 3 && isShiftedRow ->
                        (sizing.outerPadPx + honeycombShift - dp(6)).coerceAtLeast(0)

                    savedRowCount == 3 ->
                        (sizing.outerPadPx - dp(12)).coerceAtLeast(0)

                    isShiftedRow ->
                        sizing.outerPadPx + honeycombShift

                    else ->
                        sizing.outerPadPx
                }

                val rightPad = when {
                    savedRowCount == 4 && shouldHoneycomb && !isShiftedRow ->
                        sizing.outerPadPx + honeycombShift + dp(2)

                    savedRowCount == 4 ->
                        sizing.outerPadPx + dp(2)

                    savedRowCount == 3 && shouldHoneycomb && !isShiftedRow ->
                        (sizing.outerPadPx + honeycombShift - dp(10)).coerceAtLeast(0)

                    savedRowCount == 3 ->
                        (sizing.outerPadPx - dp(12)).coerceAtLeast(0)

                    shouldHoneycomb && !isShiftedRow ->
                        sizing.outerPadPx + honeycombShift

                    else ->
                        sizing.outerPadPx
                }

                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.START
                    setPadding(leftPad, vPad, rightPad, vPad)
                    clipToPadding = false
                    clipChildren = false
                }

                rowKeys.forEachIndexed { i, key ->
                    val kv = createKey(key)

                    if (layoutShape == KeyShape.TRIANGLE) {
                        kv.triangleFlipped = (i % 2 == 1)
                    }

                    spaceIndex = applySpecialKeyColors(kv, key, spaceIndex)

                    val lp = LinearLayout.LayoutParams(sizing.keyW, sizing.keyH).apply {
                        if (i > 0) leftMargin = sizing.gapPx
                    }

                    row.addView(kv, lp)
                }

                val lpRow = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )

                if (containerRowIndex > 0) {
                    lpRow.topMargin = when (layoutShape) {
                        KeyShape.HEX,
                        KeyShape.HEX_TALL,
                        KeyShape.HEX_HALF_LEFT,
                        KeyShape.HEX_HALF_RIGHT -> -sizing.overlapPx
                        else -> 0
                    }
                }

                keyboardContainer.addView(row, lpRow)
            }

            if (currentKeyboardConfig.specialLeft.isNotEmpty()) {
                buildRow(currentKeyboardConfig.specialLeft, 0)
            }

            currentKeyboardConfig.rows.forEachIndexed { idx, rowConfig ->
                val rowIndex = idx + if (currentKeyboardConfig.specialLeft.isNotEmpty()) 1 else 0
                buildRow(rowConfig.keys, rowIndex)
            }

            if (currentKeyboardConfig.specialRight.isNotEmpty()) {
                val rowIndex = currentKeyboardConfig.rows.size +
                        if (currentKeyboardConfig.specialLeft.isNotEmpty()) 1 else 0
                buildRow(currentKeyboardConfig.specialRight, rowIndex)
            }

            keyboardContainer.post {
                syncOverlayHeightToContent()
                overlayLayer.post {
                    drawEdgeSlots()
                    isDrawing = false
                }
            }
        }
    }

    /* ───────── EDGE OVERLAY ───────── */

    private fun drawLandscapeSideSlots() {
        overlayLayer.post {
            if (keyboardContainer.childCount == 0) return@post

            val root = keyboardContainer.getChildAt(0) as? ViewGroup ?: return@post
            if (root.childCount < 3) return@post

            val leftBlock = root.getChildAt(0) as? ViewGroup ?: return@post
            val rightBlock = root.getChildAt(2) as? ViewGroup ?: return@post

            val slots = EdgeSlotsStorage.load(this@MyKeyboardService)
                .filter { it.type != EdgeActionType.NONE }

            val landscapeBindings = if (KeyboardPrefs.getRowCount(this@MyKeyboardService) == 5) {
                emptyList()
            } else {
                activeEdgeBindings(KeyboardPrefs.getRowCount(this@MyKeyboardService))
            }

            val ovLoc = IntArray(2)
            overlayLayer.getLocationOnScreen(ovLoc)

            val keySize = landscapeKeySizePx()
            val rawSideWidthLeft = keySize
            val rawSideWidthRight = keySize

            val savedRowCount = KeyboardPrefs.getRowCount(this@MyKeyboardService)
            val visualRows = edgeRowIndices(leftBlock.childCount)

            fun rowView(block: ViewGroup, rowIndex: Int): View? {
                if (rowIndex !in 0 until block.childCount) return null
                return block.getChildAt(rowIndex)
            }

            fun firstChild(row: View): View? {
                val vg = row as? ViewGroup ?: return null
                if (vg.childCount == 0) return null
                return vg.getChildAt(0)
            }

            fun lastChild(row: View): View? {
                val vg = row as? ViewGroup ?: return null
                if (vg.childCount == 0) return null
                return vg.getChildAt(vg.childCount - 1)
            }

            visualRows.forEachIndexed { visualIndex, rowIndex ->
                val leftRow = rowView(leftBlock, rowIndex)
                val rightRow = rowView(rightBlock, rowIndex)

                val leftAnchor = leftRow?.let { firstChild(it) }
                val rightAnchor = rightRow?.let { lastChild(it) }

                if (leftAnchor == null || rightAnchor == null) return@forEachIndexed

                if (
                    leftAnchor.width <= 0 || leftAnchor.height <= 0 ||
                    rightAnchor.width <= 0 || rightAnchor.height <= 0
                ) {
                    overlayLayer.post { drawLandscapeSideSlots() }
                    return@post
                }

                val leftSlot = if (savedRowCount == 5) {
                    slots.firstOrNull {
                        (it.index / 2).coerceIn(0, 2) == visualIndex &&
                                it.side == EdgePos.Side.LEFT
                    }
                } else {
                    landscapeBindings.firstOrNull {
                        it.visualIndex == visualIndex &&
                                it.side == EdgePos.Side.LEFT
                    }?.slot
                }

                val rightSlot = if (savedRowCount == 5) {
                    slots.firstOrNull {
                        (it.index / 2).coerceIn(0, 2) == visualIndex &&
                                it.side == EdgePos.Side.RIGHT
                    }
                } else {
                    landscapeBindings.firstOrNull {
                        it.visualIndex == visualIndex &&
                                it.side == EdgePos.Side.RIGHT
                    }?.slot
                }

                val leftLoc = IntArray(2)
                val rightLoc = IntArray(2)

                leftAnchor.getLocationOnScreen(leftLoc)
                rightAnchor.getLocationOnScreen(rightLoc)

                val leftAnchorLeft = leftLoc[0] - ovLoc[0]
                val rightAnchorRight = (rightLoc[0] - ovLoc[0]) + rightAnchor.width

                leftSlot?.let { slot ->
                    val tuning = sideButtonTuning(
                        isLandscapeMode = true,
                        rowCount = savedRowCount,
                        visualIndex = visualIndex,
                        side = EdgePos.Side.LEFT,
                        slotType = slot.type
                    )

                    val sideW = (rawSideWidthLeft * tuning.widthScale).toInt()
                        .coerceAtLeast(dp(14))

                    val left = leftAnchorLeft - sideW + dp(tuning.x)
                    val top = leftLoc[1] - ovLoc[1] + dp(tuning.y)

                    val btn = createSideButtonView(
                        tagName = "edge_slot_left_$visualIndex",
                        slot = slot,
                        tuning = tuning,
                        isLandscapeMode = true
                    )

                    val sideH = (leftAnchor.height * tuning.heightScale).toInt()
                        .coerceAtLeast(dp(18))

                    overlayLayer.addView(
                        btn,
                        FrameLayout.LayoutParams(sideW, sideH).apply {
                            leftMargin = left.coerceIn(
                                -sideW / 2,
                                overlayLayer.width - sideW
                            )

                            topMargin = (top + (leftAnchor.height - sideH) / 2).coerceIn(
                                dp(2),
                                overlayLayer.height - sideH - dp(2)
                            )
                        }
                    )
                }

                rightSlot?.let { slot ->
                    val tuning = sideButtonTuning(
                        isLandscapeMode = true,
                        rowCount = savedRowCount,
                        visualIndex = visualIndex,
                        side = EdgePos.Side.RIGHT,
                        slotType = slot.type
                    )

                    val sideW = (rawSideWidthRight * tuning.widthScale).toInt()
                        .coerceAtLeast(dp(14))

                    val left = rightAnchorRight - sideW + dp(tuning.x)
                    val top = rightLoc[1] - ovLoc[1] + dp(tuning.y)

                    val btn = createSideButtonView(
                        tagName = "edge_slot_right_$visualIndex",
                        slot = slot,
                        tuning = tuning,
                        isLandscapeMode = true
                    )

                    val sideH = (rightAnchor.height * tuning.heightScale).toInt()
                        .coerceAtLeast(dp(18))

                    overlayLayer.addView(
                        btn,
                        FrameLayout.LayoutParams(sideW, sideH).apply {
                            leftMargin = left.coerceIn(
                                0,
                                overlayLayer.width - sideW
                            )

                            topMargin = (top + (rightAnchor.height - sideH) / 2).coerceIn(
                                dp(2),
                                overlayLayer.height - sideH - dp(2)
                            )
                        }
                    )
                }
            }
        }
    }
    private fun clearEdgeSlots() {
        val toRemove = mutableListOf<View>()

        for (i in 0 until overlayLayer.childCount) {
            val v = overlayLayer.getChildAt(i)
            val tag = v.tag?.toString() ?: continue

            if (
                tag.startsWith("edge_slot_") ||
                tag.startsWith("edge_icon_") ||
                tag.startsWith("landscape_side_btn_") ||
                tag.startsWith("landscape_side_bg_")
            ) {
                toRemove.add(v)
            }
        }

        toRemove.forEach { overlayLayer.removeView(it) }
    }

    private fun drawEdgeSlots() {
        overlayLayer.post {
            clearEdgeSlots()

            if (keyboardContainer.childCount == 0) return@post
            if (overlayLayer.width <= 0 || overlayLayer.height <= 0) {
                overlayLayer.post { drawEdgeSlots() }
                return@post
            }

            val safeY = dp(2)

            val liftY = when (KeyboardPrefs.getRowCount(this)) {
                5 -> dp(2)
                4 -> dp(12)
                else -> dp(6)
            }

            val sizing = computeRowSizing(7, availableKeyboardWidthPx())
            val keyW = sizing.keyW
            val totalRows = keyboardContainer.childCount

            val slotW = when (totalRows) {
                3 -> (keyW * 0.70f).toInt().coerceIn(dp(28), dp(54))
                4 -> (keyW * 0.70f).toInt().coerceIn(dp(28), dp(56))
                else -> (keyW * 0.72f).toInt().coerceIn(dp(30), dp(58))
            }

            val ovLoc = IntArray(2)
            overlayLayer.getLocationOnScreen(ovLoc)

            fun firstKey(row: View): View? {
                val vg = row as? ViewGroup ?: return null
                if (vg.childCount == 0) return null
                return vg.getChildAt(0)
            }

            fun lastKey(row: View): View? {
                val vg = row as? ViewGroup ?: return null
                if (vg.childCount == 0) return null
                return vg.getChildAt(vg.childCount - 1)
            }

            fun addSideButtonAt(
                tag: String,
                slot: EdgeSlot,
                tuning: SideButtonTuning,
                left: Int,
                top: Int,
                width: Int,
                height: Int
            ) {
                val btn = createSideButtonView(
                    tagName = tag,
                    slot = slot,
                    tuning = tuning,
                    isLandscapeMode = false
                )

                val sideOutset = when (totalRows) {
                    5 -> (width * 0.55f).toInt()
                    4 -> (width * 0.55f).toInt()
                    3 -> (width * 0.40f).toInt()
                    else -> (width * 0.14f).toInt()
                }

                val finalHeight = (height * tuning.heightScale).toInt()
                    .coerceAtLeast(dp(18))

                overlayLayer.addView(
                    btn,
                    FrameLayout.LayoutParams(width, finalHeight).apply {
                        gravity = Gravity.START

                        leftMargin = left.coerceIn(
                            -sideOutset,
                            overlayLayer.width - width + sideOutset
                        )

                        topMargin = (top + (height - finalHeight) / 2).coerceIn(
                            dp(2),
                            overlayLayer.height - finalHeight - dp(2)
                        )
                    }
                )
            }

            if (totalRows == 5) {
                val visualRows = edgeRowIndices(totalRows)
                val slots = EdgeSlotsStorage.load(this)
                    .filter { it.type != EdgeActionType.NONE }

                visualRows.forEachIndexed { visualIndex, rowIndex ->
                    val row = keyboardContainer.getChildAt(rowIndex) ?: return@forEachIndexed
                    val first = firstKey(row) ?: return@forEachIndexed
                    val last = lastKey(row) ?: return@forEachIndexed

                    if (first.width <= 0 || first.height <= 0 || last.width <= 0) {
                        first.post { drawEdgeSlots() }
                        return@post
                    }

                    val firstLoc = IntArray(2)
                    val lastLoc = IntArray(2)

                    first.getLocationOnScreen(firstLoc)
                    last.getLocationOnScreen(lastLoc)

                    val rowLeft = firstLoc[0] - ovLoc[0]
                    val rowRight = lastLoc[0] - ovLoc[0] + last.width

                    val baseTop = (firstLoc[1] - ovLoc[1] - liftY).coerceIn(
                        safeY,
                        overlayLayer.height - first.height - safeY
                    )

                    val leftSlot = slots.firstOrNull {
                        (it.index / 2).coerceIn(0, 2) == visualIndex &&
                                it.side == EdgePos.Side.LEFT
                    }

                    val rightSlot = slots.firstOrNull {
                        (it.index / 2).coerceIn(0, 2) == visualIndex &&
                                it.side == EdgePos.Side.RIGHT
                    }

                    leftSlot?.let { slot ->
                        val tuning = sideButtonTuning(
                            isLandscapeMode = false,
                            rowCount = totalRows,
                            visualIndex = visualIndex,
                            side = EdgePos.Side.LEFT,
                            slotType = slot.type
                        )

                        val width = (slotW * tuning.widthScale).toInt()
                            .coerceAtLeast(dp(18))

                        addSideButtonAt(
                            tag = "edge_slot_left_$visualIndex",
                            slot = slot,
                            tuning = tuning,
                            left = rowLeft - width + dp(tuning.x),
                            top = baseTop + dp(tuning.y),
                            width = width,
                            height = first.height
                        )
                    }

                    rightSlot?.let { slot ->
                        val tuning = sideButtonTuning(
                            isLandscapeMode = false,
                            rowCount = totalRows,
                            visualIndex = visualIndex,
                            side = EdgePos.Side.RIGHT,
                            slotType = slot.type
                        )

                        val width = (slotW * tuning.widthScale).toInt()
                            .coerceAtLeast(dp(18))

                        addSideButtonAt(
                            tag = "edge_slot_right_$visualIndex",
                            slot = slot,
                            tuning = tuning,
                            left = rowRight + dp(tuning.x),
                            top = baseTop + dp(tuning.y),
                            width = width,
                            height = first.height
                        )
                    }
                }

                return@post
            }

            val bindings = activeEdgeBindings(totalRows)

            bindings.forEach { binding ->
                val row = keyboardContainer.getChildAt(binding.visualIndex) ?: return@forEach
                val first = firstKey(row) ?: return@forEach
                val last = lastKey(row) ?: return@forEach

                if (first.width <= 0 || first.height <= 0 || last.width <= 0 || last.height <= 0) {
                    first.post { drawEdgeSlots() }
                    return@post
                }

                val firstLoc = IntArray(2)
                val lastLoc = IntArray(2)

                first.getLocationOnScreen(firstLoc)
                last.getLocationOnScreen(lastLoc)

                val rowLeft = firstLoc[0] - ovLoc[0]
                val rowRight = lastLoc[0] - ovLoc[0] + last.width

                val baseTop = (firstLoc[1] - ovLoc[1] - liftY).coerceIn(
                    safeY,
                    overlayLayer.height - first.height - safeY
                )

                val tuning = sideButtonTuning(
                    isLandscapeMode = false,
                    rowCount = totalRows,
                    visualIndex = binding.visualIndex,
                    side = binding.side,
                    slotType = binding.slot.type
                )

                val width = (slotW * tuning.widthScale).toInt()
                    .coerceAtLeast(dp(18))

                val left = if (binding.side == EdgePos.Side.LEFT) {
                    rowLeft - width + dp(tuning.x)
                } else {
                    rowRight + dp(tuning.x)
                }

                addSideButtonAt(
                    tag = if (binding.side == EdgePos.Side.LEFT) {
                        "edge_slot_left_${binding.visualIndex}"
                    } else {
                        "edge_slot_right_${binding.visualIndex}"
                    },
                    slot = binding.slot,
                    tuning = tuning,
                    left = left,
                    top = baseTop + dp(tuning.y),
                    width = width,
                    height = first.height
                )
            }
        }
    }

    private fun performEdgeAction(slot: EdgeSlot) {
        when (slot.type) {
            EdgeActionType.SHIFT -> toggleShift()
            EdgeActionType.BACKSPACE -> backspaceOnce()
            EdgeActionType.ENTER -> sendEnter()
            EdgeActionType.SPACE -> currentInputConnection?.commitText(" ", 1)
            EdgeActionType.CHAR -> slot.value?.let { currentInputConnection?.commitText(it, 1) }
            EdgeActionType.EMOJI_PICKER -> showEmojiPicker()
            EdgeActionType.NONE -> Unit
        }
    }
    private fun createSideButtonView(
        tagName: String,
        slot: EdgeSlot,
        tuning: SideButtonTuning,
        isLandscapeMode: Boolean
    ): FrameLayout {
        val label = when (slot.type) {
            EdgeActionType.SHIFT -> if (isShifted) "⇪" else "⇧"
            EdgeActionType.BACKSPACE -> "⌫"
            EdgeActionType.ENTER -> "↵"
            EdgeActionType.SPACE -> "␣"
            EdgeActionType.CHAR -> slot.value ?: ""
            EdgeActionType.EMOJI_PICKER -> "😊"
            EdgeActionType.NONE -> ""
        }

        val box = FrameLayout(this).apply {
            tag = tagName

            // TEMP DEBUG: prava površina side buttona
            setBackgroundColor(Color.GREEN)

            isClickable = true
            isFocusable = false
            isFocusableInTouchMode = false
            isSelected = false
        }

        val icon = TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(
                if (slot.type == EdgeActionType.SHIFT && isShifted) {
                    edgeIconActiveColor(themedCtx)
                } else {
                    edgeIconTextColor(themedCtx)
                }
            )

            textSize = tuning.iconTextSizeSp ?: when {
                slot.type == EdgeActionType.SHIFT && isLandscapeMode -> 17f
                slot.type == EdgeActionType.SHIFT -> 21f
                isLandscapeMode -> 13.5f
                else -> 13f
            }

            translationX = dp(tuning.iconX).toFloat()
            translationY = dp(tuning.iconY).toFloat()

            isFocusable = false
            isFocusableInTouchMode = false
            isSelected = false
        }

        box.addView(
            icon,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        box.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.alpha = 0.75f

                    if (slot.type == EdgeActionType.BACKSPACE) {
                        scheduleBackspaceHold()
                    }

                    true
                }

                MotionEvent.ACTION_UP -> {
                    view.performClick()
                    view.alpha = 1f

                    if (slot.type == EdgeActionType.BACKSPACE) {
                        cancelPendingBackspaceHold()

                        if (isBackspaceHoldRunning()) {
                            stopBackspaceHold()
                        } else {
                            backspaceOnce()
                        }
                    } else {
                        performEdgeAction(slot)
                    }

                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    view.alpha = 1f

                    if (slot.type == EdgeActionType.BACKSPACE) {
                        cancelPendingBackspaceHold()
                        stopBackspaceHold()
                    }

                    true
                }

                else -> false
            }
        }

        return box
    }


    /* ───────── KEY VIEW ───────── */
    private fun buildLandscapeLayout() {
        landscapeSpaceIndex = 0
        val savedRowCount = KeyboardPrefs.getRowCount(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                when (savedRowCount) {
                    3 -> dp(2)
                    5 -> dp(10)
                    else -> dp(8)
                },
                when (savedRowCount) {
                    4 -> dp(0)
                    else -> dp(4)
                },
                when (savedRowCount) {
                    3 -> dp(4)
                    5 -> dp(6)
                    else -> dp(8)
                },
                dp(4)
            )
            clipChildren = false
            clipToPadding = false
        }

        val leftContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                2.2f
            ).apply {
                if (savedRowCount == 5) {
                    leftMargin = dp(14)
                }
            }
        }

        val centerContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = if (savedRowCount == 3) dp(-16) else dp(8)
                rightMargin = if (savedRowCount == 3) dp(30) else dp(8)
            }
        }

        val rightContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false

            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                if (savedRowCount == 3) 1.9f else 2.2f
            ).apply {
                when (savedRowCount) {
                    3 -> {
                        leftMargin = -dp(24)
                        rightMargin = dp(0)
                    }

                    5 -> {
                        leftMargin = dp(0)
                        rightMargin = dp(14)
                    }
                }
            }
        }

        root.addView(leftContainer)
        root.addView(centerContainer)
        root.addView(rightContainer)

        keyboardContainer.addView(
            root,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        buildLandscapeLeftLetters(leftContainer)
        buildLandscapeCenter(centerContainer)
        buildLandscapeRightLetters(rightContainer)
    }
    private fun createCenterTextKey(label: String): KeyView {
        return KeyView(themedCtx).apply {
            text = label
            isAllCaps = false

            shape = effectiveShape()
            hideFill = true
            hideStroke = true
            customBgColor = Color.TRANSPARENT
            forceSquare = false

            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(0, 0, 0, 0)

            minWidth = 0
            minimumWidth = 0
            manualLabelSizeSp = 22f

            setTextColor(themeColor(this@MyKeyboardService, R.attr.keyText, Color.WHITE))

            // centralne tipke nemaju split label i nemaju swipe-up alternate
            useSplitLabels = false
            mainLabel = ""
            swipeUpLabel = null

            isClickable = true
            isLongClickable = false
            isFocusable = false
            isFocusableInTouchMode = false
            isSelected = false
            highlightColor = Color.TRANSPARENT
            setTextIsSelectable(false)

            setOnTouchListener { v, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        v.isPressed = true
                        hideLongPressPopup()
                        hideEmojiPopup()
                        true
                    }

                    MotionEvent.ACTION_UP -> {
                        v.performClick()
                        v.isPressed = false
                        v.isSelected = false
                        v.clearFocus()

                        currentInputConnection?.commitText(label, 1)
                        true
                    }

                    MotionEvent.ACTION_CANCEL -> {
                        v.isPressed = false
                        v.isSelected = false
                        v.clearFocus()
                        true
                    }

                    else -> true
                }
            }
        }
    }
    private fun landscapeKeySizePx(): Int {
        val savedRowCount = KeyboardPrefs.getRowCount(this)

        return when (currentShape) {
            KeyShape.HEX,
            KeyShape.HEX_TALL,
            KeyShape.HEX_HALF_LEFT,
            KeyShape.HEX_HALF_RIGHT -> {
                when (savedRowCount) {
                    3 -> dp(33)
                    4 -> dp(38)
                    5 -> dp(36)
                    else -> dp(42)
                }
            }

            KeyShape.TRIANGLE -> {
                when (savedRowCount) {
                    3 -> dp(32)
                    5 -> dp(34)
                    else -> dp(40)
                }
            }

            KeyShape.CIRCLE -> {
                when (savedRowCount) {
                    3 -> dp(32)
                    5 -> dp(34)
                    else -> dp(38)
                }
            }

            KeyShape.CUBE -> {
                when (savedRowCount) {
                    3 -> dp(31)
                    5 -> dp(33)
                    else -> dp(35)
                }
            }
        }
    }
    private fun landscapeRowOverlapPx(keySize: Int): Int {
        val savedRowCount = KeyboardPrefs.getRowCount(this)

        return when (currentShape) {
            KeyShape.HEX,
            KeyShape.HEX_TALL,
            KeyShape.HEX_HALF_LEFT,
            KeyShape.HEX_HALF_RIGHT -> {
                when (savedRowCount) {
                    5 -> dp(9)
                    4 -> dp(4) // manje preklapanja = redovi se više razmaknu
                    else -> (keySize * 0.25f).toInt()
                }
            }

            KeyShape.TRIANGLE -> {
                if (savedRowCount == 5) dp(3)
                else (keySize * 0.18f).toInt()
            }

            KeyShape.CIRCLE -> if (savedRowCount == 5) dp(5) else dp(4)
            KeyShape.CUBE -> if (savedRowCount == 5) dp(5) else dp(4)
        }
    }
    private fun buildLandscapeLeftLetters(container: LinearLayout) {
        val keySize = landscapeKeySizePx()
        val rowOverlap = landscapeRowOverlapPx(keySize)
        val keyGap = landscapeKeyGapPx()
        val halfStep = (keySize / 2f).toInt()

        currentKeyboardConfig.rows.forEachIndexed { rowIndex, row ->
            val keys = leftLandscapeKeysForRow(row.keys, rowIndex)
            if (keys.isEmpty()) return@forEachIndexed

            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                clipChildren = false
                clipToPadding = false
            }

            val savedRowCount = KeyboardPrefs.getRowCount(this)

            val baseLeftInset = when (savedRowCount) {
                3 -> dp(18)    // sva 3 reda desno od side buttona
                4 -> dp(12)
                else -> 0
            }

            val middleRowExtraRight = when (savedRowCount) {
                3 -> dp(9)    // samo 2. red još mrvicu desno za centriranje
                else -> 0
            }

            val rowLp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                if (rowIndex > 0) topMargin = -rowOverlap

                leftMargin = when {
                    // 3-row: 1. i 3. red ostaju honeycomb, ali svi idu malo desno
                    savedRowCount == 3 && rowIndex in setOf(0, 2) ->
                        -(halfStep / 2) + baseLeftInset

                    // 3-row: 2. red ide desno + dodatno centriranje
                    savedRowCount == 3 ->
                        baseLeftInset + middleRowExtraRight

                    savedRowCount == 4 && rowIndex in setOf(0, 2) ->
                        -halfStep + baseLeftInset

                    savedRowCount == 4 ->
                        baseLeftInset

                    isOddLandscapeRow(rowIndex) -> halfStep

                    else -> 0
                }
            }

            val startIndex = 0

            keys.forEachIndexed { i, key ->
                val kv = createKey(key)

                landscapeSpaceIndex = applySpecialKeyColors(kv, key, landscapeSpaceIndex)

                if (currentShape == KeyShape.TRIANGLE) {
                    kv.triangleFlipped = ((startIndex + i) % 2 == 1)
                }

                val lp = LinearLayout.LayoutParams(keySize, keySize).apply {
                    if (i > 0) leftMargin = keyGap
                }

                rowLayout.addView(kv, lp)
            }

            container.addView(rowLayout, rowLp)
        }
    }
    private fun buildLandscapeRightLetters(container: LinearLayout) {
        val keySize = landscapeKeySizePx()
        val rowOverlap = landscapeRowOverlapPx(keySize)
        val keyGap = landscapeKeyGapPx()
        val halfStep = (keySize / 2f).toInt()
        val savedRowCount = KeyboardPrefs.getRowCount(this)
        val baseRightInset = when (savedRowCount) {
            3 -> dp(14)    // sva 3 desna reda lijevo od side buttona
            else -> 0
        }

        currentKeyboardConfig.rows.forEachIndexed { rowIndex, row ->
            val visibleRow = row.keys.filterNot {
                it.longPressBindings.contains(EDGE_GHOST_MARKER)
            }

            val keys = rightLandscapeKeysForRow(row.keys, rowIndex)
            if (keys.isEmpty()) return@forEachIndexed

            val rowShiftX = 0

            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                clipChildren = false
                clipToPadding = false
                translationX = rowShiftX.toFloat()
            }

            val takeCount = when {
                savedRowCount == 3 -> {
                    when (rowIndex) {
                        0 -> 5
                        1 -> 6
                        2 -> 5
                        else -> keys.size
                    }
                }

                savedRowCount == 5 -> {
                    if (isOddLandscapeRow(rowIndex)) 3 else 4
                }

                else -> {
                    if (isOddLandscapeRow(rowIndex)) 3 else 4
                }
            }

            val startIndex = (visibleRow.size - takeCount).coerceAtLeast(0)

            keys.forEachIndexed { i, key ->
                val kv = createKey(key)

                landscapeSpaceIndex = applySpecialKeyColors(kv, key, landscapeSpaceIndex)

                if (currentShape == KeyShape.TRIANGLE) {
                    kv.triangleFlipped = ((startIndex + i) % 2 == 1)
                }

                val lp = LinearLayout.LayoutParams(keySize, keySize).apply {
                    if (i > 0) leftMargin = keyGap
                }

                rowLayout.addView(kv, lp)
            }

            val rowLp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                if (rowIndex > 0) {
                    topMargin = -rowOverlap
                }

                rightMargin = when {
                    // 3-row: sva 3 reda lijevo, uz postojeći honeycomb pomak
                    savedRowCount == 3 && isOddLandscapeRow(rowIndex) ->
                        halfStep + baseRightInset

                    savedRowCount == 3 ->
                        baseRightInset

                    savedRowCount == 5 && isOddLandscapeRow(rowIndex) ->
                        halfStep

                    savedRowCount == 5 ->
                        0

                    isOddLandscapeRow(rowIndex) ->
                        halfStep

                    else -> 0
                }
            }

            container.addView(rowLayout, rowLp)
        }
    }



    private fun buildLandscapeCenter(container: LinearLayout) {
        val cfg = KeyboardPrefs.loadHorizontalCenterLayout(this)
        val savedRowCount = KeyboardPrefs.getRowCount(this)

        val centerRows = if (savedRowCount == 3 && cfg.rows.isNotEmpty()) {
            cfg.rows.drop(1)   // 3-row: bez gornjeg reda
        } else {
            cfg.rows
        }

        val keySize = if (savedRowCount == 3) dp(28) else dp(28)
        val rowGap = if (savedRowCount == 3) dp(6) else dp(6)

        centerRows.forEachIndexed { rowIndex, rowConfig ->
            val rowKeys = rowConfig.keys.filter { key ->
                key.label.isNotBlank() &&
                        !key.longPressBindings.contains("__USER_EMPTY__") &&
                        !key.longPressBindings.contains(EDGE_GHOST_MARKER)
            }

            if (rowKeys.isEmpty()) return@forEachIndexed

            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                clipChildren = false
                clipToPadding = false
            }

            rowKeys.forEachIndexed { i, key ->
                val displayLabel = when {
                    savedRowCount == 3 && rowIndex == 0 && key.label == "@" -> "0"
                    savedRowCount == 3 && rowIndex == centerRows.lastIndex && key.label == "$" -> "@"
                    else -> key.label
                }

                val kv = createCenterTextKey(displayLabel)

                val lp = LinearLayout.LayoutParams(
                    keySize,
                    keySize
                ).apply {
                    if (i > 0) leftMargin = dp(4)
                }

                rowLayout.addView(kv, lp)
            }

            val rowLp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                if (container.childCount > 0) topMargin = rowGap
            }

            container.addView(rowLayout, rowLp)
        }
    }

    private fun isOddLandscapeRow(rowIndex: Int): Boolean {
        return rowIndex % 2 == 0
    }

    private fun leftLandscapeKeysForRow(
        rowKeys: List<KeyConfig>,
        rowIndex: Int
    ): List<KeyConfig> {
        val visible = rowKeys.filterNot {
            it.longPressBindings.contains(EDGE_GHOST_MARKER)
        }

        val savedRowCount = KeyboardPrefs.getRowCount(this)

        if (savedRowCount == 3) {
            return when (rowIndex) {
                0 -> visible.take(6)
                1 -> visible.take(5)
                2 -> visible.take(6)
                else -> visible.take(6)
            }
        }

        val takeCount = if (
            savedRowCount == 4 &&
            rowIndex in setOf(0, 2) &&
            visible.size >= 8
        ) {
            4
        } else {
            if (isOddLandscapeRow(rowIndex)) 3 else 4
        }

        return visible.take(takeCount)
    }

    private fun rightLandscapeKeysForRow(
        rowKeys: List<KeyConfig>,
        rowIndex: Int
    ): List<KeyConfig> {
        val visible = rowKeys.filterNot {
            it.longPressBindings.contains(EDGE_GHOST_MARKER)
        }

        val savedRowCount = KeyboardPrefs.getRowCount(this)

        if (savedRowCount == 3) {
            return when (rowIndex) {
                0 -> visible.drop(6).take(5)
                1 -> visible.drop(5).take(6)
                2 -> visible.drop(6).take(5)
                else -> visible.drop(6)
            }
        }

        val takeCount = if (
            savedRowCount == 4 &&
            rowIndex in setOf(0, 2) &&
            visible.size >= 8
        ) {
            4
        } else {
            if (isOddLandscapeRow(rowIndex)) 3 else 4
        }

        return visible.takeLast(takeCount)
    }

    private fun makeUppercaseConfig(cfg: KeyboardConfig): KeyboardConfig {
        fun up(k: KeyConfig): KeyConfig {
            val lbl = k.label
            val newLbl = if (lbl.length == 1 && lbl[0].isLetter()) lbl.uppercase() else lbl
            return k.copy(label = newLbl, longPressBindings = k.longPressBindings.toMutableList())
        }

        return cfg.copy(
            rows = cfg.rows.map { it.copy(keys = it.keys.map(::up).toMutableList()) }.toMutableList(),
            specialLeft = cfg.specialLeft.map(::up).toMutableList(),
            specialRight = cfg.specialRight.map(::up).toMutableList()
        )
    }

    private fun createKey(keyConfig: KeyConfig): KeyView = KeyView(themedCtx).apply {
        val activeShape = effectiveShape()
        val label = keyConfig.label
        setTextIsSelectable(false)
        highlightColor = Color.TRANSPARENT
        isFocusable = false
        isFocusableInTouchMode = false
        isSelected = false

        if (label.isEmpty()) {
            val isEdgeGhost = keyConfig.longPressBindings.contains(EDGE_GHOST_MARKER)
            val isUserEmpty = keyConfig.longPressBindings.contains(USER_EMPTY_MARKER)

            text = ""
            isAllCaps = false
            shape = activeShape
            gravity = Gravity.CENTER
            isClickable = false
            isFocusable = false

            when {
                isEdgeGhost -> {
                    hideCompletely = true
                    alpha = 0f
                    Color.TRANSPARENT
                }

                isUserEmpty -> {
                    hideCompletely = false
                    alpha = 0.65f
                    customBgColor = themeColor(
                        this@MyKeyboardService,
                        R.attr.keyFill,
                        0xFF4A4A4A.toInt()
                    )
                }

                else -> {
                    hideCompletely = true
                    alpha = 0f
                    Color.TRANSPARENT
                }
            }

            return@apply
        }

        hideCompletely = false

        val display = if (label.length == 1 && label[0].isLetter()) {
            if (isShifted) label.uppercase() else label.lowercase()
        } else {
            label
        }

        text = display

        useSplitLabels = false
        mainLabel = ""
        swipeUpLabel = null

        val rowCount = KeyboardPrefs.getRowCount(this@MyKeyboardService)

        when (label) {
            "." -> {
                useSplitLabels = true
                mainLabel = "."
                swipeUpLabel = ","
            }

            "?" -> {
                useSplitLabels = true
                mainLabel = "?"
                swipeUpLabel = "!"
            }

            "123" -> {
                if (isAlphabetLayoutActive() && rowCount >= 4) {
                    useSplitLabels = true
                    mainLabel = "123"
                    swipeUpLabel = "😊"
                }
            }
        }

        isAllCaps = false
        shape = activeShape
        isSpecial = (label == "↵")
        gravity = Gravity.CENTER
        includeFontPadding = false
        setPadding(0, 0, 0, 0)

        textSize = when (activeShape) {
            KeyShape.HEX,
            KeyShape.HEX_TALL,
            KeyShape.HEX_HALF_LEFT,
            KeyShape.HEX_HALF_RIGHT -> if (isLandscape()) 14f else 18f

            KeyShape.TRIANGLE -> if (isLandscape()) 13f else 16f
            KeyShape.CIRCLE -> if (isLandscape()) 14f else 18f
            KeyShape.CUBE -> if (isLandscape()) 14f else 18f
        }

        if (label in setOf("⇧", "⌫", "↵", "123", "ABC", "abc", "😊")) {
            textSize = if (isLandscape()) 13f else 16f
        }

        setTextColor(themeColor(this@MyKeyboardService, R.attr.keyText, Color.WHITE))

        if (label == "↵") {
            customBgColor = KeyboardPrefs.getEnterBg(context)
            setTextColor(KeyboardPrefs.getEnterIcon(context))
        }

        if (label == "⇧") {
            if (isShifted) {
                text = "⇪"
                customBgColor = Color.WHITE
                setTextColor(Color.BLACK)
            } else {
                text = "⇧"
                customBgColor = null
                setTextColor(themeColor(this@MyKeyboardService, R.attr.keyText, Color.WHITE))
            }
        }

        val nonBindable = setOf("⇧", "⌫", "↵", "123", "ABC", "abc", " ", "😊")
        val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

        var longPressTriggered = false
        var startX = 0f
        var startY = 0f
        val step = dp(18)
        var handledBySwipeUp = false
        val swipeUpThreshold = dp(26)

        val longPressRunnable = Runnable {
            if (label in nonBindable) return@Runnable
            val binds = keyConfig.longPressBindings
            if (binds.isNotEmpty()) {
                longPressTriggered = true
                showLongPressPopup(this, binds)
                lpSelectedIndex = 0
                updateLongPressHighlight()
            }
        }

        isLongClickable = true

        setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = e.rawX
                    startY = e.rawY
                    longPressTriggered = false
                    handledBySwipeUp = false
                    hideLongPressPopup()
                    hideEmojiPopup()

                    mainHandler.removeCallbacks(longPressRunnable)
                    if (label !in nonBindable) {
                        mainHandler.postDelayed(longPressRunnable, longPressTimeout)
                    }

                    inputController.handleTouch(v as TextView, e)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - startX
                    val dy = e.rawY - startY
                    val absDx = kotlin.math.abs(dx)
                    val absDy = kotlin.math.abs(dy)

                    // 123 swipe-up => emoji samo na 4/5 row alphabet layoutu
                    if (!longPressTriggered &&
                        !handledBySwipeUp &&
                        label == "123" &&
                        rowCount >= 4 &&
                        isAlphabetLayoutActive() &&
                        dy < -swipeUpThreshold &&
                        absDy > absDx
                    ) {
                        handledBySwipeUp = true
                        mainHandler.removeCallbacks(longPressRunnable)
                        hideLongPressPopup()
                        showEmojiPicker()
                        v.isPressed = false
                        return@setOnTouchListener true
                    }

                    // . swipe-up => ,
                    // ? swipe-up => !
                    if (!longPressTriggered &&
                        !handledBySwipeUp &&
                        (label == "." || label == "?") &&
                        (v as? KeyView)?.useSplitLabels == true &&
                        dy < -swipeUpThreshold &&
                        absDy > absDx
                    ) {
                        val kv = v as KeyView
                        val swipeText = kv.swipeUpLabel

                        if (!swipeText.isNullOrBlank()) {
                            handledBySwipeUp = true
                            mainHandler.removeCallbacks(longPressRunnable)
                            hideLongPressPopup()
                            currentInputConnection?.commitText(swipeText, 1)
                            v.isPressed = false
                            return@setOnTouchListener true
                        }
                    }

                    if (!longPressTriggered && absDx > dp(20) && absDx > absDy * 1.05f) {
                        mainHandler.removeCallbacks(longPressRunnable)
                        hideLongPressPopup()
                    }

                    if (longPressTriggered) {
                        if (absDx > absDy && absDx > step) {
                            moveLpSelection(if (dx > 0) 1 else -1, 0)
                            startX = e.rawX
                            startY = e.rawY
                        } else if (absDy > step) {
                            moveLpSelection(0, if (dy > 0) 1 else -1)
                            startX = e.rawX
                            startY = e.rawY
                        } else if (lpRects.isNotEmpty()) {
                            val rx = e.rawX.toInt()
                            val ry = e.rawY.toInt()
                            val newIdx = lpRects.indexOfFirst { it.contains(rx, ry) }
                            if (newIdx != -1 && newIdx != lpSelectedIndex) {
                                lpSelectedIndex = newIdx
                                updateLongPressHighlight()
                            }
                        }
                    }

                    inputController.handleTouch(v as TextView, e)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (handledBySwipeUp) {
                        v.isPressed = false
                        v.isSelected = false
                        v.clearFocus()
                        mainHandler.removeCallbacks(longPressRunnable)
                        return@setOnTouchListener true
                    }

                    v.performClick()
                    v.isSelected = false
                    v.clearFocus()
                    mainHandler.removeCallbacks(longPressRunnable)

                    if (longPressTriggered) {
                        lpChars.getOrNull(lpSelectedIndex)?.let { ch ->
                            currentInputConnection?.commitText(ch, 1)
                        }
                        hideLongPressPopup()
                        v.isPressed = false
                        true
                    } else {
                        when (label) {
                            "⇧" -> {
                                v.isPressed = false
                                toggleShift()
                                true
                            }

                            "😊" -> {
                                v.isPressed = false
                                showEmojiPicker()
                                true
                            }

                            else -> {
                                inputController.handleTouch(v as TextView, e)
                                true
                            }
                        }
                    }
                }

                MotionEvent.ACTION_CANCEL -> {
                    handledBySwipeUp = false
                    mainHandler.removeCallbacks(longPressRunnable)

                    if (longPressTriggered) {
                        hideLongPressPopup()
                    }

                    v.isPressed = false
                    v.isSelected = false
                    v.clearFocus()
                    inputController.handleTouch(v as TextView, e)
                    true
                }

                else -> false
            }
        }
   }

    private fun applySpecialKeyColors(kv: KeyView, key: KeyConfig, spaceIndex: Int): Int {
        var nextSpaceIndex = spaceIndex

        if (key.label == " ") {
            val linked = KeyboardPrefs.isSpaceLinked(this)
            val c1 = KeyboardPrefs.getSpace1Bg(this)
            val c2 = if (linked) c1 else KeyboardPrefs.getSpace2Bg(this)
            kv.customBgColor = if (spaceIndex == 0) c1 else c2
            nextSpaceIndex++
        }

        if (key.label == "↵") {
            kv.customBgColor = KeyboardPrefs.getEnterBg(this)
            kv.setTextColor(KeyboardPrefs.getEnterIcon(this))
        }

        return nextSpaceIndex
    }
    /* ───────── INNER CLASSES ───────── */

    class CharSelectorAdapter(
        private val items: List<String>,
        private val onItemClick: (String) -> Unit
    ) : RecyclerView.Adapter<CharSelectorAdapter.ViewHolder>() {

        private var selectedPos = RecyclerView.NO_POSITION

        inner class ViewHolder(val button: Button) : RecyclerView.ViewHolder(button) {
            fun bind(char: String, isSelected: Boolean) {
                button.text = char
                button.setBackgroundColor(
                    if (isSelected) 0xFFFFCC80.toInt() else 0x00000000
                )

                button.setOnClickListener {
                    val old = selectedPos
                    val newPos = bindingAdapterPosition
                    if (newPos == RecyclerView.NO_POSITION) return@setOnClickListener

                    selectedPos = newPos
                    if (old != RecyclerView.NO_POSITION) notifyItemChanged(old)
                    notifyItemChanged(selectedPos)

                    onItemClick(char)
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val btn = Button(parent.context).apply {
                isAllCaps = false
                textSize = 18f
                setPadding(16, 16, 16, 16)
            }
            return ViewHolder(btn)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position], position == selectedPos)
        }

        override fun getItemCount(): Int = items.size
    }
}