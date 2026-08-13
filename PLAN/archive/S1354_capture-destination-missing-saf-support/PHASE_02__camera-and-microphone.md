# Phase 02 - Camera and microphone integration

**Strategic spec:** [`../S1354_capture-destination-missing-saf-support.md`](../S1354_capture-destination-missing-saf-support.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** -
**Completed:** 2026-08-03

## Objective

Route camera photos, camera videos and microphone recordings to the shared writer while preserving network and fallback paths.

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/capture/CameraCaptureSaver.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/capture/MicRecordingSaver.kt` | Modified | ≤ 260 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/capture/CameraCaptureSaverTest.kt` | Modified | ≤ 500 |

## Steps

### Step 02.1 - Delegate local camera writes

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/capture/CameraCaptureSaver.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Inject `LocalCaptureDestinationWriter` into `CameraCaptureSaver` and delegate `CameraCaptureTarget.Resource` local writes to it. Preserve DCIM, network upload, MediaStore fallback, clipboard and temporary-file deletion behavior.

**Why:**

Configured photo and video resources reach the saver as local paths, but a selected SAF tree cannot be represented by `File`.

**Verification:**

- `Grep` - `LocalCaptureDestinationWriter` is present in `CameraCaptureSaver.kt`.
- `Grep` - `ResourceType.SMB` is still present.

**Status:** `[x]` done

**Step Log:**

- 2026-08-03 - Verification 2/2 PASS. Files: `CameraCaptureSaver.kt`. Post-change PASS.

### Step 02.2 - Delegate local microphone writes

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/capture/MicRecordingSaver.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Inject `LocalCaptureDestinationWriter` into `MicRecordingSaver` and route configured or browsed local resource writes through it. Keep network upload handling and Downloads fallback unchanged; expose the returned path or URI as the saved location.

**Why:**

Microphone capture builds a `File` from every local resource path, so a persisted SAF tree reaches the wrong writer path.

**Verification:**

- `Grep` - `LocalCaptureDestinationWriter` is present in `MicRecordingSaver.kt`.
- `Grep` - `ResourceUnavailable` is still present.

**Status:** `[x]` done

**Step Log:**

- 2026-08-03 - Verification 2/2 PASS. Files: `MicRecordingSaver.kt`. Dev log recorded.

### Step 02.3 - Extend camera saver coverage

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/capture/CameraCaptureSaverTest.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Extend the camera saver test fixture for the shared writer and assert a local SAF destination uses its result while network resources remain delegated to the upload callback.

**Why:**

Network routing is expressly out of scope, so regression coverage must prove the new local branch does not swallow it.

**Verification:**

- `Grep` - `LocalCaptureDestinationWriter` is present in `CameraCaptureSaverTest.kt`.
- `Grep` - `content://` is present in `CameraCaptureSaverTest.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-03 - Verification 2/2 PASS. Files: `CameraCaptureSaverTest.kt`. Post-change PASS.

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `a.ps1 dq` PASS (2026-08-03).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] `catalog_sync.ps1 -Module app_v2` is current.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

## Handoff Notes to Next Phase

Camera and microphone preserve their own fallback notices while local SAF writes share stream-based implementation.

## Rollback Plan

Revert phase commit(s); no settings migration or UI change exists.

## Phase-boundary audit

- P0/P1: none. Local resource routing remains on IO through the shared writer; network upload and fallback ownership stay unchanged.
- Evidence: scoped `post-change` PASS and `a.ps1 dq` exit 0.
