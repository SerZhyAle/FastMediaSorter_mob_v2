# Phase 04 - Tests and cleanup

**Strategic spec:** [`../S1354_capture-destination-missing-saf-support.md`](../S1354_capture-destination-missing-saf-support.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 02, Phase 03
**Blocks:** none - final phase
**Steps done:** 0 / 2
**Started:** -
**Completed:** -

## Objective

Verify confirmed routes, synchronize the code catalog and prepare device evidence without changing user-facing documentation.

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/test/java/com/sza/fastmediasorter/data/capture/CameraCaptureSaverTest.kt` | Modified | ≤ 500 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/capture/LocalCaptureDestinationWriterTest.kt` | Modified | ≤ 320 |
| `dev/CATALOG/app_v2.jsonl` | Generated | generated |

## Steps

### Step 04.1 - Complete focused unit coverage

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/capture/CameraCaptureSaverTest.kt`, `app_v2/src/test/java/com/sza/fastmediasorter/data/capture/LocalCaptureDestinationWriterTest.kt`
**Depends on:** Step 02.3, Step 03.2

**Prompt for developer:**

> Complete focused unit coverage for filesystem and SAF classification, camera local routing and failure propagation. Run the focused project test target; retain device provider I/O for manual verification.

**Why:**

The strategic criteria require compatibility for ordinary folders and safe failure when a SAF tree is unavailable.

**Verification:**

- `Grep` - `content://` is present in `LocalCaptureDestinationWriterTest.kt`.
- `Grep` - `LocalCaptureDestinationWriter` is present in `CameraCaptureSaverTest.kt`.
- Focused project test command exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-03 - Verification 3/3 PASS. Standard unit suite exit 0.

### Step 04.2 - Synchronize catalog and device scope

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 04.1

**Prompt for developer:**

> Synchronize the `app_v2` catalog and record manual verification for photo, video, microphone and screen recording into a persisted writable SAF tree plus unavailable-tree fallback. Do not add strings or docs/FEATURES entries.

**Why:**

Device provider evidence is required by the final strategic criterion, while this fixes an existing capability and leaves the public showcase unchanged.

**Verification:**

- `Grep` - `LocalCaptureDestinationWriter` is present in `dev/CATALOG/app_v2.jsonl`.
- `Grep` - no `S1354:` persistent logging tag is added outside the temporary device-test lifecycle.

**Status:** `[ ]` not done

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] Focused tests pass.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

## Rollback Plan

Revert phase commit(s); no settings migration, UI or public documentation change exists.
