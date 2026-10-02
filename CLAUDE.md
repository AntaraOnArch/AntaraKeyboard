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

# Run a single test class
./gradlew test --tests "com.example.antarakeyboard.ExampleUnitTest"

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

Everything that depends on layout (Set layout editors, long-press bindings, side buttons, numeric layout) automatically adapts to the currently selected row count and key shape.

### Main app buttons

1. **Enable** – opens Android system settings so the user can enable the keyboard (IME).
2. **Choose** – opens the system input-method picker popup to select the active keyboard.
3. **Set layout** – opens the layout-editing popup (tabs below). Contents and bindings automatically follow the selected row count and shape.
4. **Key shape** dropdown – with shape preview.
5. **Keyboard rows** dropdown – 3 / 4 / 5.
6. **Bind long press** – popup for binding long-press characters (tabs below).
7. **Reset layout** – see [Reset layout](#reset-layout).
8. **Colors** – popup for coloring every part of the keyboard (tabs below).
9. **Theme** dropdown – Light / Dark, plus user-saved custom themes (see [Themes](#themes)).

**All popups share a uniform look** (same styling, header, tab buttons, spacing). New dialogs must follow the same style.

### Set layout popup

#### Layout (opened by default)
- Arranges the letter keys across rows.
- **Swap**: select two keys, then press the **Swap** button at the bottom of the popup to exchange their positions.
- **Drag and drop** one key onto another also swaps them.

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

- **Alphabet** tab – long-press bindings for letter keys.
- **Numeric** tab – same rules as Alphabet.
- **Protected keys – nothing can be bound** on: either **Space**, **123** (switch to numeric), **Enter**.
- Autosave.

### Reset layout
- Restores: OS-theme default (dark/light) colors, default letter positions, default side buttons.
- **Preserved** (NOT reset): long-press bindings and the selected key shape.

### Colors popup
Every sub-section autosaves.

| Section | Options |
|---------|---------|
| **Space** | Each space key colored separately, or both the same color (user's choice) |
| **Enter** | Background color + icon color |
| **Side buttons** | Background color + icon color |
| **Keys** | Letter (text) color + background color |
| **Background** | Default by theme / manual Dark or Light / user-profile custom color / RGB rainbow / transparent |

### Themes
- Main-app dropdown offers **Light** and **Dark** mode.
- If the user has picked any custom color(s), their custom combination is saved and appears in the same dropdown as a user "theme" so it can be re-selected later.

### Keyboard gestures (on the keyboard)
- Swipe up on a letter → capital letter.
- Swipe up on `.` and `?` → special character.
- Swipe left → delete text; swipe right → restore deleted text.
- Long press → popup grid with bound characters.
- Long-press both spaces for 4 s → script/alphabet selection popup.

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
└── EmojiPickerManager (emoji picker popup)
        ↑
        │ loads/saves
        ↓
Persistence Layer (SharedPreferences + Gson JSON)
├── KeyboardPrefs
├── GlobalLongPressStorage
├── EdgeSlotsStorage
├── EmojiPickerStorage
└── LongPressPresets
```

### Key Components

| Component | Location | Purpose |
|-----------|----------|---------|
| `MyKeyboardService` | `service/MyKeyboardService.kt` | Core IME service (~3200 lines) - lifecycle, rendering, text submission |
| `KeyInputController` | `service/KeyInputController.kt` | Touch/gesture detection (swipe, long-press, hold) |
| `DeleteRestoreManager` | `service/DeleteRestoreManager.kt` | Swipe delete/restore gestures, backspace hold |
| `LongPressPopupManager` | `service/LongPressPopupManager.kt` | Long press character selection popup |
| `EdgeOverlayManager` | `service/EdgeOverlayManager.kt` | Side buttons/edge overlay rendering |
| `EdgeKeyManager` | `service/EdgeKeyManager.kt` | Edge key configuration for layout |
| `EmojiPickerManager` | `service/EmojiPickerManager.kt` | Emoji picker with categories and lazy loading |
| `KeyboardConfig` | `model/KeyboardConfig.kt` | Data model: rows → keys → labels + long-press bindings |
| `KeyView` | `ui/KeyView.kt` | Custom view for rendering keys in various shapes |
| `DefaultLayout` | `ui/DefaultLayout.kt` | Predefined keyboard layouts (3/4/5-row, numeric, landscape) |
| `MainActivity` | `MainActivity.kt` | Settings UI with embedded customization dialogs |

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

In `KeyInputController.handleTouch()`:
- Horizontal swipe: `absDx > dp(20) && absDx > absDy * 1.05f` → delete/restore text
- Vertical swipe: `dy < -dp(24) && absDy > absDx` → special character (on . and ?)
- Long press: Shows popup grid for character selection

### Persistence

All configuration stored in SharedPreferences with Gson serialization:
- `alphabet_layout_*`, `numeric_layout_*`, `horizontal_center_layout_*` (per row count: 3, 4, 5)
- `key_shape`, `key_scale`, `key_height_px`
- Theme colors, edge slot configuration
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
Touch → KeyView.onTouch() → KeyInputController.handleTouch()
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
