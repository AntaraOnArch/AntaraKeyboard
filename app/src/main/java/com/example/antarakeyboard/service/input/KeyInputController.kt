package com.example.antarakeyboard.service.input

import android.view.MotionEvent
import android.widget.TextView
import com.example.antarakeyboard.extensions.dp
import com.example.antarakeyboard.service.MyKeyboardService
import kotlin.math.abs

class KeyInputController(
    private val service: MyKeyboardService
) {

    /**
     * Gesture state per key. With several fingers down (e.g. both space keys) every key has its
     * own start point; a shared one made the first finger look like a long swipe → deletion.
     */
    private class Gesture {
        var downX = 0f
        var downY = 0f
        var horizontalSwipeActive = false
        var swipeMode: SwipeMode? = null
    }

    private val gestures = java.util.WeakHashMap<TextView, Gesture>()

    private enum class SwipeMode {
        DELETE, RESTORE
    }

    /** True while a horizontal delete/restore swipe started on [view] is running. */
    fun isHorizontalSwipeActive(view: TextView): Boolean = gestures[view]?.horizontalSwipeActive == true

    /** Forgets all gestures (the service stops the delete/restore jobs itself). */
    fun reset() {
        gestures.clear()
    }

    fun handleTouch(view: TextView, event: MotionEvent): Boolean {

        val label = view.text?.toString().orEmpty()
        val g = gestures.getOrPut(view) { Gesture() }

        when (event.actionMasked) {

            /* ───────── DOWN ───────── */

            MotionEvent.ACTION_DOWN -> {

                g.downX = event.rawX
                g.downY = event.rawY

                g.horizontalSwipeActive = false
                g.swipeMode = null

                view.isPressed = true

                if (label == "⌫") {
                    service.scheduleBackspaceHold()
                }

                return true
            }

            /* ───────── MOVE ───────── */

            MotionEvent.ACTION_MOVE -> {

                val dx = event.rawX - g.downX
                val dy = event.rawY - g.downY

                val absDx = abs(dx)
                val absDy = abs(dy)

                val horizontalIntent =
                    absDx > 20.dp(service.resources) && absDx > absDy * 1.05f

                if (horizontalIntent) {

                    if (!g.horizontalSwipeActive) {

                        g.horizontalSwipeActive = true
                        view.isPressed = false

                        if (label == "⌫") {
                            service.cancelPendingBackspaceHold()
                            service.stopBackspaceHold()
                        }
                    }

                    if (dx < 0f) {

                        if (g.swipeMode != SwipeMode.DELETE) {
                            g.swipeMode = SwipeMode.DELETE
                            service.startSwipeDelete(absDx)
                        } else {
                            service.updateSwipeDelete(absDx)
                        }

                    } else {

                        if (g.swipeMode != SwipeMode.RESTORE) {
                            g.swipeMode = SwipeMode.RESTORE
                            service.startSwipeRestore(absDx)
                        } else {
                            service.updateSwipeRestore(absDx)
                        }

                    }

                    return true
                }

                return true
            }

            /* ───────── UP ───────── */

            MotionEvent.ACTION_UP -> {

                service.stopSwipeDelete()
                service.stopSwipeRestore()

                if (label == "⌫") {

                    service.cancelPendingBackspaceHold()

                    if (service.isBackspaceHoldRunning()) {
                        service.stopBackspaceHold()
                        view.isPressed = false
                        return true
                    }

                    if (g.horizontalSwipeActive) {
                        g.horizontalSwipeActive = false
                        g.swipeMode = null
                        view.isPressed = false
                        return true
                    }

                    service.backspaceOnce()

                    view.isPressed = false
                    return true
                }

                if (g.horizontalSwipeActive) {
                    g.horizontalSwipeActive = false
                    g.swipeMode = null
                    view.isPressed = false
                    return true
                }

                val dx = event.rawX - g.downX
                val dy = event.rawY - g.downY
                val absDx = abs(dx)
                val absDy = abs(dy)

                if (dy < -24.dp(service.resources) && absDy > absDx) {

                    when {

                        label == "." -> {
                            service.commitText(",")
                            view.isPressed = false
                            return true
                        }

                        label == "?" -> {
                            service.commitText("!")
                            view.isPressed = false
                            return true
                        }

                        label.length == 1 && label[0].isLetter() -> {
                            service.commitExactText(label.uppercase())
                            view.isPressed = false
                            return true
                        }

                    }
                }

                when (label) {

                    "⇧" -> service.toggleShift()

                    "↵" -> service.sendEnter()

                    else -> service.commitText(label)

                }

                view.isPressed = false
                return true
            }

            /* ───────── CANCEL ───────── */

            MotionEvent.ACTION_CANCEL -> {

                service.stopSwipeDelete()
                service.stopSwipeRestore()

                if (label == "⌫") {
                    service.cancelPendingBackspaceHold()
                    service.stopBackspaceHold()
                }

                g.horizontalSwipeActive = false
                g.swipeMode = null

                view.isPressed = false
                return true
            }
        }

        return false
    }
}