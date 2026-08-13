# Phase 2 — TvKeyRouter component

**Status:** DONE

## Goal

Create `core/input/TvNavAction.kt` and `core/input/TvKeyRouter.kt`.

## TvNavAction sealed type

```
sealed interface TvNavAction {
    data object Next : TvNavAction       // DPAD_RIGHT / TAB
    data object Prev : TvNavAction       // DPAD_LEFT / SHIFT+TAB
    data object Up : TvNavAction         // DPAD_UP
    data object Down : TvNavAction       // DPAD_DOWN
    data object Select : TvNavAction     // DPAD_CENTER / ENTER
    data object Back : TvNavAction       // BACK
}
```

## TvKeyRouter responsibilities

- Injected as `@Singleton` via Hilt.
- `fun route(event: KeyEvent): TvNavAction?`
  - Returns non-null only for `SOURCE_KEYBOARD` / `SOURCE_DPAD` and `ACTION_DOWN`.
  - `SOURCE_GAMEPAD` / `SOURCE_JOYSTICK` → returns null (handled by `GamepadInputManager`).
  - Key mapping:
    - `KEYCODE_DPAD_RIGHT`, `KEYCODE_TAB` (no shift) → `Next`
    - `KEYCODE_DPAD_LEFT`, `KEYCODE_TAB` + `META_SHIFT_ON` → `Prev`
    - `KEYCODE_DPAD_UP` → `Up`
    - `KEYCODE_DPAD_DOWN` → `Down`
    - `KEYCODE_DPAD_CENTER`, `KEYCODE_ENTER`, `KEYCODE_NUMPAD_ENTER` → `Select`
    - `KEYCODE_BACK` → `Back`

## Steps

- [x] Create `TvNavAction.kt`
- [x] Create `TvKeyRouter.kt` with Hilt `@Singleton @Inject constructor()`
- [x] No separate DI module needed — `@Singleton @Inject constructor()` handles it directly (same pattern as GamepadInputManager)
