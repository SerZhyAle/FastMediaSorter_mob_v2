# Phase 01 - Worker delete contract

**Strategic spec:** [`../S1369_bulk-delete-dies-with-hosting-screen.md`](../S1369_bulk-delete-dies-with-hosting-screen.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** Not started
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 0 / 3

## Objective

Make the persisted Browse transfer request and WorkManager worker execute DELETE for files and directories.

## Files Touched

| File | Change | Line budget |
|---|:---:|---:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/transfer/BrowseFileTransferModels.kt` | Modified | <= 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/worker/BrowseFileTransferWorker.kt` | Modified | <= 1500 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/browse/transfer/BrowseFileTransferModelsSerializationTest.kt` | Modified | <= 500 |

## Steps

### Step 01.1 - Persist delete policy

**Files:** `BrowseFileTransferModels.kt`
**Depends on:** start of phase

**Prompt for developer:**

> Add an explicitly serialized, defaulted soft-delete field to `BrowseFileTransferRequest`. Retain backward-compatible COPY/MOVE JSON decoding and use request sources for DELETE without a destination.

**Why:**

The strategic spec requires the existing soft-delete decision to remain unchanged while requests survive process and UI destruction.

**Verification:**

- `BrowseFileTransferRequest` has a `@SerializedName` soft-delete property with a safe default.
- Existing COPY/MOVE construction remains source-compatible.

**Status:** `[ ]` not done

### Step 01.2 - Execute delete in worker

**Files:** `BrowseFileTransferWorker.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add DELETE execution to the worker. Convert non-directory sources to `FileOperation.Delete`, execute directory sources through the existing directory-delete use case or handler, aggregate terminal success/partial/failure states, retain explicit cancellation and encode DELETE notification titles/results.

**Why:**

The worker is process-scoped and is the existing durable execution owner; without DELETE support Browse remains lifecycle-bound.

**Verification:**

- Every `when (operationType)` in the worker handles `FileOperationType.DELETE` deliberately.
- DELETE file paths preserve network/cloud protocol strings.
- Worker terminal events and notifications map DELETE to delete resources.
- `Log.d(` has zero matches in the modified file.

**Status:** `[ ]` not done

### Step 01.3 - Prove request compatibility

**Files:** `BrowseFileTransferModelsSerializationTest.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a DELETE request serialization round-trip and a legacy JSON decode assertion proving absent delete-policy data uses the intended default.

**Why:**

The request is persisted across process death and app updates, so wire compatibility is required by the strategic risk control.

**Verification:**

- Test constructs `FileOperationType.DELETE` request and round-trips it through Gson.
- Test verifies legacy JSON without the new field decodes to the safe default.

**Status:** `[ ]` not done

## Phase Done Criteria

- [ ] Every step is done.
- [ ] `pwsh -NoProfile -File a.ps1 fk` passes.
- [ ] Phase-boundary audit covers worker lifecycle, cancellation, foreground notification and persisted request compatibility.
