# Phase 04 - Keyboard and gamepad binding

**Strategic spec:** [`../S1359_minigame-restart-level-command.md`](../S1359_minigame-restart-level-command.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 03
**Blocks:** Phase 05
**Steps done:** 3 / 3
**Started:** 2026-08-06
**Completed:** 2026-08-06

---

## Objective

Close strategic §11 criterion 3 - make the command reachable without touch - by giving it its own entry in the screen's key map, since the focus system cannot reach the action row here.

---

## Prerequisites

- [x] `GameViewModel.restartCurrentLevel()` exists (Step 02.1).
- [x] Strategic §6 item 4 is Resolved - owner picked `R` and `BUTTON_Y` on 2026-08-06.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/game/helpers/GameInputManager.kt` | Modified | ≤ 100 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/game/GameActivity.kt` | Modified | ≤ 300 |

> No layout work. The button from Phase 03 stays exactly as it is - this phase adds a second way to reach the same command.

---

## Steps

### Step 04.1 - Add the binding to the screen's key map

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/game/helpers/GameInputManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/game/GameActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add an `onRestartLevel: () -> Unit` constructor parameter to `GameInputManager`, placed between `onPrimaryAction` and `onBack` so the callback order matches the order the keys are handled in. In `handleKeyEvent`, add a branch above the `KEYCODE_ESCAPE` one that matches `KeyEvent.KEYCODE_R` and `KeyEvent.KEYCODE_BUTTON_Y`, calls `onRestartLevel()` and returns `true`. In `GameActivity.setupViews`, pass `onRestartLevel = { viewModel.restartCurrentLevel() }` - the same call the button already makes, so both entry points share the ViewModel's `PLAYING` guard and neither needs a state check of its own.

**Why:**

Strategic §11 criterion 3 requires the command to be reachable by gamepad and D-pad, and §6 item 4 records why the button alone cannot deliver it: `GameActivity.dispatchKeyEvent` hands every event to this key map before the focus system runs, so `nextFocus*` is inert and a dedicated key is the only route.

**Verification:**

- `Grep` - `KEYCODE_BUTTON_Y` present in `GameInputManager.kt`.
- `Grep` - `onRestartLevel` matches in both `GameInputManager.kt` and `GameActivity.kt`.
- `Grep` - `GameInputManager(` returns exactly one construction site, so no caller is left with the old arity.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-06 - Verification 4\4 PASS: `KEYCODE_BUTTON_Y` 1 hit, `onRestartLevel` 2 hits in the manager and 1 in the activity, `GameInputManager(` 1 construction site. `.\a.ps1 fk` exit 0.

---

### Step 04.2 - Prove both keys restart a live level

**Files:** none - verification only
**Depends on:** Step 04.1

**Prompt for developer:**

> On a running emulator, open the mini-game, move the player so its position differs from the level start, then send `KEYCODE_R` (46) and confirm the level restarts. Read the restart from the board's content description (`Monster at row R, column C`), not from `Turns` - `restartLevel` carries `stats` over from the pre-restart state exactly as being caught does, so the turn counter is expected NOT to reset and using it as the predicate would report a false failure.

**Why:**

Strategic §11 criterion 3 is an input-reachability claim, and a compile cannot prove a key event survives `dispatchKeyEvent` - only a real key press on a real screen can.

### Scope correction, 2026-08-06

The step originally asked for the same run with `KEYCODE_BUTTON_Y` (100). **That run cannot produce the evidence it implies and is dropped**: `adb shell input keyevent` injects with a keyboard source, so a pass would prove the key map handles the code - which Step 04.3 already proves mechanically and more durably - while saying nothing about a physical gamepad emitting it. The gamepad half rides on the same `dispatchKeyEvent` -> `handleKeyEvent` path that the `R` run exercises end to end. Real-gamepad confirmation is left to the owner on hardware; no emulator run can stand in for it.

**Verification:**

- The board's `Monster at row R, column C` description changes to a level-start position after `KEYCODE_R` while playing.

**Status:** `[x]` done

**Step Log:**

- 2026-08-06 - PASS on emulator-5554 (Android 15, SDK 35), standard debug `v2.60.8041.533`. Before: `Monster at row 3, column 4`, `Turns: 1`, `topResumedActivity=...GameActivity`. After `input keyevent 46`: `Monster at row 8, column 8`, `Turns: 1`, still on `GameActivity`. The position jump is the restart; the unchanged counter is the documented carry-over.
- 2026-08-06 - Emulator contention noted, not a product defect: `GameActivity` was torn down twice by `ActivityTaskManager: START ... cat=[category.HOME] ... LauncherHomeActivity ... from uid 0`, and the same logcat window carries `LauncherHomeActivity: S1209: placing cell` plus touches this session never made. A concurrent S1209 session was driving the same device. Nothing in the log names an idle or screensaver mechanism.

---

### Step 04.3 - Pin the mapping with a unit test

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/game/helpers/GameInputManagerTest.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add `GameInputManagerTest` next to the existing `GameBoardRenderMapperTest`. Mock `KeyEvent` with mockk the way `KeyboardShortcutHandlerTest` does - stub `action` and `keyCode` only, no Robolectric. Assert that a key-down of `KEYCODE_R` and of `KEYCODE_BUTTON_Y` each returns `true` and fires `onRestartLevel` exactly once, that neither touches the direction, primary-action or back callbacks, that a key-up returns `false`, and that the pre-existing direction, primary-action and exit keys still reach their own callbacks.

**Why:**

A device run can only inject a synthetic key event, so it proves the command is wired but not that the mapping survives a later edit to the `when` block - the regression this test exists to catch is a future key being added in a way that shadows `R` or `BUTTON_Y`.

**Verification:**

- `scripts/builders/check-standard-fast.ps1 -Mode Unit -Tests "*GameInputManagerTest*"` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-06 - Exit 0. `TEST-...GameInputManagerTest.xml` reports `tests="5" skipped="0" failures="0" errors="0"`, so the class ran rather than the run being a stale pass.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` exit 0.
- [x] Phase-boundary audit run - findings below, none deferred.

## Phase notes

- Audit L1: the new callback is a constructor lambda that forwards straight to `viewModel.restartCurrentLevel()`, so `GameActivity` gained no logic (Rule 3) and the key path reuses the button's path rather than duplicating the `PLAYING` check.
- Audit L3: nothing is registered or unregistered - the callback lives for the manager's lifetime, so there is no listener-symmetry obligation and no leak surface.
- The branch consumes `R` and `BUTTON_Y` whatever the game status, matching every other branch in this key map; outside `PLAYING` the ViewModel's guard turns the press into a no-op rather than a wrong action.
- Modifier state is deliberately not inspected, so `Ctrl+R` restarts too. `KeyboardShortcutHandler` binds `Ctrl+R` to rename/refresh, but only on the `BROWSE` and `MAIN` surfaces - the game screen has neither command, so nothing is shadowed, and checking meta here would be the only branch in the file that does.

---

## Handoff Notes to Next Phase

With this phase done all four strategic §11 criteria are met, so Phase 05 may write the inventory record it deliberately withheld. One residual: `BUTTON_Y` is proven by the unit test and by sharing the `R` dispatch path, not by a physical gamepad - the owner is the only one who can close that.

---

## Rollback Plan

Revert the two Kotlin edits - one constructor parameter and one `when` branch, no state and no persistence.
