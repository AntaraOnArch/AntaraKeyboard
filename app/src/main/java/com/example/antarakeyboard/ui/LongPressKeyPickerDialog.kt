package com.example.antarakeyboard.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.example.antarakeyboard.SpecialChars
import com.example.antarakeyboard.extensions.dp
import com.example.antarakeyboard.model.KeyboardConfig
import com.example.antarakeyboard.model.KeyConfig
import kotlin.math.max

class LongPressKeyPickerDialog(
    context: Context,
    private val cfg: KeyboardConfig,
    private val onSave: (KeyboardConfig) -> Unit
) : Dialog(context) {

    // ne dopuštamo bind na ove tipke
    private val nonBindable = setOf("⇧", "⌫", "↵", "123", "ABC", "abc", " ")

    private val allKeys: List<KeyConfig> =
        cfg.rows.flatMap { it.keys }
            .filter { it.label !in nonBindable }
            .distinctBy { it.label } // ako ima duplikata labela, uzmi prvi

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(context), 16.dp(context), 16.dp(context), 12.dp(context))
        }

        root.addView(TextView(context).apply {
            text = "Odaberi tipku"
            textSize = 18f
        })

        val grid = GridLayout(context).apply {
            // dinamički broj stupaca
            columnCount = 6
            rowCount = max(1, (allKeys.size + columnCount - 1) / columnCount)
        }

        fun addKeyButton(key: KeyConfig) {
            val btn = Button(context).apply {
                text = key.label
                isAllCaps = false
                setOnClickListener {
                    LongPressEditDialog(
                        context = context,
                        key = key,
                        onChanged = {
                            // ništa posebno - key je referenca u cfg
                        }
                    ).show()
                }
            }
            grid.addView(btn, GridLayout.LayoutParams().apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(4.dp(context), 4.dp(context), 4.dp(context), 4.dp(context))
            })
        }

        allKeys.forEach { addKeyButton(it) }

        root.addView(grid)

        val bottom = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        val cancel = Button(context).apply {
            text = "Close"
            setOnClickListener { dismiss() }
        }

        val save = Button(context).apply {
            text = "Save"
            setOnClickListener {
                onSave(cfg)
                dismiss()
            }
        }

        bottom.addView(cancel)
        bottom.addView(save)

        root.addView(bottom)

        setContentView(root)
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

}

private class LongPressEditDialog(
    context: Context,
    private val key: KeyConfig,
    private val onChanged: () -> Unit
) : Dialog(context) {

    private var deleteMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(context), 16.dp(context), 16.dp(context), 12.dp(context))
        }

        root.addView(TextView(context).apply {
            text = "Long press: ${key.label}"
            textSize = 18f
        })

        val bindsGrid = GridLayout(context).apply {
            columnCount = 6
        }

        fun rebuildBinds() {
            bindsGrid.removeAllViews()
            val binds = key.longPressBindings

            if (binds.isEmpty()) {
                root.findViewWithTag<TextView>("empty")?.let { it.text = "Nema bindova." }
            } else {
                root.findViewWithTag<TextView>("empty")?.let { it.text = "" }
            }

            binds.forEachIndexed { idx, ch ->
                val btn = Button(context).apply {
                    text = if (deleteMode) "✖ $ch" else ch
                    isAllCaps = false
                    setOnClickListener {
                        if (deleteMode) {
                            key.longPressBindings.removeAt(idx)
                            onChanged()
                            rebuildBinds()
                        }
                    }
                }
                bindsGrid.addView(btn, GridLayout.LayoutParams().apply {
                    width = 0
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(4.dp(context), 4.dp(context), 4.dp(context), 4.dp(context))
                })
            }
        }

        val empty = TextView(context).apply {
            tag = "empty"
            text = ""
        }
        root.addView(empty)
        root.addView(bindsGrid)

        val rowBtns = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val addBtn = Button(context).apply {
            text = "Dodaj"
            setOnClickListener {
                showSpecialCharsPicker { picked ->
                    if (!key.longPressBindings.contains(picked)) {
                        key.longPressBindings.add(picked)
                        onChanged()
                        rebuildBinds()
                    }
                }
            }
        }

        val removeBtn = Button(context).apply {
            text = "Ukloni"
            setOnClickListener {
                deleteMode = !deleteMode
                text = if (deleteMode) "Gotovo" else "Ukloni"
                rebuildBinds()
            }
        }

        rowBtns.addView(addBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        rowBtns.addView(removeBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        root.addView(rowBtns)

        val closeBtn = Button(context).apply {
            text = "Close"
            setOnClickListener { dismiss() }
        }
        root.addView(closeBtn)

        setContentView(root)
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        rebuildBinds()
    }

    private fun showSpecialCharsPicker(onPick: (String) -> Unit) {
        // koristi SpecialChars.ALL (tvoja lista)
        val items = SpecialChars.ALL.distinct()
        AlertDialog.Builder(context)
            .setTitle("Dodaj znak")
            .setItems(items.toTypedArray()) { _, which ->
                onPick(items[which])
            }
            .show()
    }

}