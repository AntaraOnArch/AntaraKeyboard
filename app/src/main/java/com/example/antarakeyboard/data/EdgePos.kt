package com.example.antarakeyboard.data

data class EdgePos(
    val row: Int,
    val side: Side
) {
    enum class Side { LEFT, RIGHT }
}
