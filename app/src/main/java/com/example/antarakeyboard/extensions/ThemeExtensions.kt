package com.example.antarakeyboard.extensions

import android.content.Context

/** Color of theme attribute [attr], or [fallback] when the theme does not define it as a color. */
fun Context.themeColor(attr: Int, fallback: Int): Int {
    val tv = android.util.TypedValue()
    return if (
        theme.resolveAttribute(attr, tv, true) &&
        tv.type in android.util.TypedValue.TYPE_FIRST_COLOR_INT..
        android.util.TypedValue.TYPE_LAST_COLOR_INT
    ) {
        tv.data
    } else {
        fallback
    }
}
