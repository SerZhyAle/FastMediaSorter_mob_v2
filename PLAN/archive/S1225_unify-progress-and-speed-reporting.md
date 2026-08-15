# S1225 - Unify progress + speed computation and presentation into one component

**Status:** Archived
**Priority:** 65

**Tactical plan:** `PLAN/S1225_unify-progress-and-speed-reporting/INDEX.md`

## 0. Raw capture

Owner, 2026-07-27, during the Quest 3 session, after the transfer-throttle fix landed:

"другие прогрессы и вычисление и демонстрации скорости можно унифицировать и объеденить в один код"

## 1. Current state - survey done 2026-07-27

There is already a shared component, `domain/transfer/ProgressTracker.kt`, `@Singleton`, throttling at 100 ms. It has **three** adopters:

- `data/transfer/UnifiedFileOperationHandler.kt`
- `ui/player/helpers/PlayerPrefetchOffloadCoordinator.kt` (via `PrefetchProgressTracker`)
- `ui/player/PlayerViewModel.kt`

And it covers **percentage only** - no byte-rate computation, no formatting, no publish-side throttling.

Meanwhile these compute progress and/or transfer speed on their own:

- `data/transfer/SmbTransferProvider.kt`
- `domain/usecase/ByteProgressCallback.kt`
- `domain/usecase/DownloadNetworkFileUseCase.kt`
- `domain/usecase/FileOperationUseCase.kt`
- `domain/usecase/StreamOffloadUseCase.kt`
- `ui/browse/managers/BrowseArchiveDialogManager.kt`
- `ui/browse/managers/BrowseInlineAudioManager.kt`
- `ui/browse/managers/BrowseShareOperationsHelper.kt`
- `ui/browse/transfer/BrowseFileTransferModels.kt`
- `ui/browse/transfer/BrowseFileTransferProgressCodec.kt`
- `ui/dialog/FileOperationProgressDialog.kt`
- `ui/player/helpers/FileCopyProgressDialog.kt`
- `ui/player/helpers/PlayerPrefetchManager.kt`
- `worker/BrowseFileTransferWorker.kt`
- `core/util/CacheStatusHelper.kt`

So the component exists, was never finished, and the rest of the app grew its own copies around it.

## 2. Two defects the survey turned up in the existing component

Both must be fixed by whatever replaces or extends it - they are the reason "just adopt it everywhere" is not the whole job.

- **Unbounded map on a singleton.** `lastUpdateTimes` is a `mutableMapOf<String, Long>()` keyed by `operationId`, written on every report and **never removed**. A long-running process accumulates one entry per operation forever.
- **Documented thread-safety that is not implemented.** The KDoc says "Thread-safe for concurrent operations", but the map is a plain `mutableMapOf` with no synchronization, mutated from arbitrary caller coroutines.

## 3. What the unified component must own

Three concerns are currently tangled and duplicated; the split is the design work:

1. **Measurement** - bytes transferred, elapsed time, and a *smoothed* rate. The current per-chunk rate is computed over a single ~20 ms window and swings between 6.5 and 131 MB/s on a transfer whose true throughput is ~23 MB/s (measured from the byte counter in `temp/scratch/vr_session_20260727-2224.log`). A sliding window is the fix, and it belongs in one place.
2. **Emission throttling** - how often a *consumer* is told. Distinct from measurement: S1226 had to add its own 1 s publish gate in `BrowseFileTransferWorker` because `ProgressTracker`'s 100 ms percentage throttle does not govern the expensive publish path (foreground notification + WorkManager Room write). A unified component should express "measure often, publish rarely" as one contract.
3. **Presentation** - percent, byte counts and rate formatted for UI. Currently re-implemented per dialog.

### 3.3 Owner inputs (Approval gate)

- **Goal:** Provided by user - solve S1225 through `/spec-all`.
- **Scope:** Delegated by user - `/spec-all` auto-approval. Introduce one domain progress model and migrate the Browse file-operation flow, its detached-transfer indicator, worker publication, and modal dialog. Leave unrelated player, archive, and standalone download flows unchanged unless a mechanical compatibility adjustment is required.
- **UI:** Delegated by user - preserve the existing dialog, notification, and indicator layouts and text; only the values feeding them change.
- **Data and threading:** Delegated by user - the shared component is process-wide, uses monotonic elapsed time, and owns per-operation state without retaining UI objects.
- **Flavors:** Delegated by user - implementation stays in `src/main` and applies to all Browse-transfer flavors.
- **Related tickets:** S1226, S1227, S1230.

## 4. Ordering note

Do this **after** the in-app transfer indicator (S1227) rather than before: the indicator is a new consumer, and building it against the current duplication tells us concretely which parts of the contract are actually needed, instead of designing the abstraction from a file list.

## 5. Scope note

Not a behaviour change for the user, except that the displayed speed should stop jittering once measurement is smoothed. Touches many files across `domain`, `data`, `ui` and `worker` - a phased tactical plan, not a single pass.

