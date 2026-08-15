# Phase 02 — Cache Scan-Mode Invalidation

**Strategic spec:** [`../S0114_bugfix-smb-scan-subfolders-flat-list.md`](../S0114_bugfix-smb-scan-subfolders-flat-list.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** —
**Completed:** —

---

## Objective

When a resource's `scanSubdirectories` flag changes and the user saves, invalidate both the in-memory (`MediaFilesCacheManager`) and persistent (`CachedFileListRepository`) caches for that resource so the next open triggers a fresh recursive scan instead of returning the stale root-only list.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.
- [ ] `ResourceEditorUseCase.kt` is backed up to `temp/` (695 LOC — backup required).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ResourceEditorUseCase.kt` | Modified | ≤ 720 |

---

## Steps

### Step 2.1 — Backup `ResourceEditorUseCase.kt` before editing

**Files:** `temp/`
**Depends on:** — start of phase

**Prompt for developer:**

> Create a timestamped backup of `ResourceEditorUseCase.kt` in `temp/` before any edits. File is 695 LOC — backup required.

```powershell
$ts = Get-Date -Format "yyyyMMdd_HHmmss"
Copy-Item `
  "app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ResourceEditorUseCase.kt" `
  "temp/ResourceEditorUseCase_$ts.kt"
```

**Verification:**

- `Glob` — `temp/ResourceEditorUseCase_*.kt` returns at least one match.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 1/1 PASS. Files: temp/ResourceEditorUseCase_20260508_023114.kt. Dev log recorded.

---

### Step 2.2 — Inject `CachedFileListRepository` into `ResourceEditorUseCase`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ResourceEditorUseCase.kt`
**Depends on:** Step 2.1

**Prompt for developer:**

> `ResourceEditorUseCase` already has `@Inject constructor(...)`. Add `private val cachedFileListRepository: CachedFileListRepository` as a constructor parameter. Also add the import for `MediaFilesCacheManager`.
>
> Verify the DI module that provides `ResourceEditorUseCase` — if it is bound via `@Binds` or `@Provides` in a Hilt module, add the new parameter there. If it is `@Inject`-annotated directly (no explicit `@Provides`), Hilt resolves it automatically and no module change is needed.

**Verification:**

- `Grep` — `cachedFileListRepository: CachedFileListRepository` present in the constructor of `ResourceEditorUseCase`.
- `Grep` — `import com.sza.fastmediasorter.core.cache.MediaFilesCacheManager` present in `ResourceEditorUseCase.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `ResourceEditorUseCase.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 3/3 PASS. Files: ResourceEditorUseCase.kt (+1 import line). Dev log recorded.

---

### Step 2.3 — Clear caches when `scanSubdirectories` changes on EDIT save

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ResourceEditorUseCase.kt`
**Depends on:** Step 2.2

**Prompt for developer:**

> In `ResourceEditorUseCase.save()`, inside the `ResourceEditorMode.EDIT` branch, after `updateResourceUseCase(model)` succeeds and before the result is returned, add a scan-mode change check:
>
> ```kotlin
> // Invalidate caches if scan depth changed — stale root-only list must not survive
> if (existing != null && existing.scanSubdirectories != model.scanSubdirectories) {
>     Timber.d("S0114: scanSubdirectories changed (${existing.scanSubdirectories} → ${model.scanSubdirectories}), clearing caches for resource $existingId")
>     MediaFilesCacheManager.clearCache(existingId)
>     cachedFileListRepository.deleteCachedFiles(existingId)
> }
> ```
>
> Insert this block immediately after the `updateResourceUseCase(model).getOrThrow()` line and before the closing brace of the EDIT branch.

**Verification:**

- `Grep` — `existing.scanSubdirectories != model.scanSubdirectories` present in `ResourceEditorUseCase.kt`.
- `Grep` — `MediaFilesCacheManager.clearCache(existingId)` present in `ResourceEditorUseCase.kt`.
- `Grep` — `cachedFileListRepository.deleteCachedFiles(existingId)` present in `ResourceEditorUseCase.kt`.
- `Grep` — `Timber.d("S0114:` present in `ResourceEditorUseCase.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `ResourceEditorUseCase.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 5/5 PASS. Files: ResourceEditorUseCase.kt (+6 LOC). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 2.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for `ResourceEditorUseCase.kt` via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

After Phase 02: toggling `scanSubdirectories` in resource settings and saving clears both caches. The next open performs a full recursive scan and stores the correct file list. Phase 03 closes out docs and catalog.

---

## Rollback Plan

Revert phase commit(s). Cache invalidation is purely subtractive — no data is written that would need to be undone. A rollback leaves stale caches in place, which is the pre-fix state (not worse than before).
