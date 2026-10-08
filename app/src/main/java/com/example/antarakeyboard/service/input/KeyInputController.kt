package com.example.antarakeyboard.service.input

import android.view.MotionEvent
import android.widget.TextView
import com.example.antarakeyboard.extensions.dp
import com.example.antarakeyboard.service.MyKeyboardService
import kotlin.math.abs

class KeyInputController(
    private val service: MyKeyboardService
) {

    private var downX = 0f
    private var downY = 0f

    private var horizontalSwipeActive = false
    private var swipeMode: SwipeMode? = null

    private enum class SwipeMode {
        DELETE, RESTORE
    }

    /** True while a horizontal delete/restore swipe is running. */
    fun isHorizontalSwipeActive(): Boolean = horizontalSwipeActive

    /** Forgets the current gesture (the service stops the delete/restore jobs itself). */
    fun reset() {
        horizontalSwipeActive = false
        swipeMode = null
    }

    fun handleTouch(view: TextView, event: MotionEvent): Boolean {

        val label = view.text?.toString().orEmpty()

        when (event.actionMasked) {

            /* ───────── DOWN ───────── */

            MotionEvent.ACTION_DOWN -> {

                downX = event.rawX
                downY = event.rawY

                horizontalSwipeActive = false
                swipeMode = null

                view.isPressed = true

                if (label == "⌫") {
                    service.scheduleBackspaceHold()
                }

                return true
            }

            /* ───────── MOVE ───────── */

            MotionEvent.ACTION_MOVE -> {

                val dx = event.rawX - downX
                val dy = event.rawY - downY

                val absDx = abs(dx)
                val absDy = abs(dy)

                val horizontalIntent =
                    absDx > 20.dp(service.resources) && absDx > absDy * 1.05f

                if (horizontalIntent) {

                    if (!horizontalSwipeActive) {

                        horizontalSwipeActive = true
                        view.isPressed = false

                        if (label == "⌫") {
                            service.cancelPendingBackspaceHold()
                            service.stopBackspaceHold()
                        }
                    }

                    if (dx < 0f) {

                        if (swipeMode != SwipeMode.DELETE) {
                            swipeMode = SwipeMode.DELETE
                            service.startSwipeDelete(absDx)
                        } else {
                            service.updateSwipeDelete(absDx)
                        }

                    } else {

                        if (swipeMode != SwipeMode.RESTORE) {
                            swipeMode = SwipeMode.RESTORE
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

                    if (horizontalSwipeActive) {
                        horizontalSwipeActive = false
                        swipeMode = null
                        view.isPressed = false
                        return true
                    }

                    service.backspaceOnce()

                    view.isPressed = false
                    return true
                }

                if (horizontalSwipeActive) {
                    horizontalSwipeActive = false
                    swipeMode = null
                    view.isPressed = false
                    return true
                }

                val dx = event.rawX - downX
                val dy = event.rawY - downY
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

                horizontalSwipeActive = false
                swipeMode = null

                view.isPressed = false
                return true
            }
        }

        return false
    }
}