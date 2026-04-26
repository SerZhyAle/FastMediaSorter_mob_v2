# Phase 04 — Gamepad / Mouse / Media-Button Migration

**Strategic spec:** [`../spec_player-keybinding-remapping.md`](../spec_player-keybinding-remapping.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 07
**Steps done:** 5 / 5
**Started:** 2026-04-25
**Completed:** 2026-04-25

---

## Objective

Migrate G1 `GamepadInputManager`, M1 `MouseEventHandler`, R1 `MediaButtonRestartReceiver`, and R2 `AudioPlaybackService`'s `MediaSession.Callback` to consume `KeyBindingManager`. Preserve rate limiting, analog deadzones, axis inversion and axis scaling inside the engines — the resolver owns the trigger → `CommandId` map only.

---

## Prerequisites

- [ ] Phase 02 is `✅ Done`.
- [ ] `temp/phase1/debounce-literals.md` carries concrete numeric values for G1 rate limits, G1 deadzone, and V1-related values (V1 is Phase 05).
- [ ] Strategic §10 analog threshold UX item is resolved (fixed vs. user-adjustable) — locked into `InputTrigger.GamepadAxis.threshold` schema.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/input/GamepadInputManager.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/MouseEventHandler.kt` | Modified | ≤ 400 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/MediaButtonRestartReceiver.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioPlaybackService.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/input/InputTrigger.kt` | Modified | ≤ 250 |
| `app_v2/src/test/java/com/sza/fastmediasorter/core/input/GamepadInputManagerTest.kt` | New or modified | ≤ 400 |

---

## Steps

### Step 04.1 — Add `InputTrigger.fromMotionEvent` helpers

**Files:** `domain/input/InputTrigger.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Add two helpers:
>
> - `fun InputTrigger.Companion.fromGamepadButton(keyCode: Int): InputTrigger.GamepadButton = GamepadButton(keyCode)` — used by G1 on `handleKeyEvent`.
> - `fun InputTrigger.Companion.fromGamepadAxis(axis: Int, value: Float, deadzone: Float): InputTrigger.GamepadAxis?` — returns `null` if `abs(value) < deadzone`, otherwise `GamepadAxis(axis, direction = value.sign.toInt(), threshold = deadzone)`. This is the only place deadzone gating happens at the trigger-construction level; engines may still rate-limit the resulting resolved commands.
>
> Do NOT add mouse helpers — `MouseEventHandler` constructs `InputTrigger.MouseButton(event.actionButton)` inline.

**Verification:**

- `Grep "fun InputTrigger.Companion.fromGamepadButton"` matches exactly once.
- `Grep "fun InputTrigger.Companion.fromGamepadAxis"` matches exactly once.
- `Grep "deadzone"` matches ≥ 2 in `InputTrigger.kt` (helper parameter + KDoc reference).

**Status:** `[ ]` not done

---

### Step 04.2 — Migrate GamepadInputManager (G1)

**Files:** `core/input/GamepadInputManager.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Constructor-inject `KeyBindingManager`.
>
> 1. In `handleKeyEvent(event, surface)`: build `val trigger = InputTrigger.fromGamepadButton(event.keyCode)`; resolve via `KeyBindingManager.resolve(trigger, surface)`. Then translate the `CommandId` into the existing `GamepadAction` via a `when(commandId)` switch — call this `mapCommandToGamepadAction(commandId, surface)`. The engine-level rate-limiter wraps the final dispatch (preserve existing `SystemClock.uptimeMillis()` guard using the literal value from `temp/phase1/debounce-literals.md`).
> 2. In `handleMotionEvent(event, surface)`: for each axis (`AXIS_X`, `AXIS_Y`, `AXIS_Z`, `AXIS_RZ`, `AXIS_HAT_X`, `AXIS_HAT_Y`), read the value and build `InputTrigger.fromGamepadAxis(axis, value, DEADZONE)`. If non-null, resolve. Preserve left-stick Y inversion (strategic §9.3) inside the engine — invert the `value` before constructing the trigger for `AXIS_Y`.
> 3. Preserve right-stick scaling (strategic §9.3) — if the resolved `CommandId` is `navigation.seek_forward` / `navigation.seek_backward` and the trigger is `GamepadAxis`, multiply the seek step by `abs(value)` before dispatching.
> 4. Delete all inline `BUTTON_*` / `AXIS_*` literals from the public `handleKeyEvent` / `handleMotionEvent` bodies. Literals MAY remain in private helper functions that define what is a "gamepad axis we care about" (e.g. the axis list itself).

**Verification:**

- `Grep -c "BUTTON_A\|BUTTON_B\|BUTTON_X\|BUTTON_Y\|BUTTON_L1\|BUTTON_R1\|BUTTON_START\|BUTTON_SELECT" app_v2/src/main/java/com/sza/fastmediasorter/core/input/GamepadInputManager.kt` returns 0 inside `handleKeyEvent` and `handleMotionEvent` scopes (acceptable residue: a private `supportedButtons` list).
- `Grep "keyBindingManager.resolve"` matches ≥ 2.
- `Grep "DEADZONE"` still matches — deadzone constant not deleted.
- `Grep` for the rate-limit literal value (from Phase 01) still appears in the file.
- `Grep -n "Log\.d\("` returns zero hits.

**Status:** `[ ]` not done

---

### Step 04.3 — Migrate MouseEventHandler (M1)

**Files:** `ui/common/MouseEventHandler.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Constructor-inject `KeyBindingManager`. For `BUTTON_SECONDARY`, `BUTTON_TERTIARY`, `BUTTON_BACK`, `BUTTON_FORWARD` branches: build `InputTrigger.MouseButton(event.actionButton)` and resolve. Left-click (open / double-click) and hover / tooltip flows stay unchanged — these are not bindable commands in v1 (strategic §7 lists them, but §14 "Out of Scope" excludes gesture-level remaps; left-click is canonically "activate").
>
> Scroll wheel: `AXIS_VSCROLL` and `AXIS_HSCROLL` continue to emit `InputAction.ScrollWheel` as before — wheel is not remappable in v1. Document this decision with a one-line comment: `// Wheel is not remappable in v1 (see spec §7 + §10 "analog threshold UX" item).` Keep the comment terse.

**Verification:**

- `Grep "keyBindingManager.resolve"` matches ≥ 1 in the file.
- `Grep "InputTrigger.MouseButton"` matches ≥ 1.
- `Grep -c "BUTTON_SECONDARY\|BUTTON_TERTIARY\|BUTTON_BACK\|BUTTON_FORWARD"` in `MouseEventHandler.kt` returns 0 (moved to the resolver via the JSON asset).
- `Grep "AXIS_VSCROLL\|AXIS_HSCROLL"` still matches — wheel logic unchanged.
- `Grep -n "Log\.d\("` returns zero hits.

**Status:** `[ ]` not done

---

### Step 04.4 — Migrate MediaButtonRestartReceiver (R1) and AudioPlaybackService MediaSession (R2)

**Files:** `ui/player/MediaButtonRestartReceiver.kt`, `ui/player/AudioPlaybackService.kt`
**Depends on:** Step 04.1 (uses `InputTrigger.fromKeyEvent` from Phase 03 Step 03.1 — confirm imported)

**Prompt for developer:**

> **R1:** The receiver currently filters `Intent.EXTRA_KEY_EVENT` for `MEDIA_*` keycodes to decide whether to restart the service. Replace the keycode whitelist with: extract the `KeyEvent`, build `InputTrigger.fromKeyEvent(event)`, resolve via an injected `KeyBindingManager`. If the resolved `CommandId` belongs to the `PLAYBACK_CORE` or `NAVIGATION` group, start the service. Otherwise pass through. This way a user-assigned media-button binding automatically restarts playback too.
>
> **R2:** `MediaSession.Callback` has `onPlay`, `onPause`, `onSkipToNext`, `onSkipToPrevious` — these are NOT keycodes, they are Media3 semantic callbacks. Migration here is different: add a single bridge method `fun dispatchCommand(commandId: CommandId)` inside the service that performs the playback action. Then rewire `onPlay` to call `dispatchCommand(CommandId.PLAYBACK_PAUSE_PLAY)` (or similar). The point: every entry point funnels through one `dispatchCommand` — the same method the future settings UI will use to test bindings.

**Verification:**

- `Grep "keyBindingManager.resolve"` matches ≥ 1 in `MediaButtonRestartReceiver.kt`.
- `Grep "fun dispatchCommand"` matches exactly once in `AudioPlaybackService.kt`.
- `Grep "KEYCODE_MEDIA_"` in `MediaButtonRestartReceiver.kt` returns 0 inside the whitelist (the raw keycodes no longer drive the decision); literal may remain in a doc comment only.
- `Grep -n "Log\.d\("` returns zero hits across both files.

**Status:** `[ ]` not done

---

### Step 04.5 — Regression tests: gamepad path + mouse bridge

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/core/input/GamepadInputManagerTest.kt`
**Depends on:** Steps 04.2, 04.3

**Prompt for developer:**

> Create or extend `GamepadInputManagerTest.kt`:
>
> 1. Stub `KeyBindingManager` such that `BUTTON_A` resolves to `CommandId.PLAYBACK_PAUSE_PLAY`.
> 2. Fire a synthetic `KeyEvent` for `BUTTON_A` with surface `PLAYER` — assert `PlayerAction.PlayPause` was dispatched.
> 3. Fire an `AXIS_Y` motion event with value `0.8f` (above deadzone); assert a `Volume` command fires at the expected step.
> 4. Fire an `AXIS_Y` motion event with value `0.1f` (below deadzone `0.15f`); assert **nothing** fires.
> 5. Fire two rapid seek events 50 ms apart — assert the second is rate-limited.

**Verification:**

- `Grep -c "@Test"` in this file returns ≥ 5.
- `./gradlew.bat :app_v2:testStandardDebugUnitTest --tests "*.GamepadInputManagerTest"` exits 0.
- `Grep -n "Log\.d\("` returns zero hits in the test file.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` is `[x] done`.
- [ ] `/build` skill reports green for every non-VR flavor (`standard`, `lite`, `photos`, `legacy`) — VR is Phase 05.
- [ ] `Grep -c "BUTTON_A\|BUTTON_B\|BUTTON_X\|BUTTON_Y" app_v2/src/main/java/com/sza/fastmediasorter/core/input/GamepadInputManager.kt` returns 0 inside the `handleKeyEvent` method body. `sed -n` confirms no residue inside the switch.
- [ ] Debounce / rate-limit constants from `temp/phase1/debounce-literals.md` are still grep-present in each engine — nothing was accidentally deleted.
- [ ] Dev-log entries added for every "Files Touched" file.
- [ ] Grep for `TODO(phase-04)` across `app_v2/` returns zero hits.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated for signature changes.

---

## Handoff Notes to Next Phase

- Phase 05 (VR) can now trust that the keyboard and mouse fallback paths inside `VrControllerInputManager` will route through `KeyBindingManager` automatically, because the BT-keyboard/mouse helper inside V1 already delegates to K1/M1 call sites via the existing code paths.
- `R1` and `R2` now share a single bridge — the future Phase 06 settings UI exposes "Assign media button to …" for any `CommandId`, and both entry points respect it with no further plumbing.
- `GamepadInputManager` retains rate-limiting and deadzone internally. When Phase 06 adds an "Analog threshold slider" (if strategic §10 resolves the UX to adjustable), the slider writes to `InputTrigger.GamepadAxis.threshold` per binding — engine-level `DEADZONE` stays as the floor.

---

## Rollback Plan

- Revert the phase commits. No schema change. Rate-limit / deadzone literals are preserved in git history via Phase 01's `temp/phase1/debounce-literals.md` artefact — rollback restores inline literals without losing the reference copy.
