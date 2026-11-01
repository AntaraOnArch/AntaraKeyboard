package com.example.antarakeyboard

import android.annotation.SuppressLint
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
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.os.LocaleListCompat
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.core.view.isEmpty
import androidx.core.view.isNotEmpty
import com.example.antarakeyboard.data.SettingsBackup
import com.example.antarakeyboard.data.OpenSourceLicenses
import com.example.antarakeyboard.service.KeyScale
import kotlin.math.roundToInt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.antarakeyboard.data.AppLanguages
import com.example.antarakeyboard.data.AppLanguageSettings
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
import com.example.antarakeyboard.ui.ColorsDialog
import com.example.antarakeyboard.extensions.themeColor
import com.example.antarakeyboard.ui.LayoutEditorBinder
import com.example.antarakeyboard.ui.LongPressEditorBinder
import com.example.antarakeyboard.ui.ReselectSpinner
import com.example.antarakeyboard.ui.RgbSettingsDialog
import com.example.antarakeyboard.ui.ShapePreviewView
import com.example.antarakeyboard.ui.defaultHorizontalCenterLayout
import android.graphics.Color
import com.example.antarakeyboard.R
import com.example.antarakeyboard.data.EmojiPickerStorage
import com.example.antarakeyboard.data.SavedLayoutStorage
import com.example.antarakeyboard.data.LastCustomColors
import com.example.antarakeyboard.ui.SavedLayoutsDialog

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

        // Android < 13: a language picked on the keyboard is stored in our prefs only;
        // apply it to the app (AppCompat recreates the activity once if it differs)
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            val wanted = PrefsManager.getAppLanguage(this)
            val applied = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            if (wanted != applied) {
                AppCompatDelegate.setApplicationLocales(
                    if (wanted.isEmpty()) LocaleListCompat.getEmptyLocaleList()
                    else LocaleListCompat.forLanguageTags(wanted)
                )
            }
        }

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
    // Text built from data (names, key labels, color values), not from UI strings
    @SuppressLint("SetTextI18n")
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
    // Text built from data (names, key labels, color values), not from UI strings
    @SuppressLint("SetTextI18n")
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
    private val languageTags = listOf("") + AppLanguages.TAGS
    private var shownLanguageTag = ""

    /** Shows the current app language (it may have been changed from the keyboard). */
    private fun syncLanguageSpinner() {
        val current = AppLanguages.match(AppLanguageSettings.selectedTag(this)) ?: ""
        shownLanguageTag = current
        findViewById<Spinner>(R.id.spinnerLanguage)
            .setSelection(languageTags.indexOf(current).coerceAtLeast(0), false)
    }

    override fun onResume() {
        super.onResume()
        // A language changed in Android settings: the editors must show its letters already
        KeyboardPrefs.ensureDefaultLongPress(this)
        syncLanguageSpinner()
    }

    private fun setupLanguageSpinner() {
        val spinner = findViewById<Spinner>(R.id.spinnerLanguage)
        val labels = languageTags.map { tag ->
            if (tag.isEmpty()) getString(R.string.language_system_default) else AppLanguages.nativeName(tag)
        }
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        // One shared setting with the keyboard's dual-space language picker. The spinner must
        // not restore its own old position on recreate (that would re-apply the old language
        // after the keyboard changed it); onResume syncs it from the setting instead.
        spinner.isSaveEnabled = false
        syncLanguageSpinner()

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val tag = languageTags[position]
                if (tag == shownLanguageTag) return
                shownLanguageTag = tag
                // App language + matching keyboard script (Cyrillic languages type Cyrillic)
                AppLanguageSettings.select(this@MainActivity, tag)
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

        // Save the current layout (and colors) as a backup before the reset
        val timestamp = SavedLayoutStorage.formatTimestamp(System.currentTimeMillis())
        SavedLayoutStorage.saveCurrentLayout(this, getString(R.string.backup_name, timestamp))

        if (layout) {
            // Resetiraj horizontal center layout
            KeyboardPrefs.clearHorizontalCenterLayoutForRowCount(this, currentRowCount)
            KeyboardPrefs.saveHorizontalCenterLayoutForRowCount(this, currentRowCount, defaultHorizontalCenterLayout)

            // Main layouts back to default, but KEEP the global binds
            KeyboardPrefs.clearAlphabetLayoutForRowCount(this, currentRowCount)
            KeyboardPrefs.clearNumericLayoutForRowCount(this, currentRowCount)

            val alphabetWithBinds = KeyboardPrefs.loadAlphabetLayoutWithGlobalBinds(this, currentRowCount)
            val numericWithBinds = KeyboardPrefs.loadNumericLayoutWithGlobalBinds(this, currentRowCount)

            KeyboardPrefs.saveAlphabetLayoutForRowCount(this, currentRowCount, alphabetWithBinds)
            KeyboardPrefs.saveNumericLayoutForRowCount(this, currentRowCount, numericWithBinds)

            // Side buttons back to default for the current row count
            resetSideButtonsForRowCount(currentRowCount)

            // Key height and row spacing sliders back to default
            KeyboardPrefs.resetKeySizing(this)
            setupKeySizeSlider()
            setupRowSpacingSlider()
        }

        if (colors) {
            val isDarkTheme = PrefsManager.isDarkMode(this)

            // Save the current colors as the custom theme before the reset
            LastCustomColors.save(this)

            // ALL colors back to the theme defaults
            resetAllColorsToThemeDefault(isDarkTheme)

            // Clear the custom theme flag so the keyboard uses the default colors, and turn RGB off
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

        // Clear a possible old per-key entry for " "
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

        // 6. Clear the per-key colors
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

    private fun openColorsDialog() {
        autosaveDirtySection = null
        ColorsDialog(
            this,
            markDirty = { autosaveDirtySection = it },
            flushSavedToast = ::showSavedToastIfDirty,
            // Background changes can leave the Transparent theme
            onDismiss = ::setupThemeSpinner
        ).show()
    }

    /** RGB Smooth / RGB Wild settings: autosaving popup with a live preview. */
    private fun showRgbSettingsDialog(wild: Boolean) {
        autosaveDirtySection = null
        val section = getString(if (wild) R.string.theme_rgb_wild else R.string.theme_rgb_smooth)
        RgbSettingsDialog.show(
            this,
            wild,
            onChanged = { autosaveDirtySection = section },
            onDismiss = { showSavedToastIfDirty() }
        )
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
        // Global binds are always current (every editor saves them); extracting them from the
        // stored layout here would bring back an old snapshot, e.g. a previous language's letters

        // Set the new row count
        KeyboardPrefs.setRowCount(this, rowCount)

        // Load the default layouts for the new row count with the global binds applied
        val alphabetWithBinds = KeyboardPrefs.loadAlphabetLayoutWithGlobalBinds(this, rowCount)
        val numericWithBinds = KeyboardPrefs.loadNumericLayoutWithGlobalBinds(this, rowCount)

        KeyboardPrefs.saveAlphabetLayoutForRowCount(this, rowCount, alphabetWithBinds)
        KeyboardPrefs.saveNumericLayoutForRowCount(this, rowCount, numericWithBinds)

        resetSideButtonsForRowCount(rowCount)
    }

    // Text built from data (names, key labels, color values), not from UI strings
    @SuppressLint("SetTextI18n")
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

        // Load with the global binds; every change is saved immediately
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

    // Text built from data (names, key labels, color values), not from UI strings
    @SuppressLint("SetTextI18n")
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
                    // The symbol brings its own binds (shared by all row counts); otherwise the
                    // save would store it as "no binds" and wipe them
                    key.longPressBindings.addAll(
                        GlobalLongPressStorage.loadNumericBinds(this).getOrElse(picked) { emptyList() }
                    )
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
                            v.startDragAndDrop(data, shadow, null, 0)
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
                        savedLayoutsDialog.showSavedLayoutActions(
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

            v.startDragAndDrop(data, shadow, null, 0)
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

    private fun setupThemeSpinner() {
        // Programmatic selection below must not count as the user re-picking a theme
        spinnerTheme.onReselected = null

        themeOptions = buildList {
            add(THEME_LIGHT)
            add(THEME_DARK)
            add(THEME_TRANSPARENT)
            if (LastCustomColors.exists(this@MainActivity) || SavedLayoutStorage.getSavedLayouts(this@MainActivity).isNotEmpty()) add(THEME_CUSTOM)
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
                    savedLayoutsDialog.showSavedLayoutsPopup { applied ->
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
            THEME_RGB_SMOOTH -> showRgbSettingsDialog(wild = false)
            THEME_RGB_WILD -> showRgbSettingsDialog(wild = true)
            THEME_CUSTOM -> savedLayoutsDialog.showSavedLayoutsPopup { applied -> if (applied) selectCustomTheme() }
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
            THEME_RGB_SMOOTH -> showRgbSettingsDialog(wild = false)
            THEME_RGB_WILD -> showRgbSettingsDialog(wild = true)
        }
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

    private val savedLayoutsDialog by lazy { SavedLayoutsDialog(this, SavedLayoutsHost()) }

    private inner class SavedLayoutsHost : SavedLayoutsDialog.Host {
        override fun rowsLabel(rows: Int) = this@MainActivity.rowsLabel(rows)
        override fun shapeLabel(shapeName: String) = this@MainActivity.shapeLabel(shapeName)
        override fun restoreSavedLayout(savedLayout: SavedLayoutStorage.SavedLayout) =
            this@MainActivity.restoreSavedLayout(savedLayout)
    }
}
