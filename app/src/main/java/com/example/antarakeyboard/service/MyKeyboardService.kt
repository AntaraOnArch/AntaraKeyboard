package com.example.antarakeyboard.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.ResultReceiver
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isEmpty
import com.example.antarakeyboard.R
import com.example.antarakeyboard.data.AppLanguageSettings
import com.example.antarakeyboard.data.KeyboardPrefs
import com.example.antarakeyboard.data.PrefsManager
import com.example.antarakeyboard.data.ScriptMapper
import com.example.antarakeyboard.extensions.dp
import com.example.antarakeyboard.model.KeyConfig
import com.example.antarakeyboard.model.KeyMarkers
import com.example.antarakeyboard.model.KeyShape
import com.example.antarakeyboard.model.KeyboardConfig
import com.example.antarakeyboard.model.shiftedBinding
import com.example.antarakeyboard.service.input.KeyInputController
import com.example.antarakeyboard.ui.KeyView
import com.example.antarakeyboard.data.EdgeSlotsStorage
import com.example.antarakeyboard.model.EdgeActionType
import com.example.antarakeyboard.model.EdgeSlot
import com.example.antarakeyboard.ui.defaultKeyboardLayout
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MyKeyboardService : InputMethodService(), EdgeActionCallback {

    /* ───────── STATE ───────── */

    private var isShifted = false
    private var isDrawing = false
    // A redraw requested while one is in progress runs right after it instead of being dropped
    private var pendingRedraw = false
    private var isPasswordInput = false
    /** Double space → period is possible in this field (written text); the setting is read per space. */
    private var doubleSpaceField = false

    // Word suggestions: strip above the keys; the dictionary survives input view recreation
    private val suggestionStrip by lazy { SuggestionStrip(this, serviceScope, SuggestionStripHost()) }
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
            return ensureStableSpaceMarkers(
                KeyboardPrefs.loadNumericLayoutWithGlobalBinds(this, KeyboardPrefs.getLayoutId(this))
            )
        }

    private var lastIsDark: Boolean? = null
    private var lastAppLanguage: String? = null
    private var targetKeyboardHeightPx: Int = 0
    /** Side paddings of the keyboard from its layout (classic shapes use none, see [applySidePadding]). */
    private var overlayBasePadL = 0
    private var overlayBasePadR = 0

    lateinit var inputController: KeyInputController
    private val landscapeBuilder by lazy { LandscapeLayoutBuilder(this, LandscapeHost()) }
    private val classicBuilder by lazy { ClassicLayoutBuilder(this, ClassicHost()) }

    private var alphabetLayoutLower: KeyboardConfig? = null
    private var alphabetLayoutUpper: KeyboardConfig? = null

    private lateinit var emojiPickerManager: EmojiPickerManager

    private val languagePicker by lazy { LanguagePickerPopup(this, LanguagePickerHost()) }

    private var leftSpaceHeld = false
    private var rightSpaceHeld = false
    private var dualSpaceHoldJob: Job? = null
    private var dualSpacePickerWasShown = false

    private companion object {
        const val DUAL_SPACE_HOLD_MS = 4000L
        /** A hide this soon after an input started is the app restarting (rotation), not the user. */
        const val INPUT_RESTART_HIDE_MS = 500L
        /**
         * A hide or input restart this close to a rotation (before or after it) belongs to the
         * rotation: the app may hide while its old window goes away, before the new config arrives.
         */
        const val ROTATION_WINDOW_MS = 1500L
    }

    /**
     * Text field the user left the emoji panel open in (null = closed). It stays open there through
     * rotations and input restarts until its X button or another text field closes it.
     */
    private var emojiOpenInEditor: String? = null
    /** When the current input connection started (see hideSoftInput). */
    private var inputStartedAt = 0L
    /** When the last configuration change (rotation) arrived. */
    private var configChangedAt = -ROTATION_WINDOW_MS
    /** A hide that may still turn out to be part of a rotation; it closes the panel if none follows. */
    private var pendingEmojiCloseJob: Job? = null

    // RGB animation
    private val rgbAnimator by lazy { RgbBackgroundAnimator(this, serviceScope, ::applyRgbFrame) }
    /* ───────── LIFECYCLE ───────── */
    //Claude sync

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
        overlayLayer.addView(
            suggestionStrip.createView(),
            0,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                suggestionStrip.heightPx,
                Gravity.TOP
            )
        )
        suggestionStrip.update()

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
            isPortraitProvider = { isPortrait() },
            currentShapeProvider = { currentShape },
            keyHeightStretchProvider = { keyHeightFactor() },
            rowSpacingPxProvider = { rowSpacingPx() },
            colorsProvider = { anchor -> longPressPopupColors(anchor) },
            rgbBackgroundProvider = { if (rgbAnimator.isRunning) rgbAnimator.newDrawable() else null }
        )

        edgeOverlayManager = EdgeOverlayManager(
            context = this,
            overlayLayerProvider = { overlayLayer },
            keyboardContainerProvider = { keyboardContainer },
            themedCtxProvider = { themedCtx },
            landscapeKeySizePxProvider = { landscapeBuilder.sideButtonSizePx() },
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
                    closeEmojiPicker()
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

        overlayBasePadL = overlayLayer.paddingLeft
        overlayBasePadR = overlayLayer.paddingRight
        val basePadT = overlayLayer.paddingTop
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
            v.setPadding(sidePaddingLeft(), basePadT, sidePaddingRight(), basePadB + bottomInset)

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
        doubleSpaceField = InputTypes.allowsDoubleSpacePeriod(info)
        keyPreviewManager.setEnabled(!isPasswordInput)

        // First: a language changed elsewhere switches the script, which the dictionary follows
        KeyboardPrefs.ensureDefaultLongPress(this)

        // Word suggestions (main app switch); nothing in passwords, numbers, e-mails or URLs
        suggestionStrip.startInput(info)

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
        // Another text field closes the emoji panel; in the same one it comes back. Right after a
        // rotation the app's recreated field may report another id, so the same app is enough
        val key = editorKey(info)
        if (emojiOpenInEditor != key) {
            val sameAppAfterRotation = recentlyRotated() &&
                emojiOpenInEditor?.substringBefore('/') == info?.packageName
            emojiOpenInEditor = if (sameAppAfterRotation) key else null
        }
        restoreEmojiPanel()
    }
    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        resetTransientState()
        suggestionStrip.clear()
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
        suggestionStrip.refresh()
    }

    /** The suggestion strip's view of this service. */
    private inner class SuggestionStripHost : SuggestionStrip.Host {
        override val themedContext: Context get() = themedCtx
        override val inputConnection: InputConnection? get() = currentInputConnection
        override fun textColor() = emojiPickerColors().text
        override fun performHapticFeedback(view: View) = hapticManager.performHapticFeedback(view)
        override fun onSuggestionApplied() = deleteRestoreManager.clearRestoreBuffer()
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
    // Language splits are disabled in build.gradle.kts (bundle.language.enableSplit), so every
    // translation is installed and no Play Core download is needed
    @SuppressLint("AppBundleLocaleChanges")
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
        languagePicker.hide()
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
        configChangedAt = SystemClock.uptimeMillis()
        // A hide that came just before the rotation was the app's old window going away
        pendingEmojiCloseJob?.cancel()

        // Recreate (onCreateInputView releases popups and jobs of the old view) keyboard view to adapt to new configuration
        recreateInputView()
        restoreEmojiPanel()
    }

    private fun resetTransientState() {
        isShifted = false

        if (::deleteRestoreManager.isInitialized) deleteRestoreManager.resetState()
        if (::emojiPickerManager.isInitialized) hideEmojiPopup()
        if (::longPressPopupManager.isInitialized) hideLongPressPopup()
        if (::keyPreviewManager.isInitialized) keyPreviewManager.hide()
        languagePicker.hide()
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
            languageHoldSpacesHeld() &&
            dualSpaceHoldJob == null &&
            !languagePicker.isShowing
        ) {
            dualSpacePickerWasShown = false

            dualSpaceHoldJob = serviceScope.launch {
                delay(DUAL_SPACE_HOLD_MS.milliseconds)
                dualSpaceHoldJob = null

                if (languageHoldSpacesHeld()) {
                    dualSpacePickerWasShown = true
                    languagePicker.show()
                }
            }
        }
    }

    private fun handleDualSpaceUpOrCancel(key: KeyConfig): Boolean {
        if (!isDualSpaceKey(key)) return false

        val shouldConsumeSpace =
            dualSpacePickerWasShown || languagePicker.isShowing

        if (isLeftSpace(key)) {
            leftSpaceHeld = false
        }

        if (isRightSpace(key)) {
            rightSpaceHeld = false
        }

        if (!languageHoldSpacesHeld()) {
            cancelDualSpaceHoldTimer()
        }

        if (!leftSpaceHeld && !rightSpaceHeld && !languagePicker.isShowing) {
            dualSpacePickerWasShown = false
        }

        return shouldConsumeSpace
    }

    /**
     * The language picker opens after holding both space keys, or the only one
     * (classic layouts have a single space key).
     */
    private fun languageHoldSpacesHeld(): Boolean {
        val spaces = currentKeyboardConfig.rows.sumOf { row -> row.keys.count { it.label == " " } }
        return if (spaces < 2) leftSpaceHeld || rightSpaceHeld else leftSpaceHeld && rightSpaceHeld
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
        // Numbers the space keys in reading order: the first is the left space
        val spaceIndices = generateSequence(0) { it + 1 }.iterator()

        fun fixKey(key: KeyConfig): KeyConfig {
            if (key.label != " ") return key

            val thisSpaceIndex = spaceIndices.next()

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
            currentShape.drawShape
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
            currentShape.drawShape
        }
    }


    private fun activeAlphabetBaseLayout(): KeyboardConfig {
        val layout = KeyboardPrefs.loadAlphabetLayoutWithGlobalBinds(this, KeyboardPrefs.getLayoutId(this))
        // Classic + numbers with its number row switched off: the first row is left out
        if (KeyboardPrefs.hidesNumberRow(this) && layout.rows.size > 1) layout.rows.removeAt(0)
        return ensureStableSpaceMarkers(
            layout
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

    /** The language picker's view of this service. */
    private inner class LanguagePickerHost : LanguagePickerPopup.Host {
        override val themedContext: Context get() = themedCtx
        override val rootView: View get() = this@MyKeyboardService.rootView
        override fun colors() = emojiPickerColors()
        override fun onShow() {
            hideLongPressPopup()
            hideEmojiPopup()
            cancelDualSpaceHoldTimer()
            dualSpacePickerWasShown = true
        }
        override fun onDismissed() {
            if (!leftSpaceHeld && !rightSpaceHeld) {
                dualSpacePickerWasShown = false
            }
        }
        override fun onLanguagePicked(tag: String) = applyLanguageFromPicker(tag)
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
        languagePicker.hide()
        // Another script types another language: switch the suggestion dictionary too
        suggestionStrip.onScriptChanged()
        redrawKeyboard()
    }



    // Back hides the keyboard as usual and forgets an open emoji panel, so the keyboard comes back
    // without it (rotations and input restarts never send Back, so they keep the panel)
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && emojiOpenInEditor != null) closeEmojiPicker()
        return super.onKeyDown(keyCode, event)
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        inputStartedAt = SystemClock.uptimeMillis()
    }

    override fun onWindowShown() {
        super.onWindowShown()
        restoreEmojiPanel()
    }

    /**
     * The keyboard hidden on purpose (the navigation bar's hide button, or the app) forgets an open
     * emoji panel like Back does, so it comes back with the letters. Some apps (Google search) also
     * hide it while they restart after a rotation – right after the new input started
     * ([onStartInput]), or even before the new configuration reaches the keyboard
     * ([onConfigurationChanged]); such a hide keeps the panel. A hide with no rotation around it
     * closes the panel once [ROTATION_WINDOW_MS] has passed without one. This is not a limit on how
     * long the panel stays open: a rotation keeps it however long it has been open.
     */
    // Deprecated, but the only hook for a deliberate hide; there is no replacement.
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onCreateInputMethodInterface(): AbstractInputMethodImpl = object : InputMethodImpl() {
        override fun hideSoftInput(flags: Int, resultReceiver: ResultReceiver?) {
            if (emojiOpenInEditor != null && !partOfRotation()) {
                pendingEmojiCloseJob?.cancel()
                pendingEmojiCloseJob = serviceScope.launch {
                    delay(ROTATION_WINDOW_MS.milliseconds)
                    closeEmojiPicker()
                }
            }
            super.hideSoftInput(flags, resultReceiver)
        }
    }

    private fun recentlyRotated() = SystemClock.uptimeMillis() - configChangedAt < ROTATION_WINDOW_MS

    /** An app restarting after a rotation hides right after its new input started, or around the rotation. */
    private fun partOfRotation() =
        SystemClock.uptimeMillis() - inputStartedAt < INPUT_RESTART_HIDE_MS || recentlyRotated()

    private fun showEmojiPicker() {
        // A delete/restore swipe must never keep running behind the picker
        stopSwipeGestures()
        hideLongPressPopup()
        hideEmojiPopup()

        pendingEmojiCloseJob?.cancel()
        emojiPickerManager.show()
        emojiOpenInEditor = editorKey(currentInputEditorInfo)
    }

    private fun closeEmojiPicker() {
        pendingEmojiCloseJob?.cancel()
        emojiOpenInEditor = null
        hideEmojiPopup()
    }

    private fun editorKey(info: EditorInfo?): String = "${info?.packageName}/${info?.fieldId}"

    /**
     * Shows the emoji panel again on the current input view if the user left it open. Rotating
     * recreates the view, and some apps (e.g. Google search) also restart the input a moment later,
     * so this runs after each of those steps; a panel already showing is left alone.
     */
    private fun restoreEmojiPanel() {
        if (emojiOpenInEditor == null) return
        overlayLayer.post {
            if (emojiOpenInEditor != null && ::emojiPickerManager.isInitialized && !emojiPickerManager.isShowing) {
                showEmojiPicker()
            }
        }
    }






    private fun isPortrait() =
        resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    private fun isLandscape() =
        resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun computeTargetKeyboardHeight(): Int {
        val screenH = resources.displayMetrics.heightPixels
        val savedRowCount = KeyboardPrefs.getRowCount(this)

        return if (isPortrait()) {
            portraitKeyboardHeight(screenH)
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

    private fun portraitKeyboardHeight(screenH: Int): Int =
        (screenH * 0.36f).roundToInt().coerceAtLeast(230.dp(this))

    private fun totalVisibleRows(): Int {
        var rows = currentKeyboardConfig.rows.size
        if (currentKeyboardConfig.specialLeft.isNotEmpty()) rows += 1
        if (currentKeyboardConfig.specialRight.isNotEmpty()) rows += 1
        return rows.coerceAtLeast(1)
    }

    private fun keyHeight(): Int {
        val containerH = (targetKeyboardHeightPx + lastBottomInsetPx).takeIf { it > 0 }
            ?: computeTargetKeyboardHeight()

        val usableH = (containerH - overlayLayer.paddingTop - overlayLayer.paddingBottom)
            .coerceAtLeast(120.dp(this))

        return KeySizing.keyHeight(sizingSpec(), totalVisibleRows(), usableH)
    }

    private fun sizingSpec() = KeySizing.Spec(
        rowCount = KeyboardPrefs.getRowCount(this),
        shape = currentShape.drawShape,
        landscape = isLandscape(),
        density = resources.displayMetrics.density
    )

    /** Classic shapes reach from edge to edge (their side buttons sit at the screen edge). */
    private fun sidePaddingLeft() = if (currentShape.isClassic) 0 else overlayBasePadL
    private fun sidePaddingRight() = if (currentShape.isClassic) 0 else overlayBasePadR

    private fun applySidePadding() {
        val l = sidePaddingLeft()
        val r = sidePaddingRight()
        if (overlayLayer.paddingLeft != l || overlayLayer.paddingRight != r) {
            overlayLayer.setPadding(l, overlayLayer.paddingTop, r, overlayLayer.paddingBottom)
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
        val stripH = if (suggestionStrip.isVisible) {
            suggestionStrip.heightPx
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

        if (out == " ") commitSpace() else currentInputConnection?.commitText(out, 1)
    }

    /** A space; right after "word " it turns that space into ". " (double space → period). */
    private fun commitSpace() {
        val ic = currentInputConnection ?: return
        // Read now: the switch may change while the keyboard stays open in this field
        if (doubleSpaceField && KeyboardPrefs.isDoubleSpacePeriodEnabled(this) &&
            DoubleSpacePeriod.shouldReplace(ic.getTextBeforeCursor(2, 0))
        ) {
            ic.beginBatchEdit()
            ic.deleteSurroundingText(1, 0)
            ic.commitText(". ", 1)
            ic.endBatchEdit()
        } else {
            ic.commitText(" ", 1)
        }
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

    /* ───────── LAYOUT ───────── */

    /**
     * Row sizing with the key height slider applied (see [KeySizing.baseRowSizing] for the tuned defaults).
     * The keys themselves grow ([KeyView.heightStretch]); sizes and row spacing stay as tuned.
     * Only the honeycomb overlap grows with the hexagon tips so the rows stay interlocked.
     */
    private fun computeRowSizing(count: Int, availW: Int): KeySizing.RowSizing {
        val base = KeySizing.baseRowSizing(sizingSpec(), count, availW, ::keyHeight)
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

    /** Height stretch the keys are drawn with (portrait or landscape). */
    private fun keyHeightStretch(): Float =
        if (isLandscape()) landscapeKeyHeightStretch() else portraitKeyHeightStretch()

    /**
     * Portrait: the slider factor times the layout's own default.
     * 3-row portrait triangles are taller by default (what used to be 150 % on the slider).
     */
    private fun portraitKeyHeightStretch(): Float {
        val threeRows = KeyboardPrefs.getRowCount(this) == 3
        val layoutDefault = when {
            threeRows && currentShape == KeyShape.TRIANGLE -> KeyScale.THREE_ROW_TRIANGLE_DEFAULT
            threeRows && currentShape == KeyShape.CUBE -> KeyScale.THREE_ROW_CUBE_DEFAULT
            else -> 1f
        }
        return keyHeightFactor() * layoutDefault
    }

    /**
     * Landscape keys are drawn exactly as tall as the portrait keys (key height slider included):
     * the stretch turns the shape height of the square landscape key into the portrait one.
     */
    private fun landscapeKeyHeightStretch(): Float {
        var w = landscapeBuilder.keySizePx().toFloat()
        var h = landscapeBuilder.keyBoxHeightPx().toFloat()
        if (keysForcedSquare(landscape = true) && landscapeShape() != KeyShape.HEX_TALL) {
            w = minOf(w, h)
            h = w
        }
        val base = KeyView.shapeHeight(landscapeShape(), w, h)
        if (base <= 0f) return keyHeightFactor()
        return portraitKeyShapeHeightPx() / base
    }

    /**
     * Row sizing of the widest portrait row, computed the way portrait builds its rows
     * (screen sides swapped), so it is the same whichever orientation asks.
     */
    private fun portraitRowSizing(): KeySizing.RowSizing {
        val dm = resources.displayMetrics
        // Same width as availableKeyboardWidthPx() in portrait (the side paddings do not rotate)
        val portraitW = (minOf(dm.widthPixels, dm.heightPixels) -
            overlayLayer.paddingLeft - overlayLayer.paddingRight).coerceAtLeast(200.dp(this))
        val portraitH = maxOf(dm.widthPixels, dm.heightPixels)
        val spec = sizingSpec().copy(landscape = false)
        val keyH = KeySizing.keyHeight(spec, totalVisibleRows(), portraitKeyboardHeight(portraitH))
        val widestRow = currentKeyboardConfig.rows.maxOfOrNull { row ->
            row.keys.count { !it.longPressBindings.contains(KeyMarkers.EDGE_GHOST) }
        }?.coerceAtLeast(1) ?: 1
        return KeySizing.baseRowSizing(spec, widestRow, portraitW) { keyH }
    }

    /** Drawn shape height of the portrait keys at 100 % on the key height slider (px). */
    private fun portraitBaseShapeHeightPx(): Float {
        val sizing = portraitRowSizing()
        val shape = effectiveShape()
        var w = sizing.keyW.toFloat()
        var h = sizing.keyH.toFloat()
        if (keysForcedSquare(landscape = false) && shape != KeyShape.HEX_TALL) {
            w = minOf(w, h)
            h = w
        }
        return KeyView.shapeHeight(shape, w, h)
    }

    /** Drawn key height of the portrait layout (px), key height slider included. */
    private fun portraitKeyShapeHeightPx(): Float =
        portraitBaseShapeHeightPx() * portraitKeyHeightStretch()

    /** Extra vertical space between rows from the row spacing slider (px, may be negative). */
    private fun rowSpacingPx(): Int =
        KeyScale.clampRowSpacing(KeyboardPrefs.getRowSpacingDp(this)).dp(this)

    /**
     * Whether keys are drawn square (KeyView.forceSquare). Only portrait triangles
     * and the 3-row portrait cube use their own height; HEX_TALL is never squared.
     */
    private fun keysForcedSquare(landscape: Boolean = isLandscape()): Boolean {
        val shape = effectiveShape()
        if (shape == KeyShape.HEX_TALL) return false
        val rows = KeyboardPrefs.getRowCount(this)
        val allowNonSquare = if (landscape) {
            // Landscape triangles get a box just as tall as the triangle (LandscapeLayoutBuilder)
            shape == KeyShape.TRIANGLE
        } else {
            shape == KeyShape.TRIANGLE || (rows == 3 && shape == KeyShape.CUBE)
        }
        return !allowNonSquare
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
            applySidePadding()
            if (currentShape.isClassic) {
                // Side buttons are keys inside the rows (ClassicLayoutBuilder)
                edgeOverlayManager.clearEdgeSlots()
                classicBuilder.build()
                keyboardContainer.post {
                    syncOverlayHeightToContent()
                    finishRedraw()
                }
                return@launch
            }
            if (isLandscape()) {
                landscapeBuilder.build()

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
                                    3 -> -(6.dp(this@MyKeyboardService))    // 3-row: rows a little closer
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
        deleteRestoreManager.clearRestoreBuffer()
        commitSpace()
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
    private val keyTouchHost by lazy { KeyTouchHost() }

    /** The key touch handler's view of this service. */
    private inner class KeyTouchHost : KeyTouchListener.Host {
        override val serviceScope get() = this@MyKeyboardService.serviceScope
        override val inputController get() = this@MyKeyboardService.inputController
        override val hapticManager get() = this@MyKeyboardService.hapticManager
        override val keyPreviewManager get() = this@MyKeyboardService.keyPreviewManager
        override val longPressPopupManager get() = this@MyKeyboardService.longPressPopupManager
        override val bothSpacesHeld get() = leftSpaceHeld && rightSpaceHeld
        override fun isAlphabetLayoutActive() = this@MyKeyboardService.isAlphabetLayoutActive()
        override fun hideLongPressPopup() = this@MyKeyboardService.hideLongPressPopup()
        override fun hideEmojiPopup() = this@MyKeyboardService.hideEmojiPopup()
        override fun showLongPressPopup(anchor: View, chars: List<String>) =
            this@MyKeyboardService.showLongPressPopup(anchor, chars)
        override fun moveLpSelection(dx: Int, dy: Int) = this@MyKeyboardService.moveLpSelection(dx, dy)
        override fun showEmojiPicker() = this@MyKeyboardService.showEmojiPicker()
        override fun handleDualSpaceDown(key: KeyConfig) = this@MyKeyboardService.handleDualSpaceDown(key)
        override fun handleDualSpaceUpOrCancel(key: KeyConfig) =
            this@MyKeyboardService.handleDualSpaceUpOrCancel(key)
        override fun stopSwipeGestures() = this@MyKeyboardService.stopSwipeGestures()
        override fun toggleShift() = this@MyKeyboardService.toggleShift()
        override fun commitText(text: String) {
            currentInputConnection?.commitText(text, 1)
        }
    }

    /** The landscape builder's view of this service. */
    private inner class LandscapeHost : LandscapeLayoutBuilder.Host {
        override val keyboardContainer get() = this@MyKeyboardService.keyboardContainer
        override val themedContext: Context get() = themedCtx
        override val currentShape get() = this@MyKeyboardService.currentShape
        override val currentRows get() = currentKeyboardConfig.rows
        override fun effectiveShape() = this@MyKeyboardService.effectiveShape()
        override fun landscapeShape() = this@MyKeyboardService.landscapeShape()
        override fun keyHeightStretch() = this@MyKeyboardService.keyHeightStretch()
        override fun portraitRowSizing() = this@MyKeyboardService.portraitRowSizing()
        override fun portraitBaseShapeHeightPx() = this@MyKeyboardService.portraitBaseShapeHeightPx()
        override fun rowSpacingPx() = this@MyKeyboardService.rowSpacingPx()
        override fun createKey(key: KeyConfig) = this@MyKeyboardService.createKey(key)
        override fun keyColors(label: String?) = this@MyKeyboardService.keyColors(label)
        override fun applySpecialKeyColors(kv: KeyView, key: KeyConfig, spaceIndex: Int) =
            this@MyKeyboardService.applySpecialKeyColors(kv, key, spaceIndex)
        override fun hidePopups() {
            hideLongPressPopup()
            hideEmojiPopup()
        }
        override fun commitText(text: String) {
            currentInputConnection?.commitText(text, 1)
        }
    }

    /** The classic layout builder's view of this service. */
    private inner class ClassicHost : ClassicLayoutBuilder.Host {
        override val keyboardContainer get() = this@MyKeyboardService.keyboardContainer
        override val currentShape get() = this@MyKeyboardService.currentShape
        override val currentRows get() = currentKeyboardConfig.rows
        override val landscape get() = isLandscape()
        override fun availableWidthPx() = availableKeyboardWidthPx()
        override fun keyHeightFactor() = this@MyKeyboardService.keyHeightFactor()
        override fun rowSpacingPx() = this@MyKeyboardService.rowSpacingPx()
        override fun sideSlots() = EdgeSlotsStorage.load(this@MyKeyboardService)
            .filter { it.type != EdgeActionType.NONE }
        override fun createKey(key: KeyConfig) = this@MyKeyboardService.createKey(key)
        override fun applySpecialKeyColors(kv: KeyView, key: KeyConfig, spaceIndex: Int) =
            this@MyKeyboardService.applySpecialKeyColors(kv, key, spaceIndex)
        override fun styleAsSideButton(kv: KeyView) {
            val (fill, icon) = edgeOverlayManager.sideButtonColors()
            kv.customBgColor = fill
            kv.setTextColor(icon)
            kv.customStrokeColor = icon
            kv.customStrokeWidthPx = 1f
        }
        override fun createSideButton(slot: EdgeSlot) =
            edgeOverlayManager.createRowSideButton(slot, isLandscape())
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

        val rowCount = KeyboardPrefs.getVisibleRowCount(this@MyKeyboardService)

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
                // Classic layouts have their own 😊 key
                if (isAlphabetLayoutActive() && rowCount >= 4 && !currentShape.isClassic) {
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
            KeyShape.CUBE, KeyShape.CLASSIC, KeyShape.CLASSIC_NUMBERS -> if (isLandscape()) 14f else 18f
        }

        if (label in setOf("⇧", "⌫", "↵", "123", "ABC", "abc", "😊")) {
            textSize = if (isLandscape()) 13f else 16f
        }

        // Final key colors (incl. Shift's active look) are set by applySpecialKeyColors()
        setTextColor(keyColors(label).second)

        isLongClickable = true
        setOnTouchListener(KeyTouchListener(this@MyKeyboardService, this, keyConfig, rowCount, keyTouchHost))
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

    /* ───────── RGB ANIMATION (RgbBackgroundAnimator) ───────── */

    /** One RGB frame: every surface (and an open long-press popup) gets a fresh drawable. */
    private fun applyRgbFrame() {
        rootView.background = rgbAnimator.newDrawable()
        overlayLayer.background = rgbAnimator.newDrawable()
        keyboardContainer.background = rgbAnimator.newDrawable()
        window?.window?.setBackgroundDrawable(rgbAnimator.newDrawable())
        if (longPressPopupManager.isPopupShowing) {
            longPressPopupManager.updateRgbBackground(rgbAnimator.newDrawable())
        }
    }

    private fun stopRgbAnimation() = rgbAnimator.stop()

    private fun restoreNormalBackground() {
        stopRgbAnimation()

        val bg = KeyboardPrefs.resolveKeyboardBackground(this, lastIsDark == true)

        rootView.setBackgroundColor(bg)
        overlayLayer.setBackgroundColor(bg)
        keyboardContainer.setBackgroundColor(bg)
        window?.window?.setBackgroundDrawable(bg.toDrawable())
    }

    private fun startRgbAnimation() {
        if (!rgbAnimator.start()) restoreNormalBackground()
    }
}