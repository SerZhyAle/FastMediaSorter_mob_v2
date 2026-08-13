# Phase 11 - Compact recording indicator UI (src/main)

**Strategic spec:** [`../S0774_programs-screen-recording-action.md`](../S0774_programs-screen-recording-action.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 09 (pause state contract + relocated timer). Functional pause/resume needs Phase 10 merged too, but this phase compiles and the stop path works standalone.
**Blocks:** Phase 12
**Steps done:** 6 / 6
**Started:** 2026-07-03
**Completed:** 2026-07-03

---

## Objective

Replace the full-screen modal `AlertDialog` in both `MainScreenRecordingManager` and `MainVoiceCaptureManager` with one shared, non-modal, corner-anchored compact indicator (dot + timer + pause/resume + stop), fixing the device-test finding that the current card blocks the recorded content.

---

## Prerequisites

- [ ] Phase 09 is ✅ Done.
- [ ] `.\a.ps1 fk` green before starting.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/view_recording_indicator.xml` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/RecordingIndicatorOverlayManager.kt` | New | ≤ 140 |
| `app_v2/src/main/res/layout/activity_main.xml` | Modified | - |
| `app_v2/src/main/res/layout-land/activity_main.xml` | Modified | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainScreenRecordingManager.kt` | Modified | ≤ 130 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainVoiceCaptureManager.kt` | Modified | ≤ 320 |
| `app_v2/src/main/res/values/strings.xml` (+ `values-ru`, `values-uk`) | Modified | - |

> **Landscape parity note:** `view_recording_indicator.xml` is a new, orientation-agnostic file (a fixed-gravity corner pill, no portrait-specific sizing) - no `layout-land` counterpart needed. `activity_main.xml` DOES have an existing `layout-land` counterpart - both variants get the same container addition (step 11.3).

---

## Steps

### Step 11.1 - Add the indicator layout resource

**Files:** `view_recording_indicator.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Create a compact horizontal pill: a small red dot (reuse `@drawable/recording_indicator_dot`), a `TextView` for the `mm:ss` timer (red text, e.g. `?attr/colorError`), an icon-only pause/resume `ImageButton` or `MaterialButton` (style `Widget.FastMediaSorter.Button.Icon`) and an icon-only stop button (same style, red square icon - reuse/add `@drawable/ic_stop` if not already present). Background a rounded pill drawable (reuse `@drawable/bg_camera_timer` or an equivalent `?attr/colorSurface`-based pill if that drawable is camera-specific in naming only - check before reusing). Both icon buttons ≥ 48dp touch target (Rule 16) even though the pill itself is visually compact - use padding, not a shrunk `minWidth`/`minHeight`. Each icon button gets a `contentDescription` bound at runtime (Step 11.4/11.5), not hardcoded in the layout. Root `id`: `recordingIndicatorRoot`; timer `id`: `recordingIndicatorTimer`; pause button `id`: `recordingIndicatorPauseResume`; stop button `id`: `recordingIndicatorStop`.

**Verification:**

- `Glob` - file exists.
- `Grep` - `recordingIndicatorRoot`, `recordingIndicatorTimer`, `recordingIndicatorPauseResume`, `recordingIndicatorStop` each present once.
- `Grep` - no hardcoded `="#` hex color (Rule 20 neuroslop gate).

**Status:** `[x] done`

**Step Log:**

- 2026-07-03 - Verification 2/2 PASS. Files: `layout/view_recording_indicator.xml` (new, 52 LOC). Reused existing `ic_pause`/`ic_stop` drawables and `bg_camera_timer`/`recording_indicator_dot` (S0566 camera capture assets) rather than adding new ones. Dev log recorded.
- 2026-07-03 (retroactive, during Step 11.5) - Added a third `recordingIndicatorCancel` button (`ic_delete`, `visibility="gone"` by default) - see Step 11.5 log for why. Re-ran `.\a.ps1 fc` + `post-change.ps1`, still PASS.

---

### Step 11.2 - Add RecordingIndicatorOverlayManager

**Files:** `RecordingIndicatorOverlayManager.kt`
**Depends on:** Step 11.1

**Prompt for developer:**

> Create a plain class (constructor: `activity: FragmentActivity`, the inflated indicator view or its container) exposing: `fun show(accessibleLabel: String, onPauseResume: () -> Unit, onStop: () -> Unit)`, `fun updateTimer(text: String)`, `fun setPaused(paused: Boolean, pauseCd: String, resumeCd: String)` (swaps the pause/resume icon + `contentDescription`, non-color state distinction per §3.2 - the icon itself changes, not just a tint), `fun dismiss()`. Inflate `view_recording_indicator.xml` once (lazily) into the container passed from `activity_main.xml` (Step 11.3), keep it `View.GONE` until `show()`. Position must stay within `systemBars`/`displayCutout` safe bounds (Rule 17) - apply a `ViewCompat.setOnApplyWindowInsetsListener` top-inset offset on the container, mirroring the existing pattern at `MainActivity.applyEdgeToEdgeInsets()`. Both icon buttons must be `focusable = true` with sane `nextFocus*`/D-pad order (Rule 16) - the indicator is reachable while other content behind it still works (non-modal), so do not steal focus or block touches outside its own bounds.

**Verification:**

- `Glob` - file exists.
- `Grep` - `class RecordingIndicatorOverlayManager` once; `fun show(`, `fun updateTimer(`, `fun setPaused(`, `fun dismiss(` present.
- `Grep` - `setOnApplyWindowInsetsListener` present (systemBars/cutout safe-bounds compliance).
- `Grep -n "Log\.d\("` - zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-07-03 - Verification 4/4 PASS. Files: `ui/main/helpers/RecordingIndicatorOverlayManager.kt` (new, 65 LOC). Locates views via `findViewById` (host-neutral, matching the two consuming managers) rather than `ActivityMainBinding`, so it does not couple to MainActivity's generated binding type. Insets applied as an additive top-margin offset (not padding) since the pill is a direct `CoordinatorLayout` child via `<include>`. Buttons rely on default D-pad focus search (no explicit `nextFocus*` wired - no single correct anchor among MainActivity's many panels without guessing). `.\a.ps1 fc` BUILD SUCCESSFUL. Dev log recorded.
- 2026-07-03 (retroactive, during Step 11.5) - Added optional `onCancel`/`cancelCd` params to `show()` (discard-button support) and a mandatory `stopCd` param (stop button had no `contentDescription` at all - accessibility gap in this step's own original code). See Step 11.5 log. Re-ran `.\a.ps1 fk` + `post-change.ps1`, still PASS.

---

### Step 11.3 - Mount the indicator container in activity_main.xml (both orientations)

**Files:** `activity_main.xml`, `activity_main.xml` (layout-land)
**Depends on:** Step 11.1

**Prompt for developer:**

> Add a `<include layout="@layout/view_recording_indicator" .../>` (or an equivalent `<ViewStub>` if lazy inflation is preferred) as the LAST child of the root `CoordinatorLayout` in both `res/layout/activity_main.xml` and `res/layout-land/activity_main.xml`, `android:layout_gravity="top|end"`, `android:visibility="gone"` by default, with a top/end margin. Being the last child means it draws above the other panels (CoordinatorLayout siblings draw in child order) without needing a separate window or `SYSTEM_ALERT_WINDOW` permission (non-goal, strategic §2 non-goals). Both files must carry the identical addition - keep IDs and attributes in sync between them.

**Verification:**

- `Grep` - the same new indicator container `id` (e.g. `recordingIndicatorContainer`) present in both `layout/activity_main.xml` and `layout-land/activity_main.xml`.
- `Grep` - `layout_gravity="top|end"` present in both.

**Status:** `[x] done`

**Step Log:**

- 2026-07-03 - Verification 2/2 PASS. Files: `layout/activity_main.xml`, `layout-land/activity_main.xml` (+9 LOC each, identical `<include>`). Caught and fixed a namespace bug in `view_recording_indicator.xml` (`res/app` instead of `res-auto`) while cross-checking against the host layouts. `.\a.ps1 fr` BUILD SUCCESSFUL. Dev log recorded.

---

### Step 11.4 - Rewire MainScreenRecordingManager to the compact indicator

**Files:** `MainScreenRecordingManager.kt`
**Depends on:** Step 09.2 (isPaused/elapsedMs), Step 11.2, Step 11.3

**Prompt for developer:**

> Replace `showRecordingCard()`/`dismissRecordingCard()`'s `MaterialAlertDialogBuilder` body with calls into `RecordingIndicatorOverlayManager`. The timer tick now reads `stateController.elapsedMs()` instead of a raw `now - startedAt` subtraction (Phase 09 already accounts for paused spans). Observe `stateController.isPaused` (new `collectOnLifecycle` alongside the existing `isRecording` observer) to drive `setPaused()`. Wire the pause/resume button to `if (currently paused) controller?.requestResume(activity) else controller?.requestPause(activity)`; wire stop to the existing `controller?.requestStop(activity)` call, unchanged. Update the class KDoc - it no longer "mirrors the voice-recorder dialog" (that dialog is gone too, Step 11.5); both now share `RecordingIndicatorOverlayManager`.

**Verification:**

- `Grep` - `MaterialAlertDialogBuilder` absent from the file (fully removed).
- `Grep` - `RecordingIndicatorOverlayManager` present; `stateController.isPaused` and `stateController.elapsedMs(` present.
- `Grep` - `requestPause` and `requestResume` both referenced.

**Status:** `[x] done`

**Step Log:**

- 2026-07-03 - Verification 3/3 PASS. Files: `ui/main/helpers/MainScreenRecordingManager.kt` (rewritten body, ~+15 LOC net). Same "unbaselined since S0774's original creation" detekt gap as Phase 10 - fixed a pre-existing `ReturnCount` (justified `@Suppress`, guard-clause style) and 4 pre-existing `MagicNumber` (1000/60 time-math literals, extracted to companion constants) while here. `.\a.ps1 fk` BUILD SUCCESSFUL, scoped detekt PASS. Dev log recorded.
- 2026-07-03 (retroactive, during Step 11.5) - Added the new mandatory `stopCd` argument to this manager's `indicator.show(..)` call (accessibility fix - see Step 11.2/11.5 logs). Re-ran `.\a.ps1 fk` + `post-change.ps1`, still PASS.

---

### Step 11.5 - Rewire MainVoiceCaptureManager to the compact indicator + add pause

**Files:** `MainVoiceCaptureManager.kt`
**Depends on:** Step 09.1 (RecordingElapsedTimer), Step 11.2, Step 11.3

**Prompt for developer:**

> Replace `showRecordingDialog()`/`dismissRecordingDialog()`'s `MaterialAlertDialogBuilder` body with `RecordingIndicatorOverlayManager`, exactly like Step 11.4. This manager has no foreground service, so reuse `RecordingElapsedTimer` (relocated in Phase 09) directly - construct it with an `onTick` callback that calls `overlayManager.updateTimer(..)`, call `.start()` in `actuallyStart()`, `.stop()` in `release()`/`cancel()`, matching `CameraCaptureActivity`'s usage exactly. Add `fun pause()` and `fun resume()`: guard on `isRecorderStarted`, call `mediaRecorder?.pause()` / `.resume()` in a `try/catch` (same defensive pattern as `releaseRecorder()`'s existing `stop()` catch), call `recordingElapsedTimer.pause()`/`.resume()`, and drive the indicator's `setPaused()`. Wire the indicator's pause/resume button to these two new methods; stop button to the existing `stop()`. `cancel()` (audio-focus-loss path) must also reset any paused state - do not leave a stale "paused" indicator if the recording is torn down while paused.

**Verification:**

- `Grep` - `MaterialAlertDialogBuilder` absent from the file.
- `Grep` - `RecordingIndicatorOverlayManager`, `RecordingElapsedTimer`, `fun pause()`, `fun resume()` present.
- `Grep` - `mediaRecorder?.pause()` and `mediaRecorder?.resume()` each inside a `try`/`catch`.

**Status:** `[x] done`

**Step Log:**

- 2026-07-03 - Verification 3/3 PASS. Files: `ui/main/helpers/MainVoiceCaptureManager.kt` (rewritten body). Discovered mid-step: the old dialog had a NEGATIVE button ("Cancel" -> discard without saving) that the plan's 2-button indicator design (pause/resume + stop) had no equivalent for - dropping it would have been a silent functionality regression (Rule 8). Fixed by adding a third, optional "discard" button to the shared component (`view_recording_indicator.xml` + `RecordingIndicatorOverlayManager.show(onCancel, cancelCd)`, both amended retroactively - see their Step Logs), gone by default so screen recording (no such action in its own scope) is unaffected. Also discovered and fixed: neither manager's `show()` call was setting the stop button's `contentDescription` (accessibility gap in Step 11.2/11.4's own code) - added a `stopCd` parameter, wired at both call sites. `.\a.ps1 fc` BUILD SUCCESSFUL, scoped detekt PASS (one `SpacingBetweenDeclarationsWithComments` fixed). Dev log recorded.

---

### Step 11.6 - Trilingual strings for pause/resume

**Files:** `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** - can run any time before 11.4/11.5 land

**Prompt for developer:**

> Add two shared keys covering both call sites - `recording_pause` ("Pause recording" / EN) and `recording_resume` ("Resume recording" / EN) - via `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key recording_pause -En "Pause recording" -Ru "<RU>" -Uk "<UK>"` and the `recording_resume` equivalent (one lockstep call each, parity-enforced). Reuse the existing `screen_recording_stop` / `quick_voice_recording_stop` strings as the stop button's per-feature `contentDescription` - do not add new stop keys. Check against `docs/COMMUNICATION_POLICY.md` §2/§6 (message formula + tone checklist) before finalizing RU/UK wording.

**Verification:**

- `Grep` - `recording_pause` and `recording_resume` present in all three `strings.xml` files.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "recording_"` exits 0.
- Strings pass `COMMUNICATION_POLICY.md` §6 checklist.

**Status:** `[x] done`

**Step Log:**

- 2026-07-03 - Both keys were actually added in Phase 10 Step 10.1 (the notification action label needed them before Phase 11 started - see that step's topology note), not here. This step closes as a parity re-check only: `check_strings_localized.ps1 -KeyPrefix "recording_"` -> `OK: all 2 key(s)`. Stop button reuses `screen_recording_stop`/`quick_voice_recording_stop` (no new keys) as planned. Discard button reuses the existing generic `cancel` key (parity already OK, confirmed) rather than a new one - matches the original dialog's negative-button label exactly. Both short EN action labels are plain imperative verbs, no tone-checklist concerns (not an error/confirmation/empty-state message). No dev log needed - no file changed by this step.

---

## Phase Done Criteria

- [x] All six steps `[x]`.
- [x] `.\a.ps1 fc` green (layout + code together).
- [x] `Grep` - `AlertDialog` absent from both `MainScreenRecordingManager.kt` and `MainVoiceCaptureManager.kt`.
- [x] `scripts/quality/assert-neuroslop.ps1` clean on all touched files (no hex colors, no trivial comments, no bare Flow collection).
- [x] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

- Phase 12 regenerates the catalog (two new public classes: `RecordingElapsedTimer`, `RecordingIndicatorOverlayManager`) and closes the Completion Gate.
- Device retest (next `BlockNeedUserTest` cycle) must confirm: indicator does not block recorded content, pause/resume works for both screen and voice recording, timer stays correct across a pause and across backgrounding mid-recording.

---

## Rollback Plan

Revert the phase commit(s) - no Room schema or persisted-settings change; the modal dialogs can be restored verbatim from history if the compact indicator regresses.
