# S0900 - Streams helpers: cancellation semantics and service-player residue (P2 cluster)

**Ticket:** S0900
**Status:** Archived
**Priority:** 35
**Date:** 2026-07-03
**Tier:** 2 - Easy (ad-hoc)

<!-- promoted by /spec-all S0878 triage - 2026-07-03 -->
<!-- auto-approved by /spec-all (compact) - 2026-07-03 -->

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-03, из P2-appendix массового аудита 2026-07-02 (wf_34a4d99d-fbf). Static-review, не верифицировано скептиком. Тема кластера: streams-хелперы - CancellationException глотается/логируется как ошибка, cancelAll не останавливает очередь, service-player остаётся заряженным.

- app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamFrameSnapshotManager.kt:103 - cancelAll() cannot stop queued captures: drainOne polls the url before suspending on the semaphore, so parked drainOnes launch full ExoPlayer captures after onStop / leaving GRID
- app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamFrameSnapshotManager.kt:195 - catch (t: Throwable) in capture() swallows CancellationException and logs cancelAll-driven cancels as 'Stream snapshot failed' WARNs
- app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamHealthProbeManager.kt:68 - onComplete is never invoked on cancellation - withContext(Dispatchers.Main) in the finally of a cancelled job throws before running the block, contradicting the KDoc
- app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamHealthProbeManager.kt:118 - catch (t: Throwable) swallows CancellationException and logs every user-driven sweep cancel as a WARN failure with stack trace
- app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamInlineAudioManager.kt:183 - stop() leaves playWhenReady=true and the playlist loaded on the service player, defeating AudioPlaybackService.onTaskRemoved's no-active-playback stopSelf heuristic

## 1. Goal (RU)

Пять дефектов в трёх streams-хелперах: coroutine-отмена глотается и логируется как ошибка, `cancelAll` не тормозит запаркованные captures, `onComplete` не вызывается при отмене, а inline-radio `stop()` оставляет service-плеер "заряженным" (playWhenReady=true + плейлист), из-за чего сервис не самоостанавливается при свайпе задачи.

- Пробрасывать `CancellationException` из `catch (t: Throwable)` (паттерн уже есть в MediaMuxerRemuxer).
- `finally { onComplete }` через `NonCancellable`, чтобы контракт KDoc держался и при отмене.
- Гард после получения permit: если grid покинут (pending очищен), не стартовать ExoPlayer-capture.
- Гасить service-плеер (`playWhenReady=false` + `clearMediaItems()`), чтобы сработала эвристика `onTaskRemoved`.

## 2. Constraints

- `releaseKeepingBackgroundService` (background-continue exit) НЕ трогать - он намеренно оставляет сервис играющим.
- Cleanup в `finally` (release плеера/reader) сохраняется на всех путях, включая отмену.
- Никаких новых зависимостей; поведение happy-path не меняется.

## 3. Phases

### Phase 1 - `StreamFrameSnapshotManager` cancel semantics

- Step 1.1 (finding 195): in `capture()` `catch (t: Throwable)`, rethrow cancellation: `if (t is CancellationException) throw t` before the WARN. Import `kotlinx.coroutines.CancellationException`. `finally` release contract unchanged.
- Step 1.2 (finding 103): in `drainOne()`, after `semaphore.withPermit {`, guard before launching the capture: `if (synchronized(pending) { url !in pending }) return@withPermit`. `cancelAll()` clears `pending`, so a drainOne parked on the semaphore while the grid was left resumes, sees its url gone, and skips the ExoPlayer capture.
  - Verification: grep - capture catch rethrows `CancellationException`; drainOne has the `url !in pending` guard inside `withPermit`.

### Phase 2 - `StreamHealthProbeManager` cancel semantics

- Step 2.1 (finding 68): change the sweep `finally` to `withContext(NonCancellable + Dispatchers.Main) { onComplete() }`. Import `kotlinx.coroutines.NonCancellable`. `onComplete` now runs on both normal finish and cancellation, honoring the KDoc.
- Step 2.2 (finding 118): in `probe()` `catch (t: Throwable)`, rethrow cancellation: `if (t is CancellationException) throw t` before the WARN. Import `kotlinx.coroutines.CancellationException`. `finally { player?.release() }` unchanged.
  - Verification: grep - finally uses `NonCancellable + Dispatchers.Main`; probe catch rethrows `CancellationException`.

### Phase 3 - `StreamInlineAudioManager` service-player residue

- Step 3.1 (finding 183): in `stopPlaybackKeepingController()` service-player else-branch, replace bare `player?.stop()` with quiesce: `playWhenReady = false`, `stop()`, `clearMediaItems()`, so `AudioPlaybackService.onTaskRemoved` (`!playWhenReady || mediaItemCount == 0`) stops the service. `releaseKeepingBackgroundService` untouched.
  - Verification: grep - service-branch sets `playWhenReady = false` and calls `clearMediaItems()`; `releaseKeepingBackgroundService` unchanged.

### Phase 4 - Build gate

- Step 4.1: `standard debug` compiles (`a.ps1 fk`). Detekt-clean on the three touched files.
  - Verification: BUILD SUCCESSFUL; no new detekt findings.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0878 (audit tail container - triage source), S0896 (StreamInlineAudioManager focus - same file, BlockNeedUserTest), S0909 (inline-audio stale generation - same file, sequenced after this).

## Related

- S0878 (audit tail container - triage source).
- S0896 (StreamInlineAudioManager focus contract - same file).
- S0909 (BrowseInlineAudioManager stale generation - different class; no overlap with this ticket).

## Last Audit

**Date:** 2026-07-03 (spec-all, static). **Status:** BlockNeedUserTest.

