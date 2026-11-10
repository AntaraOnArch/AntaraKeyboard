package com.example.antarakeyboard.data

import com.example.antarakeyboard.model.KeyConfig
import com.example.antarakeyboard.model.KeyShape
import com.example.antarakeyboard.model.KeyboardConfig
import com.example.antarakeyboard.model.RowConfig
import com.example.antarakeyboard.model.addLongPress

/**
 * Classic phone keyboard layouts ([KeyShape.CLASSIC] 4 rows, [KeyShape.CLASSIC_NUMBERS] 5 rows
 * with a number row): full-width rows of rounded keys with different widths (wide space,
 * 1.5-wide Shift / ⌫ / Enter …). The row count is fixed by the shape.
 *
 * Their layouts, side buttons and numeric pages are stored under their own layout id
 * ([layoutId]), next to the 3 / 4 / 5 row layouts of the other shapes.
 */
object ClassicLayouts {

    /** Layout ids of the classic shapes (the other shapes use their row count 3 / 4 / 5). */
    const val LAYOUT_CLASSIC = 40
    const val LAYOUT_CLASSIC_NUMBERS = 50

    /** Width of a full row in key units (one letter key = 1). */
    const val ROW_UNITS = 10f

    /** Narrowest space key (units) when a row is full. */
    private const val MIN_SPACE_UNITS = 2f

    /** Width of a side button in a classic row (units); it is only an icon, so narrow. */
    const val SIDE_BUTTON_UNITS = 1f

    /** Storage id of a layout: the row count, or the classic shape's own id. */
    fun layoutId(shape: KeyShape, rowCount: Int): Int = when (shape) {
        KeyShape.CLASSIC -> LAYOUT_CLASSIC
        KeyShape.CLASSIC_NUMBERS -> LAYOUT_CLASSIC_NUMBERS
        else -> rowCount
    }

    fun isClassic(layoutId: Int): Boolean =
        layoutId == LAYOUT_CLASSIC || layoutId == LAYOUT_CLASSIC_NUMBERS

    /** Rows a classic shape always has. */
    fun rowCountOf(shape: KeyShape): Int = if (shape == KeyShape.CLASSIC_NUMBERS) 5 else 4

    /**
     * Width of every key of a row in units. Space keys share what is left of [rowUnits]
     * (at least [MIN_SPACE_UNITS] together); every other key has a fixed width by its label,
     * so a key keeps its width wherever it is moved. [sideUnits] is taken by side buttons.
     */
    fun keyWidths(
        shape: KeyShape,
        labels: List<String>,
        sideUnits: Float = 0f,
        rowUnits: Float = ROW_UNITS
    ): List<Float> {
        val fixed = labels.map { if (it == " ") 0f else fixedWidth(shape, it) }
        val spaces = labels.count { it == " " }
        if (spaces == 0) return fixed
        val space = maxOf(MIN_SPACE_UNITS, rowUnits - sideUnits - fixed.sum()) / spaces
        return labels.mapIndexed { i, label -> if (label == " ") space else fixed[i] }
    }

    /** One row to lay out: its key labels and the units its side buttons take. */
    data class RowSpec(val labels: List<String>, val sideUnits: Float = 0f)

    /**
     * Widths of all rows of a keyboard, plus the keyboard width in units (at least [ROW_UNITS]):
     * the widest row decides it (e.g. ten letters plus two side buttons = 12), and the space key
     * of every row stretches so that row reaches from edge to edge too.
     */
    fun layoutWidths(shape: KeyShape, rows: List<RowSpec>): Pair<Float, List<List<Float>>> {
        val units = maxOf(
            ROW_UNITS,
            rows.maxOfOrNull { keyWidths(shape, it.labels, it.sideUnits).sum() + it.sideUnits } ?: 0f
        )
        return units to rows.map { keyWidths(shape, it.labels, it.sideUnits, units) }
    }

    private fun fixedWidth(shape: KeyShape, label: String): Float = when (label) {
        "⇧", "⌫" -> 1.5f
        "↵" -> if (shape == KeyShape.CLASSIC) 2f else 1.5f
        "123", "abc", "ABC" -> if (shape == KeyShape.CLASSIC) 1.25f else 1.5f
        "😊" -> if (shape == KeyShape.CLASSIC) 1.25f else 1f
        else -> 1f
    }

