# Phase 03 - Screen recording integration

**Strategic spec:** [`../S1354_capture-destination-missing-saf-support.md`](../S1354_capture-destination-missing-saf-support.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** -
**Completed:** 2026-08-03

## Objective

Route the screen-recording service's configured local destination through the shared SAF-capable writer without changing foreground-service lifecycle ownership.

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenVideoRecordingService.kt` | Modified | ≤ 500 |

## Steps

### Step 03.1 - Delegate screen-recording local writes

**Files:** `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenVideoRecordingService.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Inject `LocalCaptureDestinationWriter` lazily into `ScreenVideoRecordingService`. Replace `File(destDir, tempFile.name)` and its private local writer call with the shared writer using the selected destination path, preserving `finally` deletion, toast and foreground-service teardown.

**Why:**

The traced screen-recording flow has the same invalid `File` conversion as camera and microphone, while its projection and encoder release contract must remain unchanged.

**Verification:**

- `Grep` - `Lazy<LocalCaptureDestinationWriter>` is present.
- `Grep` - `pendingTempFile = null` remains present.

**Status:** `[x]` done

**Step Log:**

- 2026-08-03 - Verification 2/2 PASS. Files: `ScreenVideoRecordingService.kt`. Post-change PASS.

### Step 03.2 - Remove superseded direct writer dependencies

**Files:** `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenVideoRecordingService.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Remove only imports, injected fields and the private method made redundant by the shared writer; retain all media-projection release behavior and the existing `Lazy` dependency style.

**Why:**

The shared writer owns the local output mechanism, so retaining a parallel path would leave two implementations of SAF-sensitive responsibility.

**Verification:**

- `Grep` - `private suspend fun writeToDevice` returns zero matches.
- `Grep` - `LocalDestinationClassifier` returns zero matches.
- `Grep` - `releaseRecordingResources` remains present.

**Status:** `[x]` done

**Step Log:**

- 2026-08-03 - Verification 3/3 PASS. Files: `ScreenVideoRecordingService.kt`. Dev log recorded.

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - `a.ps1 dq` PASS (2026-08-03).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] `catalog_sync.ps1 -Module app_v2` is current.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

## Handoff Notes to Next Phase

The service owns projection and encoder cleanup; only post-recording byte writing is shared.

## Rollback Plan

Revert phase commit(s); no service state migration is involved.

## Phase-boundary audit

- P0/P1: none. The service preserves its existing projection and encoder release sequence; the new writer is lazily resolved and performs only post-recording IO.
- Evidence: scoped `post-change` PASS and `a.ps1 dq` exit 0.
