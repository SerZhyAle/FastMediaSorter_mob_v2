# Phase 10 - Recording engine pause implementation (src/screenCapture)

**Strategic spec:** [`../S0774_programs-screen-recording-action.md`](../S0774_programs-screen-recording-action.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 09 (controller interface + state-controller pause fields)
**Blocks:** Phase 12
**Steps done:** 2 / 2
**Started:** 2026-07-03
**Completed:** 2026-07-03

---

## Objective

Wire the two new controller methods to the real foreground service: `MediaRecorder.pause()/resume()` (API 24+, project minimum for this gated набор is API 26 - no SDK-version guard needed), foreground-notification action toggle, and `ScreenRecordingStateController` updates.

---

## Prerequisites

- [x] Phase 09 is ✅ Done.
- [x] `.\a.ps1 fkn` green before starting (noLegal flavor also mounts `src/screenCapture`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenVideoRecordingService.kt` | Modified | ≤ 500 |
| `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenVideoRecordingControllerImpl.kt` | Modified | ≤ 40 |
| `app_v2/src/main/res/values/strings.xml` (+ `values-ru`, `values-uk`) | Modified | - |

> **Topology note (added during execution):** the notification's pause/resume action label needs string resources immediately - moved the two shared `recording_pause`/`recording_resume` keys here (Step 10.1) instead of Phase 11's original Step 11.6, since Phase 10 runs before Phase 11 and both consume the same keys. Phase 11's string step is amended to a parity-check only (see its Step Log).

---

## Steps

### Step 10.1 - Add pause/resume handling to ScreenVideoRecordingService

**Files:** `ScreenVideoRecordingService.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `ACTION_PAUSE`/`ACTION_RESUME` companion constants and handle them in `onStartCommand` alongside the existing `ACTION_STOP` branch - guard both on `isRecording` (no-op if not recording, matching the existing guards). On pause: call `mediaRecorder?.pause()`, set a `isPaused` service field, call `stateController.markPaused()`, rebuild the foreground notification so its action reads "Resume" instead of "Pause" (`NotificationManagerCompat.notify` with the same `NOTIFICATION_ID`, not a second `startForeground`). On resume: the inverse (`mediaRecorder?.resume()`, `stateController.markResumed()`, notification back to "Pause"). Wrap both `MediaRecorder` calls in `try/catch (e: IllegalStateException)` + `Timber.e` - `pause()`/`resume()` throw `IllegalStateException` (not a broad `Exception`) if the encoder is in the wrong state; a narrower catch also keeps the detekt `TooGenericExceptionCaught` gate clean on these two new blocks. `onDestroy`'s mid-recording teardown path is unaffected (still stops+drops the file regardless of paused state). Add `fun pause(context: Context)` / `fun resume(context: Context)` companion functions mirroring the existing `start`/`stop`.

**Verification:**

- `Grep` - `ACTION_PAUSE`, `ACTION_RESUME` constants present.
- `Grep` - `mediaRecorder?.pause()` and `mediaRecorder?.resume()` present, each inside a `try { .. } catch (e: IllegalStateException)` block (not bare calls, not a broad `Exception` catch).
- `Grep` - `stateController.markPaused()` and `stateController.markResumed()` present.
- `Grep` - `fun pause(context: Context` and `fun resume(context: Context` present in the companion object.
- `Grep -n "Log\.d\("` - zero hits (Timber only).

**Status:** `[x] done`

**Step Log:**

- 2026-07-03 - Verification 5/5 PASS. Files: `ScreenVideoRecordingService.kt` (+~70 LOC: pause/resume handling, notification rebuild, companion functions), `values/strings.xml` + `values-ru` + `values-uk` (2 new shared keys `recording_pause`/`recording_resume`, added here rather than Phase 11 - see Files Touched topology note). `.\a.ps1 fk` and `.\a.ps1 fkn` both BUILD SUCCESSFUL. Dev log recorded.
- 2026-07-03 - Detekt gate FAIL on first `post-change.ps1` pass: this file (introduced by S0774 weeks ago) turned out to have zero entries in `config/detekt/baseline-app_v2.xml` - every pre-existing finding in it was always unbaselined, not something my edit newly triggered. Fixed all 10 findings while here rather than leave the file gate-red: narrowed 4 `catch (e: Exception)` to the actual thrown types (`IllegalStateException`, `IOException` x2), added justified `@Suppress` on 3 methods where the platform genuinely has no narrower type (`stopAndSave`'s bare `RuntimeException` from `MediaRecorder.stop()`) or the guard-clause/multi-exception-type shape is correct as written (`buildRecorder`, `startRecording`, `createChannel`), extracted 2 magic numbers to companion constants. Re-ran `.\a.ps1 fk` + scoped `assert-detekt.ps1` after each fix; final state: `assert-detekt: PASS [scoped]`.

---

### Step 10.2 - Implement requestPause/requestResume in ScreenVideoRecordingControllerImpl

**Files:** `ScreenVideoRecordingControllerImpl.kt`
**Depends on:** Step 10.1

**Prompt for developer:**

> Implement the two new interface methods, delegating straight to the Phase 10.1 companion functions - `override fun requestPause(context: Context) = ScreenVideoRecordingService.pause(context)` and the resume equivalent, matching the existing `requestStop` delegation style exactly.

**Verification:**

- `Grep` - `override fun requestPause(` and `override fun requestResume(` present, each a one-line delegation to `ScreenVideoRecordingService`.
- `.\a.ps1 fkn` - `BUILD SUCCESSFUL` (screenCapture source set compiles against the Phase 09 interface).

**Status:** `[x] done`

**Step Log:**

- 2026-07-03 - Verification 2/2 PASS. Files: `ScreenVideoRecordingControllerImpl.kt` (+8 LOC). `.\a.ps1 fkn` BUILD SUCCESSFUL. Dev log recorded.

---

## Phase Done Criteria

- [x] Both steps `[x]`.
- [x] `.\a.ps1 fkn` green.
- [x] `Grep` - no `BuildConfig.IS_*` flavor guard introduced in either file.
- [x] Dev log entry added for the two touched files.

---

## Handoff Notes to Next Phase

- Phase 11's pause/resume buttons call `controller?.requestPause(activity)` / `requestResume(activity)` - both now do real work end-to-end for standard (`fms.screenCapture=on`) and noLegal; a no-op empty-set injection keeps lite/photos/legacy inert exactly as before.

---

## Rollback Plan

Revert the phase commit - new branches in an existing `onStartCommand`/interface impl; no schema or persisted-state change, no risk to the already-shipped start/stop path if reverted.
