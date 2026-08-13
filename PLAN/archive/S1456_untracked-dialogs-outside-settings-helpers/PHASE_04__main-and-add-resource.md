# Phase 04 - Main screen and add-resource

**Strategic spec:** [`../S1456_untracked-dialogs-outside-settings-helpers.md`](../S1456_untracked-dialogs-outside-settings-helpers.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 07
**Steps done:** 3 / 3
**Started:** 2026-08-09
**Completed:** 2026-08-09

---

## Objective

Bind the dialogs of the main screen and of the add-resource wizard (11 files, 22 sites measured 2026-08-09).

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] `assert-untracked-dialogs.ps1 -List` re-read; edit what it prints.
- [ ] `CODE.LOCK` acquired immediately before each step and released right after.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/*.kt` (2 files) | Modified | ≤ 1500 each |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/*.kt` (8 files) | Modified | ≤ 1500 each |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt` | Modified | ≤ 1500 |

---

## Steps

### Step 04.1 - Bind the add-resource dialogs

**Files:** `ui/addresource/AddResourceConnectionManager.kt`, `ui/addresource/AddResourceScanManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Bind the twelve builder chains in these two managers. Both are the densest single files in the sweep, so read each chain's terminator from the gate's `-List` line numbers rather than by eye, and confirm the count drops by exactly twelve.

**Why:**

Strategic §1 measures twelve sites in these two files, the highest concentration outside the player family, which is why they get a step of their own.

**Verification:**

- `assert-untracked-dialogs.ps1 -List` prints no row under `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/`.
- `Grep` - `showBoundTo` matches in both files.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

### Step 04.2 - Bind the main-screen helper dialogs

**Files:** `ui/main/helpers/CrashReportPromptManager.kt`, `MainEventHandler.kt`, `MainLinkDownloadManager.kt`, `MainPanelItemActionsManager.kt`, `MainSftpShareManager.kt`, `MainStoragePermissionsHelper.kt`, `ResourceDeleteConfirmation.kt`, `ResourcePasswordManager.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Bind the nine builder chains in these eight helpers, using the fragment or activity each already holds. `ResourceDeleteConfirmation` and `ResourcePasswordManager` are called from more than one screen: pass the owner in from the caller rather than reaching for a stored context.

**Why:**

Strategic §5 requires the owner to be the host that is actually about to die, and a helper shared by two screens has no single stored host to bind to.

**Verification:**

- `assert-untracked-dialogs.ps1 -List` prints no row under `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/`.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

### Step 04.3 - Bind the MainActivity dialog

**Files:** `ui/main/MainActivity.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Bind the single builder chain in `MainActivity` with `.showBoundTo(this)`.

**Why:**

Strategic §5 makes `this` the owner inside an Activity.

**Verification:**

- `assert-untracked-dialogs.ps1 -List` prints no row for `ui/main/MainActivity.kt`.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] `assert-untracked-dialogs.ps1 -List` prints no row under `ui/main/` or `ui/addresource/`.
- [ ] `pwsh -NoProfile -File scripts/quality/assert-untracked-dialogs.ps1 -UpdateBaseline` ratchets the baseline down.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] Dev log entry added for the phase via `.\scripts\add_to_dev_log.ps1`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

## Step Log

- 2026-08-09 - 04.1 to 04.3 PASS. 22 edits across 11 files through `temp/S1456/apply-binding.ps1` with `temp/S1456/step-04.csv`; no import block needed reordering, and no site used the returned dialog, so nothing followed the edit.
- 2026-08-09 - `.\a.ps1 fk` BUILD SUCCESSFUL in 44s. Baseline ratcheted 86 -> 64.
- 2026-08-09 - Phase-boundary audit, Layers 1-3: no P0/P1. The twelve add-resource sites are the densest group in the sweep and were checked one by one against the gate's line numbers rather than by eye.
- 2026-08-09 - `post-change.ps1 -ScopeToFile` over the 11-file set: `post-change: PASS`, exit 0.

---
## Handoff Notes to Next Phase

A helper reachable from two screens takes its owner as a parameter; that precedent covers the shared confirmation dialogs met again in Phase 06.

---

## Rollback Plan

Revert the phase commits - dialog ownership only, no data migration and no user-facing surface change.
