package com.example.antarakeyboard

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
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
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.os.LocaleListCompat
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.core.view.isVisible
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
import com.example.antarakeyboard.data.KeyboardPrefs
import com.example.antarakeyboard.data.PrefsManager
import com.example.antarakeyboard.model.EdgeActionType
import com.example.antarakeyboard.model.EdgeSlot
import com.example.antarakeyboard.model.KeyShape
import com.example.antarakeyboard.ui.ColorsDialog
import com.example.antarakeyboard.extensions.themeColor
import com.example.antarakeyboard.ui.LayoutEditorDialog
import com.example.antarakeyboard.ui.LongPressEditorDialog
import com.example.antarakeyboard.ui.ReselectSpinner
import com.example.antarakeyboard.ui.RgbSettingsDialog
import com.example.antarakeyboard.ui.SideOffsetsDialog
import com.example.antarakeyboard.ui.ShapePreviewView
import com.example.antarakeyboard.ui.defaultHorizontalCenterLayout
import android.graphics.Color
import com.example.antarakeyboard.data.SavedLayoutStorage
import com.example.antarakeyboard.data.LastCustomColors
import com.example.antarakeyboard.ui.SavedLayoutsDialog

class MainActivity : AppCompatActivity() {

    private lateinit var preview: ShapePreviewView
    private lateinit var spinnerKeyShape: Spinner
    private lateinit var spinnerRowCount: Spinner
    private lateinit var spinnerTheme: ReselectSpinner
    private lateinit var btnThemeSettings: Button
    private lateinit var btnSavedLayouts: Button
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

