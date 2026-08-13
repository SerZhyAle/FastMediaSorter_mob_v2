# Phase 02 — Browse Delete Fix

**Strategic spec:** [`../S0071_use-trash-setting.md`](../S0071_use-trash-setting.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** —
**Completed:** —

---

## Objective

Make `BrowseDeleteManager.deleteSelectedFiles()` respect `AppSettings.useTrash`: inject `SettingsRepository`, compute `effectiveSoftDelete = useTrash && canUseSoftDelete`, suppress `UndoOperation` when hard-deleting.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Context

**Root cause:** `BrowseDeleteManager` computes `softDelete = canUseSoftDelete` (line 92–107) using only path type, without reading `AppSettings.useTrash`. `BrowseViewModel` already holds `SettingsRepository` as an injected field but does not pass it to `BrowseDeleteManager`.

**Bug confirmed in log:** lines 1267–1277 show `softDelete=true` for local files even though `useTrash` was not consulted.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseDeleteManager.kt` | Modified | ≤ 320 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseViewModel.kt` | Modified | ≤ 800 |

> `BrowseViewModel.kt` is >500 lines — create a timestamped backup in `temp/` before editing.

---

## Steps

### Step 2.1 — Add `settingsRepository` param to BrowseDeleteManager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseDeleteManager.kt`

**Depends on:** — start of phase

**Prompt for developer:**

> In `BrowseDeleteManager`, add a new constructor parameter after the existing `context` parameter:
>
> ```kotlin
> private val settingsRepository: com.sza.fastmediasorter.domain.repository.SettingsRepository,
> ```
>
> Add the required import at the top of the file:
>
> ```kotlin
> import com.sza.fastmediasorter.domain.repository.SettingsRepository
> import kotlinx.coroutines.flow.first
> ```

**Verification:**

- `Grep` — `settingsRepository: SettingsRepository` present in `BrowseDeleteManager.kt`.
- `Grep` — `import com.sza.fastmediasorter.domain.repository.SettingsRepository` present in `BrowseDeleteManager.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-03 — Verification 2/2 PASS. Files: BrowseDeleteManager.kt. Dev log deferred to phase end.

---

### Step 2.2 — Apply useTrash in deleteSelectedFiles()

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseDeleteManager.kt`

**Depends on:** Step 2.1

**Prompt for developer:**

> In `deleteSelectedFiles()`, replace the two lines:
>
> ```kotlin
> val canUseSoftDelete = DeletePathPolicy.canUseSoftDelete(selectedPaths)
> ```
>
> with:
>
> ```kotlin
> val canUseSoftDelete = DeletePathPolicy.canUseSoftDelete(selectedPaths)
> val useTrash = settingsRepository.getSettings().first().useTrash
> val effectiveSoftDelete = useTrash && canUseSoftDelete
> ```
>
> Then replace all subsequent references to `canUseSoftDelete` used as the `softDelete` argument with `effectiveSoftDelete`:
>
> ```kotlin
> val deleteOperation = FileOperation.Delete(
>     files = filesToDelete,
>     softDelete = effectiveSoftDelete          // was: canUseSoftDelete
> )
> ```
>
> Also update the Undo guard: replace the existing `if (canUseSoftDelete)` condition (which wraps the `saveUndoOperation` call inside the `FileOperationResult.Success` branch) with `if (effectiveSoftDelete)`:
>
> ```kotlin
> if (effectiveSoftDelete) {   // was: if (canUseSoftDelete)
>     val undoOp = UndoOperation(...)
>     saveUndoOperation(undoOp)
> }
> ```

**Verification:**

- `Grep` — `effectiveSoftDelete` present in `BrowseDeleteManager.kt`.
- `Grep` — `softDelete = canUseSoftDelete` returns zero hits in `BrowseDeleteManager.kt` (old form removed).
- `Grep` — `if (effectiveSoftDelete)` present in `BrowseDeleteManager.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `BrowseDeleteManager.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-03 — Verification 3/3 PASS. Files: BrowseDeleteManager.kt. Dev log deferred to phase end.

---

### Step 2.3 — Pass settingsRepository from BrowseViewModel

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseViewModel.kt`

**Depends on:** Step 2.1

**Prompt for developer:**

> `BrowseViewModel` already holds `settingsRepository: SettingsRepository` as a constructor-injected field. In the `deleteManager` instantiation block (around line 239), add the new parameter:
>
> ```kotlin
> private val deleteManager = com.sza.fastmediasorter.ui.browse.managers.BrowseDeleteManager(
>     context = context,
>     settingsRepository = settingsRepository,   // ADD THIS LINE
>     fileOperationUseCase = fileOperationUseCase,
>     ...
> )
> ```

**Verification:**

- `Grep` — `settingsRepository = settingsRepository` present inside the `BrowseDeleteManager(` constructor call in `BrowseViewModel.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `BrowseViewModel.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-03 — Verification 2/2 PASS. Files: BrowseViewModel.kt (+1 LOC). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every Step 02.* above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

`BrowseDeleteManager` now honours `useTrash`. Phase 03 applies the same fix to the player path, which is independent — can run in parallel with Phase 02 if desired, but is gated on Phase 01 (which both depend on).

---

## Rollback Plan

Revert phase commit(s). No schema or data changes. The `useTrash` DataStore key defaults to `true`, so rollback restores the pre-fix behaviour (always soft-delete for local files).
