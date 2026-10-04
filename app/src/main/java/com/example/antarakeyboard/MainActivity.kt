package com.example.antarakeyboard

import android.app.Dialog
import android.content.ClipData
import android.content.ClipDescription
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.example.antarakeyboard.data.EdgePos
import com.example.antarakeyboard.data.EdgeSlotsStorage
import com.example.antarakeyboard.extensions.dp
import com.example.antarakeyboard.data.GlobalLongPressStorage
import com.example.antarakeyboard.data.KeyboardPrefs
import com.example.antarakeyboard.model.EdgeActionType
import com.example.antarakeyboard.model.EdgeSlot
import com.example.antarakeyboard.model.KeyShape
import com.example.antarakeyboard.model.KeyboardConfig
import com.example.antarakeyboard.ui.ColorWheelView
import com.example.antarakeyboard.ui.LayoutEditorBinder
import com.example.antarakeyboard.ui.LongPressEditorBinder
import com.example.antarakeyboard.ui.ShapePreviewView
import com.example.antarakeyboard.ui.defaultHorizontalCenterLayout
import android.graphics.Color
import android.content.Context
import com.example.antarakeyboard.R
import com.example.antarakeyboard.data.EmojiPickerStorage
import com.example.antarakeyboard.data.SavedLayoutStorage

class MainActivity : AppCompatActivity() {

    private lateinit var preview: ShapePreviewView
    private lateinit var spinnerKeyShape: Spinner
    private lateinit var spinnerRowCount: Spinner
    private lateinit var spinnerVibration: Spinner
    private lateinit var spinnerTheme: Spinner
    private lateinit var btnThemeSettings: Button
    private lateinit var bindLPButton: Button
    private lateinit var spinnerReset: Spinner

    // Autosaving popups (Set layout, Bind long press): section whose "saved" toast is pending
    private var autosaveDirtySection: String? = null

    // Shape options for dropdown
    private val shapeOptions = listOf(
        KeyShape.HEX to "Hexagon",
        KeyShape.TRIANGLE to "Triangle",
        KeyShape.CIRCLE to "Circle",
        KeyShape.CUBE to "Cube"
    )

    // Row count options for dropdown
    private val rowCountOptions = listOf(3, 4, 5)

    // Vibration options
    private val vibrationOptions = listOf("On", "Off")

    // Theme options (Custom added dynamically if user has custom colors)
    private var themeOptions = listOf<String>()
    private var currentThemePosition = -1

    companion object {
        private const val THEME_LIGHT = "Light"
        private const val THEME_DARK = "Dark"
        private const val THEME_CUSTOM = "Custom"
        private const val THEME_RGB_SMOOTH = "RGB Smooth"
        private const val THEME_RGB_WILD = "RGB Wild"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("theme_prefs", MODE_PRIVATE)
        val isDark = prefs.getBoolean("dark_mode", true)

        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.activity_main)

        // Handle system bar insets (status bar + navigation bar)
        val mainScroll = findViewById<ScrollView>(R.id.mainScroll)
        val mainRoot = findViewById<LinearLayout>(R.id.mainRoot)

