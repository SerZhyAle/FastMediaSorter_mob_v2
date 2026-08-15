# S1031 - Mechanical gate: ban public mutable reactive state (MutableStateFlow/LiveData/SharedFlow)

**Status:** Archived

## 0. Raw finding (auto-parked from S1030 audit, 2026-07-13)

Canonical Android/Kotlin antipattern: exposing a `MutableStateFlow` / `MutableLiveData` /
`MutableSharedFlow` publicly (a `val`/`var` without `private`), letting any collaborator emit into
state that should have a single owner. The S1030 audit swept `src/main` (158 `Mutable*` mentions /
54 files) and found the codebase already follows the correct idiom almost everywhere (private backing
+ public `asStateFlow()`/`asSharedFlow()`/`LiveData`), with exactly ONE violation:

- `app_v2/src/main/java/com/sza/fastmediasorter/service/WearSyncEvents.kt:15-17` - three public
  `MutableSharedFlow` fields on an `object` event bus (`ackFlow`, `watchSourcesReceivedFlow`,
  `watchPlaybackStateFlow`). Any class in the module can `tryEmit()` on them, not just the intended
  `PhoneWearListenerService` emitter (per the KDoc at lines 7-13).

## 1. Why this needs its own ticket

- The "rule" deliverable of the S1030 audit request. A mechanical `assert-*.ps1` gate is the durable
  way to keep the codebase at its current-clean state as new ViewModels/managers are added.
- Detection is clean and deterministic (a declaration line with `Mutable(StateFlow|LiveData|SharedFlow)`
  and no `private` modifier), so a ratchet gate starting at the current count is straightforward
  (mirror `scripts/quality/assert-em-dash.ps1`).

## 2. Proposed direction (decide at approval)

- Add `scripts/quality/assert-public-mutable-flow.ps1` (ratchet baseline like em-dash), wire into
  `scripts/post-change.ps1` + the `a.ps1 fg` fast-gate batch + the neuroslop umbrella if appropriate.
- Owner decision: baseline the single `WearSyncEvents` occurrence and ratchet-at-1 (no code change,
  bus stays as a deliberate documented process bus), OR fix `WearSyncEvents` first (private `_flow`
  backing + public `asSharedFlow()` + explicit `tryEmitX()` methods for the emitter) and start the
  gate at 0. The bus is a narrow, documented design - allow-listing/ratcheting is the low-risk path.

## 3. Notes

- Parent audit: S1030 (archived umbrella). Sibling parked findings: `!!` cleanup, SMB `Thread.sleep`,
  logging `SimpleDateFormat` thread-safety.

## Owner decisions (2026-07-14)

- Fix `WearSyncEvents.kt` FIRST (private backing `MutableSharedFlow` + public `asSharedFlow()` + explicit `emit`/`tryEmit` methods for the intended emitter), THEN start the ratchet gate clean at 0. NOT baseline-at-1 / allow-listing the existing occurrence.

## Last Audit (2026-07-15, /spec-all)

Implemented per owner decision. Verified.

- `service/WearSyncEvents.kt` - three `MutableSharedFlow` fields made private backings (`_ackFlow` / `_watchSourcesReceivedFlow` / `_watchPlaybackStateFlow`); public read-only `asSharedFlow()` views keep the same names; added explicit `suspend fun emitAck/emitWatchSources/emitWatchPlaybackState` for the sole intended emitter. Behaviour-preserving (identical stream + buffer semantics).
- `src/wearGms/service/PhoneWearListenerService.kt` - three `.emit()` call sites now route through the new `emit*` methods.
- `src/main/ui/settings/WearSyncViewModel.kt` - unchanged (collector reads the `SharedFlow` views).
- `scripts/quality/assert-public-mutable-flow.ps1` - new ratchet gate (mirrors `assert-em-dash.ps1`), baseline `public-mutable-flow-baseline.txt` seeded at 0; `-ChangedFiles` delta mode for `-ScopeToFile`.
- Wired into `scripts/quality/assert-fast-gates.ps1` (`a.ps1 fg` batch) and `scripts/post-change.ps1` (Kotlin/Mixed, ScopeToFile-aware).

Evidence: gate seeded + PASS at 0; `assert-fast-gates` PASS; `compileStandardDebugKotlin` BUILD SUCCESSFUL (validates `src/main` + `wearGms` emitter). No runtime behaviour change -> no device test required.
