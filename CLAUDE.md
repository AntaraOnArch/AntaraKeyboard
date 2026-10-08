# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Install on connected device
./gradlew installDebug

# Run tests
./gradlew test

# Run a single test class (JVM unit tests live in app/src/test)
./gradlew testDebugUnitTest --tests "com.example.antarakeyboard.data.ScriptMapperTest"

# Clean build
./gradlew clean
```

## Project Overview

AntaraKeyboard is a privacy-focused Android custom keyboard (InputMethodService) with extensive customization options. It supports multiple key shapes (hex, triangle, circle, cube), gesture input (swipe delete/restore, swipe for capitals), and configurable layouts.

## Features & Options (functional spec)

This section is the authoritative description of intended behavior. When changing code, preserve these rules.

### Global keyboard options

| Option | Values | Where it is chosen |
|--------|--------|--------------------|
| Row count | 3, 4 or 5 rows | Main app – **Keyboard rows** dropdown |
| Key shape | Hexagon, Triangle, Circle, Cube | Main app – **Key shape** dropdown (with live preview) |
| Key height | 60 %–150 % (5 % steps, default 100 %) | Main app – **Key height** slider |
| Row spacing | −8 dp … +16 dp (1 dp steps, default 0) | Main app – **Row spacing** slider |
| Language (and with it the keyboard script) | System default or any of the 35 languages | Main app – **Language** dropdown, or on the keyboard – **hold BOTH space keys for 4 seconds** → the same language list; Android 13+ also system Settings → Apps → Antara → Language |
| Vibration feedback | On / Off | Main app – **Vibration feedback** dropdown |
| Word suggestions | On / Off (default Off) | Main app – **Word suggestions** dropdown |

Everything that depends on layout (Set layout editors, long-press bindings, side buttons, numeric layout) automatically adapts to the currently selected row count and key shape.

**Language → script:** `AppLanguageSettings` is the one app-language setting (main app, keyboard picker, Android 13+ settings). The keyboard script follows it (`AppLanguages.scriptPresetFor`): Serbian (Cyrillic), Macedonian, Russian, Ukrainian, Bulgarian → their Cyrillic, Belarusian → Russian Cyrillic, Greek → Greek, Serbian Latin and all other languages → Latin. `ensureDefaultLongPress()` re-syncs the script when the language changed elsewhere (e.g. Android settings).

**How the script works:** the letter layout itself stays Latin. Choosing a Cyrillic or Greek script maps each Latin key to a letter of that script for both the label and the typed text (`data/ScriptMapper.kt`; q/w/x/y map to script-specific letters such as љ/њ/џ; Greek follows the standard Greek layout: u → θ, w → ς, q → `;`). The selected script is stored as `selected_long_press_preset` (`system`/`latin` = Latin keyboard).

**Default long-press letters** (`data/LongPressPresets.kt`, `defaultsFor(preset, language)`):
- **Latin keyboard** – the special letters of the app/device language on their base letter (`KeyboardPrefs.languageTag()`; e.g. hr/bs/sr c → č ć, d → đ, s → š, z → ž; de a → ä, s → ß; …, ~30 languages). English and unknown languages get common accents (no symbols). `system` is also Latin, even on a Serbian device.
- **Cyrillic keyboard** – only that script's special letters on the matching key (Serbian c → ч ћ, d → ђ џ, s → ш, z → ж, l → љ, n → њ; Macedonian, Russian, Ukrainian, Bulgarian likewise). Plain letters are never bound – the key already types them.
- **Greek keyboard** – the accented vowels on their vowel key (a → ά, e → έ, h → ή, i → ί ϊ, o → ό, y → ύ ϋ, v → ώ).
- **Each script gives only its own letters:** the popup hides single letters of the other scripts – Latin, Cyrillic, Greek (`LongPressPresets.visibleFor`); custom text, emoji and symbols show in all.
- **User binds survive:** switching script, a language change, or an app update with new defaults only swaps the default letters (`LongPressPresets.mergeDefaults`); everything the user added stays after them. `KeyboardPrefs.ensureDefaultLongPress()` (on every keyboard open) does the defaults-version migration (v5 removed the old per-letter Cyrillic twins and the big Latin lists, see `LegacyLongPressPresets`) and re-syncs when the language changes (`long_press_defaults_language`).

**Key shape note:** with 3 rows, `HEX` is drawn as `HEX_TALL` (`effectiveShape()`).

**Key height:** the slider changes **only the height of the drawn keys** – never key widths or any spacing between keys or rows. Stored as `key_scale` in `keyboard_prefs`. `KeyView.heightStretch` does the work: the view keeps its tuned box, builds the shape at 100 % and stretches it vertically (hexagons and triangles via a matrix; circle and cube simply fill the taller box), and grows by exactly the shape's growth, so the space around every shape stays the same. The only spacing value that changes is the honeycomb overlap of hexagon rows, which grows by the tip growth (`KeyScale.honeycombOverlap`) so rows stay interlocked with the same gap; triangle/cube/circle row margins are untouched. Landscape uses the same mechanism. The keyboard height follows (measured from content). 3-row portrait triangles are drawn 1.5× taller at 100 % (`KeyScale.THREE_ROW_TRIANGLE_DEFAULT`, applied in `keyHeightStretch()`).

**Row spacing:** adds `row_spacing_dp` (in `keyboard_prefs`) to the vertical gap between rows – the top margin of every row after the first in portrait (`buildRow`), and through `landscapeRowOverlapScaled()` in landscape. It changes nothing else (key sizes, honeycomb overlap and horizontal spacing stay).

### Main app buttons

In screen order (`res/layout/activity_main.xml`):

1. **Enable** – opens Android system settings so the user can enable the keyboard (IME).
2. **Choose** – opens the system input-method picker popup to select the active keyboard.
3. **Set Layout** – opens the layout-editing popup (tabs below). Contents and bindings automatically follow the selected row count and shape.
4. **Key shape** dropdown – with shape preview.
5. **Keyboard rows** dropdown – 3 / 4 / 5, followed by the **Key height** and **Row spacing** sliders.
6. **Bind LongPress** button and **Reset…** dropdown (same row) – long-press binding popup (tabs below); Reset colors / Reset layout / Reset all, see [Reset](#reset).
7. **Export settings** / **Import settings** (same row) – JSON backup file, see [Settings export / import](#settings-export--import).
8. **Colors** – popup for coloring every part of the keyboard (sections below).
9. **Vibration feedback** dropdown – On / Off, followed by the **Word suggestions** dropdown – On / Off.
10. **Language** dropdown – System default + every translation, each named in its own language (`data/AppLanguages.kt`).
11. **Theme** dropdown – Light / Dark, Transparent, Custom (saved layouts), RGB Smooth / RGB Wild (see [Themes](#themes)), plus a context button below it (**RGB settings** or **Saved layouts**).
12. **About** – popup with app name, version, privacy statement and **open-source licenses** (tap an entry for the full license text).

**All popups share a uniform look** (same styling, section dropdown, spacing). Popup height fits its content (wrap content), scrolling only when the screen is too short — no fixed heights. New dialogs must follow the same style.

### Set layout popup

All tabs autosave; there is no Save button. A single "<Section> saved" toast is shown when the user switches to another tab, closes the popup, or leaves the app — only if something changed in that tab (never per individual edit).

#### Layout (opened by default)
- Arranges the letter keys across rows.
- **Swap**: select two keys, then press the **Swap** button at the bottom of the popup to exchange their positions.
- **Drag and drop** one key onto another also swaps them.
- Autosave.

#### Emoji
- Configures the emoji picker's **category slider position**: top / bottom / left / right.
- Three **fixed buttons**: **X** (close), **Backspace**, **Space**. The user chooses their **order** and whether they appear on the **left or right** side of the screen.
- Autosave.

#### Side buttons
- Number of side (edge) buttons depends on row count:
  - 5 rows → 6 side buttons
  - 4 rows → 4 side buttons
  - 3 rows → 3 side buttons
- **Drag and drop** reorders side buttons.
- **Tap** a side button → popup with special characters to assign.
- Autosave.

#### Numeric
- Shows the numeric layout matching the selected row count (and symbols chosen in the main app); here the numeric layout is fully configured.
- **Protected keys** – must ALWAYS be present; only their position may change, never removed:
  `ABC (back to letters)`, `0`, `1`, `2`, `3`, `4`, `5`, `6`, `7`, `8`, `9`, `Enter`.
- All other symbols can be removed or replaced with any symbol from the special characters list.
- **Drag and drop** only changes positions.
- **Tap** removes the symbol (if it is not protected) and immediately opens the symbol picker; the picker includes an **"empty"** option to leave the slot blank.
- Autosave.

#### Horizontal
- Edits the symbols shown in **landscape mode** as a floating "island" between the two key halves.
- **No key is protected** – every slot can be changed and the user may turn all of them off.
- **Drag and drop** moves symbols.
- **Tap** removes the current binding and opens the symbol picker popup, which also offers leaving the slot **empty**.
- Autosave.

### Bind long press popup

Lets the user bind any special letter or emoji to a key's long press.

- Section is chosen from a **dropdown** (same style as Set layout):
  - **Alphabet** – long-press bindings for letter keys.
  - **Numeric** – same rules as Alphabet.
  - **My binds** – list of every key (alphabet and numeric) that has at least one binding, with its bound characters; tapping one opens the character picker to edit it.
  - **Custom text** – choose Letters or Numbers, tap a key on the same keyboard preview, type any text (no length limit, e.g. an e-mail address or a whole message) and **Add** it to that key's long press. Below the field, everything bound to the key is listed with ✕ to remove. Uses the same binders as Alphabet / Numeric (`LongPressEditorBinder.renderKeyChooser` / `addBinding` / `removeBinding`), so all sections stay in sync.
- Keyboard preview is rendered exactly like the Set layout editor (shared `ui/EditorKeyboardRenderer.kt`); a small badge shows how many characters are bound to each key.
- Tapping a key opens an **emoji-picker-style character picker**: category tabs (special-character groups, all Latin / Cyrillic / Greek letters from `LongPressPresets`, extra symbols from the default layouts, emoji categories), a grid where tapping toggles a binding, and a strip of currently bound characters (tap to remove). Both special characters and **emoji** can be bound.
- **Protected keys – nothing can be bound** on: either **Space**, **123** (switch to numeric), **Enter**.
- **Custom text is inserted exactly as typed**: Shift only changes single-character bindings (`shiftedBinding()` in `model/KeyboardConfig.kt`). In the keyboard's long-press popup long text is shrunk/ellipsized in its cell (`KeyView` label fitting) and shown in the preview line above the grid (at most 3 lines, ellipsized). Previews in the editor (bound list, My binds) are shortened the same way; the bound text itself is never cut.
- Autosave (no Save button); one "<Section> saved" toast on section switch, closing the popup, or leaving the app.

### Reset
Dropdown with three options; each first saves the current state as a "Backup <time>" in Saved layouts.
- **Reset colors** – default colors of the currently selected Light/Dark theme (the app's own setting, not the OS theme); turns RGB themes off. The pre-reset colors are also kept as "Last custom colors".
- **Reset layout** – default letter positions, numeric and horizontal layouts, default side buttons, and the **key height / row spacing sliders back to default**.
- **Reset all** – both of the above.
- **Preserved** (never reset): long-press bindings and the selected key shape.
- Reset layout only touches the **current row count**.
- Saved layouts keep at most **5** entries (`SavedLayoutStorage.MAX_SAVED_LAYOUTS`); the oldest is dropped, so repeated resets can push out user-saved layouts.

### Colors popup
Same structure as Bind long press (`res/layout/dialog_colors.xml`): a section dropdown and a page. Every change is saved immediately; one "<Section> saved" toast on section switch, closing the popup, or leaving the app.

| Section | Options |
|---------|---------|
| **Space** | "Use theme colors" (theme space fill), or each space key colored separately / both the same color (`space_use_theme`, independent of the Keys setting) |
| **Enter** | Background color + icon color |
| **Side buttons** | "Use theme colors", or a custom background + icon color |
| **Keys** | "Use theme colors"; otherwise "All keys same color" (one background + text color) or per-key colors (tap a key → small popup; stored as `key_individual_<label>_bg/_text`, letters lowercased; "Use default for this key" clears it) |
| **Background** | Default by theme / Dark / Light / Custom color (`KeyboardPrefs.BackgroundMode`, resolved by `resolveKeyboardBackground`). Dark and Light use the theme-default backgrounds. Transparent is chosen in the Theme dropdown, not here |
| **Theme defaults** | Edits the Light and Dark default palettes (key fill, key text, space fill, keyboard background) that "use theme" options fall back to. Factory values are in `KeyboardPrefs.FactoryDefaults` |

**Color resolution – one rule everywhere:** `KeyboardPrefs.resolveKeyColors()` (regular keys: theme defaults / "all same" / per-key with theme fallback) and `KeyboardPrefs.resolveSpaceColors()` are the only place these rules live. The keyboard (`applySpecialKeyColors`, also ⌫, 😊, empty slots and the landscape island), the emoji/script pickers, saved layouts, "Last custom colors" and the Colors pages all use them. Enter uses the Enter colors; Shift uses the regular key colors (active = filled arrow). **Saved layouts and "Last custom colors" store the colors as drawn** (resolved), so restoring looks the same. Colors pages start from the drawn colors when a "use theme" switch is turned off or Background → Custom is chosen, so nothing jumps to an old stored value. Never read `getKeysBg`/`getSpace1Bg`/… directly to draw something.

### Themes
- Main-app dropdown offers **Light** and **Dark** mode. This is the app's own setting (`theme_prefs` → `dark_mode`, default dark), independent of the OS theme. It switches both the main app and the keyboard (`Theme.AntaraKeyboard.Light/Dark`). The keyboard recreates its view when the mode changes.
- **Transparent** makes the keyboard background see-through (`BackgroundMode.TRANSPARENT`); keys keep the current Light/Dark colors and popups use the theme background as their surface. Choosing any other theme, or a background in Colors → Background, brings the theme background back.
- **Custom** appears when there are saved layouts (or saved custom colors). Selecting it opens a **Saved layouts** popup listing every saved layout (plus "Last custom colors"); tapping one gives Preview / Restore / Rename / Delete. A saved layout also stores the key height and row spacing (`SavedLayout.keyScale` / `rowSpacingDp`, nullable for layouts saved before they existed) and restores them with the layout. Preview draws real keys with the saved shape, row count, key height, row spacing and colors, shows a "Transparent background" note at the top when the saved background is transparent, and lets the user edit the saved colors and then **Apply Colors** (colors only, also saved back into that entry) or **Restore Layout** (layout + colors). Restoring or applying colors makes Custom the active theme; closing the popup without applying anything reverts the dropdown to the previous theme. While Custom is active, picking **Custom** in the dropdown again, or the **Saved layouts** button below it, reopens the popup (the dropdown is a `ui/ReselectSpinner`, which reports re-picking the selected item).
- **RGB Smooth** and **RGB Wild** (animated rainbow background) are also themes in this dropdown. Selecting one activates it immediately (no separate enable toggle) and opens its settings popup (speed / saturation / brightness). Selecting any other theme turns RGB off. While an RGB theme is active, picking it again or the **RGB settings** button below the dropdown reopens that popup.

### Keyboard gestures (on the keyboard)
- Swipe up on a letter → capital letter.
- Swipe up on `.` → `,` and on `?` → `!` (shown as a small second label on the key).
- Swipe up on `123` → emoji picker (alphabet layout, 4 and 5 rows only). The `😊` key also opens it.
- Swipe left → delete text; swipe right → restore deleted text – on keys **and on the background between/around keys** (`BackgroundSwipeListener` on `overlayLayer`). Speed grows with swipe distance. The restore buffer is cleared when any other text is typed. A running swipe blocks the swipe-up shortcuts (`.`, `?`, `123`→emoji), and opening the emoji picker or any early end of a key gesture stops it (`stopSwipeGestures()`), so deletion can never run on unattended.
- Hold backspace → repeated delete after the system long-press timeout. Deleted text can be restored with swipe right.
- Long press → popup grid with bound characters. Slide the finger to choose; lifting commits the highlighted character.
- Hold both spaces for 4 s → language picker (same list and setting as the main app's Language dropdown). Swipes are ignored while both spaces are held.
- Shift is a one-tap toggle: outlined arrow `⇧` when off, filled arrow `⬆` when on (`KeyMarkers.SHIFT_OFF/ON`), always in the regular key colors (also on a side button). It resets when the keyboard closes.

### Keyboard popups
All keyboard popups are `PopupWindow`s anchored to `overlayLayer`. They use `isClippingEnabled = false` so they can extend above the keyboard's top edge (needed for the top row).
- **Key preview** (`KeyPreviewManager`) – shown above the pressed key. Uses that key's background and text color. Not shown for special keys (space, shift, backspace, enter, 123/ABC), and **never in password fields**.
- **Long-press popup** (`LongPressPopupManager`) – surface = keyboard background (`popupSurfaceColor()`; theme background when the keyboard is transparent; under an RGB theme it animates with the keyboard). Cells use the pressed key's colors. The selected cell uses the Enter colors.
- **Language picker** – opened by the dual-space hold. System default + every translation in its own language (same as the main app); picking one sets the app language and the matching keyboard script immediately. Uses the emoji picker's palette.
- **Emoji picker** (`EmojiPickerManager`) – categories plus Recent (last 30, `EmojiPickerStorage`), 5 columns, layout from the Set layout → Emoji tab. Colors follow the keyboard (`emojiPickerColors()`: surface + regular key colors).

### Text input behavior
- **Enter** uses `sendKeyChar('\n')`: runs the editor's action (Search, Send, Go, Done…) unless the field has no action or sets `IME_FLAG_NO_ENTER_ACTION`, then inserts a newline.
- **Backspace / swipe delete** remove the selection if there is one, otherwise one grapheme cluster (`android.icu.text.BreakIterator`), so emoji, flags and combining marks are never split. Swipe restore re-inserts whole graphemes. Editors that expose no text get a `KEYCODE_DEL` key event.
- **Field type** (`service/InputTypes.kt`): number / phone / date fields open on the numeric layout; password fields disable the key preview.

### Strings and translations
- Every user-visible text is a resource. English in `res/values/strings.xml` is the default; Android picks the language from the device settings.
- Translations (34): bg, be, bs, ca, cs, da, de, el, es, et, fi, fr, ga, hr, hu, is, it, lt, lv, mk, mt, nb, nl, pl, pt, ro, ru, sk, sl, sq, sr (Cyrillic), `b+sr+Latn` (Serbian Latin, a transliteration of `sr`), sv, uk.
- `app_name` is translated too and written in the language's own script (e.g. "Антара клавиатура", "Πληκτρολόγιο Αντάρα"); it is the launcher label, the IME name in system settings and the main screen title.
- **App language** (per-app locale): the Language dropdown calls `AppCompatDelegate.setApplicationLocales` and stores the tag in `PrefsManager.getAppLanguage` (`theme_prefs` → `app_language`, "" = device). Android 13+ applies it system-wide (also to the keyboard service) and lists the languages in system settings via `generateLocaleConfig` (`res/resources.properties` sets the default `en`). Below 13, AppCompat stores it for activities (`AppLocalesMetadataHolderService` in the manifest) and `MyKeyboardService.localizedContext()` applies it to the keyboard, recreating the input view when it changes.
- **Adding a language:** create `values-<tag>/strings.xml`, add the tag to `AppLanguages.TAGS` (`AppLanguagesTest` fails if they differ). Language splits are disabled in `bundle { language { enableSplit = false } }` so every translation ships for the in-app picker.
- Never hard-code UI text. A new string goes into **every** `values-*/strings.xml` (lint reports `MissingTranslation`); keep format arguments identical to English.
- Use format arguments (`%1$s`, `%1$d`) instead of concatenation, and `R.plurals` for counts (`rows_count`). Each language lists its own CLDR quantities (e.g. ru/uk/pl/cs one/few/many/other, sl adds two, lv has zero).
- Internal ids stay untranslated constants and are shown through a label function (theme ids → `themeLabel()`, key shapes → `shapeLabel()`); enums carry a `@StringRes` (`EmojiCategory.nameRes`, `EmojiPickerStorage.TabsPosition.labelRes`).
- Letters, symbols, sample alphabets and color/value formats (`rgb(...)`, hex) are not translated.
- Dates use the device locale (`SavedLayoutStorage.formatTimestamp`). Key accessibility labels (`KeyView.getAccessibleDescription`) are translated too.

### Side buttons (edge slots)
Actions available per slot (`EdgeActionType`): Shift, Backspace, Enter, Space, Char (any special character), Emoji picker, None. When Shift or Backspace is placed on a side button, that key is hidden from the main layout (replaced by an `__EDGE_GHOST__` placeholder in `EdgeKeyManager`). Enter stays in both places. The side (left/right) of each slot is fixed per row count (`EdgeSlotsStorage.fixedSideForIndex`). Slots are stored per row count.

### Landscape
Letter rows split into left and right halves with the Horizontal "island" in the middle (`buildLandscapeLayout`). The split points per row are hard-coded per row count (`leftLandscapeKeysForRow` / `rightLandscapeKeysForRow`).

### Settings export / import
- **Export** writes one JSON file through the system "save as" picker (`ActivityResultContracts.CreateDocument`, no storage permission). **Import** reads it through `OpenDocument`, asks for confirmation, saves the current state as a "Backup" in Saved layouts, applies the file, re-applies theme and language, and recreates the screen.
- Format (`data/SettingsBackup.kt`): `{"format": "antara-keyboard-settings", "version": 1, "exportedAt": …, "data": {"<prefs file>": {"<key>": {"type": "int|long|float|boolean|string|string_set", "value": …}}}}`. It contains **no package or class names**, so backups move between any builds – this is the migration path when the applicationId changes from `com.example…` before release (a new applicationId is a new app with empty settings).
- Included prefs files: `SettingsBackup.FILES` (keyboard_prefs, theme_prefs, custom_theme_prefs, edge_slots, global_lp_prefs, emoji_picker_prefs, saved_layouts_prefs). **Recent emojis are excluded** (privacy) and are never overwritten by an import.
- Import replaces each included file completely; files missing from the backup are left alone. Wrong `format`, broken JSON or files over 2 MB are rejected; a higher `version` asks the user to update; unknown files/types are skipped.
- **When adding a new SharedPreferences file, add it to `SettingsBackup.FILES`.** If a stored key or value changes meaning, bump `SettingsBackup.VERSION` and migrate older files in `decode()`.

### Word suggestions
- Only a **preview strip** of up to 3 words above the keys (`suggestionStrip` in `MyKeyboardService`, a child of `overlayLayer` above `keyboardContainer` – keyboardContainer's children must stay key rows only, the side-button overlay indexes them). Tapping a word replaces the word being typed and adds a space. **No auto-correct and no learning**: nothing typed is stored.
- Engine (`service/suggest/`): `WordSuggester` = completions by prefix from the frequency list + spelling corrections via **SymSpellKt** (`com.darkrockstudios:symspellkt`, MIT; edit distance 2, prefix length 5 ≈ 18 MB per 50k words; distance 1 for words ≤ 4 letters, no corrections below 3 letters). Suggestions keep the typed capitalization. `SuggestionController` loads one dictionary at a time in the background; `SuggestionDictionaries.forKeyboard()` picks it: Serbian Cyrillic script → `sr_cyrl`, Russian → `ru`, Greek → `el`, Bulgarian/Ukrainian/Macedonian → none; the Latin keyboard follows the app/device language (hr, sr → `sr_latn`, bs, de, otherwise `en`).
- SymSpellKt declares minSdk 26 but is pure Kotlin, so the manifest overrides it (`tools:overrideLibrary`) to keep minSdk 24.
- Refreshed from `onUpdateSelection`. The app's request is respected: in passwords, numbers, e-mail/URL fields and fields with `TYPE_TEXT_FLAG_NO_SUGGESTIONS` (e.g. Chrome's address bar, the Google search widget) the strip is **not shown at all** (`InputTypes.allowsSuggestions`), so the keyboard is shorter there.
- Dictionaries: `app/src/main/assets/dictionaries/*.txt` ("word count", most frequent first), generated by `tools/build_dictionaries.py` from FrequencyWords (OpenSubtitles 2018, **CC BY-SA 4.0** – attribution in `assets/dictionaries/NOTICE.txt`; shown in About → Open-source licenses). To add a language: add it to the script, regenerate, map it in `SuggestionDictionaries`.

### About & licenses
- `MainActivity.showAboutDialog()` lists `data/OpenSourceLicenses.ENTRIES`; each entry opens its license text from `app/src/main/assets/licenses/` (Apache 2.0 full text, the MIT texts with their copyright notices, the CC BY-SA 4.0 notice for the dictionaries).
- **When adding a runtime dependency, add an entry (and its license text if new)** – check with `./gradlew :app:dependencies --configuration releaseRuntimeClasspath`. `OpenSourceLicensesTest` fails if a referenced license file is missing.

### Privacy
- No `INTERNET` permission; only `VIBRATE`.
- No cloud backup (`allowBackup="false"`, `res/xml/backup_rules.xml` and `data_extraction_rules.xml` exclude everything from cloud backup). Direct device-to-device transfer keeps settings.
- Nothing typed is stored. The only text kept is the in-memory swipe-restore buffer and the recent-emoji list (which is left out of settings exports). Word suggestions read the word before the cursor and never store it.

### Known gaps (code vs spec)
Places where the current code does not yet match the spec above. Fix toward the spec, then remove the line here.
- **RGB Smooth / RGB Wild settings popups** still use `AlertDialog` with Save/Cancel.

**Target SDK**: 36 | **Min SDK**: 24 | **Language**: Kotlin | **Java Version**: 11

## Architecture

### Service-Centric Design

The app follows a configuration-driven architecture where `MyKeyboardService` orchestrates all keyboard functionality:

```
MyKeyboardService (InputMethodService)
├── Keyboard Rendering (redrawKeyboard, createKey, buildLandscapeLayout)
├── Input Processing (delegates to KeyInputController)
├── Configuration State (KeyboardConfig, KeyShape, isShifted)
├── DeleteRestoreManager (swipe delete/restore, backspace hold)
├── LongPressPopupManager (character selection popup)
├── EdgeOverlayManager (side buttons/edge overlay)
├── EdgeKeyManager (edge key configuration)
├── EmojiPickerManager (emoji picker popup)
├── HapticManager (vibration feedback)
└── KeyPreviewManager (key press preview popup)
        ↑
        │ loads/saves
        ↓
Persistence Layer (SharedPreferences + Gson JSON)
├── KeyboardPrefs        (keyboard_prefs: layouts, shape, rows, colors, background mode, theme defaults, RGB, vibration)
├── PrefsManager         (theme_prefs: dark_mode, use_custom_theme)
├── GlobalLongPressStorage (global_lp_prefs: binds per key label)
├── EdgeSlotsStorage     (edge_slots: org.json, per row count)
├── EmojiPickerStorage   (emoji_picker_prefs)
├── SavedLayoutStorage   (saved_layouts_prefs: max 5 snapshots of layout + colors)
└── LongPressPresets     (static per-script binding sets)
```

The main app also writes `custom_theme_prefs` ("Last custom colors") directly.

### Key Components

| Component | Location | Purpose |
|-----------|----------|---------|
| `MyKeyboardService` | `service/MyKeyboardService.kt` | Core IME service (~3300 lines) - lifecycle, rendering, text submission, popup colors, RGB animation, script picker |
| `KeyInputController` | `service/input/KeyInputController.kt` | Tap / swipe / backspace-hold handling; long press and swipe-up on `123` are handled in `createKey()` |
| `DeleteRestoreManager` | `service/DeleteRestoreManager.kt` | Swipe delete/restore gestures, backspace hold |
| `LongPressPopupManager` | `service/LongPressPopupManager.kt` | Long press character selection popup |
| `EdgeOverlayManager` | `service/EdgeOverlayManager.kt` | Side buttons/edge overlay rendering |
| `EdgeKeyManager` | `service/EdgeKeyManager.kt` | Edge key configuration for layout |
| `EmojiPickerManager` | `service/EmojiPickerManager.kt` | Emoji picker with categories and lazy loading |
| `HapticManager` | `service/HapticManager.kt` | Vibration feedback for key presses |
| `KeyPreviewManager` | `service/KeyPreviewManager.kt` | Key press preview popup above finger |
| `InputTypes` | `service/InputTypes.kt` | Password / numeric field detection from `EditorInfo.inputType` |
| `ScriptMapper` | `data/ScriptMapper.kt` | Latin → Cyrillic / Greek key mapping per script preset |
| `KeyboardConfig` | `model/KeyboardConfig.kt` | Data model: rows → keys → labels + long-press bindings |
| `KeyView` | `ui/KeyView.kt` | Custom view for rendering keys in various shapes |
| `DefaultLayout` | `ui/DefaultLayout.kt` | Predefined keyboard layouts (3/4/5-row, numeric, landscape) |
| `MainActivity` | `MainActivity.kt` | Settings UI with embedded customization dialogs (~3000 lines; most dialogs are built in code) |
| `EditorKeyboardRenderer` | `ui/EditorKeyboardRenderer.kt` | Shared keyboard preview for the Set layout and Bind long press editors |
| `SavedLayoutStorage` | `data/SavedLayoutStorage.kt` | Backups / saved layouts (Theme → Custom) |

The keyboard re-reads all prefs in `onStartInputView`, so the main app never needs to signal it after a change.

**Release build:** R8 minify and resource shrinking are on. Settings are Gson JSON and enum names, so `app/proguard-rules.pro` keeps `model/**`, `SavedLayout`, `EdgePos` and all enum constants. Any new class that is serialized, or enum stored by name, must be added there.

**Tests:** JVM unit tests in `app/src/test` cover `ScriptMapper`, `InputTypes`, `KeyboardConfig` helpers, `EdgeSlotsStorage` defaults, the protected keys of the default layouts, `AppLanguages` and `SettingsBackup` (round trip, privacy exclusion, rejection of foreign/newer files). Keep pure logic out of Android classes so it stays testable.

### Data Models

```kotlin
KeyboardConfig
├── rows: List<RowConfig>
│   └── keys: List<KeyConfig> (label + longPressBindings)
├── specialLeft: List<KeyConfig>  // Left edge keys
└── specialRight: List<KeyConfig> // Right edge keys

KeyShape: HEX, HEX_TALL, HEX_HALF_LEFT, HEX_HALF_RIGHT, TRIANGLE, CIRCLE, CUBE

EdgeSlot: (index, side, type, value) for configurable side buttons
```

### Gesture Detection

Split between `createKey()`'s touch listener in `MyKeyboardService` and `KeyInputController.handleTouch()`:
- Horizontal swipe: `absDx > dp(20) && absDx > absDy * 1.05f` → delete/restore text (`KeyInputController`)
- Vertical swipe on release: `dy < -dp(24) && absDy > absDx` → `,` / `!` on `.` / `?`, capital on letters (`KeyInputController`)
- Swipe-up during move (`dp(26)`) on `.`, `?`, `123` (`createKey`)
- Long press: coroutine started on DOWN with `ViewConfiguration.getLongPressTimeout()`; shows the popup and takes over MOVE/UP (`createKey`)

### Persistence

All configuration stored in SharedPreferences with Gson serialization:
- `alphabet_layout_*`, `numeric_layout_*`, `horizontal_center_layout_*` (per row count: 3, 4, 5)
- `key_shape`, `row_count`, `selected_long_press_preset`
- Colors (`space*`, `enter_*`, `keys_*`, `key_individual_*`, `side_buttons_*`, `background_mode` / `background_color`), theme defaults, RGB settings, edge slot configuration
- Emoji picker: recent emojis, button order, tabs position, buttons side

### UI Binders

Customization dialogs in `ui/`:
- `LayoutEditorBinder` - Drag-drop key arrangement
- `LongPressEditorBinder` - Configure character bindings per key
- `ColorWheelView` - HSV color picker
- `ShapePreviewView` - Key shape visualization

### Special Markers

`KeyMarkers` object defines sentinel strings:
- `__EDGE_GHOST__`, `__USER_EMPTY__`, `__SPACE_LEFT__`, `__SPACE_RIGHT__`

## Key Data Flow

```
Touch → createKey() touch listener (long press, swipe-up on . ? 123) → KeyInputController.handleTouch()
    ↓
├── Tap → MyKeyboardService.commitText(char)
├── Long press → showLongPressPopup() → selection → commitText()
├── Swipe horizontal → startSwipeDelete/Restore()
└── Swipe up → capital letter or special char
```

## Language Support

`LongPressPresets` provides language-specific character sets:
- Latin (default)
- Serbian, Bulgarian, Russian, Ukrainian, Macedonian Cyrillic
- Greek
- The script follows the app language (`AppLanguages.scriptPresetFor`)
