package com.example.antarakeyboard.service

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.inputmethodservice.InputMethodService
import android.view.ContextThemeWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
import com.example.antarakeyboard.extensions.dp
import com.example.antarakeyboard.data.KeyboardPrefs
import com.example.antarakeyboard.data.PrefsManager
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
import com.example.antarakeyboard.model.KeyMarkers
import com.example.antarakeyboard.data.LongPressPresets
import android.widget.RadioButton
import android.widget.RadioGroup
import java.util.Locale


class MyKeyboardService : InputMethodService(), EdgeActionCallback {

    /* ───────── STATE ───────── */

    private var isShifted = false
    private var isDrawing = false
    private var lastBottomInsetPx: Int = 0

    private var currentKeyboardConfig: KeyboardConfig = defaultKeyboardLayout
    private var currentShape: KeyShape = KeyShape.HEX
    private var activeShape: KeyShape = KeyShape.HEX

    // Coroutine scope for the service lifecycle
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private val EDGE_GHOST_MARKER = KeyMarkers.EDGE_GHOST
    private val USER_EMPTY_MARKER = KeyMarkers.USER_EMPTY
    private val SPACE_LEFT_MARKER = KeyMarkers.SPACE_LEFT
    private val SPACE_RIGHT_MARKER = KeyMarkers.SPACE_RIGHT

    private lateinit var rootView: View
    private lateinit var keyboardContainer: LinearLayout
    private lateinit var overlayLayer: FrameLayout
    private lateinit var themedCtx: Context

    // Extracted managers
    private lateinit var deleteRestoreManager: DeleteRestoreManager
    private lateinit var edgeKeyManager: EdgeKeyManager
    private lateinit var longPressPopupManager: LongPressPopupManager
    private lateinit var edgeOverlayManager: EdgeOverlayManager
    private lateinit var hapticManager: HapticManager
    private lateinit var keyPreviewManager: KeyPreviewManager

    private val myDefaultNumericConfig: KeyboardConfig
        get() {
            val rowCount = KeyboardPrefs.getRowCount(this)
            return ensureStableSpaceMarkers(
                KeyboardPrefs.loadNumericLayoutWithGlobalBinds(this, rowCount)
            )
        }

    private val OVERLAP_RATIO = 0.18f

    private var lastIsDark: Boolean? = null
    private var targetKeyboardHeightPx: Int = 0

    lateinit var inputController: KeyInputController
    private var landscapeSpaceIndex = 0

    private var alphabetLayoutLower: KeyboardConfig? = null
    private var alphabetLayoutUpper: KeyboardConfig? = null

    private lateinit var emojiPickerManager: EmojiPickerManager

    private var languagePresetPopup: PopupWindow? = null

    private var leftSpaceHeld = false
    private var rightSpaceHeld = false
    private var dualSpaceHoldJob: Job? = null
    private var dualSpacePickerWasShown = false

    private val DUAL_SPACE_HOLD_MS = 4000L
    /* ───────── LIFECYCLE ───────── */
    //claude sync

