package com.example.antarakeyboard.service

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.TextView
import com.example.antarakeyboard.data.KeyboardPrefs
import com.example.antarakeyboard.data.LongPressPresets
import com.example.antarakeyboard.extensions.dp
import com.example.antarakeyboard.model.KeyConfig
import com.example.antarakeyboard.service.input.KeyInputController
import com.example.antarakeyboard.ui.KeyView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

/**
 * Touch handling of one key: long press popup (slide to choose), swipe up on `.` `?` `123`,
 * the dual-space hold, Shift / emoji on release; taps and horizontal swipes go to
 * [KeyInputController].
 */
class KeyTouchListener(
    private val context: Context,
    private val key: KeyView,
    private val keyConfig: KeyConfig,
    private val rowCount: Int,
    private val host: Host
) : View.OnTouchListener {

    interface Host {
        val serviceScope: CoroutineScope
        val inputController: KeyInputController
        val hapticManager: HapticManager
        val keyPreviewManager: KeyPreviewManager
        val longPressPopupManager: LongPressPopupManager
        /** Both space keys are held (dual-space language picker), so a move is no swipe. */
        val bothSpacesHeld: Boolean
        fun isAlphabetLayoutActive(): Boolean
        fun hideLongPressPopup()
        fun hideEmojiPopup()
        fun showLongPressPopup(anchor: View, chars: List<String>)
        fun moveLpSelection(dx: Int, dy: Int)
        fun showEmojiPicker()
        fun handleDualSpaceDown(key: KeyConfig)
        /** True when the release belonged to the dual-space hold and must not type a space. */
        fun handleDualSpaceUpOrCancel(key: KeyConfig): Boolean
        fun stopSwipeGestures()
        fun toggleShift()
        fun commitText(text: String)
    }

    private val label = keyConfig.label
    private val nonBindable = setOf("⇧", "⌫", "↵", "123", "ABC", "abc", " ", "😊")
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

    private var longPressTriggered = false
    private var startX = 0f
    private var startY = 0f
    private val step = 18.dp(key)
    private var handledBySwipeUp = false
    private val swipeUpThreshold = 26.dp(key)
    private var longPressJob: Job? = null

    override fun onTouch(v: View, e: MotionEvent): Boolean {
        return when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = e.rawX
                startY = e.rawY
                longPressTriggered = false
                handledBySwipeUp = false
                host.hideLongPressPopup()
                host.hideEmojiPopup()

                // Haptic feedback on key press
                host.hapticManager.performHapticFeedback(v)

                // Show key preview
                host.keyPreviewManager.show(v, label)

                if (label == " ") {
                    host.handleDualSpaceDown(keyConfig)
                }

                longPressJob?.cancel()
                if (label !in nonBindable) {
                    longPressJob = host.serviceScope.launch {
                        delay(longPressTimeout.milliseconds)
                        // Latin keyboard: Latin letters only; Cyrillic keyboard: Cyrillic only
                        val binds = LongPressPresets.visibleFor(
                            KeyboardPrefs.getSelectedLongPressPreset(context),
                            keyConfig.longPressBindings
                        )
                        if (binds.isNotEmpty()) {
                            longPressTriggered = true
                            host.keyPreviewManager.hide()
                            host.hapticManager.onLongPress()
                            host.showLongPressPopup(key, binds)
                            host.longPressPopupManager.resetSelection()
                        }
                    }
                }

                host.inputController.handleTouch(v as TextView, e)
                true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = e.rawX - startX
                val dy = e.rawY - startY
                val absDx = abs(dx)
                val absDy = abs(dy)

                // A running delete/restore swipe owns the gesture: no swipe-up shortcuts
                val horizontalSwipe = host.inputController.isHorizontalSwipeActive(v as TextView)

                // 123 swipe-up => emoji, only on the 4/5-row alphabet layout
                if (!longPressTriggered &&
                    !handledBySwipeUp &&
                    !horizontalSwipe &&
                    label == "123" &&
                    rowCount >= 4 &&
                    host.isAlphabetLayoutActive() &&
                    dy < -swipeUpThreshold &&
                    absDy > absDx
                ) {
                    handledBySwipeUp = true
                    longPressJob?.cancel()
                    host.hideLongPressPopup()
                    host.keyPreviewManager.hide()
                    host.showEmojiPicker()
                    v.isPressed = false
                    return true
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
                        host.hideLongPressPopup()
                        host.commitText(swipeText)
                        v.isPressed = false
                        return true
                    }
                }

                if (!longPressTriggered && absDx > 20.dp(key) && absDx > absDy * 1.05f) {
                    longPressJob?.cancel()
                    host.hideLongPressPopup()
                }

                if (longPressTriggered) {
                    if (absDx > absDy && absDx > step) {
                        host.moveLpSelection(if (dx > 0) 1 else -1, 0)
                        startX = e.rawX
                        startY = e.rawY
                    } else if (absDy > step) {
                        host.moveLpSelection(0, if (dy > 0) 1 else -1)
                        startX = e.rawX
                        startY = e.rawY
                    } else if (host.longPressPopupManager.getLpRects().isNotEmpty()) {
                        val rx = e.rawX.toInt()
                        val ry = e.rawY.toInt()
                        val newIdx = host.longPressPopupManager.getLpRects().indexOfFirst { it.contains(rx, ry) }
                        if (newIdx != -1 && newIdx != host.longPressPopupManager.getSelectedIndex()) {
                            host.longPressPopupManager.setSelectedIndex(newIdx)
                        }
                    }
                }

                // Skip swipe gestures when long press popup is active, and while both space
                // keys are held (dual-space script/language picker) – that is no swipe
                if (!longPressTriggered && !host.bothSpacesHeld) {
                    host.inputController.handleTouch(v, e)
                }
                true
            }

            MotionEvent.ACTION_UP -> {
                // Hide key preview
                host.keyPreviewManager.hide()

                if (label == " " && host.handleDualSpaceUpOrCancel(keyConfig)) {
                    host.stopSwipeGestures()
                    v.isPressed = false
                    v.isSelected = false
                    v.clearFocus()
                    longPressJob?.cancel()
                    return true
                }

                if (handledBySwipeUp) {
                    host.stopSwipeGestures()
                    v.isPressed = false
                    v.isSelected = false
                    v.clearFocus()
                    longPressJob?.cancel()
                    return true
                }

                v.performClick()
                v.isSelected = false
                v.clearFocus()
                longPressJob?.cancel()

                if (longPressTriggered) {
                    host.longPressPopupManager.getSelectedChar()?.let { ch ->
                        host.commitText(ch)
                    }
                    host.hideLongPressPopup()
                    v.isPressed = false
                    true
                } else {
                    when (label) {
                        "⇧" -> {
                            v.isPressed = false
                            host.toggleShift()
                            true
                        }

                        "😊" -> {
                            v.isPressed = false
                            host.showEmojiPicker()
                            true
                        }

                        else -> {
                            host.inputController.handleTouch(v as TextView, e)
                            true
                        }
                    }
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                // Hide key preview
                host.keyPreviewManager.hide()

                if (label == " ") {
                    host.handleDualSpaceUpOrCancel(keyConfig)
                }

                handledBySwipeUp = false
                longPressJob?.cancel()

                if (longPressTriggered) {
                    host.hideLongPressPopup()
                }

                v.isPressed = false
                v.isSelected = false
                v.clearFocus()

                // Skip swipe cleanup when long press popup was shown
                if (!longPressTriggered) {
                    host.inputController.handleTouch(v as TextView, e)
                }
                true
            }

            else -> false
        }
    }
}
