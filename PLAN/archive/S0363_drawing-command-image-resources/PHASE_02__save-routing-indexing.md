# Phase 02 - Save Routing & MediaStore Indexing

**Strategic spec:** [`../S0363_drawing-command-image-resources.md`](../S0363_drawing-command-image-resources.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-06-05
**Completed:** 2026-06-05

---

## Objective

Route `CreateDrawingUseCase` through `DrawingTargetPolicy` so a drawing requested from a virtual image resource lands in a real directory (Downloads for "all images", `DCIM/Camera` for "camera"), and index the created local file in MediaStore so it appears in the aggregate. Behaviour for non-virtual resources is unchanged (policy returns the same path).

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/CreateDrawingUseCase.kt` | Modified | ≤ 200 |

---

## Steps

### Step 02.1 - Resolve target directory via policy

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/CreateDrawingUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `invoke`, after the name-validation block and before `runCatching`, compute `val resolvedParentPath = DrawingTargetPolicy.resolveParentPath(resource, parentPath)` and call `DrawingTargetPolicy.ensureFallbackDirectoryIfNeeded(resource, resolvedParentPath).onFailure { return@withContext Result.failure(it) }` (same pattern as `CreateTextNoteUseCase`). Pass `resolvedParentPath` to `createLocalDrawing` and `createStagedDrawing` and to `stagingRegistry.register(targetParentPath = ...)` instead of the raw `parentPath`. Do not change the LOCAL/staged branch selection.

**Verification:**

- `Grep` - `DrawingTargetPolicy.resolveParentPath(` present in `CreateDrawingUseCase.kt`.
- `Grep` - `ensureFallbackDirectoryIfNeeded(` present in `CreateDrawingUseCase.kt`.
- `Grep` - `parentPath = parentPath` (raw pass-through to `createLocalDrawing`/`createStagedDrawing`) returns zero hits - the resolved value is used.
- Project compiles - run `/build`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-05 - Verification 3/3 static PASS (resolveParentPath + ensureFallback present; `parentPath = parentPath` 0 hits). Compile jointly confirmed with Step 02.2 test run (same file). Files: domain/usecase/CreateDrawingUseCase.kt. Dev log recorded.

---

### Step 02.2 - Index the created local drawing in MediaStore

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/CreateDrawingUseCase.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> In `createLocalDrawing`, after `stagingRegistry.register(...)`, call `MediaStoreNotifier.notifyFile(appContext, targetFile.absolutePath, "create-drawing")` so the new JPEG is registered in MediaStore and surfaces in the "all images" / "camera" aggregate views. Leave `createStagedDrawing` untouched (network/cloud staging commits remotely on Save). Keep the existing `Timber.e` failure logging; do not add a ticket-id tag here.

**Verification:**

- `Grep` - `MediaStoreNotifier.notifyFile(` present in `CreateDrawingUseCase.kt`.
- `Grep -n "Log\.d\("` - zero hits in `CreateDrawingUseCase.kt`.
- Project compiles - run `/build`.
- Affected unit tests pass: `.\gradlew.bat :app_v2:testStandardDebugUnitTest --tests "com.sza.fastmediasorter.domain.usecase.CreateDrawingUseCaseTest"` - expected: BUILD SUCCESSFUL (per-class XML report).

**Status:** `[x] done`

**Step Log:**

- 2026-06-05 - Verification 4/4 PASS (notifyFile present; Log.d 0 hits; module compiled; CreateDrawingUseCaseTest exit 0). Files: domain/usecase/CreateDrawingUseCase.kt. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - proven by green `CreateDrawingUseCaseTest` standardDebug run.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for the modified file via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Saving a drawing now works for the two virtual image resources and the new file is indexed. Phase 03 can safely expose the command for them without producing a save failure.

---

## Rollback Plan

Revert phase commit - single use-case file, no schema or user-facing surface change.
