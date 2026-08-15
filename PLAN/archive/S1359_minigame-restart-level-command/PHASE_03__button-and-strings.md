# Phase 03 - Action-row button, both orientations, three locales

**Strategic spec:** [`../S1359_minigame-restart-level-command.md`](../S1359_minigame-restart-level-command.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** 2026-08-05
**Completed:** 2026-08-05

> **Both steps are done, but the phase did not deliver §11 criterion 3.** See "Criterion 3 is not met" below - closing it needs an owner decision, which is why the ticket sits at `BlockQuestions` rather than at `BlockNeedUserTest`.

---

## Objective

Put the command on the game screen in both orientations, reachable by touch, D-pad and gamepad, with a label in all three locales.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] `GameViewModel.restartCurrentLevel()` exists (Step 02.1).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/activity_game.xml` | Modified | ≤ 270 |
| `app_v2/src/main/res/layout-land/activity_game.xml` | Modified | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/game/GameActivity.kt` | Modified | ≤ 300 |

> **Landscape parity (CLAUDE.md Rule 11).** `res/layout-land/activity_game.xml` exists and is listed - both files change in the same step.

### Plan correction, 2026-08-05

The original step 03.1 was to author a trilingual `game_restart_level` key. **The key already exists** in `res/values*/strings_game.xml` for all three locales - `Restart level` / `Начать уровень заново` / `Почати рівень заново` - and `GameActivity.resetActionText()` already shows it on `btnGameReset` in the `GAME_OVER` state. Consequences, applied below:

- No string work, and no `values/strings.xml` in `Files Touched`. The string step is dropped rather than reduced to a verification, which would have failed the real-work filter.
- The new button **hides** outside `PLAYING` instead of merely disabling. Disabling would leave two buttons carrying the identical caption in the game-over state, one of them greyed - not a design choice, a defect. Hiding also costs no new wording and no guess at a second translation. Nothing is constrained to the new button in either layout, so `GONE` collapses cleanly.

---

## Steps

### Step 03.1 - Add the button to both layouts

**Files:** `app_v2/src/main/res/layout/activity_game.xml`, `app_v2/src/main/res/layout-land/activity_game.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Portrait: inside `layoutGameControlPanel`, add a `com.google.android.material.button.MaterialButton` with id `btnGameRestartLevel`, style `@style/Widget.Material3.Button.TextButton`, height `@dimen/button_height_small`, `android:text="@string/game_restart_level"`, `android:visibility="gone"`, constrained `Start_toStartOf="parent"` and `Top_toTopOf="@id/btnGameMode"` so it forms a second action row under `btnGameReset` and the existing `GridLayout` constraint on `btnGameMode` still clears it. Landscape: add the same button after `btnGameMode` in the vertical action stack, with `android:layout_gravity="end"` like its neighbours and the same `gone` default. In both files set `android:focusable="true"` and wire `nextFocusUp` to `btnGameReset`, `nextFocusRight` to `btnGameMode` and `nextFocusDown` to `btnGameUp`. Use no hardcoded colour.

**Why:**

Strategic §6 item 2 places the command in the action row rather than in a menu or a long press, §7 makes that placement the only guard against an accidental 500-point spend by keeping it away from the arrow grid, and §3 requires it reachable by D-pad and gamepad rather than touch alone.

**Verification:**

- `Grep` - `btnGameRestartLevel` present in `res/layout/activity_game.xml` and in `res/layout-land/activity_game.xml`.
- `Grep` - `@string/game_restart_level` present in both layout files.
- `Grep` - `nextFocusUp` and `nextFocusDown` present on the new button in both files.
- `Grep` - `android:(background|textColor)="#` returns zero hits in both files.
- `.\a.ps1 fr` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-05 - Verification 5\5 PASS in both files. `fr` exit 0. Verified on device afterwards: portrait `[11,1608][283,1703]`, landscape `[2141,604][2413,699]`.

---

### Step 03.2 - Wire the button in `GameActivity`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/game/GameActivity.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Next to the existing `binding.btnGameSkip.setOnClickListener` line, add `binding.btnGameRestartLevel.setOnClickListener { viewModel.restartCurrentLevel() }`. In the render paths that already toggle `binding.btnGameReset.isEnabled`, set `binding.btnGameRestartLevel.isVisible` to true only in the branch where the level is being played and false in the loading, game-over and finished branches. Add no confirmation dialog.

