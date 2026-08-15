# Phase 01 - Shared local capture writer

**Strategic spec:** [`../S1354_capture-destination-missing-saf-support.md`](../S1354_capture-destination-missing-saf-support.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 2 / 2
**Started:** -
**Completed:** 2026-08-03

## Objective

Introduce one application-scoped writer that streams a capture temp file to a normal local path or a writable SAF tree and returns the saved location.

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.
- [x] Strategic §6 research items blocking this phase are Resolved.
- [x] Working branch is `DEBUG-v030`.

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/capture/LocalCaptureDestinationWriter.kt` | New | ≤ 260 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/capture/LocalCaptureDestinationWriterTest.kt` | New | ≤ 260 |

## Steps

### Step 01.1 - Add the shared writer

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/capture/LocalCaptureDestinationWriter.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add an injectable application-scoped writer accepting a temporary file, destination path and display name. For `content:/`, resolve a writable SAF tree through `SafHelper`, create or replace the child document, stream bytes through `ContentResolver`, and return its URI. For filesystem destinations, retain `LocalDestinationClassifier` and `LocalDestinationWriter`. Return failure without deleting the caller-owned temporary file.

**Why:**

The strategic spec requires one common local-save layer because a SAF URI is not a filesystem path and repeated conversions cause the capture failure.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/data/capture/LocalCaptureDestinationWriter.kt` exists.
- `Grep` - `class LocalCaptureDestinationWriter` matches exactly once.
- `Grep` - `SafHelper.getOrCreateWritableChildFile` is present.
- `Grep` - `openOutputStream` is present.

**Status:** `[x]` done

**Step Log:**

- 2026-08-03 - Verification 4/4 PASS. Files: `LocalCaptureDestinationWriter.kt`. Post-change PASS.

### Step 01.2 - Test destination classification

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/capture/LocalCaptureDestinationWriterTest.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add unit tests for filesystem, canonical `content://` and malformed `content:/` destination classification and result normalization without requiring a device document provider.

**Why:**

Normal local folders must retain their behavior while persisted SAF paths select the new branch.

**Verification:**

- `Glob` - `app_v2/src/test/java/com/sza/fastmediasorter/data/capture/LocalCaptureDestinationWriterTest.kt` exists.
- `Grep` - `LocalCaptureDestinationWriterTest` matches exactly once.
- `Grep` - `content://` is present.

**Status:** `[x]` done

**Step Log:**

- 2026-08-03 - Verification 3/3 PASS. Files: `LocalCaptureDestinationWriterTest.kt`. Dev log recorded.

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `a.ps1 dq` PASS (2026-08-03).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] `catalog_sync.ps1 -Module app_v2` is current.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

## Handoff Notes to Next Phase

All local capture callers can delegate byte writes to one API while retaining their own fallback and user messaging.

## Rollback Plan

Revert phase commit(s); no data migration or user-facing surface changed.

## Phase-boundary audit

- P0/P1: none. The singleton retains only application context and injected writers; the IO boundary is explicit, and cancellation is rethrown after sink cleanup.
- Evidence: scoped `post-change` PASS and `a.ps1 dq` exit 0.
