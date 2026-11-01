package com.example.antarakeyboard.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isEmpty
import androidx.core.view.isNotEmpty
import androidx.core.view.isVisible
import com.example.antarakeyboard.R
import com.example.antarakeyboard.data.AppLanguageSettings
import com.example.antarakeyboard.data.AppLanguages
import com.example.antarakeyboard.data.KeyboardPrefs
import com.example.antarakeyboard.data.LongPressPresets
import com.example.antarakeyboard.data.PrefsManager
import com.example.antarakeyboard.data.ScriptMapper
import com.example.antarakeyboard.extensions.dp
import com.example.antarakeyboard.model.KeyConfig
import com.example.antarakeyboard.model.KeyMarkers
import com.example.antarakeyboard.model.KeyShape
import com.example.antarakeyboard.model.KeyboardConfig
import com.example.antarakeyboard.model.shiftedBinding
import com.example.antarakeyboard.service.input.KeyInputController
import com.example.antarakeyboard.service.suggest.SuggestionController
import com.example.antarakeyboard.service.suggest.SuggestionDictionaries
import com.example.antarakeyboard.service.suggest.WordSuggester
import com.example.antarakeyboard.ui.KeyView
import com.example.antarakeyboard.ui.defaultKeyboardLayout
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MyKeyboardService : InputMethodService(), EdgeActionCallback {

    /* ───────── STATE ───────── */

    private var isShifted = false
    private var isDrawing = false
    // A redraw requested while one is in progress runs right after it instead of being dropped
    private var pendingRedraw = false
    private var isPasswordInput = false

    // Word suggestions: strip above the keys; the dictionary survives input view recreation
    private val suggestionController by lazy {
        SuggestionController(this, serviceScope) { refreshSuggestions() }
    }
    private lateinit var suggestionStrip: LinearLayout
    private var suggestionsOn = false
    private var suggestionsAllowedInField = false
    private var lastBottomInsetPx: Int = 0

    private var currentKeyboardConfig: KeyboardConfig = defaultKeyboardLayout
    private var currentShape: KeyShape = KeyShape.HEX

    // Coroutine scope for the service lifecycle
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)


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

    private var lastIsDark: Boolean? = null
    private var lastAppLanguage: String? = null
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

    private companion object {
        const val OVERLAP_RATIO = 0.18f
        const val DUAL_SPACE_HOLD_MS = 4000L
        const val SUGGESTION_COUNT = 3
        const val SUGGESTION_LOOKBEHIND = 48
    }

    // RGB animation
    private var rgbAnimationJob: Job? = null
    private val rgbRandom = java.util.Random()
    /* ───────── LIFECYCLE ───────── */
    //claude sync

    // The IME root view has no parent to inflate into; BackgroundSwipeListener calls performClick
    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    override fun onCreateInputView(): View {
        // Recreate (theme / configuration change): stop work bound to the old views
        releaseInputViewResources()

        KeyboardPrefs.ensureDefaultLongPress(this)
        val isDark = PrefsManager.isDarkMode(this)

        val themeRes = if (isDark) {
            R.style.Theme_AntaraKeyboard_Dark
        } else {
            R.style.Theme_AntaraKeyboard_Light
        }

        themedCtx = ContextThemeWrapper(localizedContext(), themeRes)
        lastIsDark = isDark
        lastAppLanguage = PrefsManager.getAppLanguage(this)

        rootView = layoutInflater.cloneInContext(themedCtx)
            .inflate(R.layout.keyboard_view, null)

        overlayLayer = rootView.findViewById(R.id.keyboardRoot)
        keyboardContainer = rootView.findViewById(R.id.keyboardContainer)

        lastBottomInsetPx = 0



        // Initial background (the RGB animation is started in onStartInputView)
        if (!KeyboardPrefs.isAnyRgbModeEnabled(this)) {
            val bg = KeyboardPrefs.resolveKeyboardBackground(this, lastIsDark == true)

            rootView.setBackgroundColor(bg)
            overlayLayer.setBackgroundColor(bg)
            keyboardContainer.setBackgroundColor(bg)

            window?.window?.setBackgroundDrawable(bg.toDrawable())
        }


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

        // Suggestion strip sits above the rows, outside keyboardContainer (whose children
        // must all be key rows – the side-button overlay relies on that)
        suggestionStrip = createSuggestionStrip()
        overlayLayer.addView(
            suggestionStrip,
            0,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                suggestionStripHeightPx(),
                Gravity.TOP
            )
        )
        updateSuggestionStrip()

        inputController = KeyInputController(this)

        // Swipe left/right to delete/restore also works on the background between keys
        overlayLayer.setOnTouchListener(BackgroundSwipeListener())

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
            colorsProvider = { anchor -> longPressPopupColors(anchor) },
            rgbBackgroundProvider = {
                if (rgbAnimationJob != null) {
                    createRadialRainbowDrawable(lastRgbStep, lastRgbSaturation, lastRgbBrightness)
                } else {
                    null
                }
            }
        )

        edgeOverlayManager = EdgeOverlayManager(
            context = this,
            overlayLayerProvider = { overlayLayer },
            keyboardContainerProvider = { keyboardContainer },
            themedCtxProvider = { themedCtx },
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
            // Localized context: category names follow the app language
            context = themedCtx,
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
            },
            colorsProvider = { emojiPickerColors() }
        )

        hapticManager = HapticManager(this)
        hapticManager.setEnabled(KeyboardPrefs.isVibrationEnabled(this))
        keyPreviewManager = KeyPreviewManager(
            this,
            overlayLayerProvider = { overlayLayer },
            colorsProvider = { anchor -> pressedKeyColors(anchor) }
        ).apply { setEnabled(!isPasswordInput) }

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
        isExtractViewShown = false

        // Password fields: no key preview, so typed characters never appear on screen
        isPasswordInput = InputTypes.isPassword(info)
        keyPreviewManager.setEnabled(!isPasswordInput)

        // First: a language changed elsewhere switches the script, which the dictionary follows
        KeyboardPrefs.ensureDefaultLongPress(this)

        // Word suggestions (main app switch); nothing in passwords, numbers, e-mails or URLs
        suggestionsOn = KeyboardPrefs.isSuggestionsEnabled(this)
        suggestionsAllowedInField = InputTypes.allowsSuggestions(info)
        if (suggestionsOn) suggestionController.use(currentSuggestionDictionary())
        else suggestionController.release()
        updateSuggestionStrip()

        // Refresh vibration preference
        hapticManager.setEnabled(KeyboardPrefs.isVibrationEnabled(this))

        val isDarkNow = PrefsManager.isDarkMode(this)

        val languageChanged = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU &&
            lastAppLanguage != null && lastAppLanguage != PrefsManager.getAppLanguage(this)

        if ((lastIsDark != null && lastIsDark != isDarkNow) || languageChanged) {
            lastIsDark = isDarkNow
            // New themed / localized views; the rest of this method then configures them
            recreateInputView()
        }
        lastIsDark = isDarkNow

        // Background color from KeyboardPrefs, or the RGB animation
        if (KeyboardPrefs.isAnyRgbModeEnabled(this)) {
            startRgbAnimation()
        } else {
            stopRgbAnimation()
            val bg = KeyboardPrefs.resolveKeyboardBackground(this, isDarkNow)
            rootView.setBackgroundColor(bg)
            overlayLayer.setBackgroundColor(bg)
            keyboardContainer.setBackgroundColor(bg)
            window?.window?.setBackgroundDrawable(bg.toDrawable())
        }

        // Do NOT write theme colors into KeyboardPrefs here: the theme is only a fallback
        // while drawing, otherwise reset/start would overwrite the custom colors.

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

        // Number / phone / date fields open on the numeric layout
        if (InputTypes.prefersNumeric(info)) {
            currentKeyboardConfig = applyEdgeKeys(myDefaultNumericConfig)
        }

        targetKeyboardHeightPx = computeTargetKeyboardHeight()
        overlayLayer.post { redrawKeyboard() }
    }
    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        resetTransientState()
        if (::suggestionStrip.isInitialized) showSuggestions(emptyList())
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        // Every typed key, delete, suggestion and cursor move ends up here
        refreshSuggestions()
    }

    /* ───────── WORD SUGGESTIONS ───────── */

    private fun suggestionStripHeightPx(): Int = 38.dp(this)

    /** Dictionary for the current keyboard script and app/device language. */
    private fun currentSuggestionDictionary(): String? =
        SuggestionDictionaries.forKeyboard(
            KeyboardPrefs.getSelectedLongPressPreset(this),
            KeyboardPrefs.languageTag(this)
        )

    private fun createSuggestionStrip(): LinearLayout = LinearLayout(themedCtx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        tag = "suggestion_strip"

        repeat(SUGGESTION_COUNT) { index ->
            if (index > 0) {
                addView(View(themedCtx), LinearLayout.LayoutParams(1.dp(this@MyKeyboardService), 18.dp(this@MyKeyboardService)))
            }
            addView(
                TextView(themedCtx).apply {
                    gravity = Gravity.CENTER
                    textSize = 16f
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(4.dp(this@MyKeyboardService), 0, 4.dp(this@MyKeyboardService), 0)
                    setOnClickListener { view ->
                        val suggestion = (view as TextView).text?.toString().orEmpty()
                        if (suggestion.isNotEmpty()) {
                            hapticManager.performHapticFeedback(view)
                            applySuggestion(suggestion)
                        }
                    }
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            )
        }
    }

    /**
     * Shows the strip only when suggestions are on and the field allows them; fields that ask
     * for none (passwords, numbers, e-mail, URL, TYPE_TEXT_FLAG_NO_SUGGESTIONS) get no strip.
     * Recolors it to match the keys.
     */
    private fun updateSuggestionStrip() {
        if (!::suggestionStrip.isInitialized) return
        suggestionStrip.visibility =
            if (suggestionsOn && suggestionsAllowedInField) View.VISIBLE else View.GONE
        val text = emojiPickerColors().text
        for (i in 0 until suggestionStrip.childCount) {
            when (val child = suggestionStrip.getChildAt(i)) {
                is TextView -> child.setTextColor(text)
                else -> child.setBackgroundColor(androidx.core.graphics.ColorUtils.setAlphaComponent(text, 0x40))
            }
        }
        refreshSuggestions()
    }

    private fun refreshSuggestions() {
        if (!::suggestionStrip.isInitialized || !suggestionsOn) return
        val word = if (suggestionsAllowedInField) currentTypedWord() else ""
        showSuggestions(if (word.isEmpty()) emptyList() else suggestionController.suggest(word))
    }

    private fun showSuggestions(words: List<String>) {
        var slot = 0
        for (i in 0 until suggestionStrip.childCount) {
            val view = suggestionStrip.getChildAt(i) as? TextView ?: continue
            view.text = words.getOrNull(slot).orEmpty()
            slot++
        }
    }

    /** The word right before the cursor (empty with a selection or inside a word). */
    private fun currentTypedWord(): String {
        val ic = currentInputConnection ?: return ""
        if (!ic.getSelectedText(0).isNullOrEmpty()) return ""
        val before = ic.getTextBeforeCursor(SUGGESTION_LOOKBEHIND, 0) ?: return ""
        val after = ic.getTextAfterCursor(1, 0) ?: ""
        return WordSuggester.currentWord(before, after)
    }

    /** Replaces the word being typed with [suggestion] and adds a space. */
    private fun applySuggestion(suggestion: String) {
        val ic = currentInputConnection ?: return
        val typed = currentTypedWord()
        ic.beginBatchEdit()
        if (typed.isNotEmpty()) ic.deleteSurroundingText(typed.length, 0)
        ic.commitText("$suggestion ", 1)
        ic.endBatchEdit()
        deleteRestoreManager.clearRestoreBuffer()
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        resetTransientState()
        stopRgbAnimation()
    }

    override fun onEvaluateFullscreenMode() = false
    override fun onCreateExtractTextView(): View? = null

    override fun onDestroy() {
        // Popups and manager jobs (no-op if the input view was never created)
        releaseInputViewResources()

        // Cancel all coroutines
        serviceScope.cancel()
        super.onDestroy()
    }

    /**
     * Context whose resources use the app language. Android 13+ applies per-app locales to the
     * whole process (and calls onConfigurationChanged), so only older versions need the override.
     */
    private fun localizedContext(): Context {
        val tag = PrefsManager.getAppLanguage(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU || tag.isEmpty()) return this
        val config = Configuration(resources.configuration)
        config.setLocale(Locale.forLanguageTag(tag))
        return createConfigurationContext(config)
    }

    /** Dismisses popups and stops jobs owned by the current input view, if one exists. */
    private fun releaseInputViewResources() {
        stopRgbAnimation()
        hideLanguagePresetPopup()
        if (::emojiPickerManager.isInitialized) emojiPickerManager.hide()
        if (::longPressPopupManager.isInitialized) longPressPopupManager.hideLongPressPopup()
        if (::keyPreviewManager.isInitialized) keyPreviewManager.hide()
        if (::deleteRestoreManager.isInitialized) deleteRestoreManager.resetState()
        cancelDualSpaceHoldTimer()
        isDrawing = false
        pendingRedraw = false
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // Recreate (onCreateInputView releases popups and jobs of the old view) keyboard view to adapt to new configuration
        recreateInputView()
    }

    private fun resetTransientState() {
        isShifted = false

        if (::deleteRestoreManager.isInitialized) deleteRestoreManager.resetState()
        if (::emojiPickerManager.isInitialized) hideEmojiPopup()
        if (::longPressPopupManager.isInitialized) hideLongPressPopup()
        if (::keyPreviewManager.isInitialized) keyPreviewManager.hide()
        hideLanguagePresetPopup()
        resetDualSpaceHoldState()
    }

    private fun recreateInputView() {
        setInputView(onCreateInputView())
    }

    /* ───────── HELPERS ───────── */

    private fun mapForSelectedScript(text: String): String =
        ScriptMapper.map(KeyboardPrefs.getSelectedLongPressPreset(this), text)

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
                delay(DUAL_SPACE_HOLD_MS.milliseconds)
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

    /**
     * Dual-space picker: the same language choice as the main app (System default + every
     * translation, each in its own language). Choosing one sets the app language and the
     * keyboard script that goes with it (Cyrillic languages type Cyrillic).
     */
    private fun showLanguagePresetPopup() {
        hideLongPressPopup()
        hideEmojiPopup()
        hideLanguagePresetPopup()
        cancelDualSpaceHoldTimer()

        dualSpacePickerWasShown = true

        val popupWidth = (resources.displayMetrics.widthPixels * 0.86f).toInt()
            .coerceAtLeast(280.dp(this))

        // Same palette as the emoji picker: keyboard surface + key text
        val pickerColors = emojiPickerColors()
        val popupBg = pickerColors.panel
        val popupText = pickerColors.text

        val root = LinearLayout(themedCtx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp(this), 18.dp(this), 20.dp(this), 14.dp(this))
            setBackgroundColor(popupBg)
        }

        root.addView(TextView(themedCtx).apply {
            text = themedCtx.getString(R.string.main_language)
            textSize = 18f
            setTextColor(popupText)
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(0, 0, 0, 14.dp(this))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val tags = listOf("") + AppLanguages.TAGS
        val current = AppLanguageSettings.selectedTag(this).let { AppLanguages.match(it) ?: "" }

        val radioGroup = RadioGroup(themedCtx).apply { orientation = RadioGroup.VERTICAL }
        val idToTag = HashMap<Int, String>()
        tags.forEach { tag ->
            val id = View.generateViewId()
            idToTag[id] = tag
            radioGroup.addView(RadioButton(themedCtx).apply {
                this.id = id
                text = if (tag.isEmpty()) themedCtx.getString(R.string.language_system_default)
                else AppLanguages.nativeName(tag)
                textSize = 16f
                setTextColor(popupText)
                setPadding(0, 6.dp(this), 0, 6.dp(this))
                isFocusable = false
            }, RadioGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        idToTag.entries.firstOrNull { it.value == current }?.let { radioGroup.check(it.key) }

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val tag = idToTag[checkedId] ?: return@setOnCheckedChangeListener
            applyLanguageFromPicker(tag)
        }

        val scroll = ScrollView(themedCtx).apply {
            isFillViewport = false
            addView(radioGroup, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (resources.displayMetrics.heightPixels * 0.46f).toInt()
        ))

        root.addView(TextView(themedCtx).apply {
            text = themedCtx.getString(R.string.action_close)
            textSize = 14f
            setTextColor(popupText)
            gravity = Gravity.CENTER
            setPadding(8.dp(this), 16.dp(this), 8.dp(this), 0)
            isClickable = true
            isFocusable = false
            setOnClickListener { hideLanguagePresetPopup() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val popup = PopupWindow(root, popupWidth, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            isOutsideTouchable = true
            isFocusable = true
            isClippingEnabled = false
            elevation = 12.dp(this@MyKeyboardService).toFloat()
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            setOnDismissListener {
                languagePresetPopup = null
                if (!leftSpaceHeld && !rightSpaceHeld) {
                    dualSpacePickerWasShown = false
                }
            }
        }
        languagePresetPopup = popup

        // showAtLocation in an IME is relative to the rootView window: convert screen Y to local Y
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val rootLoc = IntArray(2)
        rootView.getLocationOnScreen(rootLoc)
        val localX = ((screenW - popupWidth) / 2).coerceAtLeast(8.dp(this))
        val localY = (screenH * 0.105f).toInt().coerceAtLeast(54.dp(this)) - rootLoc[1]

        popup.showAtLocation(rootView, Gravity.NO_GRAVITY, localX, localY)
    }

    /** Language chosen on the keyboard: same effect as the main app's Language dropdown. */
    private fun applyLanguageFromPicker(tag: String) {
        AppLanguageSettings.select(this, tag)
        applyLongPressPresetAndRefresh(KeyboardPrefs.getSelectedLongPressPreset(this))
        // New language for the keyboard's own texts (Android 13+ also sends a configuration change)
        overlayLayer.post { recreateInputView() }
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
        // Another script types another language: switch the suggestion dictionary too
        if (suggestionsOn) suggestionController.use(currentSuggestionDictionary())
        redrawKeyboard()
    }


    private fun showEmojiPicker() {
        // A delete/restore swipe must never keep running behind the picker
        stopSwipeGestures()
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

        val denominator = when (activeShape) {
            KeyShape.HEX,
            KeyShape.HEX_TALL,
            KeyShape.HEX_HALF_LEFT,
            KeyShape.HEX_HALF_RIGHT -> (rows - (rows - 1) * OVERLAP_RATIO).coerceAtLeast(1f)

            KeyShape.TRIANGLE -> rows.toFloat()
            KeyShape.CIRCLE -> rows.toFloat()
            KeyShape.CUBE -> rows.toFloat()
        }

        val baseH = (usableForKeys / denominator).toInt().coerceAtLeast(
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
        if (overlayLayer.width <= 0 || keyboardContainer.isEmpty()) return

        /*
         * Important: keyboardContainer.height can be clipped by the overlay's current
         * height, so it is measured again with an UNSPECIFIED height to get the real
         * height of all rows.
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
         * paddingBottom already contains the navigation/system inset. No artificial
         * minTarget or extraBottomSafety is added: they left empty space at the top.
         */
        val stripH = if (::suggestionStrip.isInitialized && suggestionStrip.isVisible) {
            suggestionStripHeightPx()
        } else {
            0
        }
        val desiredHeight =
            contentH +
                    stripH +
                    overlayLayer.paddingTop +
                    overlayLayer.paddingBottom

        /*
         * Only a guard against a completely wrong measurement, not the normal
         * keyboard limit.
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

        // Remove a minimum height that may be left over from earlier.
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

    /**
     * Delete/restore swipe on the keyboard background (gaps between keys, around the rows),
     * with the same thresholds as on keys. Touches that start on a key are handled by the key.
     */
    private inner class BackgroundSwipeListener : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var mode = SwipeMode.NONE

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    mode = SwipeMode.NONE
                    hideLongPressPopup()
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val absDx = abs(dx)
                    val absDy = abs(e.rawY - downY)
                    if (absDx > 20.dp(this@MyKeyboardService) && absDx > absDy * 1.05f) {
                        if (dx < 0f) {
                            if (mode != SwipeMode.DELETE) {
                                mode = SwipeMode.DELETE
                                startSwipeDelete(absDx)
                            } else {
                                updateSwipeDelete(absDx)
                            }
                        } else {
                            if (mode != SwipeMode.RESTORE) {
                                mode = SwipeMode.RESTORE
                                startSwipeRestore(absDx)
                            } else {
                                updateSwipeRestore(absDx)
                            }
                        }
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopSwipeGestures()
                    mode = SwipeMode.NONE
                    if (e.actionMasked == MotionEvent.ACTION_UP) v.performClick()
                }

                else -> return false
            }
            return true
        }
    }

    private enum class SwipeMode { NONE, DELETE, RESTORE }

    /** Stops every running delete/restore swipe and backspace hold. */
    private fun stopSwipeGestures() {
        if (!::deleteRestoreManager.isInitialized) return
        deleteRestoreManager.stopSwipeDelete()
        deleteRestoreManager.stopSwipeRestore()
        deleteRestoreManager.stopBackspaceHold()
        if (::inputController.isInitialized) inputController.reset()
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

    /**
     * Enter follows the editor: runs its action (Search, Send, Go, Done…) unless the field asks
     * for plain Enter (no action / IME_FLAG_NO_ENTER_ACTION), in which case a newline is sent.
     */
    fun sendEnter() {
        hapticManager.onSpecialKey()
        deleteRestoreManager.clearRestoreBuffer()
        sendKeyChar('\n')
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

    /**
     * Popup follows the active theme / Colors settings: keyboard background as surface,
     * the long-pressed key's own colors for cells, Enter colors for the selected cell.
     */
    private fun longPressPopupColors(anchor: View): LongPressPopupManager.Colors {
        // Under RGB the rgbBackgroundProvider supplies the live surface; this is the fallback
        val popupBg = popupSurfaceColor()

        val (keyBg, keyText) = pressedKeyColors(anchor)
        return LongPressPopupManager.Colors(
            popupBg = popupBg,
            keyBg = keyBg,
            keyText = keyText,
            activeBg = KeyboardPrefs.getEnterBg(this),
            activeText = KeyboardPrefs.getEnterIcon(this)
        )
    }

    /**
     * Opaque surface for keyboard popups: the keyboard background, or the theme background
     * when the keyboard is animated (RGB) or (nearly) transparent.
     */
    private fun popupSurfaceColor(): Int {
        val themeBg = KeyboardPrefs.getThemeDefaultsForMode(this, lastIsDark == true).keyboardBg
        if (KeyboardPrefs.isAnyRgbModeEnabled(this)) return themeBg
        val bg = KeyboardPrefs.resolveKeyboardBackground(this, lastIsDark == true)
        return if (Color.alpha(bg) < 0xC0) themeBg else bg
    }

    /** Emoji picker follows the keyboard: background as surface, regular key colors for buttons. */
    private fun emojiPickerColors(): EmojiPickerManager.Colors {
        val background = popupSurfaceColor()
        val (button, text) = keyColors()

        return EmojiPickerManager.Colors(
            background = background,
            panel = androidx.core.graphics.ColorUtils.blendARGB(background, button, 0.35f),
            button = button,
            text = text,
            hint = androidx.core.graphics.ColorUtils.setAlphaComponent(text, 0x99)
        )
    }

    /** (background, text) of a key as drawn — custom Colors setting or theme default. */
    private fun pressedKeyColors(anchor: View): Pair<Int, Int> {
        val themeColors = KeyboardPrefs.getThemeDefaultsForMode(this, lastIsDark == true)
        val kv = anchor as? KeyView
        return Pair(
            kv?.customBgColor ?: themeColors.keyFill,
            kv?.currentTextColor ?: themeColors.keyText
        )
    }

    private fun moveLpSelection(dx: Int, dy: Int) {
        longPressPopupManager.moveLpSelection(dx, dy)
    }

    private fun hideLongPressPopup() {
        longPressPopupManager.hideLongPressPopup()
    }


    private val longPressCellBounds: List<Rect>
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

    /**
     * Row sizing with the key height slider applied (see [baseRowSizing] for the tuned defaults).
     * The keys themselves grow ([KeyView.heightStretch]); sizes and row spacing stay as tuned.
     * Only the honeycomb overlap grows with the hexagon tips so the rows stay interlocked.
     */
    private fun computeRowSizing(count: Int, availW: Int): RowSizing {
        val base = baseRowSizing(count, availW)
        val f = keyHeightStretch()
        if (f == 1f || base.overlapPx == 0) return base

        val shape = effectiveShape()
        val hexHeight = when (shape) {
            KeyShape.HEX -> minOf(base.keyW, base.keyH) * 0.96f
            KeyShape.HEX_TALL -> base.keyH * 0.96f
            else -> return base
        }
        return base.copy(overlapPx = KeyScale.honeycombOverlap(base.overlapPx, hexHeight, f))
    }

    /** Key height factor from the main app slider (1 = tuned default). */
    private fun keyHeightFactor(): Float = KeyScale.clamp(KeyboardPrefs.getKeyScale(this))

    /**
     * Height stretch the keys are drawn with: the slider factor times the layout's own default.
     * 3-row portrait triangles are taller by default (what used to be 150 % on the slider).
     */
    private fun keyHeightStretch(): Float {
        val layoutDefault = if (
            !isLandscape() &&
            currentShape == KeyShape.TRIANGLE &&
            KeyboardPrefs.getRowCount(this) == 3
        ) {
            KeyScale.THREE_ROW_TRIANGLE_DEFAULT
        } else {
            1f
        }
        return keyHeightFactor() * layoutDefault
    }

    /** Extra vertical space between rows from the row spacing slider (px, may be negative). */
    private fun rowSpacingPx(): Int =
        KeyScale.clampRowSpacing(KeyboardPrefs.getRowSpacingDp(this)).dp(this)

    /**
     * Whether keys are drawn square (KeyView.forceSquare). Only portrait triangles
     * and the 3-row portrait cube use their own height; HEX_TALL is never squared.
     */
    private fun keysForcedSquare(): Boolean {
        val shape = effectiveShape()
        if (shape == KeyShape.HEX_TALL) return false
        val allowNonSquare = !isLandscape() && (
            shape == KeyShape.TRIANGLE ||
                (KeyboardPrefs.getRowCount(this) == 3 && shape == KeyShape.CUBE)
            )
        return !allowNonSquare
    }

    private fun baseRowSizing(count: Int, availW: Int): RowSizing {
        val savedRowCount = KeyboardPrefs.getRowCount(this)
        val layoutShape = currentShape

        /*
         * Portrait TRIANGLE: every key gets the size of the reference row with 6 keys.
         * A row with 7 keys overlaps horizontally to fit the same width without
         * shrinking the keys.
         */
        if (!isLandscape() && layoutShape == KeyShape.TRIANGLE) {
            val usableW = when (savedRowCount) {
                // Leave a little safe space at the left and right edge
                3 -> (availW - 20.dp(this)).coerceAtLeast(240.dp(this))

                // 4-row and 5-row (tuned)
                else -> (availW * 0.98f).toInt()
            }

            val referenceColumns = when (savedRowCount) {
                // 3-row has 11–12 keys per row, so it needs smaller triangles
                3 -> 8.4f

                // 4-row and 5-row sizing
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
             * 7 keys: 7 * keyW is too wide, so the excess is spread over the six gaps
             * between the keys.
             */
            // A small constant overlap applies to rows with 6 keys too.
            val baseOverlapX = (keyW * 0.22f).toInt()

            // Extra overlap needed for a row with 7 keys to fit.
            val fitOverlapX = if (count > 1) {
                ((count * keyW - usableW) / (count - 1))
                    .coerceAtLeast(0)
            } else {
                0
            }

            val overlapX = when (savedRowCount) {
                3 -> {
                    /*
                     * No more subtracting 2.dp(this): it widened the whole row and pushed
                     * the outer keys off the screen.
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

                    // 3-row portrait: less empty space, so the keys themselves
                    // can be bigger
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

                    // 3-row portrait: bigger keys
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
            // Portrait CIRCLE, CUBE and TRIANGLE: a row with 6 keys uses the same key
            // size as a row with 7 keys
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
            // 3-row portrait CUBE: the width stays, only the height grows
            !isLandscape() &&
                    savedRowCount == 3 &&
                    layoutShape == KeyShape.CUBE -> {
                // Height is 1.3 × the key width, not derived from the row's
                // computed height.
                (keyW * 1.3f).toInt()
            }

            // 3-row portrait HEX
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
        if (isDrawing) {
            pendingRedraw = true
            return
        }

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

                    finishRedraw()
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

                val leftPad = when (savedRowCount) {
                    4 -> if (isShiftedRow) {
                        (sizing.outerPadPx + honeycombShift - 6.dp(this@MyKeyboardService)).coerceAtLeast(0)
                    } else {
                        (sizing.outerPadPx - 6.dp(this@MyKeyboardService)).coerceAtLeast(0)
                    }

                    3 -> if (isShiftedRow) {
                        (sizing.outerPadPx + honeycombShift - 6.dp(this@MyKeyboardService)).coerceAtLeast(0)
                    } else {
                        (sizing.outerPadPx - 12.dp(this@MyKeyboardService)).coerceAtLeast(0)
                    }

                    else -> sizing.outerPadPx
                }

                val unshiftedHoneycombRow = shouldHoneycomb && !isShiftedRow
                val rightPad = when (savedRowCount) {
                    4 -> if (unshiftedHoneycombRow) {
                        sizing.outerPadPx + honeycombShift + 2.dp(this@MyKeyboardService)
                    } else {
                        sizing.outerPadPx + 2.dp(this@MyKeyboardService)
                    }

                    3 -> if (unshiftedHoneycombRow) {
                        (sizing.outerPadPx + honeycombShift - 10.dp(this@MyKeyboardService)).coerceAtLeast(0)
                    } else {
                        (sizing.outerPadPx - 12.dp(this@MyKeyboardService)).coerceAtLeast(0)
                    }

                    else -> sizing.outerPadPx
                }

                val isTriangle = layoutShape == KeyShape.TRIANGLE
                val isCubeOrCircle = layoutShape == KeyShape.CUBE || layoutShape == KeyShape.CIRCLE
                // Cube / circle: odd rows left, even rows right (3 and 4 rows)
                val cubeCircleShift = if (containerRowIndex % 2 == 0) -(5.dp(this@MyKeyboardService)) else 10.dp(this@MyKeyboardService)

                val shapeRowTranslationX = if (isLandscape()) 0 else when (savedRowCount) {
                    5 -> if (isTriangle) {
                        when (containerRowIndex) {
                            0, 2, 4 -> -(14.dp(this@MyKeyboardService)) // rows 1, 3 and 5 to the left
                            1, 3 -> 7.dp(this@MyKeyboardService)     // rows 2 and 4 to the right
                            else -> 0
                        }
                    } else 0

                    4 -> when {
                        // rows 1 and 3 to the left, 2 and 4 to the right
                        isTriangle -> if (containerRowIndex % 2 == 0) -(9.dp(this@MyKeyboardService)) else 20.dp(this@MyKeyboardService)
                        isCubeOrCircle -> cubeCircleShift
                        else -> 0
                    }

                    3 -> when {
                        isTriangle -> when (containerRowIndex) {
                            0, 2 -> -(4.dp(this@MyKeyboardService))   // rows 1 and 3 to the left
                            1 -> 18.dp(this@MyKeyboardService)       // row 2 to the right
                            else -> 0
                        }
                        isCubeOrCircle -> cubeCircleShift
                        else -> 0
                    }

                    else -> 0
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
                                    3 -> 1.dp(this@MyKeyboardService)    // 3-row stays as it is
                                    4 -> -(12.dp(this@MyKeyboardService))   // 4-row: rows closer together
                                    5 -> -(12.dp(this@MyKeyboardService))   // 5-row: a little closer still
                                    else -> 1.dp(this@MyKeyboardService)
                                }
                            }
                        }

                        else -> 0
                    } + rowSpacingPx()  // row spacing slider (0 = tuned default)
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
                    finishRedraw()
                }
            }
        }
    }

    private fun finishRedraw() {
        isDrawing = false
        if (pendingRedraw) {
            pendingRedraw = false
            redrawKeyboard()
        }
    }

    /* ───────── EDGE OVERLAY (delegated to EdgeOverlayManager) ───────── */

    private fun drawLandscapeSideSlots() {
        edgeOverlayManager.drawLandscapeSideSlots()
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

            setTextColor(keyColors(label).second)

            // center keys have no split label and no swipe-up alternate
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
    /**
     * Landscape row overlap (used as a negative top margin). Hexagon rows overlap more as their
     * tips grow with the key height slider; the row spacing slider adds space between rows.
     */
    private fun landscapeRowOverlapScaled(keySize: Int): Int {
        val base = landscapeRowOverlapPx(keySize)
        val overlap = when (currentShape) {
            KeyShape.HEX, KeyShape.HEX_TALL, KeyShape.HEX_HALF_LEFT, KeyShape.HEX_HALF_RIGHT ->
                KeyScale.honeycombOverlap(base, keySize * 0.96f, keyHeightStretch())
            else -> base
        }
        return overlap - rowSpacingPx()
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
                    4 -> 4.dp(this) // less overlap = rows further apart
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
        val rowOverlap = landscapeRowOverlapScaled(keySize)
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
                3 -> 18.dp(this)    // all 3 rows right of the side button
                4 -> 12.dp(this)
                else -> 0
            }

            val middleRowExtraRight = when (savedRowCount) {
                3 -> 9.dp(this)    // only row 2 a bit further right, for centering
                else -> 0
            }

            val rowLp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                if (rowIndex > 0) topMargin = -rowOverlap

                leftMargin = when {
                    // 3-row: rows 1 and 3 keep the honeycomb, but all move a little right
                    savedRowCount == 3 && rowIndex in setOf(0, 2) ->
                        -(halfStep / 2) + baseLeftInset

                    // 3-row: row 2 goes right + extra centering
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
        val rowOverlap = landscapeRowOverlapScaled(keySize)
        val keyGap = landscapeKeyGapPx()
        val halfStep = (keySize / 2f).toInt()
        val savedRowCount = KeyboardPrefs.getRowCount(this)
        val baseRightInset = when (savedRowCount) {
            3 -> 14.dp(this)    // all 3 right rows left of the side button
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

            val takeCount = when (savedRowCount) {
                3 -> {
                    when (rowIndex) {
                        0 -> 5
                        1 -> 6
                        2 -> 5
                        else -> keys.size
                    }
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
                    // 3-row: all 3 rows to the left, on top of the honeycomb shift
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

        // Show every row of the Horizontal (center island) editor
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

                // Same colors as the regular keys of the main layout
                if (key.label !in setOf("↵", " ", "⇧", "⌫", "😊")) {
                    val (fill, text) = keyColors(key.label)
                    kv.customBgColor = fill
                    kv.setTextColor(text)
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
                if (container.isNotEmpty()) topMargin = rowGap
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
                k.longPressBindings.map { shiftedBinding(it) }.toMutableList()
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
                    customBgColor = keyColors().first
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

        forceSquare = keysForcedSquare()
        // Key height slider: the key draws its shape taller/shorter and grows by that much
        heightStretch = keyHeightStretch()

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

        // Final key colors (incl. Shift's active look) are set by applySpecialKeyColors()
        setTextColor(keyColors(label).second)

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
                            delay(longPressTimeout.milliseconds)
                            // Latin keyboard: Latin letters only; Cyrillic keyboard: Cyrillic only
                            val binds = LongPressPresets.visibleFor(
                                KeyboardPrefs.getSelectedLongPressPreset(this@MyKeyboardService),
                                keyConfig.longPressBindings
                            )
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
                    val absDx = abs(dx)
                    val absDy = abs(dy)

                    // A running delete/restore swipe owns the gesture: no swipe-up shortcuts
                    val horizontalSwipe = inputController.isHorizontalSwipeActive(v as TextView)

                    // 123 swipe-up => emoji, only on the 4/5-row alphabet layout
                    if (!longPressTriggered &&
                        !handledBySwipeUp &&
                        !horizontalSwipe &&
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
                        !horizontalSwipe &&
                        (label == "." || label == "?") &&
                        (v as? KeyView)?.useSplitLabels == true &&
                        dy < -swipeUpThreshold &&
                        absDy > absDx
                    ) {
                        val swipeText = v.swipeUpLabel

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
                        } else if (longPressCellBounds.isNotEmpty()) {
                            val rx = e.rawX.toInt()
                            val ry = e.rawY.toInt()
                            val newIdx = longPressCellBounds.indexOfFirst { it.contains(rx, ry) }
                            if (newIdx != -1 && newIdx != longPressPopupManager.getSelectedIndex()) {
                                longPressPopupManager.setSelectedIndex(newIdx)
                            }
                        }
                    }

                    // Skip swipe gestures when long press popup is active, and while both space
                    // keys are held (dual-space script/language picker) – that is no swipe
                    if (!longPressTriggered && !(leftSpaceHeld && rightSpaceHeld)) {
                        inputController.handleTouch(v, e)
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    // Hide key preview
                    keyPreviewManager.hide()

                    if (label == " " && handleDualSpaceUpOrCancel(keyConfig)) {
                        stopSwipeGestures()
                        v.isPressed = false
                        v.isSelected = false
                        v.clearFocus()
                        longPressJob?.cancel()
                        return@setOnTouchListener true
                    }

                    if (handledBySwipeUp) {
                        stopSwipeGestures()
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

    /** Regular key colors as drawn (shared rule in [KeyboardPrefs.resolveKeyColors]). */
    private fun keyColors(label: String? = null): Pair<Int, Int> =
        KeyboardPrefs.resolveKeyColors(this, lastIsDark == true, label)

    /**
     * Final colors of every key: space, Enter and Shift have their own colors, every other key
     * (letters, symbols, ⌫, 😊, 123/ABC) uses the regular key colors. All colors come from the
     * same resolvers the main app, saved layouts and popups use.
     */
    private fun applySpecialKeyColors(kv: KeyView, key: KeyConfig, spaceIndex: Int): Int {
        when (key.label) {
            " " -> {
                val (left, right) = KeyboardPrefs.resolveSpaceColors(this, lastIsDark == true)
                kv.customBgColor = when {
                    isLeftSpace(key) -> left
                    isRightSpace(key) -> right
                    spaceIndex == 0 -> left
                    else -> right
                }
                return spaceIndex + 1
            }

            "↵" -> {
                kv.customBgColor = KeyboardPrefs.getEnterBg(this)
                kv.setTextColor(KeyboardPrefs.getEnterIcon(this))
            }

            "⇧" -> {
                // Same colors as every key; an active Shift shows a filled arrow
                kv.text = if (isShifted) KeyMarkers.SHIFT_ON else KeyMarkers.SHIFT_OFF
                val (fill, text) = keyColors(key.label)
                kv.customBgColor = fill
                kv.setTextColor(text)
            }

            else -> {
                val (fill, text) = keyColors(key.label)
                kv.customBgColor = fill
                kv.setTextColor(text)
            }
        }
        return spaceIndex
    }

    /* ───────── RGB ANIMATION ───────── */

    // RGB Rainbow color calculation - 6 segments × 256 steps = 1536 total steps
    // Produces smooth transitions: Red→Yellow→Green→Cyan→Blue→Magenta→Red
    private fun rainbowColor(x: Int): Int {
        val step = ((x % 1536) + 1536) % 1536  // Handle negative values
        val segment = step / 256
        val c = step % 256

        return when (segment) {
            0 -> Color.rgb(255, c, 0)           // Red to Yellow
            1 -> Color.rgb(255 - c, 255, 0)     // Yellow to Green
            2 -> Color.rgb(0, 255, c)           // Green to Cyan
            3 -> Color.rgb(0, 255 - c, 255)     // Cyan to Blue
            4 -> Color.rgb(c, 0, 255)           // Blue to Magenta
            5 -> Color.rgb(255, 0, 255 - c)     // Magenta to Red
            else -> Color.rgb(255, 0, 0)        // Fallback to Red
        }
    }

    // Apply saturation and brightness (HSV S and V scale) to a rainbow color
    private fun adjustColor(color: Int, saturation: Float, brightness: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[1] *= saturation
        hsv[2] *= brightness
        return Color.HSVToColor(hsv)
    }

    // Create multi-center radial rainbow gradient
    // 3 centers: first letter row 1, last letter row 2, 3rd letter row 3
    private fun createRadialRainbowDrawable(
        startStep: Int,
        saturation: Float,
        brightness: Float
    ): Drawable {
        val numColors = 7

        return object : Drawable() {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            private fun buildColors(phaseOffset: Int): IntArray {
                val colors = IntArray(numColors)
                for (i in 0 until numColors) {
                    val colorStep = startStep + phaseOffset + (i * 1536 / numColors)
                    val color = rainbowColor(colorStep)
                    colors[i] = adjustColor(color, saturation, brightness)
                }
                return colors
            }

            override fun draw(canvas: Canvas) {
                val bounds = bounds
                val w = bounds.width().toFloat()
                val h = bounds.height().toFloat()
                val maxDim = maxOf(w, h)

                // 3 center positions (approximate key positions):
                // Center 1: First letter, row 1 - top left area
                val cx1 = bounds.left + w * 0.12f
                val cy1 = bounds.top + h * 0.15f
                // Center 2: Last letter, row 2 - right middle area
                val cx2 = bounds.left + w * 0.88f
                val cy2 = bounds.top + h * 0.42f
                // Center 3: 3rd letter, row 3 - left-center lower area
                val cx3 = bounds.left + w * 0.28f
                val cy3 = bounds.top + h * 0.68f

                val radius = maxDim * 0.9f
                val positions = FloatArray(numColors) { i -> i.toFloat() / (numColors - 1) }

                // Draw 3 overlapping radial gradients with different phase offsets
                // Using SCREEN blend mode for additive-like blending
                paint.xfermode = null

                // First gradient (base layer)
                val colors1 = buildColors(0)
                paint.shader = RadialGradient(
                    cx1, cy1, radius, colors1, positions,
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(bounds, paint)

                // Second gradient (blend on top)
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
                val colors2 = buildColors(512)  // Phase offset for variety
                paint.shader = RadialGradient(
                    cx2, cy2, radius, colors2, positions,
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(bounds, paint)

                // Third gradient (blend on top)
                val colors3 = buildColors(1024)  // Another phase offset
                paint.shader = RadialGradient(
                    cx3, cy3, radius, colors3, positions,
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(bounds, paint)

                paint.xfermode = null
            }

            override fun setAlpha(alpha: Int) { paint.alpha = alpha }
            override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
            @Suppress("OVERRIDE_DEPRECATION")
            override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
        }
    }

    private fun applyRadialRainbow(startStep: Int, saturation: Float, brightness: Float) {
        val drawable1 = createRadialRainbowDrawable(startStep, saturation, brightness)
        val drawable2 = createRadialRainbowDrawable(startStep, saturation, brightness)
        val drawable3 = createRadialRainbowDrawable(startStep, saturation, brightness)
        val drawable4 = createRadialRainbowDrawable(startStep, saturation, brightness)

        rootView.background = drawable1
        overlayLayer.background = drawable2
        keyboardContainer.background = drawable3
        window?.window?.setBackgroundDrawable(drawable4)

        lastRgbStep = startStep
        lastRgbSaturation = saturation
        lastRgbBrightness = brightness
        if (longPressPopupManager.isPopupShowing) {
            longPressPopupManager.updateRgbBackground(
                createRadialRainbowDrawable(startStep, saturation, brightness)
            )
        }
    }

    private var rgbStep = 0
    private var lastRgbStep = 0
    private var lastRgbSaturation = 1f
    private var lastRgbBrightness = 1f

    private fun startRgbSmooth() {
        stopRgbAnimation()

        val speedMs = KeyboardPrefs.getRgbSmoothSpeed(this)
        val saturation = KeyboardPrefs.getRgbSmoothSaturation(this)
        val brightness = KeyboardPrefs.getRgbSmoothBrightness(this)

        // Calculate delay per step (1536 steps for full rainbow cycle)
        val delayPerStep = (speedMs / 1536f).toLong().coerceAtLeast(8L)

        rgbAnimationJob = serviceScope.launch {
            while (isActive) {
                rgbStep = (rgbStep + 4) % 1536  // Move through rainbow
                applyRadialRainbow(rgbStep, saturation, brightness)
                delay(delayPerStep.milliseconds)
            }
        }
    }

    private fun startRgbWild() {
        stopRgbAnimation()

        val speedMs = KeyboardPrefs.getRgbWildSpeed(this)
        val saturation = KeyboardPrefs.getRgbWildSaturation(this)
        val brightness = KeyboardPrefs.getRgbWildBrightness(this)
        val frameInterval = 16L  // ~60fps for smooth animation
        val transitionDuration = (speedMs * 0.85f).toLong()  // 85% of interval for transition

        // Ease-in-out function for smooth acceleration/deceleration
        fun easeInOutCubic(t: Float): Float {
            return if (t < 0.5f) {
                4f * t * t * t
            } else {
                1f - (-2f * t + 2f).let { it * it * it } / 2f
            }
        }

        rgbAnimationJob = serviceScope.launch {
            var startStep = 0
            var targetStep = rgbRandom.nextInt(1536)
            var transitionStartTime = System.currentTimeMillis()

            while (isActive) {
                val now = System.currentTimeMillis()
                val elapsed = now - transitionStartTime

                // Calculate eased progress
                val rawProgress = (elapsed.toFloat() / transitionDuration).coerceIn(0f, 1f)
                val easedProgress = easeInOutCubic(rawProgress)

                // Calculate shortest path difference
                var diff = targetStep - startStep
                if (diff > 768) diff -= 1536
                if (diff < -768) diff += 1536

                // Interpolate with easing
                val currentStep = (startStep + (diff * easedProgress).toInt() + 1536) % 1536

                applyRadialRainbow(currentStep, saturation, brightness)

                // Pick new target when transition completes
                if (elapsed >= speedMs) {
                    startStep = targetStep
                    targetStep = rgbRandom.nextInt(1536)
                    transitionStartTime = now
                }

                delay(frameInterval.milliseconds)
            }
        }
    }

    private fun stopRgbAnimation() {
        rgbAnimationJob?.cancel()
        rgbAnimationJob = null
    }

    private fun restoreNormalBackground() {
        stopRgbAnimation()

        val bg = KeyboardPrefs.resolveKeyboardBackground(this, lastIsDark == true)

        rootView.setBackgroundColor(bg)
        overlayLayer.setBackgroundColor(bg)
        keyboardContainer.setBackgroundColor(bg)
        window?.window?.setBackgroundDrawable(bg.toDrawable())
    }

    private fun startRgbAnimation() {
        when {
            KeyboardPrefs.isRgbSmoothEnabled(this) -> startRgbSmooth()
            KeyboardPrefs.isRgbWildEnabled(this) -> startRgbWild()
            else -> restoreNormalBackground()
        }
    }

}