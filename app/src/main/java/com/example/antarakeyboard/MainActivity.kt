package com.example.antarakeyboard

import android.app.Dialog
import android.content.ClipData
import android.content.ClipDescription
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.DragEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.example.antarakeyboard.data.EdgePos
import com.example.antarakeyboard.data.EdgeSlotsStorage
import com.example.antarakeyboard.data.GlobalLongPressStorage
import com.example.antarakeyboard.data.KeyboardPrefs
import com.example.antarakeyboard.model.EdgeActionType
import com.example.antarakeyboard.model.EdgeSlot
import com.example.antarakeyboard.model.KeyShape
import com.example.antarakeyboard.model.KeyboardConfig
import com.example.antarakeyboard.ui.ColorWheelView
import com.example.antarakeyboard.ui.LayoutEditorBinder
import com.example.antarakeyboard.ui.LongPressEditorBinder
import com.example.antarakeyboard.ui.LongPressKeyPickerDialog
import com.example.antarakeyboard.ui.ShapePreviewView
import com.example.antarakeyboard.ui.defaultFourRowKeyboardLayout
import com.example.antarakeyboard.ui.defaultFourRowNumericLayout
import com.example.antarakeyboard.ui.defaultHorizontalCenterLayout
import com.example.antarakeyboard.ui.defaultKeyboardLayout
import com.example.antarakeyboard.ui.defaultNumericLayout
import com.example.antarakeyboard.ui.defaultThreeRowKeyboardLayoutQwertz
import com.example.antarakeyboard.ui.defaultThreeRowNumericLayout
import android.graphics.Color
import android.content.Context
import com.example.antarakeyboard.R

class MainActivity : AppCompatActivity() {

    private lateinit var preview: ShapePreviewView
    private lateinit var hex: RadioButton
    private lateinit var tri: RadioButton
    private lateinit var circle: RadioButton
    private lateinit var cube: RadioButton
    private lateinit var bindLPButton: Button
    private lateinit var resetLayoutButton: Button
    private lateinit var radio3Rows: RadioButton
    private lateinit var radio4Rows: RadioButton
    private lateinit var radio5Rows: RadioButton
    private lateinit var rowCountGroup: RadioGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("theme_prefs", MODE_PRIVATE)
        val isDark = prefs.getBoolean("dark_mode", true)

        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        setContentView(R.layout.activity_main)

        val themeBtn: Button = findViewById(R.id.btnThemeToggle)

        fun applyThemeButtonText(isDarkMode: Boolean) {
            themeBtn.text = if (isDarkMode) {
                "Switch to Lightmode"
            } else {
                "Switch to Darkmode"
            }
        }

        applyThemeButtonText(isDark)

        themeBtn.setOnClickListener {
            val prefs2 = getSharedPreferences("theme_prefs", MODE_PRIVATE)
            val current = prefs2.getBoolean("dark_mode", true)
            val newMode = !current

            prefs2.edit().putBoolean("dark_mode", newMode).apply()

            AppCompatDelegate.setDefaultNightMode(
                if (newMode) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )

            applyThemeButtonText(newMode)
        }

        val btnEnableKeyboard: Button = findViewById(R.id.btnEnableKeyboard)
        val btnChooseKeyboard: Button = findViewById(R.id.btnChooseKeyboard)
        val btnSetLayout: Button = findViewById(R.id.btnSetLayout)
        val btnColors: Button = findViewById(R.id.btnColors)

