package com.example.antarakeyboard.model

enum class KeyShape {
    HEX,
    HEX_TALL,
    HEX_HALF_LEFT,
    HEX_HALF_RIGHT,
    TRIANGLE,
    CIRCLE,
    CUBE,

    /** Classic phone keyboard: 3 letter rows + a bottom row, keys of different widths (4 rows). */
    CLASSIC,

    /** Classic phone keyboard with a number row on top (5 rows). */
    CLASSIC_NUMBERS;

    /** A classic layout (fixed rows, full-width rows, keys of different widths, see ClassicLayouts). */
    val isClassic: Boolean
        get() = this == CLASSIC || this == CLASSIC_NUMBERS

    /** The shape a key is drawn with: classic layouts use the cube's rounded square. */
    val drawShape: KeyShape
        get() = if (isClassic) CUBE else this
}
