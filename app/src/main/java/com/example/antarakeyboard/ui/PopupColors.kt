package com.example.antarakeyboard.ui

/**
 * Centralized color constants for popup elements.
 * These are intentionally hardcoded as popups maintain consistent dark appearance
 * regardless of system theme for better visibility over the keyboard.
 */
object PopupColors {
    // Popup backgrounds (dark theme for visibility)
    const val POPUP_BG = 0xFF1E1E1E.toInt()
    const val CONTAINER_BG = 0xFF2A2A2A.toInt()
    const val BUTTON_BG = 0xFF3A3A3A.toInt()

    // Text colors
    const val TEXT_PRIMARY = 0xFFFFFFFF.toInt()
    const val TEXT_SECONDARY = 0xAAFFFFFF.toInt()
    const val TEXT_HINT = 0x88FFFFFF.toInt()

    // Preview/highlight
    const val PREVIEW_BG = 0xFFFFFFFF.toInt()
    const val PREVIEW_TEXT = 0xFF000000.toInt()
}
