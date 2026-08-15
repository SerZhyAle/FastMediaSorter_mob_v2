# S0931 - Deduplicate recording indicator elapsed timer

**Status:** Archived

## 1. Problem

- The shared in-app recording indicator had two separate elapsed-timer implementations behind the same UI.
- `MainVoiceCaptureManager` already used the reusable `RecordingElapsedTimer`, while `MainScreenRecordingManager` kept its own `Handler` + `Runnable` ticker only because screen recording must survive `MainActivity` recreation.

## 2. Root cause

- `RecordingElapsedTimer` originally assumed that elapsed time always lived in the helper itself: `start()` reset local state and `pause()` / `resume()` only operated on in-memory accumulation.
- Screen recording cannot rely on that model, because its source of truth is `ScreenRecordingStateController.elapsedMs()` - a process-wide value recomputed from stored instants so it survives Activity backgrounding and recreation.
- That mismatch forced `MainScreenRecordingManager` to duplicate the same scheduling + formatting behavior instead of reusing the shared helper.

## 3. Fix

- Generalized `RecordingElapsedTimer` so hosts may optionally provide an external elapsed-time source while keeping the existing local-accumulation mode for voice capture and other Activity-scoped recordings.
- Kept `onTick` as the trailing constructor parameter so existing trailing-lambda call sites remain source-compatible.
- Switched `MainScreenRecordingManager` to the shared helper and removed its local `Handler`, `Runnable`, and manual `mm:ss` formatting.
- Wired paused-state handling through the shared helper as well, so the screen-recording indicator stops ticking while paused and resumes from the controller-owned elapsed source.

## 4. Validation

- `expected: Kotlin compile passes after the helper API change | actual: PASS` - `.\a.ps1 fk`
- `expected: no new detekt findings in changed files | actual: PASS` - scoped `scripts/quality/assert-detekt.ps1 -Gate -Module app_v2 -ChangedFiles @(RecordingElapsedTimer.kt, MainScreenRecordingManager.kt)`
- `expected: post-change mechanical gates pass for the shared helper refactor | actual: PASS` - `scripts/post-change.ps1 -ScopeToFile`
- `expected: app_v2 catalog refreshed for both touched files | actual: PASS` - `scripts/catalog_sync.ps1 -Module app_v2 -ChangedFiles @(RecordingElapsedTimer.kt, MainScreenRecordingManager.kt)`

## 5. Outcome

- The recording indicator now has one shared elapsed-timer implementation for both quick voice capture and screen recording.
- Screen recording still keeps its recreation-safe elapsed source in `ScreenRecordingStateController`; only the scheduling / formatting duplicate was removed.
- No user-visible feature scope changed, so `docs/FEATURES*` stayed untouched.

## 6. Related tickets

- S0930 - source of discovery during overlay-indicator research
- S0774 - shared recording indicator overlay
- S0349 - quick voice capture feature lineage