    /* ───────── SIDE BUTTONS ───────── */

    /** Number of side button slots: two per letter row on [KeyShape.CLASSIC], none with the number row. */
    fun sideSlotCount(layoutId: Int): Int = if (layoutId == LAYOUT_CLASSIC) 6 else 0

    /** Row of a side button slot (slots 0/1 on row 1, 2/3 on row 2, 4/5 on row 3; even = left). */
    fun sideSlotRow(index: Int): Int = index / 2

    /* ───────── DEFAULT LAYOUTS ───────── */

    private fun row(vararg labels: String) =
        RowConfig(labels.map { KeyConfig(it) }.toMutableList())

    /** Factory long press: `?` and `!` live on the period (there is no `?` key). */
    private fun KeyboardConfig.addClassicSymbolLongPress() = apply {
        PERIOD_LONG_PRESS.forEach { addLongPress(".", it) }
        listOf("!", "(", ")", "{", "}", "[", "]").forEach { addLongPress("?", it) }
    }

    /** Long press of `.` in the classic layouts; `?` and `!` first (see [withPunctuationOnPeriod]). */
    val PERIOD_LONG_PRESS = listOf("?", "!", "\"", ",", ":", ";")

    private val alphabetClassic: KeyboardConfig
        get() = KeyboardConfig(
            rows = mutableListOf(
                row("q", "w", "e", "r", "t", "z", "u", "i", "o", "p"),
                row("a", "s", "d", "f", "g", "h", "j", "k", "l"),
                row("⇧", "y", "x", "c", "v", "b", "n", "m", "⌫"),
                row("123", "😊", " ", ".", "↵")
            )
        ).addClassicSymbolLongPress()

    private val alphabetClassicNumbers: KeyboardConfig
        get() = KeyboardConfig(
            rows = mutableListOf(
                row("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
                row("q", "w", "e", "r", "t", "z", "u", "i", "o", "p"),
                row("a", "s", "d", "f", "g", "h", "j", "k", "l"),
                row("⇧", "y", "x", "c", "v", "b", "n", "m", "⌫"),
                row("123", "😊", " ", ".", "↵")
            )
        ).addClassicSymbolLongPress()

    private val numericClassic: KeyboardConfig
        get() = KeyboardConfig(
            rows = mutableListOf(
                row("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
                row("-", "/", ":", ";", "(", ")", "€", "&", "@", "\""),
                row("⇧", ".", "?", "*", "+", "'", "#", "%", "⌫"),
                row("abc", "😊", " ", "_", "↵")
            )
        ).addClassicSymbolLongPress()

    private val numericClassicNumbers: KeyboardConfig
        get() = KeyboardConfig(
            rows = mutableListOf(
                row("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
                row("+", "×", "÷", "=", "/", "_", "<", ">", "[", "]"),
                row("!", "@", "#", "€", "%", "^", "&", "*", "(", ")"),
                row("-", "'", "\"", ":", ";", "$", "?", "~", "⌫"),
                row("abc", "😊", " ", ".", "↵")
            )
        ).addClassicSymbolLongPress()

    /** Default alphabet layout of a classic layout id (a fresh copy). */
    fun defaultAlphabet(layoutId: Int): KeyboardConfig =
        if (layoutId == LAYOUT_CLASSIC_NUMBERS) alphabetClassicNumbers else alphabetClassic

    /** Default numeric layout of a classic layout id (a fresh copy). */
    fun defaultNumeric(layoutId: Int): KeyboardConfig =
        if (layoutId == LAYOUT_CLASSIC_NUMBERS) numericClassicNumbers else numericClassic

    /**
     * The classic layouts have no `?` key, so `?` and `!` must be on the period's long press.
     * Long press binds are global per key, so they are added there when a classic shape is chosen
     * (in front, if missing and there is room); everything else bound to `.` stays.
     */
    fun withPunctuationOnPeriod(current: List<String>?, max: Int): List<String> {
        val binds = current ?: return PERIOD_LONG_PRESS
        val missing = listOf("?", "!").filter { it !in binds }
        if (missing.isEmpty() || binds.size + missing.size > max) return binds
        return missing + binds
    }
}
