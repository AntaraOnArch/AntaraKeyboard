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

**Target SDK**: 36 | **Min SDK**: 24 | **Language**: Kotlin | **Java Version**: 11

## Architecture

### Service-Centric Design

The app follows a configuration-driven architecture where `MyKeyboardService` orchestrates all keyboard functionality:

```
MyKeyboardService (InputMethodService)
├── Keyboard Rendering (redrawKeyboard, createKey, buildLandscapeLayout)
├── Input Processing (delegates to KeyInputController)
└── Configuration State (KeyboardConfig, KeyShape, isShifted)
        ↑
        │ loads/saves
        ↓
Persistence Layer (SharedPreferences + Gson JSON)
├── KeyboardPrefs
├── GlobalLongPressStorage
├── EdgeSlotsStorage
└── LongPressPresets
```

### Key Components

| Component | Location | Purpose |
|-----------|----------|---------|
| `MyKeyboardService` | `service/MyKeyboardService.kt` | Core IME service (4500+ lines) - lifecycle, rendering, text submission |
| `KeyInputController` | `service/KeyInputController.kt` | Touch/gesture detection (swipe, long-press, hold) |
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
