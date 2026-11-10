package com.example.antarakeyboard.ui.tutorial

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isEmpty
import androidx.core.view.isVisible
import com.example.antarakeyboard.R
import com.example.antarakeyboard.data.KeyboardPrefs
import com.example.antarakeyboard.data.LongPressPresets
import com.example.antarakeyboard.data.ScriptMapper
import com.example.antarakeyboard.data.TutorialPrefs
import com.example.antarakeyboard.extensions.dp
import com.example.antarakeyboard.ui.tutorial.TutorialDemoView.Demo

/**
 * Second part of the tutorial (after [SetupGuide]): the user practices each gesture with the real
 * keyboard in a text field – capitals, Shift, punctuation, delete, restore, long press, emoji. Each
 * step shows an animated demo and moves on by itself once the field's text shows the gesture was
 * done ([TutorialChecks]). Then one page with the animated feature demos (information only, no
 * practice) and a note that Help (?) shows the tutorial again. Skip ends it at any time.
 */
class TutorialActivity : AppCompatActivity() {

    private companion object {
        const val KEY_PAGE = "page"
        const val KEY_ROUNDS = "restore_rounds"

        /** "Well done!" stays this long before the next step. */
        const val SUCCESS_MS = 1000L

        val STEPS = TutorialStep.entries
        val FEATURES = listOf(Demo.DELETE, Demo.SWAP, Demo.BIND_EMOJI, Demo.THEMES)

        /** The practice steps, then the features page. */
        val PAGES = STEPS.size + 1
    }

    private lateinit var input: EditText
    private lateinit var demo: TutorialDemoView
    private lateinit var wellDone: TextView
    private lateinit var notActive: View
    private val watcher = KeyboardStatusWatcher(this) { showStatus(it) }