    override fun onCreateInputView(): View {
        KeyboardPrefs.ensureDefaultLongPress(this)
        val isDark = PrefsManager.isDarkMode(this)

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



        // U onCreateInputView() ili onStartInputView()
        val useTheme = KeyboardPrefs.getBackgroundUseTheme(this)
        val bg = if (useTheme) {
            // Use custom theme defaults
            KeyboardPrefs.getThemeDefaultsForMode(this, lastIsDark == true).keyboardBg
        } else {
            KeyboardPrefs.getBackgroundColor(this)  // custom boja
        }

        rootView.setBackgroundColor(bg)
        overlayLayer.setBackgroundColor(bg)
        keyboardContainer.setBackgroundColor(bg)

        window?.window?.setBackgroundDrawable(ColorDrawable(bg))


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

        // Initialize managers
        deleteRestoreManager = DeleteRestoreManager(
            context = this,
            scope = serviceScope,
            inputConnectionProvider = { currentInputConnection }
        )

        edgeKeyManager = EdgeKeyManager(this)

        longPressPopupManager = LongPressPopupManager(
            context = this,
            overlayLayerProvider = { overlayLayer },
            themedCtxProvider = { themedCtx },
            inputConnectionProvider = { currentInputConnection },
            keyHeightProvider = { keyHeight() },
            isPortraitProvider = { isPortrait() },
            currentShapeProvider = { currentShape },
            isDarkModeProvider = { lastIsDark == true }
        )

        edgeOverlayManager = EdgeOverlayManager(
            context = this,
            overlayLayerProvider = { overlayLayer },
            keyboardContainerProvider = { keyboardContainer },
            themedCtxProvider = { themedCtx },
            isDarkModeProvider = { lastIsDark == true },
            landscapeKeySizePxProvider = { landscapeKeySizePx() },
            availableKeyboardWidthPxProvider = { availableKeyboardWidthPx() },
            computeRowSizingProvider = { count, availW ->
                val sizing = computeRowSizing(count, availW)
                EdgeOverlayManager.RowSizing(
                    keyW = sizing.keyW,
                    keyH = sizing.keyH,
                    gapPx = sizing.gapPx,
                    outerPadPx = sizing.outerPadPx,
                    overlapPx = sizing.overlapPx,
                    triOverlapX = sizing.triOverlapX,
                    triOverlapY = sizing.triOverlapY
                )
            },
            actionCallback = this
        )

        emojiPickerManager = EmojiPickerManager(
            context = this,
            overlayLayerProvider = { overlayLayer },
            scope = serviceScope,
            callback = object : EmojiPickerCallback {
                override fun onEmojiSelected(emoji: String) {
                    currentInputConnection?.commitText(emoji, 1)
                }

                override fun onBackspace() {
                    backspaceOnce()
                }

                override fun onSpace() {
                    currentInputConnection?.commitText(" ", 1)
                }

                override fun onClose() {
                    hideEmojiPopup()
                }
            }
        )

        hapticManager = HapticManager(this)
        hapticManager.setEnabled(KeyboardPrefs.isVibrationEnabled(this))
        keyPreviewManager = KeyPreviewManager(this) { overlayLayer }

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
                if (insetChanged) {
                    targetKeyboardHeightPx = computeTargetKeyboardHeight()
                    redrawKeyboard()
                } else {
                    syncOverlayHeightToContent()
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

        KeyboardPrefs.ensureDefaultLongPress(this)

        // Refresh vibration preference
        hapticManager.setEnabled(KeyboardPrefs.isVibrationEnabled(this))

        val isDarkNow = PrefsManager.isDarkMode(this)

        if (lastIsDark != null && lastIsDark != isDarkNow) {
            lastIsDark = isDarkNow
            recreateInputView()
            return
        }
        lastIsDark = isDarkNow

        // NOVO: Postavi background boju iz KeyboardPrefs
        val useTheme = KeyboardPrefs.getBackgroundUseTheme(this)
        val bg = if (useTheme) {
            // Use custom theme defaults
            KeyboardPrefs.getThemeDefaultsForMode(this, isDarkNow).keyboardBg
        } else {
            KeyboardPrefs.getBackgroundColor(this)
        }
        rootView.setBackgroundColor(bg)
        overlayLayer.setBackgroundColor(bg)
        keyboardContainer.setBackgroundColor(bg)
        window?.window?.setBackgroundDrawable(ColorDrawable(bg))

        // NOVO: Postavi side buttons boje prema temi
        val sideUseTheme = KeyboardPrefs.getSideButtonsUseThemeBg(this)
        if (sideUseTheme) {
            val sideTextColor = themeColor(themedCtx, R.attr.edgeIconText,
                if (isDarkNow) Color.WHITE else Color.BLACK)
            KeyboardPrefs.setSideButtonsColors(this, Color.TRANSPARENT, sideTextColor, true)
        }

        // NE upisivati theme boje u KeyboardPrefs ovdje.
        // Theme smije biti samo fallback kod crtanja, inače reset/start pregazi custom boje.
        //val keysAllSame = KeyboardPrefs.getKeysAllSameColor(this)
        //if (keysAllSame) {
        //    val keysTextColor = themeColor(themedCtx, R.attr.keyText,
        //        if (isDarkNow) Color.WHITE else Color.BLACK)
        //    val keyFill = themeColor(themedCtx, R.attr.keyFill,
        //        if (isDarkNow) 0xFF3E3E3E.toInt() else 0xFFE0E0E0.toInt())
        //   KeyboardPrefs.setKeysColors(this, keyFill, keysTextColor, true)
        //}

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

    override fun onDestroy() {
        super.onDestroy()

        // Cleanup popups to prevent memory leaks
        hideEmojiPopup()
        hideLongPressPopup()
        hideLanguagePresetPopup()

        // Cancel manager jobs
        deleteRestoreManager.resetState()

        // Cancel all coroutines
        serviceScope.cancel()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // Hide all popups on configuration change
        hideEmojiPopup()
        hideLongPressPopup()
        hideLanguagePresetPopup()

        // Recreate keyboard view to adapt to new configuration
        recreateInputView()
    }

    private fun resetTransientState() {
        isShifted = false

        deleteRestoreManager.resetState()
        hideEmojiPopup()
        hideLongPressPopup()
        hideLanguagePresetPopup()
        resetDualSpaceHoldState()
    }

    private fun recreateInputView() {
        setInputView(onCreateInputView())
    }

    /* ───────── HELPERS ───────── */
    private fun safeOverlayTop(
        requestedTop: Int,
        childHeight: Int
    ): Int {
        val minTop = 2.dp(this)

        val maxTop = (
                overlayLayer.height -
                        childHeight -
                        2.dp(this)
                ).coerceAtLeast(minTop)

        return requestedTop.coerceIn(minTop, maxTop)
    }


    private val serbianCyrillicDirectMap = mapOf(
        "a" to "а",
        "b" to "б",
        "c" to "ц",
        "d" to "д",
        "e" to "е",
        "f" to "ф",
        "g" to "г",
        "h" to "х",
        "i" to "и",
        "j" to "ј",
        "k" to "к",
        "l" to "л",
        "m" to "м",
        "n" to "н",
        "o" to "о",
        "p" to "п",
        "r" to "р",
        "s" to "с",
        "t" to "т",
        "u" to "у",
        "v" to "в",
        "z" to "з",

        "q" to "љ",
        "w" to "њ",
        "x" to "џ",
        "y" to "ј"
    )

    private val bulgarianCyrillicDirectMap = mapOf(
        "a" to "а",
        "b" to "б",
        "c" to "ц",
        "d" to "д",
        "e" to "е",
        "f" to "ф",
        "g" to "г",
        "h" to "х",
        "i" to "и",
        "j" to "й",
        "k" to "к",
        "l" to "л",
        "m" to "м",
        "n" to "н",
        "o" to "о",
        "p" to "п",
        "r" to "р",
        "s" to "с",
        "t" to "т",
        "u" to "у",
        "v" to "в",
        "z" to "з",

        // fallback za bugarska slova bez čistog latin para
        "q" to "я",
        "w" to "ш",
        "x" to "х",
        "y" to "ъ"
    )

    private val ukrainianCyrillicDirectMap = mapOf(
        "a" to "а",
        "b" to "б",
        "c" to "ц",
        "d" to "д",
        "e" to "е",
        "f" to "ф",
        "g" to "г",
        "h" to "х",
        "i" to "і",
        "j" to "й",
        "k" to "к",
        "l" to "л",
        "m" to "м",
        "n" to "н",
        "o" to "о",
        "p" to "п",
        "r" to "р",
        "s" to "с",
        "t" to "т",
        "u" to "у",
        "v" to "в",
        "z" to "з",

        // fallback za ukrajinska slova bez čistog latin para
        "q" to "я",
        "w" to "ш",
        "x" to "ь",
        "y" to "и"
    )

    private val macedonianCyrillicDirectMap = mapOf(
        "a" to "а",
        "b" to "б",
        "c" to "ц",
        "d" to "д",
        "e" to "е",
        "f" to "ф",
        "g" to "г",
        "h" to "х",
        "i" to "и",
        "j" to "ј",
        "k" to "к",
        "l" to "л",
        "m" to "м",
        "n" to "н",
        "o" to "о",
        "p" to "п",
        "r" to "р",
        "s" to "с",
        "t" to "т",
        "u" to "у",
        "v" to "в",
        "z" to "з",

        // fallback za makedonska slova bez čistog latin para
        "q" to "љ",
        "w" to "њ",
        "x" to "џ",
        "y" to "ѕ"
    )

    private val russianCyrillicDirectMap = mapOf(
        "a" to "а",
        "b" to "б",
        "c" to "ц",
        "d" to "д",
        "e" to "е",
        "f" to "ф",
        "g" to "г",
        "h" to "х",
        "i" to "и",
        "j" to "й",
        "k" to "к",
        "l" to "л",
        "m" to "м",
        "n" to "н",
        "o" to "о",
        "p" to "п",
        "r" to "р",
        "s" to "с",
        "t" to "т",
        "u" to "у",
        "v" to "в",
        "z" to "з",

        // fallback za ruska slova bez čistog latin para
        "q" to "я",
        "w" to "ш",
        "x" to "ь",
        "y" to "ы"
    )


    private fun directMapForSelectedPreset(): Map<String, String>? {
        return when (KeyboardPrefs.getSelectedLongPressPreset(this)) {
            LongPressPresets.PRESET_SERBIAN_CYRILLIC -> serbianCyrillicDirectMap
            LongPressPresets.PRESET_BULGARIAN_CYRILLIC -> bulgarianCyrillicDirectMap
            LongPressPresets.PRESET_RUSSIAN_CYRILLIC -> russianCyrillicDirectMap
            LongPressPresets.PRESET_UKRAINIAN_CYRILLIC -> ukrainianCyrillicDirectMap
            LongPressPresets.PRESET_MACEDONIAN_CYRILLIC -> macedonianCyrillicDirectMap
            else -> null
        }
    }

    private fun mapForSelectedScript(text: String): String {
        if (text.length != 1) return text

        val map = directMapForSelectedPreset() ?: return text

        val lower = text.lowercase(Locale.ROOT)
        val mapped = map[lower] ?: return text

        val isUpper = text == text.uppercase(Locale.ROOT) &&
                text != text.lowercase(Locale.ROOT)

        return if (isUpper) {
            mapped.uppercase(Locale.ROOT)
        } else {
            mapped
        }
    }



    private fun isDualSpaceKey(key: KeyConfig): Boolean {
        return key.label == " " && hasSpaceMarker(key)
    }

    private fun handleDualSpaceDown(key: KeyConfig) {
        if (!isDualSpaceKey(key)) return

        if (isLeftSpace(key)) {
            leftSpaceHeld = true
        }

        if (isRightSpace(key)) {
            rightSpaceHeld = true
        }

        if (
            leftSpaceHeld &&
            rightSpaceHeld &&
            dualSpaceHoldJob == null &&
            languagePresetPopup == null
        ) {
            dualSpacePickerWasShown = false

            dualSpaceHoldJob = serviceScope.launch {
                delay(DUAL_SPACE_HOLD_MS)
                dualSpaceHoldJob = null

                if (leftSpaceHeld && rightSpaceHeld) {
                    dualSpacePickerWasShown = true
                    showLanguagePresetPopup()
                }
            }
        }
    }

    private fun handleDualSpaceUpOrCancel(key: KeyConfig): Boolean {
        if (!isDualSpaceKey(key)) return false

        val shouldConsumeSpace =
            dualSpacePickerWasShown || languagePresetPopup != null

        if (isLeftSpace(key)) {
            leftSpaceHeld = false
        }

        if (isRightSpace(key)) {
            rightSpaceHeld = false
        }

        if (!(leftSpaceHeld && rightSpaceHeld)) {
            cancelDualSpaceHoldTimer()
        }

        if (!leftSpaceHeld && !rightSpaceHeld && languagePresetPopup == null) {
            dualSpacePickerWasShown = false
        }

        return shouldConsumeSpace
    }

    private fun cancelDualSpaceHoldTimer() {
        dualSpaceHoldJob?.cancel()
        dualSpaceHoldJob = null
    }

    private fun resetDualSpaceHoldState() {
        leftSpaceHeld = false
        rightSpaceHeld = false
        dualSpacePickerWasShown = false
        cancelDualSpaceHoldTimer()
    }
    private fun ensureStableSpaceMarkers(cfg: KeyboardConfig): KeyboardConfig {
        var spaceCount = 0

        fun fixKey(key: KeyConfig): KeyConfig {
            if (key.label != " ") return key

            val thisSpaceIndex = spaceCount
            spaceCount++

            if (hasSpaceMarker(key)) return key

            val marker = if (thisSpaceIndex == 0) {
                KeyMarkers.SPACE_LEFT
            } else {
                KeyMarkers.SPACE_RIGHT
            }

            return key.copy(
                longPressBindings = key.longPressBindings.toMutableList().apply {
                    add(marker)
                }
            )
        }

        return cfg.copy(
            rows = cfg.rows.map { row ->
                row.copy(
                    keys = row.keys.map(::fixKey).toMutableList()
                )
            }.toMutableList(),

            specialLeft = cfg.specialLeft.map(::fixKey).toMutableList(),
            specialRight = cfg.specialRight.map(::fixKey).toMutableList()
        )
    }
    private fun isLeftSpace(key: KeyConfig): Boolean {
        return key.label == " " &&
                key.longPressBindings.contains(KeyMarkers.SPACE_LEFT)
    }

    private fun isRightSpace(key: KeyConfig): Boolean {
        return key.label == " " &&
                key.longPressBindings.contains(KeyMarkers.SPACE_RIGHT)
    }

    private fun hasSpaceMarker(key: KeyConfig): Boolean {
        return isLeftSpace(key) || isRightSpace(key)
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
    private data class KeyPos(
        val row: Int,
        val col: Int
    )

    private var selectedPos: KeyPos? = null

    private fun activeAlphabetBaseLayout(): KeyboardConfig {
        val rows = KeyboardPrefs.getRowCount(this)
        return ensureStableSpaceMarkers(
            KeyboardPrefs.loadAlphabetLayoutWithGlobalBinds(this, rows)
        )
    }


    private fun isAlphabetLayoutActive(): Boolean {
        return currentKeyboardConfig.rows.any { row ->
            row.keys.any { key ->
                key.label.length == 1 && key.label[0].isLetter()
            }
        }
    }

    private fun hideEmojiPopup() {
        emojiPickerManager.hide()
    }

    private fun hideLanguagePresetPopup() {
        languagePresetPopup?.dismiss()
        languagePresetPopup = null
    }

    private fun showLanguagePresetPopup() {
        hideLongPressPopup()
        hideEmojiPopup()
        hideLanguagePresetPopup()
        cancelDualSpaceHoldTimer()

        dualSpacePickerWasShown = true

        val popupWidth = (resources.displayMetrics.widthPixels * 0.86f).toInt()
            .coerceAtLeast(280.dp(this))

        val popupBg = themeColor(
            themedCtx,
            R.attr.keyFill,
            if (lastIsDark == true) 0xFF2A2A2A.toInt() else 0xFFFFFFFF.toInt()
        )

        val popupText = themeColor(
            themedCtx,
            R.attr.keyText,
            if (lastIsDark == true) Color.WHITE else Color.BLACK
        )

        val selectedPreset = KeyboardPrefs.getSelectedLongPressPreset(this)

        val root = LinearLayout(themedCtx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp(this), 18.dp(this), 20.dp(this), 14.dp(this))
            setBackgroundColor(popupBg)
        }

        val title = TextView(themedCtx).apply {
            text = "Odaberi pismo"
            textSize = 18f
            setTextColor(popupText)
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(0, 0, 0, 14.dp(this))
        }

        val radioGroup = RadioGroup(themedCtx).apply {
            orientation = RadioGroup.VERTICAL
        }

        fun makeRadioButton(
            titleText: String,
            subtitleText: String
        ): RadioButton {
            return RadioButton(themedCtx).apply {
                text = "$titleText\n$subtitleText"
                textSize = 15f
                setTextColor(popupText)
                includeFontPadding = true
                setPadding(0, 8.dp(this), 0, 8.dp(this))
                isClickable = true
                isFocusable = false
            }
        }

        val latinId = View.generateViewId()
        val serbianCyrId = View.generateViewId()
        val bulgarianCyrId = View.generateViewId()
        val russianCyrId = View.generateViewId()
        val ukrainianCyrId = View.generateViewId()
        val macedonianCyrId = View.generateViewId()

        val latinRadio = makeRadioButton(
            titleText = "Latinica",
            subtitleText = "a, b, c + á, č, ć, š, ž..."
        ).apply {
            id = latinId
        }

        val serbianCyrRadio = makeRadioButton(
            titleText = "Srpska ćirilica",
            subtitleText = "а, б, в, љ, њ, ђ, ћ..."
        ).apply {
            id = serbianCyrId
        }

        val bulgarianCyrRadio = makeRadioButton(
            titleText = "Bugarska ćirilica",
            subtitleText = "а, б, в, ж, ч, ш, щ, ъ..."
        ).apply {
            id = bulgarianCyrId
        }

        val russianCyrRadio = makeRadioButton(
            titleText = "Ruska ćirilica",
            subtitleText = "а, б, в, ж, ч, ш, щ, ы, э..."
        ).apply {
            id = russianCyrId
        }
        val ukrainianCyrRadio = makeRadioButton(
            titleText = "Ukrajinska ćirilica",
            subtitleText = "а, б, в, ґ, є, і, ї..."
        ).apply {
            id = ukrainianCyrId
        }

        val macedonianCyrRadio = makeRadioButton(
            titleText = "Makedonska ćirilica",
            subtitleText = "а, б, в, ѓ, ќ, љ, њ, џ..."
        ).apply {
            id = macedonianCyrId
        }

        radioGroup.addView(
            latinRadio,
            RadioGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        radioGroup.addView(
            serbianCyrRadio,
            RadioGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        radioGroup.addView(
            bulgarianCyrRadio,
            RadioGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        radioGroup.addView(
            russianCyrRadio,
            RadioGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        radioGroup.addView(
            ukrainianCyrRadio,
            RadioGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        radioGroup.addView(
            macedonianCyrRadio,
            RadioGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val initialCheckedId = when (selectedPreset) {
            LongPressPresets.PRESET_SERBIAN_CYRILLIC -> serbianCyrId
            LongPressPresets.PRESET_BULGARIAN_CYRILLIC -> bulgarianCyrId
            LongPressPresets.PRESET_RUSSIAN_CYRILLIC -> russianCyrId
            LongPressPresets.PRESET_UKRAINIAN_CYRILLIC -> ukrainianCyrId
            LongPressPresets.PRESET_MACEDONIAN_CYRILLIC -> macedonianCyrId
            else -> latinId
        }

        radioGroup.check(initialCheckedId)

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val presetId = when (checkedId) {
                serbianCyrId -> LongPressPresets.PRESET_SERBIAN_CYRILLIC
                bulgarianCyrId -> LongPressPresets.PRESET_BULGARIAN_CYRILLIC
                russianCyrId -> LongPressPresets.PRESET_RUSSIAN_CYRILLIC
                ukrainianCyrId -> LongPressPresets.PRESET_UKRAINIAN_CYRILLIC
                macedonianCyrId -> LongPressPresets.PRESET_MACEDONIAN_CYRILLIC
                else -> LongPressPresets.PRESET_LATIN
            }

            applyLongPressPresetAndRefresh(presetId)
        }

        val closeBtn = TextView(themedCtx).apply {
            text = "Zatvori"
            textSize = 14f
            setTextColor(popupText)
            gravity = Gravity.CENTER
            setPadding(8.dp(this), 16.dp(this), 8.dp(this), 0)
            isClickable = true
            isFocusable = false
            setOnClickListener {
                hideLanguagePresetPopup()
            }
        }

        root.addView(
            title,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        val scroll = ScrollView(themedCtx).apply {
            isFillViewport = false

            addView(
                radioGroup,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.heightPixels * 0.46f).toInt()
            )
        )

        root.addView(
            closeBtn,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val popup = PopupWindow(
            root,
            popupWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            isFocusable = true
            isClippingEnabled = false
            elevation = 12.dp(this@MyKeyboardService).toFloat()
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setOnDismissListener {
                languagePresetPopup = null

                if (!leftSpaceHeld && !rightSpaceHeld) {
                    dualSpacePickerWasShown = false
                }
            }
        }

        languagePresetPopup = popup

        /*
         * Gravity.CENTER je sigurniji od ručnog x/y računanja.
         * U IME-u ovo je najstabilniji modal bez rušenja.
         */
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels

        val rootLoc = IntArray(2)
        rootView.getLocationOnScreen(rootLoc)

        val desiredScreenX = ((screenW - popupWidth) / 2).coerceAtLeast(8.dp(this))

// Ovo je pozicija crvenog kvadrata sa screenshota.
// Smanji na 0.08f ako želiš još više gore.
// Povećaj na 0.14f ako želiš malo niže.
        val desiredScreenY = (screenH * 0.105f).toInt()
            .coerceAtLeast(54.dp(this))

// showAtLocation kod IME-a radi relativno prema rootView prozoru,
// zato screen Y pretvaramo u lokalni Y.
        val localX = desiredScreenX
        val localY = desiredScreenY - rootLoc[1]

        popup.showAtLocation(
            rootView,
            Gravity.NO_GRAVITY,
            localX,
            localY
        )
    }

    private fun applyLongPressPresetAndRefresh(presetId: String) {
        KeyboardPrefs.applyLongPressPreset(this, presetId)

        val baseCfg = activeAlphabetBaseLayout()
        alphabetLayoutLower = baseCfg
        alphabetLayoutUpper = makeUppercaseConfig(baseCfg)

        currentKeyboardConfig = applyEdgeKeys(
            if (isShifted) {
                alphabetLayoutUpper ?: baseCfg
            } else {
                alphabetLayoutLower ?: baseCfg
            }
        )

        resetDualSpaceHoldState()
        hideLanguagePresetPopup()

        if (isDrawing) {
            serviceScope.launch {
                delay(60L)
                redrawKeyboard()
            }
        } else {
            redrawKeyboard()
        }
    }


    private fun showEmojiPicker() {
        hideLongPressPopup()
        hideEmojiPopup()

        val keyboardHeight = computeTargetKeyboardHeight()
        emojiPickerManager.show(keyboardHeight)
    }

    private fun landscapeKeyGapPx(): Int = when (landscapeShape()) {
        KeyShape.TRIANGLE -> 0.dp(this)
        KeyShape.CIRCLE -> 2.dp(this)
        KeyShape.CUBE -> 2.dp(this)
        else -> 2.dp(this)
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

    private fun computeTargetKeyboardHeight(): Int {
        val screenH = resources.displayMetrics.heightPixels
        val savedRowCount = KeyboardPrefs.getRowCount(this)

        return if (isPortrait()) {
            (screenH * 0.36f).roundToInt().coerceAtLeast(230.dp(this))
        } else {
            val ratio = when (savedRowCount) {
                3 -> 0.28f
                4 -> 0.38f
                5 -> 0.52f
                else -> 0.36f
            }

            val minH = when (savedRowCount) {
                3 -> 120.dp(this)
                4 -> 160.dp(this)
                5 -> 175.dp(this)
                else -> 120.dp(this)
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
            .coerceAtLeast(120.dp(this))

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
                        KeyShape.HEX_HALF_RIGHT -> (usableForKeys / 3.15f).toInt().coerceAtLeast(52.dp(this))

                        KeyShape.TRIANGLE -> (usableForKeys / 3.35f).toInt().coerceAtLeast(48.dp(this))
                        KeyShape.CIRCLE -> (usableForKeys / 3.30f).toInt().coerceAtLeast(50.dp(this))
                        KeyShape.CUBE -> (usableForKeys / 3.30f).toInt().coerceAtLeast(50.dp(this))
                    }
                }

                4 -> {
                    return when (activeShape) {
                        KeyShape.HEX,
                        KeyShape.HEX_TALL,
                        KeyShape.HEX_HALF_LEFT,
                        KeyShape.HEX_HALF_RIGHT -> (usableForKeys / 4.05f).toInt().coerceAtLeast(44.dp(this))

                        KeyShape.TRIANGLE -> (usableForKeys / 4.25f).toInt().coerceAtLeast(42.dp(this))
                        KeyShape.CIRCLE -> (usableForKeys / 4.20f).toInt().coerceAtLeast(43.dp(this))
                        KeyShape.CUBE -> (usableForKeys / 4.20f).toInt().coerceAtLeast(43.dp(this))
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
            if (isLandscape()) 28.dp(this) else 36.dp(this)
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
        return (base - overlayLayer.paddingLeft - overlayLayer.paddingRight).coerceAtLeast(200.dp(this))
    }

    private fun syncOverlayHeightToContent() {
        if (!::overlayLayer.isInitialized || !::keyboardContainer.isInitialized) return
        if (overlayLayer.width <= 0 || keyboardContainer.childCount == 0) return

        /*
         * Bitno:
         * keyboardContainer.height može biti odrezan trenutačnom visinom overlayja.
         * Zato ga ponovno mjerimo s UNSPECIFIED visinom da dobijemo stvarnu
         * visinu svih redova.
         */
        val contentWidth = (
                overlayLayer.width -
                        overlayLayer.paddingLeft -
                        overlayLayer.paddingRight
                ).coerceAtLeast(200.dp(this))

        keyboardContainer.measure(
            View.MeasureSpec.makeMeasureSpec(
                contentWidth,
                View.MeasureSpec.EXACTLY
            ),
            View.MeasureSpec.makeMeasureSpec(
                0,
                View.MeasureSpec.UNSPECIFIED
            )
        )

        val contentH = keyboardContainer.measuredHeight
        if (contentH <= 0) return

        /*
         * paddingBottom već sadrži navigation/system inset.
         * Ne dodajemo umjetni minTarget ni extraBottomSafety jer su oni
         * stvarali prazan prostor gore.
         */
        val desiredHeight =
            contentH +
                    overlayLayer.paddingTop +
                    overlayLayer.paddingBottom

        /*
         * Samo zaštita od potpuno pogrešnog mjerenja.
         * Ovo više nije normalni limit tipkovnice.
         */
        val screenH = resources.displayMetrics.heightPixels

        val screenLimit = if (isLandscape()) {
            (screenH * 0.85f).roundToInt()
        } else {
            (screenH * 0.70f).roundToInt()
        }.coerceAtLeast(120.dp(this))

        val newHeight = desiredHeight.coerceIn(
            1.dp(this),
            screenLimit
        )

        // Ukloni eventualni stari minimum koji je ostao od ranije.
        overlayLayer.minimumHeight = 0

        val lp = overlayLayer.layoutParams ?: ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            newHeight
        )

        if (lp.height != newHeight) {
            lp.height = newHeight
            overlayLayer.layoutParams = lp
            overlayLayer.requestLayout()
        }
    }

    /* ───────── DELETE / RESTORE LOGIC (delegated to DeleteRestoreManager) ───────── */

    fun startSwipeDelete(absDx: Float) {
        deleteRestoreManager.startSwipeDelete(absDx)
    }

    fun updateSwipeDelete(absDx: Float) {
        deleteRestoreManager.updateSwipeDelete(absDx)
    }

    fun stopSwipeDelete() {
        deleteRestoreManager.stopSwipeDelete()
    }

    fun startSwipeRestore(absDx: Float) {
        deleteRestoreManager.startSwipeRestore(absDx)
    }

    fun updateSwipeRestore(absDx: Float) {
        deleteRestoreManager.updateSwipeRestore(absDx)
    }

    fun stopSwipeRestore() {
        deleteRestoreManager.stopSwipeRestore()
    }

    fun startBackspaceHold() {
        deleteRestoreManager.startBackspaceHold()
    }

    fun commitExactText(text: String) {
        deleteRestoreManager.clearRestoreBuffer()
        currentInputConnection?.commitText(text, 1)
    }

    override fun isBackspaceHoldRunning(): Boolean {
        return deleteRestoreManager.isBackspaceHoldRunning()
    }

    fun stopBackspaceHold() {
        deleteRestoreManager.stopBackspaceHold()
    }

    /* ───────── INPUT API for Controller ───────── */

    fun scheduleBackspaceHold() {
        deleteRestoreManager.scheduleBackspaceHold()
    }

    fun cancelPendingBackspaceHold() {
        deleteRestoreManager.cancelPendingBackspaceHold()
    }

    fun backspaceOnce() {
        hapticManager.onSpecialKey()
        deleteRestoreManager.backspaceOnce()
    }

    fun sendEnter() {
        hapticManager.onSpecialKey()
        currentInputConnection?.sendKeyEvent(
            KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)
        )
    }

    fun toggleShift() {
        hapticManager.onSpecialKey()
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
            deleteRestoreManager.clearRestoreBuffer()
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

        val baseOut = if (text.length == 1 && text[0].isLetter()) {
            if (isShifted) {
                text.uppercase(Locale.ROOT)
            } else {
                text.lowercase(Locale.ROOT)
            }
        } else {
            text
        }

        val out = mapForSelectedScript(baseOut)

        currentInputConnection?.commitText(out, 1)
    }



    /* ───────── EDGE KEYS ───────── */

    private fun applyEdgeKeys(cfg: KeyboardConfig): KeyboardConfig {
        return edgeKeyManager.applyEdgeKeys(cfg)
    }

    /* ───────── LONG PRESS POPUP (delegated to LongPressPopupManager) ───────── */

    private fun showLongPressPopup(anchor: View, chars: List<String>) {
        longPressPopupManager.showLongPressPopup(anchor, chars)
    }

    private fun updateLongPressHighlight() {
        // No-op - handled internally by LongPressPopupManager
    }

    private fun moveLpSelection(dx: Int, dy: Int) {
        longPressPopupManager.moveLpSelection(dx, dy)
    }

    private fun hideLongPressPopup() {
        longPressPopupManager.hideLongPressPopup()
    }

    // Helper properties for accessing popup state from manager
    private val lpChars: List<String>
        get() = longPressPopupManager.getLpChars()

    private val lpRects: List<android.graphics.Rect>
        get() = longPressPopupManager.getLpRects()

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

        /*
 * Portrait TRIANGLE:
 * sve tipke dobivaju veličinu referentnog reda sa 6 tipki.
 *
 * Red sa 7 tipki koristi horizontalno preklapanje kako bi
 * stao u istu dostupnu širinu bez smanjivanja tipki.
 */
        if (!isLandscape() && layoutShape == KeyShape.TRIANGLE) {
            val usableW = when (savedRowCount) {
                // Ostavljamo malo sigurnog prostora uz lijevi i desni rub
                3 -> (availW - 20.dp(this)).coerceAtLeast(240.dp(this))

                // Postojeći dobar 4-row i 5-row prikaz
                else -> (availW * 0.98f).toInt()
            }

            val referenceColumns = when (savedRowCount) {
                // 3-row ima 11–12 tipki, zato mu trebaju manji trokuti
                3 -> 8.4f

                // Postojeći 4-row i 5-row sizing
                else -> 6f
            }

            val minTriangleWidth = when (savedRowCount) {
                3 -> 30.dp(this)
                else -> 40.dp(this)
            }

            val keyW = (usableW / referenceColumns)
                .toInt()
                .coerceAtLeast(minTriangleWidth)

            /*
             * Za 7 tipki:
             * 7 * keyW je preširoko, pa višak rasporedimo kroz
             * šest razmaka između tipki.
             */
            // Mali stalni overlap vrijedi i za redove sa 6 tipki.
            val baseOverlapX = (keyW * 0.22f).toInt()

            // Dodatni overlap potreban da red sa 7 tipki stane.
            val fitOverlapX = if (count > 1) {
                ((count * keyW - usableW) / (count - 1))
                    .coerceAtLeast(0)
            } else {
                0
            }

            val overlapX = when (savedRowCount) {
                3 -> {
                    /*
                     * Ne oduzimamo više 2.dp(this), jer je to proširivalo cijeli red
                     * i izbacivalo krajnje tipke izvan ekrana.
                     */
                    maxOf(baseOverlapX, fitOverlapX)
                }

                else -> {
                    maxOf(baseOverlapX, fitOverlapX) + 2.dp(this)
                }
            }

            val usedW =
                count * keyW -
                        (count - 1).coerceAtLeast(0) * overlapX

            val outerPad = ((availW - usedW) / 2)
                .coerceAtLeast(0)

            return RowSizing(
                keyW = keyW,
                keyH = (keyW * 0.92f).toInt(),
                gapPx = 0,
                outerPadPx = outerPad,
                overlapPx = 0,
                triOverlapX = overlapX,
                triOverlapY = 0
            )
        }

        val gap = when (layoutShape) {
            KeyShape.HEX,
            KeyShape.HEX_TALL,
            KeyShape.HEX_HALF_LEFT,
            KeyShape.HEX_HALF_RIGHT -> {
                when {
                    isLandscape() -> 0.dp(this)
                    savedRowCount == 3 -> 0.dp(this)
                    savedRowCount == 4 -> 0.dp(this)
                    else -> 1.dp(this)
                }
            }

            KeyShape.TRIANGLE -> 0.dp(this)
            KeyShape.CIRCLE -> {
                when {
                    isLandscape() -> 2.dp(this)
                    savedRowCount == 3 -> 2.dp(this)
                    else -> 4.dp(this)
                }
            }
            KeyShape.CUBE -> {
                when {
                    isLandscape() -> 2.dp(this)

                    // 3-row portrait: manje praznog prostora,
                    // pa same tipke mogu biti veće
                    savedRowCount == 3 -> 2.dp(this)

                    else -> 4.dp(this)
                }
            }
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
            KeyShape.CIRCLE -> {
                when {
                    isLandscape() -> availW
                    savedRowCount == 3 -> (availW * 0.98f).toInt()
                    else -> (availW * 0.92f).toInt()
                }
            }
            KeyShape.CUBE -> {
                when {
                    isLandscape() -> availW

                    // 3-row portrait: veće tipke
                    savedRowCount == 3 -> (availW * 0.98f).toInt()

                    else -> (availW * 0.92f).toInt()
                }
            }
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
            isLandscape() && savedRowCount == 3 -> 24.dp(this)
            isLandscape() -> 40.dp(this)
            savedRowCount == 3 -> 20.dp(this)
            savedRowCount == 4 -> 24.dp(this)
            else -> 36.dp(this)
        }

        val baseKeyW = ((effectiveAvailW - (targetColumns - 1) * gap) / targetColumns)
            .toInt()
            .coerceAtLeast(minKeyWidth)

        val keyW = when {
            // red sa 6 tipki koristi istu veličinu gumba kao red sa 7 tipki
            // Portrait CIRCLE, CUBE i TRIANGLE:
            !isLandscape() &&
                    count == 6 &&
                    (
                            layoutShape == KeyShape.CIRCLE ||
                                    layoutShape == KeyShape.CUBE
                            ) -> {
                ((effectiveAvailW - 6 * gap) / 7f)
                    .toInt()
                    .coerceAtLeast(36.dp(this))
            }

            layoutShape == KeyShape.HEX &&
                    isLandscape() &&
                    savedRowCount == 3 -> baseKeyW

            layoutShape == KeyShape.HEX &&
                    isLandscape() &&
                    count <= 6 -> {
                (baseKeyW * 1.06f).toInt()
            }

            layoutShape == KeyShape.HEX &&
                    isLandscape() &&
                    count >= 7 -> {
                baseKeyW
            }

            savedRowCount == 3 -> {
                ((effectiveAvailW - (count - 1) * gap) / count.toFloat())
                    .toInt()
                    .coerceAtLeast(24.dp(this))
            }

            savedRowCount == 4 -> {
                ((effectiveAvailW - (count - 1) * gap) / count.toFloat())
                    .toInt()
                    .coerceAtLeast(22.dp(this))
            }

            count == 7 -> {
                ((effectiveAvailW - (count - 1) * gap) / count.toFloat())
                    .toInt()
                    .coerceAtLeast(36.dp(this))
            }

            count == 6 -> baseKeyW

            else -> {
                ((effectiveAvailW - (count - 1) * gap) / max(1, count).toFloat())
                    .toInt()
                    .coerceAtLeast(36.dp(this))
            }
        }

        val used = count * keyW + (count - 1) * gap

        val outer = when {
            isLandscape() && layoutShape == KeyShape.HEX -> {
                ((availW - used) / 2).coerceAtLeast(4.dp(this))
            }

            isLandscape() -> {
                ((availW - used) / 2).coerceAtLeast(6.dp(this))
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

        val keyH = when {
            // 3-row portrait CUBE:
            // širina ostaje ista, povećavamo samo visinu za 25%
            !isLandscape() &&
                    savedRowCount == 3 &&
                    layoutShape == KeyShape.CUBE -> {
                // Visina je 25% veća od širine gumba,
                // a ne 30% veća od ukupno izračunate visine reda.
                (keyW * 1.3f).toInt()
            }

            // postojeći 3-row portrait HEX
            !isLandscape() &&
                    savedRowCount == 3 &&
                    (
                            layoutShape == KeyShape.HEX ||
                                    layoutShape == KeyShape.HEX_HALF_LEFT ||
                                    layoutShape == KeyShape.HEX_HALF_RIGHT
                            ) -> {
                (keyW * 1.90f).toInt().coerceAtLeast(42.dp(this))
            }

            else -> rawKeyH
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

    private fun redrawKeyboard() {
        if (!::keyboardContainer.isInitialized) return
        if (!::overlayLayer.isInitialized) return
        if (isDrawing) return

        isDrawing = true

        serviceScope.launch {
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
                return@launch
            }


            var spaceIndex = 0

            fun buildRow(keys: List<KeyConfig>, containerRowIndex: Int) {
                val rowKeys = keys.filterNot { key ->
                    key.longPressBindings.contains(KeyMarkers.EDGE_GHOST)
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
                            savedRowCount == 3 -> 3.dp(this@MyKeyboardService)
                            else -> 1.dp(this@MyKeyboardService)
                        }
                    }

                    else -> if (isLandscape()) 1.dp(this@MyKeyboardService) else 2.dp(this@MyKeyboardService)
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
                        (sizing.outerPadPx + honeycombShift - 6.dp(this@MyKeyboardService)).coerceAtLeast(0)

                    savedRowCount == 4 ->
                        (sizing.outerPadPx - 6.dp(this@MyKeyboardService)).coerceAtLeast(0)

                    savedRowCount == 3 && isShiftedRow ->
                        (sizing.outerPadPx + honeycombShift - 6.dp(this@MyKeyboardService)).coerceAtLeast(0)

                    savedRowCount == 3 ->
                        (sizing.outerPadPx - 12.dp(this@MyKeyboardService)).coerceAtLeast(0)

                    isShiftedRow ->
                        sizing.outerPadPx + honeycombShift

                    else ->
                        sizing.outerPadPx
                }

                val rightPad = when {
                    savedRowCount == 4 && shouldHoneycomb && !isShiftedRow ->
                        sizing.outerPadPx + honeycombShift + 2.dp(this@MyKeyboardService)

                    savedRowCount == 4 ->
                        sizing.outerPadPx + 2.dp(this@MyKeyboardService)

                    savedRowCount == 3 && shouldHoneycomb && !isShiftedRow ->
                        (sizing.outerPadPx + honeycombShift - 10.dp(this@MyKeyboardService)).coerceAtLeast(0)

                    savedRowCount == 3 ->
                        (sizing.outerPadPx - 12.dp(this@MyKeyboardService)).coerceAtLeast(0)

                    shouldHoneycomb && !isShiftedRow ->
                        sizing.outerPadPx + honeycombShift

                    else ->
                        sizing.outerPadPx
                }

                val shapeRowTranslationX = if (!isLandscape()) {
                    when {

                        // 5-row TRIANGLE
                        savedRowCount == 5 &&
                                layoutShape == KeyShape.TRIANGLE -> {
                            when (containerRowIndex) {
                                0, 2, 4 -> -(14.dp(this@MyKeyboardService)) // 1., 3. i 5. red lijevo
                                1, 3 -> 7.dp(this@MyKeyboardService)     // 2. i 4. red desno
                                else -> 0
                            }
                        }

                        // 4-row TRIANGLE
                        savedRowCount == 4 &&
                                layoutShape == KeyShape.TRIANGLE -> {
                            if (containerRowIndex % 2 == 0) {
                                -(9.dp(this@MyKeyboardService))   // 1. i 3. red ulijevo
                            } else {
                                20.dp(this@MyKeyboardService)    // 2. i 4. red udesno
                            }
                        }

                        // 3-row TRIANGLE
                        savedRowCount == 3 &&
                                layoutShape == KeyShape.TRIANGLE -> {
                            when (containerRowIndex) {
                                0, 2 -> -(4.dp(this@MyKeyboardService))   // 1. i 3. red ulijevo
                                1 -> 18.dp(this@MyKeyboardService)       // 2. red udesno
                                else -> 0
                            }
                        }

                        // 4-row CUBE / CIRCLE
                        savedRowCount == 4 &&
                                (layoutShape == KeyShape.CUBE || layoutShape == KeyShape.CIRCLE) -> {
                            if (containerRowIndex % 2 == 0) {
                                -(5.dp(this@MyKeyboardService))
                            } else {
                                10.dp(this@MyKeyboardService)
                            }
                        }

                        // 3-row CUBE / CIRCLE
                        savedRowCount == 3 &&
                                (layoutShape == KeyShape.CUBE || layoutShape == KeyShape.CIRCLE) -> {
                            if (containerRowIndex % 2 == 0) {
                                -(5.dp(this@MyKeyboardService))
                            } else {
                                10.dp(this@MyKeyboardService)
                            }
                        }

                        else -> 0
                    }
                } else {
                    0
                }

                val row = LinearLayout(this@MyKeyboardService).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.START
                    setPadding(leftPad, vPad, rightPad, vPad)
                    clipToPadding = false
                    clipChildren = false

                    translationX = shapeRowTranslationX.toFloat()
                }

                rowKeys.forEachIndexed { i, key ->
                    val kv = createKey(key)

                    if (kv.shape == KeyShape.TRIANGLE) {
                        kv.triangleFlipped = ((containerRowIndex + i) % 2 == 1)
                    }
                    spaceIndex = applySpecialKeyColors(kv, key, spaceIndex)

                    val lp = LinearLayout.LayoutParams(
                        sizing.keyW,
                        sizing.keyH
                    ).apply {
                        if (i > 0) {
                            leftMargin = sizing.gapPx - sizing.triOverlapX
                        }
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

                        KeyShape.TRIANGLE -> {
                            if (isLandscape()) {
                                0
                            } else {
                                when (savedRowCount) {
                                    3 -> 1.dp(this@MyKeyboardService)    // 3-row ostaje kakav je sada
                                    4 -> -(12.dp(this@MyKeyboardService))   // 4-row stisni redove
                                    5 -> -(12.dp(this@MyKeyboardService))   // 5-row još malo jače stisni
                                    else -> 1.dp(this@MyKeyboardService)
                                }
                            }
                        }

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

    /* ───────── EDGE OVERLAY (delegated to EdgeOverlayManager) ───────── */

    private fun drawLandscapeSideSlots() {
        edgeOverlayManager.drawLandscapeSideSlots()
    }

    private fun clearEdgeSlots() {
        edgeOverlayManager.clearEdgeSlots()
    }

    private fun drawEdgeSlots() {
        edgeOverlayManager.drawEdgeSlots()
    }

    /* ───────── EdgeActionCallback Implementation ───────── */

    override fun onToggleShift() {
        toggleShift()
    }

    override fun onBackspaceOnce() {
        backspaceOnce()
    }

    override fun onSendEnter() {
        sendEnter()
    }

    override fun onCommitSpace() {
        currentInputConnection?.commitText(" ", 1)
    }

    override fun onCommitChar(char: String) {
        currentInputConnection?.commitText(char, 1)
    }

    override fun onShowEmojiPicker() {
        showEmojiPicker()
    }

    override fun onScheduleBackspaceHold() {
        scheduleBackspaceHold()
    }

    override fun onCancelPendingBackspaceHold() {
        cancelPendingBackspaceHold()
    }

    override fun isShifted(): Boolean {
        return isShifted
    }

    override fun onStopBackspaceHold() {
        stopBackspaceHold()
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
                    3 -> 2.dp(this)
                    5 -> 10.dp(this)
                    else -> 8.dp(this)
                },
                when (savedRowCount) {
                    4 -> 0.dp(this)
                    else -> 4.dp(this)
                },
                when (savedRowCount) {
                    3 -> 4.dp(this)
                    5 -> 6.dp(this)
                    else -> 8.dp(this)
                },
                4.dp(this)
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
                    leftMargin = 14.dp(this@MyKeyboardService)
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
                leftMargin = if (savedRowCount == 3) -(16.dp(this@MyKeyboardService)) else 8.dp(this@MyKeyboardService)
                rightMargin = if (savedRowCount == 3) 30.dp(this@MyKeyboardService) else 8.dp(this@MyKeyboardService)
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
                        leftMargin = -(24.dp(this@MyKeyboardService))
                        rightMargin = 0.dp(this@MyKeyboardService)
                    }

                    5 -> {
                        leftMargin = 0.dp(this@MyKeyboardService)
                        rightMargin = 14.dp(this@MyKeyboardService)
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
                    3 -> 33.dp(this)
                    4 -> 38.dp(this)
                    5 -> 36.dp(this)
                    else -> 42.dp(this)
                }
            }

            KeyShape.TRIANGLE -> {
                when (savedRowCount) {
                    3 -> 32.dp(this)
                    5 -> 34.dp(this)
                    else -> 40.dp(this)
                }
            }

            KeyShape.CIRCLE -> {
                when (savedRowCount) {
                    3 -> 32.dp(this)
                    5 -> 34.dp(this)
                    else -> 38.dp(this)
                }
            }

            KeyShape.CUBE -> {
                when (savedRowCount) {
                    3 -> 31.dp(this)
                    5 -> 33.dp(this)
                    else -> 35.dp(this)
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
                    5 -> 9.dp(this)
                    4 -> 4.dp(this) // manje preklapanja = redovi se više razmaknu
                    else -> (keySize * 0.25f).toInt()
                }
            }

            KeyShape.TRIANGLE -> {
                if (savedRowCount == 5) 3.dp(this)
                else (keySize * 0.18f).toInt()
            }

            KeyShape.CIRCLE -> if (savedRowCount == 5) 5.dp(this) else 4.dp(this)
            KeyShape.CUBE -> {
                when (savedRowCount) {
                    3 -> -(2.dp(this))
                    5 -> 5.dp(this)
                    else -> 4.dp(this)
                }
            }
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
                3 -> 18.dp(this)    // sva 3 reda desno od side buttona
                4 -> 12.dp(this)
                else -> 0
            }

            val middleRowExtraRight = when (savedRowCount) {
                3 -> 9.dp(this)    // samo 2. red još mrvicu desno za centriranje
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

                if (kv.shape == KeyShape.TRIANGLE) {
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
            3 -> 14.dp(this)    // sva 3 desna reda lijevo od side buttona
            else -> 0
        }

        currentKeyboardConfig.rows.forEachIndexed { rowIndex, row ->
            val visibleRow = row.keys.filterNot {
                it.longPressBindings.contains(KeyMarkers.EDGE_GHOST)
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

                if (kv.shape == KeyShape.TRIANGLE) {
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
        val savedRowCount = KeyboardPrefs.getRowCount(this)

        val cfg = KeyboardPrefs.loadHorizontalCenterLayoutForRowCount(
            this,
            savedRowCount
        )

        // Prikazujemo sve redove iz horizontal center editora
        val centerRows = cfg.rows

        val keySize = when (savedRowCount) {
            3 -> 25.dp(this)
            4 -> 27.dp(this)
            5 -> 28.dp(this)
            else -> 28.dp(this)
        }

        val rowGap = when (savedRowCount) {
            3 -> 4.dp(this)
            4 -> 5.dp(this)
            5 -> 6.dp(this)
            else -> 6.dp(this)
        }

        centerRows.forEach { rowConfig ->
            val rowKeys = rowConfig.keys.filter { key ->
                key.label.isNotBlank() &&
                        !key.longPressBindings.contains("__USER_EMPTY__") &&
                        !key.longPressBindings.contains(KeyMarkers.EDGE_GHOST)
            }

            if (rowKeys.isEmpty()) return@forEach

            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                clipChildren = false
                clipToPadding = false
            }

            rowKeys.forEachIndexed { i, key ->
                val kv = createCenterTextKey(key.label)

                // Primijeni iste boje kao za obične tipke u glavnom layoutu
                if (key.label !in setOf("↵", " ", "⇧", "⌫", "😊")) {
                    val allSame = KeyboardPrefs.getKeysAllSameColor(this)
                    if (allSame) {
                        kv.customBgColor = KeyboardPrefs.getKeysBg(this)
                        kv.setTextColor(KeyboardPrefs.getKeysTextColor(this))
                    } else {
                        val individualColors = KeyboardPrefs.getKeyIndividualColors(this, key.label)
                        if (individualColors != null) {
                            kv.customBgColor = individualColors.first
                            kv.setTextColor(individualColors.second)
                        } else {
                            // Nema individualne boje — fallback na theme defaults
                            val themeColors = KeyboardPrefs.getThemeDefaultsForMode(this, lastIsDark == true)
                            kv.setTextColor(themeColors.keyText)
                            kv.customBgColor = themeColors.keyFill
                        }
                    }
                }

                val lp = LinearLayout.LayoutParams(
                    keySize,
                    keySize
                ).apply {
                    if (i > 0) leftMargin = 4.dp(this@MyKeyboardService)
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
            it.longPressBindings.contains(KeyMarkers.EDGE_GHOST)
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
            it.longPressBindings.contains(KeyMarkers.EDGE_GHOST)
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
            val isLetterKey = lbl.length == 1 && lbl[0].isLetter()

            val newLbl = if (isLetterKey) {
                lbl.uppercase()
            } else {
                lbl
            }

            val newBinds = if (isLetterKey) {
                k.longPressBindings.map { it.uppercase() }.toMutableList()
            } else {
                k.longPressBindings.toMutableList()
            }

            return k.copy(
                label = newLbl,
                longPressBindings = newBinds
            )
        }

        return cfg.copy(
            rows = cfg.rows.map {
                it.copy(keys = it.keys.map(::up).toMutableList())
            }.toMutableList(),
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
            val isEdgeGhost = keyConfig.longPressBindings.contains(KeyMarkers.EDGE_GHOST)
            val isUserEmpty = keyConfig.longPressBindings.contains(KeyMarkers.USER_EMPTY)

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

        val rawDisplay = if (label.length == 1 && label[0].isLetter()) {
            if (isShifted) {
                label.uppercase(Locale.ROOT)
            } else {
                label.lowercase(Locale.ROOT)
            }
        } else {
            label
        }

        val display = mapForSelectedScript(rawDisplay)

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

// Samo 3-row portrait CUBE smije koristiti punu zadanu visinu.
// Inače KeyView zadržava kvadrat i ignorira dodatni keyH.
        val allowNonSquareShape =
            !isLandscape() &&
                    (
                            activeShape == KeyShape.TRIANGLE ||
                                    (
                                            rowCount == 3 &&
                                                    activeShape == KeyShape.CUBE
                                            )
                            )

        forceSquare = !allowNonSquareShape

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

        setTextColor(themeColor(this@MyKeyboardService, R.attr.keyText,
            if (lastIsDark == true) Color.WHITE else Color.BLACK))

        //if (label == "↵") {
        //    customBgColor = KeyboardPrefs.getEnterBg(context)
        //    setTextColor(KeyboardPrefs.getEnterIcon(context))
        //}

        if (label == "⇧") {
            if (isShifted) {
                text = "⇪"
                customBgColor = Color.WHITE
                setTextColor(Color.BLACK)
            } else {
                text = "⇧"
                customBgColor = null
                setTextColor(themeColor(this@MyKeyboardService, R.attr.keyText,
                    if (lastIsDark == true) Color.WHITE else Color.BLACK))
            }
        }

        val nonBindable = setOf("⇧", "⌫", "↵", "123", "ABC", "abc", " ", "😊")
        val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

        var longPressTriggered = false
        var startX = 0f
        var startY = 0f
        val step = 18.dp(this)
        var handledBySwipeUp = false
        val swipeUpThreshold = 26.dp(this)
        var longPressJob: Job? = null

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

                    // Haptic feedback on key press
                    hapticManager.performHapticFeedback(v)

                    // Show key preview
                    keyPreviewManager.show(v, label)

                    if (label == " ") {
                        handleDualSpaceDown(keyConfig)
                    }

                    longPressJob?.cancel()
                    if (label !in nonBindable) {
                        longPressJob = serviceScope.launch {
                            delay(longPressTimeout)
                            val binds = keyConfig.longPressBindings
                            if (binds.isNotEmpty()) {
                                longPressTriggered = true
                                keyPreviewManager.hide()
                                hapticManager.onLongPress()
                                showLongPressPopup(this@apply, binds)
                                longPressPopupManager.resetSelection()
                            }
                        }
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
                        longPressJob?.cancel()
                        hideLongPressPopup()
                        keyPreviewManager.hide()
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
                            longPressJob?.cancel()
                            hideLongPressPopup()
                            currentInputConnection?.commitText(swipeText, 1)
                            v.isPressed = false
                            return@setOnTouchListener true
                        }
                    }

                    if (!longPressTriggered && absDx > 20.dp(this) && absDx > absDy * 1.05f) {
                        longPressJob?.cancel()
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
                            if (newIdx != -1 && newIdx != longPressPopupManager.getSelectedIndex()) {
                                longPressPopupManager.setSelectedIndex(newIdx)
                            }
                        }
                    }

                    // Skip swipe gestures when long press popup is active
                    if (!longPressTriggered) {
                        inputController.handleTouch(v as TextView, e)
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    // Hide key preview
                    keyPreviewManager.hide()

                    if (label == " " && handleDualSpaceUpOrCancel(keyConfig)) {
                        v.isPressed = false
                        v.isSelected = false
                        v.clearFocus()
                        longPressJob?.cancel()
                        return@setOnTouchListener true
                    }

                    if (handledBySwipeUp) {
                        v.isPressed = false
                        v.isSelected = false
                        v.clearFocus()
                        longPressJob?.cancel()
                        return@setOnTouchListener true
                    }

                    v.performClick()
                    v.isSelected = false
                    v.clearFocus()
                    longPressJob?.cancel()

                    if (longPressTriggered) {
                        longPressPopupManager.getSelectedChar()?.let { ch ->
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
                    // Hide key preview
                    keyPreviewManager.hide()

                    if (label == " ") {
                        handleDualSpaceUpOrCancel(keyConfig)
                    }

                    handledBySwipeUp = false
                    longPressJob?.cancel()

                    if (longPressTriggered) {
                        hideLongPressPopup()
                    }

                    v.isPressed = false
                    v.isSelected = false
                    v.clearFocus()

                    // Skip swipe cleanup when long press popup was shown
                    if (!longPressTriggered) {
                        inputController.handleTouch(v as TextView, e)
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun colorLookupLabel(label: String): String {
        return if (label.length == 1 && label[0].isLetter()) {
            label.lowercase()
        } else {
            label
        }
    }

    private fun applySpecialKeyColors(kv: KeyView, key: KeyConfig, spaceIndex: Int): Int {
        var nextSpaceIndex = spaceIndex

        // 1. SPACE — uvijek posebno
        if (key.label == " ") {
            val useTheme = KeyboardPrefs.getKeysUseTheme(this)
            val spaceColor = if (useTheme) {
                // Use custom theme defaults for space
                KeyboardPrefs.getThemeDefaultsForMode(this, lastIsDark == true).spaceFill
            } else {
                // Use custom space color prefs
                val linked = KeyboardPrefs.isSpaceLinked(this)
                val c1 = KeyboardPrefs.getSpace1Bg(this)
                val c2 = if (linked) c1 else KeyboardPrefs.getSpace2Bg(this)

                when {
                    isLeftSpace(key) -> c1
                    isRightSpace(key) -> c2
                    spaceIndex == 0 -> c1
                    else -> c2
                }
            }

            kv.customBgColor = spaceColor
            nextSpaceIndex++
            return nextSpaceIndex
        }

        // 2. ENTER — uvijek posebno
        if (key.label == "↵") {
            kv.customBgColor = KeyboardPrefs.getEnterBg(this)
            kv.setTextColor(KeyboardPrefs.getEnterIcon(this))
            return nextSpaceIndex
        }

        // 3. SHIFT — posebno
        if (key.label == "⇧") {
            if (isShifted) {
                kv.text = "⇪"
                kv.customBgColor = Color.WHITE
                kv.setTextColor(Color.BLACK)
            } else {
                kv.text = "⇧"
                kv.customBgColor = null
                kv.setTextColor(themeColor(this@MyKeyboardService, R.attr.keyText,
                    if (lastIsDark == true) Color.WHITE else Color.BLACK))
            }
            return nextSpaceIndex
        }

        // 4. BACKSPACE, EMOJI — ne diraj, već su postavljeni u createKey
        if (key.label in setOf("⌫", "😊")) {
            return nextSpaceIndex
        }

        // 5. OBIČNE TIPKE + "123", "ABC", "abc" — ovdje rješavamo boje
        val allSame = KeyboardPrefs.getKeysAllSameColor(this)
        val useTheme = KeyboardPrefs.getKeysUseTheme(this)

        if (useTheme) {
            // Koristi custom theme defaults
            val themeColors = KeyboardPrefs.getThemeDefaultsForMode(this, lastIsDark == true)
            kv.setTextColor(themeColors.keyText)
            kv.customBgColor = themeColors.keyFill
        } else if (allSame) {
            // Sve iste boje iz prefs
            val keysBg = KeyboardPrefs.getKeysBg(this)
            val keysText = KeyboardPrefs.getKeysTextColor(this)
            kv.customBgColor = keysBg
            kv.setTextColor(keysText)
        } else {
            // Pokušaj individualne boje
            val individualColors = KeyboardPrefs.getKeyIndividualColors(this, colorLookupLabel(key.label))
            if (individualColors != null) {
                kv.customBgColor = individualColors.first
                kv.setTextColor(individualColors.second)
            } else {
                // NEMA individualne boje — fallback na theme defaults
                val themeColors = KeyboardPrefs.getThemeDefaultsForMode(this, lastIsDark == true)
                kv.setTextColor(themeColors.keyText)
                kv.customBgColor = themeColors.keyFill
            }
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