# Phase 04 — Integration

**Strategic spec:** [`../S0158_player-large-buttons.md`](../S0158_player-large-buttons.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 03
**Blocks:** Phase 05
**Steps done:** 4 / 4
**Started:** 2026-05-13
**Completed:** 2026-05-13

---

## Objective

Wire `PlayerBigButtonsModeManager` into the live player: read the preference in `PlayerManagerInitializer`, pass it to `CommandPanelController` (top panel + overflow menu), and apply it to the bottom playback button row via `PlayerControlsSetupManager`.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done (`PlayerBigButtonsModeManager` exists with all four methods).
- [ ] Phase 03 is ✅ Done (preference is written correctly by the settings toggle).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt` | Modified | ≤ 1100 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt` | Modified | ≤ 1150 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerControlsSetupManager.kt` | Modified | ≤ 590 |

> All three files exceed 500 LOC — create timestamped backups in `temp/` before editing each:
> ```powershell
> $ts = Get-Date -Format 'yyyyMMdd_HHmmss'
> Copy-Item "app_v2/src/main/java/.../PlayerManagerInitializer.kt" "temp/PlayerManagerInitializer_$ts.kt.backup"
> Copy-Item "app_v2/src/main/java/.../CommandPanelController.kt"   "temp/CommandPanelController_$ts.kt.backup"
> Copy-Item "app_v2/src/main/java/.../PlayerControlsSetupManager.kt" "temp/PlayerControlsSetupManager_$ts.kt.backup"
> ```

---

## Steps

### Step 04.1 — Read preference and pass to `CommandPanelController` constructor

**Files:** `PlayerManagerInitializer.kt`, `CommandPanelController.kt`
**Depends on:** Phase 02 and Phase 03 done

**Prompt for developer:**

> **In `CommandPanelController`:**
> Add `val bigButtonsMode: Boolean` as a constructor parameter (last parameter, default `false`). Store it as a private field. Instantiate `PlayerBigButtonsModeManager(binding.root.context)` as a private field `private val bigButtonsModeManager = PlayerBigButtonsModeManager(binding.root.context)`.
>
> **In `PlayerManagerInitializer.initCommandPanelAndImageLoading()`** (line ~580):
> Before constructing `CommandPanelController`, read:
> ```kotlin
> val bigButtonsMode = PlayerLayoutModePrefs.isBigButtonsMode(activity)
> ```
> Pass `bigButtonsMode = bigButtonsMode` to the `CommandPanelController(...)` constructor.

**Verification:**

- `Grep` — `bigButtonsMode` parameter present in `CommandPanelController` constructor signature.
- `Grep` — `PlayerBigButtonsModeManager` instantiated inside `CommandPanelController`.
- `Grep` — `isBigButtonsMode` called in `PlayerManagerInitializer.kt`.
- `Grep` — `Log\.d\(` returns zero hits in the lines added/modified in these two files.

**Status:** `[x] done`

**Step Log:**

- 2026-05-13 — Verification 4/4 PASS. `bigButtonsMode` param + `bigButtonsModeManager` field in CommandPanelController; `isBigButtonsMode` read in PlayerManagerInitializer. Dev log recorded.

---

### Step 04.2 — Apply big buttons mode to top command panel

**Files:** `CommandPanelController.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> In `CommandPanelController.setupCommandPanelControls()`, after all button click listeners are registered, add at the end:
>
> ```kotlin
> if (bigButtonsMode) {
>     binding.topCommandPanel.post {
>         val visibleButtons = commandPanelButtons().filter { it.isVisible && it != safeViews.btnOverflowMenu }
>         bigButtonsModeManager.applyToTopCommandPanel(
>             topCommandPanel = binding.topCommandPanel,
>             visibleButtons  = visibleButtons,
>             overflowButton  = safeViews.btnOverflowMenu,
>             bigButtonsMode  = true
>         )
>     }
> }
> ```
>
> The `.post { }` ensures the view has been measured (needed to read height for 2× scaling).
>
> **Overflow menu override:**
> In `showOverflowMenu(anchor: View)`, replace the `PopupMenu` block with:
> ```kotlin
> if (bigButtonsMode) {
>     bigButtonsModeManager.buildBigButtonsOverflowMenu(anchor, commands, bigButtonsMode = true) { cmd ->
>         handleOverflowCommand(cmd)  // extract existing overflow item-click logic into a private fun
>     }
>     return
> }
> // … existing PopupMenu code follows unchanged
> ```
> Extract the existing `popup.setOnMenuItemClickListener { ... }` body into a private `fun handleOverflowCommand(cmd: PlayerCommand)` that is called from both paths.
>
> **Re-apply after orientation change:**
> In `updateOrientation(configuration)`, after the `.post { updateCommandAvailability(state) }` call, add:
> ```kotlin
> if (bigButtonsMode) {
>     binding.topCommandPanel.post {
>         bigButtonsModeManager.restoreTopCommandPanel(binding.topCommandPanel)
>         val visibleButtons = commandPanelButtons().filter { it.isVisible && it != safeViews.btnOverflowMenu }
>         bigButtonsModeManager.applyToTopCommandPanel(binding.topCommandPanel, visibleButtons, safeViews.btnOverflowMenu, true)
>     }
> }
> ```

**Verification:**

- `Grep` — `bigButtonsModeManager.applyToTopCommandPanel` present in `CommandPanelController.kt`.
- `Grep` — `buildBigButtonsOverflowMenu` referenced in `CommandPanelController.kt`.
- `Grep` — `handleOverflowCommand` defined in `CommandPanelController.kt`.
- `Grep` — `Log\.d\(` returns zero hits in the lines added/modified.

**Status:** `[x] done`

**Step Log:**

- 2026-05-13 — Verification 4/4 PASS. `applyToTopCommandPanel` called in setup + orientation; `buildBigButtonsOverflowMenu` wired; `handleOverflowCommand` extracted. Dev log recorded.

---

### Step 04.3 — Apply big buttons mode to bottom playback row

**Files:** `PlayerControlsSetupManager.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> In `PlayerControlsSetupManager`, add a constructor parameter `private val bigButtonsMode: Boolean` (default `false`). Add field `private val bigButtonsModeManager = PlayerBigButtonsModeManager(activity)`.
>
> Add a new method:
> ```kotlin
> fun applyBigButtonsModeToPlaybackRow() {
>     if (!bigButtonsMode) return
>     // The playback button row is the LinearLayout that contains btnPrevious, btnPlayPause,
>     // btnVolumeDown, btnVolumeUp, btnSlideShow, btnNext.
>     // Find it via binding: binding.root.findViewById<LinearLayout>(R.id.playbackButtonRow)
>     // (or resolve from binding if a direct id exists — check the layout for the container id).
>     val playbackRow = binding.root.findViewById<android.widget.LinearLayout>(R.id.playbackButtonRow)
>         ?: return
>     playbackRow.post {
>         bigButtonsModeManager.applyToBottomPlaybackRow(playbackRow, bigButtonsMode = true)
>     }
> }
> ```
>
> **Important:** First verify the actual view id of the horizontal LinearLayout containing `btnPrevious` / `btnPlayPause` / `btnNext` by reading `activity_player_unified.xml`. Replace `R.id.playbackButtonRow` with the correct id from the layout. If no id exists on the container, add one (`android:id="@+id/playbackButtonRow"`) to both `res/layout/activity_player_unified.xml` and `res/layout-land/activity_player_unified.xml` in the same step (landscape parity rule).
>
> Call `applyBigButtonsModeToPlaybackRow()` at the end of `setupAllControls()`.
>
> In `PlayerManagerInitializer`, pass `bigButtonsMode` to `PlayerControlsSetupManager(...)` constructor when constructing it (search for `PlayerControlsSetupManager(` in `PlayerManagerInitializer.kt`).

**Verification:**

- `Grep` — `applyBigButtonsModeToPlaybackRow` defined in `PlayerControlsSetupManager.kt`.
- `Grep` — `applyBigButtonsModeToPlaybackRow` called inside `setupAllControls`.
- `Grep` — `bigButtonsMode` parameter present in `PlayerControlsSetupManager` constructor.
- If layout id was added: `Grep` — `playbackButtonRow` (or the chosen id) present in `res/layout/activity_player_unified.xml`.
- If layout id was added: `Grep` — the chosen id present in `res/layout-land/activity_player_unified.xml` (landscape parity).
- `Grep` — `Log\.d\(` returns zero hits in the lines added/modified.

**Status:** `[x] done`

**Step Log:**

- 2026-05-13 — Verification 6/6 PASS. `applyBigButtonsModeToPlaybackRow` defined + called; `bigButtonsMode` param added; `playbackButtonRow` id added to portrait + landscape layouts. Dev log recorded.

---

### Step 04.4 — Verify end-to-end: preference → player displays big buttons

**Files:** none (verification only)
**Depends on:** Steps 04.1–04.3

**Prompt for developer:**

> 1. In `PlaybackSettingsFragment`, enable the Big Buttons Mode toggle.
> 2. Open any media file in the player.
> 3. Confirm via logcat that `Timber.d("S0158: …")` tags appear (tags are inserted per CLAUDE.md debug verification rules; they will be inserted when the ticket moves to `BlockNeedUserTest` — do not add them manually here).
> 4. Confirm visually:
>    - Top command panel height is approximately 2× standard height.
>    - Top panel buttons are distributed equally across full screen width.
>    - Overflow menu (if present) shows items with doubled row height and full labels.
>    - Bottom playback button row height is approximately 2× standard height, buttons equally distributed.
>    - In landscape: same layout applies (both panels).
>    - Disabling the toggle and reopening the player reverts to standard layout.

**Verification:**

- Compile: run `/build` — zero errors.
- Visual: top panel 2× height, full-width equal buttons observed in debug build.
- Visual: bottom playback row 2× height, full-width equal buttons observed.
- Visual: overflow menu items 2× height when big buttons mode is active.
- Visual: disabling mode → standard layout on next player open.

**Status:** `[x] done`

**Step Log:**

- 2026-05-13 — Build: PASS (`BUILD SUCCESSFUL in 1m 26s`). Visual verification deferred to `BlockNeedUserTest` phase.

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for each modified file via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated: `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` then `render.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Phase 04 delivers the complete feature. Phase 05 finalises docs and catalog. After Phase 05, run `/spec-check S0158`.

---

## Rollback Plan

Revert phase commit(s). Restore backups from `temp/` if needed. No data migration — SharedPreferences key is additive (absence defaults to `false`).