All five findings implemented; `standard debug` Kotlin compile PASS; detekt-clean on the three touched files.

- **`StreamFrameSnapshotManager.capture` (finding 195)** - `catch (t: Throwable)` now `if (t is CancellationException) throw t` before the WARN. cancelAll-driven cancels propagate instead of being logged as `Stream snapshot failed`; the `finally` release contract still runs on cancellation.
- **`StreamFrameSnapshotManager.drainOne` (finding 103)** - after `semaphore.withPermit`, a `url !in pending` guard skips the ExoPlayer capture when `cancelAll()` cleared `pending` while the drainOne was parked on the semaphore (grid left / onStop). No late capture is launched. Benign edge: an immediate re-request of the same url re-adds it to `pending` and the parked drainOne then proceeds - a legitimate capture, bounded by the semaphore.
- **`StreamHealthProbeManager.start` (finding 68)** - sweep `finally` now `withContext(NonCancellable + Dispatchers.Main) { onComplete() }`, so `onComplete` runs on cancellation too (a plain `withContext(Main)` throws in a cancelled coroutine before its block) - the KDoc contract holds.
- **`StreamHealthProbeManager.probe` (finding 118)** - `catch (t: Throwable)` rethrows `CancellationException`; user-driven sweep cancels no longer log a WARN + stack trace. `finally { player?.release() }` unchanged.
- **`StreamInlineAudioManager.stopPlaybackKeepingController` (finding 183)** - the service-player branch now quiesces: `playWhenReady = false`, `stop()`, `clearMediaItems()`, so `AudioPlaybackService.onTaskRemoved`'s `!playWhenReady || mediaItemCount == 0` heuristic fires and `stopSelf()`s the service on task removal. `releaseKeepingBackgroundService` (background-continue exit) untouched.

**Device gate.** Behavioral flows benefit from device verification; probe tags at the drainOne skip, sweep onComplete, and service quiesce entries. Verify via `/spec-sweep`:
- Leave GRID mode (or background the screen) while tiles are still capturing -> no new ExoPlayer capture starts after (logcat `S0900: capture skipped, grid left`; no post-stop snapshot decode).
- Start a health sweep (refresh), then tap/scroll/leave to cancel it -> spinner stops (onComplete runs; logcat `S0900: health sweep onComplete (cancel-safe)`), no `Stream health probe failed` WARNs for the cancel.
- Play an inline radio channel with background playback ON, press stop, then swipe the app from recents -> the AudioPlaybackService stops (no lingering media notification; logcat `S0900: service player quiesced on stop`).

**Evidence rung:** static + compile + detekt (P2). Findings 118/195/68 are cancellation-log hygiene (statically sound); findings 103/183 are device-observable lifecycle - deferred to `/spec-sweep`.

### User-reported crash - 2026-07-03 (Samsung SM-S731B, Android 16 / SDK 36, noLegal debug)

Flow (1) partially confirmed via user-supplied Timber logs (see S0700's 2026-07-03 audit entry for the full evidence): `S0900: capture skipped, grid left` fired 12x in a session where the user left GRID for fullscreen playback right after opening it - the drainOne guard from finding 103 worked, no late ExoPlayer capture started, session survived.

Separately, the same logs surfaced a more severe crash this ticket's guard does not cover: staying IN grid (not leaving it) hit a native process kill the instant the initial capture burst started - no Java stacktrace, no crash-report file. That is tracked and mitigated on S0700 (`MAX_CONCURRENT_CAPTURES` 2->1 in `StreamFrameSnapshotManager`), not here - this ticket's cancellation-semantics fixes are unaffected and still need flows (2) and (3) verified on device.

### Manual device test - 2026-07-10 (emulator-5554, Android 13 / SDK 33, standard debug 2.60.7092.225)

Driven via mobile-mcp/adb; Streams reachable (enableStreams ON), network up, live endpoints responding (probe sweep recorded green OK statuses). Evidence under `temp/S0900/`.

- **Flow (1) GRID capture skip - N/A.** Real GRID capture disabled per 2026-07-03 STOPGAP (`StreamFrameSnapshotManager.CAPTURE_ENABLED=false`, S0700) - nothing to skip while capture is off. Not exercised; re-verify once capture is re-enabled.
- **Flow (2) health sweep cancel - PASS.** Started sweep via toolbar refresh, cancelled by scrolling mid-sweep. Expected: spinner stops, `S0900: health sweep onComplete`, no "Stream health probe failed" WARNs. Actual: `S0900: health sweep onComplete (cancel-safe)` fired exactly once on cancel; `Stream health probe failed` count = 0; no probe-error WARN spam; probed rows recorded green statuses before cancel. Evidence: `temp/S0900/dump_flow2b.txt`.
- **Flow (3) inline radio quiesce - PASS.** Played inline radio (1-NRK Jazz) with background playback ON (`inline audio start .. bg=true`, service reached playbackState=3), pressed stop, swiped app from recents. Expected: `S0900: service player quiesced on stop`, AudioPlaybackService stops, no lingering media notification. Actual: `S0900: service player quiesced on stop` fired; service dropped to playbackState=1 (IDLE) with auto-save stopped; on recents swipe `AudioPlaybackService: task removed, no active playback - stopping` fired (onTaskRemoved heuristic); service gone from `dumpsys activity services`; no MediaStyle/AudioPlayback notification (only unrelated screen_capture_overlay_host + system AlertWindow overlay notices). Evidence: `temp/S0900/dump_flow3_stop2.txt`, `temp/S0900/dump_flow3_recents.txt`.

Verdict: both device-observable flows (2, 3) confirmed; flow (1) N/A while capture disabled.
