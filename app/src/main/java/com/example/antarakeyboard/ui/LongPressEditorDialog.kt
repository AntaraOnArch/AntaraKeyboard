package com.example.antarakeyboard.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.example.antarakeyboard.R
import com.example.antarakeyboard.data.KeyboardPrefs
import com.example.antarakeyboard.extensions.dp

/**
 * Bind long press popup: Alphabet / Numeric (key preview + character picker), My binds and
 * Custom text. Every change is saved at once; [markDirty] gets the changed section's name and
 * [flushSavedToast] shows one "<Section> saved" toast (section switch, close).
 */
class LongPressEditorDialog(
    private val activity: Activity,
    private val markDirty: (section: String) -> Unit,
    private val flushSavedToast: () -> Unit
) {

    // Text built from data (names, key labels, color values), not from UI strings
    @SuppressLint("SetTextI18n")
    fun show() {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_longpress_editor)

        // Section dropdown
        val spinnerSection = dialog.findViewById<Spinner>(R.id.spinnerLongPressSection)
        val sectionOptions = listOf(
            R.string.lp_section_alphabet, R.string.lp_section_numeric,
            R.string.lp_section_my_binds, R.string.lp_section_custom
        ).map { activity.getString(it) }
        spinnerSection.adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item, sectionOptions).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        val pageAlphabet = dialog.findViewById<LinearLayout>(R.id.pageAlphabetLp)
        val pageNumeric = dialog.findViewById<LinearLayout>(R.id.pageNumericLp)
        val pageMyBinds = dialog.findViewById<ScrollView>(R.id.pageMyBindsLp)
        val myBindsContainer = dialog.findViewById<LinearLayout>(R.id.myBindsContainer)
        val pageCustomText = dialog.findViewById<ScrollView>(R.id.pageCustomTextLp)
        val customTextContainer = dialog.findViewById<LinearLayout>(R.id.customTextContainer)

        val currentRowCount = KeyboardPrefs.getRowCount(activity)
        var currentPage = 0

        // Load with the global binds; every change is saved immediately
        val alphabetBinder = LongPressEditorBinder(
            context = activity,
            initial = KeyboardPrefs.loadAlphabetLayoutWithGlobalBinds(activity, currentRowCount),
            titleText = activity.getString(R.string.lp_title_alphabet),
            onChanged = { updated ->
                KeyboardPrefs.saveAlphabetLayoutWithGlobalBinds(activity, currentRowCount, updated)
                markDirty(sectionOptions[0])
            }
        )

        val numericBinder = LongPressEditorBinder(
            context = activity,
            initial = KeyboardPrefs.loadNumericLayoutWithGlobalBinds(activity, currentRowCount),
            titleText = activity.getString(R.string.lp_title_numeric),
            lockedLabels = setOf("⇧", "⌫", "↵", "ABC", "abc", " "),
            onChanged = { updated ->
                KeyboardPrefs.saveNumericLayoutWithGlobalBinds(activity, currentRowCount, updated)
                markDirty(sectionOptions[1])
            }
        )

        alphabetBinder.bindInto(pageAlphabet)
        numericBinder.bindInto(pageNumeric)

        // My binds: every key with a binding; tap to edit its bindings
        fun buildMyBindsPage() {
            myBindsContainer.removeAllViews()

            val groups = listOf(
                activity.getString(R.string.lp_group_alphabet) to alphabetBinder,
                activity.getString(R.string.lp_group_numeric) to numericBinder
            )

            var anyBound = false
            groups.forEach { (groupName, binder) ->
                val keys = binder.boundKeys()
                if (keys.isEmpty()) return@forEach
                anyBound = true

                myBindsContainer.addView(TextView(activity).apply {
                    text = groupName
                    textSize = 16f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setPadding(0, 8.dp(activity), 0, 6.dp(activity))
                })

                keys.forEach { key ->
                    myBindsContainer.addView(Button(activity).apply {
                        text = "${key.label}   →   ${binder.visibleBindings(key).joinToString(" ")}"
                        // Long custom text: preview only, full text stays bound
                        maxLines = 3
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        isAllCaps = false
                        gravity = Gravity.START or Gravity.CENTER_VERTICAL
                        setOnClickListener {
                            binder.openPicker(key) { buildMyBindsPage() }
                        }
                    }, LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = 4.dp(activity) })
                }
            }

            if (!anyBound) {
                myBindsContainer.addView(TextView(activity).apply {
                    text = activity.getString(R.string.lp_no_binds)
                    textSize = 14f
                    alpha = 0.75f
                    setPadding(0, 8.dp(activity), 0, 8.dp(activity))
                })
            }
        }

        // Custom text: choose Letters / Numbers, tap a key, type any text and bind it
        var customUseNumeric = false
        var customSelected: com.example.antarakeyboard.model.KeyConfig? = null

        fun buildCustomTextPage() {
            customTextContainer.removeAllViews()
            val binder = if (customUseNumeric) numericBinder else alphabetBinder
            val gap = 8.dp(activity)

            val group = android.widget.RadioGroup(activity).apply {
                orientation = android.widget.RadioGroup.HORIZONTAL
            }
            val rbLetters = android.widget.RadioButton(activity).apply {
                id = View.generateViewId()
                text = activity.getString(R.string.lp_group_alphabet)
                isChecked = !customUseNumeric
            }
            val rbNumbers = android.widget.RadioButton(activity).apply {
                id = View.generateViewId()
                text = activity.getString(R.string.lp_group_numeric)
                isChecked = customUseNumeric
            }
            group.addView(rbLetters)
            group.addView(rbNumbers)
            group.setOnCheckedChangeListener { _, checkedId ->
                customUseNumeric = checkedId == rbNumbers.id
                customSelected = null
                buildCustomTextPage()
            }
            customTextContainer.addView(group)

            customTextContainer.addView(TextView(activity).apply {
                text = activity.getString(R.string.lp_custom_choose_key)
                alpha = 0.75f
                setPadding(0, gap, 0, gap / 2)
            })

            val preview = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            binder.renderKeyChooser(preview, customSelected) { key ->
                if (binder.isBindable(key)) {
                    customSelected = key
                    buildCustomTextPage()
                }
            }
            customTextContainer.addView(preview)

            val key = customSelected ?: return

            customTextContainer.addView(TextView(activity).apply {
                text = activity.getString(R.string.lp_custom_text_for, key.label)
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, gap * 2, 0, gap / 2)
            })

            val input = android.widget.EditText(activity).apply {
                hint = activity.getString(R.string.lp_custom_hint)
                // No length limit; long text is only shortened where it is previewed
                isSingleLine = true
            }
            customTextContainer.addView(input, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))

            customTextContainer.addView(Button(activity).apply {
                text = activity.getString(R.string.lp_custom_add)
                isAllCaps = false
                setOnClickListener {
                    // Inserted exactly as typed (spaces included); only blank text is refused
                    val text = input.text.toString()
                    if (text.isBlank()) return@setOnClickListener
                    if (binder.addBinding(key, text)) {
                        markDirty(sectionOptions[3])
                        buildCustomTextPage()
                    } else {
                        Toast.makeText(activity, R.string.lp_custom_already, Toast.LENGTH_SHORT).show()
                    }
                }
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.END })

            // Everything currently bound to this key; ✕ removes it
            val bindings = binder.visibleBindings(key)
            if (bindings.isNotEmpty()) {
                customTextContainer.addView(TextView(activity).apply {
                    text = activity.getString(R.string.lp_custom_bound)
                    alpha = 0.75f
                    setPadding(0, gap, 0, gap / 2)
                })
            }
            bindings.forEach { binding ->
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                row.addView(TextView(activity).apply {
                    text = binding
                    textSize = 15f
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(Button(activity).apply {
                    text = "✕"
                    contentDescription = activity.getString(R.string.action_delete)
                    setOnClickListener {
                        binder.removeBinding(key, binding)
                        markDirty(sectionOptions[3])
                        buildCustomTextPage()
                    }
                })
                customTextContainer.addView(row)
            }
        }

        fun showPage(page: Int) {
            currentPage = page
            pageAlphabet.visibility = if (page == 0) View.VISIBLE else View.GONE
            pageNumeric.visibility = if (page == 1) View.VISIBLE else View.GONE
            pageMyBinds.visibility = if (page == 2) View.VISIBLE else View.GONE
            pageCustomText.visibility = if (page == 3) View.VISIBLE else View.GONE
            if (page == 2) buildMyBindsPage()
            if (page == 3) buildCustomTextPage()
        }

        spinnerSection.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position != currentPage) flushSavedToast()
                showPage(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        dialog.setOnDismissListener {
            flushSavedToast()
        }

        dialog.show()
        dialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.92f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        showPage(0)
    }
}
