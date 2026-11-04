package com.example.antarakeyboard.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isEmpty
import com.example.antarakeyboard.R
import com.example.antarakeyboard.data.EdgePos
import com.example.antarakeyboard.data.EdgeSlotsStorage
import com.example.antarakeyboard.data.KeyboardPrefs
import com.example.antarakeyboard.extensions.dp
import com.example.antarakeyboard.model.EdgeActionType
import com.example.antarakeyboard.model.EdgeSlot
import com.example.antarakeyboard.model.KeyMarkers

/**
 * Callback interface for edge button actions.
 */
interface EdgeActionCallback {
    fun onToggleShift()
    fun onBackspaceOnce()
    fun onSendEnter()
    fun onCommitSpace()
    fun onCommitChar(char: String)
    fun onShowEmojiPicker()
    fun onScheduleBackspaceHold()
    fun onCancelPendingBackspaceHold()
    fun isBackspaceHoldRunning(): Boolean
    fun onStopBackspaceHold()
    fun isShifted(): Boolean
}

/**
 * Manages edge overlay buttons (side buttons) for the keyboard.
 * Extracted from MyKeyboardService for better separation of concerns.
 */
class EdgeOverlayManager(
    private val context: Context,
    private val overlayLayerProvider: () -> FrameLayout,
    private val keyboardContainerProvider: () -> LinearLayout,
    private val themedCtxProvider: () -> Context,
    private val landscapeKeySizePxProvider: () -> Int,
    private val availableKeyboardWidthPxProvider: () -> Int,
    private val computeRowSizingProvider: (count: Int, availW: Int) -> RowSizing,
    private val actionCallback: EdgeActionCallback
) {

    data class RowSizing(
        val keyW: Int,
        val keyH: Int,
        val gapPx: Int,
        val outerPadPx: Int,
        val overlapPx: Int,
        val triOverlapX: Int,
        val triOverlapY: Int
    )

    data class EdgeBinding(
        val visualIndex: Int,
        val side: EdgePos.Side,
        val slot: EdgeSlot
    )

    data class SideButtonTuning(
        val x: Int = 0,
        val y: Int = 0,
        val widthScale: Float = 1f,
        val heightScale: Float = 1f,
        val iconX: Int = 0,
        val iconY: Int = 0,
        val iconTextSizeSp: Float? = null
    )

    /* ───────── CLEAR ───────── */

    fun clearEdgeSlots() {
        val overlayLayer = overlayLayerProvider()
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

    /* ───────── DRAW PORTRAIT EDGE SLOTS ───────── */

    fun drawEdgeSlots() {
        val overlayLayer = overlayLayerProvider()
        val keyboardContainer = keyboardContainerProvider()

        overlayLayer.post {
            clearEdgeSlots()

            if (keyboardContainer.isEmpty()) return@post
            if (overlayLayer.width <= 0 || overlayLayer.height <= 0) {
                overlayLayer.post { drawEdgeSlots() }
                return@post
            }

            val rowCount = KeyboardPrefs.getRowCount(context)
            val liftY = when (rowCount) {
                5 -> 2.dp(context)
                4 -> 12.dp(context)
                else -> 6.dp(context)
            }

            val sizing = computeRowSizingProvider(7, availableKeyboardWidthPxProvider())
            val keyW = sizing.keyW
            val totalRows = keyboardContainer.childCount

            val slotW = when (totalRows) {
                3 -> (keyW * 0.70f).toInt().coerceIn(28.dp(context), 54.dp(context))
                4 -> (keyW * 0.70f).toInt().coerceIn(28.dp(context), 56.dp(context))
                else -> (keyW * 0.72f).toInt().coerceIn(30.dp(context), 58.dp(context))
            }

            val ovLoc = IntArray(2)
            overlayLayer.getLocationOnScreen(ovLoc)

            fun firstKey(row: View): View? {
                val vg = row as? ViewGroup ?: return null
                if (vg.isEmpty()) return null
                return vg.getChildAt(0)
            }

            fun lastKey(row: View): View? {
                val vg = row as? ViewGroup ?: return null
                if (vg.isEmpty()) return null
                return vg.getChildAt(vg.childCount - 1)
            }

            fun addSideButtonAt(
                tag: String,
                slot: EdgeSlot,
                tuning: SideButtonTuning,
                side: EdgePos.Side,
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
                    .coerceAtLeast(18.dp(context))

                overlayLayer.addView(
                    btn,
                    FrameLayout.LayoutParams(width, finalHeight).apply {
                        gravity = Gravity.START

                        // The user's offset applies after the clamp, so it always moves the button
                        leftMargin = left.coerceIn(
                            -sideOutset,
                            overlayLayer.width - width + sideOutset
                        ) + sideOffsetPx(side, landscape = false)

                        topMargin = safeOverlayTop(
                            requestedTop = top + (height - finalHeight) / 2,
                            childHeight = finalHeight
                        )
                    }
                )
            }

            if (totalRows == 5) {
                val visualRows = edgeRowIndices(totalRows)
                val slots = EdgeSlotsStorage.load(context)
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

                    val baseTop = safeOverlayTop(
                        requestedTop = firstLoc[1] - ovLoc[1] - liftY,
                        childHeight = first.height
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
                            .coerceAtLeast(18.dp(context))

                        addSideButtonAt(
                            tag = "edge_slot_left_$visualIndex",
                            slot = slot,
                            tuning = tuning,
                            side = EdgePos.Side.LEFT,
                            left = rowLeft - width + tuning.x.dp(context),
                            top = baseTop + tuning.y.dp(context),
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
                            .coerceAtLeast(18.dp(context))

                        addSideButtonAt(
                            tag = "edge_slot_right_$visualIndex",
                            slot = slot,
                            tuning = tuning,
                            side = EdgePos.Side.RIGHT,
                            left = rowRight + tuning.x.dp(context),
                            top = baseTop + tuning.y.dp(context),
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

                val baseTop = safeOverlayTop(
                    requestedTop = firstLoc[1] - ovLoc[1] - liftY,
                    childHeight = first.height
                )

                val tuning = sideButtonTuning(
                    isLandscapeMode = false,
                    rowCount = totalRows,
                    visualIndex = binding.visualIndex,
                    side = binding.side,
                    slotType = binding.slot.type
                )

                val width = (slotW * tuning.widthScale).toInt()
                    .coerceAtLeast(18.dp(context))

                val left = if (binding.side == EdgePos.Side.LEFT) {
                    rowLeft - width + tuning.x.dp(context)
                } else {
                    rowRight + tuning.x.dp(context)
                }

                addSideButtonAt(
                    tag = if (binding.side == EdgePos.Side.LEFT) {
                        "edge_slot_left_${binding.visualIndex}"
                    } else {
                        "edge_slot_right_${binding.visualIndex}"
                    },
                    slot = binding.slot,
                    tuning = tuning,
                    side = binding.side,
                    left = left,
                    top = baseTop + tuning.y.dp(context),
                    width = width,
                    height = first.height
                )
            }
        }
    }

    /* ───────── DRAW LANDSCAPE SIDE SLOTS ───────── */

    fun drawLandscapeSideSlots() {
        val overlayLayer = overlayLayerProvider()
        val keyboardContainer = keyboardContainerProvider()

        overlayLayer.post {
            if (keyboardContainer.isEmpty()) return@post

            val root = keyboardContainer.getChildAt(0) as? ViewGroup ?: return@post
            if (root.childCount < 3) return@post

            val leftBlock = root.getChildAt(0) as? ViewGroup ?: return@post
            val rightBlock = root.getChildAt(2) as? ViewGroup ?: return@post

            val savedRowCount = KeyboardPrefs.getRowCount(context)

            val slots = EdgeSlotsStorage.load(context)
                .filter { it.type != EdgeActionType.NONE }

            val landscapeBindings = if (savedRowCount == 5) {
                emptyList()
            } else {
                activeEdgeBindings(savedRowCount)
            }

            val ovLoc = IntArray(2)
            overlayLayer.getLocationOnScreen(ovLoc)

            val keySize = landscapeKeySizePxProvider()

            val visualRows = edgeRowIndices(leftBlock.childCount)

            fun rowView(block: ViewGroup, rowIndex: Int): View? {
                if (rowIndex !in 0 until block.childCount) return null
                return block.getChildAt(rowIndex)
            }

            fun firstChild(row: View): View? {
                val vg = row as? ViewGroup ?: return null
                if (vg.isEmpty()) return null
                return vg.getChildAt(0)
            }

            fun lastChild(row: View): View? {
                val vg = row as? ViewGroup ?: return null
                if (vg.isEmpty()) return null
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

                    val sideW = (keySize * tuning.widthScale).toInt()
                        .coerceAtLeast(14.dp(context))

                    val left = leftAnchorLeft - sideW + tuning.x.dp(context)
                    val top = leftLoc[1] - ovLoc[1] + tuning.y.dp(context)

                    val btn = createSideButtonView(
                        tagName = "edge_slot_left_$visualIndex",
                        slot = slot,
                        tuning = tuning,
                        isLandscapeMode = true
                    )

                    val sideH = (leftAnchor.height * tuning.heightScale).toInt()
                        .coerceAtLeast(18.dp(context))

                    overlayLayer.addView(
                        btn,
                        FrameLayout.LayoutParams(sideW, sideH).apply {
                            leftMargin = left.coerceIn(
                                -sideW / 2,
                                overlayLayer.width - sideW
                            ) + sideOffsetPx(EdgePos.Side.LEFT, landscape = true)

                            topMargin = safeOverlayTop(
                                requestedTop = top + (leftAnchor.height - sideH) / 2,
                                childHeight = sideH
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

                    val sideW = (keySize * tuning.widthScale).toInt()
                        .coerceAtLeast(14.dp(context))

                    val left = rightAnchorRight - sideW + tuning.x.dp(context)
                    val top = rightLoc[1] - ovLoc[1] + tuning.y.dp(context)

                    val btn = createSideButtonView(
                        tagName = "edge_slot_right_$visualIndex",
                        slot = slot,
                        tuning = tuning,
                        isLandscapeMode = true
                    )

                    val sideH = (rightAnchor.height * tuning.heightScale).toInt()
                        .coerceAtLeast(18.dp(context))

                    overlayLayer.addView(
                        btn,
                        FrameLayout.LayoutParams(sideW, sideH).apply {
                            leftMargin = left.coerceIn(
                                0,
                                overlayLayer.width - sideW
                            ) + sideOffsetPx(EdgePos.Side.RIGHT, landscape = true)

                            topMargin = safeOverlayTop(
                                requestedTop = top + (rightAnchor.height - sideH) / 2,
                                childHeight = sideH
                            )
                        }
                    )
                }
            }
        }
    }

    /** Main app "side button positions" slider for this side and orientation, in px. */
    private fun sideOffsetPx(side: EdgePos.Side, landscape: Boolean): Int =
        KeyboardPrefs.getSideOffsetDp(context, side, landscape).dp(context)

    /* ───────── PERFORM EDGE ACTION ───────── */

    private fun performEdgeAction(slot: EdgeSlot) {
        when (slot.type) {
            EdgeActionType.SHIFT -> actionCallback.onToggleShift()
            EdgeActionType.BACKSPACE -> actionCallback.onBackspaceOnce()
            EdgeActionType.ENTER -> actionCallback.onSendEnter()
            EdgeActionType.SPACE -> actionCallback.onCommitSpace()
            EdgeActionType.CHAR -> slot.value?.let { actionCallback.onCommitChar(it) }
            EdgeActionType.EMOJI_PICKER -> actionCallback.onShowEmojiPicker()
            EdgeActionType.NONE -> Unit
        }
    }

    /* ───────── CREATE SIDE BUTTON VIEW ───────── */

    // A plain FrameLayout; the touch listener calls performClick on ACTION_UP
    @SuppressLint("ClickableViewAccessibility")
    private fun createSideButtonView(
        tagName: String,
        slot: EdgeSlot,
        tuning: SideButtonTuning,
        isLandscapeMode: Boolean
    ): FrameLayout {
        val themedCtx = themedCtxProvider()
        val isShifted = actionCallback.isShifted()

        // 1. Label
        val label = sideButtonLabel(slot, isShifted)

        // 2. Colors
        val useThemeBg = KeyboardPrefs.getSideButtonsUseThemeBg(context)
        val sideBg = if (useThemeBg) {
            Color.TRANSPARENT
        } else {
            KeyboardPrefs.getSideButtonsBg(context)
        }
        val sideTextColor = if (useThemeBg) {
            themeColor(themedCtx, R.attr.edgeIconText, Color.WHITE)
        } else {
            KeyboardPrefs.getSideButtonsTextColor(context)
        }

        // 3. FrameLayout
        val box = FrameLayout(context).apply {
            tag = tagName

            if (!useThemeBg) {
                setBackgroundColor(sideBg)
            } else {
                setBackgroundColor(Color.TRANSPARENT)
            }

            isClickable = true
            isFocusable = false
            isFocusableInTouchMode = false
            isSelected = false
        }

        // 4. TextView with label
        val icon = TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(
                if (slot.type == EdgeActionType.SHIFT && isShifted) {
                    edgeIconActiveColor(themedCtx)
                } else {
                    sideTextColor
                }
            )

            textSize = tuning.iconTextSizeSp ?: when {
                slot.type == EdgeActionType.SHIFT && isLandscapeMode -> 17f
                slot.type == EdgeActionType.SHIFT -> 21f
                isLandscapeMode -> 13.5f
                else -> 13f
            }

            translationX = tuning.iconX.dp(context).toFloat()
            translationY = tuning.iconY.dp(context).toFloat()

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
                        actionCallback.onScheduleBackspaceHold()
                    }

                    true
                }

                MotionEvent.ACTION_UP -> {
                    view.performClick()
                    view.alpha = 1f

                    if (slot.type == EdgeActionType.BACKSPACE) {
                        actionCallback.onCancelPendingBackspaceHold()

                        if (actionCallback.isBackspaceHoldRunning()) {
                            actionCallback.onStopBackspaceHold()
                        } else {
                            actionCallback.onBackspaceOnce()
                        }
                    } else {
                        performEdgeAction(slot)
                    }

                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    view.alpha = 1f

                    if (slot.type == EdgeActionType.BACKSPACE) {
                        actionCallback.onCancelPendingBackspaceHold()
                        actionCallback.onStopBackspaceHold()
                    }

                    true
                }

                else -> false
            }
        }

        return box
    }

    /* ───────── HELPER FUNCTIONS ───────── */

    private fun safeOverlayTop(
        requestedTop: Int,
        childHeight: Int
    ): Int {
        val overlayLayer = overlayLayerProvider()
        val minTop = 2.dp(context)

        val maxTop = (
                overlayLayer.height -
                        childHeight -
                        2.dp(context)
                ).coerceAtLeast(minTop)

        return requestedTop.coerceIn(minTop, maxTop)
    }

    private fun edgeRowIndices(totalRows: Int): List<Int> = sideButtonKeyRows(totalRows)

    private fun activeEdgeBindings(totalRows: Int): List<EdgeBinding> =
        edgeBindings(totalRows, EdgeSlotsStorage.load(context))

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
                    0 -> SideButtonTuning(x = -18, y = -5, widthScale = 0.48f, heightScale = 0.58f, iconX = 0, iconY = 0)
                    1 -> SideButtonTuning(x = -18, y = -3, widthScale = 0.48f, heightScale = 0.58f, iconX = 0, iconY = 0)
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
                    1 -> SideButtonTuning(x = -17, y = -6, widthScale = 0.8f, heightScale = 0.4f, iconX = 5, iconY = 0)
                    else -> SideButtonTuning()
                }

                EdgePos.Side.RIGHT -> when (visualIndex) {
                    0 -> SideButtonTuning(x = 22, y = -8, widthScale = 0.8f, heightScale = 0.4f, iconX = -3, iconY = 0)
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

    private fun themeColor(ctx: Context, attr: Int, fallback: Int): Int {
        val tv = TypedValue()
        val th = ctx.theme
        return if (
            th.resolveAttribute(attr, tv, true) &&
            tv.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT
        ) {
            tv.data
        } else {
            fallback
        }
    }

    private fun edgeIconTextColor(ctx: Context): Int =
        themeColor(ctx, R.attr.edgeIconText, 0xFFFFFFFF.toInt())

    private fun edgeIconActiveColor(ctx: Context): Int =
        themeColor(ctx, R.attr.edgeIconTextActive, edgeIconTextColor(ctx))

    companion object {
        /** Key rows that carry side buttons: all rows with 3 or 4 rows, rows 0, 2, 4 with 5. */
        fun sideButtonKeyRows(totalRows: Int): List<Int> = when {
            totalRows <= 0 -> emptyList()
            totalRows == 3 -> listOf(0, 1, 2)
            totalRows == 4 -> listOf(0, 1, 2, 3)
            totalRows == 5 -> listOf(0, 2, 4)
            else -> listOf(0, totalRows / 2, totalRows - 1).distinct()
        }

        /**
         * Which side button goes where. [EdgeBinding.visualIndex] indexes [sideButtonKeyRows].
         * 3 and 4 rows: slot i on row i, sides alternate right, left, right(, left).
         * 5 rows: slots 0/1 on the first side-button row, 2/3 on the second, 4/5 on the third,
         * each on its own stored side. Empty slots are left out.
         */
        fun edgeBindings(totalRows: Int, slots: List<EdgeSlot>): List<EdgeBinding> = when (totalRows) {
            3, 4 -> (0 until totalRows).mapNotNull { row ->
                val slot = slots.getOrNull(row)?.takeIf { it.type != EdgeActionType.NONE }
                    ?: return@mapNotNull null
                val side = if (row % 2 == 0) EdgePos.Side.RIGHT else EdgePos.Side.LEFT
                EdgeBinding(visualIndex = row, side = side, slot = slot.copy(side = side))
            }
            else -> slots
                .filter { it.type != EdgeActionType.NONE }
                .map { slot ->
                    EdgeBinding(visualIndex = (slot.index / 2).coerceIn(0, 2), side = slot.side, slot = slot)
                }
        }

        /** The symbol a side button shows (Shift in its off state). */
        fun sideButtonLabel(slot: EdgeSlot, isShifted: Boolean = false): String = when (slot.type) {
            EdgeActionType.SHIFT -> if (isShifted) KeyMarkers.SHIFT_ON else KeyMarkers.SHIFT_OFF
            EdgeActionType.BACKSPACE -> "⌫"
            EdgeActionType.ENTER -> "↵"
            EdgeActionType.SPACE -> "␣"
            EdgeActionType.CHAR -> slot.value ?: ""
            EdgeActionType.EMOJI_PICKER -> "😊"
            EdgeActionType.NONE -> ""
        }
    }
}
