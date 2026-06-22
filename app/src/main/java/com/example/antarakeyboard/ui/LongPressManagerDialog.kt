package com.example.antarakeyboard.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.antarakeyboard.SpecialChars
import com.example.antarakeyboard.data.KeyboardPrefs
import com.example.antarakeyboard.model.KeyConfig
import com.example.antarakeyboard.model.KeyboardConfig

class LongPressManagerDialog(
    context: Context,
    private val keyboardConfig: KeyboardConfig,
    private val onSaved: () -> Unit
) : Dialog(context) {

    private var selectedKey: KeyConfig? = null
    private var removeMode = false

    private lateinit var keysRv: RecyclerView
    private lateinit var bindsRv: RecyclerView
    private lateinit var titleTv: TextView
    private lateinit var btnAdd: Button
    private lateinit var btnRemove: Button
    private lateinit var btnClose: Button

    private val nonBindableKeys = setOf("⇧", "⌫", "↵", "123", "ABC", "abc", " ")

    private val bindAdapter = BindsAdapter(
        onClick = { ch ->
            val k = selectedKey ?: return@BindsAdapter
            if (removeMode) {
                k.longPressBindings.remove(ch)
                refreshBinds()
            } else {
                // normal mode: optional preview or do nothing
            }
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(12))
        }

        titleTv = TextView(context).apply {
            textSize = 18f
            text = "Long press manager"
        }
        root.addView(titleTv)

        keysRv = RecyclerView(context).apply {
            layoutManager = GridLayoutManager(context, 8)
            adapter = KeysAdapter(buildKeyList()) { key ->
                selectedKey = key
                removeMode = false
                btnRemove.text = "Ukloni"
                titleTv.text = "Tipka: ${key.label}"
                refreshBinds()
            }
        }
        root.addView(keysRv, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(160)
        ).apply { topMargin = dp(10) })

        bindsRv = RecyclerView(context).apply {
            layoutManager = GridLayoutManager(context, 8)
            adapter = bindAdapter
        }
        root.addView(bindsRv, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(160)
        ).apply { topMargin = dp(10) })

        val actionsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        btnAdd = Button(context).apply {
            text = "Dodaj"
            isAllCaps = false
            setOnClickListener {
                val k = selectedKey
                if (k == null) {
                    Toast.makeText(context, "Odaberi tipku prvo", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                SpecialCharsDialog(context, SpecialChars.ALL) { chosen ->
                    if (!k.longPressBindings.contains(chosen)) {
                        k.longPressBindings.add(chosen)
                        refreshBinds()
                    }
                }.show()
            }
        }

        btnRemove = Button(context).apply {
            text = "Ukloni"
            isAllCaps = false
            setOnClickListener {
                val k = selectedKey
                if (k == null) {
                    Toast.makeText(context, "Odaberi tipku prvo", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                removeMode = !removeMode
                text = if (removeMode) "Gotovo" else "Ukloni"
                refreshBinds()
            }
        }

        btnClose = Button(context).apply {
            text = "Spremi i zatvori"
            isAllCaps = false
            setOnClickListener {
                val rowCount = KeyboardPrefs.getRowCount(context)

                KeyboardPrefs.saveAlphabetLayoutForRowCount(
                    context,
                    rowCount,
                    keyboardConfig
                )

                onSaved()
                dismiss()
            }
        }

        actionsRow.addView(btnAdd)
        actionsRow.addView(btnRemove)
        actionsRow.addView(btnClose)

        root.addView(actionsRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(10) })

        setContentView(root)

        // init: ništa odabrano
        refreshBinds()
    }

    private fun buildKeyList(): List<KeyConfig> {
        return keyboardConfig.rows
            .flatMap { it.keys }
            .filter { it.label !in nonBindableKeys }
            .distinctBy { it.label }
    }

    private fun refreshBinds() {
        val binds = selectedKey?.longPressBindings?.toList() ?: emptyList()
        bindAdapter.submit(binds, removeMode)
    }

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()

    // ---------- adapters ----------

    private class KeysAdapter(
        private val items: List<KeyConfig>,
        private val onPick: (KeyConfig) -> Unit
    ) : RecyclerView.Adapter<KeysAdapter.VH>() {

        private var selected = RecyclerView.NO_POSITION

        inner class VH(val btn: Button) : RecyclerView.ViewHolder(btn)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = Button(parent.context).apply {
                isAllCaps = false
                textSize = 14f
                minHeight = 0
                minimumHeight = 0
                setPadding(6, 6, 6, 6)
            }
            return VH(b)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val k = items[position]

            holder.btn.text = k.label
            holder.btn.alpha = if (position == selected) 1f else 0.85f

            holder.btn.setOnClickListener {
                val adapterPos = holder.bindingAdapterPosition
                if (adapterPos == RecyclerView.NO_POSITION) return@setOnClickListener

                val old = selected
                selected = adapterPos

                if (old != RecyclerView.NO_POSITION) {
                    notifyItemChanged(old)
                }

                notifyItemChanged(selected)

                onPick(items[adapterPos])
            }
        }

        override fun getItemCount() = items.size
    }

    private class BindsAdapter(
        private val onClick: (String) -> Unit
    ) : RecyclerView.Adapter<BindsAdapter.VH>() {

        private var items: List<String> = emptyList()
        private var removeMode: Boolean = false

        inner class VH(val root: LinearLayout) : RecyclerView.ViewHolder(root) {
            val charTv: TextView = TextView(root.context).apply {
                textSize = 18f
                gravity = Gravity.CENTER
            }
            val xTv: TextView = TextView(root.context).apply {
                text = "×"
                textSize = 14f
                gravity = Gravity.END
                visibility = View.GONE
            }
            init {
                root.addView(xTv, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ))
                root.addView(charTv, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ))
            }
        }

        fun submit(list: List<String>, remove: Boolean) {
            items = list
            removeMode = remove
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val box = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(10, 6, 10, 6)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            return VH(box)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val ch = items[position]
            holder.charTv.text = ch
            holder.xTv.visibility = if (removeMode) View.VISIBLE else View.GONE
            holder.root.alpha = if (removeMode) 0.95f else 1f

            // “ples” efekt: minimalno (bez animacije još)
            holder.xTv.rotation = if (removeMode) -12f else 0f
            holder.charTv.rotation = if (removeMode) 6f else 0f

            holder.root.setOnClickListener { onClick(ch) }
        }

        override fun getItemCount() = items.size
    }
}