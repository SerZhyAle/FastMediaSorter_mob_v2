# Phase 01 - Rotation commands

**Strategic spec:** [`../S1364_image-player-rotation-edit-submenu.md`](../S1364_image-player-rotation-edit-submenu.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 5 / 5
**Started:** 2026-08-07
**Completed:** 2026-08-07

---

## Objective

Give the screen-autorotate toggle its real name and a visible on/off mark, and make content rotation work in both directions - in the embedded player and in the standalone view model that duplicates its rotation state.

---

## Prerequisites

- [ ] Strategic §6 research items are all Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ 4 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ 4 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ 4 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt` | Modified | ≤ 15 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerViewModel.kt` | Modified | ≤ 15 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt` | Modified | ≤ 20 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt` | Modified | ≤ 20 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerCommandPanelCallbackImpl.kt` | Modified | ≤ 10 |
| `app_v2/src/main/res/menu/overflow_menu_player.xml` | Modified | ≤ 6 |

> `PlayerViewModel.kt`, `CommandPanelController.kt` and `PlayerActivity`-adjacent files are over 500 LOC - back each one up to `temp/S1364/` before editing (CLAUDE.md Rule 5). Check with `wc -l` rather than assuming.
>
> No `res/layout*` file is touched in this phase, so Rule 11 does not apply here.

---

## Steps

### Step 01.1 - Add the three new labels

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add three keys, each in one parity-enforced call:
>
> ```powershell
> pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key menu_autorotate_screen_title -En "Screen autorotate" -Ru "Автоповорот экрана" -Uk "Автоповорот екрана"
> pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key rotate_content_ccw_title -En "Rotate -90°" -Ru "Повернуть на -90°" -Uk "Повернути на -90°"
> pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key menu_edit_submenu_title -En "Editing" -Ru "Редактирование" -Uk "Редагування"
> pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key rotate_content_ccw_desc -En "Rotate the image 90° counter-clockwise" -Ru "Повернуть изображение на 90° против часовой стрелки" -Uk "Повернути зображення на 90° проти годинникової стрілки"
> ```
>
> The fourth key is the accessibility description, mirroring the existing `rotate_content_90_desc` that `showOverflowMenu()` already attaches to `ROTATE_CONTENT` - the short title states the amount, the description states the direction.
>
> Leave `rotation_toggle_title` ("Rotation" / «Поворот») in place - it still labels the inline command-bar button and its two `rotation_toggle_sensor_*_desc` siblings. Check all four new values against `docs/COMMUNICATION_POLICY.md` §2 and §6.

**Why:**

Strategic §6 item 4 records the owner's ruling that the toggle is named «Автоповорот экрана» specifically because the code showed it controls screen rotation by sensor rather than the picture, which the old bare «Поворот» hid.

**Verification:**

- `set-android-string.ps1 -Action get` on each of the three keys prints EN, RU and UK values, none `not translated`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "menu_autorotate"` - exit 0; same for `rotate_content_ccw` and `menu_edit_submenu`.
- `Grep` - `name="rotation_toggle_title"` still present in all three `strings.xml`.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

---

### Step 01.2 - Make session rotation signed and sign-safe

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/StandalonePlayerViewModel.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `PlayerViewModel`, replace the body of `rotateSession90()` with a call to a new `rotateSessionBy(stepDegrees: Int)` that normalizes into `0..359`:
>
> ```kotlin
> fun rotateSession90() = rotateSessionBy(ROTATION_STEP_DEGREES)
>
> fun rotateSessionCounter90() = rotateSessionBy(-ROTATION_STEP_DEGREES)
>
> // Kotlin's % keeps the dividend's sign, so a negative step would otherwise store a negative angle.
> private fun rotateSessionBy(stepDegrees: Int) = updateState {
>     val next = (it.sessionRotationAngle + stepDegrees) % FULL_ROTATION_DEGREES
>     it.copy(sessionRotationAngle = (next + FULL_ROTATION_DEGREES) % FULL_ROTATION_DEGREES)
> }
> ```
>
> Apply the identical change to `StandalonePlayerViewModel`, which carries its own copy of `rotateSession90()` and of both constants. Keep `rotateSession90()`'s existing signature on both - callers must not need editing in this step.

**Why:**

Strategic §2 goal 2 requires content rotation in both directions, and the planning research found the single `+90` constant has no direction parameter and that `%` alone would store a negative angle that the downstream absolute `applyRotation(angle)` would then pass straight to the view.

**Verification:**

- `Grep` - `fun rotateSessionCounter90` matches once in each of the two view models.
- `Grep` - `fun rotateSessionBy` matches once in each of the two view models.
- `Grep` - `fun rotateSession90` still matches once in each, so existing callers are untouched.
- `Grep` - `Log\.d\(` returns zero hits in both files.

**Status:** `[x]` done

---

### Step 01.3 - Declare the reverse-rotation command

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt`, `app_v2/src/main/res/menu/overflow_menu_player.xml`
**Depends on:** Step 01.2

**Prompt for developer:**

> Declare `@+id/menu_rotate_content_ccw` in `overflow_menu_player.xml` beside the existing `menu_rotate_content` item, titled `@string/rotate_content_ccw_title`. That file is never inflated, so this is an id declaration and a truthful mirror, nothing more.
>
> In `CommandPanelLayoutPlanner.PlayerCommand` add `ROTATE_CONTENT_CCW` directly after `ROTATE_CONTENT`, overflow-only (`barCapable = false`) like its twin, with a named priority constant one step below `ROTATE_CONTENT_PRIORITY` so it stays adjacent in the sorted list and the detekt MagicNumber gate stays satisfied. In `buildActiveCommands`, add it under exactly the same guard as `ROTATE_CONTENT`. Do **not** add it to the `state.isLiveVideoStream` early-return branch.
>
> Retitle `ROTATION_TOGGLE`'s `titleResId` from `R.string.rotation_toggle_title` to `R.string.menu_autorotate_screen_title`.

**Why:**

Strategic §11 criterion 2 requires both rotation directions to be present and to work, and §11 criterion 1 requires the toggle to read «Автоповорот экрана» in the menu; the planning research established that adding to the general path leaves the two exact-match live-stream unit tests untouched, while adding to the stream branch would break them.

**Verification:**

- `Grep` - `menu_rotate_content_ccw` matches in `overflow_menu_player.xml` and in `CommandPanelLayoutPlanner.kt`.
- `Grep` - `ROTATE_CONTENT_CCW` matches at least twice in `CommandPanelLayoutPlanner.kt` (enum entry and the `add(..)` in `buildActiveCommands`).
- `Grep` - `R.string.menu_autorotate_screen_title` matches once in `CommandPanelLayoutPlanner.kt`.
- `Grep` - `ROTATE_CONTENT_CCW` returns zero hits within the `isLiveVideoStream` block.
- `.\a.ps1 fu --tests "*CommandPanelLayoutPlannerTest*"` or the full `fu` - the two exact-match live-stream tests still pass.

**Status:** `[x]` done

---

### Step 01.4 - Dispatch the reverse rotation

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerCommandPanelCallbackImpl.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Add `fun onRotateContentCounter90Clicked()` to the `CommandPanelCallback` interface in `CommandPanelController.kt`, beside the existing `onRotateContent90Clicked()`. Add the matching `R.id.menu_rotate_content_ccw -> callback.onRotateContentCounter90Clicked()` branch to the `when (cmd.menuItemId)` in `handleOverflowCommand()`, next to the existing `menu_rotate_content` branch.
>
> Implement it in `PlayerCommandPanelCallbackImpl` as the mirror of `onRotateContent90Clicked()`: call `viewModel.rotateSessionCounter90()`, then read back `viewModel.state.value.sessionRotationAngle` and hand it to `activity.applyContentRotation(..)`.
>
> Every other class implementing `CommandPanelCallback` must gain the new method too - find them before editing and let the compiler confirm none is missed.

**Why:**

Strategic §11 criterion 2 requires both rotation commands to work, not merely to appear, and the interface is what forces every host implementing the callback to answer for the new command instead of silently ignoring it.

**Verification:**

- `Grep` - `onRotateContentCounter90Clicked` matches in `CommandPanelController.kt` (interface + `when` branch) and in `PlayerCommandPanelCallbackImpl.kt`.
- `Grep` - `rotateSessionCounter90` matches in `PlayerCommandPanelCallbackImpl.kt`.
- `.\a.ps1 fk` - exit 0, which is what proves no `CommandPanelCallback` implementer was missed.

**Status:** `[x]` done

---

### Step 01.5 - Show the autorotate state and describe the new direction

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt`
**Depends on:** Step 01.4

**Prompt for developer:**

> In `showOverflowMenu()`, after the `MenuItem` for a command is created, mark the autorotate entry as a checkable item reflecting the live setting:
>
> ```kotlin
> if (cmd == CommandPanelLayoutPlanner.PlayerCommand.ROTATION_TOGGLE) {
>     item.isCheckable = true
>     item.isChecked = state.playerRotationSensorEnabled
> }
> ```
>
> Use `state.playerRotationSensorEnabled`, which is the on/off value; `state.showRotationToggle` is a different field that only decides whether the item exists at all. The function already resolves `state` at its top, so no new lookup is needed.
>
> In the same loop, extend the existing `ROTATE_CONTENT` accessibility-description block to cover `ROTATE_CONTENT_CCW` with `R.string.rotate_content_ccw_desc`, so the new command is not the one item in the group without a spoken direction.

**Why:**

Strategic §11 criterion 1 requires the menu to show «Автоповорот экрана» *with* an on/off mark, and strategic §5 states the mark must be set on the created `MenuItem` because the embedded player builds this item in code rather than inflating it from XML. The description mirrors what the existing forward-rotation item already does, which CLAUDE.md Rule 16 requires of a new input-reachable command.

**Why:**

Strategic §11 criterion 1 requires the menu to show «Автоповорот экрана» *with* an on/off mark, and strategic §5 states the mark must be set on the created `MenuItem` because the embedded player builds this item in code rather than inflating it from XML.

**Verification:**

- `Grep` - `isCheckable = true` matches in `CommandPanelController.kt`.
- `Grep` - `state.playerRotationSensorEnabled` matches in `CommandPanelController.kt`.
- `Grep` - `showRotationToggle` does not appear in the new block (it gates existence, not state).
- `Grep` - `rotate_content_ccw_desc` matches in `CommandPanelController.kt`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fc` exit 0 in 59s.
- [x] `CommandPanelLayoutPlannerTest` passes - 11 tests, 0 failures, 0 errors, result XML written 9s before it was read.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added via `.\scripts\add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Step Log

- 2026-08-07 - **Plan defect fixed before starting.** The `Files Touched` table put `StandalonePlayerViewModel.kt` under `ui/player/standalone/`; it actually lives at `ui/player/StandalonePlayerViewModel.kt`. Located via the class catalog and the table corrected. Backups taken for the two files over 500 LOC (`PlayerViewModel.kt` 873, `CommandPanelController.kt` 819); the planner measured 485 and needed none.
- 2026-08-07 - Step 01.1 done. Four keys added, each in one parity-enforced call; `check_strings_localized.ps1` exit 0 for all three prefixes. `rotation_toggle_title` confirmed still present in all three locales (it still labels the inline bar button). **Deviation from the plan, deliberate:** the two accessibility descriptions spell out "90 degrees" / «90 градусов» rather than "90°", because a screen reader announces the degree sign inconsistently.
- 2026-08-07 - Step 01.2 done. `rotateSessionBy(stepDegrees)` added to both view models with the double-modulo normalization; `rotateSession90()` kept as a delegating one-liner so no existing caller changed. Predicates: each of `rotateSessionCounter90` / `rotateSessionBy` / `rotateSession90` matches exactly once per file, `Log.d(` zero.
- 2026-08-07 - Step 01.3 done. `ROTATE_CONTENT_CCW` added at named priority 670 (one below its forward twin, so the MagicNumber gate stays satisfied), guarded identically in `buildActiveCommands`, id declared in the never-inflated XML, and `ROTATION_TOGGLE` retitled. Predicate that mattered most: `ROTATE_CONTENT_CCW` returns **0** hits inside the `isLiveVideoStream` block, which is what keeps the two exact-match unit tests green - confirmed afterwards by running them.
- 2026-08-07 - Step 01.4 done. Interface method, `when` branch and impl added. **The compile is the real verification here**: `CommandPanelCallback` is an interface, so `.\a.ps1 fc` exit 0 is the proof that no other implementer was left without the method - a grep could not have shown that.
- 2026-08-07 - Step 01.4 note: `PlayerCommandPanelCallbackImpl.onRotateContent90Clicked` carries a `Timber.d("S0995: ...")` probe. Checked rather than removed on sight - S0995 is `BlockNeedUserTest`, so the tag is live and correct. Left untouched; the new CCW twin deliberately does not copy it.
- 2026-08-07 - Step 01.5 done. Checkable autorotate item reading `state.playerRotationSensorEnabled` (`state` is resolved at `CommandPanelController.kt:571`, so the new block at ~625 is in scope), plus the CCW accessibility description mirroring the existing forward one.
- 2026-08-07 - Phase Done Criteria: `.\a.ps1 fc` exit 0 (59s); `check-standard-fast.ps1 -Mode Unit -Tests "*CommandPanelLayoutPlannerTest*"` exit 0, and the result XML was checked for freshness rather than trusted on the exit code alone - 11 tests, 0 failures, file 9s old.
- 2026-08-07 - Phase-boundary audit. **One finding, fixed:** `ROTATE_CONTENT_PRIORITY`'s comment claimed that entry "stays the lowest-priority command", which adding `ROTATE_CONTENT_CCW_PRIORITY = 670` made false. Both comments rewritten so the file states who actually holds that role now (Rule 9 - a stale comment is a defect, and this one would mislead the next person choosing a priority). Layer 1 otherwise clean: state mutation stayed in the view models with the UI only dispatching, `rotateSessionCounter90` mirrors its twin's name, and the three largest touched files land at 880 / 833 / 499 LOC. Layers 2-3: `updateState` is the same mechanism already in use, no listener or lifecycle surface touched, nothing newly retained. Layer 4: Room untouched. **P3 accepted, not fixed:** `rotateSessionBy` is now duplicated across the two view models - but that duplication is pre-existing (`rotateSession90` and both constants were already twins), and de-duplicating it is a refactor this ticket did not ask for.

---

## Handoff Notes to Next Phase

Both rotation directions exist as commands and both view models normalize the angle. `ROTATE_CONTENT_CCW` is overflow-only and sorts immediately after `ROTATE_CONTENT`, so Phase 02's submenu picks both up from the planner's filtered list without special-casing.

---

## Rollback Plan

Revert the phase commit. No data migration; `sessionRotationAngle` is session state that resets on reopen, so a revert cannot leave a stored angle behind.
