package com.example.antarakeyboard.ui

import com.example.antarakeyboard.model.KeyConfig
import com.example.antarakeyboard.model.KeyboardConfig
import com.example.antarakeyboard.model.RowConfig
import com.example.antarakeyboard.model.addLongPress

private fun KeyboardConfig.addCommonSymbolLongPress() {
    listOf("\"", ",", ":", ";").forEach { ch ->
        addLongPress(".", ch)
    }

    listOf("!", "(", ")", "{", "}", "[", "]").forEach { ch ->
        addLongPress("?", ch)
    }
}

/* =========================
   ALPHABET - 5 ROWS
   26 letters + 2 spaces + . + ? + 123 + ↵ = 32
   ========================= */

val defaultKeyboardLayout: KeyboardConfig = KeyboardConfig(
    specialLeft = mutableListOf(),
    specialRight = mutableListOf(),
    rows = mutableListOf(
        // 1) w e t z i o
        RowConfig(
            mutableListOf(
                KeyConfig("w"), KeyConfig("e"), KeyConfig("t"),
                KeyConfig("z"), KeyConfig("i"), KeyConfig("o")
            )
        ),

        // 2) q a r g u l p
        RowConfig(
            mutableListOf(
                KeyConfig("q"), KeyConfig("a"), KeyConfig("r"),
                KeyConfig("g"), KeyConfig("u"), KeyConfig("l"), KeyConfig("p")
            )
        ),

        // 3) . space f h space ?
        RowConfig(
            mutableListOf(
                KeyConfig("."), KeyConfig(" "),
                KeyConfig("f"), KeyConfig("h"),
                KeyConfig(" "), KeyConfig("?")
            )
        ),

        // 4) y s d n m j k
        RowConfig(
            mutableListOf(
                KeyConfig("y"), KeyConfig("s"), KeyConfig("d"),
                KeyConfig("n"), KeyConfig("m"), KeyConfig("j"), KeyConfig("k")
            )
        ),

        // 5) x c v b 123 ↵
        RowConfig(
            mutableListOf(
                KeyConfig("x"), KeyConfig("c"), KeyConfig("v"),
                KeyConfig("b"), KeyConfig("123"), KeyConfig("↵")
            )
        )
    )
).also { cfg ->
    cfg.addCommonSymbolLongPress()
}

/* =========================
   ALPHABET - 4 ROWS
   26 letters + 2 spaces + . + ? + 123 + ↵ = 32
   8 + 8 + 8 + 8
   ========================= */

val defaultFourRowKeyboardLayout: KeyboardConfig = KeyboardConfig(
    specialLeft = mutableListOf(),
    specialRight = mutableListOf(),
    rows = mutableListOf(
        // 1) q w e r t z u i
        RowConfig(
            mutableListOf(
                KeyConfig("q"), KeyConfig("w"), KeyConfig("e"), KeyConfig("r"),
                KeyConfig("t"), KeyConfig("z"), KeyConfig("u"), KeyConfig("i")
            )
        ),

        // 2) o p a s d f g h
        RowConfig(
            mutableListOf(
                KeyConfig("o"), KeyConfig("p"), KeyConfig("a"), KeyConfig("s"),
                KeyConfig("d"), KeyConfig("f"), KeyConfig("g"), KeyConfig("h")
            )
        ),

        // 3) j k l y x . ? space
        RowConfig(
            mutableListOf(
                KeyConfig("j"), KeyConfig("k"), KeyConfig("l"),
                KeyConfig("y"), KeyConfig("x"),
                KeyConfig("."), KeyConfig("?"), KeyConfig(" ")
            )
        ),

        // 4) c v b n m space 123 ↵
        RowConfig(
            mutableListOf(
                KeyConfig("c"), KeyConfig("v"), KeyConfig("b"),
                KeyConfig("n"), KeyConfig("m"),
                KeyConfig(" "), KeyConfig("123"), KeyConfig("↵")
            )
        )
    )
).also { cfg ->
    cfg.addCommonSymbolLongPress()
}

