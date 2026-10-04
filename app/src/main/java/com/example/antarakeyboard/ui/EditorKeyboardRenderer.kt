package com.example.antarakeyboard.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.example.antarakeyboard.data.KeyboardPrefs
import com.example.antarakeyboard.extensions.dp
import com.example.antarakeyboard.model.KeyConfig
import com.example.antarakeyboard.model.KeyboardConfig

/**
 * Lays out keyboard rows for editor previews (Set layout, Bind long press),
 * so every editor shows the keyboard exactly the same way.
 * Each editor supplies its own key view via [render]'s createKey.
 */
class EditorKeyboardRenderer(private val context: Context) {

    private data class KeyPos(
        val row: Int,
        val col: Int
    )

    fun render(
        container: LinearLayout,
        cfg: KeyboardConfig,
        createKey: (key: KeyConfig, row: Int, col: Int) -> View
    ) {
        container.removeAllViews()

        val rowCount = KeyboardPrefs.getRowCount(context)

        fun addKeyToRow(
            row: LinearLayout,
            key: KeyConfig,
            pos: KeyPos,
            width: Int,
            height: Int,
            marginH: Int
        ) {
            val keyItem = createKey(key, pos.row, pos.col)

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

            val keyWidth = 34.dp(context)
            val keyHeight = 44.dp(context)

            val keyGap = 1.dp(context)
            val intraPairOverlap = -8.dp(context)
            val interPairGap = 2.dp(context)

            val rowW = (keyWidth + keyGap * 2) * 6

            val block = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 1.dp(context), 2.dp(context), 1.dp(context))
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
            val availableW = dialogW - 28.dp(context) - 8.dp(context)

            val gap = 1.dp(context)

            val keySize = (
                    (availableW - (maxKeysInAnyRow * gap * 2)) /
                            maxKeysInAnyRow.toFloat()
                    ).toInt().coerceIn(28.dp(context), 32.dp(context))

            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, 3.dp(context), 0, 3.dp(context))
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
                setPadding(0, 4.dp(context), 0, 4.dp(context))
            }

            visibleKeys.forEachIndexed { keyIndex, key ->
                val keyItem = createKey(key, rowIndex, keyIndex)

                row.addView(
                    keyItem,
                    LinearLayout.LayoutParams(0, 56.dp(context), 1f).apply {
                        marginStart = 1.dp(context)
                        marginEnd = 1.dp(context)
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

}
