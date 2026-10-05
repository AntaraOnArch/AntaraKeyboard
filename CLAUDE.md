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
| Script / alphabet | Latin; Cyrillic: Serbian, Bulgarian, Russian, Ukrainian, Macedonian | On the keyboard itself – **long-press BOTH space keys for 4 seconds** → popup menu |
| Vibration feedback | On / Off | Main app – **Vibration feedback** dropdown |

Everything that depends on layout (Set layout editors, long-press bindings, side buttons, numeric layout) automatically adapts to the currently selected row count and key shape.

**How script selection works:** the letter layout itself stays Latin. Choosing a Cyrillic script (a) maps each Latin key to a Cyrillic letter for both the label and the typed text (`data/ScriptMapper.kt`; q/w/x/y map to script-specific letters such as љ/њ/џ), and (b) replaces the alphabet long-press bindings with that script's preset from `LongPressPresets` (via `KeyboardPrefs.applyLongPressPreset`). Letters with no Latin counterpart are reached through long press. The selected preset is stored as `selected_long_press_preset`. The default (`PRESET_SYSTEM`) picks bindings from the system language.

**Key shape note:** with 3 rows, `HEX` is drawn as `HEX_TALL` (`effectiveShape()`).

### Main app buttons

In screen order (`res/layout/activity_main.xml`):

