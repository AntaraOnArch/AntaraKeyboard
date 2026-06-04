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
import com.example.antarakeyboard.model.KeyShape
import com.example.antarakeyboard.model.KeyboardConfig

class LayoutEditorBinder(
    private val context: Context,
    initial: KeyboardConfig,
    private val onSaved: (KeyboardConfig) -> Unit,
    private val lockedLabels: Set<String> = setOf("⇧", "⌫"),
    private val onEmptyKeyClick: ((KeyConfig) -> Unit)? = null,
    private val allowClearKeys: Boolean = false
){
    private fun isLocked(key: KeyConfig): Boolean = key.label in lockedLabels
    private fun isEmptyKey(key: KeyConfig): Boolean = key.label == ""
    private val cfg: KeyboardConfig = deepCopy(initial)
    private val TAG_SHAKE = 987654321
    private val USER_EMPTY_MARKER = "__USER_EMPTY__"

    private var keyboardContainer: LinearLayout? = null

    private var selectedA: KeyConfig? = null
    private var selectedB: KeyConfig? = null

    private val keyToView = linkedMapOf<KeyConfig, KeyView>()
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
            width: Int,
            height: Int,
            marginH: Int
        ) {
            val keyItem = createKeyView(key, userShape)

            row.addView(
                keyItem,
                LinearLayout.LayoutParams(width, height).apply {
                    marginStart = marginH
                    marginEnd = marginH
                }
            )
        }

        fun buildThreeRowEditorRow(keys: MutableList<KeyConfig>) {
            val visibleKeys = keys
            if (visibleKeys.isEmpty()) return

            val keyWidth = dp(34)
            val keyHeight = dp(44)

            val keyGap = dp(1)
            val intraPairOverlap = -dp(8)
            val interPairGap = dp(2)

            // širina jednog razbijenog bloka: 6 tipki
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

            visibleKeys.take(6).forEach { key ->
                addKeyToRow(
                    row = topRow,
                    key = key,
                    width = keyWidth,
                    height = keyHeight,
                    marginH = keyGap
                )
            }

            visibleKeys.drop(6).forEach { key ->
                addKeyToRow(
                    row = bottomRow,
                    key = key,
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
                    // ovo centrira CIJELI 6+5 blok u dialogu,
                    // ali NE centrira topRow/bottomRow unutar bloka
                    gravity = Gravity.CENTER_HORIZONTAL
                    bottomMargin = interPairGap
                }
            )
        }

        fun buildFiveRowEditorRow(keys: MutableList<KeyConfig>) {
            val visibleKeys = keys
            if (visibleKeys.isEmpty()) return

            val maxKeysInAnyRow = cfg.rows.maxOfOrNull { it.keys.size }
                ?: visibleKeys.size

            val dialogW = (context.resources.displayMetrics.widthPixels * 0.92f).toInt()

            // root padding 14 + 14, plus safety
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

            visibleKeys.forEach { key ->
                addKeyToRow(
                    row = row,
                    key = key,
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

        fun buildDefaultWeightedRow(keys: MutableList<KeyConfig>) {
            val visibleKeys = keys
            if (visibleKeys.isEmpty()) return

            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, dp(4), 0, dp(4))
            }

            visibleKeys.forEach { key ->
                val keyItem = createKeyView(key, userShape)

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

        cfg.rows.forEach { rowCfg ->
            when (rowCount) {
                3 -> buildThreeRowEditorRow(rowCfg.keys)
                5 -> buildFiveRowEditorRow(rowCfg.keys)
                else -> buildDefaultWeightedRow(rowCfg.keys) // 4-row ne diramo
            }
        }
    }

    private fun createKeyView(key: KeyConfig, userShape: KeyShape): View {
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
                    else -> onKeyClicked(key)
                }
            }

            setOnLongClickListener {
                startDragForKey(this, key)
                true
            }

            setOnDragListener { v, e ->
                handleDrop(v as KeyView, key, e)
            }
        }

        wrapper.addView(
            keyView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        keyToView[key] = keyView
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



    private fun onKeyClicked(key: KeyConfig) {
        if (isEmptyKey(key)) return

        if (selectedA == null || selectedA == key) {
            selectedA = key
        } else if (selectedB == null || selectedB == key) {
            selectedB = key
        } else {
            clearSelection()
            selectedA = key
        }

        applySelectionUI()
    }
    private data class KeyLocation(
        val row: MutableList<KeyConfig>,
        val index: Int
    )

    private fun findKeyLocation(target: KeyConfig): KeyLocation? {
        cfg.rows.forEach { rowCfg ->
            val i = rowCfg.keys.indexOf(target)
            if (i != -1) return KeyLocation(rowCfg.keys, i)
        }

        return null
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

    private fun swapPositions(a: KeyConfig, b: KeyConfig) {
        val locA = findKeyLocation(a) ?: return
        val locB = findKeyLocation(b) ?: return

        val tmp = locA.row[locA.index]
        locA.row[locA.index] = locB.row[locB.index]
        locB.row[locB.index] = tmp
    }

    private fun applySelectionUI() {
        val hasSelection = selectedA != null || selectedB != null
        if (hasSelection) stopIdleShake() else startIdleShake()

        keyToView.forEach { (key, view) ->
            val isSel = (key == selectedA || key == selectedB)

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

    private fun startDragForKey(view: View, key: KeyConfig) {
        val data = ClipData.newPlainText("key_ref", key.hashCode().toString())
        val shadow = View.DragShadowBuilder(view)

        view.tag = key

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            view.startDragAndDrop(data, shadow, view, 0)
        } else {
            @Suppress("DEPRECATION")
            view.startDrag(data, shadow, view, 0)
        }
    }

    private fun handleDrop(targetView: KeyView, targetKey: KeyConfig, e: DragEvent): Boolean {
        when (e.action) {
            DragEvent.ACTION_DRAG_STARTED -> return true

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
                val srcKey = srcView.tag as? KeyConfig ?: return true

                // Praznu tipku ne vučemo kao source,
                // ali dopuštamo drop NA praznu tipku.
                // To je bitno za numeric layout: broj se smije pomaknuti,
                // ali ne smije nestati iz numeričkog dijela.
                if (isEmptyKey(srcKey)) return true

                if (srcKey != targetKey) {
                    swapPositions(srcKey, targetKey)
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
        keyToView.values.forEach { stopBounce(it) }
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

    private fun dp(v: Int): Int =
        (v * context.resources.displayMetrics.density).toInt()

    private fun deepCopy(src: KeyboardConfig): KeyboardConfig {
        return KeyboardConfig(
            rows = src.rows.map { row ->
                row.copy(
                    keys = row.keys.map {
                        it.copy(longPressBindings = it.longPressBindings.toMutableList())
                    }.toMutableList()
                )
            }.toMutableList(),
            specialLeft = src.specialLeft.map {
                it.copy(longPressBindings = it.longPressBindings.toMutableList())
            }.toMutableList(),
            specialRight = src.specialRight.map {
                it.copy(longPressBindings = it.longPressBindings.toMutableList())
            }.toMutableList()
        )
    }
}