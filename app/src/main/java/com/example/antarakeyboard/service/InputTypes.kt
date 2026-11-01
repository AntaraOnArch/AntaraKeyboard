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

    fun allowsSuggestions(info: EditorInfo?): Boolean = allowsSuggestions(info?.inputType ?: 0)

    /**
     * Word suggestions only make sense in normal text: not in passwords, numbers, e-mail
     * addresses or URLs, and not where the app asks for none (TYPE_TEXT_FLAG_NO_SUGGESTIONS).
     */
    fun allowsSuggestions(inputType: Int): Boolean {
        if (inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return false
        if (inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0) return false
        if (isPassword(inputType)) return false
        return when (inputType and InputType.TYPE_MASK_VARIATION) {
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_URI -> false
            else -> true
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
