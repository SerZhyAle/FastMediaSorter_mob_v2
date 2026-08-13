# Phase 03 — Player Delete Fix

**Strategic spec:** [`../S0071_use-trash-setting.md`](../S0071_use-trash-setting.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** —
**Completed:** 2026-05-03

---

## Objective

Make `PlayerDeleteUndoCoordinator.deleteCurrentFile()` respect `AppSettings.useTrash`: use the already-injected `SettingsRepository` to read the flag, compute `effectiveSoftDelete`, and suppress the Undo snackbar when performing a hard delete.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Context

**Root cause:** `PlayerDeleteUndoCoordinator` already injects `SettingsRepository` (constructor param, line 37) and even reads from it after the delete — but only to check `enableUndo`, not `useTrash`. Line 85 hard-codes `softDelete = !isNetwork`, ignoring the user setting.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerDeleteUndoCoordinator.kt` | Modified | ≤ 340 |

> File is >300 lines — create a timestamped backup in `temp/` before editing.

---

## Steps

### Step 3.1 — Compute effectiveSoftDelete from settings in deleteCurrentFile()

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerDeleteUndoCoordinator.kt`

**Depends on:** — start of phase

**Prompt for developer:**

> In `deleteCurrentFile()`, inside the `scope.launch` block, after the `isNetwork` boolean is computed (around line 72), read `useTrash` from the already-injected `settingsRepository`:
>
> ```kotlin
> val useTrash = settingsRepository.getSettings().first().useTrash
> val effectiveSoftDelete = useTrash && !isNetwork
> ```
>
> Then on line 85, replace:
>
> ```kotlin
> val deleteOperation = FileOperation.Delete(files = listOf(file), softDelete = !isNetwork)
> ```
>
> with:
>
> ```kotlin
> val deleteOperation = FileOperation.Delete(files = listOf(file), softDelete = effectiveSoftDelete)
> ```

**Verification:**

- `Grep` — `effectiveSoftDelete` present in `PlayerDeleteUndoCoordinator.kt`.
- `Grep` — `softDelete = !isNetwork` returns zero hits in `PlayerDeleteUndoCoordinator.kt` (old form removed).

**Status:** `[x] done`

**Step Log:**

- 2026-05-03 — Verification 2/2 PASS. Files: PlayerDeleteUndoCoordinator.kt. Dev log deferred to phase end.

---

### Step 3.2 — Suppress Undo snackbar when hard-deleting

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerDeleteUndoCoordinator.kt`

**Depends on:** Step 3.1

**Prompt for developer:**

> Locate the `Success`/`PartialSuccess` branch inside `deleteCurrentFile()` (around line 92). The existing undo guard reads:
>
> ```kotlin
> if (settings.enableUndo && !isNetwork) {
> ```
>
> Replace it with:
>
> ```kotlin
> if (settings.enableUndo && effectiveSoftDelete) {
> ```
>
> This ensures the Undo snackbar is only shown when the file actually went to `.trash/` (i.e., soft-delete was used). When `useTrash=false`, `effectiveSoftDelete` is `false` for local files too, so no Undo is offered.
>
> Note: `settings` is already fetched on line 92 (`settingsRepository.getSettings().first()`). Move the `useTrash` read from Step 3.1 to reuse this single `settings` read — call it before creating `deleteOperation`:
>
> ```kotlin
> // Before deleteOperation creation:
> val settings = settingsRepository.getSettings().first()
> val useTrash = settings.useTrash
> val effectiveSoftDelete = useTrash && !isNetwork
> val deleteOperation = FileOperation.Delete(files = listOf(file), softDelete = effectiveSoftDelete)
>
> // In the result handler, reuse the same `settings`:
> if (settings.enableUndo && effectiveSoftDelete) { ... }
> ```
>
> Remove the now-redundant second `settingsRepository.getSettings().first()` call (the one that was on line 92).

**Verification:**

- `Grep` — `settings.enableUndo && effectiveSoftDelete` present in `PlayerDeleteUndoCoordinator.kt`.
- `Grep` — `settings.enableUndo && !isNetwork` returns zero hits in `PlayerDeleteUndoCoordinator.kt` (old form removed).
- `Grep` — `getSettings().first()` appears exactly once inside `deleteCurrentFile` in `PlayerDeleteUndoCoordinator.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `PlayerDeleteUndoCoordinator.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-03 — Verification 4/4 PASS. Files: PlayerDeleteUndoCoordinator.kt. Backup: temp/PlayerDeleteUndoCoordinator_20260503_234639.kt.backup. Dev log recorded.

---

## Phase Done Criteria

- [ ] Every Step 03.* above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Both `BrowseDeleteManager` (Phase 02) and `PlayerDeleteUndoCoordinator` (this phase) now honour `useTrash`. Phase 04 updates docs and regenerates the catalog.

---

## Rollback Plan

Revert phase commit(s). No schema or data changes. `useTrash` defaults to `true`, so rollback restores previous behaviour (always soft-delete for local files in the player).
