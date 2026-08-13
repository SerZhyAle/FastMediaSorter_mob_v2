# Phase 01 — Path-Based File Removal in PlayerViewModel

**Strategic spec:** [`../S0094_player-move-currently-playing.md`](../S0094_player-move-currently-playing.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02
**Steps done:** 3 / 3
**Started:** 2026-05-05
**Completed:** 2026-05-05

---

## Objective

Replace index-based file removal in `PlayerViewModel` with path-based lookup so that post-move list reconciliation removes the correct entry regardless of how far the user has navigated since initiating the operation.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done. *(foundation — no dependency)*
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt` | Modified | ≤ 713 |

> File is 713 lines — backup required before edit.

---

## Steps

### Step 01.1 — Backup PlayerViewModel before changes

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create a timestamped backup of `PlayerViewModel.kt` in `temp/` before any edits.
> Run: `Copy-Item "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt" "temp/PlayerViewModel_$(Get-Date -Format 'yyyyMMdd_HHmmss').kt"`

**Verification:**

- `Glob` — `temp/PlayerViewModel_*.kt` returns at least one match.

**Status:** `[x] done`

**Step Log:**
- 2026-05-05 — Verification 1/1 PASS. Backup: temp/PlayerViewModel_20260505_151114.kt.

---

### Step 01.2 — Rewrite `removeFileFromList` to use path-based lookup

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Replace the private `removeFileFromList(filePath: String, operation: String): Boolean` method body.
>
> Current implementation removes by `currentState.currentIndex` (ignoring `filePath`). This is wrong after optimistic navigation: the current index already points to the next track, so removal would delete the wrong file.
>
> New implementation:
> 1. Find `removeIndex = updatedFiles.indexOfFirst { it.path == filePath }`.
> 2. If `removeIndex == -1`: log a warning and return `updatedFiles.isNotEmpty()` (file already absent — treat as success).
> 3. Remove at `removeIndex`.
> 4. If list is now empty: return `false`.
> 5. Adjust `currentIndex`:
>    - If `currentIndex > removeIndex` → `(currentIndex - 1).coerceAtLeast(0)`.
>    - If `currentIndex >= updatedFiles.size` (was pointing at last, now out of bounds) → `0`.
>    - Otherwise → `currentIndex` unchanged.
> 6. `updateState { it.copy(files = updatedFiles, currentIndex = newIndex) }`, `saveResumeState()`.
> 7. Log: `"File $operation removed by path at index=$removeIndex, new size=${updatedFiles.size}, currentIndex=$newIndex"`.
> 8. Return `true`.
>
> The public signatures of `removeMovedFile(movedFilePath)` and `removeDeletedFile(deletedFilePath)` are unchanged.

**Verification:**

- `Grep` — `indexOfFirst { it.path == filePath }` present in `PlayerViewModel.kt`.
- `Grep` — `currentState.currentIndex\s*//.*ignores` is NOT present (old comment removed).
- `Grep` — `coerceAtLeast(0)` present in `PlayerViewModel.kt`.
- `Grep` — `Log\.d(` returns zero hits in `PlayerViewModel.kt`.

**Status:** `[x] done`

**Step Log:**
- 2026-05-05 — Verification 3/3 PASS. Files: PlayerViewModel.kt (path-based lookup, coerceAtLeast, no Log.d). Dev log recorded.

---

### Step 01.3 — Dev log entry

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 01.2

**Prompt for developer:**

> Run:
> ```
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt" "S0094 Phase 01" "Fix removeFileFromList to use path-based lookup instead of currentIndex"
> ```

**Verification:**

- `Grep` — `S0094 Phase 01` present in `dev/CHANGELOG.md`.

**Status:** `[x] done`

**Step Log:**
- 2026-05-05 — Verification 1/1 PASS. Dev log line `S0094 Phase 01` found in CHANGELOG.md.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added (Step 01.3).

---

## Handoff Notes to Next Phase

`removeMovedFile(path)` and `removeDeletedFile(path)` now find the target by path, not by position. Phase 02 may safely call `removeMovedFile` after optimistic navigation without risking removal of the wrong entry.

---

## Rollback Plan

Revert phase commit(s) — no data migration or user-facing surface changed.
