package com.example.antarakeyboard.extensions

import android.content.Context
import android.content.res.Resources
import android.view.View

/**
 * Extension functions for converting dp values to pixels.
 * Provides overloads for Resources, Context, and View for convenience.
 */

private inline val Resources.density: Float
    get() = displayMetrics.density

// Int -> pixels (Int)
fun Int.dp(resources: Resources): Int = (this * resources.density).toInt()
fun Int.dp(context: Context): Int = dp(context.resources)
fun Int.dp(view: View): Int = dp(view.resources)

// Float -> pixels (Float)
fun Float.dpF(resources: Resources): Float = this * resources.density
fun Float.dpF(context: Context): Float = dpF(context.resources)
fun Float.dpF(view: View): Float = dpF(view.resources)
