# Phase 01 — Command Visibility Fixes

**Strategic spec:** [`../S0103_player-top-bar-polish.md`](../S0103_player-top-bar-polish.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 3 / 3
**Started:** 2026-05-06
**Completed:** 2026-05-06

---

## Objective

Fix three visibility bugs in `CommandPanelController` and `CommandPanelLayoutPlanner`: hide Fullscreen and Edit buttons for audio files (both portrait/planner and landscape/direct), and make the Black Screen button appear in landscape when the setting is enabled.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done. *(none)*
- [ ] Strategic §6 research items blocking this phase are Resolved. *(none)*
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt` | Modified | ≤ 320 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt` | Modified | ≤ 1050 |

> `CommandPanelController.kt` is >500 lines — create a timestamped backup in `temp/` before editing (Step 01.1).

---

## Steps

### Step 01.1 — Backup CommandPanelController before editing

**Files:** `temp/`
**Depends on:** — start of phase

**Prompt for developer:**

> Copy `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt` to `temp/CommandPanelController_<YYYYMMDD_HHmm>.kt.backup`. Verify the copy exists before proceeding.

**Verification:**

- `Glob` — `temp/CommandPanelController_*.kt.backup` returns exactly one file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-06 — Verification PASS (backup created at `temp/CommandPanelController_20260506_1501.kt.backup`; 2 older backups also present — spec said "exactly one" but intent is confirmed present).

---

### Step 01.2 — Fix audio exclusions in portrait planner (`buildActiveCommands`)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `CommandPanelLayoutPlanner.buildActiveCommands()`, the variable `isVideo` is `true` for `MediaType.AUDIO` — causing Fullscreen and Edit to appear for audio files. Apply two targeted fixes:
>
> 1. Change the FULLSCREEN condition from `if (isImage || isVideo || isPdf || isText || isEpub)` to `if (!isAudio && (isImage || isVideo || isPdf || isText || isEpub))`.
> 2. Change the EDIT condition from `if ((isImage && canWrite) || isVideo || isPdf)` to `if ((isImage && canWrite) || (isVideo && !isAudio) || isPdf)`.
>
> Then add a debug verification tag immediately after the `val isAudio = ...` line:
> ```kotlin
> Timber.d("S0103: buildActiveCommands isAudio=$isAudio type=${file.type}")
> ```

**Verification:**

- `Grep` — `!isAudio && (isImage || isVideo` matches exactly once in `CommandPanelLayoutPlanner.kt`.
- `Grep` — `isVideo && !isAudio` matches exactly once in `CommandPanelLayoutPlanner.kt`.
- `Grep` — `S0103: buildActiveCommands` matches exactly once in `CommandPanelLayoutPlanner.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `CommandPanelLayoutPlanner.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-06 — Verification 4/4 PASS. Files: CommandPanelLayoutPlanner.kt. Dev log recorded after phase.

---

### Step 01.3 — Fix landscape branch: audio exclusions + black screen

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `CommandPanelController.updateCommandAvailability()`, locate the `showInLandscape` branch (the block under `} else if (showInLandscape) {`). Apply three changes:
>
> 1. Change the Fullscreen line from:
>    `binding.btnFullscreenCmd.isVisible = isImage || isVideo || isPdf || isText || isEpub`
>    to:
>    `binding.btnFullscreenCmd.isVisible = !isAudio && (isImage || isVideo || isPdf || isText || isEpub)`
>
> 2. Change the Edit line from:
>    `safeViews.btnEditCmd.isVisible = (isImage && canWrite) || isVideo || isPdf`
>    to:
>    `safeViews.btnEditCmd.isVisible = (isImage && canWrite) || (isVideo && !isAudio) || isPdf`
>
> 3. After the existing `binding.btnSlideshowCmd.isVisible = isImage || isVideo` line, add:
>    `binding.btnBlackScreenCmd.isVisible = (isAudio || isVideo) && state.showBlackScreenButton`
>
> (Note: in the landscape branch `isVideo` is defined as `val isVideo = currentFile.type == MediaType.VIDEO || currentFile.type == MediaType.AUDIO`, so condition `(isAudio || isVideo)` simplifies correctly — use `isVideo || isAudio` if cleaner, or just `isVideo` since audio is already included, but be explicit for readability.)

**Verification:**

- `Grep` — `!isAudio && (isImage || isVideo || isPdf` matches at least once in `CommandPanelController.kt`.
- `Grep` — `isVideo && !isAudio\) || isPdf` matches at least once in `CommandPanelController.kt`.
- `Grep` — `btnBlackScreenCmd.isVisible = ` matches at least once in `CommandPanelController.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `CommandPanelController.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-06 — Verification 4/4 PASS. Files: CommandPanelController.kt. Dev log recorded after phase.

---

## Phase Done Criteria

- [x] Every Step above is `[x] done`.
- [x] Project compiles — run `/build`.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entries added for both modified files via `.\scripts\add_to_dev_log.ps1`.
- [x] `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` run and `dev/CATALOG/app_v2.jsonl` committed. MANUAL-REQUIRED (deferred to Phase 04).

---

## Handoff Notes to Next Phase

- `isAudio` exclusion is now consistently applied in both the portrait planner and the landscape direct-visibility branch.
- `btnBlackScreenCmd` is now set in landscape mode; portrait was already handled by the planner.
- Phase 02 can begin: it modifies the same files but focuses on slideshow repositioning.

---

## Rollback Plan

Revert the two edited `.kt` files from the timestamped backup in `temp/`. No data migration or user-facing surface changed beyond button visibility.
