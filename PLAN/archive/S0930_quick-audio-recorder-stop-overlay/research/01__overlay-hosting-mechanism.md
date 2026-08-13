# Research: overlay-hosting mechanism for the headless quick-recorder indicator

**Strategic spec:** [`../../S0930_quick-audio-recorder-stop-overlay.md`](../../S0930_quick-audio-recorder-stop-overlay.md) §6 item 1-3

## 1. Existing overlay infrastructure

- `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/OverlayHostService.kt` and
  `ScreenGestureOverlayManager.kt` draw over other apps via
  `WindowManager.addView(view, LayoutParams(..., TYPE_APPLICATION_OVERLAY, ...))`. `noLegal` API 30+
  additionally hosts the same manager through an `AccessibilityService`
  (`app_v2/src/noLegal/java/com/sza/fastmediasorter/screencapture/ScreenshotAccessibilityService.kt`)
  with `TYPE_ACCESSIBILITY_OVERLAY`.
- `ScreenGestureOverlayManager` is not reusable as-is: its entire `View` exists to intercept drag
  gestures (`setOnTouchListener`, `FLAG_NOT_TOUCH_MODAL` pass-through, angle/distance classification).
  A static pill with clickable buttons needs the opposite touch behaviour. **Verdict: build a new,
  small, non-touch-intercepting overlay holder; do not extend `ScreenGestureOverlayManager`.**
- Flavor/source-set mounting (`app_v2/build.gradle.kts`):
  - `fms.screenCapture` (default **on**) mounts `src/screenCapture` into `standard` + `noLegal`.
  - `fms.edgeGestureOverlay` (default **off**) mounts `src/standardScreenCapture` into `standard`
    only when explicitly turned on - this is where `standard`'s gesture-overlay binding lives.
  - `noLegal` mounts its own `src/noLegal` gesture-overlay binding unconditionally (not gated by a flag).
  - `lite`, `legacy`, `vr`, `photos` never mount any screenCapture-family source set.

## 2. SYSTEM_ALERT_WINDOW permission matrix (verified by reading every flavor manifest)

| Flavor | Has SYSTEM_ALERT_WINDOW? | Source |
|---|---|---|
| standard (default build) | No | only with `-Pfms.edgeGestureOverlay=on` -> `src/standardScreenCapture/AndroidManifest.xml` |
| noLegal | Yes, unconditional | `src/noLegal/AndroidManifest.xml` |
| lite | No | no screenCapture-family source set mounted |
| photos | No | no screenCapture-family source set mounted |
| legacy | No | no screenCapture-family source set mounted |
| vr | No | no screenCapture-family source set mounted |

No shared permission-check helper exists - `Settings.canDrawOverlays(context)` is called ad hoc at
4 sites. The closest abstraction, `ScreenGestureOverlayController.isOverlayPermissionGranted()`, is
part of a heavier interface (also carries settings-intent/fallback-capture concerns) - not a fit for
a passive indicator. **A new, leaner interface's own `isAvailable(context)` is the right place for
this check**, not a shared reuse of the existing controller interface.

## 3. RecordingIndicatorOverlayManager (S0774) reuse

- `RecordingIndicatorOverlayManager` (`app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/RecordingIndicatorOverlayManager.kt`)
  is `FragmentActivity`-bound (`findViewById` against the Activity) - cannot be instantiated from a
  Service. **Not directly reusable as a class.**
- Its layout **is** already a standalone, reusable resource:
  `app_v2/src/main/res/layout/view_recording_indicator.xml` - root `LinearLayout
  id=recordingIndicatorRoot` with `recordingIndicatorTimer` (TextView), and three
  `MaterialButton style="@style/Widget.FastMediaSorter.Button.Icon"` children
  (`recordingIndicatorPauseResume`/`recordingIndicatorStop`/`recordingIndicatorCancel`), plus a dot
  `View` (`@drawable/recording_indicator_dot`) and root background `@drawable/bg_camera_timer`. The
  file's own header comment states it is orientation-agnostic (fixed corner pill, no `layout-land`
  counterpart needed) - confirmed both `activity_main.xml` and `layout-land/activity_main.xml` just
  `<include>` it identically. **Verdict: inflate `R.layout.view_recording_indicator` directly inside
  the new overlay window - zero visual duplication, no new layout file.** The new indicator only
  needs the timer + Stop button; pause/resume and cancel buttons stay `GONE`.
- Ticking timer: `app_v2/src/main/java/com/sza/fastmediasorter/util/RecordingElapsedTimer.kt` has
  **no Activity/lifecycle dependency** (`Handler(Looper.getMainLooper())` + `SystemClock.elapsedRealtime()`).
  **Verdict: reuse as-is** from inside the Service.
- Reusable localized strings (confirmed present in `values/`, `values-ru/`, `values-uk/strings.xml`):
  `R.string.quick_recorder_action_stop` ("Stop" / "Стоп" / "Стоп") and
  `R.string.quick_recorder_notification_recording` ("Recording.." / "Идёт запись.." / "Триває запис..").
  **No new strings needed.**

## 4. QuickAudioRecorderService attach/detach points

`app_v2/src/main/java/com/sza/fastmediasorter/widget/QuickAudioRecorderService.kt`:

- Show: `handleStart()` success path (after `isRecording = true`, ~line 126).
- Hide: `stopAndSave()`, immediately after `isRecording = false` / `updateAllWidgets(false)` (~line 157) -
  before the async save completes (recording itself has already stopped).
- Hide: `failAndStop()` (~line 240).
- Defensive hide: `onDestroy()` - the one bypass path that does not already call `updateAllWidgets`;
  needs its own explicit hide call so no window is ever left stuck.

No existing observable state (`isRecording` is a poll-only `@Volatile` flag) - direct function calls
at these four sites are sufficient; no new `StateFlow`/state-controller class is needed given the
spec's "no extra polling" constraint (unlike `ScreenRecordingStateController`, which exists because
`MainScreenRecordingManager` must survive Activity recreation - not a concern here).

## 5. No simpler generic overlay utility exists

Grepped `TYPE_APPLICATION_OVERLAY|TYPE_ACCESSIBILITY_OVERLAY|windowManager.addView` across every
source set - only the screenCapture-family sites above. Nothing in `src/main` already does this.

## Decisions carried into the tactical plan

1. New interface + empty-set Hilt multibind in `src/main` (mirrors `ScreenGestureOverlayController` /
   `ScreenGestureOverlayModule` shape, but leaner - `isAvailable`, `show`, `updateElapsed`, `hide`).
2. New concrete implementation class per flavor (`standardScreenCapture`, `noLegal`), each owning its
   own `WindowManager` + inflated `view_recording_indicator.xml`, bound `@IntoSet` in that flavor's
   existing `di/ScreenCaptureModule.kt` (do not create a new module file - both already exist and
   already hold exactly one `@Binds @IntoSet` line for the gesture-overlay controller).
3. `QuickAudioRecorderService` (`src/main`) injects the `Set<>`, wires the four call sites, and owns
   its own `RecordingElapsedTimer` instance feeding `updateElapsed`.
4. Zero new strings, zero new layout, zero new foreground service, zero new permission request flow.
