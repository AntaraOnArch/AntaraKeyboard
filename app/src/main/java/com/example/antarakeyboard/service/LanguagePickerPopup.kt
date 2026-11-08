package com.example.antarakeyboard.service

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.drawable.toDrawable
import com.example.antarakeyboard.R
import com.example.antarakeyboard.data.AppLanguageSettings
import com.example.antarakeyboard.data.AppLanguages
import com.example.antarakeyboard.extensions.dp

/** Language list opened on the keyboard by holding both space keys. */
class LanguagePickerPopup(private val context: Context, private val host: Host) {

    interface Host {
        val themedContext: Context
        /** The popup is placed relative to this view's window. */
        val rootView: View
        fun colors(): EmojiPickerManager.Colors
        /** Called when the popup opens: hide the other popups, end the hold timer. */
        fun onShow()
        fun onDismissed()
        fun onLanguagePicked(tag: String)
    }

    private var popup: PopupWindow? = null

    val isShowing: Boolean get() = popup != null

    fun hide() {
        popup?.dismiss()
        popup = null
    }

    /**
     * Dual-space picker: the same language choice as the main app (System default + every
     * translation, each in its own language). Choosing one sets the app language and the
     * keyboard script that goes with it (Cyrillic languages type Cyrillic).
     */
    fun show() {
        hide()
        host.onShow()
        val themedCtx = host.themedContext

        val popupWidth = (context.resources.displayMetrics.widthPixels * 0.86f).toInt()
            .coerceAtLeast(280.dp(context))

        // Same palette as the emoji picker: keyboard surface + key text
        val pickerColors = host.colors()
        val popupBg = pickerColors.panel
        val popupText = pickerColors.text

        val root = LinearLayout(themedCtx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp(this), 18.dp(this), 20.dp(this), 14.dp(this))
            setBackgroundColor(popupBg)
        }

        root.addView(TextView(themedCtx).apply {
            text = themedCtx.getString(R.string.main_language)
            textSize = 18f
            setTextColor(popupText)
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(0, 0, 0, 14.dp(this))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val tags = listOf("") + AppLanguages.TAGS
        val current = AppLanguageSettings.selectedTag(context).let { AppLanguages.match(it) ?: "" }

        val radioGroup = RadioGroup(themedCtx).apply { orientation = RadioGroup.VERTICAL }
        val idToTag = HashMap<Int, String>()
        tags.forEach { tag ->
            val id = View.generateViewId()
            idToTag[id] = tag
            radioGroup.addView(RadioButton(themedCtx).apply {
                this.id = id
                text = if (tag.isEmpty()) themedCtx.getString(R.string.language_system_default)
                else AppLanguages.nativeName(tag)
                textSize = 16f
                setTextColor(popupText)
                setPadding(0, 6.dp(this), 0, 6.dp(this))
                isFocusable = false
            }, RadioGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        idToTag.entries.firstOrNull { it.value == current }?.let { radioGroup.check(it.key) }

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val tag = idToTag[checkedId] ?: return@setOnCheckedChangeListener
            host.onLanguagePicked(tag)
        }

        val scroll = ScrollView(themedCtx).apply {
            isFillViewport = false
            addView(radioGroup, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (context.resources.displayMetrics.heightPixels * 0.46f).toInt()
        ))

        root.addView(TextView(themedCtx).apply {
            text = themedCtx.getString(R.string.action_close)
            textSize = 14f
            setTextColor(popupText)
            gravity = Gravity.CENTER
            setPadding(8.dp(this), 16.dp(this), 8.dp(this), 0)
            isClickable = true
            isFocusable = false
            setOnClickListener { hide() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val popup = PopupWindow(root, popupWidth, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            isOutsideTouchable = true
            isFocusable = true
            isClippingEnabled = false
            elevation = 12.dp(context).toFloat()
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            setOnDismissListener {
                this@LanguagePickerPopup.popup = null
                host.onDismissed()
            }
        }
        this.popup = popup

        // showAtLocation in an IME is relative to the rootView window: convert screen Y to local Y
        val screenW = context.resources.displayMetrics.widthPixels
        val screenH = context.resources.displayMetrics.heightPixels
        val rootLoc = IntArray(2)
        host.rootView.getLocationOnScreen(rootLoc)
        val localX = ((screenW - popupWidth) / 2).coerceAtLeast(8.dp(context))
        val localY = (screenH * 0.105f).toInt().coerceAtLeast(54.dp(context)) - rootLoc[1]

        popup.showAtLocation(host.rootView, Gravity.NO_GRAVITY, localX, localY)
    }
}