        btnEnableKeyboard.setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }

        btnChooseKeyboard.setOnClickListener {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showInputMethodPicker()
        }

        btnSetLayout.setOnClickListener {
            openLayoutEditorDialog()
        }

        btnColors.setOnClickListener {
            openColorsDialog()
        }

        preview = findViewById(R.id.preview)

        hex = findViewById(R.id.hexBtn)
        tri = findViewById(R.id.triBtn)
        circle = findViewById(R.id.circleBtn)
        cube = findViewById(R.id.cubeBtn)

        bindLPButton = findViewById(R.id.bindLPButton)
        resetLayoutButton = findViewById(R.id.resetLayoutButton)

        val savedShape = KeyboardPrefs.getShape(this)
        preview.shape = savedShape
        setCheckedForShape(savedShape)

        hex.setOnCheckedChangeListener { _, checked ->
            if (checked) applyShape(KeyShape.HEX)
        }

        tri.setOnCheckedChangeListener { _, checked ->
            if (checked) applyShape(KeyShape.TRIANGLE)
        }

        circle.setOnCheckedChangeListener { _, checked ->
            if (checked) applyShape(KeyShape.CIRCLE)
        }

        cube.setOnCheckedChangeListener { _, checked ->
            if (checked) applyShape(KeyShape.CUBE)
        }

        rowCountGroup = findViewById(R.id.rowCountGroup)
        radio3Rows = findViewById(R.id.radio3Rows)
        radio4Rows = findViewById(R.id.radio4Rows)
        radio5Rows = findViewById(R.id.radio5Rows)

        val savedRowCount = KeyboardPrefs.getRowCount(this)
        setCheckedForRowCount(savedRowCount)

        rowCountGroup.setOnCheckedChangeListener { _, checkedId ->
            val rowCount = when (checkedId) {
                R.id.radio3Rows -> 3
                R.id.radio4Rows -> 4
                R.id.radio5Rows -> 5
                else -> 3
            }

            saveDefaultsForRowCount(rowCount)

            Toast.makeText(this, "Broj redova: $rowCount", Toast.LENGTH_SHORT).show()
        }

        bindLPButton.setOnClickListener {
            openLongPressEditorDialog()
        }

        resetLayoutButton.setOnClickListener {
            val currentRowCount = KeyboardPrefs.getRowCount(this)
            val currentShape = KeyboardPrefs.getShape(this)
            val isDark = getSharedPreferences("theme_prefs", MODE_PRIVATE)
                .getBoolean("dark_mode", true)

            // 1. Resetiraj horizontal center layout
            KeyboardPrefs.clearHorizontalCenterLayoutForRowCount(this, currentRowCount)
            KeyboardPrefs.saveHorizontalCenterLayoutForRowCount(this, currentRowCount, defaultHorizontalCenterLayout)

            // 2. Resetiraj glavne layoute na default, ALI zadrži globalne bindove
            KeyboardPrefs.clearAlphabetLayoutForRowCount(this, currentRowCount)
            KeyboardPrefs.clearNumericLayoutForRowCount(this, currentRowCount)

            val alphabetWithBinds = KeyboardPrefs.loadAlphabetLayoutWithGlobalBinds(this, currentRowCount)
            val numericWithBinds = KeyboardPrefs.loadNumericLayoutWithGlobalBinds(this, currentRowCount)

            KeyboardPrefs.saveAlphabetLayoutForRowCount(this, currentRowCount, alphabetWithBinds)
            KeyboardPrefs.saveNumericLayoutForRowCount(this, currentRowCount, numericWithBinds)

            // Resetiraj SVE boje na default teme
            resetAllColorsToThemeDefault(isDark)

            // NOVO: Signaliziraj tipkovnici da se recreate
            val keyboardIntent = Intent("com.example.antarakeyboard.RECREATE_KEYBOARD")
            sendBroadcast(keyboardIntent)

            // 4. Resetiraj side buttons za trenutni broj redova
            resetSideButtonsForRowCount(currentRowCount)

            // 5. Update UI
            preview.shape = currentShape
            setCheckedForShape(currentShape)

            Toast.makeText(this, "Layout i boje resetirani na default", Toast.LENGTH_SHORT).show()
        }
    }

    /* =========================
       COLORS DIALOG
       ========================= */
    private fun resetAllColorsToThemeDefault(isDark: Boolean) {
        val keyFill = if (isDark) {
            getColor(R.color.key_fill_dark)
        } else {
            getColor(R.color.key_fill_light)
        }

        val keyText = themeColor(
            R.attr.keyText,
            if (isDark) Color.WHITE else Color.BLACK
        )

        val specialFill = getColor(R.color.special_fill)
        val specialText = getColor(R.color.special_text)

        val keyboardBg = if (isDark) {
            getColor(R.color.keyboard_bg_dark)
        } else {
            getColor(R.color.keyboard_bg_light)
        }

        // 1. Space colors — oba spacea na theme default
        KeyboardPrefs.setSpaceColors(this, keyFill, keyFill, true)

        // opcionalno: počisti eventualni stari individual zapis za " "
        KeyboardPrefs.clearKeyIndividualColors(this, " ")

        // 2. Enter colors
        KeyboardPrefs.setEnterColors(this, specialFill, specialText)

        // 3. Side buttons colors
        val sideTextColor = themeColor(
            R.attr.edgeIconText,
            if (isDark) Color.WHITE else Color.BLACK
        )
        KeyboardPrefs.setSideButtonsColors(
            this,
            Color.TRANSPARENT,
            sideTextColor,
            true
        )

        // 4. Keys colors
        KeyboardPrefs.setKeysColors(this, keyFill, keyText, true)

        // 5. Background color
        KeyboardPrefs.setBackgroundColor(this, keyboardBg, true)

        // 6. Očisti individualne keys boje
        val rowCount = KeyboardPrefs.getRowCount(this)
        val layout = KeyboardPrefs.loadAlphabetLayoutForRowCount(this, rowCount)

        val skipLabels = setOf(
            " ",
            "↵",
            "⇧",
            "⌫",
            "😊"
        )

        layout.rows.forEach { row ->
            row.keys.forEach { key ->
                val label = key.label

                if (label.isNotEmpty() && label !in skipLabels) {
                    KeyboardPrefs.clearKeyIndividualColors(
                        this,
                        if (label.length == 1 && label[0].isLetter()) {
                            label.lowercase()
                        } else {
                            label
                        }
                    )
                }
            }
        }
    }

    private fun openColorsDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_colors)

        val btnSpaceColor = dialog.findViewById<Button>(R.id.btnSpaceColor)
        val btnEnterColor = dialog.findViewById<Button>(R.id.btnEnterColor)
        val btnSideButtonsColor = dialog.findViewById<Button>(R.id.btnSideButtonsColor)
        val btnKeysColor = dialog.findViewById<Button>(R.id.btnKeysColor)
        val btnBackgroundColor = dialog.findViewById<Button>(R.id.btnBackgroundColor)

        val scrollView = dialog.findViewById<HorizontalScrollView>(R.id.colorsScrollView)
        val btnScrollLeft = dialog.findViewById<Button>(R.id.btnScrollLeft)
        val btnScrollRight = dialog.findViewById<Button>(R.id.btnScrollRight)

        btnScrollLeft.setOnClickListener {
            scrollView.smoothScrollBy(-dp(200), 0)
        }

        btnScrollRight.setOnClickListener {
            scrollView.smoothScrollBy(dp(200), 0)
        }

        btnSpaceColor.setOnClickListener { showSpaceColorDialog() }
        btnEnterColor.setOnClickListener { showEnterColorDialog() }
        btnSideButtonsColor.setOnClickListener { showSideButtonsColorDialog() }
        btnKeysColor.setOnClickListener { showKeysColorDialog() }
        btnBackgroundColor.setOnClickListener { showBackgroundColorDialog() }

        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.94f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun showSpaceColorDialog() {
        var linked = KeyboardPrefs.isSpaceLinked(this)
        var c1 = KeyboardPrefs.getSpace1Bg(this)
        var c2 = KeyboardPrefs.getSpace2Bg(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(4))
        }

        val cb = CheckBox(this).apply {
            text = "Both space keys same color"
            isChecked = linked
        }
        root.addView(cb)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }

        lateinit var b1: Button
        lateinit var b2: Button

        fun styleColorButton(btn: Button, color: Int) {
            btn.setBackgroundColor(color)
            btn.setTextColor(0xFFFFFFFF.toInt())
        }

        b1 = Button(this).apply {
            text = "Space 1"
            isAllCaps = false
            styleColorButton(this, c1)
            setOnClickListener {
                showAdvancedColorPicker("Space 1 color", c1) { picked ->
                    c1 = picked
                    styleColorButton(b1, c1)
                    if (linked) {
                        c2 = picked
                        styleColorButton(b2, c2)
                    }
                }
            }
        }

        b2 = Button(this).apply {
            text = "Space 2"
            isAllCaps = false
            styleColorButton(this, c2)
            setOnClickListener {
                showAdvancedColorPicker("Space 2 color", c2) { picked ->
                    c2 = picked
                    styleColorButton(b2, c2)
                    if (linked) {
                        c1 = picked
                        styleColorButton(b1, c1)
                    }
                }
            }
        }

        row.addView(
            b1,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
            }
        )
        row.addView(
            b2,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(6)
            }
        )

        root.addView(row)

        cb.setOnCheckedChangeListener { _, isChecked ->
            linked = isChecked
            if (linked) {
                c2 = c1
                styleColorButton(b2, c2)
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Space color")
            .setView(root)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                if (linked) c2 = c1
                KeyboardPrefs.setSpaceColors(this, c1, c2, linked)
                Toast.makeText(this, "Space colors saved", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showEnterColorDialog() {
        var bg = KeyboardPrefs.getEnterBg(this)
        var icon = KeyboardPrefs.getEnterIcon(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(4))
        }

        val bgBtn = Button(this).apply {
            text = "Background"
            isAllCaps = false
            setBackgroundColor(bg)
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener {
                showAdvancedColorPicker("Enter background", bg) { picked ->
                    bg = picked
                    setBackgroundColor(bg)
                }
            }
        }

        val iconBtn = Button(this).apply {
            text = "Icon color"
            isAllCaps = false
            setBackgroundColor(0xFF222222.toInt())
            setTextColor(icon)
            setOnClickListener {
                showAdvancedColorPicker("Enter icon color", icon) { picked ->
                    icon = picked
                    setTextColor(icon)
                }
            }
        }

        root.addView(bgBtn)
        root.addView(
            iconBtn,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
            }
        )

        AlertDialog.Builder(this)
            .setTitle("Enter color")
            .setView(root)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                KeyboardPrefs.setEnterColors(this, bg, icon)
                Toast.makeText(this, "Enter colors saved", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showSideButtonsColorDialog() {
        var useThemeBg = KeyboardPrefs.getSideButtonsUseThemeBg(this)
        var bg = KeyboardPrefs.getSideButtonsBg(this)
        var textColor = KeyboardPrefs.getSideButtonsTextColor(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(4))
        }

        val cb = CheckBox(this).apply {
            text = "Use theme background color"
            isChecked = useThemeBg
        }
        root.addView(cb)

        val bgBtn = Button(this).apply {
            text = "Background"
            isAllCaps = false
            setBackgroundColor(bg)
            setTextColor(0xFFFFFFFF.toInt())
            isEnabled = !useThemeBg
            setOnClickListener {
                showAdvancedColorPicker("Side buttons background", bg) { picked ->
                    bg = picked
                    setBackgroundColor(bg)
                }
            }
        }

        val textBtn = Button(this).apply {
            text = "Text color"
            isAllCaps = false
            setBackgroundColor(0xFF222222.toInt())
            setTextColor(textColor)
            isEnabled = !useThemeBg
            setOnClickListener {
                showAdvancedColorPicker("Side buttons text color", textColor) { picked ->
                    textColor = picked
                    setTextColor(textColor)
                }
            }
        }

        root.addView(bgBtn)
        root.addView(
            textBtn,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
            }
        )

        cb.setOnCheckedChangeListener { _, isChecked ->
            useThemeBg = isChecked
            bgBtn.isEnabled = !isChecked
            textBtn.isEnabled = !isChecked
            if (isChecked) {
                val themeBg = themeColor(R.attr.keyFill, getColor(R.color.key_fill_light))
                bg = themeBg
                bgBtn.setBackgroundColor(themeBg)
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Side buttons color")
            .setView(root)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                KeyboardPrefs.setSideButtonsColors(this, bg, textColor, useThemeBg)
                Toast.makeText(this, "Side buttons colors saved", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showKeysColorDialog() {
        val allSame = KeyboardPrefs.getKeysAllSameColor(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(4))
        }

        val cb = CheckBox(this).apply {
            text = "All keys same color"
            isChecked = allSame
        }
        root.addView(cb)

        // Container za "all same" gumbe
        val allSameContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (allSame) View.VISIBLE else View.GONE
        }

        val bgBtn = Button(this).apply {
            text = "Background"
            isAllCaps = false
            setBackgroundColor(KeyboardPrefs.getKeysBg(this@MainActivity))
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener {
                showAdvancedColorPicker("Keys background", KeyboardPrefs.getKeysBg(this@MainActivity)) { picked ->
                    val currentText = KeyboardPrefs.getKeysTextColor(this@MainActivity)
                    val currentAllSame = KeyboardPrefs.getKeysAllSameColor(this@MainActivity)
                    KeyboardPrefs.setKeysColors(this@MainActivity, picked, currentText, currentAllSame)
                    setBackgroundColor(picked)
                }
            }
        }

        val textBtn = Button(this).apply {
            text = "Text color"
            isAllCaps = false
            setBackgroundColor(0xFF222222.toInt())
            setTextColor(KeyboardPrefs.getKeysTextColor(this@MainActivity))
            setOnClickListener {
                showAdvancedColorPicker("Keys text color", KeyboardPrefs.getKeysTextColor(this@MainActivity)) { picked ->
                    val currentBg = KeyboardPrefs.getKeysBg(this@MainActivity)
                    val currentAllSame2 = KeyboardPrefs.getKeysAllSameColor(this@MainActivity)
                    KeyboardPrefs.setKeysColors(this@MainActivity, currentBg, picked, currentAllSame2)
                    setTextColor(picked)
                }
            }
        }

        allSameContainer.addView(bgBtn)
        allSameContainer.addView(textBtn)
        root.addView(allSameContainer)

        // Container za individualne tipke (skriven ako je allSame)
        val individualContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (allSame) View.GONE else View.VISIBLE
        }

        // Dohvati SVE tipke iz trenutnog layouta
        val rowCount = KeyboardPrefs.getRowCount(this)
        val layout = KeyboardPrefs.loadAlphabetLayoutWithGlobalBinds(this, rowCount)
        val allKeys = mutableSetOf<String>()

        layout.rows.forEach { row ->
            row.keys.forEach { key ->
                if (key.label.isNotBlank() && key.label !in setOf(" ", "↵", "⇧", "⌫", "😊")) {
                    allKeys.add(key.label)
                }
            }
        }

        // Grid s 3 stupca
        val grid = GridLayout(this).apply {
            columnCount = 3
        }

        allKeys.sorted().forEach { keyLabel ->
            val btn = Button(this).apply {
                text = keyLabel
                isAllCaps = false
                minHeight = dp(48)
                minWidth = dp(48)

                // Postavi trenutne boje
                val keyColors = KeyboardPrefs.getKeyIndividualColors(this@MainActivity, keyLabel)
                if (keyColors != null) {
                    setBackgroundColor(keyColors.first)
                    setTextColor(keyColors.second)
                }

                setOnClickListener {
                    showIndividualKeyColorPicker(keyLabel)
                }
            }

            val lp = GridLayout.LayoutParams().apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
            grid.addView(btn, lp)
        }

        individualContainer.addView(grid)
        root.addView(individualContainer)

        cb.setOnCheckedChangeListener { _, isChecked ->
            val currentBg2 = KeyboardPrefs.getKeysBg(this@MainActivity)
            val currentText2 = KeyboardPrefs.getKeysTextColor(this@MainActivity)
            KeyboardPrefs.setKeysColors(this@MainActivity, currentBg2, currentText2, isChecked)
            allSameContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
            individualContainer.visibility = if (isChecked) View.GONE else View.VISIBLE
        }

        // ScrollView koji sadrži sve
        val scrollView = ScrollView(this).apply {
            addView(root)
        }

        AlertDialog.Builder(this)
            .setTitle("Keys color")
            .setView(scrollView)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                Toast.makeText(this, "Keys colors saved", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showIndividualKeyColorPicker(keyLabel: String) {
        var bg = KeyboardPrefs.getKeyIndividualBg(this, keyLabel) ?: KeyboardPrefs.getKeysBg(this)
        var textColor = KeyboardPrefs.getKeyIndividualTextColor(this, keyLabel) ?: KeyboardPrefs.getKeysTextColor(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(4))
        }

        val preview = Button(this).apply {
            text = keyLabel
            isAllCaps = false
            setBackgroundColor(bg)
            setTextColor(textColor)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(64)
            )
        }
        root.addView(preview)

        val bgBtn = Button(this).apply {
            text = "Background"
            isAllCaps = false
            setBackgroundColor(bg)
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener {
                showAdvancedColorPicker("Key $keyLabel background", bg) { picked ->
                    bg = picked
                    preview.setBackgroundColor(picked)
                    setBackgroundColor(picked)
                }
            }
        }

        val textBtn = Button(this).apply {
            text = "Text color"
            isAllCaps = false
            setBackgroundColor(0xFF222222.toInt())
            setTextColor(textColor)
            setOnClickListener {
                showAdvancedColorPicker("Key $keyLabel text", textColor) { picked ->
                    textColor = picked
                    preview.setTextColor(picked)
                    setTextColor(picked)
                }
            }
        }

        root.addView(bgBtn)
        root.addView(textBtn)

        AlertDialog.Builder(this)
            .setTitle("Color for key: $keyLabel")
            .setView(root)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                KeyboardPrefs.setKeyIndividualColors(this, keyLabel, bg, textColor)
                Toast.makeText(this, "Color for $keyLabel saved", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showBackgroundColorDialog() {
        var useTheme = KeyboardPrefs.getBackgroundUseTheme(this)
        var bg = KeyboardPrefs.getBackgroundColor(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(4))
        }

        val cb = CheckBox(this).apply {
            text = "Use theme background"
            isChecked = useTheme
        }
        root.addView(cb)

        val bgBtn = Button(this).apply {
            text = "Background color"
            isAllCaps = false
            setBackgroundColor(bg)
            setTextColor(0xFFFFFFFF.toInt())
            isEnabled = !useTheme
            setOnClickListener {
                showAdvancedColorPicker("Background color", bg) { picked ->
                    bg = picked
                    setBackgroundColor(bg)
                }
            }
        }

        root.addView(bgBtn)

        cb.setOnCheckedChangeListener { _, isChecked ->
            useTheme = isChecked
            bgBtn.isEnabled = !isChecked
            if (isChecked) {
                val themeBg = themeColor(R.attr.keyboardBg, getColor(R.color.keyboard_bg_dark))
                bg = themeBg
                bgBtn.setBackgroundColor(themeBg)
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Background color")
            .setView(root)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                KeyboardPrefs.setBackgroundColor(this, bg, useTheme)
                Toast.makeText(this, "Background color saved", Toast.LENGTH_SHORT).show()
            }
            .show()
    }


    private fun activeAlphabetLayoutForRowCount(rowCount: Int): KeyboardConfig {
        return when (rowCount) {
            3 -> defaultThreeRowKeyboardLayoutQwertz
            4 -> defaultFourRowKeyboardLayout
            else -> defaultKeyboardLayout
        }
    }

    private fun activeNumericLayoutForRowCount(rowCount: Int): KeyboardConfig {
        return when (rowCount) {
            3 -> defaultThreeRowNumericLayout
            4 -> defaultFourRowNumericLayout
            else -> defaultNumericLayout
        }
    }

    private fun resetSideButtonsForRowCount(rowCount: Int) {
        val slots = when (rowCount) {
            4 -> listOf(
                EdgeSlot(0, EdgePos.Side.RIGHT, EdgeActionType.BACKSPACE),
                EdgeSlot(1, EdgePos.Side.LEFT, EdgeActionType.SHIFT),
                EdgeSlot(2, EdgePos.Side.RIGHT, EdgeActionType.NONE),
                EdgeSlot(3, EdgePos.Side.LEFT, EdgeActionType.NONE),
                EdgeSlot(4, EdgePos.Side.LEFT, EdgeActionType.NONE),
                EdgeSlot(5, EdgePos.Side.RIGHT, EdgeActionType.NONE)
            )

            3 -> listOf(
                EdgeSlot(0, EdgePos.Side.LEFT, EdgeActionType.NONE),
                EdgeSlot(1, EdgePos.Side.RIGHT, EdgeActionType.NONE),
                EdgeSlot(2, EdgePos.Side.LEFT, EdgeActionType.NONE),
                EdgeSlot(3, EdgePos.Side.RIGHT, EdgeActionType.NONE),
                EdgeSlot(4, EdgePos.Side.LEFT, EdgeActionType.NONE),
                EdgeSlot(5, EdgePos.Side.RIGHT, EdgeActionType.NONE)
            )

            else -> listOf(
                EdgeSlot(0, EdgePos.Side.LEFT, EdgeActionType.SHIFT),
                EdgeSlot(1, EdgePos.Side.RIGHT, EdgeActionType.BACKSPACE),
                EdgeSlot(2, EdgePos.Side.LEFT, EdgeActionType.NONE),
                EdgeSlot(3, EdgePos.Side.RIGHT, EdgeActionType.NONE),
                EdgeSlot(4, EdgePos.Side.LEFT, EdgeActionType.NONE),
                EdgeSlot(5, EdgePos.Side.RIGHT, EdgeActionType.NONE)
            )
        }

        EdgeSlotsStorage.save(this, slots)
    }

    private fun saveDefaultsForRowCount(rowCount: Int) {
        val currentRowCount = KeyboardPrefs.getRowCount(this)

        // Spremi trenutne layoute s globalnim bindovima (ako već postoje)
        if (currentRowCount != 0) {
            val currentAlphabet = KeyboardPrefs.loadAlphabetLayoutForRowCount(this, currentRowCount)
            val currentNumeric = KeyboardPrefs.loadNumericLayoutForRowCount(this, currentRowCount)
            GlobalLongPressStorage.extractAndSaveAlphabetBinds(this, currentAlphabet)
            GlobalLongPressStorage.extractAndSaveNumericBinds(this, currentNumeric)
        }

        // Postavi novi broj redova
        KeyboardPrefs.setRowCount(this, rowCount)

        // Učitaj default layoute za novi broj redova i apliciraj globalne bindove
        val alphabetWithBinds = KeyboardPrefs.loadAlphabetLayoutWithGlobalBinds(this, rowCount)
        val numericWithBinds = KeyboardPrefs.loadNumericLayoutWithGlobalBinds(this, rowCount)

        KeyboardPrefs.saveAlphabetLayoutForRowCount(this, rowCount, alphabetWithBinds)
        KeyboardPrefs.saveNumericLayoutForRowCount(this, rowCount, numericWithBinds)

        resetSideButtonsForRowCount(rowCount)
    }

    private fun setCheckedForRowCount(count: Int) {
        when (count) {
            3 -> radio3Rows.isChecked = true
            4 -> radio4Rows.isChecked = true
            else -> radio5Rows.isChecked = true
        }
    }

    private fun openLongPressEditorDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_longpress_editor)

        val btnTabAlphabet = dialog.findViewById<Button>(R.id.btnTabAlphabetLp)
        val btnTabNumeric = dialog.findViewById<Button>(R.id.btnTabNumericLp)
        val btnSave = dialog.findViewById<Button>(R.id.btnSaveLongPressEditor)

        val pageAlphabet = dialog.findViewById<LinearLayout>(R.id.pageAlphabetLp)
        val pageNumeric = dialog.findViewById<LinearLayout>(R.id.pageNumericLp)

        val currentRowCount = KeyboardPrefs.getRowCount(this)

        // Učitaj s globalnim bindovima
        val alphabetBinder = LongPressEditorBinder(
            context = this,
            initial = KeyboardPrefs.loadAlphabetLayoutWithGlobalBinds(this, currentRowCount),
            titleText = "Bind long press - alphabet"
        )

        val numericBinder = LongPressEditorBinder(
            context = this,
            initial = KeyboardPrefs.loadNumericLayoutWithGlobalBinds(this, currentRowCount),
            titleText = "Bind long press - numeric",
            lockedLabels = setOf("⇧", "⌫", "↵", "ABC", "abc", " ")
        )

        alphabetBinder.bindInto(pageAlphabet)
        numericBinder.bindInto(pageNumeric)

        fun showPage(page: Int) {
            pageAlphabet.visibility = if (page == 0) View.VISIBLE else View.GONE
            pageNumeric.visibility = if (page == 1) View.VISIBLE else View.GONE
        }

        btnTabAlphabet.setOnClickListener { showPage(0) }
        btnTabNumeric.setOnClickListener { showPage(1) }

        btnSave.setOnClickListener {
            // Spremi i izvuci u globalni storage
            KeyboardPrefs.saveAlphabetLayoutWithGlobalBinds(this, currentRowCount, alphabetBinder.getUpdatedConfig())
            KeyboardPrefs.saveNumericLayoutWithGlobalBinds(this, currentRowCount, numericBinder.getUpdatedConfig())
            Toast.makeText(this, "Long press saved ✅", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        showPage(0)
    }

    private fun setupAlphabetLongPressPage(
        container: LinearLayout,
        cfg: KeyboardConfig
    ) {
        container.removeAllViews()

        val title = TextView(this).apply {
            text = "Bind long press - alphabet"
            textSize = 18f
            setPadding(0, 0, 0, dp(10))
        }

        val btnOpen = Button(this).apply {
            text = "Odaberi tipku"
            isAllCaps = false
            setOnClickListener {
                LongPressKeyPickerDialog(
                    context = this@MainActivity,
                    cfg = cfg,
                    onSave = { updated ->
                        cfg.rows.clear()
                        cfg.rows.addAll(updated.rows)
                        cfg.specialLeft.clear()
                        cfg.specialLeft.addAll(updated.specialLeft)
                        cfg.specialRight.clear()
                        cfg.specialRight.addAll(updated.specialRight)
                    }
                ).show()
            }
        }

        container.addView(title)
        container.addView(btnOpen)
    }

    private fun setupNumericLongPressPage(
        container: LinearLayout,
        cfg: KeyboardConfig
    ) {
        container.removeAllViews()

        val title = TextView(this).apply {
            text = "Bind long press - numeric"
            textSize = 18f
            setPadding(0, 0, 0, dp(10))
        }

        val btnOpen = Button(this).apply {
            text = "Odaberi tipku"
            isAllCaps = false
            setOnClickListener {
                LongPressKeyPickerDialog(
                    context = this@MainActivity,
                    cfg = cfg,
                    onSave = { updated ->
                        cfg.rows.clear()
                        cfg.rows.addAll(updated.rows)
                        cfg.specialLeft.clear()
                        cfg.specialLeft.addAll(updated.specialLeft)
                        cfg.specialRight.clear()
                        cfg.specialRight.addAll(updated.specialRight)
                    }
                ).show()
            }
        }

        container.addView(title)
        container.addView(btnOpen)
    }

    private fun normalizeSlot(s: EdgeSlot): EdgeSlot {
        return when (s.type) {
            EdgeActionType.CHAR -> s.copy(value = s.value?.takeIf { it.isNotBlank() })
            else -> s.copy(value = null)
        }
    }

    private fun enforceNoDuplicates(
        slots: MutableList<EdgeSlot>,
        changedIndex: Int,
        chosen: EdgeSlot
    ) {
        val uniqueTypes = setOf(
            EdgeActionType.SHIFT,
            EdgeActionType.BACKSPACE,
            EdgeActionType.ENTER,
            EdgeActionType.SPACE
        )

        if (chosen.type in uniqueTypes) {
            for (i in slots.indices) {
                if (i != changedIndex && slots[i].type == chosen.type) {
                    slots[i] = slots[i].copy(type = EdgeActionType.NONE, value = null)
                }
            }
        }

        if (chosen.type == EdgeActionType.CHAR && !chosen.value.isNullOrBlank()) {
            for (i in slots.indices) {
                if (i != changedIndex &&
                    slots[i].type == EdgeActionType.CHAR &&
                    slots[i].value == chosen.value
                ) {
                    slots[i] = slots[i].copy(type = EdgeActionType.NONE, value = null)
                }
            }
        }
    }

    private fun showSpecialCharPicker(onPicked: (String) -> Unit) {
        val all = mutableListOf("∅")
        all.addAll(SpecialChars.ALL)

        AlertDialog.Builder(this)
            .setTitle("Pick a character")
            .setItems(all.toTypedArray()) { _, which ->
                val picked = all[which]
                onPicked(if (picked == "∅") "" else picked)
            }
            .show()
    }

    private fun applyNoDuplicates(
        slots: MutableList<EdgeSlot>,
        targetIndex: Int,
        newSlot: EdgeSlot
    ) {
        slots[targetIndex] = newSlot

        if (newSlot.type == EdgeActionType.NONE) return

        for (i in slots.indices) {
            if (i == targetIndex) continue

            val s = slots[i]

            val sameType = s.type == newSlot.type && newSlot.type != EdgeActionType.CHAR
            val sameChar = (
                    newSlot.type == EdgeActionType.CHAR &&
                            s.type == EdgeActionType.CHAR &&
                            !newSlot.value.isNullOrBlank() &&
                            s.value == newSlot.value
                    )

            if (sameType || sameChar) {
                slots[i] = s.copy(type = EdgeActionType.NONE, value = null)
            }
        }
    }

    private fun showEdgeTypePicker(
        oldSlot: EdgeSlot,
        onSelected: (EdgeSlot) -> Unit
    ) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(8))
        }

        val actions = listOf(
            EdgeActionType.SHIFT to "⇧ Shift",
            EdgeActionType.BACKSPACE to "⌫ Backspace",
            EdgeActionType.ENTER to "↵ Enter",
            EdgeActionType.SPACE to "␣ Space",
            EdgeActionType.EMOJI_PICKER to "😊 Emoji picker",
            EdgeActionType.NONE to "None"
        )

        val actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        actions.forEach { (type, label) ->
            val b = Button(this).apply {
                text = label
                isAllCaps = false
            }
            b.setOnClickListener {
                onSelected(oldSlot.copy(type = type, value = null))
            }
            actionsRow.addView(b)
        }

        root.addView(actionsRow)

        root.addView(TextView(this).apply {
            text = "Special chars"
            setPadding(0, dp(10), 0, dp(6))
        })

        val scroll = ScrollView(this)
        val grid = GridLayout(this).apply {
            columnCount = 6
        }

        SpecialChars.ALL.forEach { ch ->
            val b = Button(this).apply {
                text = ch
                isAllCaps = false
                minHeight = dp(44)
                minWidth = dp(44)
                setPadding(0, 0, 0, 0)
            }
            b.setOnClickListener {
                onSelected(oldSlot.copy(type = EdgeActionType.CHAR, value = ch))
            }

            val lp = GridLayout.LayoutParams().apply {
                width = 0
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
            grid.addView(b, lp)
        }

        scroll.addView(grid)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(320)
            )
        )

        AlertDialog.Builder(this)
            .setTitle("Choose side button")
            .setView(root)
            .setNegativeButton("Close", null)
            .show()
    }





    private fun openHorizontalCenterEditorDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val rowCount = KeyboardPrefs.getRowCount(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(12))
        }

        val title = TextView(this).apply {
            text = "Horizontal center keys ($rowCount rows)"
            textSize = 18f
            setPadding(0, 0, 0, dp(12))
        }

        val editorContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val btnSave = Button(this).apply {
            text = "Spremi"
            isAllCaps = false
        }

        root.addView(title)
        root.addView(editorContainer)
        root.addView(
            btnSave,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(12)
            }
        )

        dialog.setContentView(root)

        lateinit var horizontalBinder: LayoutEditorBinder

        horizontalBinder = LayoutEditorBinder(
            context = this,
            initial = KeyboardPrefs.loadHorizontalCenterLayoutForRowCount(this, rowCount),
            onSaved = { updated: KeyboardConfig ->
                KeyboardPrefs.saveHorizontalCenterLayoutForRowCount(this, rowCount, updated)
            },
            lockedLabels = emptySet(),
            onEmptyKeyClick = { key ->
                showSpecialCharPicker { picked ->
                    key.label = picked
                    key.longPressBindings.remove("__USER_EMPTY__")
                    horizontalBinder.bindInto(editorContainer)
                }
            },
            allowClearKeys = true
        )

        horizontalBinder.bindInto(editorContainer)

        btnSave.setOnClickListener {
            horizontalBinder.saveExternally()

            Toast.makeText(
                this,
                "Horizontal center saved za $rowCount rows ✅",
                Toast.LENGTH_SHORT
            ).show()

            dialog.dismiss()
        }

        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.94f).toInt(),
            (resources.displayMetrics.heightPixels * 0.82f).toInt()
        )
    }

    private fun openLayoutEditorDialog() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_layout_editor)

        val layoutEditorContainer = dialog.findViewById<FrameLayout>(R.id.layoutEditorContainer)
        val numericEditorContainer = dialog.findViewById<FrameLayout>(R.id.numericEditorContainer)

        val btnTabLayout = dialog.findViewById<Button>(R.id.btnTabLayout)
        val btnTabSideButtons = dialog.findViewById<Button>(R.id.btnTabSideButtons)
        val btnTabNumeric = dialog.findViewById<Button>(R.id.btnTabNumeric)
        val btnEditHorizontalCenter = dialog.findViewById<Button>(R.id.btnEditHorizontalCenter)

        val pageLayout = dialog.findViewById<LinearLayout>(R.id.pageLayout)
        val pageSideButtons = dialog.findViewById<LinearLayout>(R.id.pageSideButtons)
        val pageNumeric = dialog.findViewById<LinearLayout>(R.id.pageNumeric)

        val btnSwapSelected = dialog.findViewById<Button>(R.id.btnSwapSelected)
        val btnSaveEditor = dialog.findViewById<Button>(R.id.btnSaveEditor)

        val rowCount = KeyboardPrefs.getRowCount(this)

        val layoutBinder = LayoutEditorBinder(
            context = this,
            initial = KeyboardPrefs.loadAlphabetLayoutWithGlobalBinds(this, rowCount),
            onSaved = { updated: KeyboardConfig ->
                KeyboardPrefs.saveAlphabetLayoutWithGlobalBinds(this, rowCount, updated)
            }
        )

        val numericLocked = setOf(
            "⇧", "⌫", "↵", "ABC", "abc", " ",
            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9"
        )

        lateinit var numericBinder: LayoutEditorBinder

        numericBinder = LayoutEditorBinder(
            context = this,
            initial = KeyboardPrefs.loadNumericLayoutWithGlobalBinds(this, rowCount),
            onSaved = { updated: KeyboardConfig ->
                KeyboardPrefs.saveNumericLayoutWithGlobalBinds(this, rowCount, updated)
            },
            lockedLabels = numericLocked,
            onEmptyKeyClick = { key ->
                showSpecialCharPicker { picked ->
                    key.label = picked
                    key.longPressBindings.remove("__USER_EMPTY__")
                    numericBinder.bindInto(numericEditorContainer)
                }
            },
            allowClearKeys = true
        )

        layoutBinder.bindInto(layoutEditorContainer)
        numericBinder.bindInto(numericEditorContainer)

        val saveSideButtons = setupSideButtonsPage(pageSideButtons)
        var currentPage = 0

        fun showPage(page: Int) {
            currentPage = page
            pageLayout.visibility = if (page == 0) View.VISIBLE else View.GONE
            pageSideButtons.visibility = if (page == 1) View.VISIBLE else View.GONE
            pageNumeric.visibility = if (page == 2) View.VISIBLE else View.GONE
        }

        btnTabLayout.setOnClickListener { showPage(0) }
        btnTabSideButtons.setOnClickListener { showPage(1) }
        btnTabNumeric.setOnClickListener { showPage(2) }

        btnEditHorizontalCenter.setOnClickListener {
            openHorizontalCenterEditorDialog()
        }

        btnSwapSelected.setOnClickListener {
            val swapped = when (currentPage) {
                0 -> layoutBinder.swapSelectedExternally()
                2 -> numericBinder.swapSelectedExternally()
                else -> false
            }

            if (!swapped) {
                Toast.makeText(this, "Označi 2 tipke", Toast.LENGTH_SHORT).show()
            }
        }

        btnSaveEditor.setOnClickListener {
            when (currentPage) {
                0 -> {
                    layoutBinder.saveExternally()
                    Toast.makeText(this, "Layout saved ✅", Toast.LENGTH_SHORT).show()
                }

                1 -> {
                    saveSideButtons()
                    Toast.makeText(this, "Side buttons saved ✅", Toast.LENGTH_SHORT).show()
                }

                2 -> {
                    numericBinder.saveExternally()
                    Toast.makeText(this, "Numeric layout saved ✅", Toast.LENGTH_SHORT).show()
                }
            }
            dialog.dismiss()
        }

        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.94f).toInt(),
            (resources.displayMetrics.heightPixels * 0.9f).toInt()
        )

        showPage(0)
    }

    private fun showAdvancedColorPicker(
        title: String,
        initialColor: Int,
        onPicked: (Int) -> Unit
    ) {
        val r = Color.red(initialColor)
        val g = Color.green(initialColor)
        val b = Color.blue(initialColor)

        val hsv = FloatArray(3)
        Color.RGBToHSV(r, g, b, hsv)

        var currentHue = hsv[0]
        var currentSat = hsv[1]
        var currentVal = hsv[2]

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        // Preview bar (definiraj PRIJE updatePreview da bude dostupan)
        val previewBar = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
            ).apply {
                topMargin = dp(12)
            }
        }

        // Input fields
        val hexInput = android.widget.EditText(this).apply {
            textSize = 14f
            setPadding(dp(8), dp(8), dp(8), dp(8))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val hslInput = TextView(this).apply {
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val rgbInput = TextView(this).apply {
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        // DEFINIRAJ updatePreview OVDJE, prije colorWheel
        lateinit var updatePreviewRef: () -> Unit

        val updatePreview: () -> Unit = {
            val color = Color.HSVToColor(floatArrayOf(currentHue, currentSat, currentVal))
            previewBar.setBackgroundColor(color)

            val hex = String.format("#%06X", color and 0xFFFFFF)
            hexInput.setText(hex.substring(1))

            val rr = Color.red(color)
            val gg = Color.green(color)
            val bb = Color.blue(color)
            rgbInput.text = "rgb($rr $gg $bb)"

            val hslColor = colorToHSL(color)
            hslInput.text = "hsl(${hslColor[0].toInt()}deg ${(hslColor[1] * 100).toInt()}% ${(hslColor[2] * 100).toInt()}%)"

            onPicked(color)
        }

        // Sada možeš koristiti updatePreview u colorWheel
        val colorWheel = ColorWheelView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(240)
            )
            setHueSaturation(currentHue, currentSat)
            setOnColorChangedListener { h, s ->
                currentHue = h
                currentSat = s
                updatePreview()
            }
        }

        // Brightness slider
        val brightnessLabel = TextView(this).apply {
            text = "Brightness"
            textSize = 14f
            setPadding(0, dp(12), 0, dp(4))
        }

        val brightnessSeek = SeekBar(this).apply {
            max = 100
            progress = (currentVal * 100).toInt()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        brightnessSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentVal = progress / 100f
                updatePreview()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Hex input listener
        hexInput.setOnEditorActionListener { _, _, _ ->
            val hexText = hexInput.text.toString()
            try {
                val parsedColor = Color.parseColor("#$hexText")
                Color.RGBToHSV(Color.red(parsedColor), Color.green(parsedColor), Color.blue(parsedColor), hsv)
                currentHue = hsv[0]
                currentSat = hsv[1]
                currentVal = hsv[2]
                colorWheel.setHueSaturation(currentHue, currentSat)
                brightnessSeek.progress = (currentVal * 100).toInt()
                updatePreview()
            } catch (_: IllegalArgumentException) {}
            true
        }

        // Build UI
        root.addView(colorWheel)
        root.addView(brightnessLabel)
        root.addView(brightnessSeek)
        root.addView(previewBar)

        // Input fields
        val inputsRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, 0)
        }

        // Hex row
        val hexRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        hexRow.addView(TextView(this).apply { text = "#"; textSize = 14f })
        hexRow.addView(hexInput)
        hexRow.addView(Button(this).apply {
            text = "📋"
            isAllCaps = false
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = ClipData.newPlainText("hex", hexInput.text.toString())
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
            }
        })
        inputsRow.addView(hexRow)

        // HSL row
        val hslRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, 0)
        }
        hslRow.addView(hslInput)
        hslRow.addView(Button(this).apply {
            text = "📋"
            isAllCaps = false
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = ClipData.newPlainText("hsl", hslInput.text.toString())
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
            }
        })
        inputsRow.addView(hslRow)

        // RGB row
        val rgbRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, 0)
        }
        rgbRow.addView(rgbInput)
        rgbRow.addView(Button(this).apply {
            text = "📋"
            isAllCaps = false
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = ClipData.newPlainText("rgb", rgbInput.text.toString())
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
            }
        })
        inputsRow.addView(rgbRow)

        root.addView(inputsRow)

        // Initial update
        updatePreview()

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(ScrollView(this).apply { addView(root) })
            .setPositiveButton("Done") { _, _ ->
                val finalColor = Color.HSVToColor(floatArrayOf(currentHue, currentSat, currentVal))
                onPicked(finalColor)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Helper: Convert RGB to HSL
    private fun colorToHSL(color: Int): FloatArray {
        val r = Color.red(color) / 255f
        val g = Color.green(color) / 255f
        val b = Color.blue(color) / 255f

        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val l = (max + min) / 2f

        val h: Float
        val s: Float

        if (max == min) {
            h = 0f
            s = 0f
        } else {
            val d = max - min
            s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
            h = when (max) {
                r -> ((g - b) / d + (if (g < b) 6 else 0)) / 6f
                g -> ((b - r) / d + 2) / 6f
                else -> ((r - g) / d + 4) / 6f
            }
        }

        return floatArrayOf(h * 360f, s, l)
    }

    private fun simpleSeek(onChange: () -> Unit) =
        object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean
            ) = onChange()

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }

    private fun setupSideButtonsPage(container: LinearLayout): () -> Unit {
        container.removeAllViews()

        val selectedRowCount = KeyboardPrefs.getRowCount(this)

        val title = TextView(this).apply {
            text = "Set side buttons"
            textSize = 18f
            setPadding(0, 0, 0, dp(10))
        }

        val hint = TextView(this).apply {
            text = when (selectedRowCount) {
                4 -> "4-row: row1 right, row2 left, row3 right, row4 left"
                3 -> "3-row preview mode"
                else -> "5-row: 3 lijeva + 3 desna side buttona"
            }
            textSize = 13f
            alpha = 0.75f
            setPadding(0, 0, 0, dp(10))
        }

        val grid = GridLayout(this).apply {
            rowCount = when (selectedRowCount) {
                3 -> 3
                4 -> 4
                else -> 3
            }
            columnCount = 2
        }

        val slotCount = when (selectedRowCount) {
            3 -> 3
            4 -> 4
            else -> 6
        }

        val slots = EdgeSlotsStorage.load(this).toMutableList()

        while (slots.size < slotCount) {
            val i = slots.size
            slots.add(
                EdgeSlot(
                    index = i,
                    side = fixedSideForRowCount(selectedRowCount, i),
                    type = EdgeActionType.NONE
                )
            )
        }

        while (slots.size > slotCount) {
            slots.removeAt(slots.lastIndex)
        }

        fun fixSlotPosition(i: Int, s: EdgeSlot): EdgeSlot {
            return s.copy(
                index = i,
                side = fixedSideForRowCount(selectedRowCount, i)
            )
        }

        fun startDragCompat(v: View, fromIndex: Int) {
            val data = ClipData.newPlainText("fromIndex", fromIndex.toString())
            val shadow = View.DragShadowBuilder(v)

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                v.startDragAndDrop(data, shadow, null, 0)
            } else {
                @Suppress("DEPRECATION")
                v.startDrag(data, shadow, null, 0)
            }
        }

        fun parseFromIndex(e: DragEvent): Int? {
            val item = e.clipData?.getItemAt(0)?.text?.toString() ?: return null
            return item.toIntOrNull()
        }

        fun rebuild() {
            grid.removeAllViews()

            slots.forEachIndexed { index, slot ->
                val btn = Button(this).apply {
                    text = when (selectedRowCount) {
                        4 -> {
                            val rowLabel = when (index) {
                                0 -> "R1"
                                1 -> "R2"
                                2 -> "R3"
                                else -> "R4"
                            }
                            "$rowLabel • ${slot.label.ifBlank { "None" }}"
                        }

                        else -> slot.label.ifBlank { "None" }
                    }

                    isAllCaps = false
                    tag = index
                    setPadding(dp(8), dp(14), dp(8), dp(14))

                    setOnClickListener {
                        showEdgeTypePicker(slot) { newSlot ->
                            val normalized = fixSlotPosition(index, normalizeSlot(newSlot))
                            enforceNoDuplicates(slots, index, normalized)
                            slots[index] = normalized
                            rebuild()
                        }
                    }

                    setOnLongClickListener { v ->
                        startDragCompat(v, index)
                        true
                    }

                    setOnDragListener { v, e ->
                        when (e.action) {
                            DragEvent.ACTION_DRAG_STARTED -> {
                                e.clipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true
                            }

                            DragEvent.ACTION_DRAG_ENTERED -> {
                                v.alpha = 0.65f
                                true
                            }

                            DragEvent.ACTION_DRAG_EXITED -> {
                                v.alpha = 1f
                                true
                            }

                            DragEvent.ACTION_DROP -> {
                                v.alpha = 1f

                                val from = parseFromIndex(e) ?: return@setOnDragListener true
                                val to = (v.tag as? Int) ?: return@setOnDragListener true
                                if (from == to) return@setOnDragListener true

                                val tmp = slots[from]
                                slots[from] = slots[to]
                                slots[to] = tmp

                                slots[from] = fixSlotPosition(from, slots[from])
                                slots[to] = fixSlotPosition(to, slots[to])

                                rebuild()
                                true
                            }

                            DragEvent.ACTION_DRAG_ENDED -> {
                                v.alpha = 1f
                                true
                            }

                            else -> true
                        }
                    }
                }

                val col = if (fixedSideForRowCount(selectedRowCount, index) == EdgePos.Side.LEFT) {
                    0
                } else {
                    1
                }

                val row = when (selectedRowCount) {
                    4 -> index
                    else -> index / 2
                }

                val lp = GridLayout.LayoutParams().apply {
                    width = 0
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(col, 1f)
                    rowSpec = GridLayout.spec(row)
                    setMargins(dp(6), dp(6), dp(6), dp(6))
                }

                grid.addView(btn, lp)
            }
        }

        rebuild()

        container.addView(title)
        container.addView(hint)
        container.addView(grid)

        return {
            EdgeSlotsStorage.save(this, slots)
        }
    }

    private fun fixedSideForRowCount(rowCount: Int, index: Int): EdgePos.Side {
        return when (rowCount) {
            4 -> when (index) {
                0 -> EdgePos.Side.RIGHT
                1 -> EdgePos.Side.LEFT
                2 -> EdgePos.Side.RIGHT
                else -> EdgePos.Side.LEFT
            }

            else -> if (index % 2 == 0) EdgePos.Side.LEFT else EdgePos.Side.RIGHT
        }
    }

    private fun applyShape(shape: KeyShape) {
        preview.shape = shape
        KeyboardPrefs.setShape(this, shape)
        setCheckedForShape(shape)
    }

    private fun setCheckedForShape(shape: KeyShape) {
        when (shape) {
            KeyShape.HEX,
            KeyShape.HEX_TALL,
            KeyShape.HEX_HALF_LEFT,
            KeyShape.HEX_HALF_RIGHT -> hex.isChecked = true
            KeyShape.TRIANGLE -> tri.isChecked = true
            KeyShape.CIRCLE -> circle.isChecked = true
            KeyShape.CUBE -> cube.isChecked = true
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun themeColor(attr: Int, fallback: Int): Int {
        val tv = android.util.TypedValue()
        return if (
            theme.resolveAttribute(attr, tv, true) &&
            tv.type in android.util.TypedValue.TYPE_FIRST_COLOR_INT..
            android.util.TypedValue.TYPE_LAST_COLOR_INT
        ) {
            tv.data
        } else {
            fallback
        }
    }
}