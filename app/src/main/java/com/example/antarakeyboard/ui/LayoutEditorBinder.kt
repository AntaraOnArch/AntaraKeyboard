package com.example.antarakeyboard.ui

import com.example.antarakeyboard.R
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
import com.example.antarakeyboard.extensions.dp
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
            setPadding(14.dp(context), 14.dp(context), 14.dp(context), 10.dp(context))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        root.addView(TextView(context).apply {
            text = context.getString(R.string.main_set_layout)
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, 10.dp(context))
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

        // Height follows content; weight lets it shrink (and scroll) when the screen is too short
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        container.addView(root)

        buildKeyboardUI()
        startIdleShake()
    }

    private fun buildKeyboardUI() {
        val userShape = KeyboardPrefs.getShape(context)

        val container = keyboardContainer ?: return

        keyToView.clear()
        shakingViews.clear()
        bounceViews.clear()

        selectedA = null
        selectedB = null

        EditorKeyboardRenderer(context).render(container, cfg) { key, row, col ->
            createKeyView(key = key, pos = KeyPos(row, col), userShape = userShape)
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
                56.dp(context)
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
                setPadding(4.dp(context), 1.dp(context), 4.dp(context), 1.dp(context))
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
                    topMargin = 2.dp(context)
                    marginEnd = 2.dp(context)
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
        // Autosave
        onSaved(cfg)
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
                .translationY(-3.dp(context).toFloat())
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