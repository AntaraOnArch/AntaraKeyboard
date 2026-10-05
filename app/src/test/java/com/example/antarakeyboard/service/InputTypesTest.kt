package com.example.antarakeyboard.service

import android.text.InputType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputTypesTest {

    private val text = InputType.TYPE_CLASS_TEXT
    private val number = InputType.TYPE_CLASS_NUMBER

    @Test
    fun passwordVariationsAreDetected() {
        assertTrue(InputTypes.isPassword(text or InputType.TYPE_TEXT_VARIATION_PASSWORD))
        assertTrue(InputTypes.isPassword(text or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD))
        assertTrue(InputTypes.isPassword(text or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
        assertTrue(InputTypes.isPassword(number or InputType.TYPE_NUMBER_VARIATION_PASSWORD))
    }

    @Test
    fun regularFieldsAreNotPasswords() {
        assertFalse(InputTypes.isPassword(text))
        assertFalse(InputTypes.isPassword(text or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS))
        assertFalse(InputTypes.isPassword(text or InputType.TYPE_TEXT_FLAG_MULTI_LINE))
        assertFalse(InputTypes.isPassword(number))
        assertFalse(InputTypes.isPassword(InputType.TYPE_NULL))
    }

    @Test
    fun numericClassesPreferNumericLayout() {
        assertTrue(InputTypes.prefersNumeric(number))
        assertTrue(InputTypes.prefersNumeric(number or InputType.TYPE_NUMBER_FLAG_DECIMAL))
        assertTrue(InputTypes.prefersNumeric(InputType.TYPE_CLASS_PHONE))
        assertTrue(InputTypes.prefersNumeric(InputType.TYPE_CLASS_DATETIME))
        assertFalse(InputTypes.prefersNumeric(text))
        assertFalse(InputTypes.prefersNumeric(InputType.TYPE_NULL))
    }
}
