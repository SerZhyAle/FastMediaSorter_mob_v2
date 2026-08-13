# Phase 04 - Recording-state + controller contract (src/main)

**Strategic spec:** [`../S0774_programs-screen-recording-action.md`](../S0774_programs-screen-recording-action.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase (flavor-neutral contract; independent of 02/03)
**Blocks:** Phase 05, Phase 06, Phase 07
**Steps done:** 3 / 3
**Started:** 2026-06-29
**Completed:** 2026-06-29

> **Phase Step Log (2026-06-29):** `ScreenRecordingStateController` (@Singleton, StateFlow + start instant), `ScreenVideoRecordingController` interface (launch/requestStop), empty `@Multibinds` module. `.\a.ps1 fk` BUILD SUCCESSFUL (empty-set injection resolves).

---

## Objective

Add the flavor-neutral contract that decouples `src/main` (UI) from `src/screenCapture` (engine): a shared recording-state holder observed by the UI, plus a `ScreenVideoRecordingController` multibinding interface (empty set in `src/main`) that the engine implements. This is the gating seam - an empty set means the feature is absent (lite/photos/legacy).

---

## Prerequisites

- [ ] None - this is a self-contained contract phase. Can run in parallel with 02/03 (it consumes neither). It must complete before 05/06/07.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/screencapture/ScreenRecordingStateController.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/screencapture/ScreenVideoRecordingController.kt` | New | ≤ 25 |
| `app_v2/src/main/java/com/sza/fastmediasorter/di/ScreenVideoRecordingControllerModule.kt` | New | ≤ 20 |

> Place the contract alongside the existing `core/screencapture/MenuScreenshotLauncher.kt` and its empty `@Multibinds` module under `di/`, mirroring that pattern exactly.

---

## Steps

### Step 04.1 - Add ScreenRecordingStateController (shared StateFlow)

**Files:** `ScreenRecordingStateController.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create an `@Singleton` Hilt-injectable holder exposing the live recording state for the UI to observe:
> `val isRecording: StateFlow<Boolean>` and a way to read the start instant for the timer (`val startedAtElapsedRealtimeMs: Long`). Provide `fun markStarted()` (sets `SystemClock.elapsedRealtime()` and emits `true`) and `fun markStopped()` (emits `false`). Back it with `MutableStateFlow`. No Android Context. The engine service writes; `MainActivity` reads. Constructor `@Inject`.

**Verification:**

- `Glob` - file exists.
- `Grep` - `class ScreenRecordingStateController` once; `val isRecording: StateFlow<Boolean>`, `fun markStarted`, `fun markStopped` present.
- `Grep` - `@Singleton` annotation present.

**Status:** `[x] done`

---

### Step 04.2 - Add ScreenVideoRecordingController interface

**Files:** `ScreenVideoRecordingController.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create the contract interface in `core/screencapture/`:
> `fun launch(activity: FragmentActivity)` (begin the consent → recording flow) and `fun requestStop(context: Context)` (stop an active recording). Mirror `MenuScreenshotLauncher`. No implementation in `src/main`.

**Verification:**

- `Glob` - file exists.
- `Grep` - `interface ScreenVideoRecordingController` once; `fun launch(` and `fun requestStop(` present.

**Status:** `[x] done`

---

### Step 04.3 - Add empty multibinding module

**Files:** `ScreenVideoRecordingControllerModule.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Create a Hilt module under `di/` declaring `@Multibinds abstract fun screenVideoRecordingControllers(): Set<ScreenVideoRecordingController>`, mirroring `MenuScreenshotLauncherModule`. This makes `Set<ScreenVideoRecordingController>` injectable everywhere and empty in flavors that do not mount `src/screenCapture`.

**Verification:**

- `Glob` - file exists.
- `Grep` - `@Multibinds` + `Set<ScreenVideoRecordingController>` present.
- `.\a.ps1 fk` compiles (empty-set injection resolves).

**Status:** `[x] done`

---

## Phase Done Criteria

- [ ] All three steps `[x]`.
- [ ] `.\a.ps1 fk` green.
- [ ] `Grep` - no `BuildConfig.IS_*` flavor guard in any new file.
- [ ] Dev log entry added for the three files.

---

## Handoff Notes to Next Phase

- Phase 05 (`src/screenCapture`) provides the real `ScreenVideoRecordingController` impl (`@Binds @IntoSet`) and writes to `ScreenRecordingStateController` from the service.
- Phases 06/07 inject `Set<ScreenVideoRecordingController>` for gating and `ScreenRecordingStateController` for the in-app card.

---

## Rollback Plan

Revert the phase commit - new contract types with no consumers yet; empty multibinding is inert.