**Why:**

Strategic §6 item 3 rules out a confirmation step, and §11 criterion 1 requires the command during a running level - showing the button only there is what keeps it honest in the game-over state, where `btnGameReset` already carries this very label.

**Verification:**

- `Grep` - `viewModel.restartCurrentLevel()` matches twice in `GameActivity.kt`, one call site per entry point: the button listener from this step and the key-map callback from Step 04.1. It read `exactly once` while this phase was the only entry point; Phase 04 added the second and the predicate was restated on 2026-08-06 rather than left to read as a regression.
- `Grep` - `btnGameRestartLevel.isVisible` matches at least three times.
- `Grep` - `Log\.d\(` returns zero hits in `GameActivity.kt`.
- `.\a.ps1 fc` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-05 - Verification 4\4 PASS (`isVisible` set in all four render branches). `fc` exit 0. Tapped on device: both probes fired, no crash.
- 2026-08-06 - Re-checked after Phase 04: 2 call sites (`GameActivity.kt:83`, `:95`), `btnGameRestartLevel.isVisible` 4 hits, no `Log.d(`. The portrait layout budget was raised from 260 to 270 - the file is 264 lines and the original figure was an estimate written before the button landed.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fc` exit 0, then `.\a.ps1 dq` exit 0 for the device run, then `fk` + `fkn` after the probe removal.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added via `scripts/post-change.ps1` - verdict `post-change: PASS`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated by the same facade run.
- [x] Phase-boundary audit run - findings below, none deferred.

## Criterion 3 is not met - escalated to the owner

The plan's mitigation for §11 criterion 3 was `nextFocus*` on the new button. **It cannot work on this screen**, and the phase-boundary audit caught it:

- `GameActivity.dispatchKeyEvent` hands every key event to `GameInputManager.handleKeyEvent` before `super`, and that method returns `true` for all four D-pad directions plus `DPAD_CENTER` / `ENTER` / `SPACE`. The focus system never sees them, so no action-row button can be focused with a D-pad and `nextFocus*` is inert.
- Confirmed on emulator-5554, 2026-08-05: three D-pad presses moved the player (`Turns: 0 -> 3`) while focus stayed on `btnGameBack`.
- Closing it needs a dedicated key in the screen's own key map, which is a binding the owner has not chosen. Recorded as strategic §6 item 4; ticket set to `BlockQuestions`.
- The `nextFocus*` attributes were left in both layouts. They cost nothing and become correct the moment the key map stops swallowing directions; removing them would only hide the intent.

## Phase notes

- Audit L1: `GameActivity` gained one listener and four visibility assignments and no logic - it delegates to the ViewModel (Rule 3). L3: the listener is bound to a binding-owned view for the activity's lifetime, matching its `btnGameSkip` neighbour; the listener-symmetry gate reported `new imbalance 0`.
- UI-phase screenshot gate (S1338): placement decision recorded (strategic §6 item 2 + "Quiz decisions (2026-08-05)", owner ruling quoted). Screenshots captured this phase on emulator-5554: `temp/scratch/emulator-5554_20260805_130117.png` (portrait) and `temp/scratch/emulator-5554_20260805_130217.png` (landscape). The button renders as planned in both - portrait `btnGameRestartLevel 'Restart level' [11,1608][283,1703]` under `btnGameReset`, landscape `[2141,604][2413,699]` under `btnGameMode` - and `btnGameReset` reads `Reset` while playing, so there is no duplicate caption.
- Command exercised on device before the probes were removed: `S1359: voluntary restart requested, status=PLAYING` then `S1359: level restarted, score 0 -> 0`. The score was already 0 and `afterLevelRestart` clamps at zero, so the device run shows the path runs; the 900 -> 400 arithmetic is what the unit test settles.

---

## Handoff Notes to Next Phase

The command is complete end to end. What remains is the inventory record and closure.

---

## Rollback Plan

Revert phase commit(s) - one string key, one button per layout, one listener; no data migration.
