# Phase 01 — Orphaned Scan Job Fix

**Strategic spec:** [`../S0114_bugfix-smb-scan-subfolders-flat-list.md`](../S0114_bugfix-smb-scan-subfolders-flat-list.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 3 / 3
**Started:** 2026-05-08
**Completed:** 2026-05-08

---

## Objective

Cancel any in-flight scan job inside `BrowseResourceLoadManager` before launching a new one, so that progress events from a previous scan cannot pollute the UI after the current load cycle returns cached data.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done. _(none)_
- [ ] Working tree is clean or on a feature branch.
- [ ] `BrowseResourceLoadManager.kt` is backed up to `temp/` (file is at 501 LOC — backup required per project rules).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseResourceLoadManager.kt` | Modified | ≤ 520 |

---

## Steps

### Step 1.1 — Backup `BrowseResourceLoadManager.kt` before editing

**Files:** `temp/`
**Depends on:** — start of phase

**Prompt for developer:**

> Create a timestamped backup of `BrowseResourceLoadManager.kt` in `temp/` before any edits. File is 501 LOC — project rules require backup for files ≥ 500 LOC.

```powershell
$ts = Get-Date -Format "yyyyMMdd_HHmmss"
Copy-Item `
  "app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseResourceLoadManager.kt" `
  "temp/BrowseResourceLoadManager_$ts.kt"
```

**Verification:**

- `Glob` — `temp/BrowseResourceLoadManager_*.kt` returns at least one match.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 1/1 PASS. Files: temp/BrowseResourceLoadManager_20260508_022534.kt. Dev log recorded.

---

### Step 1.2 — Add `currentScanJob` field and cancel-before-launch in `loadMediaFiles()`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseResourceLoadManager.kt`
**Depends on:** Step 1.1

**Prompt for developer:**

> Add a private nullable `currentScanJob: Job?` field to `BrowseResourceLoadManager`. At the top of `loadMediaFiles()`, before `scope.launch { ... }`, cancel the previous job:
>
> ```kotlin
> currentScanJob?.cancel()
> shouldStopScanRef.set(false)
> ```
>
> After `scope.launch { ... }` returns a `Job`, assign it to both `currentScanJob` and the existing `setLoadFilesJobRef` callback so `BrowseViewModel` stays in sync:
>
> ```kotlin
> val filesJob = scope.launch(ioDispatcher + exceptionHandler) { ... }
> currentScanJob = filesJob
> setLoadFilesJobRef(filesJob)
> ```
>
> Add a `Timber.d("S0114: loadMediaFiles — cancelled previous scan, starting fresh")` tag at the point of cancellation (inside the `if (currentScanJob?.isActive == true)` guard).

**Verification:**

- `Grep` — `private var currentScanJob: Job?` present in `BrowseResourceLoadManager.kt`.
- `Grep` — `currentScanJob?.cancel()` present in `BrowseResourceLoadManager.kt`.
- `Grep` — `currentScanJob = filesJob` present in `BrowseResourceLoadManager.kt`.
- `Grep` — `Timber.d("S0114:` present in `BrowseResourceLoadManager.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `BrowseResourceLoadManager.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 5/5 PASS. Files: BrowseResourceLoadManager.kt (+6 LOC). Dev log recorded.

---

### Step 1.3 — Clear `currentScanJob` on `cancelLoad()`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseResourceLoadManager.kt`
**Depends on:** Step 1.2

**Prompt for developer:**

> In `BrowseResourceLoadManager.cancelLoad()`, after cancelling the passed-in job, also null out `currentScanJob`:
>
> ```kotlin
> fun cancelLoad(loadFilesJob: Job?) {
>     loadFilesJob?.cancel()
>     currentScanJob?.cancel()
>     currentScanJob = null
>     shouldStopScanRef.set(true)
> }
> ```
>
> This ensures that after an explicit user-triggered stop the field is clean for the next `loadMediaFiles()` call.

**Verification:**

- `Grep` — `currentScanJob = null` present in `BrowseResourceLoadManager.kt` inside `cancelLoad`.
- `Grep` — `Log\.d\(` returns zero hits in `BrowseResourceLoadManager.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 2/2 PASS. Files: BrowseResourceLoadManager.kt (+2 LOC). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 1.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for `BrowseResourceLoadManager.kt` via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

`BrowseResourceLoadManager` now guarantees at most one active scan job. Phase 02 addresses the cache validity issue that can still cause the UI to show stale data when `scanSubdirectories` was changed between sessions.

---

## Rollback Plan

Revert phase commit(s). `currentScanJob` field and cancel call are additive — no data migration or persistent state changed.
