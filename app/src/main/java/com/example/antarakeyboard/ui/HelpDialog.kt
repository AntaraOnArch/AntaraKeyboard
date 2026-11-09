package com.example.antarakeyboard.ui

import android.animation.LayoutTransition
import android.app.Activity
import android.app.Dialog
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.antarakeyboard.R
import com.example.antarakeyboard.extensions.dp

/**
 * Help popup (? next to the title): every function as a question, grouped like the main screen.
 * A tap opens / closes the answer (accordion); under it an Open button closes the popup and runs
 * the entry's action – a speed link that opens a popup, a popup section, or jumps to a control on
 * the main screen. Entries without an action (keyboard gestures) only have the answer. The caller
 * builds the groups, so the list always matches what the screen currently offers. An optional
 * [Footer] button at the bottom (the tutorial) closes the popup and runs its action.
 */
object HelpDialog {

    class Entry(val title: String, val answer: String, val action: (() -> Unit)? = null)
    class Group(val title: String, val entries: List<Entry>)
    class Footer(val title: String, val action: () -> Unit)

    private const val CHEVRON_MS = 200L

    fun show(activity: Activity, groups: List<Group>, footer: Footer? = null) {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val pad = 16.dp(activity)

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(ContextCompat.getColor(activity, R.color.main_app_bg))
            // Opening an answer slides the entries below it down
            layoutTransition = LayoutTransition().apply { enableTransitionType(LayoutTransition.CHANGING) }
        }
        root.addView(TextView(activity).apply {
            setText(R.string.help_title)
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(activity).apply {
            setText(R.string.help_hint)
            textSize = 13f
            setPadding(0, 8.dp(activity), 0, 0)
        })

        groups.filter { it.entries.isNotEmpty() }.forEach { group ->
            root.addView(TextView(activity).apply {
                text = group.title
                textSize = 15f
                alpha = 0.75f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, pad, 0, 6.dp(activity))
            })
            group.entries.forEach { root.addView(entryView(activity, dialog, it)) }
        }
        footer?.let { footer ->
            root.addView(Button(activity).apply {
                text = footer.title
                isAllCaps = false
                setTextColor(ContextCompat.getColor(activity, R.color.main_button_text))
                background = ContextCompat.getDrawable(activity, R.drawable.main_button_rounded)
                setOnClickListener {
                    dialog.dismiss()
                    footer.action()
                }
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = pad })
        }

        // Scrolls only when the screen is too short (wrap content like every popup)
        dialog.setContentView(ScrollView(activity).apply { addView(root) })
        dialog.show()
        dialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.92f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    /** One accordion entry: question row with a chevron; the answer (and its link) under it, hidden at first. */
    private fun entryView(activity: Activity, dialog: Dialog, entry: Entry): View {
        val textColor = ContextCompat.getColor(activity, R.color.main_button_text)

        val chevron = ImageView(activity).apply {
            setImageResource(R.drawable.ic_spinner_arrow)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val answer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            addView(TextView(activity).apply {
                text = entry.answer
                textSize = 14f
                alpha = 0.85f
                setTextColor(textColor)
                setPadding(0, 6.dp(activity), 0, 0)
            })
        }
        entry.action?.let { action ->
            answer.addView(Button(activity).apply {
                setText(R.string.help_go)
                isAllCaps = false
                setTextColor(textColor)
                background = ContextCompat.getDrawable(activity, R.drawable.main_button_rounded)
                minHeight = 40.dp(activity)
                minimumHeight = 40.dp(activity)
                setPadding(20.dp(activity), 0, 20.dp(activity), 0)
                setOnClickListener {
                    dialog.dismiss()
                    action()
                }
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.END
                topMargin = 8.dp(activity)
            })
        }
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(activity).apply {
                text = entry.title
                textSize = 15f
                setTextColor(textColor)
                setTypeface(typeface, Typeface.BOLD)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(chevron, LinearLayout.LayoutParams(20.dp(activity), 20.dp(activity)))
        }

        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            minimumHeight = 48.dp(activity)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12.dp(activity), 10.dp(activity), 12.dp(activity), 10.dp(activity))
            background = ContextCompat.getDrawable(activity, R.drawable.main_help_entry)
            addView(header)
            addView(answer)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 6.dp(activity) }
            setOnClickListener {
                val open = answer.visibility != View.VISIBLE
                answer.visibility = if (open) View.VISIBLE else View.GONE
                chevron.animate().rotation(if (open) 180f else 0f).setDuration(CHEVRON_MS).start()
            }
        }
    }
}
