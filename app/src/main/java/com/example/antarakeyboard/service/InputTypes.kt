package com.example.antarakeyboard.service

import android.text.InputType
import android.view.inputmethod.EditorInfo

/** Classifies the focused editor from its [EditorInfo.inputType]. */
object InputTypes {

    fun isPassword(info: EditorInfo?): Boolean = isPassword(info?.inputType ?: 0)

    fun prefersNumeric(info: EditorInfo?): Boolean = prefersNumeric(info?.inputType ?: 0)

    fun isPassword(inputType: Int): Boolean {
        val cls = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return when (cls) {
            InputType.TYPE_CLASS_TEXT ->
                variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            InputType.TYPE_CLASS_NUMBER ->
                variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }

    fun prefersNumeric(inputType: Int): Boolean {
        return when (inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_NUMBER,
            InputType.TYPE_CLASS_PHONE,
            InputType.TYPE_CLASS_DATETIME -> true
            else -> false
        }
    }
}