/* =========================
   ALPHABET - 3 ROWS
   26 letters + 2 spaces + . + ? + 123 + ↵ + 😊 = 33
   11 + 11 + 11
   ========================= */

val defaultThreeRowKeyboardLayoutQwertz: KeyboardConfig = KeyboardConfig(
    specialLeft = mutableListOf(),
    specialRight = mutableListOf(),
    rows = mutableListOf(
        // 1) q w e r t z u i o p
        RowConfig(
            mutableListOf(
                KeyConfig("q"), KeyConfig("w"), KeyConfig("e"), KeyConfig("r"), KeyConfig("t"),
                KeyConfig("z"), KeyConfig("u"), KeyConfig("i"), KeyConfig("o"), KeyConfig("p"), KeyConfig(".")
            )
        ),

        // 2) a s d f g h j k l m
        RowConfig(
            mutableListOf(
                KeyConfig("a"), KeyConfig("s"), KeyConfig("d"), KeyConfig("f"), KeyConfig("g"),
                KeyConfig("h"), KeyConfig("j"), KeyConfig("k"), KeyConfig("l"), KeyConfig("m"), KeyConfig("?")
            )
        ),

        // 3) y x c v b n . space ? space 😊 123 ↵
        RowConfig(
            mutableListOf(
                KeyConfig("y"), KeyConfig("x"), KeyConfig("c"),
                KeyConfig("v"), KeyConfig("b"), KeyConfig(" "),
                KeyConfig(" "), KeyConfig("n"),
                KeyConfig("😊"), KeyConfig("123"), KeyConfig("↵")
            )
        )
    )
).also { cfg ->
    cfg.addCommonSymbolLongPress()
}

/* =========================
   NUMERIC - 5 ROWS
   bez ⇧ i ⌫ u glavnim redovima
   ========================= */

val defaultNumericLayout: KeyboardConfig = KeyboardConfig(
    specialLeft = mutableListOf(),
    specialRight = mutableListOf(),
    rows = mutableListOf(
        // 1) ~ 1 2 3 4 <
        RowConfig(
            mutableListOf(
                KeyConfig("~"), KeyConfig("1"), KeyConfig("2"),
                KeyConfig("3"), KeyConfig("4"), KeyConfig("<")
            )
        ),

        // 2) € + 5 6 7 ( )
        RowConfig(
            mutableListOf(
                KeyConfig("€"), KeyConfig("+"), KeyConfig("5"),
                KeyConfig("6"), KeyConfig("7"), KeyConfig("("), KeyConfig(")")
            )
        ),

        // 3) . space 8 9 space ?
        RowConfig(
            mutableListOf(
                KeyConfig("."), KeyConfig(" "),
                KeyConfig("8"), KeyConfig("9"),
                KeyConfig(" "), KeyConfig("?")
            )
        ),

        // 4) > - _ 0 * / !
        RowConfig(
            mutableListOf(
                KeyConfig(">"), KeyConfig("-"), KeyConfig("_"),
                KeyConfig("0"), KeyConfig("*"), KeyConfig("/"), KeyConfig("!")
            )
        ),

        // 5) & % @ # abc ↵
        RowConfig(
            mutableListOf(
                KeyConfig("&"), KeyConfig("%"), KeyConfig("@"),
                KeyConfig("#"), KeyConfig("abc"), KeyConfig("↵")
            )
        )
    )
).also { cfg ->
    cfg.addCommonSymbolLongPress()
}

/* =========================
   NUMERIC - 4 ROWS
   ========================= */

