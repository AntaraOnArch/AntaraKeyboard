package com.example.antarakeyboard.model

/* =========================
   DATA MODELS
   ========================= */

data class KeyConfig(
    var label: String,
    var longPressBindings: MutableList<String> = mutableListOf()
)

data class RowConfig(
    var keys: MutableList<KeyConfig>
)

data class KeyboardConfig(
    var rows: MutableList<RowConfig>,
    var specialLeft: MutableList<KeyConfig> = mutableListOf(),
    var specialRight: MutableList<KeyConfig> = mutableListOf()
)

/* =========================
   HELPERS
   ========================= */

fun KeyboardConfig.findKey(label: String): KeyConfig? {
    rows.forEach { row ->
        row.keys.firstOrNull { it.label == label }?.let { return it }
    }
    return null
}

fun KeyboardConfig.addLongPress(keyLabel: String, char: String) {
    val key = findKey(keyLabel) ?: return
    if (!key.longPressBindings.contains(char)) {
        key.longPressBindings.add(char)
    }
}
fun KeyConfig.isSpaceLeftMarked(): Boolean {
    return label == " " && longPressBindings.contains(KeyMarkers.SPACE_LEFT)
}

fun KeyConfig.isSpaceRightMarked(): Boolean {
    return label == " " && longPressBindings.contains(KeyMarkers.SPACE_RIGHT)
}

fun KeyConfig.hasSpaceMarker(): Boolean {
    return isSpaceLeftMarked() || isSpaceRightMarked()
}

fun KeyboardConfig.ensureSpaceMarkers(): KeyboardConfig {
    var spaceCount = 0

    fun fixKey(key: KeyConfig): KeyConfig {
        if (key.label != " ") return key

        val index = spaceCount
        spaceCount++

        if (key.hasSpaceMarker()) return key

        val marker = if (index == 0) {
            KeyMarkers.SPACE_LEFT
        } else {
            KeyMarkers.SPACE_RIGHT
        }

        return key.copy(
            longPressBindings = key.longPressBindings.toMutableList().apply {
                add(marker)
            }
        )
    }

    return copy(
        rows = rows.map { row ->
            row.copy(keys = row.keys.map(::fixKey).toMutableList())
        }.toMutableList(),
        specialLeft = specialLeft.map(::fixKey).toMutableList(),
        specialRight = specialRight.map(::fixKey).toMutableList()
    )
}