        ViewCompat.setOnApplyWindowInsetsListener(mainScroll) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            mainRoot.setPadding(
                16.dp(this),
                16.dp(this) + systemBars.top,
                16.dp(this),
                16.dp(this) + systemBars.bottom
            )
            insets
        }

        val btnEnableKeyboard: Button = findViewById(R.id.btnEnableKeyboard)
        val btnChooseKeyboard: Button = findViewById(R.id.btnChooseKeyboard)
        val btnSetLayout: Button = findViewById(R.id.btnSetLayout)

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

        // Setup Colors Spinner
        val spinnerColors: Spinner = findViewById(R.id.spinnerColors)
        val colorOptions = listOf(
            "-- Select color to edit --",
            "Space",
            "Enter",
            "Side buttons",
            "Keys",
            "Background",
            "Edit Theme Defaults"
        )
        val colorsAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, colorOptions).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerColors.adapter = colorsAdapter

        spinnerColors.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                when (position) {
                    1 -> { showSpaceColorDialog(); spinnerColors.setSelection(0) }
                    2 -> { showEnterColorDialog(); spinnerColors.setSelection(0) }
                    3 -> { showSideButtonsColorDialog(); spinnerColors.setSelection(0) }
                    4 -> { showKeysColorDialog(); spinnerColors.setSelection(0) }
                    5 -> { showBackgroundColorDialog(); spinnerColors.setSelection(0) }
                    6 -> { showThemeDefaultsDialog(); spinnerColors.setSelection(0) }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        preview = findViewById(R.id.preview)
        bindLPButton = findViewById(R.id.bindLPButton)
        spinnerReset = findViewById(R.id.spinnerReset)

        // Setup Key Shape Spinner
        spinnerKeyShape = findViewById(R.id.spinnerKeyShape)
        val shapeAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            shapeOptions.map { it.second }
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerKeyShape.adapter = shapeAdapter

        val savedShape = KeyboardPrefs.getShape(this)
        val savedShapeIndex = shapeOptions.indexOfFirst { it.first == savedShape }.coerceAtLeast(0)
        spinnerKeyShape.setSelection(savedShapeIndex)

        preview.visibility = View.VISIBLE
        preview.shape = savedShape
        preview.invalidate()

        spinnerKeyShape.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedShape = shapeOptions[position].first
                applyShape(selectedShape)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Setup Row Count Spinner
        spinnerRowCount = findViewById(R.id.spinnerRowCount)
        val rowCountAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            rowCountOptions.map { "$it rows" }
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerRowCount.adapter = rowCountAdapter

        val savedRowCount = KeyboardPrefs.getRowCount(this)
        val savedRowIndex = rowCountOptions.indexOf(savedRowCount).coerceAtLeast(0)
        spinnerRowCount.setSelection(savedRowIndex)

        spinnerRowCount.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val rowCount = rowCountOptions[position]
                // Ignore the initial callback and programmatic sync (e.g. after restore)
                if (rowCount == KeyboardPrefs.getRowCount(this@MainActivity)) return
                saveDefaultsForRowCount(rowCount)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Setup Vibration Spinner
        spinnerVibration = findViewById(R.id.spinnerVibration)
        val vibrationAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            vibrationOptions
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerVibration.adapter = vibrationAdapter

        val vibrationEnabled = KeyboardPrefs.isVibrationEnabled(this)
        spinnerVibration.setSelection(if (vibrationEnabled) 0 else 1)

        spinnerVibration.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val enabled = position == 0
                KeyboardPrefs.setVibrationEnabled(this@MainActivity, enabled)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Setup Theme Spinner
        spinnerTheme = findViewById(R.id.spinnerTheme)
        btnThemeSettings = findViewById(R.id.btnThemeSettings)
        btnThemeSettings.setOnClickListener {
            when (themeOptions.getOrNull(currentThemePosition)) {
                THEME_RGB_SMOOTH -> showRgbSmoothDialog()
                THEME_RGB_WILD -> showRgbWildDialog()
                THEME_CUSTOM -> showSavedLayoutsPopup { applied -> if (applied) selectCustomTheme() }
            }
        }
        setupThemeSpinner()

        bindLPButton.setOnClickListener {
            openLongPressEditorDialog()
        }

        // Setup Reset Spinner
        val resetOptions = listOf("Reset…", "Reset colors", "Reset layout", "Reset all")
        spinnerReset.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, resetOptions).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerReset.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == 0) return
                performReset(colors = position != 2, layout = position != 1)
                spinnerReset.setSelection(0)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    /**
     * Reset colors and/or key layout. Long-press bindings and key shape are always preserved.
     * The current state is saved as a backup in Saved layouts first.
     */
    private fun performReset(colors: Boolean, layout: Boolean) {
        val currentRowCount = KeyboardPrefs.getRowCount(this)

        // Spremi trenutni layout (i boje) kao backup prije reseta
        val timestamp = SavedLayoutStorage.formatTimestamp(System.currentTimeMillis())
        SavedLayoutStorage.saveCurrentLayout(this, "Backup $timestamp")

        if (layout) {
            // Resetiraj horizontal center layout
            KeyboardPrefs.clearHorizontalCenterLayoutForRowCount(this, currentRowCount)
            KeyboardPrefs.saveHorizontalCenterLayoutForRowCount(this, currentRowCount, defaultHorizontalCenterLayout)

            // Resetiraj glavne layoute na default, ALI zadrži globalne bindove
            KeyboardPrefs.clearAlphabetLayoutForRowCount(this, currentRowCount)
            KeyboardPrefs.clearNumericLayoutForRowCount(this, currentRowCount)

            val alphabetWithBinds = KeyboardPrefs.loadAlphabetLayoutWithGlobalBinds(this, currentRowCount)
            val numericWithBinds = KeyboardPrefs.loadNumericLayoutWithGlobalBinds(this, currentRowCount)

            KeyboardPrefs.saveAlphabetLayoutForRowCount(this, currentRowCount, alphabetWithBinds)
            KeyboardPrefs.saveNumericLayoutForRowCount(this, currentRowCount, numericWithBinds)

            // Resetiraj side buttons za trenutni broj redova
            resetSideButtonsForRowCount(currentRowCount)
        }

        if (colors) {
            val isDarkTheme = getSharedPreferences("theme_prefs", MODE_PRIVATE)
                .getBoolean("dark_mode", true)

            // Spremi trenutne boje kao custom temu prije reseta
            saveCurrentColorsAsCustomTheme()

            // Resetiraj SVE boje na default teme
            resetAllColorsToThemeDefault(isDarkTheme)

            // Očisti custom theme flag da tipkovnica koristi default boje, i ugasi RGB
            getSharedPreferences("theme_prefs", MODE_PRIVATE).edit()
                .putBoolean("use_custom_theme", false)
                .apply()
            KeyboardPrefs.setRgbSmoothEnabled(this, false)
            KeyboardPrefs.setRgbWildEnabled(this, false)

            // Refresh theme spinner (now has Custom option available)
            setupThemeSpinner()
        }

        // Signaliziraj tipkovnici da se recreate
        sendBroadcast(Intent("com.example.antarakeyboard.RECREATE_KEYBOARD"))

        val what = when {
            colors && layout -> "Layout and colors"
            colors -> "Colors"
            else -> "Layout"
        }
        Toast.makeText(this, "$what reset. Previous saved as backup.", Toast.LENGTH_SHORT).show()
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

        // 1. Space colors — malo svjetlije od ostalih tipki
        val spaceFill = if (isDark) 0xFF4A4A4A.toInt() else 0xFFD3CAC8.toInt()
        KeyboardPrefs.setSpaceColors(this, spaceFill, spaceFill, true)

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

        // 4. Keys colors - use theme colors after reset
        KeyboardPrefs.setKeysColors(this, keyFill, keyText, true, useTheme = true)

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

    private fun showSpaceColorDialog() {
        var linked = KeyboardPrefs.isSpaceLinked(this)
        var c1 = KeyboardPrefs.getSpace1Bg(this)
        var c2 = KeyboardPrefs.getSpace2Bg(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(this), 12.dp(this), 16.dp(this), 4.dp(this))
        }

        val cb = CheckBox(this).apply {
            text = "Both space keys same color"
            isChecked = linked
        }
        root.addView(cb)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8.dp(this), 0, 0)
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
                marginEnd = 6.dp(this@MainActivity)
            }
        )
        row.addView(
            b2,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 6.dp(this@MainActivity)
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
            setPadding(16.dp(this), 12.dp(this), 16.dp(this), 4.dp(this))
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
                topMargin = 8.dp(this@MainActivity)
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
            setPadding(16.dp(this), 12.dp(this), 16.dp(this), 4.dp(this))
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
                topMargin = 8.dp(this@MainActivity)
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
            setPadding(16.dp(this), 12.dp(this), 16.dp(this), 4.dp(this))
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
                    KeyboardPrefs.setKeysColors(this@MainActivity, picked, currentText, currentAllSame, useTheme = false)
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
                    KeyboardPrefs.setKeysColors(this@MainActivity, currentBg, picked, currentAllSame2, useTheme = false)
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
                minHeight = 48.dp(this)
                minWidth = 48.dp(this)

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
                setMargins(4.dp(this@MainActivity), 4.dp(this@MainActivity), 4.dp(this@MainActivity), 4.dp(this@MainActivity))
            }
            grid.addView(btn, lp)
        }

        individualContainer.addView(grid)
        root.addView(individualContainer)

        cb.setOnCheckedChangeListener { _, isChecked ->
            val currentBg2 = KeyboardPrefs.getKeysBg(this@MainActivity)
            val currentText2 = KeyboardPrefs.getKeysTextColor(this@MainActivity)
            KeyboardPrefs.setKeysColors(this@MainActivity, currentBg2, currentText2, isChecked, useTheme = false)
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
            setPadding(16.dp(this), 12.dp(this), 16.dp(this), 4.dp(this))
        }

        val preview = Button(this).apply {
            text = keyLabel
            isAllCaps = false
            setBackgroundColor(bg)
            setTextColor(textColor)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                64.dp(this)
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
            setPadding(16.dp(this), 12.dp(this), 16.dp(this), 4.dp(this))
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

    private fun showThemeDefaultsDialog() {
        val ctx = this

        // Load current theme defaults
        var lightKeyFill = KeyboardPrefs.getThemeLightKeyFill(ctx)
        var lightKeyText = KeyboardPrefs.getThemeLightKeyText(ctx)
        var lightSpaceFill = KeyboardPrefs.getThemeLightSpaceFill(ctx)
        var lightKeyboardBg = KeyboardPrefs.getThemeLightKeyboardBg(ctx)

        var darkKeyFill = KeyboardPrefs.getThemeDarkKeyFill(ctx)
        var darkKeyText = KeyboardPrefs.getThemeDarkKeyText(ctx)
        var darkSpaceFill = KeyboardPrefs.getThemeDarkSpaceFill(ctx)
        var darkKeyboardBg = KeyboardPrefs.getThemeDarkKeyboardBg(ctx)

        val scroll = ScrollView(ctx)
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(ctx), 12.dp(ctx), 16.dp(ctx), 12.dp(ctx))
        }
        scroll.addView(root)

        fun createColorRow(label: String, color: Int, onColorPicked: (Int) -> Unit): View {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, 4.dp(ctx), 0, 4.dp(ctx))
            }

            val swatchSize = 36.dp(ctx)
            val swatchMargin = 12.dp(ctx)
            val swatch = View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(swatchSize, swatchSize).apply {
                    marginEnd = swatchMargin
                }
                setBackgroundColor(color)
            }

            val textView = TextView(ctx).apply {
                text = label
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            row.addView(swatch)
            row.addView(textView)

            row.setOnClickListener {
                val currentColor = (swatch.background as? android.graphics.drawable.ColorDrawable)?.color ?: color
                showAdvancedColorPicker(label, currentColor) { picked ->
                    swatch.setBackgroundColor(picked)
                    onColorPicked(picked)
                }
            }

            return row
        }

        // Light mode section
        val lightHeader = TextView(ctx).apply {
            text = "Light Mode Defaults"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 8.dp(ctx))
        }
        root.addView(lightHeader)

        root.addView(createColorRow("Key Fill", lightKeyFill) { lightKeyFill = it })
        root.addView(createColorRow("Key Text", lightKeyText) { lightKeyText = it })
        root.addView(createColorRow("Space Fill", lightSpaceFill) { lightSpaceFill = it })
        root.addView(createColorRow("Background", lightKeyboardBg) { lightKeyboardBg = it })

        // Divider
        val dividerMargin = 12.dp(ctx)
        val divider = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                1.dp(ctx)
            ).apply {
                topMargin = dividerMargin
                bottomMargin = dividerMargin
            }
            setBackgroundColor(0xFF888888.toInt())
        }
        root.addView(divider)

        // Dark mode section
        val darkHeader = TextView(ctx).apply {
            text = "Dark Mode Defaults"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 8.dp(ctx))
        }
        root.addView(darkHeader)

        root.addView(createColorRow("Key Fill", darkKeyFill) { darkKeyFill = it })
        root.addView(createColorRow("Key Text", darkKeyText) { darkKeyText = it })
        root.addView(createColorRow("Space Fill", darkSpaceFill) { darkSpaceFill = it })
        root.addView(createColorRow("Background", darkKeyboardBg) { darkKeyboardBg = it })

        // Reset button
        val resetBtnMargin = 16.dp(ctx)
        val resetBtn = Button(ctx).apply {
            text = "Reset to Factory"
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = resetBtnMargin
            }
        }
        root.addView(resetBtn)

        val dialog = AlertDialog.Builder(ctx)
            .setTitle("Edit Theme Defaults")
            .setView(scroll)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                KeyboardPrefs.setThemeLightDefaults(ctx, lightKeyFill, lightKeyText, lightSpaceFill, lightKeyboardBg)
                KeyboardPrefs.setThemeDarkDefaults(ctx, darkKeyFill, darkKeyText, darkSpaceFill, darkKeyboardBg)
                Toast.makeText(ctx, "Theme defaults saved", Toast.LENGTH_SHORT).show()
            }
            .create()

        resetBtn.setOnClickListener {
            AlertDialog.Builder(ctx)
                .setTitle("Reset to Factory")
                .setMessage("Reset all theme colors to factory defaults?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Reset") { _, _ ->
                    KeyboardPrefs.resetThemeDefaultsToFactory(ctx)
                    Toast.makeText(ctx, "Theme defaults reset to factory", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
                .show()
        }

        dialog.show()
    }

    private fun showRgbSmoothDialog() {
        val ctx = this

        var speed = KeyboardPrefs.getRgbSmoothSpeed(ctx)
        var saturation = KeyboardPrefs.getRgbSmoothSaturation(ctx)
        var brightness = KeyboardPrefs.getRgbSmoothBrightness(ctx)

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp(ctx), 16.dp(ctx), 24.dp(ctx), 8.dp(ctx))
        }

        // Preview bar with radial rainbow gradient
        val previewBar = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                48.dp(ctx)
            ).apply {
                topMargin = 16.dp(ctx)
                bottomMargin = 8.dp(ctx)
            }
        }
        root.addView(previewBar)

        // Speed label
        val speedLabel = TextView(ctx).apply {
            text = "Speed: ${speed / 1000}s per cycle"
            textSize = 14f
            setPadding(0, 12.dp(ctx), 0, 4.dp(ctx))
        }
        root.addView(speedLabel)

        val speedSeekBar = SeekBar(ctx).apply {
            max = KeyboardPrefs.RGB_SPEED_MAX - KeyboardPrefs.RGB_SPEED_MIN
            progress = speed - KeyboardPrefs.RGB_SPEED_MIN
        }
        root.addView(speedSeekBar)

        val speedHint = TextView(ctx).apply {
            text = "Fast ← → Slow"
            textSize = 12f
            gravity = android.view.Gravity.CENTER
            setTextColor(0xFF888888.toInt())
        }
        root.addView(speedHint)

        // Saturation
        val satLabel = TextView(ctx).apply {
            text = "Saturation: ${(saturation * 100).toInt()}%"
            textSize = 14f
            setPadding(0, 16.dp(ctx), 0, 4.dp(ctx))
        }
        root.addView(satLabel)

        val satSeekBar = SeekBar(ctx).apply {
            max = 100
            progress = (saturation * 100).toInt()
        }
        root.addView(satSeekBar)

        // Brightness
        val brightLabel = TextView(ctx).apply {
            text = "Brightness: ${(brightness * 100).toInt()}%"
            textSize = 14f
            setPadding(0, 16.dp(ctx), 0, 4.dp(ctx))
        }
        root.addView(brightLabel)

        val brightSeekBar = SeekBar(ctx).apply {
            max = 100
            progress = (brightness * 100).toInt()
        }
        root.addView(brightSeekBar)

        // Rainbow color function - 6 segments × 256 steps = 1536 total
        fun rainbowColor(x: Int): Int {
            val step = ((x % 1536) + 1536) % 1536
            val segment = step / 256
            val c = step % 256
            return when (segment) {
                0 -> Color.rgb(255, c, 0)           // Red to Yellow
                1 -> Color.rgb(255 - c, 255, 0)     // Yellow to Green
                2 -> Color.rgb(0, 255, c)           // Green to Cyan
                3 -> Color.rgb(0, 255 - c, 255)     // Cyan to Blue
                4 -> Color.rgb(c, 0, 255)           // Blue to Magenta
                5 -> Color.rgb(255, 0, 255 - c)     // Magenta to Red
                else -> Color.rgb(255, 0, 0)
            }
        }

        fun adjustBrightness(color: Int, bright: Float): Int {
            val r = ((color shr 16) and 0xFF) * bright
            val g = ((color shr 8) and 0xFF) * bright
            val b = (color and 0xFF) * bright
            return Color.rgb(r.toInt(), g.toInt(), b.toInt())
        }

        // Create multi-center radial rainbow drawable for preview (3 centers)
        fun createRadialPreview(startStep: Int): android.graphics.drawable.Drawable {
            val numColors = 7

            fun buildColors(phaseOffset: Int): IntArray {
                val colors = IntArray(numColors)
                for (i in 0 until numColors) {
                    val colorStep = startStep + phaseOffset + (i * 1536 / numColors)
                    val color = rainbowColor(colorStep)
                    colors[i] = adjustBrightness(color, brightness)
                }
                return colors
            }

            return object : android.graphics.drawable.Drawable() {
                private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

                override fun draw(canvas: android.graphics.Canvas) {
                    val bounds = bounds
                    val w = bounds.width().toFloat()
                    val h = bounds.height().toFloat()

                    // 3 center positions
                    val cx1 = bounds.left + w * 0.12f
                    val cy1 = bounds.top + h * 0.3f
                    val cx2 = bounds.left + w * 0.88f
                    val cy2 = bounds.top + h * 0.5f
                    val cx3 = bounds.left + w * 0.5f
                    val cy3 = bounds.top + h * 0.7f

                    val radius = w * 0.7f
                    val positions = FloatArray(numColors) { i -> i.toFloat() / (numColors - 1) }

                    // First gradient (base)
                    paint.xfermode = null
                    paint.shader = android.graphics.RadialGradient(
                        cx1, cy1, radius, buildColors(0), positions,
                        android.graphics.Shader.TileMode.CLAMP
                    )
                    canvas.drawRect(bounds, paint)

                    // Second gradient (SCREEN blend)
                    paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SCREEN)
                    paint.shader = android.graphics.RadialGradient(
                        cx2, cy2, radius, buildColors(512), positions,
                        android.graphics.Shader.TileMode.CLAMP
                    )
                    canvas.drawRect(bounds, paint)

                    // Third gradient (SCREEN blend)
                    paint.shader = android.graphics.RadialGradient(
                        cx3, cy3, radius, buildColors(1024), positions,
                        android.graphics.Shader.TileMode.CLAMP
                    )
                    canvas.drawRect(bounds, paint)

                    paint.xfermode = null
                }

                override fun setAlpha(alpha: Int) { paint.alpha = alpha }
                override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
                override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
            }
        }

        fun updatePreview(step: Int) {
            previewBar.background = createRadialPreview(step)
        }

        var previewStep = 0
        val previewHandler = android.os.Handler(android.os.Looper.getMainLooper())
        val previewRunnable = object : Runnable {
            override fun run() {
                previewStep = (previewStep + 8) % 1536
                updatePreview(previewStep)
                previewHandler.postDelayed(this, (speed / 192L).coerceAtLeast(16L))
            }
        }

        // Initialize preview
        updatePreview(0)

        speedSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                speed = progress + KeyboardPrefs.RGB_SPEED_MIN
                speedLabel.text = "Speed: ${speed / 1000}s per cycle"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        satSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                saturation = progress / 100f
                satLabel.text = "Saturation: ${progress}%"
                updatePreview(previewStep)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        brightSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                brightness = progress / 100f
                brightLabel.text = "Brightness: ${progress}%"
                updatePreview(previewStep)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        previewHandler.post(previewRunnable)

        val dialog = AlertDialog.Builder(ctx)
            .setTitle("RGB Smooth")
            .setView(root)
            .setNegativeButton("Cancel") { _, _ ->
                previewHandler.removeCallbacks(previewRunnable)
            }
            .setPositiveButton("Save") { _, _ ->
                previewHandler.removeCallbacks(previewRunnable)
                KeyboardPrefs.setRgbSmoothSpeed(ctx, speed)
                KeyboardPrefs.setRgbSmoothSaturation(ctx, saturation)
                KeyboardPrefs.setRgbSmoothBrightness(ctx, brightness)
                Toast.makeText(ctx, "RGB Smooth settings saved", Toast.LENGTH_SHORT).show()
            }
            .setOnDismissListener {
                previewHandler.removeCallbacks(previewRunnable)
            }
            .create()

        dialog.show()
    }

    private fun showRgbWildDialog() {
        val ctx = this

        var speed = KeyboardPrefs.getRgbWildSpeed(ctx)
        var saturation = KeyboardPrefs.getRgbWildSaturation(ctx)
        var brightness = KeyboardPrefs.getRgbWildBrightness(ctx)

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp(ctx), 16.dp(ctx), 24.dp(ctx), 8.dp(ctx))
        }

        // Preview bar - will flash rapidly with radial gradient
        val previewBar = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                48.dp(ctx)
            ).apply {
                topMargin = 16.dp(ctx)
                bottomMargin = 8.dp(ctx)
            }
        }
        root.addView(previewBar)

        // Speed label
        val speedLabel = TextView(ctx).apply {
            text = "Speed: ${speed}ms"
            textSize = 14f
            setPadding(0, 12.dp(ctx), 0, 4.dp(ctx))
        }
        root.addView(speedLabel)

        val speedSeekBar = SeekBar(ctx).apply {
            max = KeyboardPrefs.RGB_WILD_SPEED_MAX - KeyboardPrefs.RGB_WILD_SPEED_MIN
            progress = speed - KeyboardPrefs.RGB_WILD_SPEED_MIN
        }
        root.addView(speedSeekBar)

        val speedHint = TextView(ctx).apply {
            text = "Insane ← → Chill"
            textSize = 12f
            gravity = android.view.Gravity.CENTER
            setTextColor(0xFF888888.toInt())
        }
        root.addView(speedHint)

        // Saturation
        val satLabel = TextView(ctx).apply {
            text = "Saturation: ${(saturation * 100).toInt()}%"
            textSize = 14f
            setPadding(0, 16.dp(ctx), 0, 4.dp(ctx))
        }
        root.addView(satLabel)

        val satSeekBar = SeekBar(ctx).apply {
            max = 70  // 30% to 100%
            progress = ((saturation - 0.3f) * 100).toInt()
        }
        root.addView(satSeekBar)

        // Brightness
        val brightLabel = TextView(ctx).apply {
            text = "Brightness: ${(brightness * 100).toInt()}%"
            textSize = 14f
            setPadding(0, 16.dp(ctx), 0, 4.dp(ctx))
        }
        root.addView(brightLabel)

        val brightSeekBar = SeekBar(ctx).apply {
            max = 70  // 30% to 100%
            progress = ((brightness - 0.3f) * 100).toInt()
        }
        root.addView(brightSeekBar)

        val random = java.util.Random()

        // Rainbow color function - 6 segments × 256 steps = 1536 total
        fun rainbowColor(x: Int): Int {
            val step = x % 1536
            val segment = step / 256
            val c = step % 256
            return when (segment) {
                0 -> Color.rgb(255, c, 0)           // Red to Yellow
                1 -> Color.rgb(255 - c, 255, 0)     // Yellow to Green
                2 -> Color.rgb(0, 255, c)           // Green to Cyan
                3 -> Color.rgb(0, 255 - c, 255)     // Cyan to Blue
                4 -> Color.rgb(c, 0, 255)           // Blue to Magenta
                5 -> Color.rgb(255, 0, 255 - c)     // Magenta to Red
                else -> Color.rgb(255, 0, 0)
            }
        }

        fun adjustBrightness(color: Int, bright: Float): Int {
            val r = ((color shr 16) and 0xFF) * bright
            val g = ((color shr 8) and 0xFF) * bright
            val b = (color and 0xFF) * bright
            return Color.rgb(r.toInt(), g.toInt(), b.toInt())
        }

        // Create multi-center radial rainbow drawable for preview (3 centers)
        fun createRadialPreview(startStep: Int): android.graphics.drawable.Drawable {
            val numColors = 7

            fun buildColors(phaseOffset: Int): IntArray {
                val colors = IntArray(numColors)
                for (i in 0 until numColors) {
                    val colorStep = startStep + phaseOffset + (i * 1536 / numColors)
                    val color = rainbowColor(colorStep)
                    colors[i] = adjustBrightness(color, brightness)
                }
                return colors
            }

            return object : android.graphics.drawable.Drawable() {
                private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

                override fun draw(canvas: android.graphics.Canvas) {
                    val bounds = bounds
                    val w = bounds.width().toFloat()
                    val h = bounds.height().toFloat()

                    // 3 center positions
                    val cx1 = bounds.left + w * 0.12f
                    val cy1 = bounds.top + h * 0.3f
                    val cx2 = bounds.left + w * 0.88f
                    val cy2 = bounds.top + h * 0.5f
                    val cx3 = bounds.left + w * 0.5f
                    val cy3 = bounds.top + h * 0.7f

                    val radius = w * 0.7f
                    val positions = FloatArray(numColors) { i -> i.toFloat() / (numColors - 1) }

                    // First gradient (base)
                    paint.xfermode = null
                    paint.shader = android.graphics.RadialGradient(
                        cx1, cy1, radius, buildColors(0), positions,
                        android.graphics.Shader.TileMode.CLAMP
                    )
                    canvas.drawRect(bounds, paint)

                    // Second gradient (SCREEN blend)
                    paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SCREEN)
                    paint.shader = android.graphics.RadialGradient(
                        cx2, cy2, radius, buildColors(512), positions,
                        android.graphics.Shader.TileMode.CLAMP
                    )
                    canvas.drawRect(bounds, paint)

                    // Third gradient (SCREEN blend)
                    paint.shader = android.graphics.RadialGradient(
                        cx3, cy3, radius, buildColors(1024), positions,
                        android.graphics.Shader.TileMode.CLAMP
                    )
                    canvas.drawRect(bounds, paint)

                    paint.xfermode = null
                }

                override fun setAlpha(alpha: Int) { paint.alpha = alpha }
                override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
                override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
            }
        }

        var startStep = 0
        var targetStep = random.nextInt(1536)
        val frameInterval = 16L  // ~60fps for smooth animation

        // Ease-in-out function for smooth acceleration/deceleration
        fun easeInOutCubic(t: Float): Float {
            return if (t < 0.5f) {
                4f * t * t * t
            } else {
                1f - (-2f * t + 2f).let { it * it * it } / 2f
            }
        }

        fun updatePreview(step: Int) {
            previewBar.background = createRadialPreview(step)
        }

        val previewHandler = android.os.Handler(android.os.Looper.getMainLooper())
        var transitionStartTime = System.currentTimeMillis()

        val previewRunnable = object : Runnable {
            override fun run() {
                val now = System.currentTimeMillis()
                val transitionDuration = (speed * 0.85f).toLong()
                val elapsed = now - transitionStartTime

                // Calculate eased progress
                val rawProgress = (elapsed.toFloat() / transitionDuration).coerceIn(0f, 1f)
                val easedProgress = easeInOutCubic(rawProgress)

                // Calculate shortest path difference
                var diff = targetStep - startStep
                if (diff > 768) diff -= 1536
                if (diff < -768) diff += 1536

                // Interpolate with easing
                val currentStep = (startStep + (diff * easedProgress).toInt() + 1536) % 1536
                updatePreview(currentStep)

                // Pick new target when transition completes
                if (elapsed >= speed) {
                    startStep = targetStep
                    targetStep = random.nextInt(1536)
                    transitionStartTime = now
                }

                previewHandler.postDelayed(this, frameInterval)
            }
        }

        // Initialize preview with radial gradient
        previewBar.background = createRadialPreview(0)

        speedSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                speed = progress + KeyboardPrefs.RGB_WILD_SPEED_MIN
                speedLabel.text = "Speed: ${speed}ms"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        satSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                saturation = 0.3f + (progress / 100f)
                satLabel.text = "Saturation: ${(saturation * 100).toInt()}%"
                previewBar.background = createRadialPreview(startStep)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        brightSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                brightness = 0.3f + (progress / 100f)
                brightLabel.text = "Brightness: ${(brightness * 100).toInt()}%"
                previewBar.background = createRadialPreview(startStep)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        previewHandler.post(previewRunnable)

        val dialog = AlertDialog.Builder(ctx)
            .setTitle("RGB Wild")
            .setView(root)
            .setNegativeButton("Cancel") { _, _ ->
                previewHandler.removeCallbacks(previewRunnable)
            }
            .setPositiveButton("Save") { _, _ ->
                previewHandler.removeCallbacks(previewRunnable)
                KeyboardPrefs.setRgbWildSpeed(ctx, speed)
                KeyboardPrefs.setRgbWildSaturation(ctx, saturation)
                KeyboardPrefs.setRgbWildBrightness(ctx, brightness)
                Toast.makeText(ctx, "RGB Wild settings saved", Toast.LENGTH_SHORT).show()
            }
            .setOnDismissListener {
                previewHandler.removeCallbacks(previewRunnable)
            }
            .create()

        dialog.show()
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

    private fun openLongPressEditorDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_longpress_editor)

        // Section dropdown
        val spinnerSection = dialog.findViewById<Spinner>(R.id.spinnerLongPressSection)
        val sectionOptions = listOf("Alphabet long press", "Numeric long press", "My binds")
        spinnerSection.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, sectionOptions).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        val pageAlphabet = dialog.findViewById<LinearLayout>(R.id.pageAlphabetLp)
        val pageNumeric = dialog.findViewById<LinearLayout>(R.id.pageNumericLp)
        val pageMyBinds = dialog.findViewById<ScrollView>(R.id.pageMyBindsLp)
        val myBindsContainer = dialog.findViewById<LinearLayout>(R.id.myBindsContainer)

        val currentRowCount = KeyboardPrefs.getRowCount(this)
        var currentPage = 0

        autosaveDirtySection = null

        // Učitaj s globalnim bindovima; svaka promjena se odmah sprema
        val alphabetBinder = LongPressEditorBinder(
            context = this,
            initial = KeyboardPrefs.loadAlphabetLayoutWithGlobalBinds(this, currentRowCount),
            titleText = "Bind long press - alphabet",
            onChanged = { updated ->
                KeyboardPrefs.saveAlphabetLayoutWithGlobalBinds(this, currentRowCount, updated)
                autosaveDirtySection = sectionOptions[0]
            }
        )

        val numericBinder = LongPressEditorBinder(
            context = this,
            initial = KeyboardPrefs.loadNumericLayoutWithGlobalBinds(this, currentRowCount),
            titleText = "Bind long press - numeric",
            lockedLabels = setOf("⇧", "⌫", "↵", "ABC", "abc", " "),
            onChanged = { updated ->
                KeyboardPrefs.saveNumericLayoutWithGlobalBinds(this, currentRowCount, updated)
                autosaveDirtySection = sectionOptions[1]
            }
        )

        alphabetBinder.bindInto(pageAlphabet)
        numericBinder.bindInto(pageNumeric)

        // My binds: every key with a binding; tap to edit its bindings
        fun buildMyBindsPage() {
            myBindsContainer.removeAllViews()

            val groups = listOf(
                "Alphabet" to alphabetBinder,
                "Numeric" to numericBinder
            )

            var anyBound = false
            groups.forEach { (groupName, binder) ->
                val keys = binder.boundKeys()
                if (keys.isEmpty()) return@forEach
                anyBound = true

                myBindsContainer.addView(TextView(this).apply {
                    text = groupName
                    textSize = 16f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setPadding(0, 8.dp(this@MainActivity), 0, 6.dp(this@MainActivity))
                })

                keys.forEach { key ->
                    myBindsContainer.addView(Button(this).apply {
                        text = "${key.label}   →   ${binder.visibleBindings(key).joinToString(" ")}"
                        isAllCaps = false
                        gravity = Gravity.START or Gravity.CENTER_VERTICAL
                        setOnClickListener {
                            binder.openPicker(key) { buildMyBindsPage() }
                        }
                    }, LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = 4.dp(this@MainActivity) })
                }
            }

            if (!anyBound) {
                myBindsContainer.addView(TextView(this).apply {
                    text = "No long-press binds yet"
                    textSize = 14f
                    alpha = 0.75f
                    setPadding(0, 8.dp(this@MainActivity), 0, 8.dp(this@MainActivity))
                })
            }
        }

        fun showPage(page: Int) {
            currentPage = page
            pageAlphabet.visibility = if (page == 0) View.VISIBLE else View.GONE
            pageNumeric.visibility = if (page == 1) View.VISIBLE else View.GONE
            pageMyBinds.visibility = if (page == 2) View.VISIBLE else View.GONE
            if (page == 2) buildMyBindsPage()
        }

        spinnerSection.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position != currentPage) showSavedToastIfDirty()
                showPage(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        dialog.setOnDismissListener {
            showSavedToastIfDirty()
        }

        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        showPage(0)
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
            setPadding(16.dp(this), 12.dp(this), 16.dp(this), 8.dp(this))
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
            setPadding(0, 10.dp(this), 0, 6.dp(this))
        })

        val scroll = ScrollView(this)
        val grid = GridLayout(this).apply {
            columnCount = 6
        }

        SpecialChars.ALL.forEach { ch ->
            val b = Button(this).apply {
                text = ch
                isAllCaps = false
                minHeight = 44.dp(this)
                minWidth = 44.dp(this)
                setPadding(0, 0, 0, 0)
            }
            b.setOnClickListener {
                onSelected(oldSlot.copy(type = EdgeActionType.CHAR, value = ch))
            }

            val lp = GridLayout.LayoutParams().apply {
                width = 0
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(4.dp(this@MainActivity), 4.dp(this@MainActivity), 4.dp(this@MainActivity), 4.dp(this@MainActivity))
            }
            grid.addView(b, lp)
        }

        scroll.addView(grid)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                320.dp(this)
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
            setPadding(16.dp(this), 16.dp(this), 16.dp(this), 12.dp(this))
        }

        val title = TextView(this).apply {
            text = "Horizontal center keys ($rowCount rows)"
            textSize = 18f
            setPadding(0, 0, 0, 12.dp(this))
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
                topMargin = 12.dp(this@MainActivity)
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

    private fun showSavedToastIfDirty() {
        val section = autosaveDirtySection ?: return
        autosaveDirtySection = null
        Toast.makeText(this, "$section saved", Toast.LENGTH_SHORT).show()
    }

    override fun onStop() {
        super.onStop()
        // Leaving the app while an autosaving popup is open → confirm the autosave
        showSavedToastIfDirty()
    }

    private fun openLayoutEditorDialog() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_layout_editor)

        // Section dropdown
        val spinnerSection = dialog.findViewById<Spinner>(R.id.spinnerSection)
        val sectionOptions = listOf(
            "Alphabet Layout",
            "Numeric Layout",
            "Side Buttons",
            "Horizontal Center",
            "Emoji Picker Settings"
        )
        val sectionAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, sectionOptions).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerSection.adapter = sectionAdapter

        // Pages
        val pageLayout = dialog.findViewById<LinearLayout>(R.id.pageLayout)
        val pageNumeric = dialog.findViewById<LinearLayout>(R.id.pageNumeric)
        val pageSideButtons = dialog.findViewById<LinearLayout>(R.id.pageSideButtons)
        val pageHorizontalCenter = dialog.findViewById<LinearLayout>(R.id.pageHorizontalCenter)
        val pageEmojiPicker = dialog.findViewById<ScrollView>(R.id.pageEmojiPicker)
        val emojiPickerContainer = dialog.findViewById<LinearLayout>(R.id.emojiPickerContainer)

        // Containers
        val layoutEditorContainer = dialog.findViewById<FrameLayout>(R.id.layoutEditorContainer)
        val numericEditorContainer = dialog.findViewById<FrameLayout>(R.id.numericEditorContainer)
        val horizontalEditorContainer = dialog.findViewById<FrameLayout>(R.id.horizontalEditorContainer)

        // Buttons
        val btnSwapSelected = dialog.findViewById<Button>(R.id.btnSwapSelected)

        // Saved layouts
        val savedLayoutsContainer = dialog.findViewById<LinearLayout>(R.id.savedLayoutsContainer)
        val spinnerSavedLayouts = dialog.findViewById<Spinner>(R.id.spinnerSavedLayouts)

        val rowCount = KeyboardPrefs.getRowCount(this)
        var currentPage = 0

        autosaveDirtySection = null
        fun markDirty() {
            autosaveDirtySection = sectionOptions[currentPage]
        }

        // Layout binders
        val layoutBinder = LayoutEditorBinder(
            context = this,
            initial = KeyboardPrefs.loadAlphabetLayoutWithGlobalBinds(this, rowCount),
            onSaved = { updated: KeyboardConfig ->
                KeyboardPrefs.saveAlphabetLayoutWithGlobalBinds(this, rowCount, updated)
                markDirty()
            }
        )

        val numericLocked = setOf("⇧", "⌫", "↵", "ABC", "abc", " ", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9")
        lateinit var numericBinder: LayoutEditorBinder
        numericBinder = LayoutEditorBinder(
            context = this,
            initial = KeyboardPrefs.loadNumericLayoutWithGlobalBinds(this, rowCount),
            onSaved = { updated: KeyboardConfig ->
                KeyboardPrefs.saveNumericLayoutWithGlobalBinds(this, rowCount, updated)
                markDirty()
            },
            lockedLabels = numericLocked,
            onEmptyKeyClick = { key ->
                showSpecialCharPicker { picked ->
                    key.label = picked
                    key.longPressBindings.remove("__USER_EMPTY__")
                    numericBinder.bindInto(numericEditorContainer)
                    numericBinder.saveExternally()
                }
            },
            allowClearKeys = true
        )

        lateinit var horizontalBinder: LayoutEditorBinder
        horizontalBinder = LayoutEditorBinder(
            context = this,
            initial = KeyboardPrefs.loadHorizontalCenterLayoutForRowCount(this, rowCount),
            onSaved = { updated: KeyboardConfig ->
                KeyboardPrefs.saveHorizontalCenterLayoutForRowCount(this, rowCount, updated)
                markDirty()
            },
            lockedLabels = emptySet(),
            onEmptyKeyClick = { key ->
                showSpecialCharPicker { picked ->
                    key.label = picked
                    key.longPressBindings.remove("__USER_EMPTY__")
                    horizontalBinder.bindInto(horizontalEditorContainer)
                    horizontalBinder.saveExternally()
                }
            },
            allowClearKeys = true
        )

        layoutBinder.bindInto(layoutEditorContainer)
        numericBinder.bindInto(numericEditorContainer)
        horizontalBinder.bindInto(horizontalEditorContainer)

        setupSideButtonsPage(pageSideButtons) { markDirty() }

        // Emoji picker settings
        var emojiButtonOrder = EmojiPickerStorage.getButtonOrder(this).toMutableList()
        var emojiTabsPosition = EmojiPickerStorage.getTabsPosition(this)
        var emojiButtonsSide = EmojiPickerStorage.getButtonsSide(this)

        fun setupEmojiPickerPage() {
            emojiPickerContainer.removeAllViews()

            // Tabs position
            val tabsLabel = TextView(this).apply {
                text = "Category tabs position"
                textSize = 16f
                setPadding(0, 0, 0, 8.dp(this))
            }
            emojiPickerContainer.addView(tabsLabel)

            val tabsSpinner = Spinner(this)
            val tabsOptions = EmojiPickerStorage.TabsPosition.entries.map { it.displayName }
            tabsSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, tabsOptions).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            tabsSpinner.setSelection(EmojiPickerStorage.TabsPosition.entries.indexOf(emojiTabsPosition))
            tabsSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val picked = EmojiPickerStorage.TabsPosition.entries[position]
                    if (picked == emojiTabsPosition) return
                    emojiTabsPosition = picked
                    EmojiPickerStorage.setTabsPosition(this@MainActivity, emojiTabsPosition)
                    markDirty()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
            emojiPickerContainer.addView(tabsSpinner, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16.dp(this@MainActivity) })

            // Buttons side
            val sideLabel = TextView(this).apply {
                text = "Action buttons side"
                textSize = 16f
                setPadding(0, 0, 0, 8.dp(this))
            }
            emojiPickerContainer.addView(sideLabel)

            val sideSpinner = Spinner(this)
            val sideOptions = EmojiPickerStorage.ButtonsSide.entries.map { it.displayName }
            sideSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, sideOptions).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            sideSpinner.setSelection(EmojiPickerStorage.ButtonsSide.entries.indexOf(emojiButtonsSide))
            sideSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val picked = EmojiPickerStorage.ButtonsSide.entries[position]
                    if (picked == emojiButtonsSide) return
                    emojiButtonsSide = picked
                    EmojiPickerStorage.setButtonsSide(this@MainActivity, emojiButtonsSide)
                    markDirty()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
            emojiPickerContainer.addView(sideSpinner, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16.dp(this@MainActivity) })

            // Button order
            val orderLabel = TextView(this).apply {
                text = "Button order (long press to drag)"
                textSize = 16f
                setPadding(0, 0, 0, 8.dp(this))
            }
            emojiPickerContainer.addView(orderLabel)

            val orderContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }
            emojiPickerContainer.addView(orderContainer)

            fun rebuildOrder() {
                orderContainer.removeAllViews()

                emojiButtonOrder.forEachIndexed { index, action ->
                    val btn = Button(this).apply {
                        text = "${index + 1}. ${EmojiPickerStorage.getButtonLabel(action)} ${EmojiPickerStorage.getButtonDisplayName(action)}"
                        isAllCaps = false
                        tag = index

                        setOnLongClickListener { v ->
                            val data = ClipData.newPlainText("fromIndex", index.toString())
                            val shadow = View.DragShadowBuilder(v)
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                                v.startDragAndDrop(data, shadow, null, 0)
                            } else {
                                @Suppress("DEPRECATION")
                                v.startDrag(data, shadow, null, 0)
                            }
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

                                    val from = e.clipData?.getItemAt(0)?.text?.toString()?.toIntOrNull()
                                        ?: return@setOnDragListener true
                                    val to = (v.tag as? Int) ?: return@setOnDragListener true
                                    if (from == to) return@setOnDragListener true

                                    // Move dragged button to the drop position
                                    val moved = emojiButtonOrder.removeAt(from)
                                    emojiButtonOrder.add(to, moved)

                                    // Autosave
                                    EmojiPickerStorage.saveButtonOrder(this@MainActivity, emojiButtonOrder)
                                    markDirty()

                                    // Rebuild after the drag finishes dispatching
                                    v.post { rebuildOrder() }
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
                    orderContainer.addView(btn, LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = 4.dp(this@MainActivity) })
                }
            }

            rebuildOrder()
        }

        // Show page function
        fun showPage(page: Int) {
            currentPage = page
            pageLayout.visibility = if (page == 0) View.VISIBLE else View.GONE
            pageNumeric.visibility = if (page == 1) View.VISIBLE else View.GONE
            pageSideButtons.visibility = if (page == 2) View.VISIBLE else View.GONE
            pageHorizontalCenter.visibility = if (page == 3) View.VISIBLE else View.GONE
            pageEmojiPicker.visibility = if (page == 4) View.VISIBLE else View.GONE

            // Show/hide swap button (only for layout pages)
            btnSwapSelected.visibility = if (page in listOf(0, 1, 3)) View.VISIBLE else View.GONE

            if (page == 4) setupEmojiPickerPage()
        }

        spinnerSection.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position != currentPage) showSavedToastIfDirty()
                showPage(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Saved layouts dropdown
        fun refreshSavedLayoutsSpinner() {
            val savedLayouts = SavedLayoutStorage.getSavedLayouts(this)
            if (savedLayouts.isNotEmpty()) {
                savedLayoutsContainer.visibility = View.VISIBLE
                val layoutNames = mutableListOf("-- Saved layouts (${savedLayouts.size}) --")
                layoutNames.addAll(savedLayouts.map { "${it.name} (${it.rowCount} rows)" })
                spinnerSavedLayouts.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, layoutNames).apply {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
                spinnerSavedLayouts.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        if (position == 0) return
                        val selectedLayout = SavedLayoutStorage.getSavedLayouts(this@MainActivity)[position - 1]
                        showSavedLayoutActions(
                            savedLayout = selectedLayout,
                            onListChanged = { refreshSavedLayoutsSpinner() },
                            onRestored = {
                                // Restore replaced everything; skip the "saved" toast
                                autosaveDirtySection = null
                                dialog.dismiss()
                            },
                            onClosed = { spinnerSavedLayouts.setSelection(0) }
                        )
                    }
                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
            } else {
                savedLayoutsContainer.visibility = View.GONE
            }
        }
        refreshSavedLayoutsSpinner()

        btnSwapSelected.setOnClickListener {
            val swapped = when (currentPage) {
                0 -> layoutBinder.swapSelectedExternally()
                1 -> numericBinder.swapSelectedExternally()
                3 -> horizontalBinder.swapSelectedExternally()
                else -> false
            }
            if (!swapped) Toast.makeText(this, "Select 2 keys", Toast.LENGTH_SHORT).show()
        }

        dialog.setOnDismissListener {
            showSavedToastIfDirty()
        }

        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.94f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
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
            setPadding(16.dp(this), 16.dp(this), 16.dp(this), 16.dp(this))
        }

        // Preview bar (definiraj PRIJE updatePreview da bude dostupan)
        val previewBar = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                48.dp(this@MainActivity)
            ).apply {
                topMargin = 12.dp(this@MainActivity)
            }
        }

        // Input fields
        val hexInput = android.widget.EditText(this).apply {
            textSize = 14f
            setPadding(8.dp(this), 8.dp(this), 8.dp(this), 8.dp(this))
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
                240.dp(this)
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
            setPadding(0, 12.dp(this), 0, 4.dp(this))
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
            setPadding(0, 12.dp(this), 0, 0)
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
            setPadding(0, 4.dp(this), 0, 0)
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
            setPadding(0, 4.dp(this), 0, 0)
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

    private fun setupSideButtonsPage(container: LinearLayout, onChanged: () -> Unit) {
        container.removeAllViews()

        val selectedRowCount = KeyboardPrefs.getRowCount(this)

        val title = TextView(this).apply {
            text = "Set side buttons"
            textSize = 18f
            setPadding(0, 0, 0, 10.dp(this))
        }

        val hint = TextView(this).apply {
            text = when (selectedRowCount) {
                4 -> "4-row: row1 right, row2 left, row3 right, row4 left"
                3 -> "3-row preview mode"
                else -> "5-row: 3 lijeva + 3 desna side buttona"
            }
            textSize = 13f
            alpha = 0.75f
            setPadding(0, 0, 0, 10.dp(this))
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

        fun persist() {
            EdgeSlotsStorage.save(this, slots)
            onChanged()
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
                    setPadding(8.dp(this), 14.dp(this), 8.dp(this), 14.dp(this))

                    setOnClickListener {
                        showEdgeTypePicker(slot) { newSlot ->
                            val normalized = fixSlotPosition(index, normalizeSlot(newSlot))
                            enforceNoDuplicates(slots, index, normalized)
                            slots[index] = normalized
                            persist()
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

                                persist()
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
                    setMargins(6.dp(this@MainActivity), 6.dp(this@MainActivity), 6.dp(this@MainActivity), 6.dp(this@MainActivity))
                }

                grid.addView(btn, lp)
            }
        }

        rebuild()

        container.addView(title)
        container.addView(hint)
        container.addView(grid)
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
        preview.visibility = View.VISIBLE
        preview.shape = shape
        preview.invalidate()

        KeyboardPrefs.setShape(this, shape)
    }

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

    private fun setupThemeSpinner() {
        themeOptions = buildList {
            add(THEME_LIGHT)
            add(THEME_DARK)
            if (hasCustomColors() || SavedLayoutStorage.getSavedLayouts(this@MainActivity).isNotEmpty()) add(THEME_CUSTOM)
            add(THEME_RGB_SMOOTH)
            add(THEME_RGB_WILD)
        }

        val themeAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            themeOptions
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerTheme.adapter = themeAdapter

        // Determine current selection
        val prefs = getSharedPreferences("theme_prefs", MODE_PRIVATE)
        val isDark = prefs.getBoolean("dark_mode", true)
        val isCustom = prefs.getBoolean("use_custom_theme", false)

        val currentTheme = when {
            KeyboardPrefs.isRgbSmoothEnabled(this) -> THEME_RGB_SMOOTH
            KeyboardPrefs.isRgbWildEnabled(this) -> THEME_RGB_WILD
            isCustom && THEME_CUSTOM in themeOptions -> THEME_CUSTOM
            isDark -> THEME_DARK
            else -> THEME_LIGHT
        }
        currentThemePosition = themeOptions.indexOf(currentTheme)
        spinnerTheme.setSelection(currentThemePosition)
        updateThemeSettingsButton()

        spinnerTheme.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                // Ignore the initial callback fired for the already-active theme
                if (position == currentThemePosition) return
                val previousPosition = currentThemePosition
                currentThemePosition = position

                if (themeOptions[position] == THEME_CUSTOM) {
                    // Custom = pick one of the saved layouts; nothing picked → back to previous theme
                    showSavedLayoutsPopup { applied ->
                        if (applied) {
                            selectCustomTheme()
                        } else {
                            currentThemePosition = previousPosition
                            spinnerTheme.setSelection(previousPosition)
                        }
                        updateThemeSettingsButton()
                    }
                    return
                }

                applyThemeSelection(themeOptions[position])
                updateThemeSettingsButton()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    /** Mark the custom theme active (the colors themselves were applied by the saved-layouts popup). */
    private fun selectCustomTheme() {
        getSharedPreferences("theme_prefs", MODE_PRIVATE).edit()
            .putBoolean("use_custom_theme", true)
            .apply()
        KeyboardPrefs.setRgbSmoothEnabled(this, false)
        KeyboardPrefs.setRgbWildEnabled(this, false)
    }

    /** Button under the Theme dropdown: RGB settings for RGB themes, Saved layouts for Custom. */
    private fun updateThemeSettingsButton() {
        when (themeOptions.getOrNull(currentThemePosition)) {
            THEME_RGB_SMOOTH, THEME_RGB_WILD -> {
                btnThemeSettings.text = "RGB settings"
                btnThemeSettings.visibility = View.VISIBLE
            }
            THEME_CUSTOM -> {
                btnThemeSettings.text = "Saved layouts"
                btnThemeSettings.visibility = View.VISIBLE
            }
            else -> btnThemeSettings.visibility = View.GONE
        }
    }

    private fun applyThemeSelection(theme: String) {
        val prefs = getSharedPreferences("theme_prefs", MODE_PRIVATE)

        // Selecting an RGB theme activates it; any other theme turns RGB off
        KeyboardPrefs.setRgbSmoothEnabled(this, theme == THEME_RGB_SMOOTH)
        KeyboardPrefs.setRgbWildEnabled(this, theme == THEME_RGB_WILD)

        when (theme) {
            THEME_LIGHT -> {
                prefs.edit()
                    .putBoolean("dark_mode", false)
                    .putBoolean("use_custom_theme", false)
                    .apply()
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
            THEME_DARK -> {
                prefs.edit()
                    .putBoolean("dark_mode", true)
                    .putBoolean("use_custom_theme", false)
                    .apply()
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
            THEME_CUSTOM -> selectCustomTheme()
            THEME_RGB_SMOOTH -> showRgbSmoothDialog()
            THEME_RGB_WILD -> showRgbWildDialog()
        }
    }

    private fun hasCustomColors(): Boolean {
        // Check if user has saved custom theme colors
        val prefs = getSharedPreferences("custom_theme_prefs", MODE_PRIVATE)
        return prefs.getBoolean("has_custom_theme", false)
    }

    private fun saveCurrentColorsAsCustomTheme() {
        val customPrefs = getSharedPreferences("custom_theme_prefs", MODE_PRIVATE)

        // Save current keyboard colors as custom theme
        customPrefs.edit()
            .putBoolean("has_custom_theme", true)
            .putInt("custom_key_fill", KeyboardPrefs.getKeysBg(this))
            .putInt("custom_key_text", KeyboardPrefs.getKeysTextColor(this))
            .putInt("custom_background", KeyboardPrefs.getBackgroundColor(this))
            .putInt("custom_space1_bg", KeyboardPrefs.getSpace1Bg(this))
            .putInt("custom_space2_bg", KeyboardPrefs.getSpace2Bg(this))
            .putInt("custom_enter_bg", KeyboardPrefs.getEnterBg(this))
            .putInt("custom_enter_icon", KeyboardPrefs.getEnterIcon(this))
            .putInt("custom_side_bg", KeyboardPrefs.getSideButtonsBg(this))
            .putInt("custom_side_text", KeyboardPrefs.getSideButtonsTextColor(this))
            .apply()
    }

    private fun loadCustomThemeColors() {
        val customPrefs = getSharedPreferences("custom_theme_prefs", MODE_PRIVATE)

        if (!customPrefs.getBoolean("has_custom_theme", false)) return

        // Load and apply custom theme colors
        val keyFill = customPrefs.getInt("custom_key_fill", getColor(R.color.key_fill_light))
        val keyText = customPrefs.getInt("custom_key_text", Color.BLACK)
        val background = customPrefs.getInt("custom_background", getColor(R.color.keyboard_bg_light))
        val space1Bg = customPrefs.getInt("custom_space1_bg", keyFill)
        val space2Bg = customPrefs.getInt("custom_space2_bg", keyFill)
        val enterBg = customPrefs.getInt("custom_enter_bg", getColor(R.color.special_fill))
        val enterIcon = customPrefs.getInt("custom_enter_icon", Color.WHITE)
        val sideBg = customPrefs.getInt("custom_side_bg", Color.TRANSPARENT)
        val sideText = customPrefs.getInt("custom_side_text", Color.BLACK)

        KeyboardPrefs.setKeysColors(this, keyFill, keyText, true, useTheme = false)
        KeyboardPrefs.setBackgroundColor(this, background, false)
        KeyboardPrefs.setSpaceColors(this, space1Bg, space2Bg, true)
        KeyboardPrefs.setEnterColors(this, enterBg, enterIcon)
        KeyboardPrefs.setSideButtonsColors(this, sideBg, sideText, false)

        // Signal keyboard to recreate
        val keyboardIntent = Intent("com.example.antarakeyboard.RECREATE_KEYBOARD")
        sendBroadcast(keyboardIntent)
    }

    /** Preview / Restore / Rename / Delete menu for one saved layout. */
    private fun showSavedLayoutActions(
        savedLayout: SavedLayoutStorage.SavedLayout,
        onListChanged: () -> Unit,
        onRestored: () -> Unit,
        onClosed: () -> Unit = {}
    ) {
        AlertDialog.Builder(this)
            .setTitle(savedLayout.name)
            .setItems(arrayOf("Preview", "Restore", "Rename", "Delete")) { _, which ->
                when (which) {
                    0 -> showSavedLayoutPreview(savedLayout)
                    1 -> {
                        AlertDialog.Builder(this)
                            .setTitle("Restore layout")
                            .setMessage("This will replace your current layout and colors.")
                            .setPositiveButton("Restore") { _, _ ->
                                restoreSavedLayout(savedLayout)
                                onRestored()
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                    2 -> {
                        val input = android.widget.EditText(this).apply { setText(savedLayout.name); selectAll() }
                        AlertDialog.Builder(this).setTitle("Rename").setView(input)
                            .setPositiveButton("Save") { _, _ ->
                                SavedLayoutStorage.renameLayout(this, savedLayout.id, input.text.toString().trim())
                                onListChanged()
                            }.setNegativeButton("Cancel", null).show()
                    }
                    3 -> {
                        AlertDialog.Builder(this).setTitle("Delete?")
                            .setPositiveButton("Delete") { _, _ ->
                                SavedLayoutStorage.deleteLayout(this, savedLayout.id)
                                onListChanged()
                            }.setNegativeButton("Cancel", null).show()
                    }
                }
                onClosed()
            }
            .setOnCancelListener { onClosed() }
            .show()
    }

    private fun restoreSavedLayout(savedLayout: SavedLayoutStorage.SavedLayout) {
        SavedLayoutStorage.restoreLayout(this, savedLayout)
        sendBroadcast(Intent("com.example.antarakeyboard.RECREATE_KEYBOARD"))
        preview.shape = KeyboardPrefs.getShape(this)
        spinnerKeyShape.setSelection(shapeOptions.indexOfFirst { it.first == KeyboardPrefs.getShape(this) }.coerceAtLeast(0))
        spinnerRowCount.setSelection(rowCountOptions.indexOf(KeyboardPrefs.getRowCount(this)).coerceAtLeast(0))
        Toast.makeText(this, "Layout restored!", Toast.LENGTH_SHORT).show()
    }

    /**
     * Popup opened from Theme → Custom: lists all saved layouts (and the last custom colors).
     * [onDone] gets true if something was applied, false if the popup was just closed.
     */
    private fun showSavedLayoutsPopup(onDone: (applied: Boolean) -> Unit) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        var applied = false

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(this), 16.dp(this), 16.dp(this), 16.dp(this))
            setBackgroundColor(getColor(R.color.main_app_bg))
        }
        root.addView(TextView(this).apply {
            text = "Saved layouts"
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 12.dp(this@MainActivity))
        })

        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ))

        fun addEntry(title: String, subtitle: String, onClick: () -> Unit) {
            list.addView(Button(this).apply {
                text = "$title\n$subtitle"
                isAllCaps = false
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setOnClickListener { onClick() }
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 4.dp(this@MainActivity) })
        }

        fun rebuildList() {
            list.removeAllViews()

            if (hasCustomColors()) {
                addEntry("Last custom colors", "Colors only") {
                    loadCustomThemeColors()
                    Toast.makeText(this, "Custom colors applied", Toast.LENGTH_SHORT).show()
                    applied = true
                    dialog.dismiss()
                }
            }

            val savedLayouts = SavedLayoutStorage.getSavedLayouts(this)
            savedLayouts.forEach { saved ->
                addEntry(
                    saved.name,
                    "${saved.rowCount} rows • ${saved.keyShape.lowercase()} • ${SavedLayoutStorage.formatTimestamp(saved.timestamp)}"
                ) {
                    showSavedLayoutActions(
                        savedLayout = saved,
                        onListChanged = { rebuildList() },
                        onRestored = {
                            applied = true
                            dialog.dismiss()
                        }
                    )
                }
            }

            if (list.childCount == 0) {
                list.addView(TextView(this).apply {
                    text = "No saved layouts yet"
                    alpha = 0.75f
                    setPadding(0, 8.dp(this@MainActivity), 0, 8.dp(this@MainActivity))
                })
            }
        }
        rebuildList()

        dialog.setContentView(root)
        dialog.setOnDismissListener { onDone(applied) }
        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun showSavedLayoutPreview(savedLayout: SavedLayoutStorage.SavedLayout) {
        val gson = com.google.gson.Gson()

        // Mutable color values
        var currentKeyFill = savedLayout.keyFill
        var currentKeyText = savedLayout.keyText
        var currentSpaceBg = savedLayout.space1Bg
        var currentEnterBg = savedLayout.enterBg
        var currentEnterIcon = savedLayout.enterIcon
        var currentBgColor = savedLayout.backgroundColor

        // Parse the saved layout JSON
        val alphabetLayout: KeyboardConfig = gson.fromJson(
            savedLayout.alphabetLayoutJson,
            KeyboardConfig::class.java
        )

        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(this), 16.dp(this), 16.dp(this), 16.dp(this))
        }

        // Title
        val titleText = TextView(this).apply {
            text = savedLayout.name
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 8.dp(this))
        }
        root.addView(titleText)

        // Info section
        val infoText = TextView(this).apply {
            text = "Shape: ${savedLayout.keyShape} | Rows: ${savedLayout.rowCount}\nSaved: ${SavedLayoutStorage.formatTimestamp(savedLayout.timestamp)}"
            textSize = 12f
            setPadding(0, 0, 0, 12.dp(this))
        }
        root.addView(infoText)

        // Colors section
        val colorsLabel = TextView(this).apply {
            text = "Colors (tap to edit):"
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        root.addView(colorsLabel)

        val colorsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8.dp(this), 0, 12.dp(this))
        }

        // Preview container reference for updates
        lateinit var previewContainer: LinearLayout

        // Function to rebuild preview
        fun rebuildPreview() {
            previewContainer.removeAllViews()
            previewContainer.setBackgroundColor(currentBgColor)

            alphabetLayout.rows.forEach { row ->
                val rowLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = 4.dp(this@MainActivity) }
                }

                row.keys.forEach { key ->
                    if (key.label.isNotEmpty()) {
                        val keyView = TextView(this).apply {
                            text = key.label
                            textSize = 12f
                            gravity = android.view.Gravity.CENTER
                            setPadding(6.dp(this), 4.dp(this), 6.dp(this), 4.dp(this))

                            when (key.label) {
                                " " -> {
                                    setBackgroundColor(currentSpaceBg)
                                    text = "␣"
                                }
                                "↵" -> {
                                    setBackgroundColor(currentEnterBg)
                                    setTextColor(currentEnterIcon)
                                }
                                else -> {
                                    setBackgroundColor(currentKeyFill)
                                    setTextColor(currentKeyText)
                                }
                            }

                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            ).apply { marginEnd = 2.dp(this@MainActivity) }
                        }
                        rowLayout.addView(keyView)
                    }
                }
                previewContainer.addView(rowLayout)
            }
        }

        // Clickable color swatches
        fun addColorSwatch(color: Int, label: String, onColorChanged: (Int) -> Unit): View {
            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                isClickable = true
                isFocusable = true
            }
            val swatch = View(this).apply {
                setBackgroundColor(color)
                layoutParams = LinearLayout.LayoutParams(40.dp(this), 40.dp(this))
            }
            val text = TextView(this).apply {
                this.text = label
                textSize = 11f
                gravity = android.view.Gravity.CENTER
            }
            container.addView(swatch)
            container.addView(text)

            container.setOnClickListener {
                showAdvancedColorPicker(label, swatch.solidColor ?: color) { picked ->
                    swatch.setBackgroundColor(picked)
                    onColorChanged(picked)
                    rebuildPreview()
                }
            }

            colorsRow.addView(container)
            return swatch
        }

        addColorSwatch(currentKeyFill, "Keys") { currentKeyFill = it }
        addColorSwatch(currentKeyText, "Text") { currentKeyText = it }
        addColorSwatch(currentSpaceBg, "Space") { currentSpaceBg = it }
        addColorSwatch(currentEnterBg, "Enter") { currentEnterBg = it }
        addColorSwatch(currentBgColor, "Bg") { currentBgColor = it }

        root.addView(colorsRow)

        // Layout preview section
        val layoutLabel = TextView(this).apply {
            text = "Layout preview:"
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 8.dp(this))
        }
        root.addView(layoutLabel)

        // Build preview container
        previewContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(currentBgColor)
            setPadding(8.dp(this), 8.dp(this), 8.dp(this), 8.dp(this))
        }
        rebuildPreview()
        root.addView(previewContainer)

        // Buttons
        val buttonsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.END
            setPadding(0, 16.dp(this), 0, 0)
        }

        val applyColorsBtn = Button(this).apply {
            text = "Apply Colors"
            isAllCaps = false
            setOnClickListener {
                // Save modified colors to the saved layout
                SavedLayoutStorage.updateLayoutColors(
                    this@MainActivity,
                    savedLayout.id,
                    currentKeyFill,
                    currentKeyText,
                    currentSpaceBg,
                    currentEnterBg,
                    currentEnterIcon,
                    currentBgColor
                )
                Toast.makeText(this@MainActivity, "Colors updated!", Toast.LENGTH_SHORT).show()
            }
        }

        val restoreBtn = Button(this).apply {
            text = "Restore Layout"
            isAllCaps = false
            setOnClickListener {
                // Create modified layout with new colors
                val modifiedLayout = savedLayout.copy(
                    keyFill = currentKeyFill,
                    keyText = currentKeyText,
                    space1Bg = currentSpaceBg,
                    space2Bg = currentSpaceBg,
                    enterBg = currentEnterBg,
                    enterIcon = currentEnterIcon,
                    backgroundColor = currentBgColor
                )
                SavedLayoutStorage.restoreLayout(this@MainActivity, modifiedLayout)
                sendBroadcast(Intent("com.example.antarakeyboard.RECREATE_KEYBOARD"))
                preview.shape = KeyboardPrefs.getShape(this@MainActivity)
                Toast.makeText(this@MainActivity, "Layout restored!", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }

        val closeBtn = Button(this).apply {
            text = "Close"
            isAllCaps = false
            setOnClickListener { dialog.dismiss() }
        }

        buttonsRow.addView(applyColorsBtn)
        buttonsRow.addView(restoreBtn)
        buttonsRow.addView(closeBtn)
        root.addView(buttonsRow)

        val scrollView = ScrollView(this).apply {
            addView(root)
        }

        dialog.setContentView(scrollView)
        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.95f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}