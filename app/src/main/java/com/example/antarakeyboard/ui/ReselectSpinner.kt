package com.example.antarakeyboard.ui

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatSpinner

/**
 * Spinner that also reports picking the item that is already selected
 * (a plain Spinner stays silent then). Used by the Theme dropdown so choosing
 * Custom / RGB again reopens its popup.
 */
class ReselectSpinner @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.spinnerStyle
) : AppCompatSpinner(context, attrs, defStyleAttr) {

    /** Called when the user picks the already selected position. */
    var onReselected: ((position: Int) -> Unit)? = null

    override fun setSelection(position: Int) {
        val reselected = position == selectedItemPosition
        super.setSelection(position)
        if (reselected) onReselected?.invoke(position)
    }

    override fun setSelection(position: Int, animate: Boolean) {
        val reselected = position == selectedItemPosition
        super.setSelection(position, animate)
        if (reselected) onReselected?.invoke(position)
    }
}
