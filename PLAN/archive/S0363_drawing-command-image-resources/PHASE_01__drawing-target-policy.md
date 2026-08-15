# Phase 01 - Drawing Target Policy

**Strategic spec:** [`../S0363_drawing-command-image-resources.md`](../S0363_drawing-command-image-resources.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 2 / 2
**Started:** 2026-06-05
**Completed:** 2026-06-05

---

## Objective

Introduce `DrawingTargetPolicy` - a single source of truth for "may this resource show Create drawing" and "which directory receives the new drawing" - mirroring the existing `TextNoteTargetPolicy`. No call sites are rewired in this phase.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/util/DrawingTargetPolicy.kt` | New | ≤ 90 |
| `app_v2/src/test/java/com/sza/fastmediasorter/util/DrawingTargetPolicyTest.kt` | New | ≤ 120 |

---

## Steps

### Step 01.1 - Add `DrawingTargetPolicy`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/util/DrawingTargetPolicy.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create an `object DrawingTargetPolicy` modelled on `TextNoteTargetPolicy`. Expose `canCreateDrawing(resource: MediaResource?): Boolean` that returns `false` for a null, read-only, or non-image resource (`!resource.supportsImages()`), and otherwise returns `true` only for a non-virtual path OR the two allow-listed virtual image resources `LocalMediaScanner.VIRTUAL_PATH_ALL_IMAGES` and `LocalMediaScanner.VIRTUAL_PATH_CAMERA_PHOTOS` (use `VirtualPathUtils.isVirtualPath`). Expose `resolveParentPath(resource: MediaResource, currentPath: String?): String` returning the public Downloads directory for `VIRTUAL_PATH_ALL_IMAGES`; the public `DCIM/Camera` directory for `VIRTUAL_PATH_CAMERA_PHOTOS` when it exists or can be created, else the public Downloads directory; and `currentPath ?: resource.path` otherwise. Expose `ensureFallbackDirectoryIfNeeded(resource: MediaResource, parentPath: String): Result<Unit>` that, when `parentPath` equals the resolved public directory for an allow-listed virtual resource, creates it via `mkdirs()` and returns `Result.failure` if the directory is unavailable. Resolve public folders with `Environment.getExternalStoragePublicDirectory` (`DIRECTORY_DOWNLOADS`, `DIRECTORY_DCIM`). Timber only.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/util/DrawingTargetPolicy.kt` exists.
- `Grep` - `object DrawingTargetPolicy` matches exactly once.
- `Grep` - `fun canCreateDrawing(` present.
- `Grep` - `fun resolveParentPath(` present.
- `Grep` - `fun ensureFallbackDirectoryIfNeeded(` present.
- `Grep` - `VIRTUAL_PATH_ALL_IMAGES` and `VIRTUAL_PATH_CAMERA_PHOTOS` both referenced.
- `Grep -n "Log\.d\("` - zero hits in this file.

**Status:** `[x] done`

**Step Log:**

- 2026-06-05 - Verification 7/7 PASS. Files: util/DrawingTargetPolicy.kt (New, +64 LOC). Dev log recorded.

---

### Step 01.2 - Add `DrawingTargetPolicyTest`

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/util/DrawingTargetPolicyTest.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create a plain JUnit test mirroring `TextNoteTargetPolicyTest` (only `canCreateDrawing` is asserted - `resolveParentPath` touches `Environment` and is not unit-testable without Robolectric). Cover: allows `VIRTUAL_PATH_ALL_IMAGES`; allows `VIRTUAL_PATH_CAMERA_PHOTOS`; rejects other virtual image-incompatible aggregates (`VIRTUAL_PATH_ALL_VIDEO`); rejects a read-only allow-listed resource; rejects a non-image resource; allows a normal local image resource. Build `MediaResource` fixtures with `supportedMediaTypes = setOf(MediaType.IMAGE)`.

**Verification:**

- `Glob` - `app_v2/src/test/java/com/sza/fastmediasorter/util/DrawingTargetPolicyTest.kt` exists.
- `Grep` - `class DrawingTargetPolicyTest` matches exactly once.
- `Grep` - at least 5 `@Test` annotations present.
- Single-class run passes: `.\gradlew.bat :app_v2:testStandardDebugUnitTest --tests "com.sza.fastmediasorter.util.DrawingTargetPolicyTest"` - expected: BUILD SUCCESSFUL, all tests green (read the per-class XML report, not the whole suite).

**Status:** `[x] done`

**Step Log:**

- 2026-06-05 - Verification 4/4 PASS (6 @Test). Single-class run exit 0. Files: test/util/DrawingTargetPolicyTest.kt (New, +76 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - proven by green `standardDebug` unit-test run (compiles main + test source sets).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for both files via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

`DrawingTargetPolicy.canCreateDrawing` and `resolveParentPath` exist and are tested for visibility. Phase 02 routes `CreateDrawingUseCase` through `resolveParentPath` + `ensureFallbackDirectoryIfNeeded`; Phase 03 routes the three visibility sites through `canCreateDrawing`.

---

## Rollback Plan

Revert phase commit - new files only, no call sites changed, no user-facing surface.