## 6. Progress (2026-07-28, spec-next loop)

Both section-2 defects of the existing `ProgressTracker` are fixed ahead of the unification -
any future design inherits a sound base:

- Throttle map is now a `ConcurrentHashMap` (the KDoc's thread-safety claim is finally true).
- The singleton no longer leaks an entry per operation: a terminal report (last bytes / 100%)
  removes its own entry, `clearOperation` remains for abort paths, and a size-capped prune
  (>64 entries -> drop those idle >1 min) sweeps operations that never reached terminal.

The unification itself (measurement with smoothed rate, emission throttling contract,
presentation) stays blocked per section 4: S1227 (the transfer indicator, currently
BlockQuestions on owner placement decisions) must land first as the concrete second consumer
the abstraction is designed against.

## 7. Delivered shape (2026-07-31)

The three concerns of section 3 now sit in two places instead of fifteen:

- **Measurement and emission throttling** - `domain/transfer/TransferProgressReporter.kt`. Per operation id it keeps a 3 s sliding sample window for the rate, and per consumer key an independent publish gate. One call answers both "how fast is this going" and "may I tell my consumer yet".
- **Presentation** - `ui/browse/transfer/TransferProgressPercent.kt`. The one percent formula, with the running ceiling and the file-count fallback, read by the modal dialog, the S1227 strip and the worker notification.

Adopters of the reporter: `domain/usecase/FileOperationUseCase.kt` (in-process consumer, no throttle) and `worker/BrowseFileTransferWorker.kt` (worker consumer, 1 s gate, both its byte path and its folder-walk path).

Out of scope per section 3.3 and still on their own arithmetic: the player prefetch, archive, inline-audio and standalone-download flows.

## Last Audit

**Date:** 2026-07-31
**Scope:** Phase 01 + Phase 02 `Files Touched`, protocol layers 1-3. Layer 4 not applicable - no Room surface.
**Verdict:** three findings, all fixed in this pass. No P0.

### P1 - the folder-walk publish gate was never armed

`BrowseFileTransferWorker.publishDirectoryProgress` passed `forcePublish = true` alongside `minimumPublishIntervalMs = PROGRESS_MIN_INTERVAL_MS`, so the interval argument had no effect and the `if (!publishReport.shouldPublish) return` guard below it was unreachable. Every entry of a recursive folder copy rebuilt the foreground notification and wrote WorkManager's progress row to Room - the per-chunk flood S1226 removed from the file path, reintroduced on the path that emits fastest.

Two independent records asserted the opposite invariant and were false until this fix:

- `docs/ARCHITECTURE.md` - "rate-limited through the same `TransferProgressReporter` the file path uses".
- `PLAN/S1325_folder-selection-copy-move/PHASE_04__directory-progress-cancel.md` - "a tree of many small files cannot flood the notification channel".

Fix: drop the argument. The first entry still publishes immediately (no prior publication recorded for the consumer), so nothing is lost but the flood.

### P2 - the report shipped a percent nobody read

`TransferProgressReport.percent` had no production consumer: the dialog, the strip and the worker all render through `transferBytePercentOrNull` / `transferOverallPercent`, which own the running ceiling and the file-count fallback the domain figure lacks. Only a unit test read it. Left in place, this ticket would have shipped a second percent formula - the exact duplication it exists to remove (Rule 21).

Fix: field and its two constants deleted; the reporter now owns rate and publish eligibility only. Phase 01's contract narrowed accordingly, recorded in its step log.

### P2 - the smoothed rate could go negative

`OperationState.speedBytesPerSecond` computes `(last - first)` across the window. A byte counter that restarts under the same operation id - a retried file, or the folder path reporting zero after the file path reported bytes - makes that difference negative, and the domain contract handed the negative straight to consumers. Every current consumer happens to guard on `> 0`, so nothing was rendered wrong; the contract was still wrong.

Fix: result coerced to `>= 0`, with a regression test.

### P3 - accepted, not a defect

`FileOperationUseCase` also passes `forcePublish = true` next to its interval argument, which reads like the P1 above. It is not the same thing: its interval is `NO_THROTTLE_MS`, and the in-process consumer genuinely wants every sample, so neither argument is load-bearing and neither hides the other. The worker's case was a 1 s gate written to matter and then silently disabled.

### Not fixed - not this ticket's

`FileOperationUseCase.kt` also carries detekt findings for `resolveDestinationEndpoint` and `DESTINATION_PROBE_TIMEOUT_MS`. Those belong to the destination-reachability probe (`file_transfer.destination_reachability_probe`), in flight in a sibling session on the same dirty tree. Fixing another ticket's debt inside this one would hide whose change caused what. This ticket's own contribution to that file's findings - the import block whose baseline signature shifted when Phase 02 added the reporter import - was fixed.
