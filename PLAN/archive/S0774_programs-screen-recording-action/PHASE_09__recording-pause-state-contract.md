# Phase 09 - Recording pause state contract (src/main)

**Strategic spec:** [`../S0774_programs-screen-recording-action.md`](../S0774_programs-screen-recording-action.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase (extends the existing Phase 04 contract)
**Blocks:** Phase 10, Phase 11
**Steps done:** 3 / 3
**Started:** 2026-07-03
**Completed:** 2026-07-03

---

## Objective

Add the pause/resume seam both the screen-recording engine and the compact indicator UI need: a relocated, feature-neutral elapsed-time ticker, pause bookkeeping on `ScreenRecordingStateController`, and the two new controller methods. No UI and no `MediaRecorder` calls yet.

---

## Prerequisites

- [ ] Working tree is clean or on the active feature branch.
- [ ] `.\a.ps1 fk` is green before starting (baseline).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/util/RecordingElapsedTimer.kt` | New | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraRecordingTimer.kt` | Deleted (relocated) | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt` | Modified | ≤ 5 (import + type reference only) |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/screencapture/ScreenRecordingStateController.kt` | Modified | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/screencapture/ScreenVideoRecordingController.kt` | Modified | ≤ 30 |

---

## Steps

### Step 09.1 - Relocate CameraRecordingTimer to a shared utility

**Files:** `util/RecordingElapsedTimer.kt` (new), `ui/cameracapture/helpers/CameraRecordingTimer.kt` (delete), `ui/cameracapture/CameraCaptureActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Move `CameraRecordingTimer` (S0566) from `ui/cameracapture/helpers/` to `util/RecordingElapsedTimer.kt`, renaming the class to `RecordingElapsedTimer`. The class body (Handler-based ticker, `start()/pause()/resume()/stop()`, accumulate-and-freeze `mm:ss`/`h:mm:ss` formatting) does not change - it already has no Android Context or camera dependency, so this is a pure move+rename. Update `CameraCaptureActivity.kt`'s import and the one constructor call site (`recordingTimer = CameraRecordingTimer { .. }` -> `RecordingElapsedTimer { .. }`). This class will also back the voice-capture indicator in Phase 11 - a feature-specific package is the wrong home for a feature-neutral utility once a second feature needs it.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/util/RecordingElapsedTimer.kt` exists; `ui/cameracapture/helpers/CameraRecordingTimer.kt` does not.
- `Grep` - `class RecordingElapsedTimer` once in the new file.
- `Grep` - `RecordingElapsedTimer` present in `CameraCaptureActivity.kt`; `CameraRecordingTimer` absent from the whole `app_v2/src` tree.

**Status:** `[x] done`

**Step Log:**

- 2026-07-03 - Verification 3/3 PASS. Files: `util/RecordingElapsedTimer.kt` (new, +76 LOC), `ui/cameracapture/helpers/CameraRecordingTimer.kt` (deleted), `ui/cameracapture/CameraCaptureActivity.kt` (import + 2 refs). Dev log recorded.

---

### Step 09.2 - Add pause bookkeeping to ScreenRecordingStateController

**Files:** `ScreenRecordingStateController.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `val isPaused: StateFlow<Boolean>` (backed by `MutableStateFlow`), `fun markPaused()`, `fun markResumed()`. Screen recording survives Activity recreation via the foreground service, so the elapsed-time math must be recomputable from stored instants rather than an in-memory accumulator: track `pausedAtElapsedRealtimeMs` (set on `markPaused()`) and a running `accumulatedPausedMs` (incremented by `now - pausedAtElapsedRealtimeMs` inside `markResumed()`). Add `fun elapsedMs(nowElapsedRealtimeMs: Long = SystemClock.elapsedRealtime()): Long` returning `now - startedAtElapsedRealtimeMs - accumulatedPausedMs - (if paused, now - pausedAtElapsedRealtimeMs else 0)`. `markStopped()` resets `accumulatedPausedMs` and `isPaused` back to their initial values alongside the existing `isRecording` reset.

**Verification:**

- `Grep` - `val isPaused: StateFlow<Boolean>`, `fun markPaused`, `fun markResumed`, `fun elapsedMs(` present.
- `Grep` - `accumulatedPausedMs` present (proves the recomputable-from-instants design, not a live accumulator that would reset on recreation).

**Status:** `[x] done`

**Step Log:**

- 2026-07-03 - Verification 2/2 PASS. Files: `core/screencapture/ScreenRecordingStateController.kt` (+31 LOC). Dev log recorded.

---

### Step 09.3 - Extend ScreenVideoRecordingController interface

**Files:** `ScreenVideoRecordingController.kt`
**Depends on:** Step 09.2

**Prompt for developer:**

> Add `fun requestPause(context: Context)` and `fun requestResume(context: Context)` to the interface, alongside the existing `launch`/`requestStop`. Give both a default no-op body (`{}`) rather than leaving them abstract - an abstract-only addition breaks `ScreenVideoRecordingControllerImpl` (`src/screenCapture`) the instant this interface change lands, since that class does not implement them until Phase 10. The default body keeps every phase boundary independently buildable; Phase 10 overrides both with real behavior.

**Verification:**

- `Grep` - `fun requestPause(` and `fun requestResume(` present in the interface, each with a `{}` body (not abstract).
- `.\a.ps1 fk` - `BUILD SUCCESSFUL` (default bodies keep `ScreenVideoRecordingControllerImpl` compiling unchanged until Phase 10).

**Status:** `[x] done`

**Step Log:**

- 2026-07-03 - Verification 2/2 PASS. Files: `core/screencapture/ScreenVideoRecordingController.kt` (+4 LOC). `.\a.ps1 fk` BUILD SUCCESSFUL in 58s. Dev log recorded. Design note: switched from abstract to default-bodied interface methods mid-step (topology fix - see prompt).

---

## Phase Done Criteria

- [x] All three steps `[x]`.
- [x] `.\a.ps1 fk` green.
- [x] `Grep` - `CameraRecordingTimer` returns zero hits anywhere under `app_v2/src`.
- [x] Dev log entry added for the five touched/deleted files.

---

## Handoff Notes to Next Phase

- Phase 10 implements `requestPause`/`requestResume` in `ScreenVideoRecordingControllerImpl` + the real `MediaRecorder.pause()/resume()` calls in `ScreenVideoRecordingService`, and writes to `stateController.markPaused()/markResumed()`.
- Phase 11 reads `stateController.isPaused` + `elapsedMs()` for the screen-recording indicator, and uses `RecordingElapsedTimer` directly for the voice-capture indicator.

---

## Rollback Plan

Revert the phase commit - relocated class has one call site (revert restores it); new controller/state-controller members are additive and unconsumed until Phase 10/11.
