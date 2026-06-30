package com.example.antarakeyboard.ui

import android.content.ClipData
import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.example.antarakeyboard.data.KeyboardPrefs
import com.example.antarakeyboard.model.KeyConfig
import com.example.antarakeyboard.model.KeyMarkers
import com.example.antarakeyboard.model.KeyShape
import com.example.antarakeyboard.model.KeyboardConfig
import com.example.antarakeyboard.model.ensureSpaceMarkers

class LayoutEditorBinder(
    private val context: Context,
    initial: KeyboardConfig,
    private val onSaved: (KeyboardConfig) -> Unit,
    private val lockedLabels: Set<String> = setOf("⇧", "⌫"),
    private val onEmptyKeyClick: ((KeyConfig) -> Unit)? = null,
    private val allowClearKeys: Boolean = false
) {
    private fun isLocked(key: KeyConfig): Boolean = key.label in lockedLabels
    private fun isEmptyKey(key: KeyConfig): Boolean = key.label == ""

    private val TAG_SHAKE = 987654321
    private val USER_EMPTY_MARKER = KeyMarkers.USER_EMPTY

    private var keyboardContainer: LinearLayout? = null

    private val cfg: KeyboardConfig = deepCopy(initial).ensureSpaceMarkers()

    private data class KeyPos(
        val row: Int,
        val col: Int
    )

    private var selectedA: KeyPos? = null
    private var selectedB: KeyPos? = null

    private val keyToView = linkedMapOf<KeyPos, KeyView>()
    private val shakingViews = mutableListOf<View>()
    private val bounceViews = mutableSetOf<View>()

    fun bindInto(container: ViewGroup) {
        stopAllAnims()
        container.removeAllViews()

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(10))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        root.addView(TextView(context).apply {
            text = "Set Layout"
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, dp(10))
        })

        val scroll = ScrollView(context).apply {
            isFillViewport = true
        }

        keyboardContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        scroll.addView(
            keyboardContainer,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (context.resources.displayMetrics.heightPixels * 0.55f).toInt()
            )
        )

        container.addView(root)

        buildKeyboardUI()
        startIdleShake()
    }

    private fun buildKeyboardUI() {
        val userShape = KeyboardPrefs.getShape(context)

        val container = keyboardContainer ?: return
        container.removeAllViews()

        keyToView.clear()
        shakingViews.clear()
        bounceViews.clear()

        selectedA = null
        selectedB = null

        val rowCount = KeyboardPrefs.getRowCount(context)

        fun addKeyToRow(
            row: LinearLayout,
            key: KeyConfig,
            pos: KeyPos,
            width: Int,
            height: Int,
            marginH: Int
        ) {
            val keyItem = createKeyView(
                key = key,
                pos = pos,
                userShape = userShape
            )

            row.addView(
                keyItem,
                LinearLayout.LayoutParams(width, height).apply {
                    marginStart = marginH
                    marginEnd = marginH
                }
            )
        }

        fun buildThreeRowEditorRow(
            rowIndex: Int,
            keys: MutableList<KeyConfig>
        ) {
            val visibleKeys = keys
            if (visibleKeys.isEmpty()) return

            val keyWidth = dp(34)
            val keyHeight = dp(44)

            val keyGap = dp(1)
            val intraPairOverlap = -dp(8)
            val interPairGap = dp(2)

            val rowW = (keyWidth + keyGap * 2) * 6

            val block = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(1), dp(2), dp(1))
                clipChildren = false
                clipToPadding = false
            }

            val topRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.START
                clipChildren = false
                clipToPadding = false
            }

            val bottomRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
                clipChildren = false
                clipToPadding = false
            }

            visibleKeys.take(6).forEachIndexed { keyIndex, key ->
                addKeyToRow(
                    row = topRow,
                    key = key,
                    pos = KeyPos(rowIndex, keyIndex),
                    width = keyWidth,
                    height = keyHeight,
                    marginH = keyGap
                )
            }

            visibleKeys.drop(6).forEachIndexed { i, key ->
                addKeyToRow(
                    row = bottomRow,
                    key = key,
                    pos = KeyPos(rowIndex, i + 6),
                    width = keyWidth,
                    height = keyHeight,
                    marginH = keyGap
                )
            }

            block.addView(
                topRow,
                LinearLayout.LayoutParams(
                    rowW,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )

            block.addView(
                bottomRow,
                LinearLayout.LayoutParams(
                    rowW,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = intraPairOverlap
                }
            )

            container.addView(
                block,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    bottomMargin = interPairGap
                }
            )
        }

        fun buildFiveRowEditorRow(
            rowIndex: Int,
            keys: MutableList<KeyConfig>
        ) {
            val visibleKeys = keys
            if (visibleKeys.isEmpty()) return

            val maxKeysInAnyRow = cfg.rows.maxOfOrNull { it.keys.size }
                ?: visibleKeys.size

            val dialogW = (context.resources.displayMetrics.widthPixels * 0.92f).toInt()
            val availableW = dialogW - dp(28) - dp(8)

            val gap = dp(1)

            val keySize = (
                    (availableW - (maxKeysInAnyRow * gap * 2)) /
                            maxKeysInAnyRow.toFloat()
                    ).toInt().coerceIn(dp(28), dp(32))

            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, dp(3), 0, dp(3))
                clipChildren = false
                clipToPadding = false
            }

            visibleKeys.forEachIndexed { keyIndex, key ->
                addKeyToRow(
                    row = row,
                    key = key,
                    pos = KeyPos(rowIndex, keyIndex),
                    width = keySize,
                    height = keySize,
                    marginH = gap
                )
            }

            container.addView(
                row,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        fun buildDefaultWeightedRow(
            rowIndex: Int,
            keys: MutableList<KeyConfig>
        ) {
            val visibleKeys = keys
            if (visibleKeys.isEmpty()) return

            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, dp(4), 0, dp(4))
            }

            visibleKeys.forEachIndexed { keyIndex, key ->
                val keyItem = createKeyView(
                    key = key,
                    pos = KeyPos(rowIndex, keyIndex),
                    userShape = userShape
                )

                row.addView(
                    keyItem,
                    LinearLayout.LayoutParams(0, dp(56), 1f).apply {
                        marginStart = dp(1)
                        marginEnd = dp(1)
                    }
                )
            }

            container.addView(row)
        }

        cfg.rows.forEachIndexed { rowIndex, rowCfg ->
            when (rowCount) {
                3 -> buildThreeRowEditorRow(rowIndex, rowCfg.keys)
                5 -> buildFiveRowEditorRow(rowIndex, rowCfg.keys)
                else -> buildDefaultWeightedRow(rowIndex, rowCfg.keys)
            }
        }
    }

    private fun createKeyView(
        key: KeyConfig,
        pos: KeyPos,
        userShape: KeyShape
    ): View {
        val locked = isLocked(key)
        val empty = isEmptyKey(key)

        val wrapper = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(56)
            )
        }

        val keyView = KeyView(context).apply {
            text = if (key.label.isBlank()) "" else key.label
            gravity = Gravity.CENTER
            textSize = 16f
            includeFontPadding = false
            isAllCaps = false
            shape = userShape
            isSpecial = (key.label == "↵")
            setTextColor(0xFFFFFFFF.toInt())
            customBgColor = 0xFF111111.toInt()

            alpha = when {
                locked -> 0.55f
                empty -> 0.30f
                else -> 1f
            }

            setOnClickListener {
                when {
                    empty -> onEmptyKeyClick?.invoke(key)
                    else -> onKeyClicked(pos)
                }
            }

            setOnLongClickListener {
                startDragForKey(this, pos)
                true
            }

            setOnDragListener { v, e ->
                handleDrop(v as KeyView, pos, e)
            }
        }

        wrapper.addView(
            keyView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        keyToView[pos] = keyView
        shakingViews.add(keyView)

        if (allowClearKeys && !locked && !empty) {
            val clearBtn = TextView(context).apply {
                text = "×"
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(0xFFFFFFFF.toInt())
                setBackgroundColor(0x66000000)
                setPadding(dp(4), dp(1), dp(4), dp(1))
                isClickable = true
                isFocusable = false

                setOnClickListener {
                    key.label = ""
                    key.longPressBindings.clear()
                    key.longPressBindings.add(USER_EMPTY_MARKER)
                    afterLayoutChanged()
                }
            }

            wrapper.addView(
                clearBtn,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.END
                ).apply {
                    topMargin = dp(2)
                    marginEnd = dp(2)
                }
            )
        }

        return wrapper
    }

    private fun keyAt(pos: KeyPos): KeyConfig? {
        return cfg.rows
            .getOrNull(pos.row)
            ?.keys
            ?.getOrNull(pos.col)
    }

    private fun onKeyClicked(pos: KeyPos) {
        val key = keyAt(pos) ?: return
        if (isEmptyKey(key)) return

        if (selectedA == null || selectedA == pos) {
            selectedA = pos
        } else if (selectedB == null || selectedB == pos) {
            selectedB = pos
        } else {
            clearSelection()
            selectedA = pos
        }

        applySelectionUI()
    }

    private fun afterLayoutChanged() {
        buildKeyboardUI()
        clearSelection()
    }

    private fun clearSelection() {
        selectedA = null
        selectedB = null
        applySelectionUI()
    }

    private fun swapPositions(a: KeyPos, b: KeyPos) {
        val rowA = cfg.rows.getOrNull(a.row)?.keys ?: return
        val rowB = cfg.rows.getOrNull(b.row)?.keys ?: return

        if (a.col !in rowA.indices) return
        if (b.col !in rowB.indices) return

        val tmp = rowA[a.col]
        rowA[a.col] = rowB[b.col]
        rowB[b.col] = tmp
    }

    private fun applySelectionUI() {
        val hasSelection = selectedA != null || selectedB != null
        if (hasSelection) {
            stopIdleShake()
        } else {
            startIdleShake()
        }

        keyToView.forEach { entry ->
            val pos = entry.key
            val view = entry.value

            val isSel = pos == selectedA || pos == selectedB

            if (isSel) {
                view.customBgColor = 0xFFFFFFFF.toInt()
                view.setTextColor(0xFF000000.toInt())
                startBounce(view)
            } else {
                view.customBgColor = 0xFF111111.toInt()
                view.setTextColor(0xFFFFFFFF.toInt())
                stopBounce(view)
            }

            view.invalidate()
        }
    }

    private fun startDragForKey(view: View, pos: KeyPos) {
        val data = ClipData.newPlainText("key_pos", "${pos.row}:${pos.col}")
        val shadow = View.DragShadowBuilder(view)

        view.tag = pos

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            view.startDragAndDrop(data, shadow, view, 0)
        } else {
            @Suppress("DEPRECATION")
            view.startDrag(data, shadow, view, 0)
        }
    }

    private fun handleDrop(
        targetView: KeyView,
        targetPos: KeyPos,
        e: DragEvent
    ): Boolean {
        when (e.action) {
            DragEvent.ACTION_DRAG_STARTED -> {
                return true
            }

            DragEvent.ACTION_DRAG_ENTERED -> {
                targetView.alpha = 0.7f
                return true
            }

            DragEvent.ACTION_DRAG_EXITED -> {
                targetView.alpha = 1f
                return true
            }

            DragEvent.ACTION_DROP -> {
                targetView.alpha = 1f

                val srcView = e.localState as? View ?: return true
                val srcPos = srcView.tag as? KeyPos ?: return true

                val srcKey = keyAt(srcPos) ?: return true
                if (isEmptyKey(srcKey)) return true

                if (srcPos != targetPos) {
                    swapPositions(srcPos, targetPos)
                    afterLayoutChanged()
                }

                return true
            }

            DragEvent.ACTION_DRAG_ENDED -> {
                targetView.alpha = 1f
                return true
            }
        }

        return false
    }

    private fun startIdleShake() {
        if (selectedA != null || selectedB != null) return

        val deg = 8f

        shakingViews.forEach { v ->
            if (v.getTag(TAG_SHAKE) == true) return@forEach
            v.setTag(TAG_SHAKE, true)

            fun loop() {
                if (selectedA != null || selectedB != null) {
                    v.setTag(TAG_SHAKE, false)
                    v.rotation = 0f
                    return
                }

                v.animate().cancel()
                v.animate()
                    .rotation(deg)
                    .setDuration(90)
                    .withEndAction {
                        v.animate()
                            .rotation(-deg)
                            .setDuration(180)
                            .withEndAction {
                                v.animate()
                                    .rotation(0f)
                                    .setDuration(90)
                                    .withEndAction { loop() }
                                    .start()
                            }
                            .start()
                    }
                    .start()
            }

            loop()
        }
    }

    private fun stopIdleShake() {
        shakingViews.forEach { v ->
            v.setTag(TAG_SHAKE, false)
            v.animate().cancel()
            v.rotation = 0f
        }
    }

    private fun startBounce(v: View) {
        if (bounceViews.contains(v)) return
        bounceViews.add(v)

        fun loop() {
            if (!bounceViews.contains(v)) return

            v.animate().cancel()
            v.animate()
                .translationY(-dp(3).toFloat())
                .setDuration(120)
                .withEndAction {
                    if (!bounceViews.contains(v)) return@withEndAction

                    v.animate()
                        .translationY(0f)
                        .setDuration(120)
                        .withEndAction { loop() }
                        .start()
                }
                .start()
        }

        loop()
    }

    private fun stopBounce(v: View) {
        bounceViews.remove(v)
        v.animate().cancel()
        v.translationY = 0f
    }

    fun stopAllAnims() {
        stopIdleShake()

        keyToView.values.forEach { view ->
            stopBounce(view)
        }

        bounceViews.clear()
    }

    fun swapSelectedExternally(): Boolean {
        val a = selectedA
        val b = selectedB

        if (a == null || b == null) return false

        swapPositions(a, b)
        afterLayoutChanged()

        return true
    }

    fun saveExternally() {
        onSaved(cfg)
    }

    fun hasTwoSelected(): Boolean {
        return selectedA != null && selectedB != null
    }

    private fun dp(v: Int): Int {
        return (v * context.resources.displayMetrics.density).toInt()
    }

    private fun deepCopy(src: KeyboardConfig): KeyboardConfig {
        return KeyboardConfig(
            rows = src.rows.map { row ->
                row.copy(
                    keys = row.keys.map { key ->
                        key.copy(
                            longPressBindings = key.longPressBindings.toMutableList()
                        )
                    }.toMutableList()
                )
            }.toMutableList(),
            specialLeft = src.specialLeft.map { key ->
                key.copy(
                    longPressBindings = key.longPressBindings.toMutableList()
                )
            }.toMutableList(),
            specialRight = src.specialRight.map { key ->
                key.copy(
                    longPressBindings = key.longPressBindings.toMutableList()
                )
            }.toMutableList()
        )
    }
}