package com.example.antarakeyboard.data

import com.example.antarakeyboard.model.KeyboardConfig

/**
 * Ready-made letter layouts (QWERTZ, QWERTY, AZERTY, Dvorak, Colemak, Neo2, Workman) for the
 * Set layout → Letters and numbers page.
 *
 * Our default layouts are QWERTZ-based in every row count (3 rows: real QWERTZ rows; 4 and 5
 * rows: QWERTZ spread over the honeycomb). A preset is applied to the default layout by letting
 * every QWERTZ letter's key take the preset's letter from the same place on a physical keyboard,
 * so the shape of our layout stays and only the letters move. Letters a preset keeps where QWERTZ
 * has punctuation (Dvorak s, Colemak o, …) fill the QWERTZ letters left over, in reading order.
 */
object LayoutPresets {

    /**
     * [rows] are the three letter rows of the physical layout, one character per key from the left
     * (punctuation included, so the columns line up with QWERTZ). [extraLongPress] are letters of
     * the layout that have no place among our 26 letter keys.
     */
    data class Preset(
        val id: String,
        val name: String,
        val rows: List<String>,
        val extraLongPress: Map<String, List<String>> = emptyMap()
    )

    val QWERTZ = Preset("qwertz", "QWERTZ", listOf("qwertzuiopü", "asdfghjklöä", "yxcvbnm,.-"))
    val QWERTY = Preset("qwerty", "QWERTY", listOf("qwertyuiop[", "asdfghjkl;'", "zxcvbnm,./"))
    val AZERTY = Preset("azerty", "AZERTY", listOf("azertyuiop^", "qsdfghjklmù", "wxcvbn,;:!"))
    val DVORAK = Preset("dvorak", "Dvorak", listOf("',.pyfgcrl/", "aoeuidhtns-", ";qjkxbmwvz"))
    val COLEMAK = Preset("colemak", "Colemak", listOf("qwfpgjluy;[", "arstdhneio'", "zxcvbkm,./"))
    val NEO2 = Preset(
        "neo2", "Neo2", listOf("xvlcwkhgfqß", "uiaeosnrtdy", "üöäpzbm,.j"),
        extraLongPress = mapOf("a" to listOf("ä"), "o" to listOf("ö"), "u" to listOf("ü"), "s" to listOf("ß"))
    )
    val WORKMAN = Preset("workman", "Workman", listOf("qdrwbjfup;[", "ashtgyneoi'", "zxmcvkl,./"))

    val ALL = listOf(QWERTZ, QWERTY, AZERTY, DVORAK, COLEMAK, NEO2, WORKMAN)

    private fun isLetter(ch: Char) = ch in 'a'..'z'

    /** QWERTZ letter → letter of [preset] on the same physical key (see the class comment). */
    fun letterMap(preset: Preset): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val placed = mutableSetOf<Char>()
        val free = mutableListOf<Char>()
        QWERTZ.rows.forEachIndexed { r, row ->
            row.forEachIndexed { c, ref ->
                if (!isLetter(ref)) return@forEachIndexed
                val p = preset.rows.getOrNull(r)?.getOrNull(c)
                if (p != null && isLetter(p) && p !in placed) {
                    map[ref.toString()] = p.toString()
                    placed += p
                } else {
                    free += ref
                }
            }
        }
        val leftovers = preset.rows.joinToString("").filter { isLetter(it) && it !in placed }
        free.zip(leftovers.toList()).forEach { (ref, p) -> map[ref.toString()] = p.toString() }
        return map
    }

    /**
     * [defaultLayout] (QWERTZ-based) with its letter keys relabelled to [preset]. Letter keys lose
     * their binds (they belong to the old letter; the caller applies the global binds), the other
     * keys keep theirs.
     */
    fun apply(defaultLayout: KeyboardConfig, preset: Preset): KeyboardConfig {
        val map = letterMap(preset)
        return KeyboardConfig(
            rows = defaultLayout.rows.map { row ->
                row.copy(keys = row.keys.map { key ->
                    val letter = map[key.label]
                    if (letter == null) key.copy(longPressBindings = key.longPressBindings.toMutableList())
                    else key.copy(label = letter, longPressBindings = mutableListOf())
                }.toMutableList())
            }.toMutableList(),
            specialLeft = defaultLayout.specialLeft.toMutableList(),
            specialRight = defaultLayout.specialRight.toMutableList()
        )
    }

    /** The preset [layout] was built from (same labels in the same places), or null for a custom one. */
    fun detect(layout: KeyboardConfig, defaultLayout: KeyboardConfig): Preset? {
        val labels = layout.rows.map { row -> row.keys.map { it.label } }
        return ALL.firstOrNull { preset ->
            apply(defaultLayout, preset).rows.map { row -> row.keys.map { it.label } } == labels
        }
    }
}
