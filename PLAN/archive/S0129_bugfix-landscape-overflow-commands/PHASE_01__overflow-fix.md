# Phase 01 — Overflow Fix

**Strategic spec:** [`../S0129_bugfix-landscape-overflow-commands.md`](../S0129_bugfix-landscape-overflow-commands.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02
**Steps done:** 3 / 3
**Started:** 2026-05-09
**Completed:** 2026-05-09

---

## Objective

Replace the unconditional overflow suppression in the landscape branch of `CommandPanelController` with a planner-driven approach that exposes overflow-only commands (CROP, CROP_TO_FILE, COMPRESS_COPY, DRAW_OVERLAY, and any other `barCapable = false` commands) via the already-present `btnOverflowMenu` button.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt` | Modified | ≤ 1 060 |

> File is 1 054 lines — exceeds 500 LOC threshold. Backup step required before edits.

---

## Steps

### Step 01.1 — Backup CommandPanelController before edit

**Files:** `temp/`
**Depends on:** — start of phase

**Prompt for developer:**

> Create a timestamped backup of `CommandPanelController.kt` in `temp/` before any modification.
> Run from repo root:
> ```powershell
> Copy-Item `
>   "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt" `
>   "temp/CommandPanelController_$(Get-Date -Format 'yyyyMMdd_HHmmss').kt"
> ```

**Verification:**

- `Glob` — `temp/CommandPanelController_*.kt` returns at least one file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 — Verification 1/1 PASS. Backup: `temp/CommandPanelController_20260509_144842.kt`.

---

### Step 01.2 — Remove unconditional overflow suppression from the landscape branch

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `CommandPanelController.kt`, in the `else if (showInLandscape)` block (function `updateCommandAvailability`), the first two statements unconditionally hide the overflow button and clear `latestOverflowCommands`:
>
> ```kotlin
> safeViews.btnOverflowMenu.isVisible = false
> latestOverflowCommands = emptyList()
> ```
>
> Remove these two lines from the top of the landscape block. They will be replaced by the computed assignment in Step 01.3.

**Verification:**

- `Grep` — pattern `safeViews\.btnOverflowMenu\.isVisible = false` inside the `showInLandscape` block returns **zero** hits (the `else` / panel-hidden block at line ~439 retains its own copy — that one must remain).
- `Grep` — pattern `latestOverflowCommands = emptyList\(\)` inside the `showInLandscape` block returns **zero** hits (the `else` block retains its own copy).

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 — Verification 2/2 PASS. Both suppression lines removed from landscape block; `else` block retains its own copies unchanged.

---

### Step 01.3 — Compute and expose landscape overflow commands

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> At the **end** of the `showInLandscape` block, immediately after the final `safeViews.btnPrintCmd.isVisible = …` assignment, insert the following block:
>
> ```kotlin
> // S0129: expose overflow-only commands (barCapable=false) in landscape via the ⋯ button
> val landscapeOverflowCmds = planner.buildActiveCommands(
>     state, canWrite, canRead, isWifiConnected(binding.root.context),
>     showFavorite = lastKnownFavoriteVisible,
>     showRandom = showRandomNavigation,
>     allowSeparateWindow = lastKnownAllowSeparateWindow
> ).filter { !it.barCapable }
> latestOverflowCommands = landscapeOverflowCmds
> safeViews.btnOverflowMenu.isVisible = landscapeOverflowCmds.isNotEmpty()
> Timber.d("S0129: landscape overflow - ${landscapeOverflowCmds.size} items for ${currentFile.type}")
> ```
>
> No other changes to the landscape block.

**Verification:**

- `Grep` — pattern `S0129: landscape overflow` present in `CommandPanelController.kt`.
- `Grep` — pattern `landscapeOverflowCmds\.isNotEmpty\(\)` present in `CommandPanelController.kt`.
- `Grep` — pattern `filter \{ !it\.barCapable \}` present in `CommandPanelController.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `CommandPanelController.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 — Verification 4/4 PASS. `S0129: landscape overflow` tag, `isNotEmpty()` guard, `filter { !it.barCapable }`, zero `Log.d` hits. Build SUCCESSFUL.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for `CommandPanelController.kt` via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- `CommandPanelController.updateCommandAvailability` now computes `latestOverflowCommands` in landscape and shows `btnOverflowMenu` when non-empty.
- The existing `showOverflowMenu()` popup mechanism is unchanged — it reads `latestOverflowCommands` as-is, so all overflow commands automatically route through the existing `onMenuItemSelected` dispatch at lines 856–859.

---

## Rollback Plan

Revert phase commit(s) — no data migration or user-facing surface changed. Backup in `temp/` available if patch needs manual reverting.
