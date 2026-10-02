package com.example.antarakeyboard.extensions

import android.content.Context
import android.content.res.Resources
import android.view.View

/**
 * Converts Int dp value to pixels using the provided Resources.
 */
fun Int.dp(resources: Resources): Int =
    (this * resources.displayMetrics.density).toInt()

/**
 * Converts Float dp value to pixels using the provided Resources.
 */
fun Float.dpF(resources: Resources): Float =
    this * resources.displayMetrics.density

/**
 * Converts Int dp value to pixels using Context.
 */
fun Int.dp(context: Context): Int =
    (this * context.resources.displayMetrics.density).toInt()

/**
 * Converts Float dp value to pixels using Context.
 */
fun Float.dpF(context: Context): Float =
    this * context.resources.displayMetrics.density

/**
 * Converts Int dp value to pixels using View's resources.
 */
fun Int.dp(view: View): Int =
    (this * view.resources.displayMetrics.density).toInt()

/**
 * Converts Float dp value to pixels using View's resources.
 */
fun Float.dpF(view: View): Float =
    this * view.resources.displayMetrics.density
