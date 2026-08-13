# Phase 03 - Service wiring

**Strategic spec:** [`../S0930_quick-audio-recorder-stop-overlay.md`](../S0930_quick-audio-recorder-stop-overlay.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** 2026-07-04
**Completed:** 2026-07-04

---

## Objective

Wire `QuickAudioRecorderService` to show/update/hide the injected `QuickRecorderIndicatorController` at its four lifecycle transition points, with no new foreground service and no new permission request (spec §3.2 performance/permission constraints).

---

## Prerequisites

- [ ] Phase 01 and Phase 02 are ✅ Done and the project builds.
- [ ] `app_v2/src/main/java/com/sza/fastmediasorter/widget/QuickAudioRecorderService.kt` read in full (research §4 already maps its exact call sites - do not re-derive).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/widget/QuickAudioRecorderService.kt` | Modified | ≤ 430 |

---

## Steps

### Step 03.1 - Inject the controller set and the elapsed timer

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/widget/QuickAudioRecorderService.kt`
**Depends on:** - start of phase (consumes Phase 01's interface + Phase 02's bindings)

**Prompt for developer:**

> Add a field `@Inject lateinit var indicatorControllers: Set<@JvmSuppressWildcards QuickRecorderIndicatorController>` alongside the existing `@Inject lateinit var` fields (`micRecordingSaver`, etc.) - same package, no import needed for the interface. Add `import com.sza.fastmediasorter.util.RecordingElapsedTimer` and two new private fields: `private var activeIndicator: QuickRecorderIndicatorController? = null` and `private val elapsedTimer = RecordingElapsedTimer { formatted -> activeIndicator?.updateElapsed(formatted) }`. The lambda only reads `activeIndicator` when a tick actually fires (well after Hilt field injection completes in the generated `onCreate()`), so the property initializer itself is safe.

**Verification:**

- `Grep` - `indicatorControllers: Set<@JvmSuppressWildcards QuickRecorderIndicatorController>` present.
- `Grep` - `private val elapsedTimer = RecordingElapsedTimer` present.
- `Grep -n "Log\.d\("` in the file returns zero hits (Timber only, pre-existing invariant).

**Status:** `[x]` done

**Step Log:**

- 2026-07-04 - Verification 3/3 PASS. Files: `app_v2/src/main/java/com/sza/fastmediasorter/widget/QuickAudioRecorderService.kt` (+8 LOC).

---

### Step 03.2 - Wire the four lifecycle transition points

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/widget/QuickAudioRecorderService.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> In `handleStart()`, immediately after the existing `isRecording = true` / `QuickAudioRecorderWidgetProvider.updateAllWidgets(this, true)` lines (recorder successfully started): resolve `val controller = indicatorControllers.firstOrNull { it.isAvailable(this) }`; if non-null, call `controller.show(this) { stopAndSave() }`, assign `activeIndicator = controller`, and call `elapsedTimer.start()`. If null (no bound controller, or permission not granted), do nothing - the existing notification Stop action and the S0796 repeat-gesture toggle remain the only stop paths, unchanged.
>
> In `stopAndSave()`, immediately after the existing `isRecording = false` / `QuickAudioRecorderWidgetProvider.updateAllWidgets(this, false)` lines (recording has stopped, before the async save block): call `elapsedTimer.stop()`, then `activeIndicator?.hide()`, then set `activeIndicator = null`.
>
> In `failAndStop()`, add the identical three lines (`elapsedTimer.stop()`, `activeIndicator?.hide()`, `activeIndicator = null`) right after its existing `isRecording = false` / `updateAllWidgets(this, false)` lines.
>
> In `onDestroy()`, inside the existing `if (mediaRecorder != null || recorderStarted)` defensive-teardown branch, add the same three lines - this is the one bypass path that does not already call `updateAllWidgets` (research §4), so it needs its own explicit hide to guarantee the overlay window is never left stuck.

**Verification:**

- `Grep` - `controller.show(this)` present in `handleStart()`.
- `Grep -c "activeIndicator?.hide()"` in the file returns `3` (stopAndSave, failAndStop, onDestroy).
- `Grep -c "elapsedTimer.stop()"` in the file returns `3`.
- `.\a.ps1 fc` passes (exit 0).

**Status:** `[x]` done

**Step Log:**

- 2026-07-04 - Verification 4/4 PASS (`.\a.ps1 fc` -> BUILD SUCCESSFUL in 23s). Files: `app_v2/src/main/java/com/sza/fastmediasorter/widget/QuickAudioRecorderService.kt` (379 -> 400 LOC).

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fc`.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for the modified file via `.\scripts\add_to_dev_log.ps1` (via `post-change.ps1`).

---

## Handoff Notes to Next Phase

`QuickAudioRecorderService` shows/hides the floating indicator on every start/stop/failure/destroy path, backed by the reused `RecordingElapsedTimer`, with zero behaviour change on flavors where `indicatorControllers` is empty. Phase 04 records the capability, regenerates the catalog, and hands to on-device verification.

---

## Rollback Plan

Revert the phase commit - one file modified, additive only (new field + four call-site insertions), no data migration, no change to the existing notification/repeat-gesture stop paths.
