# S0905 - Audit coverage tail: layers 5/6/7 (startup, perf, R8) + runtime evidence

**Ticket:** S0905
**Status:** Archived
**Priority:** 40
**Date:** 2026-07-03
**Tier:** 3 - Moderate (ad-hoc)

<!-- promoted by /spec-all S0878 triage - 2026-07-03 -->

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-03, из coverage note массового аудита 2026-07-02 (wf_34a4d99d-fbf).

Mass audit 2026-07-02 swept: listener-symmetry, player/Glide ownership, Room main-safety, concurrency + 15 player-host contract units + critic round. NOT swept:

- Protocol layer 5 (startup path) - no dedicated sweep.
- Protocol layer 6 (perf: hot-path allocation churn, repeated expensive lookups) - no dedicated sweep.
- Protocol layer 7 (R8/minified: keep rules, reflective/serialized types on release variant) - no dedicated sweep.
- Runtime evidence debt: static-only review; LeakCanary runs on the confirmed leak tickets (S0853-S0877 family) and benchmarks never gathered.

Scope: run the missing CODE_AUDIT_PROTOCOL layers as a dedicated sweep (static where possible, release-variant build proof for layer 7), and collect runtime evidence (LeakCanary/benchmark) for the highest-value confirmed leak tickets.

## Related

- S0878 (audit tail container - triage source); docs/CODE_AUDIT_PROTOCOL.md (layer definitions, evidence ladder).

## Audit results (sweep 2026-07-05)

Method: three parallel read-only static sweeps (layers 5/6/7) + a minified `assembleStandardBenchmark` build (release proguard rules via `initWith(release)`) as the layer-7 R8 proof + a LeakCanary runtime pass on the emulator (Android 17, x86). Evidence rung per the protocol ladder noted per finding.

### Layer 5 - startup / main-thread: CLEAN (no P0/P1)

- Deferred-startup infra (`dagger.Lazy<T>` x13 in `FastMediaSorterApp`, `FirstFrameSignal` gates, `DeferredStartupWorker`, `BaseActivity` first-frame `post{}`) already covers the high-value eager-work cases; no new eager main-thread I/O found on the cold path.
- Residual Room-open-on-main race already tracked in S0869 (`BlockNeedUserTest`) - referenced, not re-drafted.
- Minor P3 parked: dead debug StrictMode flag (S0959).

### Layer 6 - performance (static): findings parked

- Browse hot-path: paging adapter re-issues thumbnails on every selection tick (no `lastLoadedKey` guard) - S0955.
- Sort correctness: `MediaFilesPagingSource` sorts per 50-item page for non-name modes, breaking global order; O(n log n) `.lowercase()` selector recompute - S0954 (P1).
- RecyclerView stable-IDs / hygiene absent module-wide - S0956.
- Clean: adapters overwhelmingly use `ListAdapter`+`DiffUtil`; numeric-keyed maps are `ConcurrentHashMap` by necessity (no SparseArray substitution); `AdapterFileInfoFormatter` (ThreadLocal `SimpleDateFormat`), `ResourceAdapter.formatMediaTypes` (bounded LRU), `DuplicateGroupAdapter` (targeted selection refresh, S0512) already optimized.

### Layer 7 - R8 / release correctness: PROVEN on minified build

- `assembleStandardBenchmark` BUILD SUCCESSFUL; R8 log has zero `missing class` / `unresolved` warnings.
- mapping.txt (`standardBenchmark`) confirms the static keep-rule coverage map:
  - Keep-ruled Gson models (`domain.model.**`, `domain.usecase.Backup**`, `data.model.TrashMetadata`, `domain.game.**`, `data.remote.**`) map to identity - fields NOT renamed. Correct.
  - `RandomPhotoFrameRefreshWorker` (non-`@HiltWorker`) class name PRESERVED at identity - androidx.work consumer rules keep `ListenableWorker` subclass names; the static "will crash" worst-case is REFUTED by the build proof.
  - `@HiltWorker` classes resolve via Dagger `SwitchingProvider` with FQCN intact.
  - `BrowseFileTransferRequest` -> `ct0`, fields `operationType->a`, `sourceResourceId->b`.. RENAMED - confirms the cross-update JSON-desync risk (narrow, self-recovering) - S0957.
- Dead `BuildConfig.PLAYER_ACTIVITY_CLASS` per-flavor field - S0958.

### Runtime evidence - LeakCanary: CLEAN

- LeakCanary enabled (`dumpHeap=true`); exercised MainActivity rotation churn x3 + bg/fg + Settings open/close (its fragment + 5 ViewModels).
- 12 destroyed instances (Activity/Fragment/View/ViewModel) watched; zero retained objects, zero heap dumps, zero leak traces after GC. No retained-Activity/Fragment/View leaks in the exercised flows.
- Not covered: player open/close + rotate-during-playback (emulator has no indexed media + tap flakiness). Player/Glide ownership was swept statically clean in the 2026-07-02 mass audit. Macrobenchmark numbers not gathered - x86 emulator is non-representative; deferred to a real-device baseline (see CODE_AUDIT_PROTOCOL "next additions").

### Parked findings (dedup-checked, none pre-existing)

- S0954 (P1) paged sort global-order bug
- S0955 (P2) browse adapter thumbnail reload on selection
- S0956 (P2) RecyclerView stable-IDs hygiene
- S0957 (Low-Med) BrowseFileTransfer Gson field-rename desync
- S0958 (P3) dead PLAYER_ACTIVITY_CLASS field
- S0959 (P3) debug StrictMode flag hardcoded off
