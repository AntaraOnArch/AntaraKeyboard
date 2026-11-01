package com.example.antarakeyboard.model

object KeyMarkers {
    const val EDGE_GHOST = "__EDGE_GHOST__"
    const val USER_EMPTY = "__USER_EMPTY__"

    const val SPACE_LEFT = "__SPACE_LEFT__"
    const val SPACE_RIGHT = "__SPACE_RIGHT__"

    /** Shift label: outlined arrow when off, filled arrow when on (U+FE0E keeps it text, not emoji). */
    const val SHIFT_OFF = "⇧"
    const val SHIFT_ON = "⬆\uFE0E"
}