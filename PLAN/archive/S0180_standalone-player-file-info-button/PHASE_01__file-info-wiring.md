# Phase 01 — File-Info Wiring

**Strategic spec:** [`../S0180_standalone-player-file-info-button.md`](../S0180_standalone-player-file-info-button.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 4 / 4
**Started:** 2026-05-13
**Completed:** 2026-05-13

---

## Objective

Restore `btnInfoCmd` to its "File Info" semantics in `StandalonePlayerActivity` and wire it to `FileInfoDialog`; fix the keyboard `onShowFileInfo` callback to use the same path. No layout changes — `btnInfoCmd` already exists in both portrait and landscape layouts via `ActivityPlayerUnifiedBinding`.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] Confirm current branch: `git branch --show-current` → `DEBUG-v001` (or feature branch).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt` | Modified | ≤ 1100 |

> File is 1044 lines — exceeds 500-line threshold. **Backup required** (Step 01.1).
> Landscape parity: no layout XML edits in this phase. `btnInfoCmd` is already present in `res/layout-land/activity_player_unified.xml` (line 105). The Kotlin fix applies to both orientations via the single binding.

---

## Steps

### Step 01.1 — Backup `StandalonePlayerActivity.kt`

**Files:** `temp/`
**Depends on:** — start of phase

**Prompt for developer:**

> Create a timestamped backup of `StandalonePlayerActivity.kt` in `temp/` before any edits:
> `cp app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt temp/StandalonePlayerActivity_<YYYYMMDD_HHMMSS>.kt.backup`

**Verification:**

- `Glob` — at least one file matching `temp/StandalonePlayerActivity_*.kt.backup` exists.

**Status:** `[x] done`

**Step Log:**
- 2026-05-13 — Verification 1/1 PASS. Files: temp/StandalonePlayerActivity_20260513_153858.kt.backup.

---

### Step 01.2 — Implement `showFileInfo()` in `StandalonePlayerActivity`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a private method `showFileInfo()` to `StandalonePlayerActivity`. It must:
> - Read the current `MediaFile` from `viewModel`'s state (the same object resolved in `observeViewModelState()`). If null, return immediately — do not show the dialog.
> - Construct and show `FileInfoDialog(context = this, file = <currentFile>, downloadUseCase = null)` via `show(supportFragmentManager, FileInfoDialog.TAG)` (or the dialog's companion TAG constant, whichever it exposes).
> - Use `Timber.d(...)` for any debug logging — never `Log.d`.
>
> Pattern reference: `PlayerDialogHelper.showFileInfo(file)` (lines 351–360) and `PlayerDialogAndUiStateManager.showFileInfo()` (line 523) for how the normal player constructs the same dialog.

**Verification:**

- `Grep` — `fun showFileInfo()` matches in `StandalonePlayerActivity.kt`.
- `Grep` — `FileInfoDialog` is referenced in `StandalonePlayerActivity.kt`.
- `Grep` — `downloadUseCase = null` (or positional null) passed to `FileInfoDialog` constructor in that file.
- `Grep -n "Log\.d\("` — zero hits in `StandalonePlayerActivity.kt`.

**Status:** `[x] done`

**Step Log:**
- 2026-05-13 — Verification 4/4 PASS. Import added (line 78), `showFileInfo()` added (line 766). Dev log deferred to phase end.

---

### Step 01.3 — Restore `btnInfoCmd` to "File Info" semantics

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> In `setupFileOperationButtons()` (around line 756), the four lines that repurpose `btnInfoCmd` for "Open in FMS" must be replaced:
>
> **Remove:**
> ```kotlin
> binding.btnInfoCmd.visibility = View.VISIBLE
> binding.btnInfoCmd.setImageResource(R.drawable.ic_open_in_browse)
> binding.btnInfoCmd.contentDescription = getString(R.string.open_in_fms)
> binding.btnInfoCmd.setOnClickListener { openInFms() }
> ```
>
> **Replace with** (keep the button visible and wire it to file info):
> ```kotlin
> binding.btnInfoCmd.setImageResource(R.drawable.ic_info)
> binding.btnInfoCmd.contentDescription = getString(R.string.file_information)
> binding.btnInfoCmd.setOnClickListener { showFileInfo() }
> ```
>
> Keep `visibility = View.VISIBLE` if it was previously absent from the layout default (the layout sets `visibility="gone"` — so the explicit `.visibility = View.VISIBLE` line is still required). Guard the click in `showFileInfo()` (Step 01.2) against null file — no additional null guard needed at the click site.

**Verification:**

- `Grep` — `ic_open_in_browse` does NOT appear in `StandalonePlayerActivity.kt` (the repurposing is gone).
- `Grep` — `ic_info` appears in `StandalonePlayerActivity.kt` near `btnInfoCmd`.
- `Grep` — `binding.btnInfoCmd.setOnClickListener` in `StandalonePlayerActivity.kt` calls `showFileInfo()`.

**Status:** `[x] done`

**Step Log:**
- 2026-05-13 — Verification 3/3 PASS. Lines 755-760 updated.

---

### Step 01.4 — Fix keyboard `onShowFileInfo` callback

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> In `setupKeyboardHandler()` (around line 313), the `onShowFileInfo` lambda currently builds a `MaterialAlertDialogBuilder` with just the file name and path. Replace its body with a single call to `showFileInfo()`. The `MaterialAlertDialogBuilder` block in that lambda must be fully removed.

**Verification:**

- `Grep` — `MaterialAlertDialogBuilder` does NOT appear inside the `onShowFileInfo` lambda in `StandalonePlayerActivity.kt`.
- `Grep` — the `onShowFileInfo` callback body in `StandalonePlayerActivity.kt` calls `showFileInfo()`.

**Status:** `[x] done`

**Step Log:**
- 2026-05-13 — Verification 2/2 PASS. `onShowFileInfo` delegates to `showFileInfo()` (line 313). Unused `MaterialAlertDialogBuilder` import removed (lint).

---

## Phase Done Criteria

- [ ] Every Step 01.* above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for `StandalonePlayerActivity.kt` via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- `btnInfoCmd` now shows `FileInfoDialog` in both portrait and landscape (same binding, same click handler).
- `openInFms()` call is currently unreachable from the UI — Phase 02 must restore it via overflow menu.
- `showFileInfo()` method is established and callable from keyboard handler, gesture callbacks, or any future entry point.

---

## Rollback Plan

Revert phase commit(s). No data migration or persistent state changed. `btnInfoCmd` repurposing was cosmetic (icon + listener swap).
