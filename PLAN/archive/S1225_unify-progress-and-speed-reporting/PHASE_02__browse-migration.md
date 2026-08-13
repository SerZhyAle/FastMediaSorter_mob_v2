# Phase 02 - Browse Migration

**Status:** Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3

## Objective

Route Browse file-operation progress through the shared reporter so the dialog, detached indicator, and worker all receive one percent and one smoothed speed value.

## Files Touched

| File | New / Modified |
|---|:---:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/FileOperationUseCase.kt` | Modified |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/transfer/TransferProgressPercent.kt` | Modified |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/FileOperationProgressDialog.kt` | Modified |
| `app_v2/src/main/java/com/sza/fastmediasorter/worker/BrowseFileTransferWorker.kt` | Modified |

## Steps

### Step 02.1 - Normalize file-operation progress

**Files:** `domain/usecase/FileOperationUseCase.kt`

**Prompt for developer:**

> Inject the reporter and report byte samples from the file-operation callback under one operation id. Emit its smoothed speed and operation-level byte values in `FileOperationProgress`; clear state on completion and cancellation. Keep the flow producer on IO and do not introduce UI dependencies.

**Verification:**

- `FileOperationUseCase` depends on `TransferProgressReporter`.
- Raw provider speed is not forwarded directly into `FileOperationProgress.Processing`.

**Status:** `[x]` done

### Step 02.2 - Reuse the same percent and publish contract

**Files:** `ui/browse/transfer/TransferProgressPercent.kt`, `worker/BrowseFileTransferWorker.kt`

**Depends on:** Step 02.1

**Prompt for developer:**

> Make the worker use the shared percent helper and the reporter's worker consumer interval instead of its private percent expression and local time gate. Preserve immediate publication on a file change and terminal notification behavior.

**Verification:**

- Worker contains no duplicate operation-percent arithmetic.
- Worker retains immediate publication for a changed file.

**Status:** `[x]` done

### Step 02.3 - Remove dialog-owned rate smoothing

**Files:** `ui/dialog/FileOperationProgressDialog.kt`

**Depends on:** Step 02.1

**Prompt for developer:**

> Delete the dialog's speed-sample queue and compute ETA from the already-smoothed domain speed. Keep current UI update throttling and the existing unknown-total behavior unchanged.

**Verification:**

- `FileOperationProgressDialog` contains no `speedSamples` or `MAX_SPEED_SAMPLES`.
- Dialog still uses `transferOverallPercent`.

**Status:** `[x]` done

## Phase Done Criteria

- [x] Every step is done.
- [x] Kotlin compilation and focused tests pass - `a.ps1 fk`, exit 0; Phase 01 `a.ps1 fu`, exit 0.
- [x] Phase-boundary audit run - Layers 1-3, retroactively at the Phase 03 boundary.

## Step Log

- 2026-07-31 - Phase-boundary audit (Layers 1-3) run late, at the start of Phase 03. Three findings, all fixed; see the strategic spec's `## Last Audit` for the full record.
- 2026-07-31 - `AUDIT-FIX: publishDirectoryProgress bypassed the publish gate.` The directory-walk call passed `forcePublish = true` next to `minimumPublishIntervalMs = PROGRESS_MIN_INTERVAL_MS`, so the interval argument was dead and `if (!publishReport.shouldPublish) return` was unreachable. S1325's own phase-04 audit records the opposite as an invariant ("the per-entry callback goes through the same rate limiter the file path uses") - that claim only became true with this fix. Argument removed.
- 2026-07-31 - `AUDIT-FIX: TransferProgressReport.percent had no production consumer.` Only a test read it, while all three rendering sites use `transferBytePercentOrNull` / `transferOverallPercent`. Shipping it would have left the ticket with the second percent formula it exists to remove (Rule 21). Field and its two constants deleted; Phase 01's contract narrowed accordingly.
- 2026-07-31 - `AUDIT-FIX: smoothed rate could go negative.` A byte counter that restarts inside the 3 s window made `(last - first)` negative. No consumer rendered it (each guards on `> 0`), but the domain contract leaked it. Result coerced to `>= 0`, regression test added.

## Rollback Plan

Revert the phase commit; no schema or persisted payload change is involved.