        findViewById<Button>(R.id.btnSideOffsets).setOnClickListener {
            showSideOffsetsDialog()
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
            R.layout.main_spinner_item,
            shapeOptions.map { getString(it.second) }
        ).apply {
            setDropDownViewResource(R.layout.main_spinner_dropdown_item)
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
            R.layout.main_spinner_item,
            rowCountOptions.map { rowsLabel(it) }
        ).apply {
            setDropDownViewResource(R.layout.main_spinner_dropdown_item)
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

        // Vibration and word suggestions: on / off switches
        findViewById<SwitchCompat>(R.id.switchVibration).apply {
            isChecked = KeyboardPrefs.isVibrationEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, on -> KeyboardPrefs.setVibrationEnabled(this@MainActivity, on) }
        }
        findViewById<SwitchCompat>(R.id.switchSuggestions).apply {
            isChecked = KeyboardPrefs.isSuggestionsEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, on -> KeyboardPrefs.setSuggestionsEnabled(this@MainActivity, on) }
        }

        setupLanguageSpinner()

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
        btnSavedLayouts = findViewById(R.id.btnSavedLayouts)
        btnSavedLayouts.setOnClickListener { openSavedLayouts() }
        setupThemeSpinner()

        bindLPButton.setOnClickListener {
            openLongPressEditorDialog()
        }

        // Setup Reset Spinner
        val resetOptions = listOf(
            R.string.reset_hint, R.string.reset_colors, R.string.reset_layout, R.string.reset_all
        ).map { getString(it) }
        // Closed: "Reset…"; in the open list the first entry reads "None" (leaves it unchanged)
        val resetNone = getString(R.string.reset_none)
        spinnerReset.adapter = object : ArrayAdapter<String>(this, R.layout.main_spinner_item, resetOptions) {
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                if (position == 0) (view as? TextView)?.text = resetNone
                return view
            }
        }.apply {
            setDropDownViewResource(R.layout.main_spinner_dropdown_item)
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
        // A reset or an import may have added saved layouts
        updateThemeSettingsButton()
    }

    private fun setupLanguageSpinner() {
        val spinner = findViewById<Spinner>(R.id.spinnerLanguage)
        val labels = languageTags.map { tag ->
            if (tag.isEmpty()) getString(R.string.language_system_default) else AppLanguages.nativeName(tag)
        }
        spinner.adapter = ArrayAdapter(this, R.layout.main_spinner_item, labels).apply {
            setDropDownViewResource(R.layout.main_spinner_dropdown_item)
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
     * The current state is saved as a backup in Saved layouts first; plain theme colors are
     * not kept (the backup is then layout only, and Reset colors makes no backup at all).
     */
    private fun performReset(colors: Boolean, layout: Boolean) {
        val currentRowCount = KeyboardPrefs.getRowCount(this)
        val hasCustomColors = !KeyboardPrefs.colorsAreThemeDefaults(this)

        if (layout || hasCustomColors) {
            val timestamp = SavedLayoutStorage.formatTimestamp(System.currentTimeMillis())
            SavedLayoutStorage.saveCurrentLayout(
                this, getString(R.string.backup_name, timestamp), includeColors = hasCustomColors
            )
        }

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

            // Key height, row spacing and side button position sliders back to default
            KeyboardPrefs.resetKeySizing(this)
            setupKeySizeSlider()
            setupRowSpacingSlider()
        }

        if (colors) {
            val isDarkTheme = PrefsManager.isDarkMode(this)

            // Keep the current colors as "Last custom colors" (only if they are custom)
            if (hasCustomColors) LastCustomColors.save(this)

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
        // The backup may be the first saved layout
        updateThemeSettingsButton()
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

    /** Side button positions: autosaving popup with four offset sliders. */
    private fun showSideOffsetsDialog() {
        autosaveDirtySection = null
        val section = getString(R.string.main_side_offsets)
        SideOffsetsDialog.show(
            this,
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

    private fun openLongPressEditorDialog() {
        autosaveDirtySection = null
        LongPressEditorDialog(
            this,
            markDirty = { autosaveDirtySection = it },
            flushSavedToast = ::showSavedToastIfDirty
        ).show()
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
        autosaveDirtySection = null
        LayoutEditorDialog(this, LayoutEditorHost()).show()
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
            R.layout.main_spinner_item,
            themeOptions.map { themeLabel(it) }
        ).apply {
            setDropDownViewResource(R.layout.main_spinner_dropdown_item)
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
            THEME_CUSTOM -> openSavedLayouts()
        }
    }

    /** Saved layouts popup; applied colors make Custom the active theme. */
    private fun openSavedLayouts() {
        savedLayoutsDialog.showSavedLayoutsPopup { applied ->
            if (applied) selectCustomTheme()
            // Colors applied, or entries deleted: dropdown and buttons follow
            setupThemeSpinner()
        }
    }

    /** Mark the custom theme active (the colors themselves were applied by the saved-layouts popup). */
    private fun selectCustomTheme() {
        PrefsManager.setCustomTheme(this, true)
        KeyboardPrefs.setRgbSmoothEnabled(this, false)
        KeyboardPrefs.setRgbWildEnabled(this, false)
    }

    /**
     * Buttons under the Theme dropdown: RGB settings while an RGB theme is active, and Saved
     * layouts whenever there is something to restore (a saved layout or "Last custom colors"),
     * whatever the theme – side by side when both show.
     */
    private fun updateThemeSettingsButton() {
        val rgb = themeOptions.getOrNull(currentThemePosition).let { it == THEME_RGB_SMOOTH || it == THEME_RGB_WILD }
        val saved = LastCustomColors.exists(this) || SavedLayoutStorage.getSavedLayouts(this).isNotEmpty()
        btnThemeSettings.isVisible = rgb
        btnSavedLayouts.isVisible = saved
        findViewById<View>(R.id.themeButtonsGap).isVisible = rgb && saved
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

    private fun restoreSavedLayout(savedLayout: SavedLayoutStorage.SavedLayout, withColors: Boolean) {
        SavedLayoutStorage.restoreLayout(this, savedLayout, withColors)
        // Restored colors make Custom the active theme
        if (withColors && savedLayout.includesColors) {
            selectCustomTheme()
            setupThemeSpinner()
        }
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
        override fun restoreSavedLayout(savedLayout: SavedLayoutStorage.SavedLayout, withColors: Boolean) =
            this@MainActivity.restoreSavedLayout(savedLayout, withColors)
        override fun onColorsApplied() {
            selectCustomTheme()
            setupThemeSpinner()
        }
    }

    private inner class LayoutEditorHost : LayoutEditorDialog.Host {
        override val savedLayoutsDialog get() = this@MainActivity.savedLayoutsDialog
        override fun markDirty(section: String) { autosaveDirtySection = section }
        override fun clearDirty() { autosaveDirtySection = null }
        override fun flushSavedToast() = showSavedToastIfDirty()
        override fun rowsLabel(rows: Int) = this@MainActivity.rowsLabel(rows)
    }
}
