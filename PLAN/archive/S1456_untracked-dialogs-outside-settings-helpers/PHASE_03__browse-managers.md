# Phase 03 - Browse managers

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

Bind every dialog raised from `ui/browse/managers/` in both the shared and the `noLegal` source set (14 files, 22 sites measured 2026-08-09).

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] `assert-untracked-dialogs.ps1 -List` re-read; edit what it prints.
- [ ] `CODE.LOCK` acquired immediately before each step and released right after.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/*.kt` (13 files) | Modified | ≤ 1500 each |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/browse/managers/BrowseApkInstallHandlerImpl.kt` | Modified | ≤ 1500 |

---

## Steps

### Step 03.1 - Bind the browse dialog managers

**Files:** `ui/browse/managers/BrowseArchiveDialogManager.kt`, `BrowseDeleteDialogManager.kt`, `BrowseFeedbackDialogManager.kt`, `BrowseFilterDialogManager.kt`, `BrowseRenameDialogManager.kt`, `BrowseSortDialogManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Replace the terminating `.show()` of each builder chain in these six managers with `.showBoundTo(fragment)`, taking the fragment the manager already holds. Where a manager keeps its own dialog field and dismisses it by hand, drop the hand-rolled dismissal that the binding now covers, but keep any dismissal that also clears manager state.

**Why:**

Strategic §7 records that a site already holding and dismissing its dialog gains a second closer after the sweep, so the redundant code is removed in the same edit rather than left to accumulate.

**Verification:**

- `assert-untracked-dialogs.ps1 -List` prints no row for these six files.
- `Grep` - `showBoundTo` matches in each of the six files.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

### Step 03.2 - Bind the remaining browse managers

**Files:** `ui/browse/managers/BrowseCameraCaptureManager.kt`, `BrowseEventHandler.kt`, `BrowseFileOperationsManager.kt`, `BrowseLifecycleHelper.kt`, `BrowseManagerInitializer.kt`, `BrowseMicRecordingManager.kt`, `ResourceOpsMenuManager.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Bind the nine builder chains in these seven files, using the fragment each manager was constructed with. `BrowseManagerInitializer` constructs other managers rather than owning a screen: pass the owner it already has through to the site instead of widening any signature.

**Why:**

Strategic §5 takes the owner from what the site already holds, and the initializer is the one file here that holds the fragment on behalf of others.

**Verification:**

- `assert-untracked-dialogs.ps1 -List` prints no row for these seven files.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

### Step 03.3 - Bind the noLegal APK install dialog

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/browse/managers/BrowseApkInstallHandlerImpl.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Bind the single builder chain in the `noLegal` implementation, taking the owner the handler already receives. Verify the flavor source set compiles, not only `standard`.

**Why:**

Strategic §1 records that the leak is not confined to `src/main`, and a flavor-only file is invisible to a `standard` compile.

**Verification:**

- `assert-untracked-dialogs.ps1 -List` prints no row under `app_v2/src/noLegal/`.
- `.\a.ps1 fkn` exits 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] `assert-untracked-dialogs.ps1 -List` prints no row under `ui/browse/`.
- [ ] `pwsh -NoProfile -File scripts/quality/assert-untracked-dialogs.ps1 -UpdateBaseline` ratchets the baseline down.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] Dev log entry added for the phase via `.\scripts\add_to_dev_log.ps1`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

## Step Log

- 2026-08-09 - 03.1 to 03.3 PASS. 22 edits across 14 files applied through `temp/S1456/apply-binding.ps1` with `temp/S1456/step-03.csv`; four import blocks reordered.
- 2026-08-09 - Six compile errors followed, all the same shape: `showBoundTo*` returns a nullable dialog and three sites use the result. `BrowseArchiveDialogManager` and `ResourceOpsMenuManager` now reach the button through `?.`, and `BrowseFeedbackDialogManager` wraps the accessibility-focus call in `?.let`. The null case is "the host was already destroyed", where skipping the follow-up wiring is the correct behaviour.
- 2026-08-09 - `.\a.ps1 fk` and `.\a.ps1 fkn` both BUILD SUCCESSFUL - the noLegal compile is what covers `src/noLegal/../BrowseApkInstallHandlerImpl.kt`.
- 2026-08-09 - Phase-boundary audit, Layers 1-3: no P0/P1. No listener, coroutine or Room surface changed; the nullable guards add no new branch that can swallow an error.
- 2026-08-09 - `post-change.ps1 -ScopeToFile` over the 14-file set: `post-change: PASS`, exit 0.

---
## Handoff Notes to Next Phase

Redundant hand-rolled dismissals are removed as they are met, not collected for a later pass; later phases apply the same rule.

---

## Rollback Plan

Revert the phase commits - dialog ownership only, no data migration and no user-facing surface change.