val defaultFourRowNumericLayout: KeyboardConfig = KeyboardConfig(
    specialLeft = mutableListOf(),
    specialRight = mutableListOf(),
    rows = mutableListOf(
        // 1) 1 2 3 4 5 6 7 8
        RowConfig(
            mutableListOf(
                KeyConfig("1"), KeyConfig("2"), KeyConfig("3"), KeyConfig("4"),
                KeyConfig("5"), KeyConfig("6"), KeyConfig("7"), KeyConfig("8")
            )
        ),

        // 2) 9 0 + - * / ( )
        RowConfig(
            mutableListOf(
                KeyConfig("9"), KeyConfig("0"), KeyConfig("+"), KeyConfig("-"),
                KeyConfig("*"), KeyConfig("/"), KeyConfig("("), KeyConfig(")")
            )
        ),

        // 3) € % @ # & _ . ?
        RowConfig(
            mutableListOf(
                KeyConfig("€"), KeyConfig("%"), KeyConfig("@"), KeyConfig("#"),
                KeyConfig("&"), KeyConfig("_"), KeyConfig("."), KeyConfig("?")
            )
        ),

        // 4) ~ < > ! space space abc ↵
        RowConfig(
            mutableListOf(
                KeyConfig("~"), KeyConfig("<"), KeyConfig(">"), KeyConfig("!"),
                KeyConfig(" "), KeyConfig(" "), KeyConfig("abc"), KeyConfig("↵")
            )
        )
    )
).also { cfg ->
    cfg.addCommonSymbolLongPress()
}

/* =========================
   NUMERIC - 3 ROWS
   ========================= */

val defaultThreeRowNumericLayout: KeyboardConfig = KeyboardConfig(
    specialLeft = mutableListOf(),
    specialRight = mutableListOf(),
    rows = mutableListOf(
        // 1) 1 2 3 4 5 6 7 8 9 0
        RowConfig(
            mutableListOf(
                KeyConfig("1"), KeyConfig("2"), KeyConfig("3"), KeyConfig("4"), KeyConfig("5"),
                KeyConfig("6"), KeyConfig("7"), KeyConfig("8"), KeyConfig("9"), KeyConfig("0")
            )
        ),

        // 2) + - * / = ( ) . ? €
        RowConfig(
            mutableListOf(
                KeyConfig("+"), KeyConfig("-"), KeyConfig("*"), KeyConfig("/"), KeyConfig("="),
                KeyConfig("("), KeyConfig(")"), KeyConfig("."), KeyConfig("?"), KeyConfig("€")
            )
        ),

        // 3) % @ # & _ ! ~ space space abc ↵
        RowConfig(
            mutableListOf(
                KeyConfig("%"), KeyConfig("@"), KeyConfig("#"), KeyConfig("&"),
                KeyConfig("_"), KeyConfig("!"), KeyConfig("~"),
                KeyConfig(" "), KeyConfig(" "),
                KeyConfig("abc"), KeyConfig("↵")
            )
        )
    )
).also { cfg ->
    cfg.addCommonSymbolLongPress()
}

/* =========================
   UPPERCASE HELPERS
   ========================= */

private fun KeyboardConfig.toUppercaseLetters(): KeyboardConfig {
    fun up(k: KeyConfig): KeyConfig {
        val lbl = k.label
        val newLbl =
            if (lbl.length == 1 && lbl[0].isLetter()) lbl.uppercase()
            else lbl

        return k.copy(
            label = newLbl,
            longPressBindings = k.longPressBindings.toMutableList()
        )
    }

    return copy(
        rows = rows.map { row ->
            row.copy(keys = row.keys.map(::up).toMutableList())
        }.toMutableList(),
        specialLeft = specialLeft.map(::up).toMutableList(),
        specialRight = specialRight.map(::up).toMutableList()
    )
}

val defaultKeyboardLayoutUpper: KeyboardConfig =
    defaultKeyboardLayout.toUppercaseLetters().also { cfg ->
        cfg.addCommonSymbolLongPress()
    }

val defaultFourRowKeyboardLayoutUpper: KeyboardConfig =
    defaultFourRowKeyboardLayout.toUppercaseLetters().also { cfg ->
        cfg.addCommonSymbolLongPress()
    }

val defaultThreeRowKeyboardLayoutQwertzUpper: KeyboardConfig =
    defaultThreeRowKeyboardLayoutQwertz.toUppercaseLetters().also { cfg ->
        cfg.addCommonSymbolLongPress()
    }