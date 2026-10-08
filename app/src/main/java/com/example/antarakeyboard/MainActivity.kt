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
import androidx.core.os.LocaleListCompat
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import com.example.antarakeyboard.data.SettingsBackup
import com.example.antarakeyboard.data.OpenSourceLicenses
import com.example.antarakeyboard.service.KeyScale
import kotlin.math.roundToInt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.antarakeyboard.data.AppLanguages
import com.example.antarakeyboard.data.EdgePos
import com.example.antarakeyboard.data.EdgeSlotsStorage
import com.example.antarakeyboard.extensions.dp
import com.example.antarakeyboard.data.GlobalLongPressStorage
import com.example.antarakeyboard.data.KeyboardPrefs
import com.example.antarakeyboard.data.PrefsManager
import com.example.antarakeyboard.model.EdgeActionType
import com.example.antarakeyboard.model.EdgeSlot
import com.example.antarakeyboard.model.KeyShape
import com.example.antarakeyboard.model.KeyboardConfig
import com.example.antarakeyboard.ui.ColorWheelView
import com.example.antarakeyboard.ui.KeyView
import com.example.antarakeyboard.ui.LayoutEditorBinder
import com.example.antarakeyboard.ui.LongPressEditorBinder
import com.example.antarakeyboard.ui.ReselectSpinner
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
    private lateinit var spinnerTheme: ReselectSpinner
    private lateinit var btnThemeSettings: Button
    private lateinit var bindLPButton: Button
    private lateinit var spinnerReset: Spinner

    // Settings export / import through the system file picker (no storage permission needed)
    private val exportSettingsLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) exportSettingsTo(uri)
        }

    private val importSettingsLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) readSettingsFrom(uri)
        }

    // Autosaving popups (Set layout, Bind long press): section whose "saved" toast is pending
    private var autosaveDirtySection: String? = null

    // Shape options for dropdown
    private val shapeOptions = listOf(
        KeyShape.HEX to R.string.shape_hexagon,
        KeyShape.TRIANGLE to R.string.shape_triangle,
        KeyShape.CIRCLE to R.string.shape_circle,
        KeyShape.CUBE to R.string.shape_cube
    )

    // Row count options for dropdown
    private val rowCountOptions = listOf(3, 4, 5)

    // Vibration options
    private val vibrationOptions = listOf(R.string.option_on, R.string.option_off)

    // Theme options (Custom added dynamically if user has custom colors)
    private var themeOptions = listOf<String>()
    private var currentThemePosition = -1

    companion object {
        // Internal theme ids (shown through themeLabel)
        private const val THEME_LIGHT = "Light"
        private const val THEME_DARK = "Dark"
        private const val THEME_TRANSPARENT = "Transparent"
        private const val THEME_CUSTOM = "Custom"
        private const val THEME_RGB_SMOOTH = "RGB Smooth"
        private const val THEME_RGB_WILD = "RGB Wild"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isDark = PrefsManager.isDarkMode(this)

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

        findViewById<Button>(R.id.btnColors).setOnClickListener {
            openColorsDialog()
        }

        preview = findViewById(R.id.preview)
        bindLPButton = findViewById(R.id.bindLPButton)
        spinnerReset = findViewById(R.id.spinnerReset)

        // Setup Key Shape Spinner
        spinnerKeyShape = findViewById(R.id.spinnerKeyShape)
        val shapeAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            shapeOptions.map { getString(it.second) }
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
            rowCountOptions.map { rowsLabel(it) }
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

        setupKeySizeSlider()
        setupRowSpacingSlider()

        // Setup Vibration Spinner
        spinnerVibration = findViewById(R.id.spinnerVibration)
        val vibrationAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            vibrationOptions.map { getString(it) }
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

        setupLanguageSpinner()
        setupSuggestionsSpinner()

        findViewById<Button>(R.id.btnAbout).setOnClickListener { showAboutDialog() }

        findViewById<Button>(R.id.btnExportSettings).setOnClickListener {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
            exportSettingsLauncher.launch("antara-keyboard-settings-$date.json")
        }
        findViewById<Button>(R.id.btnImportSettings).setOnClickListener {
            // Many file managers report .json as octet-stream or plain text
            importSettingsLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
        }

        // Setup Theme Spinner
        spinnerTheme = findViewById(R.id.spinnerTheme)
        btnThemeSettings = findViewById(R.id.btnThemeSettings)
        btnThemeSettings.setOnClickListener { openThemeSettings() }
        setupThemeSpinner()

        bindLPButton.setOnClickListener {
            openLongPressEditorDialog()
        }

        // Setup Reset Spinner
        val resetOptions = listOf(
            R.string.reset_hint, R.string.reset_colors, R.string.reset_layout, R.string.reset_all
        ).map { getString(it) }
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
     * Key height slider: 60 %–150 % in 5 % steps. Only key height changes (keyboard height
     * follows). Saved while dragging; the keyboard applies it the next time it opens.
     */
    private fun setupKeySizeSlider() {
        val label = findViewById<TextView>(R.id.tvKeySize)
        val seek = findViewById<SeekBar>(R.id.seekKeySize)
        val minPercent = (KeyScale.MIN * 100).roundToInt()
        val maxPercent = (KeyScale.MAX * 100).roundToInt()
        val step = 5

        fun percentAt(progress: Int) = minPercent + progress * step

        seek.max = (maxPercent - minPercent) / step
        val current = (KeyboardPrefs.getKeyScale(this) * 100).roundToInt()
        seek.progress = ((current - minPercent) / step).coerceIn(0, seek.max)
        label.text = getString(R.string.main_key_height, percentAt(seek.progress))

        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val percent = percentAt(progress)
                label.text = getString(R.string.main_key_height, percent)
                if (fromUser) KeyboardPrefs.setKeyScale(this@MainActivity, percent / 100f)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    /**
     * Row spacing slider: −8 dp … +16 dp in 1 dp steps (0 = tuned default). Adds vertical space
     * between rows only. Saved while dragging; the keyboard applies it the next time it opens.
     */
    private fun setupRowSpacingSlider() {
        val label = findViewById<TextView>(R.id.tvRowSpacing)
        val seek = findViewById<SeekBar>(R.id.seekRowSpacing)
        val min = KeyScale.ROW_SPACING_MIN_DP

        fun dpAt(progress: Int) = min + progress
        fun showLabel(dp: Int) {
            // Signed value, e.g. "+4 dp" / "−2 dp" / "0 dp"
            val value = when {
                dp > 0 -> "+$dp dp"
                dp < 0 -> "−${-dp} dp"
                else -> "0 dp"
            }
            label.text = getString(R.string.main_row_spacing, value)
        }

        seek.max = KeyScale.ROW_SPACING_MAX_DP - min
        val current = KeyScale.clampRowSpacing(KeyboardPrefs.getRowSpacingDp(this))
        seek.progress = current - min
        showLabel(current)

        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val dp = dpAt(progress)
                showLabel(dp)
                if (fromUser) KeyboardPrefs.setRowSpacingDp(this@MainActivity, dp)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun exportSettingsTo(uri: Uri) {
        val ok = runCatching {
            val json = SettingsBackup.export(this)
            contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray(Charsets.UTF_8)) }
        }.isSuccess
        Toast.makeText(this, if (ok) R.string.export_done else R.string.export_failed, Toast.LENGTH_SHORT).show()
    }

    private fun readSettingsFrom(uri: Uri) {
        val json = runCatching {
            contentResolver.openInputStream(uri)!!.use { input ->
                // Bounded read: a wrongly picked huge file must not fill memory
                val out = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                while (true) {
                    val n = input.read(chunk)
                    if (n < 0) break
                    out.write(chunk, 0, n)
                    if (out.size() > SettingsBackup.MAX_FILE_BYTES) return@use null
                }
                out.toString("UTF-8")
            }
        }.getOrNull()

        when (val result = json?.let { SettingsBackup.decode(it) } ?: SettingsBackup.DecodeResult.NotABackup) {
            SettingsBackup.DecodeResult.NotABackup ->
                Toast.makeText(this, R.string.import_invalid, Toast.LENGTH_LONG).show()
            SettingsBackup.DecodeResult.NewerVersion ->
                Toast.makeText(this, R.string.import_newer, Toast.LENGTH_LONG).show()
            is SettingsBackup.DecodeResult.Ok ->
                AlertDialog.Builder(this)
                    .setTitle(R.string.import_confirm_title)
                    .setMessage(R.string.import_confirm_message)
                    .setNegativeButton(R.string.action_cancel, null)
                    .setPositiveButton(R.string.import_action) { _, _ -> importSettings(result.data) }
                    .show()
        }
    }

    /** Applies an imported backup; the current state is kept as a "Backup" in Saved layouts. */
    private fun importSettings(data: Map<String, Map<String, Any>>) {
        val timestamp = SavedLayoutStorage.formatTimestamp(System.currentTimeMillis())
        val backup = SavedLayoutStorage.saveCurrentLayout(this, getString(R.string.backup_name, timestamp))

        val ok = runCatching { SettingsBackup.apply(this, data) }.isSuccess
        if (!ok) {
            Toast.makeText(this, R.string.import_failed, Toast.LENGTH_LONG).show()
            return
        }

        // The import replaced Saved layouts too; put the pre-import backup back on top
        SavedLayoutStorage.addLayout(this, backup)
        Toast.makeText(this, R.string.import_done, Toast.LENGTH_LONG).show()

        // Theme and language come from the imported file; rebuild the screen with them
        AppCompatDelegate.setDefaultNightMode(
            if (PrefsManager.isDarkMode(this)) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
        val language = PrefsManager.getAppLanguage(this)
        AppCompatDelegate.setApplicationLocales(
            if (language.isEmpty()) LocaleListCompat.getEmptyLocaleList()
            else LocaleListCompat.forLanguageTags(language)
        )
        recreate()
    }

    /** About popup: version, privacy statement and the open-source licenses (same popup style). */
    private fun showAboutDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val pad = 16.dp(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(getColor(R.color.main_app_bg))
        }

        root.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = getString(R.string.about_version, appVersionName())
            alpha = 0.75f
            setPadding(0, 2.dp(this@MainActivity), 0, pad)
        })
        root.addView(TextView(this).apply {
            text = getString(R.string.about_privacy)
            textSize = 15f
            setPadding(0, 0, 0, pad)
        })
        root.addView(TextView(this).apply {
            text = getString(R.string.about_licenses)
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = getString(R.string.about_licenses_hint)
            alpha = 0.75f
            setPadding(0, 2.dp(this@MainActivity), 0, 8.dp(this@MainActivity))
        })

        OpenSourceLicenses.ENTRIES.forEach { entry ->
            root.addView(Button(this).apply {
                text = "${entry.name}\n${entry.licenseName}"
                isAllCaps = false
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setOnClickListener { showLicenseDialog(entry) }
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 4.dp(this@MainActivity) })
        }

        // Scrolls only when the screen is too short (wrap content like every popup)
        dialog.setContentView(ScrollView(this).apply { addView(root) })
        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    /** Full license text of one component (from assets/licenses/). */
    private fun showLicenseDialog(entry: OpenSourceLicenses.Entry) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val pad = 16.dp(this)
        val licenseText = runCatching {
            assets.open("licenses/${entry.licenseAsset}").bufferedReader().use { it.readText() }
        }.getOrDefault(entry.licenseName)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(getColor(R.color.main_app_bg))
        }
        root.addView(TextView(this).apply {
            text = entry.name
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "${entry.copyright}\n${entry.url}"
            alpha = 0.75f
            setTextIsSelectable(true)
            setPadding(0, 4.dp(this@MainActivity), 0, pad)
        })
        root.addView(TextView(this).apply {
            text = licenseText
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
        })
        root.addView(Button(this).apply {
            text = getString(R.string.action_close)
            isAllCaps = false
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.END; topMargin = pad })

        dialog.setContentView(ScrollView(this).apply { addView(root) })
        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun appVersionName(): String = runCatching {
        val info = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
        info.versionName
    }.getOrNull().orEmpty()

    /** Word suggestions above the keyboard: On / Off (off by default), like vibration. */
    private fun setupSuggestionsSpinner() {
        val spinner = findViewById<Spinner>(R.id.spinnerSuggestions)
        spinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            vibrationOptions.map { getString(it) }
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinner.setSelection(if (KeyboardPrefs.isSuggestionsEnabled(this)) 0 else 1)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                KeyboardPrefs.setSuggestionsEnabled(this@MainActivity, position == 0)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    /**
     * App language: "System default" plus every translation, each named in its own language.
     * AppCompat applies it (system per-app language on Android 13+, stored by AppCompat below 13)
     * and recreates the activity; the keyboard service picks it up on its next start.
     */
    private fun setupLanguageSpinner() {
        val spinner = findViewById<Spinner>(R.id.spinnerLanguage)
        val tags = listOf("") + AppLanguages.TAGS
        val labels = tags.map { tag ->
            if (tag.isEmpty()) getString(R.string.language_system_default) else AppLanguages.nativeName(tag)
        }
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        val appLocales = AppCompatDelegate.getApplicationLocales()
        val current = if (appLocales.isEmpty) "" else AppLanguages.match(appLocales[0]?.toLanguageTag()) ?: ""
        spinner.setSelection(tags.indexOf(current).coerceAtLeast(0))

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val tag = tags[position]
                if (tag == current) return
                PrefsManager.setAppLanguage(this@MainActivity, tag)
                AppCompatDelegate.setApplicationLocales(
                    if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList()
                    else LocaleListCompat.forLanguageTags(tag)
                )
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
        SavedLayoutStorage.saveCurrentLayout(this, getString(R.string.backup_name, timestamp))

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

            // Key height and row spacing sliders back to default
            KeyboardPrefs.resetKeySizing(this)
            setupKeySizeSlider()
            setupRowSpacingSlider()
        }

        if (colors) {
            val isDarkTheme = PrefsManager.isDarkMode(this)

            // Spremi trenutne boje kao custom temu prije reseta
            saveCurrentColorsAsCustomTheme()

            // Resetiraj SVE boje na default teme
            resetAllColorsToThemeDefault(isDarkTheme)

            // Očisti custom theme flag da tipkovnica koristi default boje, i ugasi RGB
            PrefsManager.setCustomTheme(this, false)
            KeyboardPrefs.setRgbSmoothEnabled(this, false)
            KeyboardPrefs.setRgbWildEnabled(this, false)

            // Refresh theme spinner (now has Custom option available)
            setupThemeSpinner()
        }

        // The keyboard re-reads all prefs in onStartInputView, so no signal is needed

        val message = when {
            colors && layout -> R.string.reset_done_all
            colors -> R.string.reset_done_colors
            else -> R.string.reset_done_layout
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
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

        // 1. Space follows the theme again (its own colors stay stored for later)
        KeyboardPrefs.setSpaceUseTheme(this, true)

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

    /* =========================
       COLORS POPUP
       ========================= */

    // Section name shown in the dropdown → name used in the "<name> saved" toast
    private val colorSections = listOf(
        R.string.colors_section_space to R.string.colors_saved_space,
        R.string.colors_section_enter to R.string.colors_saved_enter,
        R.string.colors_section_side to R.string.colors_saved_side,
        R.string.colors_section_keys to R.string.colors_saved_keys,
        R.string.colors_section_background to R.string.colors_section_background,
        R.string.colors_section_theme_defaults to R.string.colors_section_theme_defaults
    )

    /**
     * Colors popup: section dropdown like Set layout / Bind long press. Every change is saved
     * immediately; one "<Section> saved" toast on section switch, close, or leaving the app.
     */
    private fun openColorsDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_colors)

        val spinnerSection = dialog.findViewById<Spinner>(R.id.spinnerColorsSection)
        val page = dialog.findViewById<LinearLayout>(R.id.colorsPageContainer)
        spinnerSection.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            colorSections.map { getString(it.first) }
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        var currentSection = -1
        autosaveDirtySection = null

        fun showSection(index: Int) {
            currentSection = index
            page.removeAllViews()
            val markDirty = { autosaveDirtySection = getString(colorSections[index].second) }
            when (index) {
                0 -> buildSpaceColorsPage(page, markDirty)
                1 -> buildEnterColorsPage(page, markDirty)
                2 -> buildSideButtonsColorsPage(page, markDirty)
                3 -> buildKeysColorsPage(page, markDirty)
                4 -> buildBackgroundColorsPage(page, markDirty)
                5 -> buildThemeDefaultsPage(page, markDirty)
            }
        }

        spinnerSection.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == currentSection) return
                showSavedToastIfDirty()
                showSection(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        dialog.setOnDismissListener {
            showSavedToastIfDirty()
            // Background changes can leave the Transparent theme
            setupThemeSpinner()
        }

        showSection(0)
        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    /** Button filled with [color] (label kept readable); tapping it opens the color picker. */
    private fun colorButton(label: String, color: Int, onPicked: (Int) -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            setSwatch(this, color)
            setOnClickListener {
                showAdvancedColorPicker(label, tag as? Int ?: color) { picked ->
                    setSwatch(this, picked)
                    onPicked(picked)
                }
            }
        }
    }

    private fun setSwatch(button: Button, color: Int) {
        button.tag = color
        button.setBackgroundColor(color)
        val opaque = androidx.core.graphics.ColorUtils.setAlphaComponent(color, 0xFF)
        val light = Color.alpha(color) < 0x80 ||
            androidx.core.graphics.ColorUtils.calculateLuminance(opaque) > 0.5
        button.setTextColor(if (light) Color.BLACK else Color.WHITE)
    }

    private fun LinearLayout.addSpaced(view: View) {
        addView(
            view,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { if (childCount > 0) topMargin = 8.dp(this@MainActivity) }
        )
    }

    private fun buildSpaceColorsPage(page: LinearLayout, onChanged: () -> Unit) {
        var useTheme = KeyboardPrefs.getSpaceUseTheme(this)
        var linked = KeyboardPrefs.isSpaceLinked(this)
        // Start from what is drawn now, so switching off "use theme" keeps the look
        val (drawn1, drawn2) = KeyboardPrefs.resolveSpaceColors(this, PrefsManager.isDarkMode(this))
        var c1 = drawn1
        var c2 = drawn2

        fun save() {
            if (linked) c2 = c1
            KeyboardPrefs.setSpaceColors(this, c1, c2, linked)
            onChanged()
        }

        lateinit var b2: Button
        val b1 = colorButton(getString(R.string.color_space_1), c1) { picked ->
            c1 = picked
            if (linked) setSwatch(b2, picked)
            save()
        }
        b2 = colorButton(getString(R.string.color_space_2), c2) { picked ->
            c2 = picked
            if (linked) {
                c1 = picked
                setSwatch(b1, picked)
            }
            save()
        }

        val cb = CheckBox(this).apply {
            text = getString(R.string.color_space_linked)
            isChecked = linked
            isEnabled = !useTheme
            setOnCheckedChangeListener { _, isChecked ->
                linked = isChecked
                if (linked) setSwatch(b2, c1)
                save()
            }
        }

        fun updateEnabled() {
            cb.isEnabled = !useTheme
            b1.isEnabled = !useTheme
            b2.isEnabled = !useTheme
        }

        val cbTheme = CheckBox(this).apply {
            text = getString(R.string.color_use_theme)
            isChecked = useTheme
            setOnCheckedChangeListener { _, isChecked ->
                useTheme = isChecked
                if (isChecked) KeyboardPrefs.setSpaceUseTheme(this@MainActivity, true) else save()
                updateEnabled()
                onChanged()
            }
        }

        page.addSpaced(cbTheme)
        page.addSpaced(cb)
        page.addSpaced(b1)
        page.addSpaced(b2)
        updateEnabled()
    }

    private fun buildEnterColorsPage(page: LinearLayout, onChanged: () -> Unit) {
        var bg = KeyboardPrefs.getEnterBg(this)
        var icon = KeyboardPrefs.getEnterIcon(this)

        page.addSpaced(colorButton(getString(R.string.color_background), bg) { picked ->
            bg = picked
            KeyboardPrefs.setEnterColors(this, bg, icon)
            onChanged()
        })
        page.addSpaced(colorButton(getString(R.string.color_icon), icon) { picked ->
            icon = picked
            KeyboardPrefs.setEnterColors(this, bg, icon)
            onChanged()
        })
    }

    private fun buildSideButtonsColorsPage(page: LinearLayout, onChanged: () -> Unit) {
        var useThemeBg = KeyboardPrefs.getSideButtonsUseThemeBg(this)
        // Theme look = no background + theme icon color; start from it so turning the theme off keeps the look
        var bg = if (useThemeBg) Color.TRANSPARENT else KeyboardPrefs.getSideButtonsBg(this)
        var textColor = if (useThemeBg) {
            themeColor(R.attr.edgeIconText, if (PrefsManager.isDarkMode(this)) Color.WHITE else Color.BLACK)
        } else {
            KeyboardPrefs.getSideButtonsTextColor(this)
        }

        fun save() {
            KeyboardPrefs.setSideButtonsColors(this, bg, textColor, useThemeBg)
            onChanged()
        }

        val bgBtn = colorButton(getString(R.string.color_background), bg) { picked -> bg = picked; save() }
        val textBtn = colorButton(getString(R.string.color_icon), textColor) { picked -> textColor = picked; save() }
        bgBtn.isEnabled = !useThemeBg
        textBtn.isEnabled = !useThemeBg

        val cb = CheckBox(this).apply {
            text = getString(R.string.color_use_theme)
            isChecked = useThemeBg
            setOnCheckedChangeListener { _, isChecked ->
                useThemeBg = isChecked
                bgBtn.isEnabled = !isChecked
                textBtn.isEnabled = !isChecked
                save()
            }
        }

        page.addSpaced(cb)
        page.addSpaced(bgBtn)
        page.addSpaced(textBtn)
    }

    private fun buildKeysColorsPage(page: LinearLayout, onChanged: () -> Unit) {
        val themeColors = KeyboardPrefs.getThemeDefaultsForMode(this, PrefsManager.isDarkMode(this))
        var useTheme = KeyboardPrefs.getKeysUseTheme(this)
        var allSame = KeyboardPrefs.getKeysAllSameColor(this)
        // While the theme is used, start from the theme colors, so turning it off keeps the look
        var bg = if (useTheme) themeColors.keyFill else KeyboardPrefs.getKeysBg(this)
        var textColor = if (useTheme) themeColors.keyText else KeyboardPrefs.getKeysTextColor(this)

        fun save() {
            KeyboardPrefs.setKeysColors(this, bg, textColor, allSame, useTheme = useTheme)
            onChanged()
        }

        // "All keys same color": one background + text color
        val allSameContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        allSameContainer.addSpaced(colorButton(getString(R.string.color_background), bg) { picked -> bg = picked; save() })
        allSameContainer.addSpaced(colorButton(getString(R.string.color_text), textColor) { picked -> textColor = picked; save() })

        // Per-key colors: tap a key to edit it
        val individualContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val grid = GridLayout(this).apply { columnCount = 5 }
        val rowCount = KeyboardPrefs.getRowCount(this)
        val layout = KeyboardPrefs.loadAlphabetLayoutForRowCount(this, rowCount)
        val labels = layout.rows.flatMap { it.keys }
            .map { it.label }
            .filter { it.isNotBlank() && it !in setOf(" ", "↵", "⇧", "⌫", "😊") }
            .map { if (it.length == 1 && it[0].isLetter()) it.lowercase() else it }
            .distinct()

        labels.forEach { keyLabel ->
            val btn = Button(this).apply {
                text = keyLabel
                isAllCaps = false
                minWidth = 0
                minimumWidth = 0
            }
            fun refresh() {
                val colors = KeyboardPrefs.getKeyIndividualColors(this, keyLabel)
                setSwatch(btn, colors?.first ?: themeColors.keyFill)
                btn.setTextColor(colors?.second ?: themeColors.keyText)
            }
            refresh()
            btn.setOnClickListener {
                showIndividualKeyColorPopup(keyLabel) {
                    refresh()
                    onChanged()
                }
            }
            grid.addView(btn, GridLayout.LayoutParams().apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(2.dp(this@MainActivity), 2.dp(this@MainActivity), 2.dp(this@MainActivity), 2.dp(this@MainActivity))
            })
        }
        individualContainer.addSpaced(grid)

        fun updateVisibility() {
            allSameContainer.visibility = if (!useTheme && allSame) View.VISIBLE else View.GONE
            individualContainer.visibility = if (!useTheme && !allSame) View.VISIBLE else View.GONE
        }

        val cbAllSame = CheckBox(this).apply {
            text = getString(R.string.color_all_keys_same)
            isChecked = allSame
            isEnabled = !useTheme
            setOnCheckedChangeListener { _, isChecked ->
                allSame = isChecked
                updateVisibility()
                save()
            }
        }

        val cbTheme = CheckBox(this).apply {
            text = getString(R.string.color_use_theme)
            isChecked = useTheme
            setOnCheckedChangeListener { _, isChecked ->
                useTheme = isChecked
                cbAllSame.isEnabled = !isChecked
                updateVisibility()
                save()
            }
        }

        page.addSpaced(cbTheme)
        page.addSpaced(cbAllSame)
        page.addSpaced(allSameContainer)
        page.addSpaced(individualContainer)
        updateVisibility()
    }

    /** Small popup (same style as the others) for one key's background + text color. Autosaves. */
    private fun showIndividualKeyColorPopup(keyLabel: String, onChanged: () -> Unit) {
        val themeColors = KeyboardPrefs.getThemeDefaultsForMode(this, PrefsManager.isDarkMode(this))
        val current = KeyboardPrefs.getKeyIndividualColors(this, keyLabel)
        var bg = current?.first ?: themeColors.keyFill
        var textColor = current?.second ?: themeColors.keyText

        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(this), 16.dp(this), 16.dp(this), 16.dp(this))
            setBackgroundColor(getColor(R.color.main_app_bg))
        }

        val preview = TextView(this).apply {
            text = keyLabel
            textSize = 24f
            gravity = Gravity.CENTER
            setBackgroundColor(bg)
            setTextColor(textColor)
            minHeight = 64.dp(this@MainActivity)
        }

        fun save() {
            KeyboardPrefs.setKeyIndividualColors(this, keyLabel, bg, textColor)
            preview.setBackgroundColor(bg)
            preview.setTextColor(textColor)
            onChanged()
        }

        root.addSpaced(preview)
        root.addSpaced(colorButton(getString(R.string.color_background), bg) { picked -> bg = picked; save() })
        root.addSpaced(colorButton(getString(R.string.color_text), textColor) { picked -> textColor = picked; save() })
        root.addSpaced(Button(this).apply {
            text = getString(R.string.color_key_use_default)
            isAllCaps = false
            setOnClickListener {
                KeyboardPrefs.clearKeyIndividualColors(this@MainActivity, keyLabel)
                onChanged()
                dialog.dismiss()
            }
        })

        dialog.setContentView(root)
        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.85f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun buildBackgroundColorsPage(page: LinearLayout, onChanged: () -> Unit) {
        val modes = listOf(
            KeyboardPrefs.BackgroundMode.THEME to getString(R.string.bg_mode_theme),
            KeyboardPrefs.BackgroundMode.DARK to getString(R.string.bg_mode_dark),
            KeyboardPrefs.BackgroundMode.LIGHT to getString(R.string.bg_mode_light),
            KeyboardPrefs.BackgroundMode.CUSTOM to getString(R.string.bg_mode_custom)
            // Transparent is chosen in the Theme dropdown
        )
        val currentMode = KeyboardPrefs.getBackgroundMode(this)

        val customBtn = colorButton(getString(R.string.bg_mode_custom), KeyboardPrefs.getBackgroundColor(this)) { picked ->
            KeyboardPrefs.setBackgroundColor(this, picked, useTheme = false)
            onChanged()
        }
        customBtn.isEnabled = currentMode == KeyboardPrefs.BackgroundMode.CUSTOM

        val group = android.widget.RadioGroup(this).apply {
            orientation = android.widget.RadioGroup.VERTICAL
        }
        modes.forEach { (mode, label) ->
            group.addView(android.widget.RadioButton(this).apply {
                id = View.generateViewId()
                text = label
                tag = mode
                isChecked = mode == currentMode
            })
        }
        group.setOnCheckedChangeListener { g, checkedId ->
            val mode = g.findViewById<View>(checkedId)?.tag as? KeyboardPrefs.BackgroundMode
                ?: return@setOnCheckedChangeListener
            if (mode == KeyboardPrefs.BackgroundMode.CUSTOM &&
                KeyboardPrefs.getBackgroundMode(this) != KeyboardPrefs.BackgroundMode.CUSTOM
            ) {
                // Switching to Custom starts from the background shown now, not an old stored color
                val shown = KeyboardPrefs.resolveKeyboardBackground(this, PrefsManager.isDarkMode(this))
                KeyboardPrefs.setBackgroundColor(this, shown, useTheme = false)
                setSwatch(customBtn, shown)
            } else {
                KeyboardPrefs.setBackgroundMode(this, mode)
            }
            customBtn.isEnabled = mode == KeyboardPrefs.BackgroundMode.CUSTOM
            onChanged()
        }

        page.addSpaced(group)
        page.addSpaced(customBtn)
    }

    private fun buildThemeDefaultsPage(page: LinearLayout, onChanged: () -> Unit) {
        var lightKeyFill = KeyboardPrefs.getThemeLightKeyFill(this)
        var lightKeyText = KeyboardPrefs.getThemeLightKeyText(this)
        var lightSpaceFill = KeyboardPrefs.getThemeLightSpaceFill(this)
        var lightKeyboardBg = KeyboardPrefs.getThemeLightKeyboardBg(this)

        var darkKeyFill = KeyboardPrefs.getThemeDarkKeyFill(this)
        var darkKeyText = KeyboardPrefs.getThemeDarkKeyText(this)
        var darkSpaceFill = KeyboardPrefs.getThemeDarkSpaceFill(this)
        var darkKeyboardBg = KeyboardPrefs.getThemeDarkKeyboardBg(this)

        fun saveLight() {
            KeyboardPrefs.setThemeLightDefaults(this, lightKeyFill, lightKeyText, lightSpaceFill, lightKeyboardBg)
            onChanged()
        }

        fun saveDark() {
            KeyboardPrefs.setThemeDarkDefaults(this, darkKeyFill, darkKeyText, darkSpaceFill, darkKeyboardBg)
            onChanged()
        }

        fun header(text: String) = TextView(this).apply {
            this.text = text
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        page.addSpaced(header(getString(R.string.theme_defaults_light)))
        page.addSpaced(colorButton(getString(R.string.color_key_fill), lightKeyFill) { lightKeyFill = it; saveLight() })
        page.addSpaced(colorButton(getString(R.string.color_key_text), lightKeyText) { lightKeyText = it; saveLight() })
        page.addSpaced(colorButton(getString(R.string.color_space_fill), lightSpaceFill) { lightSpaceFill = it; saveLight() })
        page.addSpaced(colorButton(getString(R.string.color_background), lightKeyboardBg) { lightKeyboardBg = it; saveLight() })

        page.addSpaced(header(getString(R.string.theme_defaults_dark)))
        page.addSpaced(colorButton(getString(R.string.color_key_fill), darkKeyFill) { darkKeyFill = it; saveDark() })
        page.addSpaced(colorButton(getString(R.string.color_key_text), darkKeyText) { darkKeyText = it; saveDark() })
        page.addSpaced(colorButton(getString(R.string.color_space_fill), darkSpaceFill) { darkSpaceFill = it; saveDark() })
        page.addSpaced(colorButton(getString(R.string.color_background), darkKeyboardBg) { darkKeyboardBg = it; saveDark() })

        page.addSpaced(Button(this).apply {
            text = getString(R.string.reset_factory)
            isAllCaps = false
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(R.string.reset_factory)
                    .setMessage(R.string.reset_factory_message)
                    .setNegativeButton(R.string.action_cancel, null)
                    .setPositiveButton(R.string.action_reset) { _, _ ->
                        KeyboardPrefs.resetThemeDefaultsToFactory(this@MainActivity)
                        page.removeAllViews()
                        buildThemeDefaultsPage(page, onChanged)
                        onChanged()
                    }
                    .show()
            }
        })
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
            text = getString(R.string.rgb_speed_seconds, speed / 1000)
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
            text = getString(R.string.rgb_fast_slow)
            textSize = 12f
            gravity = android.view.Gravity.CENTER
            setTextColor(0xFF888888.toInt())
        }
        root.addView(speedHint)

        // Saturation
        val satLabel = TextView(ctx).apply {
            text = getString(R.string.rgb_saturation, (saturation * 100).toInt())
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
            text = getString(R.string.rgb_brightness, (brightness * 100).toInt())
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
                speedLabel.text = getString(R.string.rgb_speed_seconds, speed / 1000)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        satSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                saturation = progress / 100f
                satLabel.text = getString(R.string.rgb_saturation, progress)
                updatePreview(previewStep)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        brightSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                brightness = progress / 100f
                brightLabel.text = getString(R.string.rgb_brightness, progress)
                updatePreview(previewStep)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        previewHandler.post(previewRunnable)

        val dialog = AlertDialog.Builder(ctx)
            .setTitle(R.string.theme_rgb_smooth)
            .setView(root)
            .setNegativeButton(R.string.action_cancel) { _, _ ->
                previewHandler.removeCallbacks(previewRunnable)
            }
            .setPositiveButton(R.string.action_save) { _, _ ->
                previewHandler.removeCallbacks(previewRunnable)
                KeyboardPrefs.setRgbSmoothSpeed(ctx, speed)
                KeyboardPrefs.setRgbSmoothSaturation(ctx, saturation)
                KeyboardPrefs.setRgbSmoothBrightness(ctx, brightness)
                Toast.makeText(ctx, getString(R.string.rgb_settings_saved, getString(R.string.theme_rgb_smooth)), Toast.LENGTH_SHORT).show()
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
            text = getString(R.string.rgb_speed_ms, speed)
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
            text = getString(R.string.rgb_insane_chill)
            textSize = 12f
            gravity = android.view.Gravity.CENTER
            setTextColor(0xFF888888.toInt())
        }
        root.addView(speedHint)

        // Saturation
        val satLabel = TextView(ctx).apply {
            text = getString(R.string.rgb_saturation, (saturation * 100).toInt())
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
            text = getString(R.string.rgb_brightness, (brightness * 100).toInt())
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
                speedLabel.text = getString(R.string.rgb_speed_ms, speed)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        satSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                saturation = 0.3f + (progress / 100f)
                satLabel.text = getString(R.string.rgb_saturation, (saturation * 100).toInt())
                previewBar.background = createRadialPreview(startStep)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        brightSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                brightness = 0.3f + (progress / 100f)
                brightLabel.text = getString(R.string.rgb_brightness, (brightness * 100).toInt())
                previewBar.background = createRadialPreview(startStep)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        previewHandler.post(previewRunnable)

        val dialog = AlertDialog.Builder(ctx)
            .setTitle(R.string.theme_rgb_wild)
            .setView(root)
            .setNegativeButton(R.string.action_cancel) { _, _ ->
                previewHandler.removeCallbacks(previewRunnable)
            }
            .setPositiveButton(R.string.action_save) { _, _ ->
                previewHandler.removeCallbacks(previewRunnable)
                KeyboardPrefs.setRgbWildSpeed(ctx, speed)
                KeyboardPrefs.setRgbWildSaturation(ctx, saturation)
                KeyboardPrefs.setRgbWildBrightness(ctx, brightness)
                Toast.makeText(ctx, getString(R.string.rgb_settings_saved, getString(R.string.theme_rgb_wild)), Toast.LENGTH_SHORT).show()
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
        val sectionOptions = listOf(
            R.string.lp_section_alphabet, R.string.lp_section_numeric,
            R.string.lp_section_my_binds, R.string.lp_section_custom
        ).map { getString(it) }
        spinnerSection.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, sectionOptions).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        val pageAlphabet = dialog.findViewById<LinearLayout>(R.id.pageAlphabetLp)
        val pageNumeric = dialog.findViewById<LinearLayout>(R.id.pageNumericLp)
        val pageMyBinds = dialog.findViewById<ScrollView>(R.id.pageMyBindsLp)
        val myBindsContainer = dialog.findViewById<LinearLayout>(R.id.myBindsContainer)
        val pageCustomText = dialog.findViewById<ScrollView>(R.id.pageCustomTextLp)
        val customTextContainer = dialog.findViewById<LinearLayout>(R.id.customTextContainer)

        val currentRowCount = KeyboardPrefs.getRowCount(this)
        var currentPage = 0

        autosaveDirtySection = null

        // Učitaj s globalnim bindovima; svaka promjena se odmah sprema
        val alphabetBinder = LongPressEditorBinder(
            context = this,
            initial = KeyboardPrefs.loadAlphabetLayoutWithGlobalBinds(this, currentRowCount),
            titleText = getString(R.string.lp_title_alphabet),
            onChanged = { updated ->
                KeyboardPrefs.saveAlphabetLayoutWithGlobalBinds(this, currentRowCount, updated)
                autosaveDirtySection = sectionOptions[0]
            }
        )

        val numericBinder = LongPressEditorBinder(
            context = this,
            initial = KeyboardPrefs.loadNumericLayoutWithGlobalBinds(this, currentRowCount),
            titleText = getString(R.string.lp_title_numeric),
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
                getString(R.string.lp_group_alphabet) to alphabetBinder,
                getString(R.string.lp_group_numeric) to numericBinder
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
                        // Long custom text: preview only, full text stays bound
                        maxLines = 3
                        ellipsize = android.text.TextUtils.TruncateAt.END
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
                    text = getString(R.string.lp_no_binds)
                    textSize = 14f
                    alpha = 0.75f
                    setPadding(0, 8.dp(this@MainActivity), 0, 8.dp(this@MainActivity))
                })
            }
        }

        // Custom text: choose Letters / Numbers, tap a key, type any text and bind it
        var customUseNumeric = false
        var customSelected: com.example.antarakeyboard.model.KeyConfig? = null

        fun buildCustomTextPage() {
            customTextContainer.removeAllViews()
            val binder = if (customUseNumeric) numericBinder else alphabetBinder
            val gap = 8.dp(this)

            val group = android.widget.RadioGroup(this).apply {
                orientation = android.widget.RadioGroup.HORIZONTAL
            }
            val rbLetters = android.widget.RadioButton(this).apply {
                id = View.generateViewId()
                text = getString(R.string.lp_group_alphabet)
                isChecked = !customUseNumeric
            }
            val rbNumbers = android.widget.RadioButton(this).apply {
                id = View.generateViewId()
                text = getString(R.string.lp_group_numeric)
                isChecked = customUseNumeric
            }
            group.addView(rbLetters)
            group.addView(rbNumbers)
            group.setOnCheckedChangeListener { _, checkedId ->
                customUseNumeric = checkedId == rbNumbers.id
                customSelected = null
                buildCustomTextPage()
            }
            customTextContainer.addView(group)

            customTextContainer.addView(TextView(this).apply {
                text = getString(R.string.lp_custom_choose_key)
                alpha = 0.75f
                setPadding(0, gap, 0, gap / 2)
            })

            val preview = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            binder.renderKeyChooser(preview, customSelected) { key ->
                if (binder.isBindable(key)) {
                    customSelected = key
                    buildCustomTextPage()
                }
            }
            customTextContainer.addView(preview)

            val key = customSelected ?: return

            customTextContainer.addView(TextView(this).apply {
                text = getString(R.string.lp_custom_text_for, key.label)
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, gap * 2, 0, gap / 2)
            })

            val input = android.widget.EditText(this).apply {
                hint = getString(R.string.lp_custom_hint)
                // No length limit; long text is only shortened where it is previewed
                isSingleLine = true
            }
            customTextContainer.addView(input, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))

            customTextContainer.addView(Button(this).apply {
                text = getString(R.string.lp_custom_add)
                isAllCaps = false
                setOnClickListener {
                    // Inserted exactly as typed (spaces included); only blank text is refused
                    val text = input.text.toString()
                    if (text.isBlank()) return@setOnClickListener
                    if (binder.addBinding(key, text)) {
                        autosaveDirtySection = sectionOptions[3]
                        buildCustomTextPage()
                    } else {
                        Toast.makeText(this@MainActivity, R.string.lp_custom_already, Toast.LENGTH_SHORT).show()
                    }
                }
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.END })

            // Everything currently bound to this key; ✕ removes it
            val bindings = binder.visibleBindings(key)
            if (bindings.isNotEmpty()) {
                customTextContainer.addView(TextView(this).apply {
                    text = getString(R.string.lp_custom_bound)
                    alpha = 0.75f
                    setPadding(0, gap, 0, gap / 2)
                })
            }
            bindings.forEach { binding ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                row.addView(TextView(this).apply {
                    text = binding
                    textSize = 15f
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(Button(this).apply {
                    text = "✕"
                    contentDescription = getString(R.string.action_delete)
                    setOnClickListener {
                        binder.removeBinding(key, binding)
                        autosaveDirtySection = sectionOptions[3]
                        buildCustomTextPage()
                    }
                })
                customTextContainer.addView(row)
            }
        }

        fun showPage(page: Int) {
            currentPage = page
            pageAlphabet.visibility = if (page == 0) View.VISIBLE else View.GONE
            pageNumeric.visibility = if (page == 1) View.VISIBLE else View.GONE
            pageMyBinds.visibility = if (page == 2) View.VISIBLE else View.GONE
            pageCustomText.visibility = if (page == 3) View.VISIBLE else View.GONE
            if (page == 2) buildMyBindsPage()
            if (page == 3) buildCustomTextPage()
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
            .setTitle(R.string.pick_character)
            .setItems(all.toTypedArray()) { _, which ->
                val picked = all[which]
                onPicked(if (picked == "∅") "" else picked)
            }
            .show()
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
            EdgeActionType.SHIFT to getString(R.string.edge_shift),
            EdgeActionType.BACKSPACE to getString(R.string.edge_backspace),
            EdgeActionType.ENTER to getString(R.string.edge_enter),
            EdgeActionType.SPACE to getString(R.string.edge_space),
            EdgeActionType.EMOJI_PICKER to getString(R.string.edge_emoji),
            EdgeActionType.NONE to getString(R.string.edge_none)
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
            text = getString(R.string.edge_special_chars)
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
            .setTitle(R.string.edge_choose_title)
            .setView(root)
            .setNegativeButton(R.string.action_close, null)
            .show()
    }





    private fun showSavedToastIfDirty() {
        val section = autosaveDirtySection ?: return
        autosaveDirtySection = null
        Toast.makeText(this, getString(R.string.section_saved, section), Toast.LENGTH_SHORT).show()
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
            R.string.layout_section_alphabet,
            R.string.layout_section_numeric,
            R.string.layout_section_side,
            R.string.layout_section_horizontal,
            R.string.layout_section_emoji
        ).map { getString(it) }
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
                text = getString(R.string.emoji_tabs_position)
                textSize = 16f
                setPadding(0, 0, 0, 8.dp(this))
            }
            emojiPickerContainer.addView(tabsLabel)

            val tabsSpinner = Spinner(this)
            val tabsOptions = EmojiPickerStorage.TabsPosition.entries.map { getString(it.labelRes) }
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
                text = getString(R.string.emoji_buttons_side)
                textSize = 16f
                setPadding(0, 0, 0, 8.dp(this))
            }
            emojiPickerContainer.addView(sideLabel)

            val sideSpinner = Spinner(this)
            val sideOptions = EmojiPickerStorage.ButtonsSide.entries.map { getString(it.labelRes) }
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
                text = getString(R.string.emoji_button_order)
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
                        text = "${index + 1}. ${EmojiPickerStorage.getButtonLabel(action)} ${getString(EmojiPickerStorage.getButtonDisplayName(action))}"
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
                val layoutNames = mutableListOf(getString(R.string.saved_layouts_header, savedLayouts.size))
                layoutNames.addAll(savedLayouts.map { getString(R.string.saved_layout_entry, it.name, rowsLabel(it.rowCount)) })
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
            if (!swapped) Toast.makeText(this, R.string.select_two_keys, Toast.LENGTH_SHORT).show()
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
            text = getString(R.string.color_picker_brightness)
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
                Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
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
                Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
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
                Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
            }
        })
        inputsRow.addView(rgbRow)

        root.addView(inputsRow)

        // Initial update
        updatePreview()

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(ScrollView(this).apply { addView(root) })
            .setPositiveButton(R.string.action_done) { _, _ ->
                val finalColor = Color.HSVToColor(floatArrayOf(currentHue, currentSat, currentVal))
                onPicked(finalColor)
            }
            .setNegativeButton(R.string.action_cancel, null)
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


    private fun setupSideButtonsPage(container: LinearLayout, onChanged: () -> Unit) {
        container.removeAllViews()

        val selectedRowCount = KeyboardPrefs.getRowCount(this)

        val title = TextView(this).apply {
            text = getString(R.string.side_title)
            textSize = 18f
            setPadding(0, 0, 0, 10.dp(this))
        }

        val hint = TextView(this).apply {
            text = when (selectedRowCount) {
                4 -> getString(R.string.side_hint_4)
                3 -> getString(R.string.side_hint_3)
                else -> getString(R.string.side_hint_5)
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
                            val rowLabel = getString(R.string.side_row_label, index.coerceAtMost(3) + 1)
                            "$rowLabel • ${slot.label.ifBlank { getString(R.string.edge_none) }}"
                        }

                        else -> slot.label.ifBlank { getString(R.string.edge_none) }
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
        // Programmatic selection below must not count as the user re-picking a theme
        spinnerTheme.onReselected = null

        themeOptions = buildList {
            add(THEME_LIGHT)
            add(THEME_DARK)
            add(THEME_TRANSPARENT)
            if (hasCustomColors() || SavedLayoutStorage.getSavedLayouts(this@MainActivity).isNotEmpty()) add(THEME_CUSTOM)
            add(THEME_RGB_SMOOTH)
            add(THEME_RGB_WILD)
        }

        val themeAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            themeOptions.map { themeLabel(it) }
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerTheme.adapter = themeAdapter

        // Determine current selection
        val isDark = PrefsManager.isDarkMode(this)
        val isCustom = PrefsManager.isCustomTheme(this)

        val currentTheme = when {
            KeyboardPrefs.isRgbSmoothEnabled(this) -> THEME_RGB_SMOOTH
            KeyboardPrefs.isRgbWildEnabled(this) -> THEME_RGB_WILD
            KeyboardPrefs.getBackgroundMode(this) == KeyboardPrefs.BackgroundMode.TRANSPARENT -> THEME_TRANSPARENT
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

        // Picking the active Custom / RGB theme again reopens its popup
        spinnerTheme.onReselected = { position ->
            if (position == currentThemePosition) openThemeSettings()
        }
    }

    /** Translated name for a theme id (the ids themselves are internal constants). */
    private fun themeLabel(theme: String): String = getString(
        when (theme) {
            THEME_LIGHT -> R.string.theme_light
            THEME_DARK -> R.string.theme_dark
            THEME_TRANSPARENT -> R.string.theme_transparent
            THEME_CUSTOM -> R.string.theme_custom
            THEME_RGB_SMOOTH -> R.string.theme_rgb_smooth
            else -> R.string.theme_rgb_wild
        }
    )

    /** Translated key shape name. */
    private fun shapeLabel(shapeName: String): String {
        val shape = runCatching { KeyShape.valueOf(shapeName) }.getOrNull()
        val res = shapeOptions.firstOrNull { it.first == shape }?.second ?: return shapeName.lowercase()
        return getString(res)
    }

    private fun rowsLabel(rows: Int): String = resources.getQuantityString(R.plurals.rows_count, rows, rows)

    /** Popup for the active theme: Saved layouts for Custom, settings for RGB themes. */
    private fun openThemeSettings() {
        when (themeOptions.getOrNull(currentThemePosition)) {
            THEME_RGB_SMOOTH -> showRgbSmoothDialog()
            THEME_RGB_WILD -> showRgbWildDialog()
            THEME_CUSTOM -> showSavedLayoutsPopup { applied -> if (applied) selectCustomTheme() }
        }
    }

    /** Mark the custom theme active (the colors themselves were applied by the saved-layouts popup). */
    private fun selectCustomTheme() {
        PrefsManager.setCustomTheme(this, true)
        KeyboardPrefs.setRgbSmoothEnabled(this, false)
        KeyboardPrefs.setRgbWildEnabled(this, false)
    }

    /** Button under the Theme dropdown: RGB settings for RGB themes, Saved layouts for Custom. */
    private fun updateThemeSettingsButton() {
        when (themeOptions.getOrNull(currentThemePosition)) {
            THEME_RGB_SMOOTH, THEME_RGB_WILD -> {
                btnThemeSettings.text = getString(R.string.theme_btn_rgb_settings)
                btnThemeSettings.visibility = View.VISIBLE
            }
            THEME_CUSTOM -> {
                btnThemeSettings.text = getString(R.string.theme_btn_saved_layouts)
                btnThemeSettings.visibility = View.VISIBLE
            }
            else -> btnThemeSettings.visibility = View.GONE
        }
    }

    private fun applyThemeSelection(theme: String) {
        // Selecting an RGB theme activates it; any other theme turns RGB off
        KeyboardPrefs.setRgbSmoothEnabled(this, theme == THEME_RGB_SMOOTH)
        KeyboardPrefs.setRgbWildEnabled(this, theme == THEME_RGB_WILD)

        // Transparent is a background theme; leaving it brings back the theme background
        if (theme == THEME_TRANSPARENT) {
            KeyboardPrefs.setBackgroundMode(this, KeyboardPrefs.BackgroundMode.TRANSPARENT)
        } else if (KeyboardPrefs.getBackgroundMode(this) == KeyboardPrefs.BackgroundMode.TRANSPARENT) {
            KeyboardPrefs.setBackgroundMode(this, KeyboardPrefs.BackgroundMode.THEME)
        }

        when (theme) {
            THEME_LIGHT -> {
                PrefsManager.setDarkMode(this, false)
                PrefsManager.setCustomTheme(this, false)
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
            THEME_DARK -> {
                PrefsManager.setDarkMode(this, true)
                PrefsManager.setCustomTheme(this, false)
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
            // Keys keep the current Light/Dark colors
            THEME_TRANSPARENT -> PrefsManager.setCustomTheme(this, false)
            THEME_CUSTOM -> selectCustomTheme()
            THEME_RGB_SMOOTH -> showRgbSmoothDialog()
            THEME_RGB_WILD -> showRgbWildDialog()
        }
    }

    private fun hasCustomColors(): Boolean {
        // Check if user has saved custom theme colors
        val prefs = getSharedPreferences(PrefsManager.PREFS_CUSTOM_THEME, MODE_PRIVATE)
        return prefs.getBoolean("has_custom_theme", false)
    }

    private fun saveCurrentColorsAsCustomTheme() {
        val customPrefs = getSharedPreferences(PrefsManager.PREFS_CUSTOM_THEME, MODE_PRIVATE)

        // Save current keyboard colors as custom theme
        // Colors as drawn (theme or custom), so "Last custom colors" restores what was visible
        val isDark = PrefsManager.isDarkMode(this)
        val (keyFill, keyText) = KeyboardPrefs.resolveKeyColors(this, isDark)
        val (space1, space2) = KeyboardPrefs.resolveSpaceColors(this, isDark)
        customPrefs.edit()
            .putBoolean("has_custom_theme", true)
            .putInt("custom_key_fill", keyFill)
            .putInt("custom_key_text", keyText)
            .putInt("custom_background", KeyboardPrefs.resolveKeyboardBackground(this, isDark))
            .putInt("custom_space1_bg", space1)
            .putInt("custom_space2_bg", space2)
            .putInt("custom_enter_bg", KeyboardPrefs.getEnterBg(this))
            .putInt("custom_enter_icon", KeyboardPrefs.getEnterIcon(this))
            .putInt("custom_side_bg", KeyboardPrefs.getSideButtonsBg(this))
            .putInt("custom_side_text", KeyboardPrefs.getSideButtonsTextColor(this))
            .apply()
    }

    private fun loadCustomThemeColors() {
        val customPrefs = getSharedPreferences(PrefsManager.PREFS_CUSTOM_THEME, MODE_PRIVATE)

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
        KeyboardPrefs.setSpaceColors(this, space1Bg, space2Bg, space1Bg == space2Bg)
        KeyboardPrefs.setEnterColors(this, enterBg, enterIcon)
        KeyboardPrefs.setSideButtonsColors(this, sideBg, sideText, false)
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
            .setItems(arrayOf(
                R.string.action_preview, R.string.action_restore, R.string.action_rename, R.string.action_delete
            ).map { getString(it) }.toTypedArray()) { _, which ->
                when (which) {
                    0 -> showSavedLayoutPreview(savedLayout, onApplied = onRestored)
                    1 -> {
                        AlertDialog.Builder(this)
                            .setTitle(R.string.restore_layout_title)
                            .setMessage(R.string.restore_layout_message)
                            .setPositiveButton(R.string.action_restore) { _, _ ->
                                restoreSavedLayout(savedLayout)
                                onRestored()
                            }
                            .setNegativeButton(R.string.action_cancel, null)
                            .show()
                    }
                    2 -> {
                        val input = android.widget.EditText(this).apply { setText(savedLayout.name); selectAll() }
                        AlertDialog.Builder(this).setTitle(R.string.action_rename).setView(input)
                            .setPositiveButton(R.string.action_save) { _, _ ->
                                SavedLayoutStorage.renameLayout(this, savedLayout.id, input.text.toString().trim())
                                onListChanged()
                            }.setNegativeButton(R.string.action_cancel, null).show()
                    }
                    3 -> {
                        AlertDialog.Builder(this).setTitle(R.string.delete_confirm)
                            .setPositiveButton(R.string.action_delete) { _, _ ->
                                SavedLayoutStorage.deleteLayout(this, savedLayout.id)
                                onListChanged()
                            }.setNegativeButton(R.string.action_cancel, null).show()
                    }
                }
                onClosed()
            }
            .setOnCancelListener { onClosed() }
            .show()
    }

    private fun restoreSavedLayout(savedLayout: SavedLayoutStorage.SavedLayout) {
        SavedLayoutStorage.restoreLayout(this, savedLayout)
        // The layout may carry its own key height / row spacing
        setupKeySizeSlider()
        setupRowSpacingSlider()
        preview.shape = KeyboardPrefs.getShape(this)
        spinnerKeyShape.setSelection(shapeOptions.indexOfFirst { it.first == KeyboardPrefs.getShape(this) }.coerceAtLeast(0))
        spinnerRowCount.setSelection(rowCountOptions.indexOf(KeyboardPrefs.getRowCount(this)).coerceAtLeast(0))
        Toast.makeText(this, R.string.layout_restored, Toast.LENGTH_SHORT).show()
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
            text = getString(R.string.saved_layouts_title)
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
                addEntry(getString(R.string.last_custom_colors), getString(R.string.colors_only)) {
                    loadCustomThemeColors()
                    Toast.makeText(this, R.string.custom_colors_applied, Toast.LENGTH_SHORT).show()
                    applied = true
                    dialog.dismiss()
                }
            }

            val savedLayouts = SavedLayoutStorage.getSavedLayouts(this)
            savedLayouts.forEach { saved ->
                addEntry(
                    saved.name,
                    getString(R.string.saved_layout_subtitle, rowsLabel(saved.rowCount), shapeLabel(saved.keyShape), SavedLayoutStorage.formatTimestamp(saved.timestamp))
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
                    text = getString(R.string.no_saved_layouts)
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

    /** [onApplied] runs after colors or the whole layout were applied (makes Custom the active theme). */
    private fun showSavedLayoutPreview(
        savedLayout: SavedLayoutStorage.SavedLayout,
        onApplied: () -> Unit
    ) {
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

        // Transparent background can't be seen in the preview, so say it explicitly
        if (!savedLayout.backgroundUseTheme && Color.alpha(savedLayout.backgroundColor) == 0) {
            root.addView(TextView(this).apply {
                text = getString(R.string.transparent_background)
                textSize = 13f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, 0, 0, 8.dp(this@MainActivity))
            })
        }

        // Info section
        val infoText = TextView(this).apply {
            text = getString(R.string.preview_info, shapeLabel(savedLayout.keyShape), savedLayout.rowCount, SavedLayoutStorage.formatTimestamp(savedLayout.timestamp))
            textSize = 12f
            setPadding(0, 0, 0, 12.dp(this))
        }
        root.addView(infoText)

        // Colors section
        val colorsLabel = TextView(this).apply {
            text = getString(R.string.preview_colors_label)
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

        // Preview: real keys with the saved shape, row count, key height, row spacing and colors
        fun rebuildPreview() {
            previewContainer.removeAllViews()
            previewContainer.setBackgroundColor(currentBgColor)

            val savedShape = runCatching { KeyShape.valueOf(savedLayout.keyShape) }.getOrDefault(KeyShape.HEX)
            // Same per-layout rules as the keyboard (3-row hexagons are tall, 3-row triangles taller)
            val shape = if (savedLayout.rowCount == 3 && savedShape == KeyShape.HEX) KeyShape.HEX_TALL else savedShape
            val stretch = KeyScale.clamp(savedLayout.keyScale ?: KeyScale.DEFAULT) *
                (if (savedLayout.rowCount == 3 && savedShape == KeyShape.TRIANGLE) KeyScale.THREE_ROW_TRIANGLE_DEFAULT else 1f)
            val rowSpacing = KeyScale.clampRowSpacing(savedLayout.rowSpacingDp ?: 0).dp(this)

            val rows = alphabetLayout.rows.map { row -> row.keys.filter { it.label.isNotEmpty() } }
            val maxKeys = rows.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1
            val availW = (resources.displayMetrics.widthPixels * 0.95f).toInt() - 48.dp(this)
            val keyW = availW / maxKeys
            val baseH = if (shape == KeyShape.HEX_TALL) (keyW * 1.6f).toInt() else keyW
            val isHex = shape == KeyShape.HEX || shape == KeyShape.HEX_TALL
            // Honeycomb rows interlock by the hexagon tips (a quarter of the drawn height)
            val rowOverlap = when {
                isHex -> (baseH * 0.96f * stretch * 0.25f).toInt() - 1.dp(this)
                shape == KeyShape.TRIANGLE -> (baseH * 0.15f * stretch).toInt()
                else -> -(2.dp(this))
            }

            rows.forEachIndexed { rowIndex, keys ->
                val rowLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    clipChildren = false
                    clipToPadding = false
                    // Honeycomb: every second row shifted by half a key
                    if (isHex && rowIndex % 2 == 1) setPadding(keyW / 2, 0, 0, 0)
                }

                keys.forEachIndexed { keyIndex, key ->
                    val (fill, textColor) = when (key.label) {
                        " " -> currentSpaceBg to currentKeyText
                        "↵" -> currentEnterBg to currentEnterIcon
                        else -> currentKeyFill to currentKeyText
                    }
                    rowLayout.addView(KeyView(this).apply {
                        text = if (key.label == " ") "" else key.label
                        isAllCaps = false
                        gravity = Gravity.CENTER
                        includeFontPadding = false
                        textSize = 11f
                        this.shape = shape
                        forceSquare = shape != KeyShape.TRIANGLE && shape != KeyShape.HEX_TALL
                        heightStretch = stretch
                        triangleFlipped = (rowIndex + keyIndex) % 2 == 1
                        customBgColor = fill
                        setTextColor(textColor)
                    }, LinearLayout.LayoutParams(keyW, baseH))
                }

                previewContainer.addView(rowLayout, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { if (rowIndex > 0) topMargin = -rowOverlap + rowSpacing })
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

            var swatchColor = color
            container.setOnClickListener {
                showAdvancedColorPicker(label, swatchColor) { picked ->
                    swatchColor = picked
                    swatch.setBackgroundColor(picked)
                    onColorChanged(picked)
                    rebuildPreview()
                }
            }

            colorsRow.addView(container)
            return swatch
        }

        addColorSwatch(currentKeyFill, getString(R.string.swatch_keys)) { currentKeyFill = it }
        addColorSwatch(currentKeyText, getString(R.string.swatch_text)) { currentKeyText = it }
        addColorSwatch(currentSpaceBg, getString(R.string.swatch_space)) { currentSpaceBg = it }
        addColorSwatch(currentEnterBg, getString(R.string.swatch_enter)) { currentEnterBg = it }
        addColorSwatch(currentBgColor, getString(R.string.swatch_bg)) { currentBgColor = it }

        root.addView(colorsRow)

        // Layout preview section
        val layoutLabel = TextView(this).apply {
            text = getString(R.string.preview_layout_label)
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
            clipChildren = false
            clipToPadding = false
        }
        rebuildPreview()
        root.addView(previewContainer)

        // Buttons
        val buttonsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.END
            setPadding(0, 16.dp(this), 0, 0)
        }

        // Saved layout with the colors edited in this preview
        fun editedLayout() = savedLayout.copy(
            keyFill = currentKeyFill,
            keyText = currentKeyText,
            space1Bg = currentSpaceBg,
            space2Bg = currentSpaceBg,
            enterBg = currentEnterBg,
            enterIcon = currentEnterIcon,
            backgroundColor = currentBgColor,
            // A color picked here is a concrete color, not "use theme"
            backgroundUseTheme = savedLayout.backgroundUseTheme && currentBgColor == savedLayout.backgroundColor
        )

        val applyColorsBtn = Button(this).apply {
            text = getString(R.string.apply_colors)
            isAllCaps = false
            setOnClickListener {
                // Keep the edits in the saved entry, then apply its colors to the keyboard
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
                SavedLayoutStorage.applyColors(this@MainActivity, editedLayout())
                Toast.makeText(this@MainActivity, R.string.colors_applied, Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                onApplied()
            }
        }

        val restoreBtn = Button(this).apply {
            text = getString(R.string.restore_layout_button)
            isAllCaps = false
            setOnClickListener {
                restoreSavedLayout(editedLayout())
                dialog.dismiss()
                onApplied()
            }
        }

        val closeBtn = Button(this).apply {
            text = getString(R.string.action_close)
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