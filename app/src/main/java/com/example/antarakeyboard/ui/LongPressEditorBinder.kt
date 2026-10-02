package com.example.antarakeyboard.ui

import android.content.Context
import android.graphics.Typeface
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.example.antarakeyboard.R
import com.example.antarakeyboard.SpecialChars
import com.example.antarakeyboard.data.KeyboardPrefs
import com.example.antarakeyboard.extensions.dp
import com.example.antarakeyboard.model.KeyConfig
import com.example.antarakeyboard.model.KeyboardConfig
import com.example.antarakeyboard.model.KeyShape

class LongPressEditorBinder(
    private val context: Context,
    initial: KeyboardConfig,
    private val titleText: String,
    private val lockedLabels: Set<String> = setOf("⇧", "⌫", "↵", "123", "ABC", "abc", " ")
) {
    private val cfg: KeyboardConfig = deepCopy(initial)

    // Create themed context for KeyView to use proper colors
    private val themedContext: Context by lazy {
        val isDark = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
            .getBoolean("dark_mode", true)
        val themeRes = if (isDark) R.style.Theme_AntaraKeyboard_Dark else R.style.Theme_AntaraKeyboard_Light
        ContextThemeWrapper(context, themeRes)
    }
    private var keyboardContainer: LinearLayout? = null

    fun bindInto(container: ViewGroup) {
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
            text = titleText
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, 10.dp(context))
        })

        val hint = TextView(context).apply {
            text = "Tap na tipku za uređivanje long press znakova"
            textSize = 13f
            alpha = 0.75f
            setPadding(0, 0, 0, 10.dp(context))
        }
        root.addView(hint)

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
                (context.resources.displayMetrics.heightPixels * 0.50f).toInt()
            )
        )

        container.addView(root)
        buildKeyboardUI()
    }

    fun getUpdatedConfig(): KeyboardConfig = cfg

    private fun buildKeyboardUI() {
        val userShape = KeyboardPrefs.getShape(context)
        val container = keyboardContainer ?: return
        container.removeAllViews()

        fun buildRow(keys: List<KeyConfig>) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, 4.dp(context), 0, 4.dp(context))
            }

            keys.forEach { key ->
                val kv = createKeyView(key, userShape)
                row.addView(
                    kv,
                    LinearLayout.LayoutParams(0, 56.dp(context), 1f).apply {
                        marginStart = 1.dp(context)
                        marginEnd = 1.dp(context)
                    }
                )
            }

            container.addView(row)
        }

        cfg.rows.forEach { buildRow(it.keys) }
    }

    private fun createKeyView(key: KeyConfig, userShape: KeyShape): KeyView {
        val locked = key.label in lockedLabels

        return KeyView(themedContext).apply {
            text = key.label
            gravity = Gravity.CENTER
            textSize = 16f
            includeFontPadding = false
            isAllCaps = false
            shape = userShape
            isSpecial = (key.label == "↵")
            setTextColor(0xFFFFFFFF.toInt())

            alpha = if (locked) 0.55f else 1f

            setOnClickListener {
                if (!locked) {
                    showLongPressPickerForKey(key)
                }
            }
        }
    }

    private fun showLongPressPickerForKey(key: KeyConfig) {
        val allChars = SpecialChars.ALL
        val selected = BooleanArray(allChars.size) { i ->
            key.longPressBindings.contains(allChars[i])
        }

        AlertDialog.Builder(context)
            .setTitle("Long press za: ${key.label}")
            .setMultiChoiceItems(allChars.toTypedArray(), selected) { _, which, isChecked ->
                selected[which] = isChecked
            }
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                key.longPressBindings.clear()
                allChars.forEachIndexed { index, ch ->
                    if (selected[index]) {
                        key.longPressBindings.add(ch)
                    }
                }
                buildKeyboardUI()
            }
            .show()
    }

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