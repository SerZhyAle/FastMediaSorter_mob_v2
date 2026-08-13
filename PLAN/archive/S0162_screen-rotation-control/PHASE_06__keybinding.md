# S0162 Phase 06 — Keyboard Binding for Rotation Toggle

## Files

- `app_v2/src/main/java/com/sza/fastmediasorter/domain/input/CommandId.kt`
- `app_v2/src/main/assets/input/default_bindings.json`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerKeyboardHandler.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerKeyboardCallbackImpl.kt`
- `app_v2/src/main/res/values/strings.xml` (+ `strings_ru.xml`, `strings_uk.xml`)

---

## CommandId.kt

In the `// --- VIEW_ZOOM ---` block, after `FULLSCREEN`:

```kotlin
const val ROTATION_TOGGLE = "view.rotation_toggle"
```

---

## default_bindings.json

Default key: **R** (`KEYCODE_R = 46`, no modifier = `key:46:0`).

`R` alone is currently unassigned (Ctrl+R is `sorting.rename`).
Mnemonic: **R**otation.

Add a new entry to the `bindings` array:

```json
{
  "command_id": "view.rotation_toggle",
  "triggers": {
    "keyboard": ["key:46:0"],
    "gamepad": [],
    "mouse": [],
    "vr": []
  }
}
```

Place near other `view.*` entries (after `view.fullscreen`).

---

## PlayerKeyboardHandler.kt

### PlayerKeyboardCallback interface

Add after `onToggleBlackScreen()`:

```kotlin
fun onToggleRotationSensor() {}
```

(Default no-op body keeps binary compatibility with any other implementations.)

### handleCommand() dispatch

Add after the `CommandId.BLACK_SCREEN` line, before `else -> false`:

```kotlin
CommandId.ROTATION_TOGGLE -> { callback.onToggleRotationSensor(); true }
```

---

## PlayerKeyboardCallbackImpl.kt

Find the existing override for `onToggleBlackScreen()` and add alongside it:

```kotlin
override fun onToggleRotationSensor() {
    // S0162: same action as the command panel button
    activity.viewModel.toggleRotationSensor()
}
```

`activity` is the `PlayerActivity` reference already held by the impl class
(check existing pattern — it uses `activity.` prefix for similar calls).

---

## String resources — keybinding label

`KeybindingRowLabelFormatter.resolveCommandLabel()` auto-resolves
`keybinding_label_<command_id_dots_to_underscores>`:

Resource name: `keybinding_label_view_rotation_toggle`

### strings.xml (EN)

```xml
<string name="keybinding_label_view_rotation_toggle">Screen Rotation</string>
```

### strings_ru.xml

```xml
<string name="keybinding_label_view_rotation_toggle">Поворот экрана</string>
```

### strings_uk.xml

```xml
<string name="keybinding_label_view_rotation_toggle">Поворот екрана</string>
```

The command appears automatically in `KeybindingRemapActivity` under the `VIEW_ZOOM` group
header — no code changes needed there.

---

## Acceptance

- `R` key in player fires `toggleRotationSensor()` when `followSystemRotation=false`.
- When `followSystemRotation=true` (toggle hidden), the key press still calls
  `toggleRotationSensor()` via the keyboard handler — but the ViewModel must guard:
  only apply the flip when `!followSystemRotation` (otherwise the key is a no-op;
  add this guard in `PlayerViewModel.toggleRotationSensor()`).
- `KeybindingRemapActivity` shows "Screen Rotation" row in VIEW_ZOOM group with default R key.
- User can rebind to any key; the new binding persists via `InputBindingRepository`.
- Strings pass locale audit for `keybinding_label_view_rotation_toggle`.
