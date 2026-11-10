package com.example.antarakeyboard.ui

import android.animation.LayoutTransition
import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.Typeface
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
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
    private const val WIDTH_FRACTION = 0.9f
    /** Space above the popup (below the status bar) and below it (above the navigation bar). */
    private const val TOP_GAP_DP = 16
    private const val BOTTOM_GAP_DP = 72
    /** Blur of the screen behind the popup (Android 12+, where the device supports it). */
    private const val BLUR_DP = 12

    fun show(activity: Activity, groups: List<Group>, footer: Footer? = null) {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val pad = 16.dp(activity)

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            // Opening an answer slides the entries below it down
            layoutTransition = LayoutTransition().apply { enableTransitionType(LayoutTransition.CHANGING) }
        }
        // Title with a small X in the corner (a tap outside or Back closes it too)
        root.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(activity).apply {
                setText(R.string.help_title)
                textSize = 18f
                setTypeface(typeface, Typeface.BOLD)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(ImageButton(activity).apply {
                setImageResource(R.drawable.ic_close)
                background = ContextCompat.getDrawable(activity, R.drawable.main_icon_button)
                contentDescription = activity.getString(R.string.action_close)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(10.dp(activity), 10.dp(activity), 10.dp(activity), 10.dp(activity))
                setOnClickListener { dialog.dismiss() }
            }, LinearLayout.LayoutParams(40.dp(activity), 40.dp(activity)))
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

        // Full-screen window: a lighter (blurred where supported) scrim marks where a tap closes
        // the popup; the card sits near the top and wraps its content, ending well above the
        // navigation bar (then it scrolls)
        val card = ScrollView(activity).apply {
            addView(root)
            // Framed like every popup (tutorial card look)
            background = GradientDrawable().apply {
                cornerRadius = 16.dp(activity).toFloat()
                setColor(ContextCompat.getColor(activity, R.color.main_app_bg))
                setStroke(2.dp(activity), ContextCompat.getColor(activity, R.color.special_fill))
            }
            val stroke = 2.dp(activity)
            setPadding(stroke, stroke, stroke, stroke)
            clipToOutline = true
            // Taps on the card itself never reach the scrim
            isClickable = true
        }
        val scrim = FrameLayout(activity).apply {
            setBackgroundColor(ContextCompat.getColor(activity, R.color.help_scrim))
            setOnClickListener { dialog.cancel() }
            addView(card, FrameLayout.LayoutParams(
                (activity.resources.displayMetrics.widthPixels * WIDTH_FRACTION).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            ))
            // The window covers the system bars too: keep the card clear of them
            ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                (card.layoutParams as FrameLayout.LayoutParams).apply {
                    topMargin = bars.top + TOP_GAP_DP.dp(activity)
                    bottomMargin = bars.bottom + BOTTOM_GAP_DP.dp(activity)
                }
                card.requestLayout()
                WindowInsetsCompat.CONSUMED
            }
        }
        dialog.setContentView(scrim)
        dialog.window?.apply {
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            WindowCompat.setDecorFitsSystemWindows(this, false)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                attributes = attributes.apply { blurBehindRadius = BLUR_DP.dp(activity) }
            }
        }
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
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