1. **Enable** – opens Android system settings so the user can enable the keyboard (IME).
2. **Choose** – opens the system input-method picker popup to select the active keyboard.
3. **Set Layout** – opens the layout-editing popup (tabs below). Contents and bindings automatically follow the selected row count and shape.
4. **Key shape** dropdown – with shape preview.
5. **Keyboard rows** dropdown – 3 / 4 / 5.
6. **Bind LongPress** button and **Reset…** dropdown (same row) – long-press binding popup (tabs below); Reset colors / Reset layout / Reset all, see [Reset](#reset).
7. **Colors** – popup for coloring every part of the keyboard (sections below).
8. **Vibration feedback** dropdown – On / Off.
9. **Theme** dropdown – Light / Dark, Transparent, Custom (saved layouts), RGB Smooth / RGB Wild (see [Themes](#themes)), plus a context button below it (**RGB settings** or **Saved layouts**).

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
- Keyboard preview is rendered exactly like the Set layout editor (shared `ui/EditorKeyboardRenderer.kt`); a small badge shows how many characters are bound to each key.
- Tapping a key opens an **emoji-picker-style character picker**: category tabs (special-character groups, all Latin / Cyrillic special letters from `LongPressPresets`, extra symbols from the default layouts, emoji categories), a grid where tapping toggles a binding, and a strip of currently bound characters (tap to remove). Both special characters and **emoji** can be bound.
- **Protected keys – nothing can be bound** on: either **Space**, **123** (switch to numeric), **Enter**.
- Autosave (no Save button); one "<Section> saved" toast on section switch, closing the popup, or leaving the app.

### Reset
Dropdown with three options; each first saves the current state as a "Backup <time>" in Saved layouts.
- **Reset colors** – default colors of the currently selected Light/Dark theme (the app's own setting, not the OS theme); turns RGB themes off. The pre-reset colors are also kept as "Last custom colors".
- **Reset layout** – default letter positions, numeric and horizontal layouts, default side buttons.
- **Reset all** – both of the above.
- **Preserved** (never reset): long-press bindings and the selected key shape.
- Reset layout only touches the **current row count**.
- Saved layouts keep at most **5** entries (`SavedLayoutStorage.MAX_SAVED_LAYOUTS`); the oldest is dropped, so repeated resets can push out user-saved layouts.

### Colors popup
Same structure as Bind long press (`res/layout/dialog_colors.xml`): a section dropdown and a page. Every change is saved immediately; one "<Section> saved" toast on section switch, closing the popup, or leaving the app.

| Section | Options |
|---------|---------|
| **Space** | Each space key colored separately, or both the same color (user's choice) |
| **Enter** | Background color + icon color |
| **Side buttons** | "Use theme colors", or a custom background + icon color |
| **Keys** | "Use theme colors"; otherwise "All keys same color" (one background + text color) or per-key colors (tap a key → small popup; stored as `key_individual_<label>_bg/_text`, letters lowercased; "Use default for this key" clears it) |
| **Background** | Default by theme / Dark / Light / Custom color (`KeyboardPrefs.BackgroundMode`, resolved by `resolveKeyboardBackground`). Dark and Light use the theme-default backgrounds. Transparent is chosen in the Theme dropdown, not here |
| **Theme defaults** | Edits the Light and Dark default palettes (key fill, key text, space fill, keyboard background) that "use theme" options fall back to. Factory values are in `KeyboardPrefs.FactoryDefaults` |

**Color resolution on the keyboard** (`applySpecialKeyColors`): Space → theme space fill if keys use the theme, otherwise space 1 / space 2 colors. Enter → Enter colors. Shift → white with black text while shifted. Other keys → theme defaults, the "all same" colors, or per-key colors (falling back to theme defaults).

### Themes
- Main-app dropdown offers **Light** and **Dark** mode. This is the app's own setting (`theme_prefs` → `dark_mode`, default dark), independent of the OS theme. It switches both the main app and the keyboard (`Theme.AntaraKeyboard.Light/Dark`). The keyboard recreates its view when the mode changes.
- **Transparent** makes the keyboard background see-through (`BackgroundMode.TRANSPARENT`); keys keep the current Light/Dark colors and popups use the theme background as their surface. Choosing any other theme, or a background in Colors → Background, brings the theme background back.
- **Custom** appears when there are saved layouts (or saved custom colors). Selecting it opens a **Saved layouts** popup listing every saved layout (plus "Last custom colors"); tapping one gives Preview / Restore / Rename / Delete. Preview lets the user edit the saved colors and then **Apply Colors** (colors only, also saved back into that entry) or **Restore Layout** (layout + colors). Restoring or applying colors makes Custom the active theme; closing the popup without applying anything reverts the dropdown to the previous theme. While Custom is active, picking **Custom** in the dropdown again, or the **Saved layouts** button below it, reopens the popup (the dropdown is a `ui/ReselectSpinner`, which reports re-picking the selected item).
- **RGB Smooth** and **RGB Wild** (animated rainbow background) are also themes in this dropdown. Selecting one activates it immediately (no separate enable toggle) and opens its settings popup (speed / saturation / brightness). Selecting any other theme turns RGB off. While an RGB theme is active, picking it again or the **RGB settings** button below the dropdown reopens that popup.

### Keyboard gestures (on the keyboard)
- Swipe up on a letter → capital letter.
- Swipe up on `.` → `,` and on `?` → `!` (shown as a small second label on the key).
- Swipe up on `123` → emoji picker (alphabet layout, 4 and 5 rows only). The `😊` key also opens it.
- Swipe left → delete text; swipe right → restore deleted text. Speed grows with swipe distance. The restore buffer is cleared when any other text is typed.
- Hold backspace → repeated delete after the system long-press timeout. Deleted text can be restored with swipe right.
- Long press → popup grid with bound characters. Slide the finger to choose; lifting commits the highlighted character.
- Long-press both spaces for 4 s → script/alphabet selection popup.
- Shift is a one-tap toggle (`⇧` / `⇪`). It resets when the keyboard closes.

### Keyboard popups
All keyboard popups are `PopupWindow`s anchored to `overlayLayer`. They use `isClippingEnabled = false` so they can extend above the keyboard's top edge (needed for the top row).
- **Key preview** (`KeyPreviewManager`) – shown above the pressed key. Uses that key's background and text color. Not shown for special keys (space, shift, backspace, enter, 123/ABC), and **never in password fields**.
- **Long-press popup** (`LongPressPopupManager`) – surface = keyboard background (`popupSurfaceColor()`; theme background when the keyboard is transparent; under an RGB theme it animates with the keyboard). Cells use the pressed key's colors. The selected cell uses the Enter colors.
- **Script picker** – opened by the dual-space hold. Lists Latin plus the 5 Cyrillic scripts; selection applies immediately. Uses the emoji picker's palette.
- **Emoji picker** (`EmojiPickerManager`) – categories plus Recent (last 30, `EmojiPickerStorage`), 5 columns, layout from the Set layout → Emoji tab. Colors follow the keyboard (`emojiPickerColors()`: surface + regular key colors).

### Text input behavior
- **Enter** uses `sendKeyChar('\n')`: runs the editor's action (Search, Send, Go, Done…) unless the field has no action or sets `IME_FLAG_NO_ENTER_ACTION`, then inserts a newline.
- **Backspace / swipe delete** remove the selection if there is one, otherwise one grapheme cluster (`android.icu.text.BreakIterator`), so emoji, flags and combining marks are never split. Swipe restore re-inserts whole graphemes. Editors that expose no text get a `KEYCODE_DEL` key event.
- **Field type** (`service/InputTypes.kt`): number / phone / date fields open on the numeric layout; password fields disable the key preview.
- Keyboard UI strings live in `res/values/strings.xml` (English).

### Side buttons (edge slots)
Actions available per slot (`EdgeActionType`): Shift, Backspace, Enter, Space, Char (any special character), Emoji picker, None. When Shift or Backspace is placed on a side button, that key is hidden from the main layout (replaced by an `__EDGE_GHOST__` placeholder in `EdgeKeyManager`). Enter stays in both places. The side (left/right) of each slot is fixed per row count (`EdgeSlotsStorage.fixedSideForIndex`). Slots are stored per row count.

### Landscape
Letter rows split into left and right halves with the Horizontal "island" in the middle (`buildLandscapeLayout`). The split points per row are hard-coded per row count (`leftLandscapeKeysForRow` / `rightLandscapeKeysForRow`).

### Privacy
- No `INTERNET` permission; only `VIBRATE`.
- No cloud backup (`allowBackup="false"`, `res/xml/backup_rules.xml` and `data_extraction_rules.xml` exclude everything from cloud backup). Direct device-to-device transfer keeps settings.
- Nothing typed is stored. The only text kept is the in-memory swipe-restore buffer and the recent-emoji list.

### Known gaps (code vs spec)
Places where the current code does not yet match the spec above. Fix toward the spec, then remove the line here.
- **Main app strings** are still hard-coded in `MainActivity` (English). Keyboard strings are already in `strings.xml`.
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
| `ScriptMapper` | `data/ScriptMapper.kt` | Latin → Cyrillic key mapping per script preset |
| `KeyboardConfig` | `model/KeyboardConfig.kt` | Data model: rows → keys → labels + long-press bindings |
| `KeyView` | `ui/KeyView.kt` | Custom view for rendering keys in various shapes |
| `DefaultLayout` | `ui/DefaultLayout.kt` | Predefined keyboard layouts (3/4/5-row, numeric, landscape) |
| `MainActivity` | `MainActivity.kt` | Settings UI with embedded customization dialogs (~3000 lines; most dialogs are built in code) |
| `EditorKeyboardRenderer` | `ui/EditorKeyboardRenderer.kt` | Shared keyboard preview for the Set layout and Bind long press editors |
| `SavedLayoutStorage` | `data/SavedLayoutStorage.kt` | Backups / saved layouts (Theme → Custom) |

The keyboard re-reads all prefs in `onStartInputView`, so the main app never needs to signal it after a change.

**Release build:** R8 minify and resource shrinking are on. Settings are Gson JSON and enum names, so `app/proguard-rules.pro` keeps `model/**`, `SavedLayout`, `EdgePos` and all enum constants. Any new class that is serialized, or enum stored by name, must be added there.

**Tests:** JVM unit tests in `app/src/test` cover `ScriptMapper`, `InputTypes`, `KeyboardConfig` helpers, `EdgeSlotsStorage` defaults and the protected keys of the default layouts. Keep pure logic out of Android classes so it stays testable.

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
- Auto-detection via `getForSystemLanguage()`
