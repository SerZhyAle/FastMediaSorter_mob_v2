# Phase 06 — Player Tear-Off (Overflow Entry Point)

**Strategic spec:** [`../S0028_vr-multi-window-playback.md`](../S0028_vr-multi-window-playback.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 04
**Blocks:** Phase 07
**Steps done:** 3 / 3
**Started:** 2026-05-04
**Completed:** 2026-05-04

---

## Objective

Wire entry point 3 from strategic §2 Goal 3: an overflow menu item in the player that opens the current file in a new `PlayerActivity` window (without playback position), then finishes the current player and returns to Browse. Guarded by `allowSeparateWindow` setting and `BuildConfig.SUPPORT_VR_PLAYER`.

---

## Prerequisites

- [ ] Phase 04 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.
- [ ] Phase 05 is ✅ Done (strings are available: `R.string.action_open_in_separate_window`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | Modified | ≤ 1100 |
| `app_v2/src/main/res/menu/overflow_menu_player.xml` | Modified | — |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt` | Modified | ≤ 350 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt` | Modified | ≤ 1050 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerCommandPanelCallbackImpl.kt` | Modified | ≤ 300 |

> `PlayerActivity.kt` is 1007 lines and `CommandPanelController.kt` is 984 lines — create timestamped backups in `temp/` before editing both.
>
> **Architecture note (discovered during implementation):** VideoPlayerManager has no menu code. The player overflow menu is owned by `CommandPanelController` (PopupMenu) with commands defined as the `PlayerCommand` enum in `CommandPanelLayoutPlanner`. Step 06.2 was retargeted accordingly.

---

## Steps

### Step 06.1 — Add `tearOffPlayer()` to `PlayerActivity`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create a timestamped backup of `PlayerActivity.kt` in `temp/` if the file exceeds 500 lines.
>
> Add a method `fun tearOffPlayer()` to `PlayerActivity`:
>
> ```kotlin
> fun tearOffPlayer() {
>     val filePath = currentFilePath ?: return  // read from existing field or ViewModel
>     val newWindowId = java.util.UUID.randomUUID().toString()
>     val intent = Intent(this, PlayerActivity::class.java).apply {
>         putExtra(EXTRA_FILE_PATH, filePath)          // use existing constant if present
>         putExtra(EXTRA_WINDOW_ID, newWindowId)
>         addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
>     }
>     startActivity(intent)
>     finish()    // close current player, system navigates back to Browse
> }
> ```
>
> Do NOT pass the current playback position. The new window starts from the beginning of the file (strategic ADR-6).
>
> Note: `finish()` here is equivalent to pressing Back in the player — the Browse activity underneath resumes naturally.

**Verification:**

- `Grep` — `tearOffPlayer` matches in `PlayerActivity.kt`.
- `Grep` — `EXTRA_WINDOW_ID` matches in the `tearOffPlayer` method area (check with context lines).
- `Grep` — `FLAG_ACTIVITY_MULTIPLE_TASK` matches in `PlayerActivity.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `PlayerActivity.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 4/4 PASS. Added tearOffPlayer() to PlayerActivity (uses currentFilePath, EXTRA_WINDOW_ID, FLAG_ACTIVITY_MULTIPLE_TASK, finish()). Files: PlayerActivity.kt (+13 LOC). Dev log recorded.

---

### Step 06.2 — Add overflow menu item via CommandPanel infrastructure

**Files:** `app_v2/src/main/res/menu/overflow_menu_player.xml`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerCommandPanelCallbackImpl.kt`
**Depends on:** Step 06.1

**Prompt for developer:**

> Create timestamped backups of `CommandPanelController.kt` (984 lines) in `temp/` before editing.
>
> The player overflow menu is built dynamically from `PlayerCommand` enum entries. Add "In separate window" as a new command in this pipeline:
>
> **1. `overflow_menu_player.xml`** — add before `</menu>`:
> ```xml
> <!-- S0028: multi-window — shown only on VR+setting via PlayerCommand filter -->
> <item
>     android:id="@+id/menu_open_in_separate_window"
>     android:icon="@drawable/ic_open_in_browse"
>     android:title="@string/action_open_in_separate_window"
>     app:showAsAction="never" />
> ```
>
> **2. `CommandPanelLayoutPlanner.kt`** — add enum entry (priority 610, barCapable=false, goes to overflow):
> ```kotlin
> OPEN_IN_SEPARATE_WINDOW(610, R.id.menu_open_in_separate_window, false, R.string.action_open_in_separate_window, R.drawable.ic_open_in_browse),
> ```
> Add `allowSeparateWindow: Boolean = false` parameter (with default) to `buildActiveCommands()`. Inside its body, add: `if (allowSeparateWindow) add(OPEN_IN_SEPARATE_WINDOW)`.
>
> **3. `CommandPanelController.kt`** —
> - Add field: `private var lastKnownAllowSeparateWindow: Boolean = false`
> - In the existing async settings block inside `updateCommandAvailability()` (where `lastKnownFavoriteVisible` is updated), also read `settings.allowSeparateWindow` and cache it:
>   ```kotlin
>   val shouldAllowSeparateWindow = BuildConfig.SUPPORT_VR_PLAYER && settings.allowSeparateWindow
>   val separateChanged = lastKnownAllowSeparateWindow != shouldAllowSeparateWindow
>   lastKnownAllowSeparateWindow = shouldAllowSeparateWindow
>   ```
>   Extend the `if (favoriteChanged)` condition to `if (favoriteChanged || separateChanged)`.
> - Pass `allowSeparateWindow = lastKnownAllowSeparateWindow` to the `planner.buildActiveCommands()` call.
> - In `CommandPanelCallback` interface, add: `fun onOpenInSeparateWindowClicked()`
> - In `showOverflowMenu()` when block, add: `R.id.menu_open_in_separate_window -> callback.onOpenInSeparateWindowClicked()`
>
> **4. `PlayerCommandPanelCallbackImpl.kt`** — add override:
> ```kotlin
> override fun onOpenInSeparateWindowClicked() {
>     activity.tearOffPlayer()
> }
> ```

**Verification:**

- `Grep` — `menu_open_in_separate_window` matches in `overflow_menu_player.xml`.
- `Grep` — `OPEN_IN_SEPARATE_WINDOW` matches in `CommandPanelLayoutPlanner.kt`.
- `Grep` — `allowSeparateWindow` matches in `CommandPanelLayoutPlanner.kt`.
- `Grep` — `lastKnownAllowSeparateWindow` matches in `CommandPanelController.kt`.
- `Grep` — `onOpenInSeparateWindowClicked` matches in `CommandPanelController.kt`.
- `Grep` — `onOpenInSeparateWindowClicked` matches in `PlayerCommandPanelCallbackImpl.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `CommandPanelController.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `CommandPanelLayoutPlanner.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 8/8 PASS. Added OPEN_IN_SEPARATE_WINDOW to PlayerCommand enum; added allowSeparateWindow param to buildActiveCommands(); added lastKnownAllowSeparateWindow field + settings read in CommandPanelController; added onOpenInSeparateWindowClicked to interface + callback dispatch; implemented in PlayerCommandPanelCallbackImpl. Files: overflow_menu_player.xml (+7 lines), CommandPanelLayoutPlanner.kt (+5 LOC), CommandPanelController.kt (+8 LOC), PlayerCommandPanelCallbackImpl.kt (+4 LOC). Dev log recorded.

---

### Step 06.3 — Compile check and smoke validation

**Files:** *(no new files — build only)*
**Depends on:** Steps 06.1, 06.2

**Prompt for developer:**

> Run `/build` for standard flavor. The project must compile without errors. Confirm that `tearOffPlayer` in `PlayerActivity` uses `FLAG_ACTIVITY_MULTIPLE_TASK` (grep confirms) and calls `finish()`. Confirm `onOpenInSeparateWindowClicked` exists in `PlayerCommandPanelCallbackImpl`.

**Verification:**

- `/build` reports 0 errors.
- `Grep` — `tearOffPlayer` matches in both `PlayerActivity.kt` and `PlayerCommandPanelCallbackImpl.kt`.
- `Grep` — `FLAG_ACTIVITY_MULTIPLE_TASK` matches in `PlayerActivity.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 3/3 PASS. BUILD SUCCESSFUL in 43s. tearOffPlayer in PlayerActivity.kt and PlayerCommandPanelCallbackImpl.kt confirmed. FLAG_ACTIVITY_MULTIPLE_TASK confirmed. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 06.*` above is `[x] done`.
- [x] Project compiles — `/build` passes for VR flavor.
- [x] `Grep` for `TODO(phase-06)` returns zero hits.
- [x] Dev log entries added for all files in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated — `PlayerActivity`, `CommandPanelController`, `CommandPanelLayoutPlanner`, `PlayerCommandPanelCallbackImpl` public surfaces changed.

---

## Handoff Notes to Next Phase

All three entry points are live. Phase 07 finalizes user-facing documentation and catalog sync.

---

## Rollback Plan

Revert phase commit(s). Overflow menu item disappears; `tearOffPlayer` method removed. No persistent state affected.