    private var page = 0
    private var advancing = false
    private var settingText = false
    private val advance = Runnable { next() }
    private var longPressLetters = emptySet<String>()
    /** Default long-press letters of the language, per key (e.g. c → č ć, d → đ …). */
    private var examples = emptyList<Pair<String, List<String>>>()
    private val restoreCounter = RestoreCounter()
    private var preset = ""
    private var demoWord = "Antara"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Opening the practice counts as seen: Help (?) brings it back any time
        TutorialPrefs.setDone(this)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_tutorial)
        val root = findViewById<View>(R.id.tutorialRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            // The keyboard takes the bottom: the practice field stays right above it
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            val pad = 16.dp(this)
            v.setPadding(pad + bars.left, pad + bars.top, pad + bars.right, pad + bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        input = findViewById(R.id.tutorialInput)
        demo = findViewById(R.id.tutorialDemo)
        wellDone = findViewById(R.id.tutorialWellDone)
        notActive = findViewById(R.id.tutorialNotActive)
        setupKeyboardData()

        findViewById<Button>(R.id.btnTutorialSkip).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnTutorialSkipStep).setOnClickListener { next() }
        findViewById<Button>(R.id.btnTutorialNext).setOnClickListener {
            if (page == PAGES - 1) finish() else next()
        }

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (STEPS.getOrNull(page) == TutorialStep.RESTORE && !settingText && !advancing) {
                    restoreCounter.onText(s?.toString().orEmpty())
                    showRounds()
                }
                check()
            }
        })

        if (savedInstanceState != null) {
            page = savedInstanceState.getInt(KEY_PAGE)
            // The field gets its text back after onCreate (seen as typing); a round in progress starts over
            restoreCounter.reset("", savedInstanceState.getInt(KEY_ROUNDS))
        }
        // After a rotation the field keeps its own text (restored after onCreate)
        showPage(fresh = savedInstanceState == null)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // A step finished just before the rotation is recognized again from the restored text
        outState.putInt(KEY_PAGE, page)
        outState.putInt(KEY_ROUNDS, restoreCounter.rounds)
    }

    override fun onStart() {
        super.onStart()
        watcher.start()
    }

    override fun onStop() {
        watcher.stop()
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Back from the input method picker
        if (hasFocus) watcher.refresh()
    }

    /** Keys, script and long-press letters of the user's keyboard, so demos and hints match it. */
    private fun setupKeyboardData() {
        preset = KeyboardPrefs.getSelectedLongPressPreset(this)
        val layout = KeyboardPrefs.loadAlphabetLayoutWithGlobalBinds(this, KeyboardPrefs.getLayoutId(this))
        val keys = layout.rows.flatMap { it.keys }
        longPressLetters = TutorialChecks.longPressLetters(
            bindings = keys.flatMap { LongPressPresets.visibleFor(preset, it.longPressBindings) },
            keyOutputs = keys.map { ScriptMapper.map(preset, it.label) }
        )

        // Default long-press keys of the language, e.g. c → č ć, d → đ, s → š, z → ž
        examples = LongPressPresets.defaultsFor(preset, KeyboardPrefs.languageTag(this)).entries
            .filter { (key, letters) -> key == key.lowercase() && letters.isNotEmpty() }
            .map { (key, letters) -> ScriptMapper.map(preset, key) to letters }

        // The sample word in the language's script (Antara / Антара / Αντάρα)
        demoWord = getString(R.string.tutorial_sample_text)
        configure(demo)
    }

    private fun configure(view: TutorialDemoView) {
        val preset = preset
        view.letter = { ScriptMapper.map(preset, it) }
        view.shape = KeyboardPrefs.getShape(this).drawShape
        // The long-press demo shows the first of them
        examples.firstOrNull()?.let { (key, letters) ->
            view.longPressKey = key
            view.longPressLetters = letters
        }
        view.word = demoWord
        view.themeNames = listOf(R.string.theme_light, R.string.theme_dark, R.string.theme_custom, R.string.theme_rgb_smooth)
            .map { getString(it) }
    }

    private fun showPage(fresh: Boolean) {
        advancing = false
        wellDone.visibility = View.INVISIBLE
        val step = STEPS.getOrNull(page)
        if (step != null) showStep(step, fresh) else showFeatures()
    }

    private fun showStep(step: TutorialStep, fresh: Boolean) {
        val (title, text) = when (step) {
            TutorialStep.CAPITALS -> R.string.help_q_capitals to R.string.tutorial_capitals_text
            TutorialStep.CAPS_LOCK -> R.string.tutorial_caps_title to R.string.tutorial_caps_text
            TutorialStep.PUNCTUATION -> R.string.help_q_punctuation to R.string.tutorial_punctuation_text
            TutorialStep.RESTORE -> R.string.tutorial_restore_title to R.string.tutorial_restore_text
            TutorialStep.LONG_PRESS -> R.string.help_q_special to R.string.tutorial_long_press_text
            TutorialStep.EMOJI -> R.string.help_q_emoji to R.string.tutorial_emoji_text
        }
        findViewById<TextView>(R.id.tutorialProgress).text = getString(R.string.tutorial_progress, page + 1, STEPS.size)
        findViewById<TextView>(R.id.tutorialTitle).setText(title)
        findViewById<TextView>(R.id.tutorialText).apply {
            isVisible = true
            setText(text)
        }
        val exampleView = findViewById<TextView>(R.id.tutorialExample)
        // Every key with default long-press letters in this language, not just one example
        val showExamples = step == TutorialStep.LONG_PRESS && examples.isNotEmpty()
        exampleView.isVisible = showExamples
        if (showExamples) {
            exampleView.text = getString(R.string.tutorial_long_press_example,
                examples.joinToString("   ") { (key, letters) -> "$key → ${letters.joinToString(" ")}" })
        }
        if (step == TutorialStep.RESTORE) showRounds()
        demo.demo = Demo.valueOf(step.name)
        demo.isVisible = true
        findViewById<View>(R.id.tutorialFeatures).isVisible = false

        input.isVisible = true
        findViewById<View>(R.id.btnTutorialSkipStep).isVisible = true
        findViewById<View>(R.id.btnTutorialNext).isVisible = false
        showStatus(KeyboardStatusWatcher.status(this))

        if (fresh) {
            // Every step starts from an empty field
            settingText = true
            input.setText("")
            settingText = false
            if (step == TutorialStep.RESTORE) {
                restoreCounter.reset("")
                showRounds()
            }
        }
        input.requestFocus()
        WindowCompat.getInsetsController(window, input).show(WindowInsetsCompat.Type.ime())
    }

    /** Information only: every feature with its demo on one scrolling page, then Done. */
    private fun showFeatures() {
        findViewById<TextView>(R.id.tutorialProgress).text = ""
        findViewById<TextView>(R.id.tutorialTitle).setText(R.string.tutorial_features_title)
        findViewById<View>(R.id.tutorialText).isVisible = false
        findViewById<View>(R.id.tutorialExample).isVisible = false
        demo.isVisible = false
        val container = findViewById<LinearLayout>(R.id.tutorialFeatures)
        container.isVisible = true
        if (container.isEmpty()) buildFeatures(container)

        // Nothing to type any more: hide the field and the keyboard
        input.clearFocus()
        WindowCompat.getInsetsController(window, input).hide(WindowInsetsCompat.Type.ime())
        input.isVisible = false
        notActive.isVisible = false
        findViewById<View>(R.id.btnTutorialSkipStep).isVisible = false
        findViewById<Button>(R.id.btnTutorialNext).apply {
            isVisible = true
            setText(R.string.tutorial_done)
        }
    }

    private fun buildFeatures(container: LinearLayout) {
        // The texts name the main screen's buttons with their own labels
        FEATURES.forEach { feature ->
            val (title, body) = when (feature) {
                // Deleting a whole word: information only (practice is the Restore step)
                Demo.DELETE -> R.string.tutorial_delete_title to getString(R.string.tutorial_delete_text)
                Demo.SWAP -> R.string.tutorial_swap_title to
                    getString(R.string.tutorial_swap_text, getString(R.string.main_set_layout))
                Demo.BIND_EMOJI -> R.string.tutorial_bind_title to
                    getString(R.string.tutorial_bind_text, getString(R.string.main_bind_long_press))
                else -> R.string.tutorial_themes_title to
                    getString(R.string.tutorial_themes_text, getString(R.string.main_theme), getString(R.string.main_colors))
            }
            container.addView(TextView(this).apply {
                setText(title)
                textSize = 17f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, 16.dp(this@TutorialActivity), 0, 0)
                ViewCompat.setAccessibilityHeading(this, true)
            })
            container.addView(TextView(this).apply {
                text = body
                textSize = 15f
                setPadding(0, 6.dp(this@TutorialActivity), 0, 0)
            })
            container.addView(TutorialDemoView(this).apply {
                configure(this)
                demo = feature
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 140.dp(this)
            ).apply { topMargin = 8.dp(this@TutorialActivity) })
        }
        container.addView(TextView(this).apply {
            text = getString(R.string.tutorial_help_note, getString(R.string.help_title))
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 20.dp(this@TutorialActivity), 0, 0)
        })
    }

    private fun check() {
        val step = STEPS.getOrNull(page) ?: return
        if (settingText || advancing) return
        if (!TutorialChecks.isDone(step, input.text.toString(), restoreCounter.rounds, longPressLetters)) return

        advancing = true
        wellDone.visibility = View.VISIBLE
        wellDone.scaleX = 0.6f
        wellDone.scaleY = 0.6f
        wellDone.animate().scaleX(1f).scaleY(1f).setDuration(250).start()
        // Live region: setting the text makes TalkBack read it
        wellDone.setText(R.string.tutorial_well_done)
        wellDone.postDelayed(advance, SUCCESS_MS)
    }

    /** Restore step: rounds done so far, e.g. 1 / 3. */
    private fun showRounds() {
        findViewById<TextView>(R.id.tutorialExample).apply {
            isVisible = true
            text = getString(R.string.tutorial_rounds, restoreCounter.rounds, TutorialChecks.RESTORE_ROUNDS)
        }
    }

    private fun next() {
        wellDone.removeCallbacks(advance)
        page++
        showPage(fresh = true)
    }

    /** Practice needs Antara as the active keyboard: offer Enable / Choose while it isn't. */
    private fun showStatus(status: KeyboardStatusWatcher.Status) {
        val practicing = page < STEPS.size
        notActive.isVisible = practicing && !status.active
        findViewById<Button>(R.id.btnTutorialActivate).apply {
            setText(if (status.enabled) R.string.main_choose else R.string.main_enable)
            setOnClickListener {
                if (status.enabled) {
                    (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
                } else {
                    startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                }
            }
        }
    }
}
